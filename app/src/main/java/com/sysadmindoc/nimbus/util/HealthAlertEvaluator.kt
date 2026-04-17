package com.sysadmindoc.nimbus.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import java.util.Locale
import kotlin.math.abs

private fun formatHpa(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

/**
 * Evaluates weather conditions for health-related triggers.
 *
 * Three alert categories:
 * - **Migraine**: Rapid barometric pressure changes (3-hour rolling window)
 * - **Respiratory**: Extreme humidity (very high or very low)
 * - **Arthritis/joint pain**: Large temperature swings over 12 hours
 *
 * Uses actual surface pressure from hourly data when available,
 * with temperature/humidity frontal-passage proxy as fallback.
 */
object HealthAlertEvaluator {

    /**
     * Evaluate all health alerts from hourly weather data.
     *
     * @param hourly Forecast hourly conditions (should include past hour + forecast)
     * @param pressureThresholdHpa User-configurable pressure delta threshold (default 5.0 hPa)
     * @param enableMigraine Whether to evaluate migraine/pressure alerts
     * @param enableRespiratory Whether to evaluate respiratory alerts
     * @param enableArthritis Whether to evaluate arthritis/joint alerts
     */
    fun evaluate(
        hourly: List<HourlyConditions>,
        pressureThresholdHpa: Double = 5.0,
        enableMigraine: Boolean = true,
        enableRespiratory: Boolean = true,
        enableArthritis: Boolean = true,
    ): List<HealthAlert> {
        val alerts = mutableListOf<HealthAlert>()

        if (enableMigraine) {
            evaluatePressureChange(hourly, pressureThresholdHpa)?.let { alerts.add(it) }
        }

        if (enableRespiratory) {
            evaluateHumidity(hourly)?.let { alerts.add(it) }
        }

        if (enableArthritis) {
            evaluateTemperatureSwing(hourly)?.let { alerts.add(it) }
        }

        return alerts
    }

    /**
     * Detects rapid barometric pressure changes using 3-hour rolling windows.
     *
     * Uses actual [HourlyConditions.surfacePressure] when available.
     * Falls back to temperature/humidity frontal-passage heuristic when
     * pressure data is missing (e.g., Pirate Weather, some Bright Sky stations).
     *
     * Warning thresholds (based on medical literature):
     * - >= threshold hPa change in 3h → WARNING (strong migraine trigger)
     * - >= threshold * 0.6 hPa change in 3h → ADVISORY (possible trigger)
     */
    private fun evaluatePressureChange(
        hourly: List<HourlyConditions>,
        thresholdHpa: Double,
    ): HealthAlert? {
        // Try real pressure data first
        val pressureAlert = evaluateRealPressure(hourly, thresholdHpa)
        if (pressureAlert != null) return pressureAlert

        // Fallback: temperature/humidity frontal-passage proxy
        return evaluateFrontalProxy(hourly)
    }

    /**
     * Evaluate pressure changes using actual surface pressure readings.
     * Scans 3-hour rolling windows in the next 12 hours of forecast data.
     */
    private fun evaluateRealPressure(
        hourly: List<HourlyConditions>,
        thresholdHpa: Double,
    ): HealthAlert? {
        val next12 = hourly.take(12)
        val pressures = next12.mapNotNull { it.surfacePressure }

        // Need at least 4 pressure readings to compute a meaningful 3h window
        if (pressures.size < 4) return null

        // Find the maximum pressure change across all 3-hour windows
        var maxDelta = 0.0
        var maxDeltaDirection = "" // "dropping" or "rising"
        val windowSize = minOf(3, pressures.size - 1)

        for (i in 0 until pressures.size - windowSize) {
            val delta = pressures[i + windowSize] - pressures[i]
            if (abs(delta) > abs(maxDelta)) {
                maxDelta = delta
                maxDeltaDirection = if (delta < 0) "dropping" else "rising"
            }
        }

        val absDelta = abs(maxDelta)

        if (absDelta >= thresholdHpa) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.WARNING,
                message = "Rapid pressure change detected — migraine trigger likely.",
                detail = "Barometric pressure $maxDeltaDirection by ${formatHpa(absDelta)} hPa within a 3-hour window in the next 12 hours. " +
                    "Consider taking preventive medication.",
            )
        }

        if (absDelta >= thresholdHpa * 0.6) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                message = "Moderate pressure change ahead — possible migraine trigger.",
                detail = "Barometric pressure $maxDeltaDirection by ${formatHpa(absDelta)} hPa over the next few hours.",
            )
        }

        return null
    }

    /**
     * Fallback: detect frontal passage from temperature + humidity shifts
     * when pressure data isn't available. Large simultaneous swings in both
     * indicate a weather front, which correlates with pressure changes.
     */
    private fun evaluateFrontalProxy(hourly: List<HourlyConditions>): HealthAlert? {
        val next6 = hourly.take(6)
        if (next6.size < 3) return null

        val humidities = next6.mapNotNull { it.humidity }
        val humidityChange = if (humidities.size >= 2) humidities.max() - humidities.min() else 0
        val tempChange = next6.maxOf { it.temperature } - next6.minOf { it.temperature }

        if (tempChange > 8 && humidityChange > 30) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.WARNING,
                message = "Rapid weather changes ahead — migraine trigger likely.",
                detail = "Temperature swing of ${tempChange.toInt()}\u00B0C and humidity change of $humidityChange% " +
                    "in the next 6 hours suggests a weather front approaching.",
            )
        }

        if (tempChange > 5 && humidityChange > 20) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                message = "Moderate weather changes ahead — possible migraine trigger.",
                detail = "Temperature and humidity shifts detected in the next 6 hours.",
            )
        }

        return null
    }

    /**
     * Evaluates respiratory health risk from extreme humidity.
     * Very high humidity (>85%) can trigger asthma, COPD flares.
     * Very low humidity (<20%) dries airways, aggravates sinuses.
     */
    private fun evaluateHumidity(hourly: List<HourlyConditions>): HealthAlert? {
        val current = hourly.firstOrNull() ?: return null
        val humidity = current.humidity ?: return null

        return when {
            humidity > 85 -> HealthAlert(
                type = HealthAlertType.RESPIRATORY,
                severity = HealthSeverity.ADVISORY,
                message = "Very high humidity ($humidity%).",
                detail = "May aggravate respiratory conditions such as asthma or COPD. " +
                    "Stay hydrated and limit strenuous outdoor activity.",
            )
            humidity < 20 -> HealthAlert(
                type = HealthAlertType.RESPIRATORY,
                severity = HealthSeverity.ADVISORY,
                message = "Very low humidity ($humidity%).",
                detail = "Dry air may irritate airways and sinuses. Consider using a humidifier indoors.",
            )
            else -> null
        }
    }

    /**
     * Detects large temperature swings over the next 12 hours.
     * Rapid temperature changes (>10-15\u00B0C) can worsen joint pain
     * and arthritis symptoms.
     */
    private fun evaluateTemperatureSwing(hourly: List<HourlyConditions>): HealthAlert? {
        val next12 = hourly.take(12)
        if (next12.size < 4) return null

        val maxTemp = next12.maxOf { it.temperature }
        val minTemp = next12.minOf { it.temperature }
        val swing = maxTemp - minTemp

        if (swing > 15) {
            return HealthAlert(
                type = HealthAlertType.ARTHRITIS_TRIGGER,
                severity = HealthSeverity.WARNING,
                message = "Large temperature swing of ${swing.toInt()}\u00B0C expected.",
                detail = "Rapid temperature changes may worsen joint pain and arthritis symptoms. " +
                    "Dress in layers and stay warm during colder periods.",
            )
        }

        if (swing > 10) {
            return HealthAlert(
                type = HealthAlertType.ARTHRITIS_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                message = "Notable temperature swing of ${swing.toInt()}\u00B0C expected.",
                detail = "May affect sensitive individuals with joint conditions.",
            )
        }

        return null
    }
}

@Stable
data class HealthAlert(
    val type: HealthAlertType,
    val severity: HealthSeverity,
    val message: String,
    val detail: String = "",
)

enum class HealthAlertType(val label: String) {
    MIGRAINE_TRIGGER("Migraine Trigger"),
    RESPIRATORY("Respiratory"),
    ARTHRITIS_TRIGGER("Joint Pain"),
}

enum class HealthSeverity(val label: String, val color: Color) {
    WARNING("Warning", Color(0xFFFF5722)),
    ADVISORY("Advisory", Color(0xFFFF9800)),
    INFO("Info", Color(0xFF2196F3)),
}
