package com.sysadmindoc.nimbus.util

import android.content.Context
import android.content.Intent
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings

object ShareWeatherHelper {

    fun buildShareText(
        data: WeatherData,
        airQuality: AirQualityData? = null,
        s: NimbusSettings = NimbusSettings(),
    ): String = buildString {
        val location = data.location
        val current = data.current

        appendLine("--- ZeusWatch ---")
        appendLine("${location.name}${if (location.region.isNotBlank()) ", ${location.region}" else ""}")
        appendLine()

        appendLine("Now: ${WeatherFormatter.formatTemperatureUnit(current.temperature, s)} ${current.weatherCode.description}")
        appendLine("Feels like ${WeatherFormatter.formatTemperature(current.feelsLike, s)}")
        appendLine("High ${WeatherFormatter.formatTemperature(current.dailyHigh, s)} / Low ${WeatherFormatter.formatTemperature(current.dailyLow, s)}")
        appendLine()

        appendLine("Wind: ${WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s)}")
        appendLine("Humidity: ${current.humidity}%")
        appendLine("UV Index: ${current.uvIndex.toInt()} (${WeatherFormatter.uvDescription(current.uvIndex)})")
        appendLine("Pressure: ${WeatherFormatter.formatPressure(current.pressure, s)}")
        current.visibility?.let { appendLine("Visibility: ${WeatherFormatter.formatVisibility(it, s)}") }
        appendLine()

        airQuality?.let { aq ->
            appendLine("Air Quality: ${aq.usAqi} ${aq.aqiLevel.label}")
        }

        if (data.daily.isNotEmpty()) {
            appendLine("--- Forecast ---")
            data.daily.take(3).forEach { day ->
                val label = WeatherFormatter.formatDayLabel(day.date)
                val desc = day.weatherCode.description
                val hi = WeatherFormatter.formatTemperature(day.temperatureHigh, s)
                val lo = WeatherFormatter.formatTemperature(day.temperatureLow, s)
                val precip = day.precipitationProbability
                append("$label: $desc $hi/$lo")
                if (precip > 0) append(" ($precip% precip)")
                appendLine()
            }
        }

        appendLine()
        append("Shared via ZeusWatch")
    }.trimEnd()

    fun share(context: Context, data: WeatherData, airQuality: AirQualityData? = null, s: NimbusSettings = NimbusSettings()) {
        val text = buildShareText(data, airQuality, s)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Weather for ${data.location.name}")
        }
        context.startActivity(
            Intent.createChooser(intent, "Share weather").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
