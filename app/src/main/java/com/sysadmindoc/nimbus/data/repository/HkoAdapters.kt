package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.HkoApi
import com.sysadmindoc.nimbus.data.api.HkoCurrentReportResponse
import com.sysadmindoc.nimbus.data.api.HkoForecastDay
import com.sysadmindoc.nimbus.data.api.HkoForecastResponse
import com.sysadmindoc.nimbus.data.api.HkoLocalForecastResponse
import com.sysadmindoc.nimbus.data.api.HkoObservationBlock
import com.sysadmindoc.nimbus.data.api.HkoWarningDetail
import com.sysadmindoc.nimbus.data.api.HkoWarningSummary
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.util.SourceLocaleText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HkoAdapter"
private val HONG_KONG_ZONE: ZoneId = ZoneId.of("Asia/Hong_Kong")

/**
 * Forecast adapter for the Hong Kong Observatory Open Data API.
 *
 * HKO publishes territory-wide observations and 9-day forecasts, but not an
 * hourly forecast series. Hourly data is intentionally empty so existing cards
 * degrade to the configured fallback provider when users need hourly detail.
 */
@Singleton
class HkoForecastAdapter @Inject constructor(
    private val api: HkoApi,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = runCatching {
        require(isHongKongCoordinate(latitude, longitude)) {
            "HKO forecast coverage is limited to Hong Kong"
        }

        val language = SourceLocaleText.preferredHkoLanguage()
        val forecast = api.getForecast9Day(lang = language)
        val current = api.getCurrentReport(lang = language)
        val localForecast = api.getLocalForecast(lang = language)

        mapToWeatherData(
            forecast = forecast,
            current = current,
            localForecast = localForecast,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
        )
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w(TAG, "HKO forecast failed", it)
    }

    internal fun mapToWeatherData(
        forecast: HkoForecastResponse,
        current: HkoCurrentReportResponse,
        localForecast: HkoLocalForecastResponse,
        latitude: Double,
        longitude: Double,
        locationName: String?,
    ): WeatherData {
        require(forecast.weatherForecast.isNotEmpty()) { "HKO forecast response had no daily forecast entries" }

        val observationTime = parseHkoTime(current.updateTime)
            ?: parseHkoTime(current.temperature?.recordTime)
            ?: parseHkoTime(forecast.updateTime)
            ?: LocalDateTime.now(HONG_KONG_ZONE)
        val daily = forecast.weatherForecast.mapNotNull(::mapDailyForecast)
        val todayDaily = daily.firstOrNull { !it.date.isBefore(observationTime.toLocalDate()) }
            ?: daily.firstOrNull()
        val temperature = stationValue(current.temperature, "Hong Kong Observatory")
            ?: current.temperature?.data?.firstOrNull()?.value
            ?: todayDaily?.temperatureLow
            ?: 0.0
        val humidity = stationValue(current.humidity, "Hong Kong Observatory")
            ?: current.humidity?.data?.firstOrNull()?.value
            ?: 0.0
        val weatherCode = WeatherCode.fromCode(
            hkoIconToWmo(current.icon.firstOrNull())
                ?: textToWmo(localForecast.forecastDesc)
                ?: textToWmo(forecast.weatherForecast.firstOrNull()?.forecastWeather)
                ?: 0,
        )

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: "Hong Kong",
                region = "Hong Kong",
                country = "HK",
                latitude = latitude,
                longitude = longitude,
                timeZone = HONG_KONG_ZONE.id,
            ),
            current = CurrentConditions(
                temperature = temperature,
                feelsLike = temperature,
                humidity = humidity.toInt().coerceIn(0, 100),
                weatherCode = weatherCode,
                observationTime = observationTime,
                isDay = observationTime.hour in 6..18,
                windSpeed = 0.0,
                windDirection = 0,
                windGusts = null,
                pressure = 0.0,
                uvIndex = parseUvIndex(current.uvindex),
                visibility = null,
                dewPoint = null,
                cloudCover = cloudCoverFor(weatherCode),
                precipitation = current.rainfall?.data?.mapNotNull { it.max }?.maxOrNull() ?: 0.0,
                dailyHigh = todayDaily?.temperatureHigh ?: temperature,
                dailyLow = todayDaily?.temperatureLow ?: temperature,
                sunrise = null,
                sunset = null,
                sourceConditionText = localForecast.forecastDesc.takeUnlessBlank(),
            ),
            hourly = emptyList(),
            daily = daily,
            lastUpdated = parseHkoTime(forecast.updateTime) ?: observationTime,
        )
    }

    private fun mapDailyForecast(day: HkoForecastDay): DailyConditions? {
        val date = parseForecastDate(day.forecastDate) ?: return null
        val weatherCode = WeatherCode.fromCode(
            hkoIconToWmo(day.forecastIcon)
                ?: textToWmo(day.forecastWeather)
                ?: 0,
        )

        return DailyConditions(
            date = date,
            weatherCode = weatherCode,
            temperatureHigh = day.forecastMaxtemp?.value ?: day.forecastMintemp?.value ?: 0.0,
            temperatureLow = day.forecastMintemp?.value ?: day.forecastMaxtemp?.value ?: 0.0,
            precipitationProbability = psrToPercent(day.probabilityOfSignificantRain),
            precipitationSum = null,
            sunrise = null,
            sunset = null,
            uvIndexMax = null,
            windSpeedMax = null,
            windDirectionDominant = null,
            sourceConditionText = day.forecastWeather.takeUnlessBlank(),
        )
    }
}

