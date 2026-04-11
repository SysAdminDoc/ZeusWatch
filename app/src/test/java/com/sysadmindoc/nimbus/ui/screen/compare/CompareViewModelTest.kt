package com.sysadmindoc.nimbus.ui.screen.compare

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var savedLocationsFlow: MutableStateFlow<List<SavedLocationEntity>>
    private lateinit var viewModel: CompareViewModel

    private val currentLocation = SavedLocationEntity(
        id = 1,
        name = "My Location",
        latitude = 39.7392,
        longitude = -104.9903,
        isCurrentLocation = true,
        sortOrder = -1,
    )
    private val seattle = SavedLocationEntity(
        id = 2,
        name = "Seattle",
        latitude = 47.6062,
        longitude = -122.3321,
        region = "Washington",
        country = "US",
        sortOrder = 0,
    )
    private val chicago = SavedLocationEntity(
        id = 3,
        name = "Chicago",
        latitude = 41.8781,
        longitude = -87.6298,
        region = "Illinois",
        country = "US",
        sortOrder = 1,
    )
    private val miami = SavedLocationEntity(
        id = 4,
        name = "Miami",
        latitude = 25.7617,
        longitude = -80.1918,
        region = "Florida",
        country = "US",
        sortOrder = 2,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        weatherRepository = mockk()
        locationRepository = mockk()
        savedLocationsFlow = MutableStateFlow(listOf(currentLocation, seattle, chicago, miami))

        coEvery {
            weatherRepository.getWeather(currentLocation.latitude, currentLocation.longitude, currentLocation.name)
        } returns Result.success(weatherFor(currentLocation.name))
        coEvery {
            weatherRepository.getWeather(seattle.latitude, seattle.longitude, seattle.name)
        } returns Result.success(weatherFor(seattle.name))
        coEvery {
            weatherRepository.getWeather(chicago.latitude, chicago.longitude, chicago.name)
        } returns Result.success(weatherFor(chicago.name))
        coEvery {
            weatherRepository.getWeather(miami.latitude, miami.longitude, miami.name)
        } returns Result.success(weatherFor(miami.name))

        every { locationRepository.savedLocations } returns savedLocationsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `latest selection wins when earlier request finishes last`() = runTest {
        val chicagoResult = CompletableDeferred<Result<WeatherData>>()
        val miamiResult = CompletableDeferred<Result<WeatherData>>()
        coEvery {
            weatherRepository.getWeather(chicago.latitude, chicago.longitude, chicago.name)
        } coAnswers { chicagoResult.await() }
        coEvery {
            weatherRepository.getWeather(miami.latitude, miami.longitude, miami.name)
        } coAnswers { miamiResult.await() }

        viewModel = CompareViewModel(weatherRepository, locationRepository)
        advanceUntilIdle()

        viewModel.selectLocation1(chicago)
        viewModel.selectLocation1(miami)

        miamiResult.complete(Result.success(weatherFor(miami.name)))
        chicagoResult.complete(Result.success(weatherFor(chicago.name)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(miami.id, state.location1?.id)
        assertEquals(miami.name, state.weather1?.location?.name)
        assertFalse(state.isLoading)
    }

    private fun weatherFor(name: String): WeatherData {
        return WeatherData(
            location = LocationInfo(name = name, latitude = 0.0, longitude = 0.0),
            current = CurrentConditions(
                temperature = 20.0,
                feelsLike = 19.0,
                humidity = 50,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = true,
                windSpeed = 10.0,
                windDirection = 180,
                windGusts = null,
                pressure = 1012.0,
                uvIndex = 4.0,
                visibility = 10_000.0,
                dewPoint = null,
                cloudCover = 10,
                precipitation = 0.0,
                dailyHigh = 24.0,
                dailyLow = 16.0,
                sunrise = null,
                sunset = null,
            ),
            hourly = emptyList(),
            daily = emptyList(),
        )
    }
}
