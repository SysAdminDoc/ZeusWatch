package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteoAlarmAdapterTest {

    private val api = mockk<MeteoAlarmApi>()
    private val adapter = MeteoAlarmAdapter(api)

    // ── getAlerts (base interface) ───────────────────────────────────────

    @Test
    fun `getAlerts always returns empty list — country detection is at repository level`() = runTest {
        // No API call expected — this is by design.
        val result = adapter.getAlerts(48.85, 2.35) // Paris
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    // ── getAlertsForCountry ──────────────────────────────────────────────

    @Test
    fun `single warning with one info block maps all fields correctly`() = runTest {
        coEvery { api.getWarnings("de") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "de-2026-001",
                    sender = "DWD",
                    info = listOf(
                        MeteoAlarmInfo(
                            event = "Thunderstorm",
                            severity = "Severe",
                            urgency = "Immediate",
                            certainty = "Likely",
                            headline = "Severe Thunderstorm Warning",
                            description = "Large hail and strong winds expected.",
                            instruction = "Stay indoors.",
                            onset = "2026-06-15T12:00:00+02:00",
                            expires = "2026-06-15T18:00:00+02:00",
                            senderName = "Deutscher Wetterdienst",
                            area = listOf(MeteoAlarmArea(areaDesc = "Bavaria")),
                        )
                    )
                )
            )
        )

        val result = adapter.getAlertsForCountry("DE")
        assertTrue(result.isSuccess)
        val alerts = result.getOrThrow()
        assertEquals(1, alerts.size)

        val alert = alerts.first()
        assertEquals("de-2026-001", alert.id)
        assertEquals("Thunderstorm", alert.event)
        assertEquals("Severe Thunderstorm Warning", alert.headline)
        assertEquals("Large hail and strong winds expected.", alert.description)
        assertEquals("Stay indoors.", alert.instruction)
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertEquals(AlertUrgency.IMMEDIATE, alert.urgency)
        assertEquals("Likely", alert.certainty)
        assertEquals("Deutscher Wetterdienst", alert.senderName)
        assertEquals("Bavaria", alert.areaDescription)
        assertEquals("2026-06-15T12:00:00+02:00", alert.effective)
        assertEquals("2026-06-15T18:00:00+02:00", alert.expires)
    }

    @Test
    fun `country code is lowercased before API call`() = runTest {
        // Adapter calls meteoAlarmApi.getWarnings(countryCode.lowercase())
        coEvery { api.getWarnings("fr") } returns MeteoAlarmResponse(warnings = emptyList())

        val result = adapter.getAlertsForCountry("FR") // uppercase input
        assertTrue(result.isSuccess)
        // If the mock returned the response, lowercase was correctly applied
    }

    @Test
    fun `warning with null identifier uses synthetic id`() = runTest {
        coEvery { api.getWarnings("it") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = null,
                    info = listOf(
                        MeteoAlarmInfo(
                            event = "Flood",
                            severity = "Extreme",
                            onset = "2026-06-15T08:00:00+01:00",
                        )
                    )
                )
            )
        )

        val alert = adapter.getAlertsForCountry("IT").getOrThrow().first()
        assertTrue("ID should be non-empty", alert.id.isNotBlank())
        assertTrue("ID should contain adapter source", alert.id.startsWith("meteoalarm_"))
    }

    @Test
    fun `info with null event is skipped`() = runTest {
        coEvery { api.getWarnings("es") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "es-001",
                    info = listOf(
                        MeteoAlarmInfo(event = null, severity = "Minor"),            // skipped
                        MeteoAlarmInfo(event = "Heat Wave", severity = "Moderate"),  // kept
                    )
                )
            )
        )

        val alerts = adapter.getAlertsForCountry("ES").getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Heat Wave", alerts.first().event)
    }

    @Test
    fun `multiple info blocks in one warning produce multiple alerts`() = runTest {
        coEvery { api.getWarnings("nl") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "nl-multi",
                    info = listOf(
                        MeteoAlarmInfo(event = "Wind", severity = "Minor"),
                        MeteoAlarmInfo(event = "Rain", severity = "Moderate"),
                        MeteoAlarmInfo(event = "Fog", severity = "Minor"),
                    )
                )
            )
        )

        val alerts = adapter.getAlertsForCountry("NL").getOrThrow()
        assertEquals(3, alerts.size)
        assertEquals("Wind", alerts[0].event)
        assertEquals("Rain", alerts[1].event)
        assertEquals("Fog", alerts[2].event)
    }

    @Test
    fun `multiple area descriptions are joined with comma`() = runTest {
        coEvery { api.getWarnings("no") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "no-001",
                    info = listOf(
                        MeteoAlarmInfo(
                            event = "Avalanche",
                            severity = "Severe",
                            area = listOf(
                                MeteoAlarmArea(areaDesc = "Vestland"),
                                MeteoAlarmArea(areaDesc = "Troms og Finnmark"),
                            )
                        )
                    )
                )
            )
        )

        val alert = adapter.getAlertsForCountry("NO").getOrThrow().first()
        assertEquals("Vestland, Troms og Finnmark", alert.areaDescription)
    }

    @Test
    fun `area with all null areaDesc falls back to Unknown area`() = runTest {
        coEvery { api.getWarnings("gr") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "gr-001",
                    info = listOf(
                        MeteoAlarmInfo(
                            event = "Storm",
                            severity = "Moderate",
                            area = listOf(MeteoAlarmArea(areaDesc = null)),
                        )
                    )
                )
            )
        )

        val alert = adapter.getAlertsForCountry("GR").getOrThrow().first()
        assertEquals("Unknown area", alert.areaDescription)
    }

    @Test
    fun `severity minor maps to AlertSeverity MINOR`() = runTest {
        coEvery { api.getWarnings("se") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "se-001",
                    info = listOf(MeteoAlarmInfo(event = "Snow", severity = "Minor"))
                )
            )
        )
        val alert = adapter.getAlertsForCountry("SE").getOrThrow().first()
        assertEquals(AlertSeverity.MINOR, alert.severity)
    }

    @Test
    fun `severity extreme maps to AlertSeverity EXTREME`() = runTest {
        coEvery { api.getWarnings("pt") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "pt-001",
                    info = listOf(MeteoAlarmInfo(event = "Hurricane", severity = "Extreme"))
                )
            )
        )
        val alert = adapter.getAlertsForCountry("PT").getOrThrow().first()
        assertEquals(AlertSeverity.EXTREME, alert.severity)
    }

    @Test
    fun `empty warning list returns empty success`() = runTest {
        coEvery { api.getWarnings("at") } returns MeteoAlarmResponse(warnings = emptyList())

        val result = adapter.getAlertsForCountry("AT")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `senderName falls back to warning sender when info senderName is null`() = runTest {
        coEvery { api.getWarnings("be") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "be-001",
                    sender = "IRM-KMI",
                    info = listOf(
                        MeteoAlarmInfo(
                            event = "Fog",
                            severity = "Minor",
                            senderName = null,  // no info-level sender
                        )
                    )
                )
            )
        )

        val alert = adapter.getAlertsForCountry("BE").getOrThrow().first()
        assertEquals("IRM-KMI", alert.senderName)
    }

    @Test
    fun `senderName falls back to displayName when both warning sender and info senderName are null`() = runTest {
        coEvery { api.getWarnings("lu") } returns MeteoAlarmResponse(
            warnings = listOf(
                MeteoAlarmWarning(
                    identifier = "lu-001",
                    sender = null,
                    info = listOf(MeteoAlarmInfo(event = "Rain", severity = "Minor", senderName = null))
                )
            )
        )

        val alert = adapter.getAlertsForCountry("LU").getOrThrow().first()
        assertEquals("MeteoAlarm (EUMETNET)", alert.senderName)
    }
}
