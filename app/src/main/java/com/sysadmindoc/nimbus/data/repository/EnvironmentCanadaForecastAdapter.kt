package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.EcccAbbreviatedForecast
import com.sysadmindoc.nimbus.data.api.EcccCurrentConditions
import com.sysadmindoc.nimbus.data.api.EcccFeature
import com.sysadmindoc.nimbus.data.api.EcccForecastEntry
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaForecastApi
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.pow

private const val TAG = "EcccForecastAdapter"

/**
 * Forecast adapter for Environment and Climate Change Canada.
 *
 * Data source: MSC GeoMet OGC API Features `citypageweather-realtime`
 * collection — `https://api.weather.gc.ca/collections/...`. Data is
 * derived from the published citypageweather XML product.
 *
 * Approach:
 *   1. Compute a small bounding box around (lat, lon) — 0.5° radius is
 *      enough to catch the closest reporting city in populated areas;
 *      fallback to 1.5° when the tight box returns no features.
 *   2. Pick the feature whose geometry is closest to (lat, lon).
 *   3. Map currentConditions → CurrentConditions. Pressure arrives in
 *      kPa (convert to hPa for consistency); wind speed is already km/h.
 *   4. Map forecastGroup.forecast[] (usually Today + Tonight + next
 *      5-6 day/night pairs) into DailyConditions by pairing each day's
 *      high with its night's low.
 *   5. No hourly — the free ECCC OGC collection doesn't publish hourly.
 *      Return an empty list; UI cards that need hourly degrade gracefully.
 */
