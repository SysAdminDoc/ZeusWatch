package com.sysadmindoc.nimbus.util

import androidx.compose.runtime.Stable
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
                ClothingSuggestion("Heavy winter coat, insulated layers, thermal underwear", ClothingCategory.OUTERWEAR)
            )
            feelsLike <= 0 -> suggestions.add(
                ClothingSuggestion("Winter coat with warm layers underneath", ClothingCategory.OUTERWEAR)
            )
            feelsLike <= 10 -> suggestions.add(
                ClothingSuggestion("Warm jacket or fleece with a base layer", ClothingCategory.OUTERWEAR)
            )
            feelsLike <= 18 -> suggestions.add(
                ClothingSuggestion("Light jacket or sweater", ClothingCategory.OUTERWEAR)
            )
            feelsLike <= 25 -> suggestions.add(
                ClothingSuggestion("T-shirt or light long sleeve", ClothingCategory.TOP)
            )
            else -> suggestions.add(
                ClothingSuggestion("Light, breathable clothing", ClothingCategory.TOP)
            )
        }

        // Cold extremities protection
        if (feelsLike <= 5) {
            suggestions.add(ClothingSuggestion("Warm hat and gloves", ClothingCategory.ACCESSORIES))
        }
        if (feelsLike <= -5) {
            suggestions.add(ClothingSuggestion("Scarf or face covering", ClothingCategory.ACCESSORIES))
        }

        // Rain gear
        if (current.precipitation > 0 || current.weatherCode.isRainy) {
            suggestions.add(ClothingSuggestion("Rain jacket or umbrella", ClothingCategory.RAIN))
            if (current.precipitation > 2.0) {
                suggestions.add(ClothingSuggestion("Waterproof shoes or boots", ClothingCategory.FOOTWEAR))
            }
        }

        // Snow gear
        if (current.snowfall != null && current.snowfall > 0) {
            suggestions.add(ClothingSuggestion("Waterproof boots and warm socks", ClothingCategory.FOOTWEAR))
        }

        // Sun protection
        if (current.uvIndex >= 3 && current.isDay) {
            suggestions.add(ClothingSuggestion("Sunglasses", ClothingCategory.ACCESSORIES))
            if (current.uvIndex >= 6) {
                suggestions.add(ClothingSuggestion("Hat and sunscreen (SPF 30+)", ClothingCategory.ACCESSORIES))
            }
        }

        // Wind protection
        val gusts = current.windGusts ?: current.windSpeed
        if (gusts > 40 && feelsLike > 10) {
            suggestions.add(ClothingSuggestion("Windbreaker", ClothingCategory.OUTERWEAR))
        }

        return suggestions
    }
}

@Stable
data class ClothingSuggestion(
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
