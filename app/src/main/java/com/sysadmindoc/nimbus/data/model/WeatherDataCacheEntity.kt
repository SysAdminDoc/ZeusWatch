package com.sysadmindoc.nimbus.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val WEATHER_DATA_CACHE_SCHEMA_VERSION = 1

@Entity(
    tableName = "weather_data_cache",
    indices = [
        Index("locationKey"),
        Index("sourceProvider"),
        Index("savedLocationId"),
    ],
)
data class WeatherDataCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val locationKey: String,
    val sourceProvider: String,
    val savedLocationId: Long? = null,
    val schemaVersion: Int = WEATHER_DATA_CACHE_SCHEMA_VERSION,
    val payloadJson: String,
    val cachedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = WEATHER_DATA_CACHE_SCHEMA_VERSION

        fun makeKey(
            latitude: Double,
            longitude: Double,
            sourceProvider: String,
            savedLocationId: Long?,
        ): String {
            val locationScope = savedLocationId?.let { "saved:$it" }
                ?: "coord:${WeatherCacheEntity.makeKey(latitude, longitude)}"
            return "$sourceProvider|$locationScope"
        }
    }

    fun isExpired(maxAgeMs: Long): Boolean =
        System.currentTimeMillis() - cachedAt > maxAgeMs
}
