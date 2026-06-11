package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.model.matchesSavedLocation
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val geocodingApi: GeocodingApi,
    private val dao: SavedLocationDao,
) {
    val savedLocations: Flow<List<SavedLocationEntity>> = dao.getAllFlow()

    /** Serializes [addLocation]'s check-then-insert so a double-tap can't insert duplicates. */
    private val addLocationMutex = Mutex()

    suspend fun search(query: String): Result<List<GeocodingResult>> =
        withContext(Dispatchers.IO) {
            try {
                val response = geocodingApi.searchLocation(query)
                Result.success(response.results ?: emptyList())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }

    suspend fun addLocation(result: GeocodingResult): Long = addLocationMutex.withLock {
        dao.getAll()
            .firstOrNull { location -> matchesSavedLocation(result, location) }
            ?.let { existing -> return existing.id }

        val nextOrder = (dao.maxSortOrder() ?: -1) + 1
        return dao.insert(
            SavedLocationEntity(
                name = result.name,
                region = result.admin1 ?: "",
                country = result.country ?: "",
                latitude = result.latitude,
                longitude = result.longitude,
                sortOrder = nextOrder,
            )
        )
    }

    suspend fun addCurrentLocation(lat: Double, lon: Double, name: String): Long {
        // Atomic delete-old + insert-new so a cancellation can't strand the user
        // with no current-location row (which background workers rely on).
        return dao.replaceCurrentLocation(
            SavedLocationEntity(
                name = name,
                latitude = lat,
                longitude = lon,
                sortOrder = -1, // Always first
                isCurrentLocation = true,
            )
        )
    }

    suspend fun removeLocation(id: Long) {
        dao.deleteById(id)
    }

    suspend fun getAll(): List<SavedLocationEntity> = dao.getAll()

    suspend fun reorderLocations(orderedIds: List<Long>) {
        val currentLocationId = dao.getCurrentLocation()?.id
        val normalizedIds = orderedIds.filterNot { it == currentLocationId }
        currentLocationId?.let { dao.updateSortOrder(it, -1) }
        dao.reorderAll(normalizedIds)
    }

    suspend fun ensureCurrentLocation(lat: Double, lon: Double, name: String) {
        val existing = dao.getCurrentLocation()
        if (existing == null) {
            addCurrentLocation(lat, lon, name)
        } else {
            dao.update(
                existing.copy(
                    latitude = lat,
                    longitude = lon,
                    name = name,
                    sortOrder = -1,
                    isCurrentLocation = true,
                )
            )
        }
    }
}
