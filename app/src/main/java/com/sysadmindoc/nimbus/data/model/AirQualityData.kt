package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * Air quality data for UI display.
 */
@Stable
data class AirQualityData(
    val usAqi: Int,
    val europeanAqi: Int,
    val aqiLevel: AqiLevel,
    val pm25: Double,
    val pm10: Double,
    val ozone: Double,
    val nitrogenDioxide: Double,
    val sulphurDioxide: Double,
    val carbonMonoxide: Double,
    val pollen: PollenData,
    val hourlyAqi: List<HourlyAqi> = emptyList(),
)

@Stable
data class HourlyAqi(
    val hour: String,
    val aqi: Int,
    val level: AqiLevel,
)

enum class AqiLevel(
    val label: String,
    val color: Color,
    val advice: String,
    val range: IntRange,
) {
    GOOD(
        "Good", Color(0xFF4CAF50), "Air quality is satisfactory.",
        0..50
    ),
    MODERATE(
        "Moderate", Color(0xFFFFEB3B), "Acceptable. May be a risk for sensitive groups.",
        51..100
    ),
    UNHEALTHY_SENSITIVE(
        "Unhealthy for Sensitive Groups", Color(0xFFFF9800),
        "Sensitive groups may experience health effects.",
        101..150
    ),
    UNHEALTHY(
        "Unhealthy", Color(0xFFF44336),
        "Everyone may begin to experience health effects.",
        151..200
    ),
    VERY_UNHEALTHY(
        "Very Unhealthy", Color(0xFF9C27B0),
        "Health alert: increased risk of health effects for everyone.",
        201..300
    ),
    HAZARDOUS(
        "Hazardous", Color(0xFF880E4F),
        "Health warning of emergency conditions.",
        301..500
    );

    companion object {
        fun fromAqi(aqi: Int): AqiLevel = entries.firstOrNull { aqi in it.range }
            ?: if (aqi > 300) HAZARDOUS else GOOD
    }
}

/**
 * Pollen concentration data (grains/m3).
 */
@Stable
data class PollenData(
    val alder: PollenReading = PollenReading.NONE,
    val birch: PollenReading = PollenReading.NONE,
    val grass: PollenReading = PollenReading.NONE,
    val mugwort: PollenReading = PollenReading.NONE,
    val olive: PollenReading = PollenReading.NONE,
    val ragweed: PollenReading = PollenReading.NONE,
) {
    val hasData: Boolean
        get() = listOf(alder, birch, grass, mugwort, olive, ragweed)
            .any { it != PollenReading.NONE }

    val overallLevel: PollenLevel
        get() = listOf(alder, birch, grass, mugwort, olive, ragweed)
            .maxByOrNull { it.level.ordinal }?.level ?: PollenLevel.NONE
}

@Stable
data class PollenReading(
    val concentration: Double,
    val level: PollenLevel,
    val name: String = "",
) {
    companion object {
        val NONE = PollenReading(0.0, PollenLevel.NONE)

        fun fromConcentration(value: Double?, name: String, thresholds: PollenThresholds): PollenReading {
            if (value == null || value <= 0) return PollenReading(0.0, PollenLevel.NONE, name)
            val level = when {
                value < thresholds.low -> PollenLevel.LOW
                value < thresholds.moderate -> PollenLevel.MODERATE
                value < thresholds.high -> PollenLevel.HIGH
                else -> PollenLevel.VERY_HIGH
            }
            return PollenReading(value, level, name)
        }
    }
}

data class PollenThresholds(val low: Double, val moderate: Double, val high: Double)

enum class PollenLevel(val label: String, val color: Color) {
    NONE("None", Color(0xFF9E9E9E)),
    LOW("Low", Color(0xFF4CAF50)),
    MODERATE("Moderate", Color(0xFFFFEB3B)),
    HIGH("High", Color(0xFFFF9800)),
    VERY_HIGH("Very High", Color(0xFFF44336)),
}

// Standard pollen thresholds (grains/m3)
object PollenThresholdsDb {
    val ALDER = PollenThresholds(10.0, 50.0, 200.0)
    val BIRCH = PollenThresholds(10.0, 50.0, 200.0)
    val GRASS = PollenThresholds(5.0, 20.0, 50.0)
    val MUGWORT = PollenThresholds(5.0, 20.0, 50.0)
    val OLIVE = PollenThresholds(10.0, 50.0, 200.0)
    val RAGWEED = PollenThresholds(5.0, 20.0, 50.0)
}

/**
 * Moon phase and astronomy data.
 */
@Stable
data class AstronomyData(
    val moonPhase: MoonPhase,
    val moonIllumination: Double, // 0-100%
    val moonrise: String?,
    val moonset: String?,
    val dayLength: String?, // "HH:mm"
)

enum class MoonPhase(val label: String, val emoji: String) {
    NEW_MOON("New Moon", "\uD83C\uDF11"),
    WAXING_CRESCENT("Waxing Crescent", "\uD83C\uDF12"),
    FIRST_QUARTER("First Quarter", "\uD83C\uDF13"),
    WAXING_GIBBOUS("Waxing Gibbous", "\uD83C\uDF14"),
    FULL_MOON("Full Moon", "\uD83C\uDF15"),
    WANING_GIBBOUS("Waning Gibbous", "\uD83C\uDF16"),
    LAST_QUARTER("Last Quarter", "\uD83C\uDF17"),
    WANING_CRESCENT("Waning Crescent", "\uD83C\uDF18");

    companion object {
        /**
         * Calculate moon phase from illumination percentage and whether waxing.
         * Open-Meteo doesn't directly provide phase, so we derive from day of lunar cycle.
         */
        fun fromDayOfCycle(day: Double): MoonPhase {
            val normalized = day % 29.53 // Synodic month
            return when {
                normalized < 1.85 -> NEW_MOON
                normalized < 7.38 -> WAXING_CRESCENT
                normalized < 9.23 -> FIRST_QUARTER
                normalized < 14.77 -> WAXING_GIBBOUS
                normalized < 16.61 -> FULL_MOON
                normalized < 22.15 -> WANING_GIBBOUS
                normalized < 23.99 -> LAST_QUARTER
                normalized < 29.53 -> WANING_CRESCENT
                else -> NEW_MOON
            }
        }

        fun fromIlluminationAndAge(illumination: Double, lunarAge: Double): MoonPhase {
            return fromDayOfCycle(lunarAge)
        }
    }
}
