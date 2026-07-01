package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CommunityReport
import com.sysadmindoc.nimbus.data.model.ReportCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityReportGeoTest {

    @Test
    fun `geohash uses lowercase firestore-safe alphabet`() {
        val geohash = CommunityReportGeo.geohash(39.7392, -104.9903)

        assertTrue(geohash.matches(Regex("^[0-9bcdefghjkmnpqrstuvwxyz]+$")))
    }

    @Test
    fun `query bounds cover nearby longitudes instead of scanning latitude only`() {
        val bounds = CommunityReportGeo.queryBounds(39.7392, -104.9903, radiusKm = 50.0)
        val precision = bounds.first().start.length
        val nearbyEast = CommunityReportGeo.geohash(39.7392, -104.45, precision)
        val distantSameLatitude = CommunityReportGeo.geohash(39.7392, -90.0, precision)

        assertTrue(bounds.any { nearbyEast >= it.start && nearbyEast < it.end })
        assertFalse(bounds.any { distantSameLatitude >= it.start && distantSameLatitude < it.end })
    }

    @Test
    fun `sortNearby keeps legacy blank-geohash reports from crashing distance filtering`() {
        val reports = listOf(
            report(id = "same-lat-far-lon", lon = -90.0, timestamp = 3),
            report(id = "legacy-nearby", lon = -104.99, timestamp = 2, geohash = ""),
            report(id = "new-nearby", lon = -104.98, timestamp = 4),
        )

        val nearby = CommunityReportGeo.sortNearby(
            reports = reports,
            latitude = 39.7392,
            longitude = -104.9903,
            radiusKm = 50.0,
            maxResults = 10,
        )

        assertEquals(listOf("new-nearby", "legacy-nearby"), nearby.map { it.id })
    }

    private fun report(
        id: String,
        lon: Double,
        timestamp: Long,
        geohash: String = CommunityReportGeo.geohash(39.7392, lon),
    ): CommunityReport =
        CommunityReport(
            id = id,
            latitude = 39.7392,
            longitude = lon,
            condition = ReportCondition.RAIN,
            timestamp = timestamp,
            geohash = geohash,
        )
}
