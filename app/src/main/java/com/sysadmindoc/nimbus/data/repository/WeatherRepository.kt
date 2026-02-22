package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.WeatherDao
import com.sysadmindoc.nimbus.data.location.ReverseGeocoder
import com.sysadmindoc.nimbus.data.model.*
import kotlinx.coroutines.Dispatchers
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
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApi.getForecast(latitude, longitude)
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
            } catch (_: Exception) { /* Cache failure is non-fatal */ }

            Result.success(weatherData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedWeather(latitude: Double, longitude: Double): WeatherData? =
        withContext(Dispatchers.IO) {
            try {
                val key = WeatherCacheEntity.makeKey(latitude, longitude)
                val cached = weatherDao.getCached(key) ?: return@withContext null
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
            // Last resort: try Open-Meteo search with nearby city
            try {
                val geo = geocodingApi.searchLocation("${latitude},${longitude}", count = 1)
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

        return WeatherData(
            location = location,
            current = CurrentConditions(
                temperature = current.temperature ?: 0.0,
                feelsLike = current.apparentTemperature ?: current.temperature ?: 0.0,
                humidity = current.humidity ?: 0,
                weatherCode = WeatherCode.fromCode(current.weatherCode),
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
                dailyHigh = dailyHigh,
                dailyLow = dailyLow,
                sunrise = daily?.sunrise?.getOrNull(todayIndex),
                sunset = daily?.sunset?.getOrNull(todayIndex),
            ),
            hourly = mapHourlyData(hourly),
            daily = mapDailyData(daily),
        )
    }

    private fun mapHourlyData(hourly: HourlyWeather?): List<HourlyConditions> {
        if (hourly == null) return emptyList()
        val now = LocalDateTime.now()
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
            )
        }
    }

    private fun parseDateTime(str: String): LocalDateTime? = try {
        LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) { null }

    private fun parseDate(str: String): LocalDate? = try {
        LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) { null }
}
