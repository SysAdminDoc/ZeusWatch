package com.sysadmindoc.nimbus.data.api

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [AlertSourceAdapter] for the WMO Severe Weather Information Centre.
 * Provides global CAP-standard weather alerts from 130+ national services.
 * Used as a fallback for countries without a dedicated adapter.
 *
 * NOTE (2026-06-11): the v2 `json/warnings.json` endpoint is dead (404) — the
 * live feed is `json/wmo_all.json` with a different schema and no per-item geo
 * coordinates, so this adapter currently always returns empty. A rewrite with
 * a proximity design is roadmapped.
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // 404/400 means "no alerts here" — same contract as NwsAlertAdapter.
            // This also keeps the dead v2 endpoint's 404 from triggering the
            // caller's failure/fallback path on every fetch.
            if (e.code() == 404 || e.code() == 400) {
                Result.success(emptyList())
            } else {
                Log.w(TAG, "WMO alert fetch failed: HTTP ${e.code()}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WMO alert fetch failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun isNearby(warning: WmoWarning, lat: Double, lon: Double): Boolean {
        if (warning.minLat != null && warning.maxLat != null &&
            warning.minLon != null && warning.maxLon != null
        ) {
            val latPad = BBOX_PAD_DEG
            if (lat < warning.minLat - latPad || lat > warning.maxLat + latPad) return false

            // Longitude degrees shrink with latitude — widen the lon pad so the
            // padding stays roughly constant in ground distance (clamped so it
            // doesn't blow up near the poles).
            val lonPad = latPad / cos(Math.toRadians(lat)).coerceAtLeast(0.1)
            val minLon = warning.minLon - lonPad
            val maxLon = warning.maxLon + lonPad
            // minLon > maxLon means the bbox wraps the antimeridian (±180°).
            return if (warning.minLon > warning.maxLon) {
                lon >= minLon || lon <= maxLon
            } else {
                lon in minLon..maxLon
            }
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
