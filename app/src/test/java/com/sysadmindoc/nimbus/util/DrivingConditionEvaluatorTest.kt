package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.*
import org.junit.Test

class DrivingConditionEvaluatorTest {

    private fun conditions(
        temperature: Double = 15.0,
        precipitation: Double = 0.0,
        humidity: Int = 50,
        weatherCode: WeatherCode = WeatherCode.CLEAR_SKY,
        dewPoint: Double? = 5.0,
        visibility: Double? = 15000.0,
        windGusts: Double? = null,
        windSpeed: Double = 10.0,
    ) = CurrentConditions(
        temperature = temperature,
        feelsLike = temperature,
        humidity = humidity,
        weatherCode = weatherCode,
        isDay = true,
        windSpeed = windSpeed,
        windDirection = 180,
        windGusts = windGusts,
        pressure = 1013.0,
        uvIndex = 3.0,
        visibility = visibility,
        dewPoint = dewPoint,
        cloudCover = 50,
        precipitation = precipitation,
        dailyHigh = temperature + 5,
        dailyLow = temperature - 5,
        sunrise = "06:00",
        sunset = "18:00",
    )

    // ── Black Ice Tests ──

    @Test
    fun `near freezing with precipitation triggers black ice danger`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(temperature = 1.0, precipitation = 0.5))
        val ice = result.filter { it.type == DrivingAlertType.BLACK_ICE }
        assertTrue("Expected black ice alert", ice.isNotEmpty())
        assertTrue(ice.any { it.severity == DrivingSeverity.DANGER })
    }

    @Test
    fun `near freezing with high humidity triggers black ice`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(temperature = 1.5, humidity = 90))
        val ice = result.filter { it.type == DrivingAlertType.BLACK_ICE }
        assertTrue("Expected black ice alert for cold+humid", ice.isNotEmpty())
    }

    @Test
    fun `slightly above freezing with very high humidity triggers black ice caution`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(temperature = 3.5, humidity = 92))
        val ice = result.filter { it.type == DrivingAlertType.BLACK_ICE }
        assertTrue("Expected black ice caution", ice.isNotEmpty())
    }

    @Test
    fun `warm temperature has no black ice`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(temperature = 10.0, precipitation = 1.0))
        val ice = result.filter { it.type == DrivingAlertType.BLACK_ICE }
        assertTrue(ice.isEmpty())
    }

    // ── Fog Tests ──

    @Test
    fun `fog weather code triggers fog alert`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(weatherCode = WeatherCode.FOG))
        val fog = result.filter { it.type == DrivingAlertType.FOG }
        assertTrue("Expected fog alert", fog.isNotEmpty())
    }

    @Test
    fun `freezing fog triggers fog alert`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(weatherCode = WeatherCode.DEPOSITING_RIME_FOG))
        val fog = result.filter { it.type == DrivingAlertType.FOG }
        assertTrue("Expected freezing fog alert", fog.isNotEmpty())
    }

    @Test
    fun `small dew point spread with high humidity triggers fog advisory`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(temperature = 10.0, dewPoint = 8.0, humidity = 92))
        val fog = result.filter { it.type == DrivingAlertType.FOG }
        assertTrue("Expected fog advisory from dewpoint spread", fog.isNotEmpty())
    }

    // ── Low Visibility Tests ──

    @Test
    fun `very low visibility triggers danger`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(visibility = 500.0))
        val vis = result.filter { it.type == DrivingAlertType.LOW_VISIBILITY }
        assertTrue(vis.isNotEmpty())
        assertTrue(vis.any { it.severity == DrivingSeverity.DANGER })
    }

    @Test
    fun `moderately low visibility triggers caution`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(visibility = 3000.0))
        val vis = result.filter { it.type == DrivingAlertType.LOW_VISIBILITY }
        assertTrue(vis.isNotEmpty())
        assertTrue(vis.any { it.severity == DrivingSeverity.CAUTION })
    }

    @Test
    fun `good visibility has no alert`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(visibility = 10000.0))
        val vis = result.filter { it.type == DrivingAlertType.LOW_VISIBILITY }
        assertTrue(vis.isEmpty())
    }

    // ── Hydroplaning Tests ──

    @Test
    fun `heavy precipitation triggers hydroplaning`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(precipitation = 6.0))
        val hydro = result.filter { it.type == DrivingAlertType.HYDROPLANING }
        assertTrue("Expected hydroplaning alert", hydro.isNotEmpty())
    }

    @Test
    fun `heavy rain weather code triggers hydroplaning`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(weatherCode = WeatherCode.RAIN_HEAVY))
        val hydro = result.filter { it.type == DrivingAlertType.HYDROPLANING }
        assertTrue("Expected hydroplaning from heavy rain code", hydro.isNotEmpty())
    }

    @Test
    fun `light rain has no hydroplaning`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(precipitation = 1.0))
        val hydro = result.filter { it.type == DrivingAlertType.HYDROPLANING }
        assertTrue(hydro.isEmpty())
    }

    // ── High Wind Tests ──

    @Test
    fun `extreme wind gusts trigger danger`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(windGusts = 90.0))
        val wind = result.filter { it.type == DrivingAlertType.HIGH_WIND }
        assertTrue(wind.isNotEmpty())
        assertTrue(wind.any { it.severity == DrivingSeverity.DANGER })
    }

    @Test
    fun `strong wind gusts trigger caution`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(windGusts = 55.0))
        val wind = result.filter { it.type == DrivingAlertType.HIGH_WIND }
        assertTrue(wind.isNotEmpty())
        assertTrue(wind.any { it.severity == DrivingSeverity.CAUTION })
    }

    @Test
    fun `light wind has no alert`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(windGusts = 20.0, windSpeed = 10.0))
        val wind = result.filter { it.type == DrivingAlertType.HIGH_WIND }
        assertTrue(wind.isEmpty())
    }

    // ── Snow/Ice Tests ──

    @Test
    fun `heavy snow triggers snow ice danger`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(weatherCode = WeatherCode.SNOW_HEAVY))
        val snow = result.filter { it.type == DrivingAlertType.SNOW_ICE }
        assertTrue(snow.isNotEmpty())
        assertTrue(snow.any { it.severity == DrivingSeverity.DANGER })
    }

    @Test
    fun `freezing rain triggers snow ice danger`() {
        val result = DrivingConditionEvaluator.evaluate(conditions(weatherCode = WeatherCode.FREEZING_RAIN_HEAVY))
        val snow = result.filter { it.type == DrivingAlertType.SNOW_ICE }
        assertTrue(snow.isNotEmpty())
    }

    // ── General ──

    @Test
    fun `clear conditions return no alerts`() {
        val result = DrivingConditionEvaluator.evaluate(conditions())
        assertTrue("Expected no driving alerts", result.isEmpty())
    }

    @Test
    fun `results sorted by severity`() {
        // Trigger multiple alerts: freezing + fog + low visibility
        val result = DrivingConditionEvaluator.evaluate(
            conditions(temperature = 0.5, precipitation = 0.5, visibility = 800.0, weatherCode = WeatherCode.FOG)
        )
        if (result.size >= 2) {
            for (i in 0 until result.size - 1) {
                assertTrue(result[i].severity.ordinal <= result[i + 1].severity.ordinal)
            }
        }
    }
}
