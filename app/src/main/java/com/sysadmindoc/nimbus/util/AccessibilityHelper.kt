package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Generates accessible content descriptions for Canvas-drawn composables
 * and complex data displays for TalkBack screen reader support.
 */
object AccessibilityHelper {

    /** Full current conditions description for the hero header area. */
    fun currentConditions(current: CurrentConditions, locationName: String, s: NimbusSettings = NimbusSettings()): String {
        val temp = WeatherFormatter.formatTemperature(current.temperature, s)
        val feelsLike = WeatherFormatter.formatTemperature(current.feelsLike, s)
        val condition = current.weatherCode.description
        val high = WeatherFormatter.formatTemperature(current.dailyHigh, s)
        val low = WeatherFormatter.formatTemperature(current.dailyLow, s)
        return buildString {
            append("$locationName. ")
            append("Currently $temp, $condition. ")
            append("Feels like $feelsLike. ")
            append("High $high, low $low.")
        }
    }

    /** Wind compass Canvas description. */
    fun windCompass(speed: Double, direction: Int, gusts: Double?, s: NimbusSettings = NimbusSettings()): String {
        val dir = WeatherFormatter.formatWindDirection(direction)
        return buildString {
            append("Wind compass. ")
            append("Wind from $dir at ${WeatherFormatter.formatWindSpeed(speed, s)}. ")
            if (gusts != null && gusts > speed) {
                append("Gusts up to ${WeatherFormatter.formatWindSpeed(gusts, s)}.")
            }
        }
    }

    /** Temperature graph Canvas description. */
    fun temperatureGraph(hourly: List<HourlyConditions>, s: NimbusSettings = NimbusSettings()): String {
        val data = hourly.take(24)
        if (data.size < 2) return "Temperature trend graph, insufficient data."
        val temps = data.map { it.temperature }
        val high = WeatherFormatter.formatTemperature(temps.max(), s)
        val low = WeatherFormatter.formatTemperature(temps.min(), s)
        val current = WeatherFormatter.formatTemperature(temps.first(), s)
        return "24-hour temperature trend. " +
            "Ranges from $low to $high. " +
            "Currently $current."
    }

    /** UV index bar Canvas description. */
    fun uvIndex(uvIndex: Double): String {
        val level = WeatherFormatter.uvDescription(uvIndex)
        return "UV index ${uvIndex.toInt()}, $level."
    }

    /** UV index bar description with forecast context. */
    fun uvIndex(
        uvIndex: Double,
        peakTime: LocalDateTime?,
        peakUv: Double?,
        referenceTime: LocalDateTime?,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        val level = WeatherFormatter.uvDescription(uvIndex)
        return buildString {
            append("UV index ${uvIndex.toInt()}, $level. ")
            if (peakTime != null && peakUv != null && peakUv > uvIndex) {
                append("Peaks at ${WeatherFormatter.formatRelativeHourLabel(peakTime, referenceTime, s)} ")
                append("with UV ${peakUv.toInt()}. ")
            }
            if (uvIndex >= 1) {
                val safeMinutes = (200.0 / (uvIndex * 3.0)).toInt().coerceIn(5, 120)
                append("Estimated safe sun exposure without SPF: $safeMinutes minutes.")
            }
        }.trim()
    }

    /** Humidity gauge description. */
    fun humidity(humidity: Int, dewPoint: Double?, s: NimbusSettings = NimbusSettings()): String {
        return buildString {
            append("Humidity gauge. $humidity percent, ${humidityComfortLabel(humidity)}. ")
            dewPoint?.let {
                append("Dew point ${WeatherFormatter.formatDewPoint(it, s)}, ")
                append("${WeatherFormatter.dewPointComfort(it).lowercase(Locale.getDefault())}.")
            }
        }.trim()
    }

