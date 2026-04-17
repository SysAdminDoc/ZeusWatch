package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.model.PirateWeatherResponse
import com.sysadmindoc.nimbus.data.model.PwCurrently
import com.sysadmindoc.nimbus.data.model.PwDaily
import com.sysadmindoc.nimbus.data.model.PwDailyBlock
import com.sysadmindoc.nimbus.data.model.PwHourly
import com.sysadmindoc.nimbus.data.model.PwHourlyBlock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Regression coverage for percent/bounds clamping introduced in the
 * Pirate Weather adapter. Out-of-range values from the upstream API
 * (e.g. `humidity = 1.3`, negative wind) must not leak into the UI
 * as nonsensical display values like "130%" or negative km/h.
 */
class PirateWeatherAdapterHardeningTest {

    @Test
    fun `out-of-range fractions are clamped and NaN is treated as zero`() = runTest {
        val api = mockk<PirateWeatherApi>()
        val prefs = mockk<UserPreferences>()
        val adapter = PirateWeatherForecastAdapter(api, prefs)
        val nowEpoch = Instant.now().epochSecond

        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "test-key"))
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns PirateWeatherResponse(
            latitude = 0.0,
            longitude = 0.0,
            timezone = "UTC",
            currently = PwCurrently(
                time = nowEpoch,
                temperature = 20.0,
                apparentTemperature = 20.0,
                humidity = 1.3, // >1.0, should clamp to 100
                pressure = 1013.0,
                windSpeed = -5.0, // negative m/s, should clamp to 0 km/h
                windGust = -1.0,
                windBearing = 180,
                cloudCover = Double.NaN, // NaN, should become 0
                uvIndex = -2.0, // negative, should clamp to 0
                visibility = -10.0, // negative km → 0 meters
                precipIntensity = -1.0, // negative mm/h → 0
                precipProbability = 1.5, // >1.0 → 100%
                precipType = null,
                icon = "clear-day",
                dewPoint = 10.0,
            ),
            hourly = PwHourlyBlock(data = emptyList<PwHourly>()),
            daily = PwDailyBlock(data = emptyList<PwDaily>()),
        )

        val result = adapter.getWeather(0.0, 0.0, "Test")
        assertTrue("Adapter succeeded", result.isSuccess)
        val data = result.getOrThrow()
        val c = data.current

        assertEquals(100, c.humidity) // 1.3 clamped
        assertEquals(0.0, c.windSpeed, 0.0001)
        assertEquals(0.0, c.windGusts ?: -1.0, 0.0001)
        assertEquals(0, c.cloudCover) // NaN → 0
        assertEquals(0.0, c.uvIndex, 0.0001)
        assertEquals(0.0, c.visibility ?: -1.0, 0.0001)
        assertEquals(0.0, c.precipitation, 0.0001)
    }
}
