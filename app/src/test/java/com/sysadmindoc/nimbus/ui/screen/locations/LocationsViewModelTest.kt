package com.sysadmindoc.nimbus.ui.screen.locations

import app.cash.turbine.test
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var locationRepository: LocationRepository
    private lateinit var viewModel: LocationsViewModel

    private val testResults = listOf(
        GeocodingResult(
            id = 1, name = "Denver", latitude = 39.7, longitude = -104.9,
            country = "United States", admin1 = "Colorado",
        ),
        GeocodingResult(
            id = 2, name = "Denver", latitude = 39.8, longitude = -105.0,
            country = "United States", admin1 = "North Carolina",
        ),
    )

    private val savedLocations = listOf(
        SavedLocationEntity(
            id = 1, name = "My Location", latitude = 39.7, longitude = -104.9,
            isCurrentLocation = true, sortOrder = -1,
        ),
        SavedLocationEntity(
            id = 2, name = "New York", latitude = 40.7, longitude = -74.0,
            region = "New York", country = "United States", sortOrder = 0,
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxed = true)
        every { locationRepository.savedLocations } returns flowOf(savedLocations)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LocationsViewModel {
        return LocationsViewModel(locationRepository)
    }

    @Test
    fun `savedLocations emits from repository flow`() = runTest {
        viewModel = createViewModel()

        viewModel.savedLocations.test {
            val items = expectMostRecentItem()
            assertEquals(2, items.size)
            assertEquals("My Location", items[0].name)
            assertEquals("New York", items[1].name)
        }
    }

    @Test
    fun `search with short query clears results`() = runTest {
        viewModel = createViewModel()

        viewModel.onSearchQueryChanged("D")

        viewModel.searchState.test {
            val state = expectMostRecentItem()
            assertEquals("D", state.query)
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
        }
    }

    @Test
    fun `search with valid query returns results`() = runTest {
        coEvery { locationRepository.search("Denver") } returns Result.success(testResults)

        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("Denver")

        advanceUntilIdle()

        viewModel.searchState.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.results.size)
            assertEquals("Denver", state.results[0].name)
            assertFalse(state.isSearching)
        }
    }

    @Test
    fun `search failure returns empty results`() = runTest {
        coEvery { locationRepository.search(any()) } returns Result.failure(Exception("Network error"))

        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("Denver")

        advanceUntilIdle()

        viewModel.searchState.test {
            val state = expectMostRecentItem()
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
        }
    }

    @Test
    fun `addLocation calls repository and clears search`() = runTest {
        coEvery { locationRepository.addLocation(any()) } returns 3L

        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("Denver")
        viewModel.addLocation(testResults[0])

        advanceUntilIdle()

        coVerify { locationRepository.addLocation(testResults[0]) }

        viewModel.searchState.test {
            val state = expectMostRecentItem()
            assertEquals("", state.query)
            assertTrue(state.results.isEmpty())
        }
    }

    @Test
    fun `removeLocation calls repository`() = runTest {
        coEvery { locationRepository.removeLocation(any()) } just Runs

        viewModel = createViewModel()
        viewModel.removeLocation(2L)

        advanceUntilIdle()

        coVerify { locationRepository.removeLocation(2L) }
    }

    @Test
    fun `clearSearch resets search state`() = runTest {
        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("Denver")
        viewModel.clearSearch()

        viewModel.searchState.test {
            val state = expectMostRecentItem()
            assertEquals("", state.query)
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
        }
    }
}
