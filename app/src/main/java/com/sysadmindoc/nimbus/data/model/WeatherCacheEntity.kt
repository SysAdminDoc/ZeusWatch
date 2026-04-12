package com.sysadmindoc.nimbus.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Room entity storing serialized weather data for offline access.
 * Keyed by lat/lon rounded to 2 decimal places for cache hits.
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey
    val locationKey: String, // "lat,lon" rounded to 2 decimals
    val responseJson: String,
    val locationName: String,
    val locationRegion: String = "",
    val locationCountry: String = "",
    val latitude: Double,
    val longitude: Double,
    val cachedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        // Must use Locale.US — default-locale format() produces comma decimals
        // in de_DE / fr_FR / etc. (e.g. "39,74,-104,99"), which both corrupts
        // the stored key format and loses cache hits if the device locale changes.
        fun makeKey(lat: Double, lon: Double): String =
            String.format(Locale.US, "%.2f,%.2f", lat, lon)

        const val DEFAULT_MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes
        const val MAX_AGE_MS = DEFAULT_MAX_AGE_MS // For backward compatibility
    }

    val isExpired: Boolean
        get() = isExpired(DEFAULT_MAX_AGE_MS)

    /** Check expiration against a configurable TTL. */
    fun isExpired(maxAgeMs: Long): Boolean =
        System.currentTimeMillis() - cachedAt > maxAgeMs

    /** Age of cached data in minutes. */
    val ageMinutes: Long
        get() = (System.currentTimeMillis() - cachedAt) / 60_000
}
