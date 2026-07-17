package com.sysadmindoc.nimbus.ui.component

import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TemperatureTrendSummaryTest {
    private val celsius = NimbusSettings(tempUnit = TempUnit.CELSIUS)

    private fun hourAt(hourOffset: Long, tempC: Double) = HourlyConditions(
        time = LocalDateTime.of(2026, 1, 15, 9, 0).plusHours(hourOffset),
        temperature = tempC,
        feelsLike = null,
        weatherCode = WeatherCode.PARTLY_CLOUDY,
        isDay = true,
        precipitationProbability = 0,
        precipitation = null,
        windSpeed = null,
        windDirection = null,
        humidity = null,
        uvIndex = null,
        cloudCover = null,
        visibility = null,
    )

    @Test
    fun `summary rounds low and high to nearest like the chart markers`() {
        val summary = buildTemperatureTrendSummary(
            data = listOf(hourAt(0, -5.7), hourAt(1, 2.0), hourAt(2, 10.4)),
            settings = celsius,
        )
        // formatTemperature renders -5.7 as "-6°"; the announcement must match.
        assertEquals(-6, summary.low)
        assertEquals(10, summary.high)
        assertEquals(3, summary.hours)
        assertEquals(R.string.temperature_trend_warmer, summary.directionRes)
    }

    @Test
    fun `summary detects cooling and steady trends`() {
        val cooling = buildTemperatureTrendSummary(
            data = listOf(hourAt(0, 10.0), hourAt(1, 5.0)),
            settings = celsius,
        )
        assertEquals(R.string.temperature_trend_cooler, cooling.directionRes)

        val steady = buildTemperatureTrendSummary(
            data = listOf(hourAt(0, 10.0), hourAt(1, 10.5)),
            settings = celsius,
        )
        assertEquals(R.string.temperature_trend_steady, steady.directionRes)
    }
}
