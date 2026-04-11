package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocationRepositoryTest {

    private lateinit var geocodingApi: GeocodingApi
    private lateinit var dao: SavedLocationDao
    private lateinit var repository: LocationRepository

    @Before
    fun setup() {
        geocodingApi = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        repository = LocationRepository(geocodingApi, dao)
    }

    @Test
    fun `addLocation returns existing id for duplicate saved place`() = runTest {
        val existing = SavedLocationEntity(
            id = 7L,
            name = "Denver",
            region = "Colorado",
            country = "United States",
            latitude = 39.7392,
            longitude = -104.9903,
            sortOrder = 0,
        )
        val result = GeocodingResult(
            id = 100L,
            name = "Denver",
            latitude = 39.7392,
            longitude = -104.9903,
            country = "United States",
            admin1 = "Colorado",
        )

        coEvery { dao.getAll() } returns listOf(existing)

        val insertedId = repository.addLocation(result)

        assertEquals(7L, insertedId)
        coVerify(exactly = 1) { dao.getAll() }
        coVerify(exactly = 0) { dao.maxSortOrder() }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `addLocation inserts when search result is not already saved`() = runTest {
        val result = GeocodingResult(
            id = 101L,
            name = "Chicago",
            latitude = 41.8781,
            longitude = -87.6298,
            country = "United States",
            admin1 = "Illinois",
        )

        coEvery { dao.getAll() } returns emptyList()
        coEvery { dao.maxSortOrder() } returns 2
        coEvery { dao.insert(any()) } returns 10L

        val insertedId = repository.addLocation(result)

        assertEquals(10L, insertedId)
        coVerify(exactly = 1) { dao.insert(match { entity ->
            entity.name == "Chicago" &&
                entity.region == "Illinois" &&
                entity.country == "United States" &&
                entity.sortOrder == 3
        }) }
    }

    @Test
    fun `reorderLocations preserves current location at top`() = runTest {
        val currentLocation = SavedLocationEntity(
            id = 1L,
            name = "My Location",
            latitude = 39.7392,
            longitude = -104.9903,
            isCurrentLocation = true,
            sortOrder = 2,
        )
        coEvery { dao.getCurrentLocation() } returns currentLocation
        coEvery { dao.updateSortOrder(any(), any()) } returns Unit
        coEvery { dao.reorderAll(any()) } returns Unit

        repository.reorderLocations(listOf(2L, 1L, 3L))

        coVerify(exactly = 1) { dao.updateSortOrder(1L, -1) }
        coVerify(exactly = 1) { dao.reorderAll(listOf(2L, 3L)) }
    }

    @Test
    fun `ensureCurrentLocation restores anchored current location metadata`() = runTest {
        val existing = SavedLocationEntity(
            id = 1L,
            name = "Old Name",
            latitude = 35.0,
            longitude = -100.0,
            isCurrentLocation = true,
            sortOrder = 4,
        )
        coEvery { dao.getCurrentLocation() } returns existing
        coEvery { dao.update(any()) } returns Unit

        repository.ensureCurrentLocation(39.7392, -104.9903, "Denver")

        coVerify(exactly = 1) {
            dao.update(match { updated ->
                updated.id == 1L &&
                    updated.name == "Denver" &&
                    updated.latitude == 39.7392 &&
                    updated.longitude == -104.9903 &&
                    updated.sortOrder == -1 &&
                    updated.isCurrentLocation
            })
        }
    }
}
