package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.PressureUnit
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.TimeFormat
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import java.time.LocalDateTime
import kotlin.math.roundToInt
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
        return "${value.roundToInt()}\u00B0"
    }

    /** Format with unit suffix: "72°F" */
    fun formatTemperatureUnit(celsius: Double, s: NimbusSettings = NimbusSettings()): String {
        val value = convertTemp(celsius, s.tempUnit)
        return "${value.roundToInt()}${s.tempUnit.symbol}"
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

    /** Dew point comfort descriptor based on Celsius value. */
    fun dewPointComfort(dewPointCelsius: Double): String = when {
        dewPointCelsius < 10 -> "Dry"
        dewPointCelsius < 16 -> "Comfortable"
        dewPointCelsius < 18 -> "Pleasant"
        dewPointCelsius < 21 -> "Slightly humid"
        dewPointCelsius < 24 -> "Muggy"
        else -> "Oppressive"
    }

    /** Explain why feels-like differs from actual temperature. */
    fun feelsLikeReason(tempCelsius: Double, feelsLikeCelsius: Double, windKmh: Double, humidity: Int): String? {
        val diff = feelsLikeCelsius - tempCelsius
        if (kotlin.math.abs(diff) < 2) return null
        return when {
            diff < -2 && windKmh > 10 -> "Wind chill"
            diff < -2 -> "Cold exposure"
            diff > 2 && humidity > 50 -> "Heat index"
            diff > 2 -> "Solar heating"
            else -> null
        }
    }

    /** Compute pressure trend from hourly surface pressure data (hPa). */
    fun pressureTrend(hourly: List<com.sysadmindoc.nimbus.data.model.HourlyConditions>): String? {
        val pressures = hourly.take(6).mapNotNull { it.surfacePressure }
        if (pressures.size < 3) return null
        val delta = pressures.last() - pressures.first()
        return when {
            delta > 2.0 -> "\u2197 Rising"
            delta > 0.5 -> "\u2197 Slowly rising"
            delta < -2.0 -> "\u2198 Falling"
            delta < -0.5 -> "\u2198 Slowly falling"
            else -> "\u2192 Steady"
        }
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

    // ── Snowfall ──────────────────────────────────────────────────────────

    fun formatSnowfall(cm: Double, s: NimbusSettings = NimbusSettings()): String = when (s.precipUnit) {
        PrecipUnit.INCHES -> "%.1f in".format(cm / 2.54)
        PrecipUnit.MM -> "%.1f cm".format(cm)
    }

    fun formatSnowDepth(cm: Double, s: NimbusSettings = NimbusSettings()): String = when (s.precipUnit) {
        PrecipUnit.INCHES -> "%.0f in".format(cm / 2.54)
        PrecipUnit.MM -> "%.0f cm".format(cm)
    }

    // ── Sunshine Duration ─────────────────────────────────────────────────

    /** Convert seconds of sunshine to human-readable duration. */
    fun formatSunshineDuration(seconds: Double): String {
        val hours = (seconds / 3600).toInt()
        val mins = ((seconds % 3600) / 60).toInt()
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    // ── Precipitation Hours ───────────────────────────────────────────────

    fun formatPrecipitationHours(hours: Double): String {
        val h = hours.toInt()
        return if (h == 0) "None" else "${h}h"
    }

    // ── CAPE / Severe Weather ─────────────────────────────────────────────

    fun formatCape(cape: Double): String = "${cape.toInt()} J/kg"

    fun capeDescription(cape: Double): String = when {
        cape < 300 -> "Stable"
        cape < 1000 -> "Marginally Unstable"
        cape < 2500 -> "Moderately Unstable"
        cape < 4000 -> "Very Unstable"
        else -> "Extremely Unstable"
    }

    // ── Beaufort Wind Scale ───────────────────────────────────────────────

    data class BeaufortInfo(val scale: Int, val label: String, val colorHex: Long)

    fun beaufortScale(kmh: Double): BeaufortInfo = when {
        kmh < 2 -> BeaufortInfo(0, "Calm", 0xFFAED6F1)
        kmh < 6 -> BeaufortInfo(1, "Light Air", 0xFF85C1E9)
        kmh < 12 -> BeaufortInfo(2, "Light Breeze", 0xFF5DADE2)
        kmh < 20 -> BeaufortInfo(3, "Gentle Breeze", 0xFF48C9B0)
        kmh < 29 -> BeaufortInfo(4, "Moderate Breeze", 0xFF52BE80)
        kmh < 39 -> BeaufortInfo(5, "Fresh Breeze", 0xFF82E0AA)
        kmh < 50 -> BeaufortInfo(6, "Strong Breeze", 0xFFF9E79F)
        kmh < 62 -> BeaufortInfo(7, "Near Gale", 0xFFF5B041)
        kmh < 75 -> BeaufortInfo(8, "Gale", 0xFFEB984E)
        kmh < 89 -> BeaufortInfo(9, "Strong Gale", 0xFFE74C3C)
        kmh < 103 -> BeaufortInfo(10, "Storm", 0xFFC0392B)
        kmh < 118 -> BeaufortInfo(11, "Violent Storm", 0xFF8E44AD)
        else -> BeaufortInfo(12, "Hurricane", 0xFF6C3483)
    }

    // ── Golden Hour ───────────────────────────────────────────────────────

    /** Returns pair of (morning golden hour end, evening golden hour start) as formatted times. */
    fun goldenHourTimes(
        sunrise: String?,
        sunset: String?,
        s: NimbusSettings = NimbusSettings(),
    ): Pair<String, String>? {
        if (sunrise == null || sunset == null) return null
        return try {
            val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val rise = java.time.LocalDateTime.parse(sunrise, fmt)
            val set = java.time.LocalDateTime.parse(sunset, fmt)
            val morningEnd = rise.plusMinutes(60)
            val eveningStart = set.minusMinutes(60)
            Pair(formatTime(morningEnd.toString(), s), formatTime(eveningStart.toString(), s))
        } catch (_: Exception) { null }
    }

    // ── Outdoor Activity Score ────────────────────────────────────────────

    /**
     * Composite score 0-100 for outdoor activity suitability.
     * Considers temperature comfort, wind, UV, precipitation, air quality.
     */
    fun outdoorActivityScore(
        tempCelsius: Double,
        humidity: Int,
        windKmh: Double,
        uvIndex: Double,
        precipProbability: Int,
        aqi: Int? = null,
    ): Int {
        // Temperature: ideal 15-25C = 100, penalty for deviation
        val tempScore = when {
            tempCelsius in 15.0..25.0 -> 100
            tempCelsius in 10.0..30.0 -> 80
            tempCelsius in 5.0..35.0 -> 50
            tempCelsius in 0.0..40.0 -> 25
            else -> 10
        }
        // Humidity: ideal 30-60%
        val humidityScore = when {
            humidity in 30..60 -> 100
            humidity in 20..70 -> 80
            humidity in 10..80 -> 50
            else -> 30
        }
        // Wind: calm is best
        val windScore = when {
            windKmh < 15 -> 100
            windKmh < 30 -> 75
            windKmh < 50 -> 40
            else -> 15
        }
        // UV: moderate is fine
        val uvScore = when {
            uvIndex < 3 -> 100
            uvIndex < 6 -> 85
            uvIndex < 8 -> 60
            uvIndex < 11 -> 35
            else -> 15
        }
        // Precipitation: lower is better
        val precipScore = (100 - precipProbability).coerceIn(0, 100)
        // AQI: lower is better
        val aqiScore = when {
            aqi == null -> 80 // assume decent if unknown
            aqi <= 50 -> 100
            aqi <= 100 -> 70
            aqi <= 150 -> 40
            else -> 15
        }
        // Weighted average
        return ((tempScore * 0.30) + (windScore * 0.20) + (precipScore * 0.20) +
            (uvScore * 0.10) + (humidityScore * 0.10) + (aqiScore * 0.10)).toInt().coerceIn(0, 100)
    }

    fun outdoorScoreLabel(score: Int): String = when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        score >= 20 -> "Poor"
        else -> "Stay Inside"
    }
}
