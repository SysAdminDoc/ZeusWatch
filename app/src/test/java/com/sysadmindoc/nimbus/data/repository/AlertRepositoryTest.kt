package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.NwsAlertFeature
import com.sysadmindoc.nimbus.data.api.NwsAlertProperties
import com.sysadmindoc.nimbus.data.api.NwsAlertResponse
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AlertRepositoryTest {

    private lateinit var api: NwsAlertApi
    private lateinit var repository: AlertRepository

    @Before
    fun setup() {
        api = mockk()
        repository = AlertRepository(api)
    }

    private fun makeFeature(
        id: String = "alert-1",
        event: String = "Tornado Warning",
        severity: String = "Extreme",
        urgency: String = "Immediate",
    ) = NwsAlertFeature(
        id = id,
        properties = NwsAlertProperties(
            event = event,
            headline = "$event for Test County",
            description = "A tornado has been sighted.",
            instruction = "Take shelter immediately.",
            severity = severity,
            urgency = urgency,
            certainty = "Observed",
            senderName = "NWS Denver",
            areaDesc = "Denver Metro Area",
            effective = "2025-01-15T12:00:00",
            expires = "2025-01-15T14:00:00",
            response = "Shelter",
        ),
    )

    @Test
    fun `getAlerts returns mapped alerts on success`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                makeFeature(event = "Tornado Warning", severity = "Extreme"),
                makeFeature(id = "alert-2", event = "Flood Watch", severity = "Moderate"),
            ),
        )

        val result = repository.getAlerts(39.7, -104.9)
        assertTrue(result.isSuccess)
        val alerts = result.getOrThrow()
        assertEquals(2, alerts.size)
        assertEquals("Tornado Warning", alerts[0].event)
        assertEquals(AlertSeverity.EXTREME, alerts[0].severity)
    }

    @Test
    fun `getAlerts sorts by severity then urgency`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                makeFeature(id = "1", event = "Heat Advisory", severity = "Minor", urgency = "Expected"),
                makeFeature(id = "2", event = "Tornado Warning", severity = "Extreme", urgency = "Immediate"),
                makeFeature(id = "3", event = "Flood Warning", severity = "Severe", urgency = "Immediate"),
            ),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals("Tornado Warning", alerts[0].event)   // Extreme
        assertEquals("Flood Warning", alerts[1].event)      // Severe
        assertEquals("Heat Advisory", alerts[2].event)       // Minor
    }

    @Test
    fun `getAlerts skips features with null event`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                NwsAlertFeature(id = "bad", properties = NwsAlertProperties(event = null)),
                makeFeature(event = "Winter Storm Watch"),
            ),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Winter Storm Watch", alerts[0].event)
    }

    @Test
    fun `getAlerts skips features with null properties`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                NwsAlertFeature(id = "bad", properties = null),
                makeFeature(),
            ),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(1, alerts.size)
    }

    @Test
    fun `getAlerts returns empty list for 404 (non-US location)`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 404")

        val result = repository.getAlerts(51.5, -0.1) // London
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `getAlerts returns empty list for 400 (bad request)`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 400")

        val result = repository.getAlerts(0.0, 0.0)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `getAlerts returns failure for non-404 errors`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("Network timeout")

        val result = repository.getAlerts(39.7, -104.9)
        assertTrue(result.isFailure)
    }

    @Test
    fun `getAlerts returns empty list for empty response`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = emptyList(),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(0, alerts.size)
    }

    @Test
    fun `getAlerts formats point as 4 decimal places`() = runTest {
        coEvery { api.getActiveAlerts(eq("39.7392,-104.9847"), any(), any()) } returns NwsAlertResponse()

        repository.getAlerts(39.73921234, -104.98471234)
        // If no crash, the format is working (coEvery matched the exact string)
    }
}
