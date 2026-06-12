package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions

/**
 * Rule-based clothing recommendations derived from current weather conditions.
 * Considers temperature (with wind chill), precipitation, UV, and wind.
 */
object ClothingSuggestionEvaluator {

    fun evaluate(current: CurrentConditions): List<ClothingSuggestion> {
        val suggestions = mutableListOf<ClothingSuggestion>()
        val feelsLike = current.feelsLike

        // Core layer recommendation based on feels-like temperature
        when {
            feelsLike <= -10 -> suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_heavy_winter_coat,
                    "Heavy winter coat, insulated layers, thermal underwear",
                    ClothingCategory.OUTERWEAR,
                )
            )
            feelsLike <= 0 -> suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_winter_coat,
                    "Winter coat with warm layers underneath",
                    ClothingCategory.OUTERWEAR,
                )
            )
            feelsLike <= 10 -> suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_warm_jacket,
                    "Warm jacket or fleece with a base layer",
                    ClothingCategory.OUTERWEAR,
                )
            )
            feelsLike <= 18 -> suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_light_jacket,
                    "Light jacket or sweater",
                    ClothingCategory.OUTERWEAR,
                )
            )
            feelsLike <= 25 -> suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_tshirt_light_sleeve,
                    "T-shirt or light long sleeve",
                    ClothingCategory.TOP,
                )
            )
            else -> suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_light_breathable,
                    "Light, breathable clothing",
                    ClothingCategory.TOP,
                )
            )
        }

        // Cold extremities protection
        if (feelsLike <= 5) {
            suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_warm_hat_gloves,
                    "Warm hat and gloves",
                    ClothingCategory.ACCESSORIES,
                )
            )
        }
        if (feelsLike <= -5) {
            suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_scarf_face_covering,
                    "Scarf or face covering",
                    ClothingCategory.ACCESSORIES,
                )
            )
        }

        // Rain gear
        if (current.precipitation > 0 || current.weatherCode.isRainy) {
            suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_rain_jacket_umbrella,
                    "Rain jacket or umbrella",
                    ClothingCategory.RAIN,
                )
            )
            if (current.precipitation > 2.0) {
                suggestions.add(
                    ClothingSuggestion(
                        R.string.clothing_waterproof_shoes,
                        "Waterproof shoes or boots",
                        ClothingCategory.FOOTWEAR,
                    )
                )
            }
        }

        // Snow gear
        if (current.snowfall != null && current.snowfall > 0) {
            suggestions.add(
                ClothingSuggestion(
                    R.string.clothing_waterproof_boots_socks,
                    "Waterproof boots and warm socks",
                    ClothingCategory.FOOTWEAR,
                )
            )
        }

        // Sun protection
        if (current.uvIndex >= 3 && current.isDay) {
            suggestions.add(ClothingSuggestion(R.string.clothing_sunglasses, "Sunglasses", ClothingCategory.ACCESSORIES))
            if (current.uvIndex >= 6) {
                suggestions.add(
                    ClothingSuggestion(
                        R.string.clothing_hat_sunscreen,
                        "Hat and sunscreen (SPF 30+)",
                        ClothingCategory.ACCESSORIES,
                    )
                )
            }
        }

        // Wind protection
        val gusts = current.windGusts ?: current.windSpeed
        if (gusts > 40 && feelsLike > 10) {
            suggestions.add(ClothingSuggestion(R.string.clothing_windbreaker, "Windbreaker", ClothingCategory.OUTERWEAR))
        }

        return suggestions
    }
}

@Stable
data class ClothingSuggestion(
    @StringRes val textRes: Int,
    val text: String,
    val category: ClothingCategory,
)

enum class ClothingCategory(val label: String, val icon: String) {
    OUTERWEAR("Outerwear", "checkroom"),
    TOP("Top", "checkroom"),
    ACCESSORIES("Accessories", "styler"),
    RAIN("Rain", "umbrella"),
    FOOTWEAR("Footwear", "do_not_step"),
}
