package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AccessibilityHelperTest {

    // --- Test fixtures ---

    private fun makeCurrent(
        temp: Double = 72.0,
        feelsLike: Double = 70.0,
        high: Double = 80.0,
        low: Double = 60.0,
        humidity: Int = 55,
        windSpeed: Double = 10.0,
        windDirection: Int = 180,
        windGusts: Double? = 15.0,
        pressure: Double = 1013.25,
        uvIndex: Double = 5.0,
        cloudCover: Int = 40,
    ) = CurrentConditions(
        temperature = temp,
        feelsLike = feelsLike,
        humidity = humidity,
        weatherCode = WeatherCode.CLEAR_SKY,
        isDay = true,
        windSpeed = windSpeed,
        windDirection = windDirection,
        windGusts = windGusts,
        pressure = pressure,
        uvIndex = uvIndex,
        visibility = 16000.0,
        dewPoint = 55.0,
        cloudCover = cloudCover,
        precipitation = 0.0,
        dailyHigh = high,
        dailyLow = low,
        sunrise = "2025-01-15T07:00:00",
        sunset = "2025-01-15T17:30:00",
    )

    private fun makeHourly(count: Int = 12, baseTemp: Double = 70.0): List<HourlyConditions> {
        val now = LocalDateTime.now()
        return (0 until count).map { i ->
            HourlyConditions(
                time = now.plusHours(i.toLong()),
                temperature = baseTemp + i,
                feelsLike = baseTemp + i - 2,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = true,
                precipitationProbability = if (i > 8) 30 else 0,
                precipitation = null,
                windSpeed = 10.0,
                windDirection = 180,
                humidity = 50,
                uvIndex = 3.0,
                cloudCover = 20,
                visibility = 16000.0,
            )
        }
    }

    private fun makeDaily(count: Int = 5): List<DailyConditions> {
        val today = LocalDate.now()
        return (0 until count).map { i ->
            DailyConditions(
                date = today.plusDays(i.toLong()),
                weatherCode = WeatherCode.PARTLY_CLOUDY,
                temperatureHigh = 80.0 + i,
                temperatureLow = 60.0 - i,
                precipitationProbability = 10 * i,
                precipitationSum = null,
                sunrise = "2025-01-15T07:00:00",
                sunset = "2025-01-15T17:30:00",
                uvIndexMax = 6.0,
                windSpeedMax = 15.0,
                windDirectionDominant = 180,
            )
        }
    }

    // --- Current conditions ---

    @Test
    fun `currentConditions includes location, temp, condition, feels like, high and low`() {
        val desc = AccessibilityHelper.currentConditions(makeCurrent(), "Denver")
        assertTrue(desc.contains("Denver"))
        assertTrue(desc.contains("72 degrees"))
        assertTrue(desc.contains("Clear"))
        assertTrue(desc.contains("Feels like 70"))
        assertTrue(desc.contains("High 80"))
        assertTrue(desc.contains("low 60"))
    }

    // --- Wind compass ---

    @Test
    fun `windCompass describes direction, speed, and gusts`() {
        val desc = AccessibilityHelper.windCompass(10.0, 180, 20.0)
        assertTrue(desc.contains("Wind compass"))
        assertTrue(desc.contains("from S"))
        assertTrue(desc.contains("10 miles per hour"))
        assertTrue(desc.contains("Gusts up to 20"))
    }

    @Test
    fun `windCompass omits gusts when not exceeding speed`() {
        val desc = AccessibilityHelper.windCompass(15.0, 0, 10.0)
        assertFalse(desc.contains("Gusts"))
    }

    @Test
    fun `windCompass omits gusts when null`() {
        val desc = AccessibilityHelper.windCompass(10.0, 90, null)
        assertFalse(desc.contains("Gusts"))
    }

    // --- Temperature graph ---

    @Test
    fun `temperatureGraph includes range and current temp`() {
        val hourly = makeHourly(24, 65.0)
        val desc = AccessibilityHelper.temperatureGraph(hourly)
        assertTrue(desc.contains("24-hour"))
        assertTrue(desc.contains("65"))
        assertTrue(desc.contains("88")) // 65 + 23
    }

    @Test
    fun `temperatureGraph handles insufficient data`() {
        val desc = AccessibilityHelper.temperatureGraph(makeHourly(1))
        assertTrue(desc.contains("insufficient"))
    }

    // --- UV index ---

    @Test
    fun `uvIndex includes value and level`() {
        val desc = AccessibilityHelper.uvIndex(7.0)
        assertTrue(desc.contains("UV index 7"))
        assertTrue(desc.contains("High"))
    }

    // --- Air quality ---

    @Test
    fun `airQuality includes AQI value, level, and advice`() {
        val aq = AirQualityData(
            usAqi = 55,
            europeanAqi = 40,
            aqiLevel = AqiLevel.MODERATE,
            pm25 = 12.0, pm10 = 20.0, ozone = 30.0,
            nitrogenDioxide = 10.0, sulphurDioxide = 5.0, carbonMonoxide = 200.0,
            pollen = PollenData(),
        )
        val desc = AccessibilityHelper.airQuality(aq)
        assertTrue(desc.contains("55"))
        assertTrue(desc.contains("Moderate"))
        assertTrue(desc.contains("Acceptable"))
    }

    // --- Daily forecast ---

    @Test
    fun `dailyForecast summarizes first 3 days and count`() {
        val desc = AccessibilityHelper.dailyForecast(makeDaily(7))
        assertTrue(desc.contains("7-day"))
        assertTrue(desc.contains("Today"))
        assertTrue(desc.contains("Tomorrow"))
        assertTrue(desc.contains("And 4 more days"))
    }

    @Test
    fun `dailyForecast handles empty list`() {
        assertEquals("Daily forecast unavailable.", AccessibilityHelper.dailyForecast(emptyList()))
    }

    // --- Hourly forecast ---

    @Test
    fun `hourlyForecast includes temp range and precip`() {
        val desc = AccessibilityHelper.hourlyForecast(makeHourly(12, 60.0))
        assertTrue(desc.contains("12 hours"))
        assertTrue(desc.contains("60 to 71"))
        assertTrue(desc.contains("Precipitation chance up to 30"))
    }

    @Test
    fun `hourlyForecast handles empty list`() {
        assertEquals("Hourly forecast unavailable.", AccessibilityHelper.hourlyForecast(emptyList()))
    }

    // --- Moon phase ---

    @Test
    fun `moonPhase includes phase label, illumination, and times`() {
        val astro = AstronomyData(
            moonPhase = MoonPhase.FULL_MOON,
            moonIllumination = 98.0,
            moonrise = "2025-01-15T18:30:00",
            moonset = "2025-01-16T06:15:00",
            dayLength = "10h 30m",
        )
        val desc = AccessibilityHelper.moonPhase(astro)
        assertTrue(desc.contains("Full Moon"))
        assertTrue(desc.contains("98 percent"))
        assertTrue(desc.contains("Moonrise"))
        assertTrue(desc.contains("Day length 10h 30m"))
    }

    // --- Details grid ---

    @Test
    fun `detailsGrid includes all major fields`() {
        val desc = AccessibilityHelper.detailsGrid(makeCurrent())
        assertTrue(desc.contains("Weather details"))
        assertTrue(desc.contains("Humidity 55"))
        assertTrue(desc.contains("UV index 5"))
        assertTrue(desc.contains("Sunrise"))
        assertTrue(desc.contains("Sunset"))
    }
}
