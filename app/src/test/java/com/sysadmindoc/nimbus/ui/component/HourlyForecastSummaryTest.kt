package com.sysadmindoc.nimbus.ui.component

import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class HourlyForecastSummaryTest {
    private fun hourAt(hourOffset: Long, humidity: Int?) = HourlyConditions(
        time = LocalDateTime.of(2026, 1, 15, 9, 0).plusHours(hourOffset),
        temperature = 10.0,
        feelsLike = null,
        weatherCode = WeatherCode.PARTLY_CLOUDY,
        isDay = true,
        precipitationProbability = 10,
        precipitation = null,
        windSpeed = null,
        windDirection = null,
        humidity = humidity,
        uvIndex = null,
        cloudCover = null,
        visibility = null,
    )

    @Test
    fun `humidityRange ignores hours with missing humidity`() {
        val hours = listOf(
            hourAt(0, humidity = null),
            hourAt(1, humidity = 55),
            hourAt(2, humidity = 78),
            hourAt(3, humidity = null),
        )
        // A missing reading must not become 0% ("0% to 78% humidity").
        assertEquals(55 to 78, humidityRange(hours))
    }

    @Test
    fun `humidityRange is null when no hour carries a reading`() {
        assertNull(humidityRange(listOf(hourAt(0, null), hourAt(1, null))))
        assertNull(humidityRange(emptyList()))
    }

    @Test
    fun `feelsLikeDeltaText rounds to nearest like the rendered labels`() {
        assertEquals("-1", feelsLikeDeltaText(-0.6))
        assertEquals("+4", feelsLikeDeltaText(3.9))
        assertEquals("-2", feelsLikeDeltaText(-1.6))
        assertEquals("+2", feelsLikeDeltaText(2.0))
    }

    @Test
    fun `feelsLikeDeltaText stays blank when the delta rounds to zero`() {
        assertEquals(" ", feelsLikeDeltaText(0.4))
        assertEquals(" ", feelsLikeDeltaText(-0.4))
        assertEquals(" ", feelsLikeDeltaText(0.0))
    }
}
