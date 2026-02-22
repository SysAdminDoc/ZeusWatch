package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.data.model.WeatherData
import java.time.format.TextStyle
import java.util.Locale

/**
 * Generates accessible content descriptions for Canvas-drawn composables
 * and complex data displays for TalkBack screen reader support.
 */
object AccessibilityHelper {

    /** Full current conditions description for the hero header area. */
    fun currentConditions(current: CurrentConditions, locationName: String): String {
        val temp = current.temperature.toInt()
        val feelsLike = current.feelsLike.toInt()
        val condition = current.weatherCode.description
        val high = current.dailyHigh.toInt()
        val low = current.dailyLow.toInt()
        return buildString {
            append("$locationName. ")
            append("Currently $temp degrees, $condition. ")
            append("Feels like $feelsLike degrees. ")
            append("High $high, low $low.")
        }
    }

    /** Wind compass Canvas description. */
    fun windCompass(speed: Double, direction: Int, gusts: Double?): String {
        val dir = WeatherFormatter.formatWindDirection(direction)
        return buildString {
            append("Wind compass. ")
            append("Wind from $dir at ${speed.toInt()} miles per hour. ")
            if (gusts != null && gusts > speed) {
                append("Gusts up to ${gusts.toInt()} miles per hour.")
            }
        }
    }

    /** Temperature graph Canvas description. */
    fun temperatureGraph(hourly: List<HourlyConditions>): String {
        val data = hourly.take(24)
        if (data.size < 2) return "Temperature trend graph, insufficient data."
        val temps = data.map { it.temperature }
        val high = temps.max().toInt()
        val low = temps.min().toInt()
        return "24-hour temperature trend. " +
            "Ranges from $low to $high degrees. " +
            "Currently ${temps.first().toInt()} degrees."
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
    fun hourlyForecast(hourly: List<HourlyConditions>): String {
        val data = hourly.take(12)
        if (data.isEmpty()) return "Hourly forecast unavailable."
        val first = data.first()
        val maxTemp = data.maxOf { it.temperature }.toInt()
        val minTemp = data.minOf { it.temperature }.toInt()
        val maxPrecip = data.maxOf { it.precipitationProbability }
        return buildString {
            append("Hourly forecast for next ${data.size} hours. ")
            append("Temperatures range from $minTemp to $maxTemp degrees. ")
            if (maxPrecip > 0) {
                append("Precipitation chance up to $maxPrecip percent.")
            }
        }
    }

    /** Daily forecast list description. */
    fun dailyForecast(daily: List<DailyConditions>): String {
        if (daily.isEmpty()) return "Daily forecast unavailable."
        return buildString {
            append("${daily.size}-day forecast. ")
            daily.take(3).forEach { day ->
                val label = WeatherFormatter.formatDayLabel(day.date)
                append("$label: ${day.weatherCode.description}, ")
                append("high ${day.temperatureHigh.toInt()}, low ${day.temperatureLow.toInt()}. ")
            }
            if (daily.size > 3) append("And ${daily.size - 3} more days.")
        }
    }

    /** Weather details grid description. */
    fun detailsGrid(current: CurrentConditions): String {
        return buildString {
            append("Weather details. ")
            append("Humidity ${current.humidity} percent. ")
            append("Wind ${WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection)}. ")
            append("Pressure ${WeatherFormatter.formatPressure(current.pressure)}. ")
            append("UV index ${current.uvIndex.toInt()}. ")
            current.visibility?.let { append("Visibility ${WeatherFormatter.formatVisibility(it)}. ") }
            current.dewPoint?.let { append("Dew point ${it.toInt()} degrees. ") }
            current.sunrise?.let { append("Sunrise ${WeatherFormatter.formatTime(it)}. ") }
            current.sunset?.let { append("Sunset ${WeatherFormatter.formatTime(it)}.") }
        }
    }

    /** Full weather summary for share functionality. */
    fun weatherSummary(data: WeatherData): String {
        return buildString {
            appendLine("Weather for ${data.location.name}")
            appendLine("${data.current.temperature.toInt()}\u00B0 - ${data.current.weatherCode.description}")
            appendLine("Feels like ${data.current.feelsLike.toInt()}\u00B0")
            appendLine("High ${data.current.dailyHigh.toInt()}\u00B0 / Low ${data.current.dailyLow.toInt()}\u00B0")
            appendLine("Wind: ${WeatherFormatter.formatWindSpeed(data.current.windSpeed, data.current.windDirection)}")
            appendLine("Humidity: ${data.current.humidity}%")
        }.trimEnd()
    }
}
