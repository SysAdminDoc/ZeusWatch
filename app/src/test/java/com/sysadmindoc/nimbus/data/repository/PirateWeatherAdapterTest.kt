package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.model.PirateWeatherResponse
import com.sysadmindoc.nimbus.data.model.PwCurrently
import com.sysadmindoc.nimbus.data.model.PwDaily
import com.sysadmindoc.nimbus.data.model.PwDailyBlock
import com.sysadmindoc.nimbus.data.model.PwHourly
import com.sysadmindoc.nimbus.data.model.PwHourlyBlock
import com.sysadmindoc.nimbus.data.model.PwIconMapper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [PirateWeatherForecastAdapter] core data mapping.
 * Clamping / NaN hardening is covered separately in
 * [PirateWeatherAdapterHardeningTest].
 */
class PirateWeatherForecastAdapterTest {

    private val api = mockk<PirateWeatherApi>()
    private val prefs = mockk<UserPreferences>()
    private val adapter = PirateWeatherForecastAdapter(api, prefs)

    // Stable "now" epoch: 2025-06-01 16:00:00 UTC
    private val nowEpoch = 1_748_764_800L

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalCurrently(
        epoch: Long = nowEpoch,
        windSpeed: Double = 0.0,
        humidity: Double = 0.5,
        cloudCover: Double = 0.3,
        visibility: Double? = 10.0,
        precipIntensity: Double = 0.0,
        precipType: String? = null,
        icon: String = "clear-day",
    ) = PwCurrently(
        time = epoch,
        temperature = 20.0,
        apparentTemperature = 19.0,
        humidity = humidity,
        pressure = 1013.0,
        windSpeed = windSpeed,
        windBearing = 180,
        cloudCover = cloudCover,
        uvIndex = 3.0,
        visibility = visibility,
        precipIntensity = precipIntensity,
        precipProbability = 0.0,
        precipType = precipType,
        icon = icon,
    )

    private fun minimalResponse(
        currently: PwCurrently? = minimalCurrently(),
        hourly: List<PwHourly> = emptyList(),
        daily: List<PwDaily> = emptyList(),
    ) = PirateWeatherResponse(
        latitude = 40.71,
        longitude = -74.01,
        timezone = "UTC",
        currently = currently,
        hourly = PwHourlyBlock(data = hourly),
        daily = PwDailyBlock(data = daily),
    )

