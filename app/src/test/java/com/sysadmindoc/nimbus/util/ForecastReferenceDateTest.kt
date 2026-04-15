package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ForecastReferenceDateTest {

    @Test
    fun `weatherReferenceDate prefers observation time`() {
        val data = sampleWeatherData(
            observationTime = LocalDateTime.of(2026, 4, 16, 9, 0),
            dailyDate = LocalDate.of(2026, 4, 15),
            hourlyTime = LocalDateTime.of(2026, 4, 15, 8, 0),
            lastUpdated = LocalDateTime.of(2026, 4, 14, 12, 0),
        )

        assertEquals(LocalDate.of(2026, 4, 16), weatherReferenceDate(data))
    }

    @Test
    fun `weatherReferenceDate falls back to daily then hourly then lastUpdated`() {
        val withoutObservation = sampleWeatherData(
            observationTime = null,
            dailyDate = LocalDate.of(2026, 4, 15),
            hourlyTime = LocalDateTime.of(2026, 4, 14, 8, 0),
            lastUpdated = LocalDateTime.of(2026, 4, 13, 12, 0),
        )
        assertEquals(LocalDate.of(2026, 4, 15), weatherReferenceDate(withoutObservation))

        val withoutObservationOrDaily = sampleWeatherData(
            observationTime = null,
            dailyDate = null,
            hourlyTime = LocalDateTime.of(2026, 4, 14, 8, 0),
            lastUpdated = LocalDateTime.of(2026, 4, 13, 12, 0),
        )
        assertEquals(LocalDate.of(2026, 4, 14), weatherReferenceDate(withoutObservationOrDaily))

        val withoutForecastDates = sampleWeatherData(
            observationTime = null,
            dailyDate = null,
            hourlyTime = null,
            lastUpdated = LocalDateTime.of(2026, 4, 13, 12, 0),
        )
        assertEquals(LocalDate.of(2026, 4, 13), weatherReferenceDate(withoutForecastDates))
    }

    private fun sampleWeatherData(
        observationTime: LocalDateTime?,
        dailyDate: LocalDate?,
        hourlyTime: LocalDateTime?,
        lastUpdated: LocalDateTime,
    ): WeatherData {
        return WeatherData(
            location = LocationInfo("Denver", latitude = 39.7, longitude = -104.9),
            current = CurrentConditions(
                temperature = 20.0,
                feelsLike = 20.0,
                humidity = 40,
                weatherCode = WeatherCode.CLEAR_SKY,
                observationTime = observationTime,
                isDay = true,
                windSpeed = 10.0,
                windDirection = 180,
                windGusts = null,
                pressure = 1015.0,
                uvIndex = 4.0,
                visibility = 16.0,
                dewPoint = 8.0,
                cloudCover = 10,
                precipitation = 0.0,
                dailyHigh = 24.0,
                dailyLow = 12.0,
                sunrise = "2026-04-15T06:30:00",
                sunset = "2026-04-15T19:30:00",
            ),
            hourly = hourlyTime?.let {
                listOf(
                    HourlyConditions(
                        time = it,
                        temperature = 20.0,
                        feelsLike = null,
                        weatherCode = WeatherCode.CLEAR_SKY,
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
                )
            } ?: emptyList(),
            daily = dailyDate?.let {
                listOf(
                    DailyConditions(
                        date = it,
                        weatherCode = WeatherCode.CLEAR_SKY,
                        temperatureHigh = 24.0,
                        temperatureLow = 12.0,
                        precipitationProbability = 0,
                        precipitationSum = null,
                        sunrise = "2026-04-15T06:30:00",
                        sunset = "2026-04-15T19:30:00",
                        uvIndexMax = null,
                        windSpeedMax = null,
                        windDirectionDominant = null,
                    )
                )
            } ?: emptyList(),
            lastUpdated = lastUpdated,
        )
    }
}
