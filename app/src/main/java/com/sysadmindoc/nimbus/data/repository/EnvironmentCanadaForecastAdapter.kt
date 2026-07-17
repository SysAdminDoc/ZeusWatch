package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.EcccAbbreviatedForecast
import com.sysadmindoc.nimbus.data.api.EcccCurrentConditions
import com.sysadmindoc.nimbus.data.api.EcccFeature
import com.sysadmindoc.nimbus.data.api.EcccForecastEntry
import com.sysadmindoc.nimbus.data.api.EcccHourlyEntry
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaForecastApi
import com.sysadmindoc.nimbus.data.api.flexibleDouble
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.util.SourceLocaleText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
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
 * collection — `https://api.weather.gc.ca/collections/...` (bilingual
 * dashboard schema; see the schema note in [EnvironmentCanadaForecastApi]).
 *
 * Approach:
 *   1. Compute a small bounding box around (lat, lon) — 0.5° radius is
 *      enough to catch the closest reporting city in populated areas;
 *      fallback to 1.5° when the tight box returns no features.
 *   2. Pick the feature whose geometry is closest to (lat, lon).
 *   3. Map currentConditions → CurrentConditions. Pressure arrives in
 *      kPa (convert to hPa for consistency); wind speed is already km/h.
 *   4. Map forecastGroup.forecasts[] (Today/Tonight + day/night pairs)
 *      into DailyConditions by pairing each day's high with its night's
 *      low. Pairing keys on the invariant English `period.value` text so
 *      it is stable regardless of the requested display language.
 *   5. Map hourlyForecastGroup.hourlyForecasts[] (UTC instants) into
 *      location-local HourlyConditions.
 *
 * Display strings (condition, forecast summaries) resolve through the
 * bilingual `{en, fr}` payload by the user's preferred language, while
 * all parsing logic (period pairing, weekday resolution, text→WMO
 * fallback) always reads the English side.
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
        val language = SourceLocaleText.preferredLanguage(SUPPORTED_LANGUAGES)
        val feature = findNearestFeature(latitude, longitude, language)
            ?: error("No ECCC city within 1.5° of ($latitude, $longitude)")
        mapToWeatherData(
            feature = feature,
            requestedLat = latitude,
            requestedLon = longitude,
            locationName = locationName,
            zone = locationZone ?: ZoneId.systemDefault(),
            language = language,
        )
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w(TAG, "ECCC forecast failed", it)
    }

    private suspend fun findNearestFeature(lat: Double, lon: Double, language: String): EcccFeature? {
        val tight = api.getCityWeather(buildBbox(lat, lon, 0.5), lang = language)
        closest(tight.features, lat, lon)?.let { return it }
        val wide = api.getCityWeather(buildBbox(lat, lon, 1.5), lang = language)
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
     * ECCC stamps its metadata with UTC ISO-8601 timestamps, but the rest
     * of the app — and the user — wants the forecast in the location's
     * frame of reference. Project every UTC instant into [zone] before
     * dropping the offset, otherwise daily-period indexing anchors to the
     * UTC date (off by up to one day for western Canada late at night).
     */
    internal fun mapToWeatherData(
        feature: EcccFeature,
        requestedLat: Double,
        requestedLon: Double,
        locationName: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        language: String = SourceLocaleText.preferredLanguage(SUPPORTED_LANGUAGES),
    ): WeatherData {
        val props = feature.properties
            ?: error("ECCC feature missing properties")
        val cc = props.currentConditions
        val forecastEntries = props.forecastGroup?.forecasts.orEmpty()
        val hourlyEntries = props.hourlyForecastGroup?.hourlyForecasts.orEmpty()

        val observationTime = parseInstant(cc?.timestamp?.text("en"), zone)
            ?: parseInstant(props.lastUpdated, zone)
            ?: LocalDateTime.now(zone)
        val today = observationTime.toLocalDate()

        val hourlyList = buildHourly(hourlyEntries, zone, language)
        val dailyList = buildDailyFromForecastGroup(forecastEntries, hourlyList, today, language)
        val todayDaily = dailyList.firstOrNull { it.date == today }

        val sunrise = parseInstant(props.riseSet?.sunrise?.text("en"), zone)
        val sunset = parseInstant(props.riseSet?.sunset?.text("en"), zone)

        val pressureHpa = cc?.pressure?.value?.double()?.let { it * 10.0 } ?: 0.0 // kPa → hPa
        val visibilityMeters = cc?.visibility?.value?.double()?.let { it * 1000.0 } // km → m
        val temperature = cc?.temperature?.value?.double()
        val currentCode = mapCurrentToWmo(cc)

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: props.name?.text(language) ?: "Unknown",
                region = "Canada",
                country = "CA",
                latitude = requestedLat,
                longitude = requestedLon,
                timeZone = zone.id,
            ),
            current = CurrentConditions(
                temperature = temperature ?: 0.0,
                feelsLike = cc?.humidex?.value?.double()
                    ?: cc?.windChill?.value?.double()
                    ?: temperature
                    ?: 0.0,
                humidity = cc?.relativeHumidity?.value?.int() ?: 0,
                weatherCode = WeatherCode.fromCode(currentCode),
                observationTime = observationTime,
                isDay = isDayByIcon(extractIconCode(cc?.iconCode?.value)),
                windSpeed = cc?.wind?.speed?.value?.double() ?: 0.0,
                windDirection = cc?.wind?.bearing?.value?.int() ?: 0,
                windGusts = cc?.wind?.gust?.value?.double(),
                pressure = pressureHpa,
                uvIndex = 0.0, // current UV not published; hourly carries it
                visibility = visibilityMeters,
                dewPoint = cc?.dewpoint?.value?.double(),
                cloudCover = 0, // text-only in ECCC; leave zeroed
                precipitation = 0.0,
                dailyHigh = todayDaily?.temperatureHigh ?: temperature ?: 0.0,
                dailyLow = todayDaily?.temperatureLow ?: temperature ?: 0.0,
                sunrise = sunrise?.toString(),
                sunset = sunset?.toString(),
                sourceConditionText = cc?.condition?.text(language),
            ),
            hourly = hourlyList,
            daily = dailyList,
        )
    }

    private fun buildHourly(
        entries: List<EcccHourlyEntry>,
        zone: ZoneId,
        language: String,
    ): List<HourlyConditions> = entries.mapNotNull { entry ->
        val time = parseInstant(entry.timestamp, zone) ?: return@mapNotNull null
        val temperature = entry.temperature?.value?.double() ?: return@mapNotNull null
        val icon = extractIconCode(entry.iconCode?.value)
        HourlyConditions(
            time = time,
            temperature = temperature,
            feelsLike = null,
            weatherCode = WeatherCode.fromCode(
                iconToWmo(icon) ?: conditionTextToWmo(entry.condition?.text("en")),
            ),
            isDay = isDayByIcon(icon),
            precipitationProbability = entry.lop?.value?.int() ?: 0,
            precipitation = null,
            windSpeed = entry.wind?.speed?.value?.double(),
            windDirection = compassToDegrees(entry.wind?.direction?.value?.text("en")),
            humidity = null,
            uvIndex = entry.uv?.indexValue(),
            cloudCover = null,
            visibility = null,
            windGusts = entry.wind?.gust?.value?.double(),
            sourceConditionText = entry.condition?.text(language),
        )
    }

    private fun buildDailyFromForecastGroup(
        forecasts: List<EcccForecastEntry>,
        hourly: List<HourlyConditions>,
        today: LocalDate,
        language: String,
    ): List<DailyConditions> {
        if (forecasts.isEmpty()) return emptyList()
        // ECCC periods alternate day ("Friday") and night ("Friday night").
        // Pair them so each DailyConditions gets both a high (day period)
        // and a low (night period). `period.value` is the invariant
        // English weekday name in both display languages.
        val byDayName = linkedMapOf<String, DayNightPair>()
        forecasts.forEach { entry ->
            val period = entry.period?.value?.text("en")?.trim()
                ?: entry.period?.textForecastName?.text("en")?.trim()
                ?: return@forEach
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
            val highValue = pair.day?.temperatureFor("high")
            val lowValue = pair.night?.temperatureFor("low")
            val leadEntry = pair.day ?: pair.night
            val code = mapAbbreviatedToWmo(leadEntry?.abbreviatedForecast)
            val popHigh = listOfNotNull(
                pair.day?.abbreviatedForecast?.pop?.value?.double(),
                pair.night?.abbreviatedForecast?.pop?.value?.double(),
            ).maxOrNull() ?: hourlyPopFor(hourly, pair.date)

            DailyConditions(
                date = pair.date,
                weatherCode = WeatherCode.fromCode(code),
                temperatureHigh = highValue ?: lowValue ?: 0.0,
                temperatureLow = lowValue ?: highValue ?: 0.0,
                precipitationProbability = popHigh?.toInt() ?: 0,
                precipitationSum = null,
                sunrise = null,
                sunset = null,
                uvIndexMax = pair.day?.uv?.indexValue(),
                windSpeedMax = null,
                windDirectionDominant = null,
                snowfallSum = null,
                sunshineDuration = null,
                windGustsMax = null,
                sourceConditionText = leadEntry?.abbreviatedForecast?.textSummary?.text(language)
                    ?: leadEntry?.textSummary?.text(language),
            )
        }
    }

    /** Max hourly likelihood-of-precipitation for [date], as a POP fallback. */
    private fun hourlyPopFor(hourly: List<HourlyConditions>, date: LocalDate): Double? =
        hourly.filter { it.time.toLocalDate() == date }
            .maxOfOrNull { it.precipitationProbability }
            ?.takeIf { it > 0 }
            ?.toDouble()

    private fun EcccForecastEntry.temperatureFor(tempClass: String): Double? =
        temperatures?.temperature
            ?.firstOrNull { it.tempClass?.text("en").equals(tempClass, ignoreCase = true) }
            ?.value?.double()

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

    private fun parseInstant(timestamp: String?, zone: ZoneId): LocalDateTime? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(timestamp).atZoneSameInstant(zone).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            null
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

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "fr")

        /** 16-wind compass text (English) → bearing degrees. */
        private val COMPASS_DEGREES = mapOf(
            "N" to 0, "NNE" to 22, "NE" to 45, "ENE" to 67,
            "E" to 90, "ESE" to 112, "SE" to 135, "SSE" to 157,
            "S" to 180, "SSW" to 202, "SW" to 225, "WSW" to 247,
            "W" to 270, "WNW" to 292, "NW" to 315, "NNW" to 337,
        )

        internal fun compassToDegrees(text: String?): Int? =
            text?.trim()?.uppercase(java.util.Locale.US)?.let { COMPASS_DEGREES[it] }

        internal fun isDayByIcon(iconCode: Int?): Boolean {
            // ECCC iconCode 0-29 is day; 30+ is night. Default to day when
            // the code is absent so UI icons stay sensible.
            return iconCode == null || iconCode < 30
        }

        /**
         * Map ECCC currentConditions to a WMO weather code.
         * iconCode takes precedence when present; falls back to a pattern
         * match over the ENGLISH `condition` string (locale-invariant).
         */
        internal fun mapCurrentToWmo(cc: EcccCurrentConditions?): Int {
            if (cc == null) return 0
            val byIcon = iconToWmo(extractIconCode(cc.iconCode?.value))
            if (byIcon != null && byIcon >= 0) return byIcon
            return conditionTextToWmo(cc.condition?.text("en"))
        }

        internal fun mapAbbreviatedToWmo(af: EcccAbbreviatedForecast?): Int {
            if (af == null) return 0
            val byIcon = iconToWmo(extractIconCode(af.icon?.value))
            if (byIcon != null && byIcon >= 0) return byIcon
            return conditionTextToWmo(af.textSummary?.text("en"))
        }

        internal fun extractIconCode(element: kotlinx.serialization.json.JsonElement?): Int? =
            flexibleDouble(element)?.toInt()

        internal fun iconToWmo(iconCode: Int?): Int? {
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

        internal fun conditionTextToWmo(text: String?): Int {
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
