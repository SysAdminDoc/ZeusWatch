package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
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
        return buildString {
            append("${daily.size}-day forecast. ")
            daily.take(3).forEach { day ->
                val label = WeatherFormatter.formatDayLabel(day.date)
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
}
