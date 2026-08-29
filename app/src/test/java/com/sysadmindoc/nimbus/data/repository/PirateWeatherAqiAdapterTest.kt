package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.PirateWeatherResponse
import com.sysadmindoc.nimbus.data.model.PwCurrently
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pirate Weather derives its air quality index from the requested unit system:
 * `us` yields the US EPA AQI, `si` the EU CAQI and `ca` the Canadian AQHI. The
 * app renders the value against [AqiLevel]'s EPA bands, so a request that
 * drifts off `us` would mislabel clean air as unhealthy without failing.
 */
class PirateWeatherAqiAdapterTest {

    private val api = mockk<PirateWeatherApi>()
    private val prefs = mockk<UserPreferences>()
    private val adapter = PirateWeatherAqiAdapter(api, prefs)

    @Test
    fun `air quality is requested on US units at version 2 with only the current block`() = runTest {
        val units = slot<String>()
        val version = slot<Int>()
        val exclude = slot<String>()
        val include = slot<String>()
        stub(currently(airQualityIndex = 42.0), units, version, exclude, include)

        adapter.getAirQuality(40.71, -74.01).getOrThrow()

        assertEquals("us", units.captured)
        // airQualityIndex is a version>1 field. Without it the reply parses
        // fine and the index is simply absent, which reads as "no data here".
        assertEquals(2, version.captured)
        assertEquals("airqualitydetails", include.captured)
        // Only the current index is consumed; everything else is payload the
        // app pays for and throws away.
        listOf("minutely", "hourly", "daily", "alerts").forEach { block ->
            assertTrue("$block should be excluded", block in exclude.captured)
        }
    }

    @Test
    fun `the index maps onto the EPA level bands`() = runTest {
        // Band edges from AqiLevel: 0-50 good, 51-100 moderate, 101-150
        // unhealthy for sensitive groups, 151-200 unhealthy.
        val expected = mapOf(
            12.0 to AqiLevel.GOOD,
            75.0 to AqiLevel.MODERATE,
            132.0 to AqiLevel.UNHEALTHY_SENSITIVE,
            178.0 to AqiLevel.UNHEALTHY,
        )

        expected.forEach { (index, level) ->
            stub(currently(airQualityIndex = index))

            val data = adapter.getAirQuality(40.71, -74.01).getOrThrow()

            assertEquals(index.toInt(), data.usAqi)
            assertEquals(level, data.aqiLevel)
        }
    }

    @Test
    fun `pollutant concentrations are carried through`() = runTest {
        stub(
            currently(
                airQualityIndex = 60.0,
                pm25 = 15.2,
                pm10 = 22.4,
                ozone = 31.0,
                no2 = 8.5,
                so2 = 1.2,
                co = 220.0,
            ),
        )

        val data = adapter.getAirQuality(40.71, -74.01).getOrThrow()

        assertEquals(15.2, data.pm25, 0.001)
        assertEquals(22.4, data.pm10, 0.001)
        assertEquals(31.0, data.ozone, 0.001)
        assertEquals(8.5, data.nitrogenDioxide, 0.001)
        assertEquals(1.2, data.sulphurDioxide, 0.001)
        assertEquals(220.0, data.carbonMonoxide, 0.001)
    }

    @Test
    fun `a reply without an index fails instead of reporting clean air`() = runTest {
        stub(currently(airQualityIndex = null))

        // Defaulting a missing index to 0 would render as "Good" — the one
        // wrong answer that stops a user acting on genuinely bad air.
        assertTrue(adapter.getAirQuality(40.71, -74.01).isFailure)
    }

    @Test
    fun `a missing currently block fails`() = runTest {
        stub(null)

        assertTrue(adapter.getAirQuality(40.71, -74.01).isFailure)
    }

    @Test
    fun `a blank API key fails without calling the API`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = ""))

        assertTrue(adapter.getAirQuality(40.71, -74.01).isFailure)
        coVerify(exactly = 0) { api.getAirQuality(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a network failure surfaces as a failed result`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "test-key"))
        coEvery { api.getAirQuality(any(), any(), any(), any(), any(), any(), any()) } throws IOException("offline")

        assertTrue(adapter.getAirQuality(40.71, -74.01).isFailure)
    }

    private fun currently(
        airQualityIndex: Double?,
        pm25: Double? = null,
        pm10: Double? = null,
        ozone: Double? = null,
        no2: Double? = null,
        so2: Double? = null,
        co: Double? = null,
    ) = PwCurrently(
        time = 1_748_764_800L,
        temperature = 20.0,
        airQualityIndex = airQualityIndex,
        pm25 = pm25,
        pm10 = pm10,
        ozoneConcentration = ozone,
        nitrogenDioxide = no2,
        sulphurDioxide = so2,
        carbonMonoxide = co,
    )

    private fun stub(
        currently: PwCurrently?,
        units: io.mockk.CapturingSlot<String>? = null,
        version: io.mockk.CapturingSlot<Int>? = null,
        exclude: io.mockk.CapturingSlot<String>? = null,
        include: io.mockk.CapturingSlot<String>? = null,
    ) {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "test-key"))
        coEvery {
            api.getAirQuality(
                any(),
                any(),
                any(),
                units?.let { capture(it) } ?: any(),
                version?.let { capture(it) } ?: any(),
                exclude?.let { capture(it) } ?: any(),
                include?.let { capture(it) } ?: any(),
            )
        } returns PirateWeatherResponse(
            latitude = 40.71,
            longitude = -74.01,
            timezone = "UTC",
            currently = currently,
        )
    }
}
