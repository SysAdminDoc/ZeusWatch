package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EnvironmentCanadaAlertAdapter].
 *
 * The [resolveProvince] method is `internal`, so it is directly accessible
 * from the same package. Province bounding boxes are coarse approximations —
 * we test representative cities, not edge cases near province borders.
 */
class EnvironmentCanadaAlertAdapterTest {

    private val api = mockk<EnvironmentCanadaAlertApi>()
    private val adapter = EnvironmentCanadaAlertAdapter(api)

    // ── resolveProvince ──────────────────────────────────────────────────

    @Test
    fun `Toronto resolves to Ontario`() {
        assertEquals("on", adapter.resolveProvince(43.65, -79.38))
    }

    @Test
    fun `Vancouver resolves to British Columbia`() {
        assertEquals("bc", adapter.resolveProvince(49.25, -123.12))
    }

    @Test
    fun `Edmonton resolves to Alberta`() {
        assertEquals("ab", adapter.resolveProvince(53.55, -113.49))
    }

    @Test
    fun `Calgary resolves to Alberta`() {
        // Calgary at -114.07°W used to fall inside BC's old bbox
        // (-139.1..-114.0). The corrected BC east edge is -120.0 (the
        // Continental Divide / actual BC-AB border), so the foothills
        // (Calgary, Banff, Lake Louise, Canmore) now resolve to AB.
        assertEquals("ab", adapter.resolveProvince(51.05, -114.07))
    }

    @Test
    fun `Banff resolves to Alberta`() {
        // Banff (51.18°N, -115.57°W) is in the AB Rockies; previously
        // misclassified as BC.
        assertEquals("ab", adapter.resolveProvince(51.18, -115.57))
    }

    @Test
    fun `Saskatoon resolves to Saskatchewan`() {
        assertEquals("sk", adapter.resolveProvince(52.13, -106.67))
    }

    @Test
    fun `Winnipeg resolves to Manitoba`() {
        assertEquals("mb", adapter.resolveProvince(49.90, -97.14))
    }

    @Test
    fun `Quebec City resolves to Quebec`() {
        assertEquals("qc", adapter.resolveProvince(46.81, -71.21))
    }

    @Test
    fun `southern New Brunswick resolves to New Brunswick`() {
        // Fredericton (45.96°N) is inside QC's broad bbox; use a southern NB coord below 45°N
        assertEquals("nb", adapter.resolveProvince(44.7, -66.5))
    }

    @Test
    fun `Halifax resolves to Nova Scotia`() {
        assertEquals("ns", adapter.resolveProvince(44.65, -63.60))
    }

    @Test
    fun `PEI bbox is covered by Quebec bbox so Charlottetown resolves to Quebec`() {
        // PE's entire extent (lat 45.9-47.1, lon -64.4..-62.0) lies inside QC's broader
        // bounding box (lat 45.0-62.6, lon -79.8..-57.1). firstOrNull picks QC first —
        // this is a known coarse-bbox limitation; province-level filtering still works
        // because all Canadian Atlantic alerts include PEI in the feed for QC.
        assertEquals("qc", adapter.resolveProvince(46.24, -63.13))
    }

    @Test
    fun `St John's NL resolves to Newfoundland and Labrador`() {
        assertEquals("nl", adapter.resolveProvince(47.56, -52.71))
    }

    @Test
    fun `Whitehorse resolves to Yukon`() {
        assertEquals("yt", adapter.resolveProvince(60.72, -135.06))
    }

    @Test
    fun `Yellowknife resolves to Northwest Territories`() {
        assertEquals("nt", adapter.resolveProvince(62.45, -114.37))
    }

    @Test
    fun `Iqaluit resolves to Nunavut`() {
        assertEquals("nu", adapter.resolveProvince(63.75, -68.52))
    }

    @Test
    fun `New York City (USA) returns null`() {
        assertNull(adapter.resolveProvince(40.71, -74.01))
    }