@Singleton
class HkoAlertAdapter @Inject constructor(
    private val api: HkoApi,
) {
    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> = runCatching {
        require(isHongKongCoordinate(latitude, longitude)) {
            "HKO alert coverage is limited to Hong Kong"
        }

        val language = SourceLocaleText.preferredHkoLanguage()
        val summaries = api.getWarningSummary(lang = language)
        val details = api.getWarningInfo(lang = language).details
        if (details.isNotEmpty()) {
            details.mapIndexed { index, detail -> mapDetail(index, detail, summaries) }
        } else {
            summaries.entries.mapIndexed { index, entry -> mapSummary(index, entry.key, entry.value) }
        }
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w(TAG, "HKO alerts failed", it)
    }

    suspend fun getAlertsDetailed(latitude: Double, longitude: Double): AlertFetchResult =
        getAlerts(latitude, longitude).toFetchResult(WeatherSourceProvider.HKO.name)

    private fun mapDetail(
        index: Int,
        detail: HkoWarningDetail,
        summaries: Map<String, HkoWarningSummary>,
    ): WeatherAlert {
        val summary = summaries[detail.warningStatementCode]
            ?: summaries.entries.firstOrNull { it.value.code == detail.subtype }?.value
        val warningCode = detail.subtype ?: summary?.code ?: detail.warningStatementCode ?: "HKO"
        val event = summary?.name ?: hkoWarningName(detail.warningStatementCode, warningCode)
        val description = detail.contents.joinToString(separator = "\n").ifBlank { event }
        return WeatherAlert(
            id = "hko-$warningCode-${detail.updateTime ?: index}",
            event = event,
            headline = event,
            description = description,
            instruction = null,
            severity = mapSeverity(detail.warningStatementCode, warningCode, summary?.type),
            urgency = mapUrgency(summary?.actionCode),
            certainty = "Observed",
            senderName = "Hong Kong Observatory",
            areaDescription = "Hong Kong",
            effective = summary?.issueTime ?: detail.updateTime,
            expires = summary?.expireTime,
            response = summary?.actionCode,
            coversRequestedLocation = true,
        )
    }

    private fun mapSummary(index: Int, key: String, summary: HkoWarningSummary): WeatherAlert {
        val warningCode = summary.code ?: key
        val event = summary.name ?: hkoWarningName(key, warningCode)
        return WeatherAlert(
            id = "hko-$warningCode-${summary.updateTime ?: summary.issueTime ?: index}",
            event = event,
            headline = event,
            description = event,
            instruction = null,
            severity = mapSeverity(key, warningCode, summary.type),
            urgency = mapUrgency(summary.actionCode),
            certainty = "Observed",
            senderName = "Hong Kong Observatory",
            areaDescription = "Hong Kong",
            effective = summary.issueTime ?: summary.updateTime,
            expires = summary.expireTime,
            response = summary.actionCode,
            coversRequestedLocation = true,
        )
    }
}

internal fun isHongKongCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude in 22.10..22.60 && longitude in 113.80..114.55

private fun parseHkoTime(value: String?): LocalDateTime? = try {
    value?.takeIf { it.isNotBlank() }
        ?.let { OffsetDateTime.parse(it).atZoneSameInstant(HONG_KONG_ZONE).toLocalDateTime() }
} catch (_: Exception) {
    null
}

private fun parseForecastDate(value: String?): LocalDate? = try {
    value?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }
} catch (_: Exception) {
    null
}

private fun stationValue(block: HkoObservationBlock?, place: String): Double? =
    block?.data?.firstOrNull { it.place.equals(place, ignoreCase = true) }?.value

/**
 * Maps HKO forecast icon codes to WMO weather codes. Covers the full
 * documented icon set — icons must resolve here rather than through
 * [textToWmo], whose English keywords never match on Chinese-locale
 * responses and previously left unmapped icons rendering as clear sky.
 */
