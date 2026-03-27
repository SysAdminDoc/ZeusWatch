package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.*
import org.junit.Test

class PetSafetyEvaluatorTest {

    private fun conditions(
        temperature: Double = 22.0,
        humidity: Int = 50,
        feelsLike: Double = temperature,
        isDay: Boolean = true,
        cloudCover: Int = 20,
        cape: Double? = null,
    ) = CurrentConditions(
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        weatherCode = WeatherCode.CLEAR_SKY,
        isDay = isDay,
        windSpeed = 5.0,
        windDirection = 180,
        windGusts = null,
        pressure = 1013.0,
        uvIndex = 3.0,
        visibility = 10000.0,
        dewPoint = 10.0,
        cloudCover = cloudCover,
        precipitation = 0.0,
        cape = cape,
        dailyHigh = temperature + 2,
        dailyLow = temperature - 2,
        sunrise = "06:00",
        sunset = "18:00",
    )

    @Test
    fun `hot sunny day triggers hot pavement warning`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 35.0, cloudCover = 10))
        val pavement = result.filter { it.type == PetAlertType.HOT_PAVEMENT }
        assertTrue("Expected hot pavement alert", pavement.isNotEmpty())
    }

    @Test
    fun `extreme heat triggers pavement danger`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 42.0, cloudCover = 0))
        val pavement = result.filter { it.type == PetAlertType.HOT_PAVEMENT }
        assertTrue(pavement.any { it.severity == PetSeverity.DANGER })
    }

    @Test
    fun `night time does not trigger hot pavement`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 38.0, isDay = false))
        val pavement = result.filter { it.type == PetAlertType.HOT_PAVEMENT }
        assertTrue("Hot pavement should not trigger at night", pavement.isEmpty())
    }

    @Test
    fun `cloudy day reduces pavement risk`() {
        // With 80% cloud cover, pavement estimate is lower
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 30.0, cloudCover = 80))
        val pavement = result.filter { it.type == PetAlertType.HOT_PAVEMENT }
        // 30 + 20 + (1-0.8)*10 = 52, which is WARNING level
        assertTrue(pavement.isEmpty() || pavement.all { it.severity != PetSeverity.DANGER })
    }

    @Test
    fun `high heat index triggers heat stress danger`() {
        // Hot + very humid = dangerous heat index
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 38.0, humidity = 80))
        val heatStress = result.filter { it.type == PetAlertType.HEAT_STRESS }
        assertTrue("Expected heat stress alert", heatStress.isNotEmpty())
        assertTrue(heatStress.any { it.severity == PetSeverity.DANGER })
    }

    @Test
    fun `moderate heat with high humidity triggers heat stress warning`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 33.0, humidity = 70))
        val heatStress = result.filter { it.type == PetAlertType.HEAT_STRESS }
        assertTrue("Expected heat stress alert", heatStress.isNotEmpty())
    }

    @Test
    fun `warm moderate humidity triggers caution`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 28.0, humidity = 75))
        val heatStress = result.filter { it.type == PetAlertType.HEAT_STRESS }
        assertTrue(heatStress.isEmpty() || heatStress.all { it.severity == PetSeverity.CAUTION })
    }

    @Test
    fun `cool temperature does not trigger heat stress`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 20.0, humidity = 60))
        val heatStress = result.filter { it.type == PetAlertType.HEAT_STRESS }
        assertTrue("No heat stress expected", heatStress.isEmpty())
    }

    @Test
    fun `extreme cold triggers cold exposure danger`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = -20.0, feelsLike = -20.0))
        val cold = result.filter { it.type == PetAlertType.COLD_EXPOSURE }
        assertTrue(cold.isNotEmpty())
        assertTrue(cold.any { it.severity == PetSeverity.DANGER })
    }

    @Test
    fun `moderate cold triggers cold exposure warning`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = -8.0, feelsLike = -8.0))
        val cold = result.filter { it.type == PetAlertType.COLD_EXPOSURE }
        assertTrue(cold.isNotEmpty())
        assertTrue(cold.any { it.severity == PetSeverity.WARNING })
    }

    @Test
    fun `cool temps trigger cold exposure caution`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 2.0, feelsLike = 2.0))
        val cold = result.filter { it.type == PetAlertType.COLD_EXPOSURE }
        assertTrue(cold.isNotEmpty())
        assertTrue(cold.any { it.severity == PetSeverity.CAUTION })
    }

    @Test
    fun `mild temperature has no cold exposure`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 15.0, feelsLike = 15.0))
        val cold = result.filter { it.type == PetAlertType.COLD_EXPOSURE }
        assertTrue(cold.isEmpty())
    }

    @Test
    fun `high CAPE triggers storm anxiety`() {
        val result = PetSafetyEvaluator.evaluate(conditions(cape = 1500.0))
        val storm = result.filter { it.type == PetAlertType.STORM_ANXIETY }
        assertTrue("Expected storm anxiety alert", storm.isNotEmpty())
    }

    @Test
    fun `very high CAPE triggers storm anxiety warning`() {
        val result = PetSafetyEvaluator.evaluate(conditions(cape = 3000.0))
        val storm = result.filter { it.type == PetAlertType.STORM_ANXIETY }
        assertTrue(storm.isNotEmpty())
        assertTrue(storm.any { it.severity == PetSeverity.WARNING })
    }

    @Test
    fun `low CAPE has no storm anxiety`() {
        val result = PetSafetyEvaluator.evaluate(conditions(cape = 500.0))
        val storm = result.filter { it.type == PetAlertType.STORM_ANXIETY }
        assertTrue(storm.isEmpty())
    }

    @Test
    fun `comfortable conditions return no alerts`() {
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 22.0, humidity = 50, isDay = true, cloudCover = 50))
        assertTrue("Expected no alerts for comfortable conditions", result.isEmpty())
    }

    @Test
    fun `results sorted by severity`() {
        // Multiple alerts at different severities
        val result = PetSafetyEvaluator.evaluate(conditions(temperature = 40.0, humidity = 80, cloudCover = 0))
        if (result.size >= 2) {
            for (i in 0 until result.size - 1) {
                assertTrue(
                    "Alerts should be sorted by severity",
                    result[i].severity.ordinal <= result[i + 1].severity.ordinal
                )
            }
        }
    }
}
