package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.PressureUnit
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.TimeFormat
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * All raw values from the API are metric (Celsius, km/h, mm, hPa, meters).
 * This formatter converts to the user's preferred units for display.
 */
object WeatherFormatter {

    // ── Temperature ──────────────────────────────────────────────────────

    /** Convert metric celsius to user unit and format as "72°" */
    fun formatTemperature(celsius: Double, s: NimbusSettings = NimbusSettings()): String {
        val value = convertTemp(celsius, s.tempUnit)
        return "${value.toInt()}\u00B0"
    }

    /** Format with unit suffix: "72°F" */
    fun formatTemperatureUnit(celsius: Double, s: NimbusSettings = NimbusSettings()): String {
        val value = convertTemp(celsius, s.tempUnit)
        return "${value.toInt()}${s.tempUnit.symbol}"
    }

    private fun convertTemp(celsius: Double, unit: TempUnit): Double = when (unit) {
        TempUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
        TempUnit.CELSIUS -> celsius
    }

    // ── Wind ─────────────────────────────────────────────────────────────

    fun formatWindSpeed(kmh: Double, direction: Int, s: NimbusSettings = NimbusSettings()): String {
        val speed = convertWind(kmh, s.windUnit)
        return "${formatWindDirection(direction)} ${speed.toInt()} ${s.windUnit.symbol}"
    }

    fun formatWindSpeed(kmh: Double, s: NimbusSettings = NimbusSettings()): String {
        val speed = convertWind(kmh, s.windUnit)
        return "${speed.toInt()} ${s.windUnit.symbol}"
    }

    fun formatWindDirection(degrees: Int): String = when {
        degrees < 23 -> "N"
        degrees < 68 -> "NE"
        degrees < 113 -> "E"
        degrees < 158 -> "SE"
        degrees < 203 -> "S"
        degrees < 248 -> "SW"
        degrees < 293 -> "W"
        degrees < 338 -> "NW"
        else -> "N"
    }

    private fun convertWind(kmh: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.MPH -> kmh / 1.60934
        WindUnit.KMH -> kmh
        WindUnit.MS -> kmh / 3.6
        WindUnit.KNOTS -> kmh / 1.852
    }

    // ── Pressure ─────────────────────────────────────────────────────────

    fun formatPressure(hPa: Double, s: NimbusSettings = NimbusSettings()): String = when (s.pressureUnit) {
        PressureUnit.INHG -> "%.2f inHg".format(hPa * 0.02953)
        PressureUnit.HPA -> "${hPa.toInt()} hPa"
        PressureUnit.MBAR -> "${hPa.toInt()} mbar"
    }

    // ── Precipitation ────────────────────────────────────────────────────

    fun formatPrecipitation(mm: Double, s: NimbusSettings = NimbusSettings()): String = when (s.precipUnit) {
        PrecipUnit.INCHES -> "%.2f in".format(mm / 25.4)
        PrecipUnit.MM -> "%.1f mm".format(mm)
    }

    // ── Visibility ───────────────────────────────────────────────────────

    fun formatVisibility(meters: Double?, s: NimbusSettings = NimbusSettings()): String {
        if (meters == null) return "--"
        return when (s.visibilityUnit) {
            VisibilityUnit.MILES -> {
                val miles = meters / 1609.344
                if (miles >= 10) "10+ mi" else "%.1f mi".format(miles)
            }
            VisibilityUnit.KM -> {
                val km = meters / 1000.0
                if (km >= 16) "16+ km" else "%.1f km".format(km)
            }
        }
    }

    // ── Dew Point (temperature) ──────────────────────────────────────────

    fun formatDewPoint(celsius: Double, s: NimbusSettings = NimbusSettings()): String {
        val value = convertTemp(celsius, s.tempUnit)
        return "${value.toInt()}\u00B0"
    }

    // ── Unit-independent formatters ──────────────────────────────────────

    fun formatUvLevel(uv: Double): String = uvDescription(uv)
    fun formatHumidity(percent: Int): String = "$percent%"
    fun formatUvIndex(uv: Double): String = uv.toInt().toString()
    fun formatCloudCover(percent: Int): String = "$percent%"

    fun uvDescription(uv: Double): String = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very High"
        else -> "Extreme"
    }

    // ── Time / Date ──────────────────────────────────────────────────────

    fun formatHourLabel(time: LocalDateTime, s: NimbusSettings = NimbusSettings()): String {
        val now = LocalDateTime.now()
        if (time.hour == now.hour && time.dayOfYear == now.dayOfYear) return "Now"
        val pattern = when (s.timeFormat) {
            TimeFormat.TWELVE_HOUR -> "h a"
            TimeFormat.TWENTY_FOUR_HOUR -> "HH:mm"
        }
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun formatDayLabel(date: java.time.LocalDate): String {
        val today = java.time.LocalDate.now()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> {
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                val dayNum = date.dayOfMonth
                "$dayName $dayNum"
            }
        }
    }

    fun formatTime(isoString: String?, s: NimbusSettings = NimbusSettings()): String {
        if (isoString == null) return "--"
        return try {
            val dt = LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val pattern = when (s.timeFormat) {
                TimeFormat.TWELVE_HOUR -> "h:mm a"
                TimeFormat.TWENTY_FOUR_HOUR -> "HH:mm"
            }
            dt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
        } catch (_: Exception) {
            "--"
        }
    }
}
