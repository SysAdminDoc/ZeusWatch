package com.sysadmindoc.nimbus.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
        fun makeKey(lat: Double, lon: Double): String =
            "%.2f,%.2f".format(lat, lon)

        const val MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes
    }

    val isExpired: Boolean
        get() = System.currentTimeMillis() - cachedAt > MAX_AGE_MS
}
