package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

class WmoAlertAdapterTest {

    private lateinit var api: WmoAlertApi
    private lateinit var adapter: WmoAlertAdapter

    @Before
    fun setup() {
        api = mockk()
        adapter = WmoAlertAdapter(api)
    }

    private fun warning(
        id: String = "cap-1",
        event: String = "Tropical Cyclone",
        severity: String = "Severe",
        lat: Double? = null,
        lon: Double? = null,
        minLat: Double? = null,
        maxLat: Double? = null,
        minLon: Double? = null,
        maxLon: Double? = null,
    ) = WmoWarning(
        capId = id,
        event = event,
        severity = severity,
        status = "Actual",
        lat = lat,
        lon = lon,
        minLat = minLat,
        maxLat = maxLat,
        minLon = minLon,
        maxLon = maxLon,
    )

    private fun respond(vararg warnings: WmoWarning) {
        coEvery { api.getWarnings() } returns WmoWarningsResponse(warnings.toList())
    }

    // --- Bounding-box proximity ---

    @Test
    fun `point inside bbox is matched`() = runTest {
        respond(warning(minLat = 30.0, maxLat = 40.0, minLon = -110.0, maxLon = -100.0))

        val alerts = adapter.getAlerts(35.0, -105.0).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.SEVERE, alerts.first().severity)
    }

    @Test
    fun `point outside latitude band is rejected`() = runTest {
        respond(warning(minLat = 30.0, maxLat = 40.0, minLon = -110.0, maxLon = -100.0))

        assertTrue(adapter.getAlerts(45.0, -105.0).getOrThrow().isEmpty())
    }

    @Test
    fun `longitude pad widens with latitude instead of reusing the lat pad`() = runTest {
        // At lat 60° the lon pad is 0.5 / cos(60°) = 1.0°. A point 0.8° east of
        // maxLon is inside with the latitude-aware pad but was rejected by the
        // old code that applied the 0.5° lat pad to longitude.
        respond(warning(minLat = 55.0, maxLat = 65.0, minLon = 10.0, maxLon = 20.0))

        assertEquals(1, adapter.getAlerts(60.0, 20.8).getOrThrow().size)
        assertTrue(adapter.getAlerts(60.0, 21.2).getOrThrow().isEmpty())
    }

    @Test
    fun `bbox wrapping the antimeridian matches points on both sides`() = runTest {
        // minLon > maxLon means the box spans ±180° (e.g. Fiji).
        respond(warning(minLat = -25.0, maxLat = -10.0, minLon = 170.0, maxLon = -170.0))

        assertEquals(1, adapter.getAlerts(-17.0, 178.0).getOrThrow().size)
        assertEquals(1, adapter.getAlerts(-17.0, -178.0).getOrThrow().size)
        assertTrue(adapter.getAlerts(-17.0, 0.0).getOrThrow().isEmpty())
    }

    // --- Point-radius fallback ---

    @Test
    fun `falls back to haversine radius when no bbox is present`() = runTest {
        respond(warning(lat = 10.0, lon = 10.0))

        assertEquals(1, adapter.getAlerts(10.5, 10.0).getOrThrow().size) // ~56 km
        assertTrue(adapter.getAlerts(12.0, 12.0).getOrThrow().isEmpty()) // ~310 km
    }

    // --- Status filtering ---

    @Test
    fun `non-Actual warnings are filtered out`() = runTest {
        respond(
            warning(minLat = 30.0, maxLat = 40.0, minLon = -110.0, maxLon = -100.0)
                .copy(status = "Exercise"),
        )

        assertTrue(adapter.getAlerts(35.0, -105.0).getOrThrow().isEmpty())
    }

    // --- Error handling ---

    @Test
    fun `getAlerts returns empty success on 404`() = runTest {
        // The v2 warnings.json endpoint is currently dead (404) — this must
        // not surface as a failure or it would trip fallback/retry storms.
        coEvery { api.getWarnings() } throws httpException(404)

        val result = adapter.getAlerts(35.0, -105.0)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAlerts returns empty success on 400`() = runTest {
        coEvery { api.getWarnings() } throws httpException(400)

        val result = adapter.getAlerts(35.0, -105.0)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAlerts returns failure on server error`() = runTest {
        coEvery { api.getWarnings() } throws httpException(500)

        assertTrue(adapter.getAlerts(35.0, -105.0).isFailure)
    }

    @Test
    fun `getAlerts returns failure on other exceptions`() = runTest {
        coEvery { api.getWarnings() } throws RuntimeException("Connection timeout")

        val result = adapter.getAlerts(35.0, -105.0)
        assertTrue(result.isFailure)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
    }
}
