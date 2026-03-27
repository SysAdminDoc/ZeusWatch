package com.sysadmindoc.nimbus.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
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
                        message = "Pavement dangerously hot (~${pavementEstimate.toInt()}\u00B0C).",
                        detail = "Can burn paw pads in seconds. Walk on grass or wait until evening.",
                    )
                )
                pavementEstimate > 50 -> alerts.add(
                    PetSafetyAlert(
                        type = PetAlertType.HOT_PAVEMENT,
                        severity = PetSeverity.WARNING,
                        message = "Pavement is hot (~${pavementEstimate.toInt()}\u00B0C).",
                        detail = "Test pavement with your hand. If too hot for you, it's too hot for paws.",
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
                    message = "Extreme heat risk for pets.",
                    detail = "Keep pets indoors with water and AC. Do not leave in vehicles.",
                )
            )
            heatIndex > 32 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.HEAT_STRESS,
                    severity = PetSeverity.WARNING,
                    message = "High heat stress risk for pets.",
                    detail = "Limit outdoor time. Provide shade and fresh water. Watch for panting.",
                )
            )
            heatIndex > 27 && current.humidity > 70 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.HEAT_STRESS,
                    severity = PetSeverity.CAUTION,
                    message = "Warm and humid \u2014 monitor pets outdoors.",
                    detail = "Brachycephalic breeds (pugs, bulldogs) are especially vulnerable.",
                )
            )
        }

        // Cold weather warnings
        when {
            current.feelsLike < -15 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.COLD_EXPOSURE,
                    severity = PetSeverity.DANGER,
                    message = "Dangerously cold for pets.",
                    detail = "Frostbite risk on ears, paws, and tail. Keep outdoor time under 5 minutes.",
                )
            )
            current.feelsLike < -5 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.COLD_EXPOSURE,
                    severity = PetSeverity.WARNING,
                    message = "Very cold \u2014 limit pet outdoor time.",
                    detail = "Small dogs and short-haired breeds need a coat. Check paws for ice buildup.",
                )
            )
            current.feelsLike < 5 -> alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.COLD_EXPOSURE,
                    severity = PetSeverity.CAUTION,
                    message = "Chilly for small or short-haired pets.",
                    detail = "Consider a sweater for sensitive breeds.",
                )
            )
        }

        // Storm / thunder anxiety
        if (current.cape != null && current.cape > 1000) {
            alerts.add(
                PetSafetyAlert(
                    type = PetAlertType.STORM_ANXIETY,
                    severity = if (current.cape > 2500) PetSeverity.WARNING else PetSeverity.CAUTION,
                    message = "Thunderstorms possible \u2014 prepare anxious pets.",
                    detail = "Create a safe space. Consider calming aids if your pet is storm-sensitive.",
                )
            )
        }

        return alerts.sortedBy { it.severity.ordinal }
    }

    private fun calculateHeatIndex(tempC: Double, humidity: Int): Double {
        // Simplified heat index (Celsius). Steadman's formula approximation.
        val t = tempC
        val r = humidity.toDouble()
        if (t < 27 || r < 40) return t
        return -8.785 + 1.611 * t + 2.339 * r - 0.1461 * t * r -
            0.01231 * t * t - 0.01642 * r * r +
            0.002212 * t * t * r + 0.0007255 * t * r * r -
            0.000003582 * t * t * r * r
    }
}

@Stable
data class PetSafetyAlert(
    val type: PetAlertType,
    val severity: PetSeverity,
    val message: String,
    val detail: String = "",
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
