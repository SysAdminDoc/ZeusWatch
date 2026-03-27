package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.NwsAlertAdapter
import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.NwsAlertFeature
import com.sysadmindoc.nimbus.data.api.NwsAlertProperties
import com.sysadmindoc.nimbus.data.api.NwsAlertResponse
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NwsAlertAdapterTest {

    private lateinit var api: NwsAlertApi
    private lateinit var adapter: NwsAlertAdapter

    @Before
    fun setup() {
        api = mockk()
        adapter = NwsAlertAdapter(api)
    }

    private fun makeFeature(
        id: String = "urn:oid:2.49.0.1.840.0.alert-1",
        event: String = "Tornado Warning",
        severity: String = "Extreme",
        urgency: String = "Immediate",
        certainty: String = "Observed",
        headline: String? = null,
        description: String = "A tornado has been sighted.",
        instruction: String? = "Take shelter immediately.",
        senderName: String = "NWS Denver",
        areaDesc: String = "Denver Metro Area",
        effective: String? = "2025-06-15T12:00:00-06:00",
        expires: String? = "2025-06-15T14:00:00-06:00",
        response: String? = "Shelter",
    ) = NwsAlertFeature(
        id = id,
        properties = NwsAlertProperties(
            event = event,
            headline = headline ?: "$event for Test County",
            description = description,
            instruction = instruction,
            severity = severity,
            urgency = urgency,
            certainty = certainty,
            senderName = senderName,
            areaDesc = areaDesc,
            effective = effective,
            expires = expires,
            response = response,
        ),
    )

    // --- Successful parsing ---

    @Test
    fun getAlertsReturnsParsedAlertsOnSuccess() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                makeFeature(event = "Tornado Warning", severity = "Extreme"),
                makeFeature(id = "alert-2", event = "Flood Watch", severity = "Moderate"),
            ),
        )

        val result = adapter.getAlerts(39.74, -104.98)

        assertTrue(result.isSuccess)
        val alerts = result.getOrThrow()
        assertEquals(2, alerts.size)
        assertEquals("Tornado Warning", alerts[0].event)
        assertEquals("Flood Watch", alerts[1].event)
    }

    @Test
    fun getAlertsFormatsPointCorrectly() = runTest {
        val pointSlot = slot<String>()
        coEvery { api.getActiveAlerts(capture(pointSlot), any(), any()) } returns NwsAlertResponse()

        adapter.getAlerts(39.7392, -104.9847)

        assertEquals("39.7392,-104.9847", pointSlot.captured)
    }

    @Test
    fun getAlertsMapsAllFieldsCorrectly() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature(
                id = "alert-xyz",
                event = "Winter Storm Warning",
                severity = "Severe",
                urgency = "Expected",
                certainty = "Likely",
                description = "Heavy snow expected.",
                instruction = "Avoid travel.",
                senderName = "NWS Boulder",
                areaDesc = "Front Range Mountains",
                effective = "2025-01-10T06:00:00",
                expires = "2025-01-11T06:00:00",
                response = "Prepare",
            )),
        )

        val alert = adapter.getAlerts(40.0, -105.0).getOrThrow().first()

        assertEquals("alert-xyz", alert.id)
        assertEquals("Winter Storm Warning", alert.event)
        assertEquals("Winter Storm Warning for Test County", alert.headline)
        assertEquals("Heavy snow expected.", alert.description)
        assertEquals("Avoid travel.", alert.instruction)
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertEquals(AlertUrgency.EXPECTED, alert.urgency)
        assertEquals("Likely", alert.certainty)
        assertEquals("NWS Boulder", alert.senderName)
        assertEquals("Front Range Mountains", alert.areaDescription)
        assertEquals("2025-01-10T06:00:00", alert.effective)
        assertEquals("2025-01-11T06:00:00", alert.expires)
        assertEquals("Prepare", alert.response)
    }

    // --- Severity mapping ---

    @Test
    fun alertSeverityMapsExtreme() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature(severity = "Extreme")),
        )
        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals(AlertSeverity.EXTREME, alert.severity)
    }

    @Test
    fun alertSeverityMapsSevere() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature(severity = "Severe")),
        )
        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals(AlertSeverity.SEVERE, alert.severity)
    }

    @Test
    fun alertSeverityMapsModerate() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature(severity = "Moderate")),
        )
        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals(AlertSeverity.MODERATE, alert.severity)
    }

    @Test
    fun alertSeverityMapsMinor() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature(severity = "Minor")),
        )
        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals(AlertSeverity.MINOR, alert.severity)
    }

    @Test
    fun alertSeverityMapsUnknownForInvalidValue() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature(severity = "NotARealSeverity")),
        )
        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
    }

    @Test
    fun alertSeverityMapsUnknownForNull() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(NwsAlertFeature(
                id = "a1",
                properties = NwsAlertProperties(
                    event = "Test Event",
                    severity = null,
                ),
            )),
        )
        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
    }

    // --- Empty response ---

    @Test
    fun getAlertsReturnsEmptyListForEmptyResponse() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = emptyList(),
        )

        val result = adapter.getAlerts(39.0, -104.0)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    // --- Null filtering ---

    @Test
    fun getAlertsSkipsFeaturesWithNullProperties() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                NwsAlertFeature(id = "bad", properties = null),
                makeFeature(event = "Good Alert"),
            ),
        )

        val alerts = adapter.getAlerts(39.0, -104.0).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Good Alert", alerts[0].event)
    }

    @Test
    fun getAlertsSkipsFeaturesWithNullEvent() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                NwsAlertFeature(id = "bad", properties = NwsAlertProperties(event = null)),
                makeFeature(event = "Valid Event"),
            ),
        )

        val alerts = adapter.getAlerts(39.0, -104.0).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Valid Event", alerts[0].event)
    }

    // --- Error handling ---

    @Test
    fun getAlertsReturnsEmptyListOn404() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 404")

        val result = adapter.getAlerts(39.0, -104.0)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun getAlertsReturnsEmptyListOn400() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 400")

        val result = adapter.getAlerts(39.0, -104.0)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun getAlertsReturnsFailureOnOtherErrors() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("Connection timeout")

        val result = adapter.getAlerts(39.0, -104.0)
        assertTrue(result.isFailure)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun getAlertsReturnsFailureOnServerError() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 500 Internal Server Error")

        val result = adapter.getAlerts(39.0, -104.0)
        assertTrue(result.isFailure)
    }

    // --- Fallback field handling ---

    @Test
    fun alertUsesOnsetWhenEffectiveIsNull() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(NwsAlertFeature(
                id = "a1",
                properties = NwsAlertProperties(
                    event = "Heat Advisory",
                    effective = null,
                    onset = "2025-07-01T10:00:00",
                    expires = "2025-07-01T20:00:00",
                ),
            )),
        )

        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals("2025-07-01T10:00:00", alert.effective)
    }

    @Test
    fun alertUsesEndsWhenExpiresIsNull() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(NwsAlertFeature(
                id = "a1",
                properties = NwsAlertProperties(
                    event = "Heat Advisory",
                    effective = "2025-07-01T10:00:00",
                    expires = null,
                    ends = "2025-07-01T22:00:00",
                ),
            )),
        )

        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals("2025-07-01T22:00:00", alert.expires)
    }

    @Test
    fun alertUsesAlertIdWhenFeatureIdIsNull() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(NwsAlertFeature(
                id = null,
                properties = NwsAlertProperties(
                    alertId = "urn:fallback-id",
                    event = "Test Alert",
                ),
            )),
        )

        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals("urn:fallback-id", alert.id)
    }

    @Test
    fun alertUsesEventAsHeadlineWhenHeadlineIsNull() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(NwsAlertFeature(
                id = "a1",
                properties = NwsAlertProperties(
                    event = "Blizzard Warning",
                    headline = null,
                ),
            )),
        )

        val alert = adapter.getAlerts(39.0, -104.0).getOrThrow().first()
        assertEquals("Blizzard Warning", alert.headline)
    }

    // --- Adapter metadata ---

    @Test
    fun adapterHasCorrectSourceId() {
        assertEquals("nws", adapter.sourceId)
    }

    @Test
    fun adapterHasCorrectDisplayName() {
        assertEquals("National Weather Service", adapter.displayName)
    }

    @Test
    fun adapterSupportsUsRegion() {
        assertTrue(adapter.supportedRegions.contains("US"))
        assertEquals(1, adapter.supportedRegions.size)
    }
}
