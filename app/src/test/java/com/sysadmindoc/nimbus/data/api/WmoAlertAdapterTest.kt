package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import io.mockk.coEvery
import io.mockk.coVerify
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
        assertEquals("2026-06-11 23:32:00", alert.effective)
        assertEquals("2026-06-12 01:00:00", alert.expires)
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

        assertEquals("2026-06-11 23:30:00", alert.effective)
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
    fun `getAlerts returns empty without calling the SWIC APIs when only coordinates are available`() = runTest {
        val alerts = adapter.getAlerts(35.0, -105.0).getOrThrow()

        assertTrue(alerts.isEmpty())
        coVerify(exactly = 0) { api.getMembers() }
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
