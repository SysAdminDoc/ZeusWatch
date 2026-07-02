package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SummaryStyle
import java.util.Locale

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
    private val daytimeConditionPhrases = mapOf(
        WeatherCode.CLEAR_SKY to "Clear skies",
        WeatherCode.MAINLY_CLEAR to "Mostly sunny",
        WeatherCode.PARTLY_CLOUDY to "Partly cloudy",
        WeatherCode.OVERCAST to "Overcast skies",
        WeatherCode.FOG to "Foggy conditions",
        WeatherCode.DEPOSITING_RIME_FOG to "Foggy conditions",
        WeatherCode.DRIZZLE_LIGHT to "Light drizzle",
        WeatherCode.DRIZZLE_MODERATE to "Drizzle",
        WeatherCode.DRIZZLE_DENSE to "Steady drizzle",
        WeatherCode.FREEZING_DRIZZLE_LIGHT to "Freezing drizzle",
        WeatherCode.FREEZING_DRIZZLE_DENSE to "Freezing drizzle",
        WeatherCode.RAIN_SLIGHT to "Light rain",
        WeatherCode.RAIN_MODERATE to "Rainy",
        WeatherCode.RAIN_HEAVY to "Heavy rain",
        WeatherCode.FREEZING_RAIN_LIGHT to "Freezing rain",
        WeatherCode.FREEZING_RAIN_HEAVY to "Freezing rain",
        WeatherCode.SNOW_SLIGHT to "Light snow",
        WeatherCode.SNOW_MODERATE to "Snowy",
        WeatherCode.SNOW_HEAVY to "Heavy snow",
        WeatherCode.SNOW_GRAINS to "Snow grains",
        WeatherCode.RAIN_SHOWERS_SLIGHT to "Passing showers",
        WeatherCode.RAIN_SHOWERS_MODERATE to "Scattered showers",
        WeatherCode.RAIN_SHOWERS_VIOLENT to "Heavy downpours",
        WeatherCode.SNOW_SHOWERS_SLIGHT to "Light snow showers",
        WeatherCode.SNOW_SHOWERS_HEAVY to "Heavy snow showers",
        WeatherCode.THUNDERSTORM to "Thunderstorms",
        WeatherCode.THUNDERSTORM_HAIL_SLIGHT to "Thunderstorms with hail",
        WeatherCode.THUNDERSTORM_HAIL_HEAVY to "Severe thunderstorms with hail",
    )

    private val nighttimeConditionPhrases = daytimeConditionPhrases + (WeatherCode.MAINLY_CLEAR to "Mostly clear")

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
        context: Context? = null,
    ): String {
        val parts = mutableListOf<String>()

        // Opening: time-of-day greeting + condition
        val timeOfDay = timeOfDayLabel(current, context)
        val conditionPhrase = conditionPhrase(current, context)
        parts.add(
            summaryString(
                context,
                R.string.summary_condition_time,
                "$conditionPhrase $timeOfDay",
                conditionPhrase,
                timeOfDay,
            )
        )

        // Precipitation outlook from hourly
        val precipOutlook = precipitationOutlook(hourly, context)
        if (precipOutlook != null) {
            parts.add(precipOutlook)
        }

        // Wind note for significant wind (km/h, Beaufort-aligned phrasing)
        if (current.windSpeed > 30) {
            val windDesc = when {
                current.windSpeed > 60 -> summaryString(context, R.string.summary_wind_gale_force, "gale-force winds")
                current.windSpeed > 40 -> summaryString(context, R.string.summary_wind_strong, "strong winds")
                else -> summaryString(context, R.string.summary_wind_moderate, "moderate winds")
            }
            parts.add(summaryString(context, R.string.summary_with_wind, "with $windDesc", windDesc))
        }

        // UV warning
        if (current.isDay && current.uvIndex >= 8) {
            parts.add(summaryString(context, R.string.summary_uv_very_high, "UV index is very high"))
        } else if (current.isDay && current.uvIndex >= 6) {
            parts.add(summaryString(context, R.string.summary_uv_high, "UV index is high"))
        }

        // Humidity warning
        if (current.humidity >= 80 && current.temperature > 25) {
            parts.add(summaryString(context, R.string.summary_muggy, "and it will feel muggy"))
        }

        // Temperature sentence
        val tempStr = WeatherFormatter.formatTemperature(
            if (current.isDay) (today?.temperatureHigh ?: current.temperature) else (today?.temperatureLow ?: current.temperature),
            s,
        )
        val tempSentence = if (current.isDay) {
            summaryString(context, R.string.summary_highs_near, "Highs near $tempStr.", tempStr)
        } else {
            summaryString(context, R.string.summary_lows_near, "Lows near $tempStr.", tempStr)
        }

        // Yesterday comparison (convert to user's unit for correct degree diff)
        val comparisonStr = if (yesterdayHigh != null && today != null) {
            val todayConverted = WeatherFormatter.convertedTemp(today.temperatureHigh, s)
            val yesterdayConverted = WeatherFormatter.convertedTemp(yesterdayHigh, s)
            val diff = (todayConverted - yesterdayConverted).toInt()
            when {
                diff > 2 -> summaryString(
                    context,
                    R.string.summary_warmer_than_yesterday,
                    " ${diff}\u00B0 warmer than yesterday.",
                    diff,
                )
                diff < -2 -> summaryString(
                    context,
                    R.string.summary_cooler_than_yesterday,
                    " ${-diff}\u00B0 cooler than yesterday.",
                    -diff,
                )
                else -> ""
            }
        } else ""

        val openingSentence = sentenceJoin(parts)
        return "$openingSentence $tempSentence$comparisonStr"
    }

    private fun sentenceJoin(parts: List<String>): String {
        return parts
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar { char -> char.uppercase() } }
            .joinToString(". ")
            .plus(".")
    }

    internal fun timeOfDayLabel(current: CurrentConditions, context: Context?): String {
        val hour = current.observationTime?.hour ?: java.time.LocalTime.now().hour
        return when {
            !current.isDay -> summaryString(context, R.string.summary_time_tonight, "tonight")
            hour < 12 -> summaryString(context, R.string.summary_time_morning, "this morning")
            hour < 17 -> summaryString(context, R.string.summary_time_afternoon, "this afternoon")
            else -> summaryString(context, R.string.summary_time_evening, "this evening")
        }
    }

    private fun conditionPhrase(current: CurrentConditions, context: Context? = null): String {
        current.sourceConditionText?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        if (context != null) return current.weatherCode.localizedDescription(context)
        val phrases = if (current.isDay) daytimeConditionPhrases else nighttimeConditionPhrases
        return phrases[current.weatherCode] ?: "Variable conditions"
    }

    private fun precipitationOutlook(hourly: List<HourlyConditions>, context: Context?): String? {
        val next12 = hourly.take(12)
        if (next12.isEmpty()) return null

        val rainyHours = next12.count { it.precipitationProbability > 40 }
        val maxProb = next12.maxOfOrNull { it.precipitationProbability } ?: 0

        // Find first rainy hour
        val firstRainIdx = next12.indexOfFirst { it.precipitationProbability > 40 }

        return when {
            maxProb < 20 -> null // No rain expected
            rainyHours >= 8 -> summaryString(context, R.string.summary_rain_likely_throughout_day, "with rain likely throughout the day")
            rainyHours >= 4 && firstRainIdx <= 2 -> summaryString(context, R.string.summary_rain_expected_soon, "with rain expected soon")
            rainyHours >= 4 && firstRainIdx > 6 -> summaryString(context, R.string.summary_rain_developing_later, "with rain developing later")
            rainyHours >= 4 -> summaryString(context, R.string.summary_rain_likely_later_today, "with rain likely later today")
            rainyHours >= 1 && maxProb > 60 -> summaryString(context, R.string.summary_chance_showers, "with a chance of showers")
            rainyHours >= 1 -> summaryString(context, R.string.summary_slight_chance_rain, "with a slight chance of rain")
            else -> null
        }
    }

    private fun summaryString(
        context: Context?,
        resId: Int,
        fallback: String,
        vararg args: Any,
    ): String = if (context != null) {
        context.getString(resId, *args)
    } else {
        fallback
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
        context: Context? = null,
    ): String {
        // Always compute the template fallback first (cheap)
        val templateSummary = generate(current, today, hourly, yesterdayHigh, s, context)

        if (s.summaryStyle == SummaryStyle.CUSTOM_TEMPLATE && s.customSummaryTemplate.isNotBlank()) {
            return generateFromCustomTemplate(current, today, hourly, s, context)
        }

        if (s.summaryStyle != SummaryStyle.AI_GENERATED || aiEngine == null) {
            return templateSummary
        }

        // Attempt AI generation with formatted display values
        return try {
            val currentTemp = WeatherFormatter.formatTemperatureUnit(current.temperature, s)
            val condition = conditionPhrase(current, context)
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

    private fun generateFromCustomTemplate(
        current: CurrentConditions,
        today: DailyConditions?,
        hourly: List<HourlyConditions>,
        s: NimbusSettings,
        context: Context?,
    ): String {
        val condition = conditionPhrase(current, context)
        val timeLabel = timeOfDayLabel(current, context)
        val temp = WeatherFormatter.formatTemperatureUnit(current.temperature, s)
        val high = WeatherFormatter.formatTemperatureUnit(
            today?.temperatureHigh ?: current.dailyHigh, s,
        )
        val low = WeatherFormatter.formatTemperatureUnit(
            today?.temperatureLow ?: current.dailyLow, s,
        )
        val wind = WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s)
        val precipChance = today?.precipitationProbability
            ?: hourly.firstOrNull()?.precipitationProbability
            ?: 0
        val maxPrecip12h = hourly.take(12)
            .maxOfOrNull { it.precipitationProbability ?: 0 } ?: 0

        return s.customSummaryTemplate
            .replace("{condition}", condition)
            .replace("{time}", timeLabel)
            .replace("{temp}", temp)
            .replace("{high}", high)
            .replace("{low}", low)
            .replace("{humidity}", "${current.humidity}%")
            .replace("{wind}", wind)
            .replace("{wind_speed}", WeatherFormatter.formatWindSpeed(current.windSpeed, s))
            .replace("{uv}", String.format(Locale.getDefault(), "%.0f", current.uvIndex))
            .replace("{precip}", "$precipChance%")
            .replace("{precip_max}", "$maxPrecip12h%")
            .replace("{pressure}", WeatherFormatter.formatPressure(current.pressure, s))
            .replace("{dewpoint}", WeatherFormatter.formatTemperatureUnit(current.dewPoint ?: 0.0, s))
            .trim()
    }
}
