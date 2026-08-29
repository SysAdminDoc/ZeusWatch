package com.sysadmindoc.nimbus.wear

import com.sysadmindoc.nimbus.wear.data.DataSource
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.data.WearWeatherResult
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearWeatherViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no known location surfaces the no-location state, not an error`() = runTest(dispatcher) {
        val state = viewModelFor(WearWeatherResult.NoLocation).uiState.value

        // The error card offers "Retry", which is useless without a location.
        // The no-location card offers to request permission instead.
        assertTrue(state.noLocation)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failed fetch surfaces the error state, not the no-location state`() = runTest(dispatcher) {
        val state = viewModelFor(
            WearWeatherResult.Failed(IllegalStateException("offline")),
        ).uiState.value

        assertFalse(state.noLocation)
        assertEquals("weather_load_failed", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a successful load clears both the error and no-location states`() = runTest(dispatcher) {
        val data = WearWeatherData(
            temperature = 21,
            condition = "Clear Sky",
            high = 25,
            low = 15,
            locationName = "Seattle",
            dataSource = DataSource.DIRECT_API,
        )

        val state = viewModelFor(WearWeatherResult.Available(data)).uiState.value

        assertFalse(state.noLocation)
        assertNull(state.error)
        assertEquals(21, state.temperature)
        assertEquals("Seattle", state.locationName)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.viewModelFor(
        result: WearWeatherResult,
    ): WearWeatherViewModel {
        val repository = mockk<WearWeatherRepository>()
        coEvery { repository.loadWeather(any()) } returns result
        val locationProvider = mockk<WearLocationProvider>()
        coEvery { locationProvider.getLocation() } returns null
        val viewModel = WearWeatherViewModel(
            repository = repository,
            locationProvider = locationProvider,
            syncedWeatherStore = mockk<SyncedWeatherStore>(relaxed = true),
        )
        advanceUntilIdle()
        return viewModel
    }
}
