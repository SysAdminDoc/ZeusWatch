package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * UI-ready weather alert models.
 */
@Stable
data class WeatherAlert(
    val id: String,
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String?,
    val severity: AlertSeverity,
    val urgency: AlertUrgency,
    val certainty: String,
    val senderName: String,
    val areaDescription: String,
    val effective: String?,
    val expires: String?,
    val response: String?,
)

enum class AlertSeverity(val label: String, val color: Color, val sortOrder: Int) {
    EXTREME("Extreme", Color(0xFFD32F2F), 0),
    SEVERE("Severe", Color(0xFFFF5722), 1),
    MODERATE("Moderate", Color(0xFFFF9800), 2),
    MINOR("Minor", Color(0xFFFFEB3B), 3),
    UNKNOWN("Unknown", Color(0xFF9E9E9E), 4);

    companion object {
        fun from(value: String?): AlertSeverity = when (value?.lowercase()) {
            "extreme" -> EXTREME
            "severe" -> SEVERE
            "moderate" -> MODERATE
            "minor" -> MINOR
            else -> UNKNOWN
        }
    }
}

enum class AlertUrgency(val label: String, val sortOrder: Int) {
    IMMEDIATE("Immediate", 0),
    EXPECTED("Expected", 1),
    FUTURE("Future", 2),
    PAST("Past", 3),
    UNKNOWN("Unknown", 4);

    companion object {
        fun from(value: String?): AlertUrgency = when (value?.lowercase()) {
            "immediate" -> IMMEDIATE
            "expected" -> EXPECTED
            "future" -> FUTURE
            "past" -> PAST
            else -> UNKNOWN
        }
    }
}
