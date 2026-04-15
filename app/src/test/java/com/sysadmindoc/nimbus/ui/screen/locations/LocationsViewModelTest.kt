package com.sysadmindoc.nimbus.ui.screen.locations

import app.cash.turbine.test
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    private lateinit var locationRepository: LocationRepository
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var savedLocationsFlow: MutableStateFlow<List<SavedLocationEntity>>
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
        SavedLocationEntity(
            id = 3, name = "Chicago", latitude = 41.8781, longitude = -87.6298,
            region = "Illinois", country = "United States", sortOrder = 1,
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxed = true)
        weatherRepository = mockk(relaxed = true)
        savedLocationsFlow = MutableStateFlow(savedLocations)
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null
        every { locationRepository.savedLocations } returns savedLocationsFlow
        coEvery { locationRepository.search(any()) } returns Result.success(testResults)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LocationsViewModel {
        return LocationsViewModel(locationRepository, weatherRepository)
    }

    @Test
    fun `savedLocations emits from repository flow`() = runTest {
        viewModel = createViewModel()

        viewModel.savedLocations.test {
            val items = expectMostRecentItem()
            assertEquals(3, items.size)
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
    fun `successful search clears previous error state`() = runTest {
        coEvery { locationRepository.search("De") } returns Result.failure(Exception("Network error"))
        coEvery { locationRepository.search("Denver") } returns Result.success(testResults)

        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("De")
        advanceUntilIdle()
        assertNotNull(viewModel.searchState.value.error)

        viewModel.onSearchQueryChanged("Denver")
        advanceUntilIdle()

        val state = viewModel.searchState.value
        assertNull(state.error)
        assertEquals("Denver", state.query)
        assertEquals(2, state.results.size)
    }

    @Test
    fun `addLocation calls repository and clears search`() = runTest {
        coEvery { locationRepository.addLocation(any()) } returns 3L

        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("Denver")
        advanceUntilIdle() // Let search complete

        viewModel.addLocation(testResults[0])
        advanceUntilIdle() // Let addLocation complete (clears search)

        coVerify { locationRepository.addLocation(testResults[0]) }

        viewModel.searchState.test {
            val state = expectMostRecentItem()
            assertEquals("", state.query)
            assertTrue(state.results.isEmpty())
        }
    }

    @Test
    fun `addLocation invokes callback with selected location id`() = runTest {
        coEvery { locationRepository.addLocation(any()) } returns 7L
        var selectedLocationId: Long? = null

        viewModel = createViewModel()
        viewModel.addLocation(testResults[0]) { locationId ->
            selectedLocationId = locationId
        }

        advanceUntilIdle()

        assertEquals(7L, selectedLocationId)
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
    fun `moveLocation keeps current location fixed at the top`() = runTest {
        coEvery { locationRepository.reorderLocations(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.moveLocation(2, 0)
        advanceUntilIdle()

        coVerify(exactly = 1) { locationRepository.reorderLocations(listOf(1L, 3L, 2L)) }
    }

    @Test
    fun `moveLocation ignores attempts to drag current location`() = runTest {
        coEvery { locationRepository.reorderLocations(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.moveLocation(0, 2)
        advanceUntilIdle()

        coVerify(exactly = 0) { locationRepository.reorderLocations(any()) }
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
