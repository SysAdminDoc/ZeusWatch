package com.sysadmindoc.nimbus.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode

/**
 * Evaluates current weather conditions for driving hazards.
 * Derives alerts from standard forecast data (no external API needed).
 */
object DrivingConditionEvaluator {

    fun evaluate(current: CurrentConditions): List<DrivingAlert> {
        val alerts = mutableListOf<DrivingAlert>()

        // Black ice: temp near/below freezing + precipitation or high humidity
        if (current.temperature <= 2.0 && (current.precipitation > 0 || current.humidity > 85)) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.BLACK_ICE,
                severity = DrivingSeverity.DANGER,
                message = "Black ice likely. Roads may be slippery.",
            ))
        } else if (current.temperature <= 4.0 && current.humidity > 90) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.BLACK_ICE,
                severity = DrivingSeverity.CAUTION,
                message = "Near-freezing with high humidity. Watch for icy patches.",
            ))
        }

        // Fog: dewpoint spread < 3C or fog weather code
        val dewpointSpread = current.dewPoint?.let { current.temperature - it }
        if (current.weatherCode == WeatherCode.FOG || current.weatherCode == WeatherCode.DEPOSITING_RIME_FOG) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.FOG,
                severity = DrivingSeverity.CAUTION,
                message = "Foggy conditions. Reduced visibility.",
            ))
        } else if (dewpointSpread != null && dewpointSpread < 3.0 && current.humidity > 90) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.FOG,
                severity = DrivingSeverity.ADVISORY,
                message = "Fog possible with high humidity and narrow dewpoint spread.",
            ))
        }

        // Low visibility
        current.visibility?.let { vis ->
            if (vis < 1000) {
                alerts.add(DrivingAlert(
                    type = DrivingAlertType.LOW_VISIBILITY,
                    severity = DrivingSeverity.DANGER,
                    message = "Very low visibility (<1 km). Use fog lights.",
                ))
            } else if (vis < 5000) {
                alerts.add(DrivingAlert(
                    type = DrivingAlertType.LOW_VISIBILITY,
                    severity = DrivingSeverity.CAUTION,
                    message = "Reduced visibility. Drive carefully.",
                ))
            }
        }

        // Hydroplaning: heavy rain
        if (current.precipitation > 5.0 || current.weatherCode == WeatherCode.RAIN_HEAVY ||
            current.weatherCode == WeatherCode.RAIN_SHOWERS_VIOLENT
        ) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.HYDROPLANING,
                severity = DrivingSeverity.CAUTION,
                message = "Heavy rain. Risk of hydroplaning at highway speeds.",
            ))
        }

        // Strong winds
        val gusts = current.windGusts ?: current.windSpeed
        if (gusts > 80) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.HIGH_WIND,
                severity = DrivingSeverity.DANGER,
                message = "Dangerous winds. Avoid driving if possible.",
            ))
        } else if (gusts > 50) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.HIGH_WIND,
                severity = DrivingSeverity.CAUTION,
                message = "Strong winds may affect vehicle handling.",
            ))
        }

        // Snow/ice on roads
        if (current.weatherCode in listOf(
                WeatherCode.SNOW_MODERATE, WeatherCode.SNOW_HEAVY,
                WeatherCode.SNOW_SHOWERS_HEAVY, WeatherCode.FREEZING_RAIN_LIGHT,
                WeatherCode.FREEZING_RAIN_HEAVY
            )
        ) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.SNOW_ICE,
                severity = DrivingSeverity.DANGER,
                message = "Snow or freezing rain. Roads will be hazardous.",
            ))
        }

        return alerts.sortedBy { it.severity.ordinal }
    }
}

@Stable
data class DrivingAlert(
    val type: DrivingAlertType,
    val severity: DrivingSeverity,
    val message: String,
)

enum class DrivingAlertType(val label: String, val icon: String) {
    BLACK_ICE("Black Ice", "ac_unit"),
    FOG("Fog", "foggy"),
    LOW_VISIBILITY("Low Visibility", "visibility_off"),
    HYDROPLANING("Hydroplaning", "water"),
    HIGH_WIND("High Wind", "air"),
    SNOW_ICE("Snow/Ice", "severe_cold"),
}

enum class DrivingSeverity(val label: String, val color: Color) {
    DANGER("Danger", Color(0xFFD32F2F)),
    CAUTION("Caution", Color(0xFFFF9800)),
    ADVISORY("Advisory", Color(0xFFFFEB3B)),
}