    private fun setupMocks(response: PirateWeatherResponse) {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "test-key"))
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns response
    }

    // ── Current conditions ────────────────────────────────────────────────────

    @Test
    fun `happy path returns success and maps current conditions`() = runTest {
        setupMocks(
            minimalResponse(
                currently = minimalCurrently(
                    windSpeed = 10.0,    // m/s → 36.0 km/h
                    humidity = 0.75,     // fraction → 75%
                    cloudCover = 0.4,    // fraction → 40%
                    visibility = 5.0,    // km → 5 000 m
                    precipIntensity = 1.2,
                )
            )
        )
        val result = adapter.getWeather(40.71, -74.01, "New York")
        assertTrue("Expected success", result.isSuccess)
        val c = result.getOrThrow().current
        assertEquals(36.0, c.windSpeed, 0.01)
        assertEquals(75, c.humidity)
        assertEquals(40, c.cloudCover)
        assertEquals(5_000.0, c.visibility ?: -1.0, 0.1)
        assertEquals(1.2, c.precipitation, 0.001)
    }

    @Test
    fun `blank API key returns failure`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = ""))
        assertTrue(adapter.getWeather(0.0, 0.0).isFailure)
    }

    @Test
    fun `missing currently block returns failure`() = runTest {
        setupMocks(minimalResponse(currently = null))
        assertTrue(adapter.getWeather(0.0, 0.0).isFailure)
    }

    @Test
    fun `API IOException propagates as failure`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "key"))
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } throws IOException("timeout")
        assertTrue(adapter.getWeather(0.0, 0.0).isFailure)
    }

    @Test
    fun `precipType snow sets snowfall equal to precipIntensity in current`() = runTest {
        setupMocks(
            minimalResponse(
                currently = minimalCurrently(precipIntensity = 2.5, precipType = "snow")
            )
        )
        val c = adapter.getWeather(0.0, 0.0).getOrThrow().current
        assertEquals(2.5, c.snowfall ?: -1.0, 0.001)
    }

    @Test
    fun `precipType rain leaves snowfall null in current`() = runTest {
        setupMocks(
            minimalResponse(
                currently = minimalCurrently(precipIntensity = 3.0, precipType = "rain")
            )
        )
        assertNull(adapter.getWeather(0.0, 0.0).getOrThrow().current.snowfall)
    }

    @Test
    fun `null precipType leaves snowfall null in current`() = runTest {
        setupMocks(
            minimalResponse(
                currently = minimalCurrently(precipIntensity = 0.5, precipType = null)
            )
        )
        assertNull(adapter.getWeather(0.0, 0.0).getOrThrow().current.snowfall)
    }

    @Test
    fun `daily high and low come from first daily entry`() = runTest {
        val daily = PwDaily(
            time = nowEpoch,
            temperatureHigh = 28.0,
            temperatureLow = 14.0,
        )
        setupMocks(minimalResponse(daily = listOf(daily)))
        val c = adapter.getWeather(0.0, 0.0).getOrThrow().current
        assertEquals(28.0, c.dailyHigh, 0.001)
        assertEquals(14.0, c.dailyLow, 0.001)
    }

    // ── Hourly ────────────────────────────────────────────────────────────────

    @Test
    fun `hourly entries more than one hour before now are filtered out`() = runTest {
        val threeHoursAgo = PwHourly(time = nowEpoch - 3 * 3600, temperature = 15.0, icon = "rain")
        val ninetyMinAgo  = PwHourly(time = nowEpoch - 90 * 60,  temperature = 16.0, icon = "rain")
        val thirtyMinAgo  = PwHourly(time = nowEpoch - 30 * 60,  temperature = 17.0, icon = "rain")

        setupMocks(minimalResponse(hourly = listOf(threeHoursAgo, ninetyMinAgo, thirtyMinAgo)))
        val hourly = adapter.getWeather(0.0, 0.0).getOrThrow().hourly
        assertEquals("Only the 30-min-ago entry survives the filter", 1, hourly.size)
        assertEquals(17.0, hourly[0].temperature, 0.001)
    }

    @Test
    fun `hourly entries at or after now minus one hour are retained`() = runTest {
        val sixtyMinAgo = PwHourly(time = nowEpoch - 3600, temperature = 18.0, icon = "cloudy")
        val future      = PwHourly(time = nowEpoch + 3600, temperature = 22.0, icon = "clear-day")
        setupMocks(minimalResponse(hourly = listOf(sixtyMinAgo, future)))
        // Exactly at the -1 h boundary: isBefore(now - 1h) is false, so retained
        assertEquals(2, adapter.getWeather(0.0, 0.0).getOrThrow().hourly.size)
    }

    @Test
    fun `hourly snow precipType populates snowfall`() = runTest {
        val h = PwHourly(
            time = nowEpoch + 3600,
            temperature = -5.0,
            icon = "snow",
            precipIntensity = 1.8,
            precipType = "snow",
        )
        setupMocks(minimalResponse(hourly = listOf(h)))
        val hourly = adapter.getWeather(0.0, 0.0).getOrThrow().hourly
        assertEquals(1.8, hourly.first().snowfall ?: -1.0, 0.001)
    }

    @Test
    fun `hourly rain precipType leaves snowfall null`() = runTest {
        val h = PwHourly(
            time = nowEpoch + 3600,
            temperature = 8.0,
            icon = "rain",
            precipIntensity = 2.0,
            precipType = "rain",
        )
        setupMocks(minimalResponse(hourly = listOf(h)))
        assertNull(adapter.getWeather(0.0, 0.0).getOrThrow().hourly.first().snowfall)
    }

    // ── Daily ─────────────────────────────────────────────────────────────────

    @Test
    fun `daily precipIntensity mm per h is multiplied to mm per day`() = runTest {
        val day = PwDaily(
            time = nowEpoch,
            precipIntensity = 2.5,   // 2.5 mm/h × 24 = 60 mm/day
            precipType = "rain",
            temperatureHigh = 20.0,
            temperatureLow = 10.0,
        )
        setupMocks(minimalResponse(daily = listOf(day)))
        val d = adapter.getWeather(0.0, 0.0).getOrThrow().daily.first()
        assertEquals(60.0, d.precipitationSum ?: -1.0, 0.01)
        assertNull("snowfallSum should be null for rain", d.snowfallSum)
    }

    @Test
    fun `daily snow precipType populates snowfallSum`() = runTest {
        val day = PwDaily(
            time = nowEpoch,
            precipIntensity = 3.0,   // 3.0 mm/h × 24 = 72 mm/day
            precipType = "snow",
            temperatureHigh = -5.0,
            temperatureLow = -12.0,
        )
        setupMocks(minimalResponse(daily = listOf(day)))
        val d = adapter.getWeather(0.0, 0.0).getOrThrow().daily.first()
        assertEquals(72.0, d.snowfallSum ?: -1.0, 0.01)
    }

    @Test
    fun `daily null precipType leaves snowfallSum null`() = runTest {
        val day = PwDaily(
            time = nowEpoch,
            precipIntensity = 1.0,
            precipType = null,
            temperatureHigh = 15.0,
            temperatureLow = 5.0,
        )
        setupMocks(minimalResponse(daily = listOf(day)))
        assertNull(adapter.getWeather(0.0, 0.0).getOrThrow().daily.first().snowfallSum)
    }

    @Test
    fun `sunrise and sunset are formatted as ISO local date time strings`() = runTest {
        // 2025-06-01 11:00:00 UTC, 2025-06-01 23:30:00 UTC
        val day = PwDaily(
            time = nowEpoch,
            sunriseTime = 1_748_775_600L,
            sunsetTime = 1_748_820_600L,
            temperatureHigh = 20.0,
            temperatureLow = 10.0,
        )
        setupMocks(minimalResponse(daily = listOf(day)))
        val d = adapter.getWeather(0.0, 0.0).getOrThrow().daily.first()
        assertNotNull(d.sunrise)
        assertNotNull(d.sunset)
        val isoPattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")
        assertTrue("sunrise should be ISO datetime: ${d.sunrise}", d.sunrise!!.matches(isoPattern))
        assertTrue("sunset should be ISO datetime: ${d.sunset}", d.sunset!!.matches(isoPattern))
    }

    @Test
    fun `daily wind speed converts m per s to km per h`() = runTest {
        val day = PwDaily(
            time = nowEpoch,
            windSpeed = 5.0,   // 5.0 m/s × 3.6 = 18.0 km/h
            temperatureHigh = 20.0,
            temperatureLow = 10.0,
        )
        setupMocks(minimalResponse(daily = listOf(day)))
        val d = adapter.getWeather(0.0, 0.0).getOrThrow().daily.first()
        assertEquals(18.0, d.windSpeedMax ?: -1.0, 0.01)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// PwIconMapper — exhaustive mapping and isDayFromIcon
// ──────────────────────────────────────────────────────────────────────────────

class PwIconMapperTest {

    // ── toWmoCode ─────────────────────────────────────────────────────────────

    @Test fun `clear-day maps to WMO 0`() = assertEquals(0, PwIconMapper.toWmoCode("clear-day"))
    @Test fun `clear-night maps to WMO 0`() = assertEquals(0, PwIconMapper.toWmoCode("clear-night"))
    @Test fun `partly-cloudy-day maps to WMO 2`() = assertEquals(2, PwIconMapper.toWmoCode("partly-cloudy-day"))
    @Test fun `partly-cloudy-night maps to WMO 2`() = assertEquals(2, PwIconMapper.toWmoCode("partly-cloudy-night"))
    @Test fun `cloudy maps to WMO 3`() = assertEquals(3, PwIconMapper.toWmoCode("cloudy"))
    @Test fun `wind maps to WMO 3 overcast fallback`() = assertEquals(3, PwIconMapper.toWmoCode("wind"))
    @Test fun `fog maps to WMO 45`() = assertEquals(45, PwIconMapper.toWmoCode("fog"))
    @Test fun `rain maps to WMO 63`() = assertEquals(63, PwIconMapper.toWmoCode("rain"))
    @Test fun `rain with sleet precipType maps to WMO 66`() = assertEquals(66, PwIconMapper.toWmoCode("rain", "sleet"))
    @Test fun `sleet maps to WMO 66`() = assertEquals(66, PwIconMapper.toWmoCode("sleet"))
    @Test fun `snow maps to WMO 73`() = assertEquals(73, PwIconMapper.toWmoCode("snow"))
    @Test fun `hail maps to WMO 96`() = assertEquals(96, PwIconMapper.toWmoCode("hail"))
    @Test fun `thunderstorm maps to WMO 95`() = assertEquals(95, PwIconMapper.toWmoCode("thunderstorm"))
    @Test fun `tornado maps to WMO 99`() = assertEquals(99, PwIconMapper.toWmoCode("tornado"))
    @Test fun `unknown icon maps to WMO -1`() = assertEquals(-1, PwIconMapper.toWmoCode("partly-windy"))

    // ── isDayFromIcon ─────────────────────────────────────────────────────────

    @Test fun `isDayFromIcon clear-day returns true`() = assertTrue(PwIconMapper.isDayFromIcon("clear-day"))
    @Test fun `isDayFromIcon clear-night returns false`() = assertFalse(PwIconMapper.isDayFromIcon("clear-night"))
    @Test fun `isDayFromIcon partly-cloudy-day returns true`() = assertTrue(PwIconMapper.isDayFromIcon("partly-cloudy-day"))
    @Test fun `isDayFromIcon partly-cloudy-night returns false`() = assertFalse(PwIconMapper.isDayFromIcon("partly-cloudy-night"))
    @Test fun `isDayFromIcon rain (no suffix) returns true`() = assertTrue(PwIconMapper.isDayFromIcon("rain"))
    @Test fun `isDayFromIcon fog (no suffix) returns true`() = assertTrue(PwIconMapper.isDayFromIcon("fog"))
    @Test fun `isDayFromIcon snow (no suffix) returns true`() = assertTrue(PwIconMapper.isDayFromIcon("snow"))
}
