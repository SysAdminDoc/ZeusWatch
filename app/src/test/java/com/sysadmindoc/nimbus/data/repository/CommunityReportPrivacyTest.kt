package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CommunityReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityReportPrivacyTest {

    @Test
    fun `submission coordinates are rounded to two decimal degrees`() {
        val location = CommunityReportPrivacy.coarsenLocation(39.7392, -104.995)

        assertEquals(39.74, location.latitude, 0.0)
        assertEquals(-105.0, location.longitude, 0.0)
        assertEquals(
            CommunityReportGeo.geohash(39.74, -105.0),
            CommunityReportGeo.geohash(location.latitude, location.longitude),
        )
    }

    @Test
    fun `submission coordinates stay within valid world bounds`() {
        assertEquals(
            CoarsenedCommunityReportLocation(90.0, -180.0),
            CommunityReportPrivacy.coarsenLocation(100.0, -200.0),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite submission coordinates are rejected`() {
        CommunityReportPrivacy.coarsenLocation(Double.NaN, -104.99)
    }

    @Test
    fun `expiry is exactly two hours after report creation`() {
        val createdAt = 1_000L

        assertEquals(
            createdAt + CommunityReportPrivacy.RETENTION_MS,
            CommunityReportPrivacy.expiresAt(createdAt),
        )
    }

    @Test
    fun `expired TTL reports and legacy reports disappear from app results`() {
        val now = 10_000_000L
        val visible = CommunityReport(timestamp = now - 1_000L, expiresAt = now + 1_000L)
        val expired = CommunityReport(timestamp = now - 2_000L, expiresAt = now)
        val legacyVisible = CommunityReport(timestamp = now - CommunityReportPrivacy.RETENTION_MS + 1L)
        val legacyExpired = CommunityReport(timestamp = now - CommunityReportPrivacy.RETENTION_MS)

        assertTrue(CommunityReportPrivacy.isVisible(visible, now))
        assertFalse(CommunityReportPrivacy.isVisible(expired, now))
        assertEquals(
            listOf(visible, legacyVisible),
            CommunityReportPrivacy.visibleReports(
                listOf(visible, expired, legacyVisible, legacyExpired),
                now,
            ),
        )
    }
}
