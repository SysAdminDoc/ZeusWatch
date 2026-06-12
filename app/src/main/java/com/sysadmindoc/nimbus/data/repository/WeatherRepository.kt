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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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
        locationTimeZone: String? = null,
        sourceOverrides: SourceOverrides = SourceOverrides(),
    ): Result<WeatherData> {
        return sourceManager.get().getWeather(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            locationTimeZone = locationTimeZone,
            sourceOverrides = sourceOverrides,
        )
    }

    suspend fun getWeather(location: SavedLocationEntity): Result<WeatherData> =
        getWeather(
            latitude = location.latitude,
            longitude = location.longitude,
            locationName = location.name,
            locationTimeZone = location.timeZone,
            sourceOverrides = location.sourceOverrides(),
        )

    /**
     * Direct Open-Meteo forecast fetch — used by the [OpenMeteoForecastAdapter].
     * Bypasses the source manager to avoid circular delegation.
     */
    suspend fun getWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    /**
     * Direct Open-Meteo BOM ACCESS-G forecast fetch.
     *
     * This is the roadmap's safe Australian path: it uses Open-Meteo's
     * documented `/v1/bom` proxy rather than the undocumented BOM app API.
     */
    suspend fun getBomWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getBomForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    private suspend fun getOpenMeteoWeather(
        latitude: Double,
        longitude: Double,
        locationName: String?,
        fetch: suspend (forecastHours: Int) -> OpenMeteoResponse,
    ): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val settings = userPreferences.settings.first()
            val forecastHours = settings.hourlyForecastHours
            val cacheMaxAgeMs = settings.cacheTtlMs.takeIf { it > 0L } ?: DEFAULT_CACHE_MAX_AGE_MS
            val response = fetch(forecastHours)
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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
                    timeZone = response.timezone?.takeIf { it.toZoneIdOrNull() != null },
                )
                mapToWeatherData(
                    response,
                    location,
                    // Preserve the original fetch time so "updated X ago" and
                    // the staleness colouring reflect the cache's real age —
                    // not "now", which made every cached read look fresh.
                    observedAt = Instant.ofEpochMilli(cached.cachedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime(),
                )
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
                        timeZone = result.timezone?.takeIf { it.toZoneIdOrNull() != null },
                    )
                } ?: emptyList()
                Result.success(locations)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }

    private suspend fun resolveLocationName(
        latitude: Double,
        longitude: Double,
        provided: String?,
    ): LocationInfo {
        if (!provided.isNullOrBlank()) {
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
                    timeZone = first?.timezone?.takeIf { it.toZoneIdOrNull() != null },
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
        observedAt: LocalDateTime? = null,
    ): WeatherData {
        val mappedLocation = location.withResponseTimeZone(response.timezone)
        val hourly = response.hourly
        val daily = response.daily
        val currentHourlyIndex = nearestHourlyIndex(response)
        val snapshot = resolveCurrentSnapshot(response, currentHourlyIndex)

        // Anchor "today" on the location-local date instead of assuming the
        // daily array starts today. It does for the current API params (no
        // past_days), so this resolves to index 0 in practice — but matching by
        // date keeps the current-conditions high/low/sunrise correct if the
        // params ever change or a cached response is replayed across midnight.
        val today = response.locationLocalNow().toLocalDate()
        val todayIndex = daily?.time
            ?.indexOfFirst { parseDate(it) == today }
            ?.takeIf { it >= 0 }
            ?: 0
        val dailyHigh = daily?.temperatureMax?.getOrNull(todayIndex) ?: snapshot.temperature
        val dailyLow = daily?.temperatureMin?.getOrNull(todayIndex) ?: snapshot.temperature

        return WeatherData(
            location = mappedLocation,
            current = snapshot.toCurrentConditions(
                dailyHigh = dailyHigh,
                dailyLow = dailyLow,
                sunrise = daily?.sunrise?.getOrNull(todayIndex),
                sunset = daily?.sunset?.getOrNull(todayIndex),
            ),
            hourly = mapHourlyData(hourly, snapshot.observationTime),
            daily = mapDailyData(daily),
            lastUpdated = observedAt ?: LocalDateTime.now(),
        )
    }

    private fun resolveCurrentSnapshot(
        response: OpenMeteoResponse,
        hourlyIndex: Int?,
    ): OpenMeteoCurrentSnapshot {
        val current = response.current
        val hourly = response.hourly
        val temperature = current?.temperature ?: hourly?.temperature.valueAt(hourlyIndex) ?: 0.0

        return OpenMeteoCurrentSnapshot(
            temperature = temperature,
            feelsLike = current?.apparentTemperature ?: hourly?.apparentTemperature.valueAt(hourlyIndex) ?: temperature,
            humidity = current?.humidity ?: hourly?.humidity.valueAt(hourlyIndex) ?: 0,
            weatherCode = current?.weatherCode ?: hourly?.weatherCode.valueAt(hourlyIndex),
            observationTime = resolveObservationTime(response, hourlyIndex),
            isDay = (current?.isDay ?: hourly?.isDay.valueAt(hourlyIndex) ?: 1) == 1,
            windSpeed = current?.windSpeed ?: hourly?.windSpeed.valueAt(hourlyIndex) ?: 0.0,
            windDirection = current?.windDirection ?: hourly?.windDirection.valueAt(hourlyIndex) ?: 0,
            windGusts = current?.windGusts ?: hourly?.windGusts.valueAt(hourlyIndex),
            pressure = resolveCurrentPressure(current, hourly, hourlyIndex),
            uvIndex = current?.uvIndex ?: hourly?.uvIndex.valueAt(hourlyIndex) ?: 0.0,
            visibility = current?.visibility ?: hourly?.visibility.valueAt(hourlyIndex),
            dewPoint = current?.dewPoint,
            cloudCover = current?.cloudCover ?: hourly?.cloudCover.valueAt(hourlyIndex) ?: 0,
            precipitation = current?.precipitation ?: hourly?.precipitation.valueAt(hourlyIndex) ?: 0.0,
            snowfall = current?.snowfall ?: hourly?.snowfall.valueAt(hourlyIndex),
            // Open-Meteo snow_depth arrives in meters; the formatter contract
            // (WeatherFormatter.formatSnowDepth) expects centimeters.
            snowDepth = (current?.snowDepth ?: hourly?.snowDepth.valueAt(hourlyIndex))
                ?.let { it * 100.0 },
            cape = current?.cape ?: hourly?.cape.valueAt(hourlyIndex),
        )
    }

    private fun resolveObservationTime(
        response: OpenMeteoResponse,
        hourlyIndex: Int?,
    ): LocalDateTime = response.current?.time?.let(::parseDateTime)
        ?: hourlyIndex?.let { response.hourly?.time?.getOrNull(it)?.let(::parseDateTime) }
        ?: response.locationLocalNow()

    private fun resolveCurrentPressure(
        current: CurrentWeather?,
        hourly: HourlyWeather?,
        hourlyIndex: Int?,
    ): Double = current?.pressureMsl
        ?: current?.surfacePressure
        ?: hourly?.surfacePressure.valueAt(hourlyIndex)
        ?: 0.0

    private data class OpenMeteoCurrentSnapshot(
        val temperature: Double,
        val feelsLike: Double,
        val humidity: Int,
        val weatherCode: Int?,
        val observationTime: LocalDateTime,
        val isDay: Boolean,
        val windSpeed: Double,
        val windDirection: Int,
        val windGusts: Double?,
        val pressure: Double,
        val uvIndex: Double,
        val visibility: Double?,
        val dewPoint: Double?,
        val cloudCover: Int,
        val precipitation: Double,
        val snowfall: Double?,
        val snowDepth: Double?,
        val cape: Double?,
    ) {
        fun toCurrentConditions(
            dailyHigh: Double,
            dailyLow: Double,
            sunrise: String?,
            sunset: String?,
        ): CurrentConditions = CurrentConditions(
            temperature = temperature,
            feelsLike = feelsLike,
            humidity = humidity,
            weatherCode = WeatherCode.fromCode(weatherCode),
            observationTime = observationTime,
            isDay = isDay,
            windSpeed = windSpeed,
            windDirection = windDirection,
            windGusts = windGusts,
            pressure = pressure,
            uvIndex = uvIndex,
            visibility = visibility,
            dewPoint = dewPoint,
            cloudCover = cloudCover,
            precipitation = precipitation,
            snowfall = snowfall,
            snowDepth = snowDepth,
            cape = cape,
            dailyHigh = dailyHigh,
            dailyLow = dailyLow,
            sunrise = sunrise,
            sunset = sunset,
        )
    }

    private fun nearestHourlyIndex(response: OpenMeteoResponse): Int? {
        val hourly = response.hourly ?: return null
        if (hourly.time.isEmpty()) return null
        val localNow = response.locationLocalNow()
        return hourly.time
            .mapIndexedNotNull { index, time -> parseDateTime(time)?.let { index to it } }
            .minByOrNull { (_, time) -> abs(Duration.between(time, localNow).toMinutes()) }
            ?.first
    }

    private fun OpenMeteoResponse.locationLocalNow(): LocalDateTime {
        val zone = timezone
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return zone?.let { LocalDateTime.ofInstant(Instant.now(), it) } ?: LocalDateTime.now()
    }

    private fun <T> List<T?>?.valueAt(index: Int?): T? =
        index?.let { this?.getOrNull(it) }

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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun parseDateTime(str: String): LocalDateTime? = try {
        LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) { null }

    private fun parseDate(str: String): LocalDate? = try {
        LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) { null }

    private fun LocationInfo.withResponseTimeZone(responseTimeZone: String?): LocationInfo {
        val validResponseTimeZone = responseTimeZone?.takeIf { it.toZoneIdOrNull() != null }
        return if (timeZone == null && validResponseTimeZone != null) {
            copy(timeZone = validResponseTimeZone)
        } else {
            this
        }
    }
}