    /** Rain next-hour bar chart description. */
    fun nowcast(
        data: List<MinutelyPrecipitation>,
        referenceTime: LocalDateTime?,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        val filtered = nextNowcastEntries(data, referenceTime)
        if (filtered.isEmpty()) return "Rain next hour chart unavailable."

        val firstRainIdx = filtered.indexOfFirst { it.precipitation > RAIN_THRESHOLD_MM }
        val lastRainIdx = filtered.indexOfLast { it.precipitation > RAIN_THRESHOLD_MM }
        val summary = when {
            firstRainIdx < 0 -> "No precipitation expected."
            firstRainIdx == 0 && lastRainIdx >= filtered.size - 2 -> "Rain continuing."
            firstRainIdx == 0 -> "Rain ending in about ${(lastRainIdx + 1) * 15} minutes."
            else -> "Rain starting in about ${firstRainIdx * 15} minutes."
        }
        val peak = filtered.maxByOrNull { it.precipitation }
        return buildString {
            append("Rain next hour chart. $summary")
            if (peak != null && peak.precipitation > 0.0) {
                append(" Peak ${WeatherFormatter.formatPrecipitation(peak.precipitation, s)} ")
                append("at ${WeatherFormatter.formatRelativeHourLabel(peak.time, referenceTime, s)}.")
            }
        }
    }

    /** Outdoor score gauge and factor breakdown description. */
    fun outdoorScore(
        score: Int,
        tempCelsius: Double,
        humidity: Int,
        windKmh: Double,
        uvIndex: Double,
        precipProbability: Int,
    ): String {
        val tempScore = factorScore(tempCelsius, 15.0, 25.0, 5.0, 35.0)
        val windScore = (100 - (windKmh / 50.0 * 100).toInt()).coerceIn(0, 100)
        val rainScore = (100 - precipProbability).coerceIn(0, 100)
        val uvScore = (100 - (uvIndex / 11.0 * 100).toInt()).coerceIn(0, 100)
        val humidityScore = factorScore(humidity.toDouble(), 30.0, 60.0, 10.0, 85.0)
        return "Outdoor activity score $score out of 100, ${WeatherFormatter.outdoorScoreLabel(score)}. " +
            "Factor scores: temperature $tempScore, wind $windScore, rain $rainScore, " +
            "UV $uvScore, humidity $humidityScore."
    }

    /** Precipitation forecast bar chart description. */
    fun precipitationForecast(
        hourly: List<HourlyConditions>,
        referenceTime: LocalDateTime?,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        val data = hourly.take(24)
        if (data.isEmpty()) return "Precipitation forecast chart unavailable."
        val maxProb = data.maxOf { it.precipitationProbability }
        val totalPrecip = data.sumOf { it.precipitation ?: 0.0 }
        val peak = data.maxByOrNull { it.precipitationProbability }
        return buildString {
            append("Precipitation forecast chart. ")
            if (maxProb == 0) {
                append("No rain expected.")
            } else {
                append("Chance up to $maxProb percent")
                if (peak != null) {
                    append(" at ${WeatherFormatter.formatRelativeHourLabel(peak.time, referenceTime, s)}")
                }
                append(".")
            }
            if (totalPrecip > 0.0) {
                append(" Total accumulation ${WeatherFormatter.formatPrecipitation(totalPrecip, s)}.")
            }
        }
    }

    /** Sun ephemeris arc description. */
    fun sunArc(
        sunrise: String?,
        sunset: String?,
        moonrise: String?,
        moonset: String?,
        referenceTime: LocalDateTime?,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        if (sunrise == null || sunset == null) return "Sun path chart unavailable."
        val sunState = sunState(sunrise, sunset, referenceTime)
        return buildString {
            append("Sun path chart. ")
            append("Sunrise ${WeatherFormatter.formatTime(sunrise, s)}, ")
            append("sunset ${WeatherFormatter.formatTime(sunset, s)}. ")
            append("$sunState. ")
            if (moonrise != null && moonset != null) {
                append("Moonrise ${WeatherFormatter.formatTime(moonrise, s)}, ")
                append("moonset ${WeatherFormatter.formatTime(moonset, s)}.")
            }
        }.trim()
    }

    /** Sunshine duration ring description. */
    fun sunshineDuration(sunshineDurationSeconds: Double, dayLengthMinutes: Long?): String {
        val sunshineText = WeatherFormatter.formatSunshineDuration(sunshineDurationSeconds)
        val maxMinutes = dayLengthMinutes?.coerceAtLeast(1) ?: (14L * 60L)
        val sunshineMinutes = (sunshineDurationSeconds / 60.0).toLong().coerceAtLeast(0L)
        val percentOfDaylight = (sunshineMinutes * 100L / maxMinutes).coerceIn(0L, 100L)
        return "Sunshine duration chart. $sunshineText of sunshine today, " +
            "$percentOfDaylight percent of available daylight."
    }

