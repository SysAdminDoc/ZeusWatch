package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LocationTimeZoneResolver @Inject constructor(
    private val geocodingApi: GeocodingApi,
) {
    private val resolvedTimeZones = ConcurrentHashMap<String, String>()

    suspend fun resolveZone(
        latitude: Double,
        longitude: Double,
        explicitTimeZone: String? = null,
    ): ZoneId = resolveTimeZoneId(latitude, longitude, explicitTimeZone)?.toZoneIdOrNull()
        ?: ZoneId.systemDefault()

    suspend fun resolveTimeZoneId(
        latitude: Double,
        longitude: Double,
        explicitTimeZone: String? = null,
    ): String? {
        explicitTimeZone?.takeIf { it.toZoneIdOrNull() != null }?.let { return it }

        val key = coordinateKey(latitude, longitude)
        resolvedTimeZones[key]?.let { return it }

        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                geocodingApi.reverseGeocode(latitude, longitude, count = 1)
                    .results
                    ?.firstNotNullOfOrNull { it.timezone?.takeIf { zone -> zone.toZoneIdOrNull() != null } }
            }.getOrNull()
        }
        if (resolved != null) resolvedTimeZones[key] = resolved
        return resolved
    }

    private fun coordinateKey(latitude: Double, longitude: Double): String =
        "${(latitude * COORDINATE_CACHE_SCALE).roundToInt()}:" +
            (longitude * COORDINATE_CACHE_SCALE).roundToInt()

    private companion object {
        const val COORDINATE_CACHE_SCALE = 1000
    }
}

internal fun String?.toZoneIdOrNull(): ZoneId? {
    if (isNullOrBlank()) return null
    return runCatching { ZoneId.of(this) }.getOrNull()
}