private fun hkoIconToWmo(icon: Int?): Int? = when (icon) {
    50, 70 -> 0
    51, 71 -> 1
    52, 72 -> 2
    53, 54 -> 80
    60, 61 -> 3
    62 -> 61
    63 -> 63
    64 -> 65
    65 -> 95
    // Night variants: 73-75 fine (moon phases), 76 mainly cloudy, 77 mainly fine.
    73, 74, 75 -> 0
    76 -> 3
    77 -> 1
    // 80-85: wind / humidity / visibility descriptors.
    80 -> 2 // windy
    81 -> 0 // dry
    82 -> 2 // humid
    83, 84, 85 -> 45 // fog / mist / haze
    // 90-93: temperature descriptors (hot/warm/cool/cold) carry no cloud
    // information — map conservatively to mainly clear.
    90, 91, 92, 93 -> 1
    else -> null
}

private fun textToWmo(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    val lower = text.lowercase(Locale.ROOT)
    return when {
        "thunder" in lower -> 95
        "heavy showers" in lower || "heavy shower" in lower -> 82
        "heavy rain" in lower -> 65
        "showers" in lower || "shower" in lower -> 80
        "rain" in lower -> 63
        "drizzle" in lower -> 53
        "fog" in lower || "mist" in lower || "haze" in lower -> 45
        "overcast" in lower || "cloudy" in lower -> 3
        "sunny intervals" in lower || "bright periods" in lower -> 2
        "sunny periods" in lower || "mainly fine" in lower || "fine" in lower -> 1
        "sunny" in lower || "clear" in lower -> 0
        else -> null
    }
}

private fun psrToPercent(psr: String?): Int = when (psr?.trim()?.lowercase(Locale.ROOT)) {
    "high" -> 85
    "medium high" -> 70
    "medium" -> 50
    "medium low" -> 35
    "low" -> 20
    else -> 0
}

private fun parseUvIndex(element: JsonElement?): Double {
    return when (element) {
        is JsonPrimitive -> element.doubleOrNull ?: 0.0
        is JsonObject -> {
            val data = element["data"] as? JsonArray
            data?.firstOrNull()
                ?.let { it as? JsonObject }
                ?.get("value")
                ?.let { it as? JsonPrimitive }
                ?.doubleOrNull
                ?: 0.0
        }
        else -> 0.0
    }
}

private fun cloudCoverFor(code: WeatherCode): Int = when {
    code == WeatherCode.CLEAR_SKY -> 0
    code == WeatherCode.MAINLY_CLEAR -> 25
    code == WeatherCode.PARTLY_CLOUDY -> 50
    code.isCloudy || code.isRainy || code.isStormy -> 100
    else -> 0
}

private fun mapSeverity(statementCode: String?, warningCode: String?, type: String?): AlertSeverity {
    val combined = listOfNotNull(statementCode, warningCode, type).joinToString(" ").uppercase(Locale.ROOT)
    return when {
        "WRAINB" in combined || "TC10" in combined || "TC9" in combined || "TSUNAMI" in combined ||
            "WTMW" in combined -> AlertSeverity.EXTREME
        "WRAINR" in combined || "TC8" in combined || "WL" in combined || "WFIRER" in combined ->
            AlertSeverity.SEVERE
        "WRAINA" in combined || "TC3" in combined || "WTS" in combined || "WHOT" in combined ||
            "WCOLD" in combined || "WMSGNL" in combined || "WFIREY" in combined -> AlertSeverity.MODERATE
        "WFROST" in combined || "TC1" in combined -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
    }
}

private fun mapUrgency(actionCode: String?): AlertUrgency = when (actionCode?.uppercase(Locale.ROOT)) {
    "ISSUE", "REISSUE", "EXTEND" -> AlertUrgency.IMMEDIATE
    "UPDATE" -> AlertUrgency.EXPECTED
    "CANCEL" -> AlertUrgency.PAST
    else -> AlertUrgency.IMMEDIATE
}

private fun hkoWarningName(statementCode: String?, warningCode: String?): String = when (statementCode ?: warningCode) {
    "WFIRE" -> "Fire Danger Warning"
    "WFROST" -> "Frost Warning"
    "WHOT" -> "Very Hot Weather Warning"
    "WCOLD" -> "Cold Weather Warning"
    "WMSGNL" -> "Strong Monsoon Signal"
    "WTCSGNL" -> "Tropical Cyclone Warning Signal"
    "WRAIN" -> "Rainstorm Warning Signal"
    "WFNTSA" -> "Special Announcement on Flooding in the Northern New Territories"
    "WL" -> "Landslip Warning"
    "WTMW" -> "Tsunami Warning"
    "WTS" -> "Thunderstorm Warning"
    else -> "HKO Weather Warning"
}

private fun String?.takeUnlessBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
