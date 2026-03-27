package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.WeatherAlert

/**
 * Unified adapter interface for weather alert sources.
 * Each implementation wraps a specific national/regional alert API
 * and maps its response to the common [WeatherAlert] model.
 */
interface AlertSourceAdapter {
    /** Unique machine-readable identifier, e.g. "nws", "meteoalarm". */
    val sourceId: String

    /** Human-readable name shown in UI / settings. */
    val displayName: String

    /**
     * ISO 3166-1 alpha-2 country codes this adapter covers.
     * Use "GLOBAL" if the source provides worldwide alerts.
     */
    val supportedRegions: Set<String>

    /**
     * Fetch active weather alerts near the given coordinates.
     * Implementations must map source-specific data to [WeatherAlert].
     */
    suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>>
}
