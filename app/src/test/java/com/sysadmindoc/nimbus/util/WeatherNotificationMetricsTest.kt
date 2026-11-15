package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherNotificationMetricsTest {

    private val labels = WeatherMetricLabels(
        feelsLike = "Feels like",
        uvIndex = "UV Index",
        humidity = "Humidity",
        wind = "Wind",
    )

    @Test
    fun daytimeShowsFeelsLikeUvAndWindInTheUsersUnits() {
        val metrics = weatherNotificationMetrics(
            current(isDay = true, uvIndex = 7.6),
            NimbusSettings(tempUnit = TempUnit.FAHRENHEIT, windUnit = WindUnit.MPH),
            labels,
        )

        assertEquals(
            listOf(
                WeatherMetric("Feels like", "68°", MetricTone.UNSPECIFIED),
                WeatherMetric("UV Index", "7", MetricTone.CAUTION),
                WeatherMetric("Wind", "W 20 mph", MetricTone.UNSPECIFIED),
            ),
            metrics,
        )
    }

    @Test
    fun nightSwapsUvForHumidity() {
        val metrics = weatherNotificationMetrics(
            current(isDay = false, uvIndex = 0.0),
            NimbusSettings(tempUnit = TempUnit.CELSIUS, windUnit = WindUnit.KMH),
            labels,
        )

        assertEquals(
            listOf(
                WeatherMetric("Feels like", "20°", MetricTone.UNSPECIFIED),
                WeatherMetric("Humidity", "85%", MetricTone.UNSPECIFIED),
                WeatherMetric("Wind", "W 32 km/h", MetricTone.UNSPECIFIED),
            ),
            metrics,
        )
    }

    @Test
    fun uvTonesFollowTheUvCardBands() {
        assertEquals(MetricTone.SAFE, uvTone(0.0))
        assertEquals(MetricTone.SAFE, uvTone(2.9))
        assertEquals(MetricTone.CAUTION, uvTone(3.0))
        assertEquals(MetricTone.CAUTION, uvTone(7.9))
        assertEquals(MetricTone.DANGER, uvTone(8.0))
        assertEquals(MetricTone.DANGER, uvTone(11.5))
    }

    private fun current(isDay: Boolean, uvIndex: Double) = CurrentConditions(
        temperature = 21.0,
        feelsLike = 20.0,
        humidity = 85,
        weatherCode = WeatherCode.CLEAR_SKY,
        isDay = isDay,
        windSpeed = 32.1869,
        windDirection = 270,
        windGusts = null,
        pressure = 1013.0,
        uvIndex = uvIndex,
        visibility = null,
        dewPoint = null,
        cloudCover = 0,
        precipitation = 0.0,
        dailyHigh = 24.0,
        dailyLow = 12.0,
        sunrise = null,
        sunset = null,
    )
}
