package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs

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
    val geometry: AlertGeometry? = null,
    val coversRequestedLocation: Boolean? = null,
)

@Stable
data class AlertGeometry(
    val polygons: List<AlertPolygon>,
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        return polygons.any { it.contains(latitude, longitude) }
    }
}

@Stable
data class AlertPolygon(
    val points: List<AlertCoordinate>,
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        if (points.size < 3) return false

        val x = longitude
        val y = latitude
        var inside = false
        var previousIndex = points.lastIndex

        for (currentIndex in points.indices) {
            val current = points[currentIndex]
            val previous = points[previousIndex]
            val xi = current.longitude
            val yi = current.latitude
            val xj = previous.longitude
            val yj = previous.latitude

            if (isPointOnSegment(x, y, xi, yi, xj, yj)) {
                return true
            }

            val crossesLatitude = (yi > y) != (yj > y)
            if (crossesLatitude) {
                val intersectionX = ((xj - xi) * (y - yi) / (yj - yi)) + xi
                if (x < intersectionX) {
                    inside = !inside
                }
            }

            previousIndex = currentIndex
        }

        return inside
    }
}

@Stable
data class AlertCoordinate(
    val latitude: Double,
    val longitude: Double,
)

private const val GEOMETRY_EPSILON = 1e-9

private fun isPointOnSegment(
    x: Double,
    y: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
): Boolean {
    val squaredLength = ((x2 - x1) * (x2 - x1)) + ((y2 - y1) * (y2 - y1))
    if (squaredLength <= GEOMETRY_EPSILON) {
        return abs(x - x1) <= GEOMETRY_EPSILON && abs(y - y1) <= GEOMETRY_EPSILON
    }

    val cross = ((y - y1) * (x2 - x1)) - ((x - x1) * (y2 - y1))
    if (abs(cross) > GEOMETRY_EPSILON) return false

    val dot = ((x - x1) * (x2 - x1)) + ((y - y1) * (y2 - y1))
    if (dot < -GEOMETRY_EPSILON) return false

    return dot <= squaredLength + GEOMETRY_EPSILON
}

enum class AlertSeverity(val label: String, val color: Color, val sortOrder: Int) {
    EXTREME("Extreme", Color(0xFFD32F2F), 0),
    SEVERE("Severe", Color(0xFFFF5722), 1),
    MODERATE("Moderate", Color(0xFFFF9800), 2),
    MINOR("Minor", Color(0xFFFFEB3B), 3),
    UNKNOWN("Unknown", Color(0xFF9E9E9E), 4);

    companion object {
        // Locale.ROOT keeps lowercase() deterministic — default-locale lowercase on
        // a Turkish device maps 'I' to 'ı' (dotless i), which would drop
        // upstream alert strings like "IMMEDIATE" into the UNKNOWN bucket.
        fun from(value: String?): AlertSeverity = when (value?.lowercase(Locale.ROOT)) {
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
        fun from(value: String?): AlertUrgency = when (value?.lowercase(Locale.ROOT)) {
            "immediate" -> IMMEDIATE
            "expected" -> EXPECTED
            "future" -> FUTURE
            "past" -> PAST
            else -> UNKNOWN
        }
    }
}
