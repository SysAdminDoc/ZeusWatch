package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JmaAlertAdapterTest {

    private val api = mockk<JmaAlertApi>()
    private val adapter = JmaAlertAdapter(api)

    @Test
    fun `happy path — maps all fields from a well-formed entry`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(
                    id = "jma-2026-storm-001",
                    title = "Heavy Rain Emergency Warning",
                    updated = "2026-06-15T12:00:00+09:00",
                    content = "Extremely heavy rain is forecast for Kagoshima.",
                    severity = "Extreme",
                    urgency = "Immediate",
                    certainty = "Observed",
                    area = "Kagoshima Prefecture",
                    onset = "2026-06-15T10:00:00+09:00",
                    expires = "2026-06-16T06:00:00+09:00",
                    author = "Japan Meteorological Agency",
                )
            )
        )

        val result = adapter.getAlerts(31.5, 130.5)
        assertTrue(result.isSuccess)
        val alert = result.getOrThrow().first()

        assertEquals("jma-2026-storm-001", alert.id)
        assertEquals("Heavy Rain Emergency Warning", alert.event)
        assertEquals("Heavy Rain Emergency Warning", alert.headline)
        assertEquals("Extremely heavy rain is forecast for Kagoshima.", alert.description)
        assertEquals(AlertSeverity.EXTREME, alert.severity)
        assertEquals(AlertUrgency.IMMEDIATE, alert.urgency)
        assertEquals("Observed", alert.certainty)
        assertEquals("Japan Meteorological Agency", alert.senderName)
        assertEquals("Kagoshima Prefecture", alert.areaDescription)
        assertEquals("2026-06-15T10:00:00+09:00", alert.effective)
        assertEquals("2026-06-16T06:00:00+09:00", alert.expires)
    }

    @Test
    fun `null-title entries are filtered out`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(id = "x", title = null, severity = "Minor"),         // filtered
                JmaAlertEntry(id = "y", title = "Flood Warning", severity = "Severe"), // kept
            )
        )

        val alerts = adapter.getAlerts(35.7, 139.7).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Flood Warning", alerts.first().event)
    }

    @Test
    fun `author field is used as senderName`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(
                    title = "Tsunami Advisory",
                    author = "JMA Pacific Warning Center",
                    severity = "Severe",
                )
            )
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals("JMA Pacific Warning Center", alert.senderName)
    }

    @Test
    fun `null author falls back to displayName Japan Meteorological Agency`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(title = "Snow Warning", author = null, severity = "Minor")
            )
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals("Japan Meteorological Agency", alert.senderName)
    }

    @Test
    fun `null area defaults to Japan`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(title = "High Wind Warning", area = null, severity = "Moderate")
            )
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals("Japan", alert.areaDescription)
    }

    @Test
    fun `onset is used as effective when present`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(
                    title = "Blizzard Warning",
                    onset = "2026-12-01T06:00:00+09:00",
                    updated = "2026-11-30T20:00:00+09:00",
                    severity = "Severe",
                )
            )
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals("2026-12-01T06:00:00+09:00", alert.effective)
    }

    @Test
    fun `updated is used as effective when onset is null`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(
                    title = "Fog Warning",
                    onset = null,
                    updated = "2026-06-15T08:00:00+09:00",
                    severity = "Minor",
                )
            )
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals("2026-06-15T08:00:00+09:00", alert.effective)
    }

    @Test
    fun `synthetic ID used when entry id is null`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(id = null, title = "Storm Warning", updated = "2026-06-15T12:00:00+09:00")
            )
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertTrue("ID should start with jma_", alert.id.startsWith("jma_"))
    }

    @Test
    fun `unknown severity maps to AlertSeverity UNKNOWN`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(JmaAlertEntry(title = "Information", severity = "Informational"))
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
    }

    @Test
    fun `null severity maps to AlertSeverity UNKNOWN`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(JmaAlertEntry(title = "Advisory", severity = null))
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
    }

    @Test
    fun `instruction is always null for JMA alerts`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(JmaAlertEntry(title = "Any Alert", severity = "Minor"))
        )

        val alert = adapter.getAlerts(35.7, 139.7).getOrThrow().first()
        assertEquals(null, alert.instruction)
    }

    @Test
    fun `multiple entries are all returned when all have titles`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(
            entries = listOf(
                JmaAlertEntry(id = "1", title = "Rain Warning", severity = "Moderate"),
                JmaAlertEntry(id = "2", title = "Wind Warning", severity = "Minor"),
                JmaAlertEntry(id = "3", title = "Tsunami Watch", severity = "Severe"),
            )
        )

        val alerts = adapter.getAlerts(35.7, 139.7).getOrThrow()
        assertEquals(3, alerts.size)
    }

    @Test
    fun `empty entry list returns empty success`() = runTest {
        coEvery { api.getAlerts() } returns JmaAlertResponse(entries = emptyList())

        val result = adapter.getAlerts(35.7, 139.7)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `API failure returns Result failure`() = runTest {
        coEvery { api.getAlerts() } throws RuntimeException("network error")

        val result = adapter.getAlerts(35.7, 139.7)
        assertTrue(result.isFailure)
    }
}
