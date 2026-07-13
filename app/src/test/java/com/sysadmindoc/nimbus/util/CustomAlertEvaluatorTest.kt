package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CustomAlertEvaluatorTest {

    private val baseTime = LocalDateTime.of(2026, 1, 15, 9, 0)

    @Test
    fun `snowfall rule compares in millimeters (cm forecast x10)`() {
        // Open-Meteo snowfall is centimeters. 3 cm total = 30 mm, which must
        // exceed a 20 mm threshold. The pre-fix bug summed raw cm (3) and never
        // fired.
        val rule = snowfallRule(thresholdMm = 20.0)
        val data = weatherWithHourlySnowfallCm(listOf(1.0, 1.0, 1.0)) // 3 cm total

        val triggered = evaluateCustomAlertRules(listOf(rule), data)

        assertEquals(1, triggered.size)
        assertEquals(rule.id, triggered.first().rule.id)
        // Observed value is reported in canonical mm (30.0), not raw cm.
        assertEquals(30.0, triggered.first().observedCanonical, 0.001)
    }

    @Test
    fun `snowfall rule below threshold does not fire`() {
        val rule = snowfallRule(thresholdMm = 20.0)
        val data = weatherWithHourlySnowfallCm(listOf(1.0, 0.5)) // 1.5 cm = 15 mm

        assertTrue(evaluateCustomAlertRules(listOf(rule), data).isEmpty())
    }

    private fun snowfallRule(thresholdMm: Double) = CustomAlertRule(
        id = "snow-1",
        metric = CustomAlertMetric.SNOWFALL_SUM_NEXT_24H,
        operator = CustomAlertOperator.GREATER_THAN,
        thresholdCanonical = thresholdMm,
        enabled = true,
    )

    private fun weatherWithHourlySnowfallCm(snowfallCm: List<Double>): WeatherData {
        val hourly = snowfallCm.mapIndexed { i, cm ->
            HourlyConditions(
                time = baseTime.plusHours(i.toLong()),
                temperature = 0.0,
                feelsLike = null,
                weatherCode = WeatherCode.SNOW_SLIGHT,
                isDay = true,
                precipitationProbability = 100,
                precipitation = null,
                windSpeed = null,
                windDirection = null,
                humidity = null,
                uvIndex = null,
                cloudCover = null,
                visibility = null,
                snowfall = cm,
            )
        }
        return WeatherData(
            location = LocationInfo(name = "Test", latitude = 40.0, longitude = -74.0),
            current = current(),
            hourly = hourly,
            daily = emptyList(),
        )
    }

    private fun current() = CurrentConditions(
        temperature = 0.0,
        feelsLike = 0.0,
        humidity = 90,
        weatherCode = WeatherCode.SNOW_SLIGHT,
        isDay = true,
        windSpeed = 0.0,
        windDirection = 0,
        windGusts = null,
        pressure = 1000.0,
        uvIndex = 0.0,
        visibility = null,
        dewPoint = null,
        cloudCover = 100,
        precipitation = 0.0,
        dailyHigh = 1.0,
        dailyLow = -5.0,
        sunrise = null,
        sunset = null,
    )
}