    /** Visibility scale and trend description. */
    fun visibility(
        visibilityMeters: Double,
        hourly: List<HourlyConditions>,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        val current = WeatherFormatter.formatVisibility(visibilityMeters, s)
        val currentKm = visibilityMeters / 1000.0
        val trendValues = hourly.mapNotNull { it.visibility }.take(24)
        return buildString {
            append("Visibility chart. Current visibility $current, ${visibilityTierLabel(currentKm)}. ")
            if (trendValues.size >= 4) {
                val low = trendValues.min()
                val high = trendValues.max()
                append("Next 24 hours range from ${WeatherFormatter.formatVisibility(low, s)} ")
                append("to ${WeatherFormatter.formatVisibility(high, s)}.")
            }
        }.trim()
    }

    /** Wind forecast line chart description. */
    fun windTrend(
        hourly: List<HourlyConditions>,
        referenceTime: LocalDateTime?,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        val data = hourly.take(24)
        if (data.size < 3) return "Wind forecast chart unavailable."
        val maxWind = data.maxOfOrNull { it.windSpeed ?: 0.0 } ?: 0.0
        val maxGust = data.maxOfOrNull { it.windGusts ?: 0.0 } ?: 0.0
        val peak = data.maxByOrNull { it.windSpeed ?: 0.0 }
        return buildString {
            append("Wind forecast chart. Peak ${WeatherFormatter.formatWindSpeed(maxWind, s)}")
            if (peak != null) {
                append(" at ${WeatherFormatter.formatRelativeHourLabel(peak.time, referenceTime, s)}")
            }
            append(".")
            if (maxGust > maxWind * 1.2) {
                append(" Gusts up to ${WeatherFormatter.formatWindSpeed(maxGust, s)}.")
            }
        }
    }

    /** AQI gauge Canvas description. */
    fun airQuality(data: AirQualityData): String {
        return buildString {
            append("Air quality index ${data.usAqi}, ${data.aqiLevel.label}. ")
            append(data.aqiLevel.advice)
        }
    }

    /** Pollen card description. */
    fun pollenCard(pollen: PollenData): String {
        if (!pollen.hasData) return "No pollen data available."
        val readings = listOf(
            "Alder" to pollen.alder,
            "Birch" to pollen.birch,
            "Grass" to pollen.grass,
            "Mugwort" to pollen.mugwort,
            "Olive" to pollen.olive,
            "Ragweed" to pollen.ragweed,
        ).filter { it.second.level != PollenLevel.NONE }

        return buildString {
            append("Pollen levels. Overall ${pollen.overallLevel.label}. ")
            readings.forEach { (name, reading) ->
                append("$name: ${reading.level.label}. ")
            }
        }
    }

    /** Moon phase Canvas description. */
    fun moonPhase(astronomy: AstronomyData): String {
        return buildString {
            append("${astronomy.moonPhase.label}, ")
            append("${astronomy.moonIllumination.toInt()} percent illuminated. ")
            astronomy.moonrise?.let { append("Moonrise ${WeatherFormatter.formatTime(it)}. ") }
            astronomy.moonset?.let { append("Moonset ${WeatherFormatter.formatTime(it)}. ") }
            astronomy.dayLength?.let { append("Day length $it.") }
        }
    }

    /** Hourly forecast strip description (summarizes key points). */
    fun hourlyForecast(hourly: List<HourlyConditions>, s: NimbusSettings = NimbusSettings()): String {
        val data = hourly.take(12)
        if (data.isEmpty()) return "Hourly forecast unavailable."
        val maxTemp = WeatherFormatter.formatTemperature(data.maxOf { it.temperature }, s)
        val minTemp = WeatherFormatter.formatTemperature(data.minOf { it.temperature }, s)
        val maxPrecip = data.maxOf { it.precipitationProbability }
        return buildString {
            append("Hourly forecast for next ${data.size} hours. ")
            append("Temperatures range from $minTemp to $maxTemp. ")
            if (maxPrecip > 0) {
                append("Precipitation chance up to $maxPrecip percent.")
            }
        }
    }

