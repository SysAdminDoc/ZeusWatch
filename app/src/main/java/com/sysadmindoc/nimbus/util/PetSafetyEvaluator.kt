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

    /** UV index at or above which sun is strong enough to superheat pavement. */
    private const val STRONG_SUN_UV = 4.0

    /** Weak-sun residual-heat adder (°C) when UV is below [STRONG_SUN_UV]. */
    private const val WEAK_SUN_PAVEMENT_ADDER = 10.0

    /** Estimated pavement temperature (°C) at which a WARNING fires. */
    private const val PAVEMENT_WARNING_FLOOR = 55.0

    /** Air temperature (°C) that warrants a WARNING under strong sun even below the pavement floor. */
    private const val AIR_TEMP_WARNING_FLOOR = 25.0

    fun evaluate(current: CurrentConditions): List<PetSafetyAlert> {
        val alerts = mutableListOf<PetSafetyAlert>()

        // Pavement temperature estimate: under strong sun, pavement can run
        // 20-30C hotter than air. The full solar adder only applies when the
        // sun is plausibly strong (UV >= STRONG_SUN_UV) — a clear 20C morning
        // under weak sun was previously claiming ~50C pavement. Weak sun gets
        // a reduced residual-heat adder instead, and a WARNING needs either an
        // estimated pavement >= 55C or air >= 25C with strong sun.
        if (current.isDay && current.cloudCover < 50) {
            val strongSun = current.uvIndex >= STRONG_SUN_UV
            val solarAdder = if (strongSun) {
                20 + (1 - current.cloudCover / 100.0) * 10
            } else {
                WEAK_SUN_PAVEMENT_ADDER
            }
            val pavementEstimate = current.temperature + solarAdder
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
                pavementEstimate >= PAVEMENT_WARNING_FLOOR ||
                    (strongSun && current.temperature >= AIR_TEMP_WARNING_FLOOR) -> alerts.add(
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
