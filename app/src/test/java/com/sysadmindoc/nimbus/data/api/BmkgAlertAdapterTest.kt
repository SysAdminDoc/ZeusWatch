package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BmkgAlertAdapterTest {

    private lateinit var api: BmkgAlertApi
    private lateinit var adapter: BmkgAlertAdapter

    @Before
    fun setup() {
        api = mockk()
        adapter = BmkgAlertAdapter(api)
    }

    @Test
    fun `maps CAP detail that covers requested coordinate`() = runTest {
        coEvery { api.getNowcastFeed() } returns rssFeed().xmlBody()
        coEvery { api.getAlertDetail(DETAIL_URL) } returns capAlert().xmlBody()

        val alerts = adapter.getAlerts(-6.2, 106.8).getOrThrow()

        assertEquals(1, alerts.size)
        val alert = alerts.single()
        assertEquals("CSL20260701002", alert.id)
        assertEquals("Thunderstorm", alert.event)
        assertEquals("Thunderstorm early warning", alert.headline)
        assertEquals(AlertSeverity.MODERATE, alert.severity)
        assertEquals(AlertUrgency.IMMEDIATE, alert.urgency)
        assertEquals("Observed", alert.certainty)
        assertEquals("Indonesia Agency for Meteorology, Climatology, and Geophysics", alert.senderName)
        assertEquals("Jakarta Selatan", alert.areaDescription)
        assertEquals(true, alert.coversRequestedLocation)
        assertTrue(alert.geometry?.contains(-6.2, 106.8) == true)
        coVerify(exactly = 1) { api.getNowcastFeed() }
        coVerify(exactly = 1) { api.getAlertDetail(DETAIL_URL) }
    }

    @Test
    fun `filters CAP detail outside requested coordinate`() = runTest {
        coEvery { api.getNowcastFeed() } returns rssFeed().xmlBody()
        coEvery { api.getAlertDetail(DETAIL_URL) } returns capAlert().xmlBody()

        val alerts = adapter.getAlerts(-8.6, 116.1).getOrThrow()

        assertTrue(alerts.isEmpty())
        coVerify(exactly = 1) { api.getAlertDetail(DETAIL_URL) }
    }

    @Test
    fun `returns empty outside Indonesia without network`() = runTest {
        val alerts = adapter.getAlerts(39.7, -104.9).getOrThrow()

        assertTrue(alerts.isEmpty())
        coVerify(exactly = 0) { api.getNowcastFeed() }
    }

    private fun rssFeed(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Early Warning</title>
              <link>$DETAIL_URL</link>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private fun capAlert(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <identifier>CSL20260701002</identifier>
          <sent>2026-07-01T08:30:00+07:00</sent>
          <info>
            <language>en</language>
            <event>Thunderstorm</event>
            <urgency>Immediate</urgency>
            <severity>Moderate</severity>
            <certainty>Observed</certainty>
            <senderName>Indonesia Agency for Meteorology, Climatology, and Geophysics</senderName>
            <headline>Thunderstorm early warning</headline>
            <description>Potential moderate to heavy rain with lightning and strong winds.</description>
            <effective>2026-07-01T08:30:00+07:00</effective>
            <expires>2026-07-01T11:30:00+07:00</expires>
            <area>
              <areaDesc>Jakarta Selatan</areaDesc>
              <polygon>-6.30,106.70 -6.30,106.90 -6.10,106.90 -6.10,106.70 -6.30,106.70</polygon>
            </area>
          </info>
        </alert>
    """.trimIndent()

    private fun String.xmlBody(): ResponseBody =
        toResponseBody("application/xml".toMediaType())

    private companion object {
        private const val DETAIL_URL = "https://www.bmkg.go.id/alerts/nowcast/en/CSL20260701002_alert.xml"
    }
}
