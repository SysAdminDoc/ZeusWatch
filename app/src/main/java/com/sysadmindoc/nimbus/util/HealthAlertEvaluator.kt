package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import java.time.Duration
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

    /** A "3-hour" pressure window accepts reading pairs 2.5–3.5h apart. */
    private const val MIN_WINDOW_MINUTES = 150L
    private const val MAX_WINDOW_MINUTES = 210L

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
     * Scans ~3-hour windows in the next 12 hours of forecast data.
     *
     * Deltas are computed between readings whose *timestamps* are 2.5–3.5h
     * apart — not between fixed list offsets. Indexing a null-compacted list
     * silently stretched the "3-hour" window across data gaps, turning
     * ordinary diurnal drift in sparse pressure series into false WARNINGs.
     */
    private fun evaluateRealPressure(
        hourly: List<HourlyConditions>,
        thresholdHpa: Double,
    ): HealthAlert? {
        val next12 = hourly.take(12)
        val readings = next12.mapNotNull { h -> h.surfacePressure?.let { h.time to it } }

        // Need at least 4 pressure readings to compute a meaningful 3h window
        if (readings.size < 4) return null

        // Find the maximum pressure change across all ~3-hour reading pairs
        var maxDelta = 0.0
        for (i in readings.indices) {
            for (j in i + 1 until readings.size) {
                val minutesApart = abs(Duration.between(readings[i].first, readings[j].first).toMinutes())
                if (minutesApart < MIN_WINDOW_MINUTES || minutesApart > MAX_WINDOW_MINUTES) continue
                val delta = readings[j].second - readings[i].second
                if (abs(delta) > abs(maxDelta)) {
                    maxDelta = delta
                }
            }
        }

        val absDelta = abs(maxDelta)

        if (absDelta >= thresholdHpa) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.WARNING,
                messageRes = R.string.health_alert_pressure_warning,
                detailRes = R.string.health_alert_pressure_warning_detail,
                detailArgs = listOf(formatHpa(absDelta)),
            )
        }

        if (absDelta >= thresholdHpa * 0.6) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                messageRes = R.string.health_alert_pressure_advisory,
                detailRes = R.string.health_alert_pressure_advisory_detail,
                detailArgs = listOf(formatHpa(absDelta)),
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
                messageRes = R.string.health_alert_front_warning,
                detailRes = R.string.health_alert_front_warning_detail,
                detailArgs = listOf(tempChange.toInt(), humidityChange),
            )
        }

        if (tempChange > 5 && humidityChange > 20) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                messageRes = R.string.health_alert_front_advisory,
                detailRes = R.string.health_alert_front_advisory_detail,
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
                messageRes = R.string.health_alert_humidity_high,
                messageArgs = listOf(humidity),
                detailRes = R.string.health_alert_humidity_high_detail,
            )
            humidity < 20 -> HealthAlert(
                type = HealthAlertType.RESPIRATORY,
                severity = HealthSeverity.ADVISORY,
                messageRes = R.string.health_alert_humidity_low,
                messageArgs = listOf(humidity),
                detailRes = R.string.health_alert_humidity_low_detail,
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
                messageRes = R.string.health_alert_temp_swing_warning,
                messageArgs = listOf(swing.toInt()),
                detailRes = R.string.health_alert_temp_swing_warning_detail,
            )
        }

        if (swing > 10) {
            return HealthAlert(
                type = HealthAlertType.ARTHRITIS_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                messageRes = R.string.health_alert_temp_swing_advisory,
                messageArgs = listOf(swing.toInt()),
                detailRes = R.string.health_alert_temp_swing_advisory_detail,
            )
        }

        return null
    }
}

@Stable
data class HealthAlert(
    val type: HealthAlertType,
    val severity: HealthSeverity,
    @StringRes val messageRes: Int,
    val messageArgs: List<Any> = emptyList(),
    @StringRes val detailRes: Int? = null,
    val detailArgs: List<Any> = emptyList(),
)

enum class HealthAlertType(@StringRes val labelRes: Int) {
    MIGRAINE_TRIGGER(R.string.health_alert_type_migraine),
    RESPIRATORY(R.string.health_alert_type_respiratory),
    ARTHRITIS_TRIGGER(R.string.health_alert_type_arthritis),
}

enum class HealthSeverity(@StringRes val labelRes: Int, val color: Color) {
    WARNING(R.string.health_severity_warning, Color(0xFFFF5722)),
    ADVISORY(R.string.health_severity_advisory, Color(0xFFFF9800)),
    INFO(R.string.health_severity_info, Color(0xFF2196F3)),
}
