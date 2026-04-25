package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AccessibilityHelperTest {

    private val celsiusSettings = NimbusSettings(tempUnit = TempUnit.CELSIUS)

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
        val desc = AccessibilityHelper.currentConditions(makeCurrent(), "Denver", celsiusSettings)
        assertTrue("Expected Denver in: $desc", desc.contains("Denver"))
        assertTrue("Expected 72 in: $desc", desc.contains("72"))
        assertTrue("Expected Clear in: $desc", desc.contains("Clear"))
        assertTrue("Expected Feels like 70 in: $desc", desc.contains("Feels like 70"))
        assertTrue("Expected High 80 in: $desc", desc.contains("High 80"))
        assertTrue("Expected low 60 in: $desc", desc.contains("low 60"))
    }

    // --- Wind compass ---

    @Test
    fun `windCompass describes direction, speed, and gusts`() {
        val desc = AccessibilityHelper.windCompass(10.0, 180, 20.0)
        assertTrue(desc.contains("Wind compass"))
        assertTrue(desc.contains("from S"))
        assertTrue(desc.contains("6 mph"))
        assertTrue(desc.contains("Gusts up to 12 mph"))
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
        val desc = AccessibilityHelper.temperatureGraph(hourly, celsiusSettings)
        assertTrue("Expected 24-hour in: $desc", desc.contains("24-hour"))
        assertTrue("Expected 65 in: $desc", desc.contains("65"))
        assertTrue("Expected 88 in: $desc", desc.contains("88"))
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

    @Test
    fun `uvIndex with peak includes peak hour and safe exposure`() {
        val now = LocalDateTime.of(2026, 4, 25, 10, 0)
        val desc = AccessibilityHelper.uvIndex(
            uvIndex = 4.0,
            peakTime = now.plusHours(2),
            peakUv = 8.0,
            referenceTime = now,
            s = celsiusSettings,
        )

        assertTrue(desc.contains("UV index 4"))
        assertTrue(desc.contains("Peaks at"))
        assertTrue(desc.contains("safe sun exposure"))
    }

    @Test
    fun `humidity includes comfort and dew point`() {
        val desc = AccessibilityHelper.humidity(55, 12.0, celsiusSettings)

        assertTrue(desc.contains("55 percent"))
        assertTrue(desc.contains("comfortable"))
        assertTrue(desc.contains("Dew point"))
    }

    @Test
    fun `nowcast summarizes rain timing and peak`() {
        val now = LocalDateTime.of(2026, 4, 25, 9, 0)
        val data = listOf(
            MinutelyPrecipitation(now, 0.0),
            MinutelyPrecipitation(now.plusMinutes(15), 0.0),
            MinutelyPrecipitation(now.plusMinutes(30), 0.8),
            MinutelyPrecipitation(now.plusMinutes(45), 0.2),
        )

        val desc = AccessibilityHelper.nowcast(data, now, celsiusSettings)

        assertTrue(desc.contains("Rain next hour chart"))
        assertTrue(desc.contains("Rain starting"))
        assertTrue(desc.contains("Peak"))
    }

    @Test
    fun `outdoorScore includes score and factor breakdown`() {
        val desc = AccessibilityHelper.outdoorScore(
            score = 72,
            tempCelsius = 20.0,
            humidity = 45,
            windKmh = 10.0,
            uvIndex = 4.0,
            precipProbability = 20,
        )

        assertTrue(desc.contains("72 out of 100"))
        assertTrue(desc.contains("Good"))
        assertTrue(desc.contains("Factor scores"))
    }

    @Test
    fun `precipitationForecast describes peak chance and total`() {
        val now = LocalDateTime.of(2026, 4, 25, 9, 0)
        val hourly = makeHourly(4).mapIndexed { i, hour ->
            hour.copy(
                time = now.plusHours(i.toLong()),
                precipitationProbability = i * 25,
                precipitation = i * 0.5,
            )
        }

        val desc = AccessibilityHelper.precipitationForecast(hourly, now, celsiusSettings)

        assertTrue(desc.contains("Precipitation forecast chart"))
        assertTrue(desc.contains("75 percent"))
        assertTrue(desc.contains("Total accumulation"))
    }

    @Test
    fun `sunArc formats sun state and moon times`() {
        val now = LocalDateTime.of(2026, 4, 25, 12, 0)
        val desc = AccessibilityHelper.sunArc(
            sunrise = "2026-04-25T06:30:00",
            sunset = "2026-04-25T20:00:00",
            moonrise = "2026-04-25T22:00:00",
            moonset = "2026-04-25T07:30:00",
            referenceTime = now,
            s = celsiusSettings,
        )

        assertTrue(desc.contains("Sun path chart"))
        assertTrue(desc.contains("Daylight"))
        assertTrue(desc.contains("Moonrise"))
    }

    @Test
    fun `sunshineDuration includes daylight percentage`() {
        val desc = AccessibilityHelper.sunshineDuration(6 * 3600.0, 12 * 60)

        assertTrue(desc.contains("6h 0m"))
        assertTrue(desc.contains("50 percent"))
    }

    @Test
    fun `visibility includes current tier and trend range`() {
        val hourly = makeHourly(6).mapIndexed { i, hour ->
            hour.copy(visibility = (i + 1) * 3000.0)
        }

        val desc = AccessibilityHelper.visibility(9000.0, hourly, celsiusSettings)

        assertTrue(desc.contains("Visibility chart"))
        assertTrue(desc.contains("moderate"))
        assertTrue(desc.contains("Next 24 hours range"))
    }

    @Test
    fun `windTrend includes peak and gusts`() {
        val now = LocalDateTime.of(2026, 4, 25, 9, 0)
        val hourly = makeHourly(6).mapIndexed { i, hour ->
            hour.copy(
                time = now.plusHours(i.toLong()),
                windSpeed = 10.0 + i,
                windGusts = if (i == 5) 30.0 else 12.0,
            )
        }

        val desc = AccessibilityHelper.windTrend(hourly, now, celsiusSettings)

        assertTrue(desc.contains("Wind forecast chart"))
        assertTrue(desc.contains("Peak"))
        assertTrue(desc.contains("Gusts up to"))
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
        val desc = AccessibilityHelper.hourlyForecast(makeHourly(12, 60.0), celsiusSettings)
        assertTrue("Expected 12 hours in: $desc", desc.contains("12 hours"))
        assertTrue("Expected 60 in: $desc", desc.contains("60"))
        assertTrue("Expected 71 in: $desc", desc.contains("71"))
        assertTrue("Expected precip in: $desc", desc.contains("Precipitation chance up to 30"))
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
