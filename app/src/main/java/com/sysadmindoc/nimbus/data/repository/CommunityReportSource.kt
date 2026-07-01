package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CommunityReport

/**
 * Abstraction for community weather report operations.
 * The standard flavor uses Firebase Firestore; the freenet flavor provides a no-op stub.
 */
interface CommunityReportSource {

    /** Anonymous account id when a community-report backend is available. */
    val deviceId: String

    /** Submit a community weather report. Returns the document ID on success. */
    suspend fun submitReport(report: CommunityReport): Result<String>

    /**
     * Query community reports near a location within a radius.
     */
    suspend fun getReportsNearby(
        lat: Double,
        lon: Double,
        radiusKm: Double = 50.0,
    ): Result<List<CommunityReport>>

    /** Delete a report by ID (only allowed for reports from this device). */
    suspend fun deleteReport(id: String): Result<Unit>
}
