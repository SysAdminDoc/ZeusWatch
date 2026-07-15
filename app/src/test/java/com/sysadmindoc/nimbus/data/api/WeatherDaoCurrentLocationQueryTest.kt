package com.sysadmindoc.nimbus.data.api

import androidx.room.Room
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * JVM Room test for [WeatherDao.getLatestCurrentLocationWeatherData]: the
 * smartspacer surface must show the GPS current location, not whichever
 * secondary city happened to refresh last. Covers both cache-row shapes: rows
 * written via a SavedLocationEntity fetch (savedLocationId set) and rows from
 * the main UI's GPS path (savedLocationId = NULL, coordinate locationKey only).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeatherDaoCurrentLocationQueryTest {

    private lateinit var database: NimbusDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NimbusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `prefers older current-location row over newer secondary-city row by savedLocationId`() = runTest {
        val currentId = database.savedLocationDao().insert(
            savedLocation(name = "Denver", latitude = 39.7, longitude = -104.9, isCurrentLocation = true),
        )
        val secondaryId = database.savedLocationDao().insert(
            savedLocation(name = "Tokyo", latitude = 35.7, longitude = 139.7),
        )
        database.weatherDao().upsertWeatherData(
            cacheRow(latitude = 39.7, longitude = -104.9, savedLocationId = currentId, cachedAt = 1_000L),
        )
        database.weatherDao().upsertWeatherData(
            cacheRow(latitude = 35.7, longitude = 139.7, savedLocationId = secondaryId, cachedAt = 2_000L),
        )

        val row = database.weatherDao().getLatestCurrentLocationWeatherData(
            currentLocationKey = WeatherCacheEntity.makeKey(39.7, -104.9),
            schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
        )

        assertEquals(currentId, row?.savedLocationId)
        assertEquals(1_000L, row?.cachedAt)
    }

    @Test
    fun `matches GPS rows cached with null savedLocationId via locationKey`() = runTest {
        database.savedLocationDao().insert(
            savedLocation(name = "Denver", latitude = 39.7, longitude = -104.9, isCurrentLocation = true),
        )
        val secondaryId = database.savedLocationDao().insert(
            savedLocation(name = "Tokyo", latitude = 35.7, longitude = 139.7),
        )
        // Main UI GPS path: savedLocationId is NULL, only the coordinate key links the row.
        database.weatherDao().upsertWeatherData(
            cacheRow(latitude = 39.7, longitude = -104.9, savedLocationId = null, cachedAt = 1_000L),
        )
        database.weatherDao().upsertWeatherData(
            cacheRow(latitude = 35.7, longitude = 139.7, savedLocationId = secondaryId, cachedAt = 2_000L),
        )

        val row = database.weatherDao().getLatestCurrentLocationWeatherData(
            currentLocationKey = WeatherCacheEntity.makeKey(39.7, -104.9),
            schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
        )

        assertEquals(WeatherCacheEntity.makeKey(39.7, -104.9), row?.locationKey)
        assertNull(row?.savedLocationId)
    }

    @Test
    fun `returns null when no cache row belongs to the current location`() = runTest {
        database.savedLocationDao().insert(
            savedLocation(name = "Denver", latitude = 39.7, longitude = -104.9, isCurrentLocation = true),
        )
        val secondaryId = database.savedLocationDao().insert(
            savedLocation(name = "Tokyo", latitude = 35.7, longitude = 139.7),
        )
        database.weatherDao().upsertWeatherData(
            cacheRow(latitude = 35.7, longitude = 139.7, savedLocationId = secondaryId, cachedAt = 2_000L),
        )

        val row = database.weatherDao().getLatestCurrentLocationWeatherData(
            currentLocationKey = WeatherCacheEntity.makeKey(39.7, -104.9),
            schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
        )

        assertNull(row)
    }

    @Test
    fun `returns null when no current-location saved row exists`() = runTest {
        val secondaryId = database.savedLocationDao().insert(
            savedLocation(name = "Tokyo", latitude = 35.7, longitude = 139.7),
        )
        database.weatherDao().upsertWeatherData(
            cacheRow(latitude = 35.7, longitude = 139.7, savedLocationId = secondaryId, cachedAt = 2_000L),
        )

        val row = database.weatherDao().getLatestCurrentLocationWeatherData(
            currentLocationKey = WeatherCacheEntity.makeKey(35.7, 139.7),
            schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
        )

        assertNull(row)
    }

    private fun savedLocation(
        name: String,
        latitude: Double,
        longitude: Double,
        isCurrentLocation: Boolean = false,
    ): SavedLocationEntity = SavedLocationEntity(
        name = name,
        latitude = latitude,
        longitude = longitude,
        isCurrentLocation = isCurrentLocation,
    )

    private fun cacheRow(
        latitude: Double,
        longitude: Double,
        savedLocationId: Long?,
        cachedAt: Long,
    ): WeatherDataCacheEntity = WeatherDataCacheEntity(
        cacheKey = WeatherDataCacheEntity.makeKey(latitude, longitude, "OPEN_METEO", savedLocationId),
        locationKey = WeatherCacheEntity.makeKey(latitude, longitude),
        sourceProvider = "OPEN_METEO",
        savedLocationId = savedLocationId,
        payloadJson = "{}",
        cachedAt = cachedAt,
    )
}
