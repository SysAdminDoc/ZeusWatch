package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.OpenWeatherMapApi
import com.sysadmindoc.nimbus.data.model.*
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "OwmAdapter"

/**
 * Forecast adapter for OpenWeatherMap One Call 3.0 API.
 * Maps OWM response to [WeatherData] domain model.
 * All OWM data arrives in metric (Celsius, m/s, hPa, mm).
 * Wind speed is converted from m/s → km/h for internal consistency.
 */
@Singleton
class OwmForecastAdapter @Inject constructor(
    private val api: OpenWeatherMapApi,
    private val prefs: UserPreferences,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = runCatching {
        val apiKey = prefs.settings.first().owmApiKey
        require(apiKey.isNotBlank()) { "OpenWeatherMap API key not configured" }

        val response = api.getOneCall(latitude, longitude, apiKey)
        mapToWeatherData(response, latitude, longitude, locationName)
    }.onFailure { Log.w(TAG, "OWM forecast failed", it) }

    private fun mapToWeatherData(
        r: OwmOneCallResponse,
        lat: Double,
        lon: Double,
        locationName: String?,
    ): WeatherData {
        val current = r.current ?: error("No current data from OWM")
        val zone = try { ZoneId.of(r.timezone) } catch (_: Exception) { ZoneId.systemDefault() }
        val firstDaily = r.daily.firstOrNull()
        val locationLocalNow = epochToLocalDateTime(current.dt, zone)

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: "Unknown",
                latitude = lat,
                longitude = lon,
            ),
            current = CurrentConditions(
                temperature = current.temp,
                feelsLike = current.feelsLike,
                humidity = current.humidity,
                weatherCode = WeatherCode.fromCode(
                    OwmConditionMapper.toWmoCode(current.weather.firstOrNull()?.id ?: 0)
                ),
                observationTime = locationLocalNow,
                isDay = current.weather.firstOrNull()?.icon?.let {
                    OwmConditionMapper.isDayFromIcon(it)
                } ?: true,
                windSpeed = current.windSpeed * 3.6, // m/s → km/h
                windDirection = current.windDeg,
                windGusts = current.windGust?.let { it * 3.6 },
                pressure = current.pressure.toDouble(),
                uvIndex = current.uvi,
                visibility = current.visibility, // meters (WeatherFormatter expects meters)
                dewPoint = current.dewPoint,
                cloudCover = current.clouds,
                precipitation = (current.rain?.oneHour ?: 0.0) + (current.snow?.oneHour ?: 0.0),
                snowfall = current.snow?.oneHour,
                dailyHigh = firstDaily?.temp?.max ?: current.temp,
                dailyLow = firstDaily?.temp?.min ?: current.temp,
                sunrise = current.sunrise?.let { epochToTimeStr(it, zone) },
                sunset = current.sunset?.let { epochToTimeStr(it, zone) },
            ),
            hourly = mapHourly(r.hourly, zone, locationLocalNow),
            daily = mapDaily(r.daily, zone),
        )
    }

    private fun mapHourly(
        hourly: List<OwmHourly>,
        zone: ZoneId,
        now: LocalDateTime,
    ): List<HourlyConditions> {
        return hourly.mapNotNull { h ->
            val time = epochToLocalDateTime(h.dt, zone)
            if (time.isBefore(now.minusHours(1))) return@mapNotNull null
            HourlyConditions(
                time = time,
                temperature = h.temp,
                feelsLike = h.feelsLike,
                weatherCode = WeatherCode.fromCode(
                    OwmConditionMapper.toWmoCode(h.weather.firstOrNull()?.id ?: 0)
                ),
                isDay = h.weather.firstOrNull()?.icon?.let {
                    OwmConditionMapper.isDayFromIcon(it)
                } ?: true,
                precipitationProbability = ((h.pop ?: 0.0) * 100).toInt(),
                precipitation = (h.rain?.oneHour ?: 0.0) + (h.snow?.oneHour ?: 0.0),
                windSpeed = h.windSpeed?.let { it * 3.6 },
                windDirection = h.windDeg,
                humidity = h.humidity,
                uvIndex = h.uvi,
                cloudCover = h.clouds,
                visibility = h.visibility, // meters (WeatherFormatter expects meters)
                snowfall = h.snow?.oneHour,
                windGusts = h.windGust?.let { it * 3.6 },
                surfacePressure = h.pressure?.toDouble(),
            )
        }
    }

    private fun mapDaily(daily: List<OwmDaily>, zone: ZoneId): List<DailyConditions> {
        return daily.mapNotNull { d ->
            val date = epochToLocalDate(d.dt, zone)
            DailyConditions(
                date = date,
                weatherCode = WeatherCode.fromCode(
                    OwmConditionMapper.toWmoCode(d.weather.firstOrNull()?.id ?: 0)
                ),
                temperatureHigh = d.temp.max,
                temperatureLow = d.temp.min,
                precipitationProbability = ((d.pop ?: 0.0) * 100).toInt(),
                precipitationSum = d.rain?.plus(d.snow ?: 0.0),
                sunrise = d.sunrise?.let { epochToTimeStr(it, zone) },
                sunset = d.sunset?.let { epochToTimeStr(it, zone) },
                uvIndexMax = d.uvi,
                windSpeedMax = d.windSpeed?.let { it * 3.6 },
                windDirectionDominant = d.windDeg,
                snowfallSum = d.snow,
                windGustsMax = d.windGust?.let { it * 3.6 },
            )
        }
    }

    private fun epochToLocalDateTime(epoch: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochSecond(epoch).atZone(zone).toLocalDateTime()

    private fun epochToLocalDate(epoch: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()

    private fun epochToTimeStr(epoch: Long, zone: ZoneId): String =
        Instant.ofEpochSecond(epoch).atZone(zone).toLocalDateTime()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

/**
 * Alert adapter for OpenWeatherMap.
 */
@Singleton
class OwmAlertAdapter @Inject constructor(
    private val api: OpenWeatherMapApi,
    private val prefs: UserPreferences,
) {
    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> = runCatching {
        val apiKey = prefs.settings.first().owmApiKey
        require(apiKey.isNotBlank()) { "OpenWeatherMap API key not configured" }

        val response = api.getOneCall(latitude, longitude, apiKey, exclude = "current,minutely,hourly,daily")
        response.alerts.mapIndexed { i, alert ->
            val zone = try { ZoneId.of(response.timezone) } catch (_: Exception) { ZoneId.systemDefault() }
            WeatherAlert(
                id = "owm-${alert.start}-$i",
                event = alert.event,
                headline = alert.event,
                description = alert.description,
                instruction = null,
                severity = mapOwmSeverity(alert.tags),
                urgency = AlertUrgency.UNKNOWN,
                certainty = "",
                senderName = alert.senderName,
                areaDescription = "",
                effective = Instant.ofEpochSecond(alert.start).atZone(zone)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                expires = Instant.ofEpochSecond(alert.end).atZone(zone)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                response = null,
            )
        }
    }.onFailure { Log.w(TAG, "OWM alerts failed", it) }

    private fun mapOwmSeverity(tags: List<String>): AlertSeverity {
        val lower = tags.map { it.lowercase() }
        return when {
            "extreme" in lower -> AlertSeverity.EXTREME
            "severe" in lower || "warning" in lower -> AlertSeverity.SEVERE
            "moderate" in lower || "watch" in lower -> AlertSeverity.MODERATE
            "minor" in lower || "advisory" in lower -> AlertSeverity.MINOR
            else -> AlertSeverity.UNKNOWN
        }
    }
}

/**
 * Air quality adapter for OpenWeatherMap.
 * OWM uses its own 1-5 AQI scale; we convert to US EPA 0-500 scale.
 */
@Singleton
class OwmAqiAdapter @Inject constructor(
    @Named("owm_aqi") private val api: OpenWeatherMapApi,
    private val prefs: UserPreferences,
) {
    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
    ): Result<AirQualityData> = runCatching {
        val apiKey = prefs.settings.first().owmApiKey
        require(apiKey.isNotBlank()) { "OpenWeatherMap API key not configured" }

        val response = api.getAirPollution(latitude, longitude, apiKey)
        val entry = response.list.firstOrNull() ?: error("No air pollution data")
        val c = entry.components

        // Convert OWM μg/m3 values to approximate US EPA AQI
        val pm25Aqi = pm25ToAqi(c.pm2_5)
        val usAqi = maxOf(pm25Aqi, pm10ToAqi(c.pm10), o3ToAqi(c.o3))

        AirQualityData(
            usAqi = usAqi,
            europeanAqi = owmAqiToEuropean(entry.main.aqi),
            aqiLevel = AqiLevel.fromAqi(usAqi),
            pm25 = c.pm2_5,
            pm10 = c.pm10,
            ozone = c.o3 / 2.0, // μg/m3 → approximate ppb
            nitrogenDioxide = c.no2 / 1.88, // μg/m3 → approximate ppb
            sulphurDioxide = c.so2 / 2.62, // μg/m3 → approximate ppb
            carbonMonoxide = c.co / 1145.0 * 1000.0, // μg/m3 → approximate ppb
            pollen = PollenData(), // OWM doesn't provide pollen
        )
    }.onFailure { Log.w(TAG, "OWM AQI failed", it) }

    /** Approximate PM2.5 μg/m3 → US EPA AQI */
    private fun pm25ToAqi(pm: Double): Int = when {
        pm <= 12.0 -> linearScale(pm, 0.0, 12.0, 0, 50)
        pm <= 35.4 -> linearScale(pm, 12.1, 35.4, 51, 100)
        pm <= 55.4 -> linearScale(pm, 35.5, 55.4, 101, 150)
        pm <= 150.4 -> linearScale(pm, 55.5, 150.4, 151, 200)
        pm <= 250.4 -> linearScale(pm, 150.5, 250.4, 201, 300)
        else -> linearScale(pm, 250.5, 500.0, 301, 500)
    }

    /** Approximate PM10 μg/m3 → US EPA AQI */
    private fun pm10ToAqi(pm: Double): Int = when {
        pm <= 54.0 -> linearScale(pm, 0.0, 54.0, 0, 50)
        pm <= 154.0 -> linearScale(pm, 55.0, 154.0, 51, 100)
        pm <= 254.0 -> linearScale(pm, 155.0, 254.0, 101, 150)
        pm <= 354.0 -> linearScale(pm, 255.0, 354.0, 151, 200)
        pm <= 424.0 -> linearScale(pm, 355.0, 424.0, 201, 300)
        else -> linearScale(pm, 425.0, 604.0, 301, 500)
    }

    /** Approximate O3 μg/m3 → US EPA AQI (using 1-hour breakpoints) */
    private fun o3ToAqi(o3: Double): Int {
        val ppb = o3 / 2.0 // rough μg/m3 → ppb
        return when {
            ppb <= 54.0 -> linearScale(ppb, 0.0, 54.0, 0, 50)
            ppb <= 70.0 -> linearScale(ppb, 55.0, 70.0, 51, 100)
            ppb <= 85.0 -> linearScale(ppb, 71.0, 85.0, 101, 150)
            ppb <= 105.0 -> linearScale(ppb, 86.0, 105.0, 151, 200)
            ppb <= 200.0 -> linearScale(ppb, 106.0, 200.0, 201, 300)
            else -> linearScale(ppb, 201.0, 604.0, 301, 500)
        }
    }

    private fun linearScale(value: Double, loC: Double, hiC: Double, loI: Int, hiI: Int): Int =
        ((hiI - loI) / (hiC - loC) * (value - loC) + loI).toInt().coerceIn(0, 500)

    /** OWM 1-5 scale → European AQI (roughly 0-100 range per tier) */
    private fun owmAqiToEuropean(aqi: Int): Int = when (aqi) {
        1 -> 20
        2 -> 40
        3 -> 60
        4 -> 80
        5 -> 100
        else -> 0
    }
}