    /** Daily forecast list description. */
    fun dailyForecast(daily: List<DailyConditions>, s: NimbusSettings = NimbusSettings()): String {
        if (daily.isEmpty()) return "Daily forecast unavailable."
        val referenceDate = daily.firstOrNull()?.date
        return buildString {
            append("${daily.size}-day forecast. ")
            daily.take(3).forEach { day ->
                val label = WeatherFormatter.formatRelativeDayLabel(day.date, referenceDate)
                val high = WeatherFormatter.formatTemperature(day.temperatureHigh, s)
                val low = WeatherFormatter.formatTemperature(day.temperatureLow, s)
                append("$label: ${day.weatherCode.description}, ")
                append("high $high, low $low. ")
            }
            if (daily.size > 3) append("And ${daily.size - 3} more days.")
        }
    }

    /** Weather details grid description. */
    fun detailsGrid(current: CurrentConditions, s: NimbusSettings = NimbusSettings()): String {
        return buildString {
            append("Weather details. ")
            append("Humidity ${current.humidity} percent. ")
            append("Wind ${WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s)}. ")
            current.windGusts?.let { if (it > current.windSpeed) append("Gusts ${WeatherFormatter.formatWindSpeed(it, s)}. ") }
            append("Pressure ${WeatherFormatter.formatPressure(current.pressure, s)}. ")
            append("UV index ${current.uvIndex.toInt()}. ")
            current.visibility?.let { append("Visibility ${WeatherFormatter.formatVisibility(it, s)}. ") }
            current.dewPoint?.let { append("Dew point ${WeatherFormatter.formatDewPoint(it, s)}. ") }
            current.sunrise?.let { append("Sunrise ${WeatherFormatter.formatTime(it, s)}. ") }
            current.sunset?.let { append("Sunset ${WeatherFormatter.formatTime(it, s)}.") }
        }
    }

    /** Full weather summary for share functionality. */
    fun weatherSummary(data: WeatherData, s: NimbusSettings = NimbusSettings()): String {
        return buildString {
            appendLine("Weather for ${data.location.name}")
            appendLine("${WeatherFormatter.formatTemperature(data.current.temperature, s)} - ${data.current.weatherCode.description}")
            appendLine("Feels like ${WeatherFormatter.formatTemperature(data.current.feelsLike, s)}")
            appendLine("High ${WeatherFormatter.formatTemperature(data.current.dailyHigh, s)} / Low ${WeatherFormatter.formatTemperature(data.current.dailyLow, s)}")
            appendLine("Wind: ${WeatherFormatter.formatWindSpeed(data.current.windSpeed, data.current.windDirection, s)}")
            appendLine("Humidity: ${data.current.humidity}%")
        }.trimEnd()
    }

    private fun humidityComfortLabel(humidity: Int): String = when {
        humidity < 25 -> "very dry"
        humidity < 30 -> "dry"
        humidity in 30..60 -> "comfortable"
        humidity in 61..70 -> "slightly humid"
        humidity in 71..85 -> "humid"
        else -> "very humid"
    }

    private fun nextNowcastEntries(
        data: List<MinutelyPrecipitation>,
        referenceTime: LocalDateTime?,
    ): List<MinutelyPrecipitation> {
        val now = referenceTime ?: return data.take(NOWCAST_ENTRY_LIMIT)
        return data.filter { it.time.isAfter(now.minusMinutes(5)) }.take(NOWCAST_ENTRY_LIMIT)
    }

    private fun factorScore(
        value: Double,
        idealLow: Double,
        idealHigh: Double,
        okLow: Double,
        okHigh: Double,
    ): Int = when {
        value in idealLow..idealHigh -> 100
        value in okLow..okHigh -> 60
        else -> 20
    }

    private fun sunState(sunrise: String, sunset: String, referenceTime: LocalDateTime?): String {
        return try {
            val rise = LocalDateTime.parse(sunrise)
            val set = LocalDateTime.parse(sunset)
            val now = referenceTime ?: LocalDateTime.now()
            when {
                now.isBefore(rise) -> "Before sunrise"
                now.isBefore(set) -> "Daylight"
                else -> "After sunset"
            }
        } catch (_: Exception) {
            "Sun position unavailable"
        }
    }

    private fun visibilityTierLabel(km: Double): String = when {
        km < 1.0 -> "very poor"
        km < 4.0 -> "poor"
        km < 10.0 -> "moderate"
        km < 20.0 -> "good"
        km < 40.0 -> "clear"
        else -> "perfectly clear"
    }

    private const val NOWCAST_ENTRY_LIMIT = 8
    private const val RAIN_THRESHOLD_MM = 0.05
}
