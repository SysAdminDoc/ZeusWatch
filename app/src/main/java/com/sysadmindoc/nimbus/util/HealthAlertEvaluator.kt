package com.sysadmindoc.nimbus.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.data.model.HourlyConditions

/**
 * Evaluates weather conditions for health-related triggers.
 * Monitors pressure changes (migraines), humidity extremes (respiratory),
 * and temperature swings (arthritis).
 */
object HealthAlertEvaluator {

    /**
     * Evaluate health alerts from hourly pressure data.
     * @param hourly Recent hourly conditions (past + forecast)
     * @param pressureThresholdHpa Pressure change threshold in hPa over 3 hours
     */
    fun evaluate(
        hourly: List<HourlyConditions>,
        pressureThresholdHpa: Double = 5.0,
    ): List<HealthAlert> {
        val alerts = mutableListOf<HealthAlert>()

        // Analyze pressure trends over 3-hour windows
        val pressureAlert = evaluatePressureChange(hourly, pressureThresholdHpa)
        if (pressureAlert != null) alerts.add(pressureAlert)

        // High humidity respiratory alert
        val humidityAlert = evaluateHumidity(hourly)
        if (humidityAlert != null) alerts.add(humidityAlert)

        // Temperature swing (arthritis trigger)
        val tempSwingAlert = evaluateTemperatureSwing(hourly)
        if (tempSwingAlert != null) alerts.add(tempSwingAlert)

        return alerts
    }

    private fun evaluatePressureChange(
        hourly: List<HourlyConditions>,
        thresholdHpa: Double,
    ): HealthAlert? {
        // We don't have pressure in hourly data directly, but we can check
        // if the first few hours show rapid temp/humidity shifts as a proxy
        // For now, use the hourly data we have. In the future, add hourly pressure.

        // Look at the next 6 hours of data for rapid changes
        val next6 = hourly.take(6)
        if (next6.size < 3) return null

        // Check humidity as a proxy for frontal passage
        val humidityChange = next6.maxOf { it.humidity ?: 0 } - next6.minOf { it.humidity ?: 0 }
        val tempChange = next6.maxOf { it.temperature } - next6.minOf { it.temperature }

        // Large temp + humidity swings indicate frontal passage = pressure change
        if (tempChange > 8 && humidityChange > 30) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.WARNING,
                message = "Rapid weather changes ahead. Migraine trigger likely.",
                detail = "Temperature swing of ${tempChange.toInt()}\u00B0C and humidity change of $humidityChange% in the next 6 hours.",
            )
        }

        if (tempChange > 5 && humidityChange > 20) {
            return HealthAlert(
                type = HealthAlertType.MIGRAINE_TRIGGER,
                severity = HealthSeverity.ADVISORY,
                message = "Moderate weather changes ahead. Possible migraine trigger.",
                detail = "Temperature and humidity shifts detected in the next 6 hours.",
            )
        }

        return null
    }

    private fun evaluateHumidity(hourly: List<HourlyConditions>): HealthAlert? {
        val current = hourly.firstOrNull() ?: return null
        val humidity = current.humidity ?: return null

        return when {
            humidity > 85 -> HealthAlert(
                type = HealthAlertType.RESPIRATORY,
                severity = HealthSeverity.ADVISORY,
                message = "Very high humidity ($humidity%).",
                detail = "May aggravate respiratory conditions. Stay hydrated and limit strenuous activity.",
            )
            humidity < 20 -> HealthAlert(
                type = HealthAlertType.RESPIRATORY,
                severity = HealthSeverity.ADVISORY,
                message = "Very low humidity ($humidity%).",
                detail = "Dry air may irritate airways. Consider using a humidifier.",
            )
            else -> null
        }
    }

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
                detail = "Rapid temperature changes may worsen joint pain and arthritis symptoms.",
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
