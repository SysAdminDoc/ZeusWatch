package com.sysadmindoc.nimbus.data.api

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_cache WHERE locationKey = :key LIMIT 1")
    suspend fun getCached(key: String): WeatherCacheEntity?

    @Query(
        """
        SELECT * FROM weather_data_cache
        WHERE locationKey = :locationKey
        AND ((:savedLocationId IS NULL AND savedLocationId IS NULL) OR savedLocationId = :savedLocationId)
        AND sourceProvider IN (:sourceProviders)
        AND schemaVersion = :schemaVersion
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun getCachedWeatherData(
        locationKey: String,
        savedLocationId: Long?,
        sourceProviders: List<String>,
        schemaVersion: Int,
    ): WeatherDataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeatherData(entity: WeatherDataCacheEntity)

    @Query("DELETE FROM weather_cache WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM weather_data_cache WHERE cachedAt < :before")
    suspend fun deleteWeatherDataOlderThan(before: Long)

    @Query("DELETE FROM weather_cache")
    suspend fun clearAll()

    @Query("DELETE FROM weather_data_cache")
    suspend fun clearAllWeatherData()
}