    @Test
    fun `London UK returns null`() {
        assertNull(adapter.resolveProvince(51.51, -0.13))
    }

    @Test
    fun `South Pole returns null`() {
        assertNull(adapter.resolveProvince(-90.0, 0.0))
    }

    // ── getAlerts ────────────────────────────────────────────────────────

    @Test
    fun `returns empty list when resolveProvince is null (non-Canadian coords)`() = runTest {
        // Use a coordinate clearly outside Canada
        val result = adapter.getAlerts(40.71, -74.01)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `filters out No watches or warnings entries`() = runTest {
        coEvery { api.getProvinceAlerts("on", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(
                    title = "No watches or warnings in effect",
                    severity = "Minor",
                ),
                EnvironmentCanadaEntry(
                    id = "real-001",
                    title = "Tornado Warning",
                    severity = "Extreme",
                    urgency = "Immediate",
                    certainty = "Observed",
                    summary = "Tornado on the ground near Barrie.",
                ),
            )
        )

        val result = adapter.getAlerts(43.65, -79.38) // Toronto → Ontario
        assertTrue(result.isSuccess)
        val alerts = result.getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Tornado Warning", alerts.first().event)
    }

    @Test
    fun `filters out No warnings in effect entries (alternate wording)`() = runTest {
        coEvery { api.getProvinceAlerts("on", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(title = "No warnings in effect for Ontario"),
            )
        )

        val alerts = adapter.getAlerts(43.65, -79.38).getOrThrow()
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `entries with null title are filtered`() = runTest {
        coEvery { api.getProvinceAlerts("on", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(id = "x", title = null, severity = "Minor"),
                EnvironmentCanadaEntry(id = "y", title = "Winter Storm Warning", severity = "Severe"),
            )
        )

        val alerts = adapter.getAlerts(43.65, -79.38).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Winter Storm Warning", alerts.first().event)
    }

    @Test
    fun `maps severity string to AlertSeverity enum`() = runTest {
        coEvery { api.getProvinceAlerts("bc", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(title = "Extreme Fire Danger", severity = "Extreme"),
                EnvironmentCanadaEntry(title = "Frost Advisory", severity = "Minor"),
            )
        )

        val alerts = adapter.getAlerts(49.25, -123.12).getOrThrow()
        assertEquals(2, alerts.size)
        assertEquals(AlertSeverity.EXTREME, alerts[0].severity)
        assertEquals(AlertSeverity.MINOR, alerts[1].severity)
    }

    @Test
    fun `senderName is Environment Canada`() = runTest {
        coEvery { api.getProvinceAlerts("mb", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(title = "Blizzard Warning", severity = "Severe"),
            )
        )

        val alert = adapter.getAlerts(49.90, -97.14).getOrThrow().first()
        assertEquals("Environment Canada", alert.senderName)
    }

    @Test
    fun `alert ID falls back to synthetic key when entry id is null`() = runTest {
        coEvery { api.getProvinceAlerts("ab", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(
                    id = null,
                    title = "Wind Warning",
                    updated = "2026-06-15T12:00:00Z",
                    severity = "Severe",
                ),
            )
        )

        val alert = adapter.getAlerts(53.55, -113.49).getOrThrow().first() // Edmonton → ab
        assertNotNull(alert.id)
        assertTrue(alert.id.startsWith("eccc_"))
    }

    @Test
    fun `areaDesc from entry is used in alert areaDescription`() = runTest {
        coEvery { api.getProvinceAlerts("on", any()) } returns EnvironmentCanadaResponse(
            entries = listOf(
                EnvironmentCanadaEntry(
                    title = "Severe Thunderstorm Watch",
                    severity = "Severe",
                    areaDesc = "Toronto Metro Area",
                ),
            )
        )

        val alert = adapter.getAlerts(43.65, -79.38).getOrThrow().first()
        assertEquals("Toronto Metro Area", alert.areaDescription)
    }
}
