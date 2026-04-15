package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.BrightSkyApi
import com.sysadmindoc.nimbus.data.model.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BrightSkyAdapter"

/**
 * Forecast adapter for Bright Sky (DWD open data).
 * No API key required. Best coverage in/near Germany.
 * DWD data: Celsius, km/h, mm, hPa, meters.
 * Fetches today + 9 more days of forecast data.
 */
@Singleton
class BrightSkyForecastAdapter @Inject constructor(
    private val api: BrightSkyApi,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = runCatching {
        val today = LocalDate.now(ZoneOffset.UTC)
        val endDate = today.plusDays(10) // DWD MOSMIX forecasts go ~10 days

        val response = api.getWeather(
            latitude = latitude,
            longitude = longitude,
            date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
            lastDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )

        // Also grab current conditions for a more accurate "now" reading
        val currentResponse = runCatching {
            api.getCurrentWeather(latitude, longitude)
        }.getOrNull()

        mapToWeatherData(response, currentResponse, latitude, longitude, locationName)
    }.onFailure { Log.w(TAG, "Bright Sky forecast failed", it) }

    private fun mapToWeatherData(
        forecast: BrightSkyWeatherResponse,
        currentResp: BrightSkyWeatherResponse?,
        lat: Double,
        lon: Double,
        locationName: String?,
    ): WeatherData {
        val entries = forecast.weather
        require(entries.isNotEmpty()) { "No weather data from Bright Sky" }

        // Use current_weather endpoint if available, otherwise latest observation
        val currentEntry = currentResp?.weather?.firstOrNull() ?: findCurrentEntry(entries)
        ?: entries.first()
        val currentLocalTime = parseTimestamp(currentEntry.timestamp)
            ?: entries.firstNotNullOfOrNull { parseTimestamp(it.timestamp) }
            ?: LocalDateTime.now()
        val today = currentLocalTime.toLocalDate()

        // Build hourly from forecast entries
        val hourly = mapHourly(entries, currentLocalTime)

        // Aggregate daily from hourly entries
        val daily = aggregateDaily(entries)

        // Today's high/low from daily aggregation
        val todayDaily = daily.firstOrNull { it.date == today }

        val stationName = forecast.sources.firstOrNull()?.stationName

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: stationName ?: "Unknown",
                latitude = lat,
                longitude = lon,
            ),
            current = CurrentConditions(
                temperature = currentEntry.temperature ?: 0.0,
                feelsLike = currentEntry.temperature ?: 0.0, // Bright Sky doesn't provide feels-like
                humidity = currentEntry.humidity?.toInt() ?: 0,
                weatherCode = WeatherCode.fromCode(
                    BsConditionMapper.toWmoCode(currentEntry.condition, currentEntry.icon)
                ),
                observationTime = currentLocalTime,
                isDay = BsConditionMapper.isDayFromIcon(currentEntry.icon),
                windSpeed = currentEntry.windSpeed ?: 0.0, // Already km/h
                windDirection = currentEntry.windDirection ?: 0,
                windGusts = currentEntry.windGustSpeed,
                pressure = currentEntry.pressureMsl ?: currentEntry.pressure ?: 0.0,
                uvIndex = 0.0, // DWD doesn't provide UV
                visibility = currentEntry.visibility?.let { it / 1000.0 }, // m → km
                dewPoint = currentEntry.dewPoint,
                cloudCover = currentEntry.cloudCover?.toInt() ?: 0,
                precipitation = currentEntry.precipitation ?: 0.0,
                dailyHigh = todayDaily?.temperatureHigh ?: currentEntry.temperature ?: 0.0,
                dailyLow = todayDaily?.temperatureLow ?: currentEntry.temperature ?: 0.0,
                sunrise = null, // Bright Sky doesn't provide sunrise/sunset
                sunset = null,
            ),
            hourly = hourly,
            daily = daily,
        )
    }

    private fun findCurrentEntry(entries: List<BsWeatherEntry>): BsWeatherEntry? {
        val nowInstant = Instant.now()
        val nowLocal = LocalDateTime.now()
        return entries.minByOrNull { entry ->
            val entryInstant = parseTimestampInstant(entry.timestamp)
            if (entryInstant != null) {
                kotlin.math.abs(Duration.between(nowInstant, entryInstant).toMinutes())
            } else {
                val entryTime = parseTimestamp(entry.timestamp) ?: return@minByOrNull Long.MAX_VALUE
                kotlin.math.abs(Duration.between(nowLocal, entryTime).toMinutes())
            }
        }
    }

    private fun mapHourly(entries: List<BsWeatherEntry>, now: LocalDateTime): List<HourlyConditions> {
        return entries.mapNotNull { e ->
            val time = parseTimestamp(e.timestamp) ?: return@mapNotNull null
            if (time.isBefore(now.minusHours(1))) return@mapNotNull null
            HourlyConditions(
                time = time,
                temperature = e.temperature ?: 0.0,
                feelsLike = null,
                weatherCode = WeatherCode.fromCode(
                    BsConditionMapper.toWmoCode(e.condition, e.icon)
                ),
                isDay = BsConditionMapper.isDayFromIcon(e.icon),
                precipitationProbability = e.precipitationProbability?.toInt()
                    ?: e.precipitationProbability6h?.toInt()
                    ?: 0,
                precipitation = e.precipitation,
                windSpeed = e.windSpeed,
                windDirection = e.windDirection,
                humidity = e.humidity?.toInt(),
                uvIndex = null, // Not provided by DWD
                cloudCover = e.cloudCover?.toInt(),
                visibility = e.visibility?.let { it / 1000.0 },
                windGusts = e.windGustSpeed,
                sunshineDuration = e.sunshine, // minutes
                surfacePressure = e.pressureMsl ?: e.pressure,
            )
        }
    }

    /**
     * Aggregate hourly entries into daily summaries.
     * Groups by date, computes high/low/dominant weather/max wind/total precip.
     */
    private fun aggregateDaily(entries: List<BsWeatherEntry>): List<DailyConditions> {
        return entries.groupBy { entry ->
            parseTimestamp(entry.timestamp)?.toLocalDate()
        }.filterKeys { it != null }.map { (date, dayEntries) ->
            val temps = dayEntries.mapNotNull { it.temperature }
            val winds = dayEntries.mapNotNull { it.windSpeed }
            val gusts = dayEntries.mapNotNull { it.windGustSpeed }
            val precip = dayEntries.mapNotNull { it.precipitation }
            val precipProb = dayEntries.mapNotNull {
                it.precipitationProbability ?: it.precipitationProbability6h
            }
            val sunshine = dayEntries.mapNotNull { it.sunshine }

            // Dominant weather code = most common non-dry condition, or dry
            val dominantWmo = dayEntries
                .map { BsConditionMapper.toWmoCode(it.condition, it.icon) }
                .filter { it > 0 }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key
                ?: if (dayEntries.any { it.cloudCover?.let { cc -> cc > 60 } == true }) 3 else 0

            val windDirs = dayEntries.mapNotNull { it.windDirection }

            DailyConditions(
                date = date!!,
                weatherCode = WeatherCode.fromCode(dominantWmo),
                temperatureHigh = temps.maxOrNull() ?: 0.0,
                temperatureLow = temps.minOrNull() ?: 0.0,
                precipitationProbability = precipProb.maxOrNull()?.toInt() ?: 0,
                precipitationSum = if (precip.isNotEmpty()) precip.sum() else null,
                sunrise = null,
                sunset = null,
                uvIndexMax = null,
                windSpeedMax = winds.maxOrNull(),
                windDirectionDominant = if (windDirs.isNotEmpty()) {
                    // Circular mean of wind directions
                    val sinSum = windDirs.sumOf { kotlin.math.sin(Math.toRadians(it.toDouble())) }
                    val cosSum = windDirs.sumOf { kotlin.math.cos(Math.toRadians(it.toDouble())) }
                    ((Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)) + 360) % 360).toInt()
                } else null,
                snowfallSum = null, // DWD doesn't separate snow from rain in Bright Sky
                sunshineDuration = if (sunshine.isNotEmpty()) sunshine.sum() else null,
                windGustsMax = gusts.maxOrNull(),
            )
        }.sortedBy { it.date }
    }

    private fun parseTimestamp(ts: String): LocalDateTime? = try {
        OffsetDateTime.parse(ts).toLocalDateTime()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(ts, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTimestampInstant(ts: String): Instant? = try {
        OffsetDateTime.parse(ts).toInstant()
    } catch (_: Exception) {
        null
    }
}

/**
 * Alert adapter for Bright Sky (DWD warnings).
 * Maps DWD alerts to [WeatherAlert] domain model.
 */
@Singleton
class BrightSkyAlertAdapter @Inject constructor(
    private val api: BrightSkyApi,
) {
    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> = runCatching {
        val response = api.getAlerts(latitude, longitude)
        response.alerts.map { alert ->
            WeatherAlert(
                id = alert.alertId.ifBlank { "dwd-${alert.id}" },
                event = alert.eventEn ?: alert.eventDe ?: "Weather Warning",
                headline = alert.headlineEn ?: alert.headlineDe ?: alert.eventEn ?: "DWD Warning",
                description = alert.descriptionEn ?: alert.descriptionDe ?: "",
                instruction = alert.instructionEn ?: alert.instructionDe,
                severity = AlertSeverity.from(alert.severity),
                urgency = AlertUrgency.from(alert.urgency),
                certainty = alert.certainty ?: "",
                senderName = "DWD",
                areaDescription = "",
                effective = alert.effective ?: alert.onset,
                expires = alert.expires,
                response = alert.responseType,
            )
        }
    }.onFailure { Log.w(TAG, "Bright Sky alerts failed", it) }
}
