package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.PirateWeatherResponse
import com.sysadmindoc.nimbus.data.model.PwAlert
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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

class PirateWeatherAlertAdapterTest {

    private lateinit var api: PirateWeatherApi
    private lateinit var prefs: UserPreferences
    private lateinit var adapter: PirateWeatherAlertAdapter

    @Before
    fun setup() {
        api = mockk()
        prefs = mockk()
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "test-key"))
        adapter = PirateWeatherAlertAdapter(api, prefs)
    }

    private fun pwAlert(
        severity: String,
        title: String = "Severe Thunderstorm Warning",
    ) = PwAlert(
        title = title,
        regions = listOf("Test County"),
        severity = severity,
        time = 1_750_000_000,
        expires = 1_750_010_000,
        description = "Storms expected.",
    )

    private fun response(vararg alerts: PwAlert) = PirateWeatherResponse(alerts = alerts.toList())

    // --- Severity mapping ---

    @Test
    fun `CAP severity strings map to the matching AlertSeverity`() = runTest {
        // Pirate Weather emits CAP-valued severities, not "warning"/"watch"/"advisory".
        val expected = mapOf(
            "Extreme" to AlertSeverity.EXTREME,
            "Severe" to AlertSeverity.SEVERE,
            "Moderate" to AlertSeverity.MODERATE,
            "Minor" to AlertSeverity.MINOR,
            "Unknown" to AlertSeverity.UNKNOWN,
        )
        for ((cap, severity) in expected) {
            coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns
                response(pwAlert(severity = cap))

            val alert = adapter.getAlerts(39.7, -104.9).getOrThrow().first()
            assertEquals("CAP value '$cap' should map to $severity", severity, alert.severity)
        }
    }

    @Test
    fun `severity mapping is case-insensitive`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns
            response(pwAlert(severity = "SEVERE"))

        val alert = adapter.getAlerts(39.7, -104.9).getOrThrow().first()
        assertEquals(AlertSeverity.SEVERE, alert.severity)
    }

    @Test
    fun `unrecognized severity maps to UNKNOWN`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns
            response(pwAlert(severity = "NotASeverity"))

        val alert = adapter.getAlerts(39.7, -104.9).getOrThrow().first()
        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
    }

    // --- API key gating ---

    @Test
    fun `blank API key returns empty success without calling the API`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = ""))

        val result = adapter.getAlerts(39.7, -104.9)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify(exactly = 0) { api.getForecast(any(), any(), any(), any(), any()) }
    }

    // --- Filtering ---

    @Test
    fun `blank-title alerts are filtered out`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns response(
            pwAlert(severity = "Severe", title = ""),
            pwAlert(severity = "Moderate", title = "Flood Watch"),
        )

        val alerts = adapter.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Flood Watch", alerts.first().event)
    }

    // --- Error handling ---

    @Test
    fun `getAlerts returns empty success on 404`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } throws httpException(404)

        val result = adapter.getAlerts(39.7, -104.9)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAlerts returns empty success on 400`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } throws httpException(400)

        val result = adapter.getAlerts(39.7, -104.9)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAlerts returns failure on server error`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } throws httpException(500)

        val result = adapter.getAlerts(39.7, -104.9)
        assertTrue(result.isFailure)
    }

    @Test
    fun `getAlerts returns failure on other exceptions`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any()) } throws
            RuntimeException("Connection timeout")

        val result = adapter.getAlerts(39.7, -104.9)
        assertTrue(result.isFailure)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
    }
}
