package com.sysadmindoc.nimbus.data.api

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [AlertSourceAdapter] for the WMO Severe Weather Information Centre.
 * Provides global CAP-standard weather alerts from 130+ national services.
 * Used as a fallback for countries without a dedicated adapter.
 */
@Singleton
class WmoAlertAdapter @Inject constructor(
    private val api: WmoAlertApi,
) : AlertSourceAdapter {

    override val sourceId = "wmo"
    override val displayName = "WMO Severe Weather"
    override val supportedRegions = setOf("GLOBAL")

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        return try {
            val response = api.getWarnings()
            val nearby = response.warnings
                .filter { it.status?.equals("Actual", ignoreCase = true) != false }
                .filter { isNearby(it, lat, lon) }
                .mapNotNull { warning -> mapToAlert(warning) }
            Result.success(nearby)
        } catch (e: Exception) {
            Log.w(TAG, "WMO alert fetch failed: ${e.message}")
            Result.success(emptyList())
        }
    }

    private fun isNearby(warning: WmoWarning, lat: Double, lon: Double): Boolean {
        if (warning.minLat != null && warning.maxLat != null &&
            warning.minLon != null && warning.maxLon != null
        ) {
            val latPad = BBOX_PAD_DEG
            return lat >= warning.minLat - latPad && lat <= warning.maxLat + latPad &&
                lon >= warning.minLon - latPad && lon <= warning.maxLon + latPad
        }

        val wLat = warning.lat ?: return false
        val wLon = warning.lon ?: return false
        return haversineKm(lat, lon, wLat, wLon) <= RADIUS_KM
    }

    private fun mapToAlert(w: WmoWarning): WeatherAlert? {
        val event = w.event ?: return null
        return WeatherAlert(
            id = w.capId ?: w.id ?: return null,
            event = event,
            headline = w.headline ?: event,
            description = w.description ?: "",
            instruction = w.instruction,
            severity = AlertSeverity.from(w.severity),
            urgency = AlertUrgency.from(w.urgency),
            certainty = w.certainty ?: "Unknown",
            senderName = w.senderName ?: w.sender ?: "WMO SWIC",
            areaDescription = w.areaDesc ?: "",
            effective = w.effective ?: w.onset,
            expires = w.expires,
            response = null,
        )
    }

    companion object {
        private const val TAG = "WmoAlertAdapter"
        private const val RADIUS_KM = 150.0
        private const val BBOX_PAD_DEG = 0.5

        private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
