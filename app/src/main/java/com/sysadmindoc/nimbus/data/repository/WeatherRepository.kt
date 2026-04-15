package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.WeatherDao
import com.sysadmindoc.nimbus.data.location.ReverseGeocoder
import com.sysadmindoc.nimbus.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApi: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
    private val reverseGeocoder: ReverseGeocoder,
    private val weatherDao: WeatherDao,
    private val userPreferences: UserPreferences,
    private val sourceManager: dagger.Lazy<WeatherSourceManager>,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    companion object {
        private const val DEFAULT_CACHE_MAX_AGE_MS = 30 * 60 * 1000L
    }

    /**
     * Public entry point — delegates through [WeatherSourceManager] which handles
     * primary/fallback source selection. Falls back to direct Open-Meteo fetching
     * if sourceManager is not yet injected (e.g. in tests).
     */
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> {
        return sourceManager.get().getWeather(latitude, longitude, locationName)
    }

    /**
     * Direct Open-Meteo forecast fetch — used by the [OpenMeteoForecastAdapter].
     * Bypasses the source manager to avoid circular delegation.
     */
    suspend fun getWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val settings = userPreferences.settings.first()
            val forecastHours = settings.hourlyForecastHours
            val cacheMaxAgeMs = settings.cacheTtlMs.takeIf { it > 0L } ?: DEFAULT_CACHE_MAX_AGE_MS
            val response = weatherApi.getForecast(latitude, longitude, forecastHours = forecastHours)
            val location = resolveLocationName(latitude, longitude, locationName)
            val weatherData = mapToWeatherData(response, location)

            // Cache the response
            try {
                val responseJson = json.encodeToString(OpenMeteoResponse.serializer(), response)
                weatherDao.upsert(
                    WeatherCacheEntity(
                        locationKey = WeatherCacheEntity.makeKey(latitude, longitude),
                        responseJson = responseJson,
                        locationName = location.name,
                        locationRegion = location.region,
                        locationCountry = location.country,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
                weatherDao.deleteOlderThan(System.currentTimeMillis() - cacheMaxAgeMs)
            } catch (_: Exception) { /* Cache failure is non-fatal */ }

            Result.success(weatherData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedWeather(latitude: Double, longitude: Double): WeatherData? =
        withContext(Dispatchers.IO) {
            try {
                val cacheMaxAgeMs = userPreferences.settings.first().cacheTtlMs
                    .takeIf { it > 0L }
                    ?: DEFAULT_CACHE_MAX_AGE_MS
                val key = WeatherCacheEntity.makeKey(latitude, longitude)
                val cached = weatherDao.getCached(key) ?: return@withContext null
                if (cached.isExpired(cacheMaxAgeMs)) return@withContext null
                val response = json.decodeFromString(OpenMeteoResponse.serializer(), cached.responseJson)
                val location = LocationInfo(
                    name = cached.locationName,
                    region = cached.locationRegion,
                    country = cached.locationCountry,
                    latitude = cached.latitude,
                    longitude = cached.longitude,
                )
                mapToWeatherData(response, location)
            } catch (_: Exception) {
                null
            }
        }

    suspend fun searchLocations(query: String): Result<List<LocationInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val response = geocodingApi.searchLocation(query)
                val locations = response.results?.map { result ->
                    LocationInfo(
                        name = result.name,
                        region = result.admin1 ?: "",
                        country = result.country ?: "",
                        latitude = result.latitude,
                        longitude = result.longitude,
                    )
                } ?: emptyList()
                Result.success(locations)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun resolveLocationName(
        latitude: Double,
        longitude: Double,
        provided: String?,
    ): LocationInfo {
        if (provided != null) {
            return LocationInfo(name = provided, latitude = latitude, longitude = longitude)
        }
        // Use Android's built-in Geocoder for reliable reverse geocoding
        val result = reverseGeocoder.resolve(latitude, longitude)
        return if (result != null && result.name.isNotBlank()) {
            LocationInfo(
                name = result.name,
                region = result.region,
                country = result.country,
                latitude = latitude,
                longitude = longitude,
            )
        } else {
            // Last resort: fall back to Open-Meteo reverse geocoding.
            try {
                val geo = geocodingApi.reverseGeocode(latitude, longitude, count = 1)
                val first = geo.results?.firstOrNull()
                LocationInfo(
                    name = first?.name ?: "Unknown Location",
                    region = first?.admin1 ?: "",
                    country = first?.country ?: "",
                    latitude = latitude,
                    longitude = longitude,
                )
            } catch (_: Exception) {
                LocationInfo(
                    name = "Unknown Location",
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        }
    }

    private fun mapToWeatherData(
        response: OpenMeteoResponse,
        location: LocationInfo,
    ): WeatherData {
        val current = response.current ?: error("No current weather data")
        val hourly = response.hourly
        val daily = response.daily

        val todayIndex = 0
        val dailyHigh = daily?.temperatureMax?.getOrNull(todayIndex) ?: current.temperature ?: 0.0
        val dailyLow = daily?.temperatureMin?.getOrNull(todayIndex) ?: current.temperature ?: 0.0

        // Anchor "now" off the response's current.time (location-local), not the device clock.
        // Using LocalDateTime.now() would compare device-local time against location-local
        // timestamps, silently filtering out all hourly entries for distant locations
        // (same class of bug fixed in AirQualityRepository in v1.6.4).
        val locationLocalNow = parseDateTime(current.time) ?: LocalDateTime.now()

        return WeatherData(
            location = location,
            current = CurrentConditions(
                temperature = current.temperature ?: 0.0,
                feelsLike = current.apparentTemperature ?: current.temperature ?: 0.0,
                humidity = current.humidity ?: 0,
                weatherCode = WeatherCode.fromCode(current.weatherCode),
                observationTime = locationLocalNow,
                isDay = (current.isDay ?: 1) == 1,
                windSpeed = current.windSpeed ?: 0.0,
                windDirection = current.windDirection ?: 0,
                windGusts = current.windGusts,
                pressure = current.pressureMsl ?: 0.0,
                uvIndex = current.uvIndex ?: 0.0,
                visibility = current.visibility,
                dewPoint = current.dewPoint,
                cloudCover = current.cloudCover ?: 0,
                precipitation = current.precipitation ?: 0.0,
                snowfall = current.snowfall,
                snowDepth = current.snowDepth,
                cape = current.cape,
                dailyHigh = dailyHigh,
                dailyLow = dailyLow,
                sunrise = daily?.sunrise?.getOrNull(todayIndex),
                sunset = daily?.sunset?.getOrNull(todayIndex),
            ),
            hourly = mapHourlyData(hourly, locationLocalNow),
            daily = mapDailyData(daily),
        )
    }

    private fun mapHourlyData(hourly: HourlyWeather?, now: LocalDateTime): List<HourlyConditions> {
        if (hourly == null) return emptyList()
        return hourly.time.mapIndexedNotNull { i, timeStr ->
            val time = parseDateTime(timeStr) ?: return@mapIndexedNotNull null
            if (time.isBefore(now.minusHours(1))) return@mapIndexedNotNull null
            HourlyConditions(
                time = time,
                temperature = hourly.temperature?.getOrNull(i) ?: 0.0,
                feelsLike = hourly.apparentTemperature?.getOrNull(i),
                weatherCode = WeatherCode.fromCode(hourly.weatherCode?.getOrNull(i)),
                isDay = (hourly.isDay?.getOrNull(i) ?: 1) == 1,
                precipitationProbability = hourly.precipitationProbability?.getOrNull(i) ?: 0,
                precipitation = hourly.precipitation?.getOrNull(i),
                windSpeed = hourly.windSpeed?.getOrNull(i),
                windDirection = hourly.windDirection?.getOrNull(i),
                humidity = hourly.humidity?.getOrNull(i),
                uvIndex = hourly.uvIndex?.getOrNull(i),
                cloudCover = hourly.cloudCover?.getOrNull(i),
                visibility = hourly.visibility?.getOrNull(i),
                snowfall = hourly.snowfall?.getOrNull(i),
                windGusts = hourly.windGusts?.getOrNull(i),
                sunshineDuration = hourly.sunshineDuration?.getOrNull(i),
                surfacePressure = hourly.surfacePressure?.getOrNull(i),
            )
        }
    }

    private fun mapDailyData(daily: DailyWeather?): List<DailyConditions> {
        if (daily == null) return emptyList()
        return daily.time.mapIndexedNotNull { i, dateStr ->
            val date = parseDate(dateStr) ?: return@mapIndexedNotNull null
            DailyConditions(
                date = date,
                weatherCode = WeatherCode.fromCode(daily.weatherCode?.getOrNull(i)),
                temperatureHigh = daily.temperatureMax?.getOrNull(i) ?: 0.0,
                temperatureLow = daily.temperatureMin?.getOrNull(i) ?: 0.0,
                precipitationProbability = daily.precipitationProbabilityMax?.getOrNull(i) ?: 0,
                precipitationSum = daily.precipitationSum?.getOrNull(i),
                sunrise = daily.sunrise?.getOrNull(i),
                sunset = daily.sunset?.getOrNull(i),
                uvIndexMax = daily.uvIndexMax?.getOrNull(i),
                windSpeedMax = daily.windSpeedMax?.getOrNull(i),
                windDirectionDominant = daily.windDirectionDominant?.getOrNull(i),
                snowfallSum = daily.snowfallSum?.getOrNull(i),
                sunshineDuration = daily.sunshineDuration?.getOrNull(i),
                windGustsMax = daily.windGustsMax?.getOrNull(i),
                precipitationHours = daily.precipitationHours?.getOrNull(i),
            )
        }
    }

    /**
     * Public entry point for minutely precipitation — delegates through the source manager.
     */
    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> {
        return sourceManager.get().getMinutelyPrecipitation(latitude, longitude)
    }

    /**
     * Direct Open-Meteo minutely fetch — used by [OpenMeteoMinutelyAdapter].
     */
    suspend fun getMinutelyPrecipitationDirect(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApi.getMinutely15Forecast(latitude, longitude)
            val minutely = response.minutely15 ?: return@withContext Result.success(emptyList())
            val data = minutely.time.mapIndexedNotNull { i, timeStr ->
                val time = parseDateTime(timeStr) ?: return@mapIndexedNotNull null
                val precip = minutely.precipitation?.getOrNull(i) ?: 0.0
                MinutelyPrecipitation(time = time, precipitation = precip)
            }
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getYesterdayWeather(
        latitude: Double,
        longitude: Double,
    ): Result<DailyConditions?> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApi.getHistoricalForecast(latitude, longitude)
            val daily = response.daily ?: return@withContext Result.success(null)
            val yesterday = daily.time.firstOrNull() ?: return@withContext Result.success(null)
            val date = parseDate(yesterday) ?: return@withContext Result.success(null)
            Result.success(
                DailyConditions(
                    date = date,
                    weatherCode = WeatherCode.fromCode(daily.weatherCode?.getOrNull(0)),
                    temperatureHigh = daily.temperatureMax?.getOrNull(0) ?: 0.0,
                    temperatureLow = daily.temperatureMin?.getOrNull(0) ?: 0.0,
                    precipitationProbability = 0,
                    precipitationSum = null,
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = null,
                    windSpeedMax = null,
                    windDirectionDominant = null,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDateTime(str: String): LocalDateTime? = try {
        LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) { null }

    private fun parseDate(str: String): LocalDate? = try {
        LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) { null }
}
