package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CommunityReport
import java.math.BigDecimal
import java.math.RoundingMode

internal data class CoarsenedCommunityReportLocation(
    val latitude: Double,
    val longitude: Double,
)

internal object CommunityReportPrivacy {
    const val RETENTION_MS = 2 * 60 * 60 * 1000L

    fun coarsenLocation(latitude: Double, longitude: Double): CoarsenedCommunityReportLocation {
        require(latitude.isFinite() && longitude.isFinite()) {
            "Community report coordinates must be finite."
        }
        return CoarsenedCommunityReportLocation(
            latitude = roundToArea(latitude.coerceIn(-90.0, 90.0)),
            longitude = roundToArea(longitude.coerceIn(-180.0, 180.0)),
        )
    }

    fun expiresAt(createdAt: Long): Long =
        createdAt.coerceAtMost(Long.MAX_VALUE - RETENTION_MS) + RETENTION_MS

    fun isVisible(report: CommunityReport, now: Long): Boolean {
        val effectiveExpiry = report.expiresAt.takeIf { it > 0L }
            ?: expiresAt(report.timestamp)
        return effectiveExpiry > now
    }

    fun visibleReports(reports: Iterable<CommunityReport>, now: Long): List<CommunityReport> =
        reports.filter { isVisible(it, now) }

    private fun roundToArea(value: Double): Double =
        BigDecimal.valueOf(value)
            .setScale(COORDINATE_DECIMALS, RoundingMode.HALF_UP)
            .toDouble()

    private const val COORDINATE_DECIMALS = 2
}
