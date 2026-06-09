package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions

/**
 * Evaluates weather conditions for pet safety.
 * Considers pavement temperature estimates, heat index, cold thresholds,
 * and other hazards relevant to dogs and cats.
 */
object PetSafetyEvaluator {

    fun evaluate(current: CurrentConditions): List<PetSafetyAlert> {
        val alerts = mutableListOf<PetSafetyAlert>()

        // Pavement temperature estimate: on sunny days, pavement can be 20-30C hotter than air
        if (current.isDay && current.cloudCover < 50) {
            val pavementEstimate = current.temperature + 20 + (1 - current.cloudCover / 100.0) * 10
            when {
                pavementEstimate > 65 -> alerts.add(
                    PetSafetyAlert(
                        type = PetAlertType.HOT_PAVEMENT,
                        severity = PetSeverity.DANGER,
                        messageRes = R.string.pet_alert_hot_pavement_danger,
                        detailRes = R.string.pet_alert_hot_pavement_danger_detail,
                        messageArg = pavementEstimate.toInt(),
                    )
                )
                pavementEstimate > 50 -> alerts.add(
                    PetSafetyAlert(
                        type = PetAlertType.HOT_PAVEMENT,
                        severity = PetSeverity.WARNING,
                        messageRes = R.string.pet_alert_hot_pavement_warning,
                        detailRes = R.string.pet_alert_hot_pavement_warning_detail,
                        messageArg = pavementEstimate.toInt(),
                    )
                )
            }
        }

        // Heat index danger for pets (dogs overheat faster than humans)
        val heatIndex = calculateHeatIndex(current.temperature, current.humidity)
        when {
            heatIndex > 40 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.HEAT_STRESS,
                    severity = PetSeverity.DANGER,
                    messageRes = R.string.pet_alert_heat_extreme,
                    detailRes = R.string.pet_alert_heat_extreme_detail,
                )
            )
            heatIndex > 32 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.HEAT_STRESS,
                    severity = PetSeverity.WARNING,
                    messageRes = R.string.pet_alert_heat_high,
                    detailRes = R.string.pet_alert_heat_high_detail,
                )
            )
            heatIndex > 27 && current.humidity > 70 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.HEAT_STRESS,
                    severity = PetSeverity.CAUTION,
                    messageRes = R.string.pet_alert_heat_humid,
                    detailRes = R.string.pet_alert_heat_humid_detail,
                )
            )
        }

        // Cold weather warnings
        when {
            current.feelsLike < -15 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.COLD_EXPOSURE,
                    severity = PetSeverity.DANGER,
                    messageRes = R.string.pet_alert_cold_danger,
                    detailRes = R.string.pet_alert_cold_danger_detail,
                )
            )
            current.feelsLike < -5 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.COLD_EXPOSURE,
                    severity = PetSeverity.WARNING,
                    messageRes = R.string.pet_alert_cold_warning,
                    detailRes = R.string.pet_alert_cold_warning_detail,
                )
            )
            current.feelsLike < 5 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.COLD_EXPOSURE,
                    severity = PetSeverity.CAUTION,
                    messageRes = R.string.pet_alert_cold_chilly,
                    detailRes = R.string.pet_alert_cold_chilly_detail,
                )
            )
        }

        // Storm / thunder anxiety
        if (current.cape != null && current.cape > 1000) {
            alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.STORM_ANXIETY,
                    severity = if (current.cape > 2500) PetSeverity.WARNING else PetSeverity.CAUTION,
                    messageRes = R.string.pet_alert_storm,
                    detailRes = R.string.pet_alert_storm_detail,
                )
            )
        }

        return alerts.sortedBy { it.severity.ordinal }
    }

    private fun calculateHeatIndex(tempC: Double, humidity: Int): Double {
        val t = tempC
        val r = humidity.toDouble()
        if (t < 27 || r < 40) return t
        val hi = -8.785 + 1.611 * t + 2.339 * r - 0.1461 * t * r -
            0.01231 * t * t - 0.01642 * r * r +
            0.002212 * t * t * r + 0.0007255 * t * r * r -
            0.000003582 * t * t * r * r
        return maxOf(t, hi)
    }
}

@Stable
data class PetSafetyAlert(
    val type: PetAlertType,
    val severity: PetSeverity,
    @StringRes val messageRes: Int,
    @StringRes val detailRes: Int? = null,
    val messageArg: Int? = null,
)

enum class PetAlertType(val label: String) {
    HOT_PAVEMENT("Hot Pavement"),
    HEAT_STRESS("Heat Stress"),
    COLD_EXPOSURE("Cold Exposure"),
    STORM_ANXIETY("Storm Anxiety"),
}

enum class PetSeverity(val label: String, val color: Color) {
    DANGER("Danger", Color(0xFFD32F2F)),
    WARNING("Warning", Color(0xFFFF9800)),
    CAUTION("Caution", Color(0xFFFFEB3B)),
}
