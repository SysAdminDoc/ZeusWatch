package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.PressureUnit
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.TimeFormat
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherFormatterTest {

    private val imperial = NimbusSettings() // defaults: F, mph, inHg, inches, miles, 12h
    private val metric = NimbusSettings(
        tempUnit = TempUnit.CELSIUS,
        windUnit = WindUnit.KMH,
        pressureUnit = PressureUnit.HPA,
        precipUnit = PrecipUnit.MM,
        visibilityUnit = VisibilityUnit.KM,
        timeFormat = TimeFormat.TWENTY_FOUR_HOUR,
    )

    // --- Temperature (input is always Celsius) ---

    @Test
    fun `formatTemperature converts C to F`() {
        // 22.2C = 72F
        assertEquals("72\u00B0", WeatherFormatter.formatTemperature(22.2, imperial))
    }

    @Test
    fun `formatTemperature keeps C when metric`() {
        assertEquals("22\u00B0", WeatherFormatter.formatTemperature(22.2, metric))
    }

    @Test
    fun `formatTemperature handles zero`() {
        // 0C = 32F
        assertEquals("32\u00B0", WeatherFormatter.formatTemperature(0.0, imperial))
        assertEquals("0\u00B0", WeatherFormatter.formatTemperature(0.0, metric))
    }

    @Test
    fun `formatTemperature handles negative`() {
        // -20C = -4F
        assertEquals("-4\u00B0", WeatherFormatter.formatTemperature(-20.0, imperial))
        assertEquals("-20\u00B0", WeatherFormatter.formatTemperature(-20.0, metric))
    }

    @Test
    fun `formatTemperatureUnit includes unit suffix`() {
        assertEquals("72\u00B0F", WeatherFormatter.formatTemperatureUnit(22.2, imperial))
        assertEquals("22\u00B0C", WeatherFormatter.formatTemperatureUnit(22.2, metric))
    }

    // --- Wind (input is always km/h) ---

    @Test
    fun `formatWindDirection returns correct cardinal direction`() {
        assertEquals("N", WeatherFormatter.formatWindDirection(0))
        assertEquals("NE", WeatherFormatter.formatWindDirection(45))
        assertEquals("E", WeatherFormatter.formatWindDirection(90))
        assertEquals("SE", WeatherFormatter.formatWindDirection(135))
        assertEquals("S", WeatherFormatter.formatWindDirection(180))
        assertEquals("SW", WeatherFormatter.formatWindDirection(225))
        assertEquals("W", WeatherFormatter.formatWindDirection(270))
        assertEquals("NW", WeatherFormatter.formatWindDirection(315))
        assertEquals("N", WeatherFormatter.formatWindDirection(350))
    }

    @Test
    fun `formatWindSpeed converts kmh to mph`() {
        // 16 km/h ~= 9.9 mph -> 9 mph
        assertEquals("N 9 mph", WeatherFormatter.formatWindSpeed(16.0, 0, imperial))
    }

    @Test
    fun `formatWindSpeed keeps kmh when metric`() {
        assertEquals("N 16 km/h", WeatherFormatter.formatWindSpeed(16.0, 0, metric))
    }

    // --- Pressure (input is always hPa) ---

    @Test
    fun `formatPressure converts hPa to inHg`() {
        assertEquals("29.92 inHg", WeatherFormatter.formatPressure(1013.25, imperial))
    }

    @Test
    fun `formatPressure keeps hPa when metric`() {
        assertEquals("1013 hPa", WeatherFormatter.formatPressure(1013.25, metric))
    }

    // --- Precipitation (input is always mm) ---

    @Test
    fun `formatPrecipitation converts mm to inches`() {
        // 6.35mm = 0.25in
        assertEquals("0.25 in", WeatherFormatter.formatPrecipitation(6.35, imperial))
    }

    @Test
    fun `formatPrecipitation keeps mm when metric`() {
        assertEquals("6.4 mm", WeatherFormatter.formatPrecipitation(6.35, metric))
    }

    // --- Visibility (input is always meters) ---

    @Test
    fun `formatVisibility returns 10+ mi for excellent visibility`() {
        assertEquals("10+ mi", WeatherFormatter.formatVisibility(16100.0, imperial))
    }

    @Test
    fun `formatVisibility returns km when metric`() {
        assertEquals("16+ km", WeatherFormatter.formatVisibility(16100.0, metric))
    }

    @Test
    fun `formatVisibility returns dash for null`() {
        assertEquals("--", WeatherFormatter.formatVisibility(null, imperial))
    }

    // --- UV ---

    @Test
    fun `uvDescription maps to correct level`() {
        assertEquals("Low", WeatherFormatter.uvDescription(0.0))
        assertEquals("Moderate", WeatherFormatter.uvDescription(3.0))
        assertEquals("High", WeatherFormatter.uvDescription(6.0))
        assertEquals("Very High", WeatherFormatter.uvDescription(8.0))
        assertEquals("Extreme", WeatherFormatter.uvDescription(11.0))
    }

    // --- Humidity / Cloud ---

    @Test
    fun `formatHumidity appends percent`() {
        assertEquals("65%", WeatherFormatter.formatHumidity(65))
    }

    @Test
    fun `formatCloudCover appends percent`() {
        assertEquals("80%", WeatherFormatter.formatCloudCover(80))
    }

    // --- Day label ---

    @Test
    fun `formatDayLabel returns Today for current date`() {
        assertEquals("Today", WeatherFormatter.formatDayLabel(java.time.LocalDate.now()))
    }

    @Test
    fun `formatDayLabel returns Tomorrow for next date`() {
        assertEquals("Tomorrow", WeatherFormatter.formatDayLabel(java.time.LocalDate.now().plusDays(1)))
    }

    @Test
    fun `formatDayLabel returns abbreviated day for future dates`() {
        val result = WeatherFormatter.formatDayLabel(java.time.LocalDate.now().plusDays(3))
        assert(result.matches(Regex("[A-Z][a-z]{2} \\d{1,2}"))) { "Got: $result" }
    }

    // --- Time ---

    @Test
    fun `formatTime parses ISO datetime to 12hr format`() {
        assertEquals("2:30 PM", WeatherFormatter.formatTime("2025-01-15T14:30:00", imperial))
    }

    @Test
    fun `formatTime parses ISO datetime to 24hr format`() {
        assertEquals("14:30", WeatherFormatter.formatTime("2025-01-15T14:30:00", metric))
    }

    @Test
    fun `formatTime returns dash for null`() {
        assertEquals("--", WeatherFormatter.formatTime(null))
    }

    @Test
    fun `formatTime returns dash for invalid string`() {
        assertEquals("--", WeatherFormatter.formatTime("not-a-date"))
    }
}
