package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import kotlin.math.roundToInt

object ActivityIndexEvaluator {

    fun evaluate(
        current: CurrentConditions,
        precipProbability: Int = 0,
        aqi: Int? = null,
    ): List<ActivityIndex> = ActivityType.entries.map { type ->
        val factors = type.scoreFactors(current, precipProbability, aqi)
        val score = factors.values.average().roundToInt().coerceIn(0, 100)
        ActivityIndex(type = type, score = score, factors = factors)
    }
}

enum class ActivityType(
    @StringRes val labelRes: Int,
    val icon: String,
) {
    RUNNING(R.string.activity_running, "🏃"),
    CYCLING(R.string.activity_cycling, "🚴"),
    FISHING(R.string.activity_fishing, "🎣"),
    STARGAZING(R.string.activity_stargazing, "⭐"),
    GRILLING(R.string.activity_grilling, "🍖"),
    LAWN_CARE(R.string.activity_lawn_care, "🌿");

    internal fun scoreFactors(
        c: CurrentConditions,
        precipProb: Int,
        aqi: Int?,
    ): Map<String, Int> {
        val temp = tempScore(c.temperature)
        val wind = windScore(c.windSpeed)
        val rain = (100 - precipProb).coerceIn(0, 100)
        val humidity = humidityScore(c.humidity)
        val uv = uvScore(c.uvIndex)
        val air = aqiScore(aqi)

        return when (this) {
            RUNNING -> mapOf("Temp" to temp, "Wind" to wind, "Rain" to rain, "UV" to uv, "AQI" to air)
            CYCLING -> mapOf("Temp" to temp, "Wind" to windScoreCycling(c.windSpeed), "Rain" to rain, "UV" to uv, "AQI" to air)
            FISHING -> mapOf("Temp" to temp, "Wind" to windScoreFishing(c.windSpeed), "Rain" to rain, "Humidity" to humidity)
            STARGAZING -> mapOf("Cloud" to cloudScore(c.cloudCover), "Humidity" to humidity, "Wind" to wind, "Rain" to rain)
            GRILLING -> mapOf("Temp" to temp, "Rain" to rain, "Wind" to wind, "Humidity" to humidity)
            LAWN_CARE -> mapOf("Temp" to temp, "Rain" to rain, "Wind" to wind, "UV" to uv, "Humidity" to humidity)
        }
    }
}

private fun tempScore(c: Double): Int = when {
    c in 10.0..28.0 -> 100
    c in 5.0..32.0 -> 70
    c in 0.0..38.0 -> 40
    else -> 15
}

private fun windScore(kmh: Double): Int = when {
    kmh < 15 -> 100
    kmh < 30 -> 70
    kmh < 50 -> 35
    else -> 10
}

private fun windScoreCycling(kmh: Double): Int = when {
    kmh < 20 -> 100
    kmh < 35 -> 60
    kmh < 50 -> 30
    else -> 10
}

private fun windScoreFishing(kmh: Double): Int = when {
    kmh < 25 -> 100
    kmh < 40 -> 65
    else -> 25
}

private fun humidityScore(h: Int): Int = when {
    h in 30..65 -> 100
    h in 20..75 -> 70
    h in 10..85 -> 40
    else -> 20
}

private fun uvScore(uv: Double): Int = when {
    uv < 4 -> 100
    uv < 7 -> 70
    uv < 10 -> 40
    else -> 15
}

private fun aqiScore(aqi: Int?): Int = when {
    aqi == null -> 80
    aqi <= 50 -> 100
    aqi <= 100 -> 65
    aqi <= 150 -> 35
    else -> 10
}

private fun cloudScore(cover: Int): Int = when {
    cover < 20 -> 100
    cover < 40 -> 75
    cover < 60 -> 45
    cover < 80 -> 20
    else -> 5
}

@Stable
data class ActivityIndex(
    val type: ActivityType,
    val score: Int,
    val factors: Map<String, Int>,
) {
    val label: String get() = "${type.icon} ${type.name.lowercase().replaceFirstChar { it.uppercase() }}"
}