@Singleton
class EnvironmentCanadaForecastAdapter @Inject constructor(
    private val api: EnvironmentCanadaForecastApi,
) {

    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationZone: ZoneId? = null,
    ): Result<WeatherData> = runCatching {
        val feature = findNearestFeature(latitude, longitude)
            ?: error("No ECCC city within 1.5° of ($latitude, $longitude)")
        mapToWeatherData(feature, latitude, longitude, locationName, locationZone ?: ZoneId.systemDefault())
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w(TAG, "ECCC forecast failed", it)
    }

    private suspend fun findNearestFeature(lat: Double, lon: Double): EcccFeature? {
        val tight = api.getCityWeather(buildBbox(lat, lon, 0.5))
        closest(tight.features, lat, lon)?.let { return it }
        val wide = api.getCityWeather(buildBbox(lat, lon, 1.5))
        return closest(wide.features, lat, lon)
    }

    private fun closest(features: List<EcccFeature>, lat: Double, lon: Double): EcccFeature? {
        // A degree of longitude shrinks with cos(latitude) — at Canadian
        // latitudes it's roughly half a degree of latitude. Weight Δlon
        // accordingly so the "nearest" city is nearest on the ground, not
        // in raw degree space.
        val lonScale = cos(Math.toRadians(lat))
        return features
            .mapNotNull { feat ->
                val coords = feat.geometry?.coordinates?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                feat to coords
            }
            .minByOrNull { feat ->
                val coords = feat.second
                val fLon = coords[0]
                val fLat = coords[1]
                (fLat - lat).pow(2) + ((fLon - lon) * lonScale).pow(2)
            }?.first
    }

    /**
     * ECCC stamps its observation/forecast metadata with `…Utc` ISO-8601
     * timestamps, but the rest of the app — and the user — wants the
     * forecast in their local frame of reference. Project the UTC instant
     * into [zone] before dropping the offset, otherwise the daily-period
     * indexing in [buildDailyFromForecastGroup] anchors to the UTC date
     * (off by up to one day for western Canadian timezones late at night).
     */
    internal fun mapToWeatherData(
        feature: EcccFeature,
        requestedLat: Double,
        requestedLon: Double,
        locationName: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeatherData {
        val props = feature.properties
            ?: error("ECCC feature missing properties")
        val cc = props.currentConditions
        val forecastEntries = props.forecastGroup?.forecast.orEmpty()

        val observationTime = parseObservation(cc?.observationDateTimeUtc, zone)
            ?: parseObservation(props.timestampUtc, zone)
            ?: LocalDateTime.now(zone)
        val today = observationTime.toLocalDate()

        val pressureHpa = cc?.pressureValue?.let { it * 10.0 } ?: 0.0 // kPa → hPa
        val visibilityMeters = cc?.visibilityValue?.let { it * 1000.0 } // km → m
        val currentCode = mapCurrentToWmo(cc)

        val dailyList = buildDailyFromForecastGroup(forecastEntries, today)
        val todayDaily = dailyList.firstOrNull { it.date == today }

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: props.cityEn ?: props.nameEn ?: "Unknown",
                region = "Canada",
                country = "CA",
                latitude = requestedLat,
                longitude = requestedLon,
                timeZone = zone.id,
            ),
            current = CurrentConditions(
                temperature = cc?.temperatureValue ?: 0.0,
                feelsLike = cc?.humidexValue
                    ?: cc?.windChillValue
                    ?: cc?.temperatureValue
                    ?: 0.0,
                humidity = cc?.relativeHumidityValue?.toInt() ?: 0,
                weatherCode = WeatherCode.fromCode(currentCode),
                observationTime = observationTime,
                isDay = isDayByIcon(cc?.iconCode),
                windSpeed = cc?.windSpeedValue ?: 0.0,
                windDirection = cc?.windBearingValue?.toInt() ?: 0,
                windGusts = cc?.windGustValue,
                pressure = pressureHpa,
                uvIndex = 0.0, // not published via citypageweather-realtime
                visibility = visibilityMeters,
                dewPoint = cc?.dewpointValue,
                cloudCover = 0, // text-only in ECCC; leave zeroed
                precipitation = 0.0,
                dailyHigh = todayDaily?.temperatureHigh ?: cc?.temperatureValue ?: 0.0,
                dailyLow = todayDaily?.temperatureLow ?: cc?.temperatureValue ?: 0.0,
                sunrise = null,
                sunset = null,
            ),
            hourly = emptyList<HourlyConditions>(),
            daily = dailyList,
        )
    }

    private fun buildDailyFromForecastGroup(
        forecasts: List<EcccForecastEntry>,
        today: LocalDate,
    ): List<DailyConditions> {
        if (forecasts.isEmpty()) return emptyList()
        // ECCC periods alternate day ("Thursday") and night ("Thursday
        // night"). Pair them so each DailyConditions gets both a high
        // (from the day period) and a low (from the night period).
        val byDayName = linkedMapOf<String, DayNightPair>()
        forecasts.forEach { entry ->
            val period = entry.period?.trim() ?: return@forEach
            val isNight = period.contains("night", ignoreCase = true) ||
                period.equals("Tonight", ignoreCase = true) ||
                period.contains("overnight", ignoreCase = true)
            val dayKey = period
                .replace(" night", "", ignoreCase = true)
                .replace("Tonight", "Today", ignoreCase = true)
                .replace(" overnight", "", ignoreCase = true)
                .trim()
            val slot = byDayName.getOrPut(dayKey) {
                DayNightPair(date = resolveForecastDate(dayKey, today, byDayName.size))
            }
            if (isNight) slot.night = entry else slot.day = entry
        }
        return byDayName.values.take(7).map { pair ->
            val highValue = pair.day?.temperatures
                ?.firstOrNull { it.tempClass.equals("high", ignoreCase = true) }
                ?.value
            val lowValue = pair.night?.temperatures
                ?.firstOrNull { it.tempClass.equals("low", ignoreCase = true) }
                ?.value
            val code = mapAbbreviatedToWmo(pair.day?.abbreviatedForecast)
            val popHigh = listOfNotNull(
                pair.day?.abbreviatedForecast?.pop,
                pair.night?.abbreviatedForecast?.pop,
            ).maxOrNull()

            DailyConditions(
                date = pair.date,
                weatherCode = WeatherCode.fromCode(code),
                temperatureHigh = highValue ?: lowValue ?: 0.0,
                temperatureLow = lowValue ?: highValue ?: 0.0,
                precipitationProbability = popHigh?.toInt() ?: 0,
                precipitationSum = null,
                sunrise = null,
                sunset = null,
                uvIndexMax = null,
                windSpeedMax = null,
                windDirectionDominant = null,
                snowfallSum = null,
                sunshineDuration = null,
                windGustsMax = null,
            )
        }
    }

    private fun resolveForecastDate(periodDay: String, today: LocalDate, fallbackOffset: Int): LocalDate {
        return when (periodDay.lowercase(java.util.Locale.CANADA)) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            else -> {
                val dayOfWeek = DayOfWeek.entries.firstOrNull { day ->
                    periodDay.equals(
                        day.getDisplayName(TextStyle.FULL, java.util.Locale.CANADA),
                        ignoreCase = true,
                    ) || periodDay.equals(
                        day.getDisplayName(TextStyle.SHORT, java.util.Locale.CANADA),
                        ignoreCase = true,
                    )
                }
                dayOfWeek?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
                    ?: today.plusDays(fallbackOffset.toLong())
            }
        }
    }

    private class DayNightPair(
        val date: LocalDate,
        var day: EcccForecastEntry? = null,
        var night: EcccForecastEntry? = null,
    )

    private fun parseObservation(timestamp: String?, zone: ZoneId): LocalDateTime? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(timestamp).atZoneSameInstant(zone).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                // Some ECCC fields use compact YYYYMMDDhhmmss (always UTC).
                if (timestamp.length >= 14) {
                    LocalDateTime.of(
                        timestamp.substring(0, 4).toInt(),
                        timestamp.substring(4, 6).toInt(),
                        timestamp.substring(6, 8).toInt(),
                        timestamp.substring(8, 10).toInt(),
                        timestamp.substring(10, 12).toInt(),
                        timestamp.substring(12, 14).toInt(),
                    ).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun buildBbox(lat: Double, lon: Double, radius: Double): String {
        val minLon = lon - radius
        val maxLon = lon + radius
        val minLat = lat - radius
        val maxLat = lat + radius
        return "%.4f,%.4f,%.4f,%.4f".format(
            java.util.Locale.US,
            minLon, minLat, maxLon, maxLat,
        )
    }

    private fun isDayByIcon(iconCode: JsonElement?): Boolean {
        val numeric = extractIconCode(iconCode) ?: return true
        // ECCC iconCode 0-15 is day; 30-45 is night. Default to day
        // when the code falls outside the documented range so UI icons
        // stay sensible during ambiguous data.
        return numeric < 30
    }

    companion object {
        /**
         * Map ECCC currentConditions to a WMO weather code.
         * iconCode takes precedence when present; falls back to a
         * pattern match over the free-text `condition` string.
         */
        internal fun mapCurrentToWmo(cc: EcccCurrentConditions?): Int {
            if (cc == null) return 0
            val byIcon = iconToWmo(extractIconCode(cc.iconCode))
            if (byIcon != null && byIcon >= 0) return byIcon
            return conditionTextToWmo(cc.condition)
        }

        internal fun mapAbbreviatedToWmo(af: EcccAbbreviatedForecast?): Int {
            if (af == null) return 0
            val byIcon = iconToWmo(extractIconCode(af.iconCode))
            if (byIcon != null && byIcon >= 0) return byIcon
            return conditionTextToWmo(af.textSummary)
        }

        internal fun extractIconCode(element: JsonElement?): Int? {
            if (element == null) return null
            val primitive = element as? JsonPrimitive ?: return null
            primitive.intOrNull?.let { return it }
            return primitive.contentOrNull?.trim()?.trimStart('0')?.ifEmpty { "0" }?.toIntOrNull()
        }

        private fun iconToWmo(iconCode: Int?): Int? {
            if (iconCode == null) return null
            val modulo = iconCode % 30 // ECCC day/night pairs offset by 30
            return when (modulo) {
                0, 1, 2 -> 0                  // sunny / mainly sunny
                3 -> 2                        // partly cloudy
                4, 5 -> 3                     // mostly cloudy / overcast
                6 -> 61                       // light rain
                7 -> 66                       // freezing rain
                8, 9 -> 71                    // light snow / snow showers
                10 -> 3                       // cloudy
                11 -> 51                      // drizzle
                12, 13 -> 65                  // rain / heavy rain
                14, 15 -> 75                  // heavy snow
                16 -> 73                      // snow
                17 -> 77                      // snow grains
                18 -> 66                      // ice pellets
                19, 20 -> 95                  // thunderstorms
                21 -> 45                      // haze
                22 -> 3                       // mist
                23, 24 -> 45                  // fog
                25 -> 66                      // ice crystals
                26 -> 73                      // blowing snow
                27 -> 99                      // blizzard
                28 -> 57                      // freezing drizzle
                else -> 0
            }
        }

        private fun conditionTextToWmo(text: String?): Int {
            if (text.isNullOrBlank()) return 0
            val lower = text.lowercase()
            return CONDITION_TEXT_RULES.firstOrNull { it.matches(lower) }?.code ?: 0
        }

        private val CONDITION_TEXT_RULES = listOf(
            ConditionTextRule(code = 99, allOf = listOf("blizzard")),
            ConditionTextRule(code = 96, allOf = listOf("thunder", "heavy")),
            ConditionTextRule(code = 95, allOf = listOf("thunder")),
            ConditionTextRule(code = 66, allOf = listOf("freezing rain")),
            ConditionTextRule(code = 57, allOf = listOf("freezing drizzle")),
            ConditionTextRule(code = 65, allOf = listOf("heavy rain")),
            ConditionTextRule(code = 80, allOf = listOf("rain shower")),
            ConditionTextRule(code = 63, anyOf = listOf("rain", "showers")),
            ConditionTextRule(code = 53, allOf = listOf("drizzle")),
            ConditionTextRule(code = 75, allOf = listOf("heavy snow")),
            ConditionTextRule(code = 85, allOf = listOf("snow shower")),
            ConditionTextRule(code = 73, anyOf = listOf("snow", "flurries")),
            ConditionTextRule(code = 66, anyOf = listOf("sleet", "ice pellet")),
            ConditionTextRule(code = 45, allOf = listOf("fog")),
            ConditionTextRule(code = 45, anyOf = listOf("haze", "mist")),
            ConditionTextRule(code = 3, allOf = listOf("overcast")),
            ConditionTextRule(code = 3, allOf = listOf("mostly cloudy")),
            ConditionTextRule(code = 2, anyOf = listOf("partly cloudy", "partly sunny")),
            ConditionTextRule(code = 1, anyOf = listOf("mostly sunny", "mainly sunny")),
            ConditionTextRule(code = 0, anyOf = listOf("sunny", "clear")),
        )

        private data class ConditionTextRule(
            val code: Int,
            val allOf: List<String> = emptyList(),
            val anyOf: List<String> = emptyList(),
        ) {
            fun matches(text: String): Boolean =
                allOf.all { it in text } && (anyOf.isEmpty() || anyOf.any { it in text })
        }
    }
}
