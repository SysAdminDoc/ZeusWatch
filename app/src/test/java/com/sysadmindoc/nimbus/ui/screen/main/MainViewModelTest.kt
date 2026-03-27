package com.sysadmindoc.nimbus.ui.screen.main

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import com.sysadmindoc.nimbus.data.location.LocationProvider
import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var airQualityRepository: AirQualityRepository
    private lateinit var weatherSourceManager: WeatherSourceManager
    private lateinit var radarRepository: RadarRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var locationProvider: LocationProvider
    private lateinit var prefs: UserPreferences

    private lateinit var viewModel: MainViewModel

    private val testWeatherData = WeatherData(
        location = LocationInfo("Denver", "Colorado", "US", 39.7, -104.9),
        current = CurrentConditions(
            temperature = 22.2, feelsLike = 21.1, humidity = 45,
            weatherCode = WeatherCode.CLEAR_SKY, isDay = true,
            windSpeed = 12.8, windDirection = 180, windGusts = 24.1,
            pressure = 1013.25, uvIndex = 5.0, visibility = 16000.0,
            dewPoint = 10.0, cloudCover = 20, precipitation = 0.0,
            dailyHigh = 26.7, dailyLow = 12.8,
            sunrise = "2025-01-15T07:00:00", sunset = "2025-01-15T17:30:00",
        ),
        hourly = emptyList(), daily = emptyList(),
    )

    private val testAirQuality = AirQualityData(
        usAqi = 42, europeanAqi = 30, aqiLevel = AqiLevel.GOOD,
        pm25 = 8.0, pm10 = 15.0, ozone = 25.0,
        nitrogenDioxide = 10.0, sulphurDioxide = 3.0, carbonMonoxide = 150.0,
        pollen = PollenData(),
    )

    private val testAstronomy = AstronomyData(
        moonPhase = MoonPhase.WAXING_GIBBOUS,
        moonIllumination = 75.0,
        moonrise = "5:30 PM", moonset = "5:15 AM",
        dayLength = "10h 30m",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        weatherRepository = mockk()
        alertRepository = mockk()
        airQualityRepository = mockk()
        weatherSourceManager = mockk()
        radarRepository = mockk()
        locationRepository = mockk()
        locationProvider = mockk()
        prefs = mockk(relaxed = true)

        every { prefs.settings } returns flowOf(NimbusSettings())
        every { prefs.lastLocation } returns flowOf(null)
        coEvery { prefs.saveLastLocation(any(), any(), any()) } returns mockk()
        coEvery { locationRepository.ensureCurrentLocation(any(), any(), any()) } returns Unit
        every { locationRepository.savedLocations } returns flowOf(emptyList())
        coEvery { alertRepository.getAlerts(any(), any()) } coAnswers { Result.success(emptyList()) }
        coEvery { airQualityRepository.getAirQuality(any(), any()) } coAnswers { Result.success(testAirQuality) }
        coEvery { weatherSourceManager.getAlerts(any(), any()) } coAnswers { Result.success(emptyList()) }
        coEvery { weatherSourceManager.getAirQuality(any(), any()) } coAnswers { Result.success(testAirQuality) }
        coEvery { weatherSourceManager.getMinutelyPrecipitation(any(), any()) } coAnswers { Result.success(emptyList()) }
        every { airQualityRepository.getAstronomy(any(), any()) } returns testAstronomy
        coEvery { weatherRepository.getWeather(any(), any(), any()) } coAnswers { Result.success(testWeatherData) }
        coEvery { weatherRepository.getMinutelyPrecipitation(any(), any()) } coAnswers { Result.success(emptyList()) }
        coEvery { weatherRepository.getYesterdayWeather(any(), any()) } coAnswers { Result.success(null) }
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null
        coEvery { radarRepository.getRadarFrames() } coAnswers {
            Result.success(RadarFrameSet(past = emptyList(), forecast = emptyList()))
        }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun stubLocationSuccess() {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } coAnswers { Result.success(mockLocation) }
    }

    private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()): MainViewModel {
        return MainViewModel(
            appContext = mockk(relaxed = true),
            repository = weatherRepository,
            alertRepository = alertRepository,
            airQualityRepository = airQualityRepository,
            weatherSourceManager = weatherSourceManager,
            radarRepository = radarRepository,
            locationRepository = locationRepository,
            locationProvider = locationProvider,
            prefs = prefs,
            geminiNanoSummaryEngine = mockk(relaxed = true),
            savedStateHandle = savedState,
        )
    }

    private fun TestScope.createAndAdvance(): MainViewModel {
        val vm = createViewModel()
        advanceUntilIdle()
        return vm
    }

    // --- Success path ---

    @Test
    fun `loadWeather transitions from loading to success`() = runTest(testDispatcher) {
        stubLocationSuccess()
        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.weatherData)
        assertEquals("Denver", state.weatherData?.location?.name)
    }

    @Test
    fun `loadWeather fetches alerts and air quality after success`() = runTest(testDispatcher) {
        stubLocationSuccess()
        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertNotNull(state.airQuality)
        assertEquals(42, state.airQuality?.usAqi)
        assertNotNull(state.astronomy)
    }

    // --- Permission flow ---

    @Test
    fun `loadWeather requests permission when not granted`() = runTest(testDispatcher) {
        every { locationProvider.hasLocationPermission } returns false
        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertTrue(state.needsLocationPermission)
        assertFalse(state.isLoading)
    }

    @Test
    fun `onPermissionGranted reloads weather`() = runTest(testDispatcher) {
        every { locationProvider.hasLocationPermission } returnsMany listOf(false, true)
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        coEvery { locationProvider.getCurrentLocation() } coAnswers { Result.success(mockLocation) }

        viewModel = createAndAdvance()
        viewModel.onPermissionGranted()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.needsLocationPermission)
        assertNotNull(state.weatherData)
    }

    @Test
    fun `onPermissionDenied sets error state`() = runTest(testDispatcher) {
        every { locationProvider.hasLocationPermission } returns false
        viewModel = createAndAdvance()
        viewModel.onPermissionDenied()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.needsLocationPermission)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("permission", ignoreCase = true))
    }

    // --- Error + cache fallback ---

    @Test
    fun `loadWeather falls back to cache on API error`() = runTest(testDispatcher) {
        stubLocationSuccess()
        coEvery { weatherRepository.getWeather(any(), any(), any()) } coAnswers { Result.failure(Exception("Network error")) }
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(39.7, -104.9) } returns testWeatherData

        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertNotNull(state.weatherData)
        assertTrue(state.isCached)
    }

    @Test
    fun `loadWeather shows error when API fails and no cache`() = runTest(testDispatcher) {
        stubLocationSuccess()
        coEvery { weatherRepository.getWeather(any(), any(), any()) } coAnswers { Result.failure(Exception("Network error")) }
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))

        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertNull(state.weatherData)
        assertNotNull(state.error)
    }

    // --- Refresh ---

    @Test
    fun `refresh sets isRefreshing then clears`() = runTest(testDispatcher) {
        stubLocationSuccess()
        viewModel = createAndAdvance()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertNotNull(state.weatherData)
    }

    // --- Preferences ---

    @Test
    fun `particle preference is observed from settings`() = runTest(testDispatcher) {
        val settingsFlow = MutableStateFlow(NimbusSettings(particlesEnabled = false))
        every { prefs.settings } returns settingsFlow
        every { locationProvider.hasLocationPermission } returns false

        viewModel = createAndAdvance()
        assertFalse(viewModel.uiState.value.particlesEnabled)

        settingsFlow.value = NimbusSettings(particlesEnabled = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.particlesEnabled)
    }
}
