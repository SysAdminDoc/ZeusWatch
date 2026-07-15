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

    @Query(
        """
        SELECT * FROM weather_data_cache
        WHERE schemaVersion = :schemaVersion
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestWeatherData(schemaVersion: Int): WeatherDataCacheEntity?

    /**
     * Latest cache row belonging to the GPS "current location" saved row, so
     * ambient surfaces don't flip to whichever secondary city refreshed last.
     * Matches by savedLocationId for rows cached via a SavedLocationEntity
     * fetch, and by coordinate locationKey for the main UI's GPS path, which
     * caches with savedLocationId = NULL ([currentLocationKey] is
     * WeatherCacheEntity.makeKey of the current location's coordinates).
     * Returns null when no current-location saved row — or no cache row for
     * it — exists; callers fall back to [getLatestWeatherData].
     */
    @Query(
        """
        SELECT w.* FROM weather_data_cache w
        INNER JOIN saved_locations l ON l.isCurrentLocation = 1
        WHERE w.schemaVersion = :schemaVersion
        AND (w.savedLocationId = l.id OR w.locationKey = :currentLocationKey)
        ORDER BY w.cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCurrentLocationWeatherData(
        currentLocationKey: String,
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
