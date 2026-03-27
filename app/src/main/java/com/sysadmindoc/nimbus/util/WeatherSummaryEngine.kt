package com.sysadmindoc.nimbus.util

import android.util.Log
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SummaryStyle

private const val TAG = "WeatherSummaryEngine"

/**
 * Template-based natural language weather summary generator.
 * Produces human-readable sentences like:
 * "Partly cloudy today with afternoon showers likely. Highs near 78."
 *
 * When [SummaryStyle.AI_GENERATED] is requested, delegates to the [SummaryEngine] implementation
 * with automatic fallback to the template engine if AI is unavailable.
 */
object WeatherSummaryEngine {

    /**
     * Generate a template-based weather summary (synchronous).
     * This is the default path and always works on every device.
     */
    fun generate(
        current: CurrentConditions,
        today: DailyConditions?,
        hourly: List<HourlyConditions>,
        yesterdayHigh: Double? = null,
        s: NimbusSettings = NimbusSettings(),
    ): String {
        val parts = mutableListOf<String>()

        // Opening: time-of-day greeting + condition
        val hour = java.time.LocalTime.now().hour
        val timeOfDay = when {
            !current.isDay -> "tonight"
            hour < 12 -> "this morning"
            hour < 17 -> "this afternoon"
            else -> "this evening"
        }
        val conditionPhrase = conditionPhrase(current.weatherCode, current.isDay)
        parts.add("$conditionPhrase $timeOfDay")

        // Precipitation outlook from hourly
        val precipOutlook = precipitationOutlook(hourly)
        if (precipOutlook != null) {
            parts.add(precipOutlook)
        }

        // Wind note for significant wind
        if (current.windSpeed > 30) {
            val windDesc = when {
                current.windSpeed > 60 -> "strong winds"
                current.windSpeed > 40 -> "breezy conditions"
                else -> "light winds"
            }
            parts.add("with $windDesc")
        }

        // UV warning
        if (current.isDay && current.uvIndex >= 8) {
            parts.add("UV index is very high")
        } else if (current.isDay && current.uvIndex >= 6) {
            parts.add("UV index is high")
        }

        // Humidity warning
        if (current.humidity >= 80 && current.temperature > 25) {
            parts.add("and it will feel muggy")
        }

        // Temperature sentence
        val tempStr = WeatherFormatter.formatTemperature(
            if (current.isDay) (today?.temperatureHigh ?: current.temperature) else (today?.temperatureLow ?: current.temperature),
            s,
        )
        val tempLabel = if (current.isDay) "Highs" else "Lows"
        val tempSentence = "$tempLabel near $tempStr."

        // Yesterday comparison (convert to user's unit for correct degree diff)
        val comparisonStr = if (yesterdayHigh != null && today != null) {
            val todayConverted = WeatherFormatter.convertedTemp(today.temperatureHigh, s)
            val yesterdayConverted = WeatherFormatter.convertedTemp(yesterdayHigh, s)
            val diff = (todayConverted - yesterdayConverted).toInt()
            when {
                diff > 2 -> " ${diff}\u00B0 warmer than yesterday."
                diff < -2 -> " ${-diff}\u00B0 cooler than yesterday."
                else -> ""
            }
        } else ""

        return parts.joinToString(" ").replaceFirstChar { it.uppercase() } + ". " + tempSentence + comparisonStr
    }

