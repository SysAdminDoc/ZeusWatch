package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.R
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
                messageRes = R.string.driving_alert_black_ice_danger,
            ))
        } else if (current.temperature <= 4.0 && current.humidity > 90) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.BLACK_ICE,
                severity = DrivingSeverity.CAUTION,
                messageRes = R.string.driving_alert_black_ice_caution,
            ))
        }

        // Fog: dewpoint spread < 3C or fog weather code
        val dewpointSpread = current.dewPoint?.let { current.temperature - it }
        if (current.weatherCode == WeatherCode.FOG || current.weatherCode == WeatherCode.DEPOSITING_RIME_FOG) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.FOG,
                severity = DrivingSeverity.CAUTION,
                messageRes = R.string.driving_alert_fog_caution,
            ))
        } else if (dewpointSpread != null && dewpointSpread < 3.0 && current.humidity > 90) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.FOG,
                severity = DrivingSeverity.ADVISORY,
                messageRes = R.string.driving_alert_fog_advisory,
            ))
        }

        // Low visibility
        current.visibility?.let { vis ->
            if (vis < 1000) {
                alerts.add(DrivingAlert(
                    type = DrivingAlertType.LOW_VISIBILITY,
                    severity = DrivingSeverity.DANGER,
                    messageRes = R.string.driving_alert_low_visibility_danger,
                ))
            } else if (vis < 5000) {
                alerts.add(DrivingAlert(
                    type = DrivingAlertType.LOW_VISIBILITY,
                    severity = DrivingSeverity.CAUTION,
                    messageRes = R.string.driving_alert_low_visibility_caution,
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
                messageRes = R.string.driving_alert_hydroplaning_caution,
            ))
        }

        // Strong winds
        val gusts = current.windGusts ?: current.windSpeed
        if (gusts > 80) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.HIGH_WIND,
                severity = DrivingSeverity.DANGER,
                messageRes = R.string.driving_alert_high_wind_danger,
            ))
        } else if (gusts > 50) {
            alerts.add(DrivingAlert(
                type = DrivingAlertType.HIGH_WIND,
                severity = DrivingSeverity.CAUTION,
                messageRes = R.string.driving_alert_high_wind_caution,
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
                messageRes = R.string.driving_alert_snow_ice_danger,
            ))
        }

        return alerts.sortedBy { it.severity.ordinal }
    }
}

@Stable
data class DrivingAlert(
    val type: DrivingAlertType,
    val severity: DrivingSeverity,
    @StringRes val messageRes: Int,
)

enum class DrivingAlertType(@StringRes val labelRes: Int, val icon: String) {
    BLACK_ICE(R.string.driving_alert_type_black_ice, "ac_unit"),
    FOG(R.string.driving_alert_type_fog, "foggy"),
    LOW_VISIBILITY(R.string.driving_alert_type_low_visibility, "visibility_off"),
    HYDROPLANING(R.string.driving_alert_type_hydroplaning, "water"),
    HIGH_WIND(R.string.driving_alert_type_high_wind, "air"),
    SNOW_ICE(R.string.driving_alert_type_snow_ice, "severe_cold"),
}

enum class DrivingSeverity(@StringRes val labelRes: Int, val color: Color) {
    DANGER(R.string.driving_severity_danger, Color(0xFFD32F2F)),
    CAUTION(R.string.driving_severity_caution, Color(0xFFFF9800)),
    ADVISORY(R.string.driving_severity_advisory, Color(0xFFFFEB3B)),
}
