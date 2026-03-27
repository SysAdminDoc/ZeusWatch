package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CommunityReport
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreenetCommunityReportRepository @Inject constructor() : CommunityReportSource {

    override val deviceId: String = "freenet-no-firebase"

    override suspend fun submitReport(report: CommunityReport): Result<String> =
        Result.failure(UnsupportedOperationException("Community reports require Google Play Services"))

    override suspend fun getReportsNearby(lat: Double, lon: Double, radiusKm: Double): Result<List<CommunityReport>> =
        Result.success(emptyList())

    override suspend fun deleteReport(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Community reports require Google Play Services"))
}
