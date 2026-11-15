package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration

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

    private fun member(
        mid: String = "093",
        name: String = "United States of America",
        dept: String = "National Weather Service",
    ) = WmoMember(
        mid = mid,
        name = name,
        dept = dept,
    )

    private fun warning(
        id: String = "urn:oid:us-warning-1",
        mid: String = "093",
        event: String? = "Tornado Warning",
        headline: String? = "Tornado Warning issued for central county",
        areaDesc: String? = "Central County",
        severity: Int? = 4,
        urgency: Int? = 4,
        certainty: Int? = 4,
        effective: String? = "2026-06-11 23:32:00",
        sent: String? = "2026-06-11 23:30:00",
        expires: String? = "2026-06-12 01:00:00",
        capUrl: String? = "us-noaa-nws-en/2026/06/11/23/34/24-700-281.xml",
    ) = WmoWarning(
        id = id,
        event = event,
        headline = headline,
        sent = sent,
        expires = expires,
        areaDesc = areaDesc,
        mid = mid,
        s = severity,
        u = urgency,
        c = certainty,
        capURL = capUrl,
        effective = effective,
    )

    /** Live row shapes from SWIC's GeoServer on 2026-11-14, times as the server wrote them. */
    private fun petropavlPolygon() = WmoWfsAlert(
        capurl = "kz-kazhydromet-en/2026/11/14/10/07/01-7c3b2f9a1e.xml",
        event = "Wind",
        headline = "",
        description = "No description",
        areadesc = "Kyzylzhar district (North Kazakhstan Region)",
        sent = "2026-11-14T02:07:01Z",
        effective = null,
        onset = "2026-11-14T07:00:00Z",
        expires = "2026-11-15T07:00:00Z",
        mem = "070",
        s = 2,
        u = 3,
        c = 3,
        rowType = "POLYGON",
    )

    private fun nenjiangPoint() = WmoWfsAlert(
        capurl = "cn-cma-xx/2026/11/14/23/45/00-21254871129dd01f22546113d6c55ac3.xml",
        event = "Dense fog",
        headline = "",
        description = "At 07:45 on November 15, 2026, the Nenjiang Meteorological Observatory",
        areadesc = "Nenjiang City",
        sent = "2026-11-14T15:45:00Z",
        effective = "2026-11-14T15:43:53Z",
        onset = null,
        expires = null,
        mem = "001",
        s = 3,
        u = 0,
        c = 0,
        rowType = "POINT",
    )

    private fun respondMembers(vararg members: WmoMember) {
        coEvery { api.getMembers() } returns listOf(WmoMemberRegion(ra = 4, members = members.toList()))
    }

    private fun respondWarnings(vararg warnings: WmoWarning) {
        coEvery { api.getWarnings() } returns WmoWarningsResponse(
            itemCount = warnings.size,
            lastUpdated = "2026-06-11 23:38:04",
            items = warnings.toList(),
        )
    }

    private fun respondFeatures(vararg rows: WmoWfsAlert) {
        coEvery { api.getFeatures(any(), any(), any(), any(), any(), any(), any()) } returns
            WmoWfsResponse(features = rows.map { WmoWfsFeature(properties = it) })
    }

    // ── point query (issue #58) ───────────────────────────────────────────

    @Test
    fun `point query marks polygon rows as covering the point and fixes the server clock`() = runTest {
        respondMembers(member(mid = "070", name = "Kazakhstan", dept = "Kazhydromet"))
        respondFeatures(petropavlPolygon())

        val alert = adapter.getAlertsForLocation(54.87, 69.15, "KZ").getOrThrow().single()

        assertEquals(true, alert.coversRequestedLocation)
        // The CAP path says 10:07:01 UTC; the server labelled it 02:07:01Z.
        assertEquals("2026-11-14T15:00:00Z", alert.effective)
        assertEquals("2026-11-15T15:00:00Z", alert.expires)
        assertEquals("Wind", alert.headline)
        assertEquals("Wind", alert.description)
        assertEquals("Kyzylzhar district (North Kazakhstan Region)", alert.areaDescription)
        assertEquals("Kazhydromet", alert.senderName)
        assertEquals(AlertSeverity.MODERATE, alert.severity)
        assertEquals(AlertUrgency.EXPECTED, alert.urgency)
    }

    @Test
    fun `point query leaves country-centroid rows unconfirmed`() = runTest {
        respondMembers(member(mid = "001", name = "China", dept = "China Meteorological Administration"))
        respondFeatures(nenjiangPoint())

        val alert = adapter.getAlertsForLocation(39.90, 116.40, "CN").getOrThrow().single()

        assertNull(alert.coversRequestedLocation)
        assertEquals("2026-11-14T23:43:53Z", alert.effective)
        assertNull(alert.expires)
        assertEquals("At 07:45 on November 15, 2026, the Nenjiang Meteorological Observatory", alert.description)
        assertEquals("China Meteorological Administration", alert.senderName)
    }

    @Test
    fun `point query asks for the member's geometry-less rows only for its own country`() = runTest {
        respondMembers(
            member(mid = "001", name = "China"),
            member(mid = "002", name = "Hong Kong, China"),
            member(mid = "072", name = "Macao, China"),
        )
        val filter = slot<String>()
        coEvery { api.getFeatures(capture(filter), any(), any(), any(), any(), any(), any()) } returns WmoWfsResponse()

        adapter.getAlertsForLocation(39.90, 116.40, "CN").getOrThrow()

        assertTrue(filter.captured.contains("<gml:coordinates>116.40000,39.90000</gml:coordinates>"))
        assertTrue(filter.captured.contains("<Literal>001</Literal>"))
        assertFalse(filter.captured.contains("<Literal>002</Literal>"))
        assertFalse(filter.captured.contains("<Literal>072</Literal>"))
    }

    @Test
    fun `coordinates-only lookup asks for polygon matches alone`() = runTest {
        respondMembers(member())
        val filter = slot<String>()
        coEvery { api.getFeatures(capture(filter), any(), any(), any(), any(), any(), any()) } returns
            WmoWfsResponse(features = listOf(WmoWfsFeature(petropavlPolygon())))

        val alerts = adapter.getAlerts(54.87, 69.15).getOrThrow()

        assertEquals(1, alerts.size)
        assertTrue(filter.captured.contains("<Intersects>"))
        assertFalse(filter.captured.contains("row_type"))
        coVerify(exactly = 0) { api.getWarnings() }
    }

    @Test
    fun `point filter skips the member clause without member ids`() {
        val filter = WmoAlertAdapter.pointFilter(55.75, 37.62, emptySet())

        assertTrue(filter.contains("<gml:coordinates>37.62000,55.75000</gml:coordinates>"))
        assertTrue(filter.contains("<PropertyIsNotEqualTo><PropertyName>mem</PropertyName><Literal>0</Literal>"))
        assertFalse(filter.contains("row_type"))
    }

    @Test
    fun `clock shift comes from the CAP path and rounds to a quarter hour`() {
        assertEquals(Duration.ofHours(8), WmoAlertAdapter.wfsClockShift(petropavlPolygon()))
        assertEquals(
            Duration.ofHours(8),
            WmoAlertAdapter.wfsClockShift(nenjiangPoint().copy(sent = "2026-11-14T15:44:10Z")),
        )
        assertNull(WmoAlertAdapter.wfsClockShift(nenjiangPoint().copy(capurl = "cn-cma-xx/no-time.xml")))
        // A path more than 14 hours off is someone else's clock, not a usable shift.
        assertNull(WmoAlertAdapter.wfsClockShift(nenjiangPoint().copy(sent = "2026-11-13T00:00:00Z")))
    }

    @Test
    fun `rows without a dated CAP path borrow the response's shift`() = runTest {
        respondMembers(member(mid = "001", name = "China"))
        respondFeatures(
            nenjiangPoint(),
            nenjiangPoint().copy(
                capurl = "cn-cma-xx/undated-warning.xml",
                sent = "2026-11-14T16:00:00Z",
                effective = null,
                expires = "2026-11-15T04:00:00Z",
            ),
        )

        val undated = adapter.getAlertsForLocation(39.90, 116.40, "CN").getOrThrow()
            .single { it.id.endsWith("undated-warning.xml") }

        assertEquals("2026-11-15T00:00:00Z", undated.effective)
        assertEquals("2026-11-15T12:00:00Z", undated.expires)
    }

    @Test
    fun `point query collapses duplicate area rows of one alert`() = runTest {
        respondMembers(member(mid = "070", name = "Kazakhstan"))
        respondFeatures(petropavlPolygon(), petropavlPolygon().copy(areadesc = "Petropavl"))

        val alerts = adapter.getAlertsForLocation(54.87, 69.15, "KZ").getOrThrow()

        assertEquals(1, alerts.size)
    }

    @Test
    fun `point query falls back to the country feed when the map server fails`() = runTest {
        respondMembers(member(mid = "107", name = "Russian Federation", dept = "Roshydromet"))
        coEvery { api.getFeatures(any(), any(), any(), any(), any(), any(), any()) } throws IOException("timeout")
        respondWarnings(warning(id = "ru-1", mid = "107"), warning(id = "us-1", mid = "093"))

        val alert = adapter.getAlertsForLocation(55.75, 37.62, "RU").getOrThrow().single()

        assertEquals("ru-1", alert.id)
        assertNull(alert.coversRequestedLocation)
        assertEquals("2026-06-11T23:32:00Z", alert.effective)
    }

    @Test
    fun `point query without a country reports a map server failure`() = runTest {
        respondMembers(member())
        coEvery { api.getFeatures(any(), any(), any(), any(), any(), any(), any()) } throws IOException("timeout")

        assertTrue(adapter.getAlertsForLocation(55.75, 37.62, null).isFailure)
        coVerify(exactly = 0) { api.getWarnings() }
    }

    @Test
    fun `point query still answers when the member list is down`() = runTest {
        coEvery { api.getMembers() } throws IOException("members down")
        respondFeatures(petropavlPolygon())

        val alert = adapter.getAlertsForLocation(54.87, 69.15, "KZ").getOrThrow().single()

        assertEquals("WMO SWIC", alert.senderName)
    }

    // ── member matching ───────────────────────────────────────────────────

    private val lookalikeMembers = listOf(
        member(mid = "001", name = "China"),
        member(mid = "002", name = "Hong Kong, China"),
        member(mid = "072", name = "Macao, China"),
        member(mid = "074", name = "Niger"),
        member(mid = "075", name = "Nigeria"),
        member(mid = "067", name = "Ireland"),
        member(mid = "010", name = "United Kingdom of Great Britain and Northern Ireland"),
        member(mid = "085", name = "Sudan"),
        member(mid = "203", name = "South Sudan"),
        member(mid = "095", name = "Republic of Korea"),
        member(mid = "120", name = "Democratic People's Republic of Korea"),
    )

    @Test
    fun `member lookup prefers an exact name over names that contain it`() {
        assertEquals(setOf("001"), adapter.memberIdsFor(lookalikeMembers, "CN"))
        assertEquals(setOf("002"), adapter.memberIdsFor(lookalikeMembers, "HK"))
        assertEquals(setOf("072"), adapter.memberIdsFor(lookalikeMembers, "MO"))
        assertEquals(setOf("074"), adapter.memberIdsFor(lookalikeMembers, "NE"))
        assertEquals(setOf("075"), adapter.memberIdsFor(lookalikeMembers, "NG"))
        assertEquals(setOf("067"), adapter.memberIdsFor(lookalikeMembers, "IE"))
        assertEquals(setOf("085"), adapter.memberIdsFor(lookalikeMembers, "SD"))
        assertEquals(setOf("203"), adapter.memberIdsFor(lookalikeMembers, "SS"))
        assertEquals(setOf("095"), adapter.memberIdsFor(lookalikeMembers, "KR"))
    }

    @Test
    fun `member lookup still finds a long official name by substring`() {
        assertEquals(setOf("010"), adapter.memberIdsFor(lookalikeMembers, "GB"))
    }

    @Test
    fun `country names cover the member-list spellings`() {
        val names = WmoAlertAdapter.memberCountryNames("ru")
        assertTrue("Russia" in names)
        assertTrue("Russian Federation" in names)
        assertTrue(WmoAlertAdapter.memberCountryNames("").isEmpty())
    }

    // ── country feed ──────────────────────────────────────────────────────

    @Test
    fun `country feed times are UTC`() {
        assertEquals("2026-11-15T02:09:00Z", WmoAlertAdapter.utcFeedTime("2026-11-15 02:09:00"))
        assertEquals("2026-11-15T02:09:00Z", WmoAlertAdapter.utcFeedTime("2026-11-15T02:09:00Z"))
        assertNull(WmoAlertAdapter.utcFeedTime(null))
    }

    @Test
    fun `getAlertsForCountry maps live SWIC schema by WMO member country`() = runTest {
        respondMembers(
            member(mid = "093", name = "United States of America"),
            member(mid = "038", name = "Belize", dept = "National Meteorological Service"),
        )
        respondWarnings(
            warning(mid = "093"),
            warning(id = "urn:oid:bz-warning-1", mid = "038", event = "Wind", headline = "Small craft caution"),
        )

        val alerts = adapter.getAlertsForCountry("US").getOrThrow()

        assertEquals(1, alerts.size)
        val alert = alerts.first()
        assertEquals("urn:oid:us-warning-1", alert.id)
        assertEquals("Tornado Warning", alert.event)
        assertEquals("Tornado Warning issued for central county", alert.headline)
        assertEquals("Central County", alert.areaDescription)
        assertEquals("National Weather Service", alert.senderName)
        assertEquals(AlertSeverity.EXTREME, alert.severity)
        assertEquals(AlertUrgency.IMMEDIATE, alert.urgency)
        assertEquals("Observed", alert.certainty)
        assertEquals("2026-06-11T23:32:00Z", alert.effective)
        assertEquals("2026-06-12T01:00:00Z", alert.expires)
    }

    @Test
    fun `getAlertsForCountry maps SWIC numeric codes`() = runTest {
        respondMembers(member())
        respondWarnings(
            warning(id = "unknown", severity = 0, urgency = 0, certainty = 0),
            warning(id = "minor", severity = 1, urgency = 1, certainty = 1),
            warning(id = "moderate", severity = 2, urgency = 2, certainty = 2),
            warning(id = "severe", severity = 3, urgency = 3, certainty = 3),
            warning(id = "extreme", severity = 4, urgency = 4, certainty = 4),
        )

        val alerts = adapter.getAlertsForCountry("US").getOrThrow().associateBy { it.id }

        assertEquals(AlertSeverity.UNKNOWN, alerts.getValue("unknown").severity)
        assertEquals(AlertSeverity.MINOR, alerts.getValue("minor").severity)
        assertEquals(AlertSeverity.MODERATE, alerts.getValue("moderate").severity)
        assertEquals(AlertSeverity.SEVERE, alerts.getValue("severe").severity)
        assertEquals(AlertSeverity.EXTREME, alerts.getValue("extreme").severity)
        assertEquals(AlertUrgency.PAST, alerts.getValue("minor").urgency)
        assertEquals(AlertUrgency.FUTURE, alerts.getValue("moderate").urgency)
        assertEquals(AlertUrgency.EXPECTED, alerts.getValue("severe").urgency)
        assertEquals(AlertUrgency.IMMEDIATE, alerts.getValue("extreme").urgency)
        assertEquals("Unknown", alerts.getValue("unknown").certainty)
        assertEquals("Unlikely", alerts.getValue("minor").certainty)
        assertEquals("Possible", alerts.getValue("moderate").certainty)
        assertEquals("Likely", alerts.getValue("severe").certainty)
        assertEquals("Observed", alerts.getValue("extreme").certainty)
    }

    @Test
    fun `getAlertsForCountry falls back from blank effective to sent time`() = runTest {
        respondMembers(member())
        respondWarnings(warning(effective = "", sent = "2026-06-11 23:30:00"))

        val alert = adapter.getAlertsForCountry("US").getOrThrow().single()

        assertEquals("2026-06-11T23:30:00Z", alert.effective)
    }

    @Test
    fun `getAlertsForCountry matches country aliases used by WMO members`() = runTest {
        respondMembers(member(mid = "107", name = "Russian Federation", dept = "Roshydromet"))
        respondWarnings(warning(mid = "107"))

        val alerts = adapter.getAlertsForCountry("RU").getOrThrow()

        assertEquals(1, alerts.size)
        assertEquals("Roshydromet", alerts.single().senderName)
    }

    @Test
    fun `getAlertsForCountry filters warnings when no WMO member matches the country`() = runTest {
        respondMembers(member(mid = "038", name = "Belize"))

        val alerts = adapter.getAlertsForCountry("US").getOrThrow()

        assertTrue(alerts.isEmpty())
        coVerify(exactly = 0) { api.getWarnings() }
    }

    @Test
    fun `getAlertsForCountry filters blank events and ids`() = runTest {
        respondMembers(member())
        respondWarnings(
            warning(id = "valid"),
            warning(id = "", event = "Wind", capUrl = ""),
            warning(id = "blank-event", event = ""),
        )

        val alerts = adapter.getAlertsForCountry("US").getOrThrow()

        assertEquals(listOf("valid"), alerts.map { it.id })
    }

    @Test
    fun `getAlertsForCountry collapses duplicate alert ids`() = runTest {
        respondMembers(member())
        respondWarnings(
            warning(id = "duplicate", headline = "English headline"),
            warning(id = "duplicate", headline = "Spanish headline"),
        )

        val alerts = adapter.getAlertsForCountry("US").getOrThrow()

        assertEquals(1, alerts.size)
        assertEquals("English headline", alerts.single().headline)
    }

    @Test
    fun `getAlertsForCountry returns empty success on 404`() = runTest {
        respondMembers(member())
        coEvery { api.getWarnings() } throws httpException(404)

        val result = adapter.getAlertsForCountry("US")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAlertsForCountry returns empty success on 400`() = runTest {
        respondMembers(member())
        coEvery { api.getWarnings() } throws httpException(400)

        val result = adapter.getAlertsForCountry("US")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAlertsForCountry returns failure on server error`() = runTest {
        respondMembers(member())
        coEvery { api.getWarnings() } throws httpException(500)

        assertTrue(adapter.getAlertsForCountry("US").isFailure)
    }

    @Test
    fun `getAlertsForCountry returns failure on other exceptions`() = runTest {
        respondMembers(member())
        coEvery { api.getWarnings() } throws RuntimeException("Connection timeout")

        val result = adapter.getAlertsForCountry("US")

        assertTrue(result.isFailure)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
    }
}
