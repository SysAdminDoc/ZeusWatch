package com.sysadmindoc.nimbus.ui.screen.compare

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var weatherSourceManager: WeatherSourceManager
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
        weatherSourceManager = mockk()
        savedLocationsFlow = MutableStateFlow(listOf(currentLocation, seattle, chicago, miami))

        coEvery { weatherRepository.getWeather(currentLocation) } returns Result.success(weatherFor(currentLocation.name))
        coEvery { weatherRepository.getWeather(seattle) } returns Result.success(weatherFor(seattle.name))
        coEvery { weatherRepository.getWeather(chicago) } returns Result.success(weatherFor(chicago.name))
        coEvery { weatherRepository.getWeather(miami) } returns Result.success(weatherFor(miami.name))
        coEvery {
            weatherSourceManager.getWeatherFromProvider(any(), any(), any(), any(), any())
        } returns Result.failure(Exception("alternate source unavailable"))

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
        coEvery { weatherRepository.getWeather(chicago) } coAnswers { chicagoResult.await() }
        coEvery { weatherRepository.getWeather(miami) } coAnswers { miamiResult.await() }

        viewModel = CompareViewModel(weatherRepository, locationRepository, weatherSourceManager)
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

    @Test
    fun `failed comparison request records failed location without raw error copy`() = runTest {
        coEvery { weatherRepository.getWeather(seattle) } returns Result.failure(Exception("raw network failure"))

        viewModel = CompareViewModel(weatherRepository, locationRepository, weatherSourceManager)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasError)
        assertEquals(seattle.id, state.failedLocation2?.id)
        assertFalse(state.isLoading)
    }

    @Test
    fun `source overlay fetches alternate provider for primary location`() = runTest {
        coEvery { weatherRepository.getWeather(currentLocation) } returns Result.success(
            weatherFor(
                name = currentLocation.name,
                sourceProvider = WeatherSourceProvider.OPEN_METEO.displayName,
                hourlyBaseTemp = 20.0,
            )
        )
        coEvery {
            weatherSourceManager.getWeatherFromProvider(
                provider = WeatherSourceProvider.MET_NORWAY,
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                locationName = currentLocation.name,
                locationTimeZone = null,
            )
        } returns Result.success(
            weatherFor(
                name = currentLocation.name,
                sourceProvider = WeatherSourceProvider.MET_NORWAY.displayName,
                hourlyBaseTemp = 22.0,
            )
        )

        viewModel = CompareViewModel(weatherRepository, locationRepository, weatherSourceManager)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isOverlayLoading)
        assertFalse(state.overlayUnavailable)
        assertEquals(
            listOf(WeatherSourceProvider.OPEN_METEO.displayName, WeatherSourceProvider.MET_NORWAY.displayName),
            state.overlayForecasts.map { it.label },
        )
    }

    @Test
    fun `source overlay marks unavailable when only primary source has hourly data`() = runTest {
        coEvery { weatherRepository.getWeather(currentLocation) } returns Result.success(
            weatherFor(
                name = currentLocation.name,
                sourceProvider = WeatherSourceProvider.OPEN_METEO.displayName,
                hourlyBaseTemp = 20.0,
            )
        )

        viewModel = CompareViewModel(weatherRepository, locationRepository, weatherSourceManager)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isOverlayLoading)
        assertTrue(state.overlayUnavailable)
        assertEquals(1, state.overlayForecasts.size)
    }

    private fun weatherFor(
        name: String,
        sourceProvider: String? = null,
        hourlyBaseTemp: Double? = null,
    ): WeatherData {
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
            hourly = hourlyBaseTemp?.let(::hourlyFor) ?: emptyList(),
            daily = emptyList(),
            sourceProvider = sourceProvider,
        )
    }

    private fun hourlyFor(baseTemp: Double): List<HourlyConditions> {
        return (0 until 6).map { hour ->
            HourlyConditions(
                time = java.time.LocalDateTime.of(2026, 7, 5, 8, 0).plusHours(hour.toLong()),
                temperature = baseTemp + hour,
                feelsLike = null,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = true,
                precipitationProbability = hour * 10,
                precipitation = null,
                windSpeed = null,
                windDirection = null,
                humidity = null,
                uvIndex = null,
                cloudCover = null,
                visibility = null,
            )
        }
    }
}
