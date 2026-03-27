package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.*
import org.junit.Test

class ClothingSuggestionEvaluatorTest {

    private fun conditions(
        feelsLike: Double = 20.0,
        precipitation: Double = 0.0,
        weatherCode: WeatherCode = WeatherCode.CLEAR_SKY,
        snowfall: Double? = null,
        uvIndex: Double = 0.0,
        isDay: Boolean = true,
        windGusts: Double? = null,
        windSpeed: Double = 5.0,
        cloudCover: Int = 0,
    ) = CurrentConditions(
        temperature = feelsLike,
        feelsLike = feelsLike,
        humidity = 50,
        weatherCode = weatherCode,
        isDay = isDay,
        windSpeed = windSpeed,
        windDirection = 180,
        windGusts = windGusts,
        pressure = 1013.0,
        uvIndex = uvIndex,
        visibility = 10000.0,
        dewPoint = 10.0,
        cloudCover = cloudCover,
        precipitation = precipitation,
        snowfall = snowfall,
        dailyHigh = feelsLike + 2,
        dailyLow = feelsLike - 2,
        sunrise = "06:00",
        sunset = "18:00",
    )

    @Test
    fun `extreme cold suggests heavy winter coat and thermal layers`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = -15.0))
        val outerwear = result.filter { it.category == ClothingCategory.OUTERWEAR }
        assertTrue(outerwear.any { it.text.contains("Heavy winter coat", ignoreCase = true) })
    }

    @Test
    fun `below freezing suggests winter coat`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = -3.0))
        val outerwear = result.filter { it.category == ClothingCategory.OUTERWEAR }
        assertTrue(outerwear.any { it.text.contains("Winter coat", ignoreCase = true) })
    }

    @Test
    fun `cool weather suggests jacket or sweater`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = 14.0))
        val outerwear = result.filter { it.category == ClothingCategory.OUTERWEAR }
        assertTrue(outerwear.any { it.text.contains("jacket", ignoreCase = true) || it.text.contains("sweater", ignoreCase = true) })
    }

    @Test
    fun `warm weather suggests light clothing`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = 30.0))
        val tops = result.filter { it.category == ClothingCategory.TOP }
        assertTrue(tops.any { it.text.contains("Light", ignoreCase = true) || it.text.contains("breathable", ignoreCase = true) })
    }

    @Test
    fun `cold weather suggests hat and gloves`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = 2.0))
        val accessories = result.filter { it.category == ClothingCategory.ACCESSORIES }
        assertTrue(accessories.any { it.text.contains("hat", ignoreCase = true) || it.text.contains("gloves", ignoreCase = true) })
    }

    @Test
    fun `very cold suggests scarf or face covering`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = -8.0))
        val accessories = result.filter { it.category == ClothingCategory.ACCESSORIES }
        assertTrue(accessories.any { it.text.contains("Scarf", ignoreCase = true) || it.text.contains("face", ignoreCase = true) })
    }

    @Test
    fun `rain suggests rain jacket or umbrella`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(precipitation = 1.0))
        val rain = result.filter { it.category == ClothingCategory.RAIN }
        assertTrue(rain.isNotEmpty())
        assertTrue(rain.any { it.text.contains("Rain jacket", ignoreCase = true) || it.text.contains("umbrella", ignoreCase = true) })
    }

    @Test
    fun `rainy weather code triggers rain gear even with zero precipitation`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(weatherCode = WeatherCode.RAIN_MODERATE))
        val rain = result.filter { it.category == ClothingCategory.RAIN }
        assertTrue(rain.isNotEmpty())
    }

    @Test
    fun `heavy rain suggests waterproof shoes`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(precipitation = 3.0))
        val footwear = result.filter { it.category == ClothingCategory.FOOTWEAR }
        assertTrue(footwear.any { it.text.contains("Waterproof", ignoreCase = true) })
    }

    @Test
    fun `snow suggests waterproof boots`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(snowfall = 2.0))
        val footwear = result.filter { it.category == ClothingCategory.FOOTWEAR }
        assertTrue(footwear.any { it.text.contains("boots", ignoreCase = true) })
    }

    @Test
    fun `moderate UV suggests sunglasses`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(uvIndex = 4.0, isDay = true))
        val accessories = result.filter { it.category == ClothingCategory.ACCESSORIES }
        assertTrue(accessories.any { it.text.contains("Sunglasses", ignoreCase = true) })
    }

    @Test
    fun `high UV suggests hat and sunscreen`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(uvIndex = 7.0, isDay = true))
        val accessories = result.filter { it.category == ClothingCategory.ACCESSORIES }
        assertTrue(accessories.any { it.text.contains("Hat", ignoreCase = true) || it.text.contains("sunscreen", ignoreCase = true) })
    }

    @Test
    fun `UV at night does not suggest sunglasses`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(uvIndex = 5.0, isDay = false))
        val accessories = result.filter { it.category == ClothingCategory.ACCESSORIES }
        assertFalse(accessories.any { it.text.contains("Sunglasses", ignoreCase = true) })
    }

    @Test
    fun `strong wind gusts suggest windbreaker`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = 15.0, windGusts = 50.0))
        val outerwear = result.filter { it.category == ClothingCategory.OUTERWEAR }
        assertTrue(outerwear.any { it.text.contains("Windbreaker", ignoreCase = true) })
    }

    @Test
    fun `clear warm day returns minimal suggestions`() {
        val result = ClothingSuggestionEvaluator.evaluate(conditions(feelsLike = 22.0))
        assertTrue(result.isNotEmpty()) // At least a top suggestion
        assertFalse(result.any { it.category == ClothingCategory.RAIN })
        assertFalse(result.any { it.category == ClothingCategory.FOOTWEAR })
    }
}