    private fun conditionPhrase(code: WeatherCode, isDay: Boolean): String = when (code) {
        WeatherCode.CLEAR_SKY -> if (isDay) "Clear skies" else "Clear skies"
        WeatherCode.MAINLY_CLEAR -> if (isDay) "Mostly sunny" else "Mostly clear"
        WeatherCode.PARTLY_CLOUDY -> "Partly cloudy"
        WeatherCode.OVERCAST -> "Overcast skies"
        WeatherCode.FOG, WeatherCode.DEPOSITING_RIME_FOG -> "Foggy conditions"
        WeatherCode.DRIZZLE_LIGHT -> "Light drizzle"
        WeatherCode.DRIZZLE_MODERATE -> "Drizzle"
        WeatherCode.DRIZZLE_DENSE -> "Steady drizzle"
        WeatherCode.FREEZING_DRIZZLE_LIGHT, WeatherCode.FREEZING_DRIZZLE_DENSE -> "Freezing drizzle"
        WeatherCode.RAIN_SLIGHT -> "Light rain"
        WeatherCode.RAIN_MODERATE -> "Rainy"
        WeatherCode.RAIN_HEAVY -> "Heavy rain"
        WeatherCode.FREEZING_RAIN_LIGHT, WeatherCode.FREEZING_RAIN_HEAVY -> "Freezing rain"
        WeatherCode.SNOW_SLIGHT -> "Light snow"
        WeatherCode.SNOW_MODERATE -> "Snowy"
        WeatherCode.SNOW_HEAVY -> "Heavy snow"
        WeatherCode.SNOW_GRAINS -> "Snow grains"
        WeatherCode.RAIN_SHOWERS_SLIGHT -> "Passing showers"
        WeatherCode.RAIN_SHOWERS_MODERATE -> "Scattered showers"
        WeatherCode.RAIN_SHOWERS_VIOLENT -> "Heavy downpours"
        WeatherCode.SNOW_SHOWERS_SLIGHT -> "Light snow showers"
        WeatherCode.SNOW_SHOWERS_HEAVY -> "Heavy snow showers"
        WeatherCode.THUNDERSTORM -> "Thunderstorms"
        WeatherCode.THUNDERSTORM_HAIL_SLIGHT -> "Thunderstorms with hail"
        WeatherCode.THUNDERSTORM_HAIL_HEAVY -> "Severe thunderstorms with hail"
        WeatherCode.UNKNOWN -> "Variable conditions"
    }

    private fun precipitationOutlook(hourly: List<HourlyConditions>): String? {
        val next12 = hourly.take(12)
        if (next12.isEmpty()) return null

        val rainyHours = next12.count { it.precipitationProbability > 40 }
        val maxProb = next12.maxOfOrNull { it.precipitationProbability } ?: 0

        // Find first rainy hour
        val firstRainIdx = next12.indexOfFirst { it.precipitationProbability > 40 }

        return when {
            maxProb < 20 -> null // No rain expected
            rainyHours >= 8 -> "with rain likely throughout the day"
            rainyHours >= 4 && firstRainIdx <= 2 -> "with rain expected this morning"
            rainyHours >= 4 && firstRainIdx > 6 -> "with rain developing this evening"
            rainyHours >= 4 -> "with rain likely this afternoon"
            rainyHours >= 1 && maxProb > 60 -> "with a chance of showers"
            rainyHours >= 1 -> "with a slight chance of rain"
            else -> null
        }
    }

    /**
     * Generate a weather summary respecting the user's [SummaryStyle] preference.
     *
     * - [SummaryStyle.TEMPLATE]: returns the template summary synchronously (wrapped in suspend).
     * - [SummaryStyle.AI_GENERATED]: attempts Gemini Nano on-device generation, falling back
     *   to the template engine if the device doesn't support it or generation fails.
     */
    suspend fun generateWithStyle(
        current: CurrentConditions,
        today: DailyConditions?,
        hourly: List<HourlyConditions>,
        yesterdayHigh: Double? = null,
        s: NimbusSettings = NimbusSettings(),
        aiEngine: SummaryEngine? = null,
    ): String {
        // Always compute the template fallback first (cheap)
        val templateSummary = generate(current, today, hourly, yesterdayHigh, s)

        if (s.summaryStyle != SummaryStyle.AI_GENERATED || aiEngine == null) {
            return templateSummary
        }

        // Attempt AI generation with formatted display values
        return try {
            val currentTemp = WeatherFormatter.formatTemperatureUnit(current.temperature, s)
            val condition = conditionPhrase(current.weatherCode, current.isDay)
            val high = WeatherFormatter.formatTemperatureUnit(
                today?.temperatureHigh ?: current.dailyHigh, s,
            )
            val low = WeatherFormatter.formatTemperatureUnit(
                today?.temperatureLow ?: current.dailyLow, s,
            )
            val wind = WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s)
            val precipChance = today?.precipitationProbability ?: 0

            val aiSummary = aiEngine.generate(
                currentTemp = currentTemp,
                condition = condition,
                high = high,
                low = low,
                humidity = current.humidity,
                windSpeed = wind,
                precipChance = precipChance,
                uvIndex = current.uvIndex,
            )

            aiSummary ?: templateSummary
        } catch (e: Exception) {
            Log.w(TAG, "AI summary failed, using template fallback: ${e.message}")
            templateSummary
        }
    }
}
