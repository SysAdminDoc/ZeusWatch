package com.sysadmindoc.nimbus.ui.screen.main

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var airQualityRepository: AirQualityRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var locationProvider: LocationProvider
    private lateinit var prefs: UserPreferences

    private lateinit var viewModel: MainViewModel

    // --- Test fixtures ---

    private val testWeatherData = WeatherData(
        location = LocationInfo("Denver", "Colorado", "US", 39.7, -104.9),
        current = CurrentConditions(
            temperature = 22.2, // Celsius (API now returns metric)
            feelsLike = 21.1,
            humidity = 45,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            windSpeed = 12.8, // km/h
            windDirection = 180,
            windGusts = 24.1,
            pressure = 1013.25,
            uvIndex = 5.0,
            visibility = 16000.0,
            dewPoint = 10.0,
            cloudCover = 20,
            precipitation = 0.0,
            dailyHigh = 26.7,
            dailyLow = 12.8,
            sunrise = "2025-01-15T07:00:00",
            sunset = "2025-01-15T17:30:00",
        ),
        hourly = emptyList(),
        daily = emptyList(),
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
        locationRepository = mockk()
        locationProvider = mockk()
        prefs = mockk(relaxed = true)

        // Default stubs
        every { prefs.settings } returns flowOf(NimbusSettings())
        every { prefs.lastLocation } returns flowOf(null)
        coEvery { prefs.saveLastLocation(any(), any(), any()) } just Awaits
        coEvery { locationRepository.ensureCurrentLocation(any(), any(), any()) } just Awaits
        every { locationRepository.savedLocations } returns flowOf(emptyList())
        coEvery { alertRepository.getAlerts(any(), any()) } returns Result.success(emptyList())
        coEvery { airQualityRepository.getAirQuality(any(), any()) } returns Result.success(testAirQuality)
        every { airQualityRepository.getAstronomy(any(), any()) } returns testAstronomy
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()): MainViewModel {
        return MainViewModel(
            repository = weatherRepository,
            alertRepository = alertRepository,
            airQualityRepository = airQualityRepository,
            locationRepository = locationRepository,
            locationProvider = locationProvider,
            prefs = prefs,
            savedStateHandle = savedState,
        )
    }

    // --- Success path ---

    @Test
    fun `loadWeather transitions from loading to success`() = runTest {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } returns Result.success(mockLocation)
        coEvery { weatherRepository.getWeather(39.7, -104.9, null) } returns Result.success(testWeatherData)

        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertNotNull(state.weatherData)
            assertEquals("Denver", state.weatherData?.location?.name)
            assertNull(state.error)
            assertFalse(state.isCached)
        }
    }

    @Test
    fun `loadWeather fetches alerts and air quality after success`() = runTest {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } returns Result.success(mockLocation)
        coEvery { weatherRepository.getWeather(39.7, -104.9, null) } returns Result.success(testWeatherData)

        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertNotNull(state.airQuality)
            assertEquals(42, state.airQuality?.usAqi)
            assertNotNull(state.astronomy)
        }

        coVerify { alertRepository.getAlerts(39.7, -104.9) }
        coVerify { airQualityRepository.getAirQuality(39.7, -104.9) }
    }

    // --- Permission flow ---

    @Test
    fun `loadWeather requests permission when not granted`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null

        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.needsLocationPermission)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `onPermissionGranted reloads weather`() = runTest {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9

        every { locationProvider.hasLocationPermission } returnsMany listOf(false, true)
        coEvery { locationProvider.getCurrentLocation() } returns Result.success(mockLocation)
        coEvery { weatherRepository.getWeather(39.7, -104.9, null) } returns Result.success(testWeatherData)
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null

        viewModel = createViewModel()
        viewModel.onPermissionGranted()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.needsLocationPermission)
            assertNotNull(state.weatherData)
        }
    }

    @Test
    fun `onPermissionDenied sets error state`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null

        viewModel = createViewModel()
        viewModel.onPermissionDenied()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.needsLocationPermission)
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("permission", ignoreCase = true))
        }
    }

    // --- Error + cache fallback ---

    @Test
    fun `loadWeather falls back to cache on API error`() = runTest {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } returns Result.success(mockLocation)
        coEvery { weatherRepository.getWeather(any(), any(), any()) } returns Result.failure(Exception("Network error"))
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(39.7, -104.9) } returns testWeatherData

        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertNotNull(state.weatherData)
            assertTrue(state.isCached)
            assertNull(state.error)
        }
    }

    @Test
    fun `loadWeather shows error when API fails and no cache`() = runTest {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } returns Result.success(mockLocation)
        coEvery { weatherRepository.getWeather(any(), any(), any()) } returns Result.failure(Exception("Network error"))
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null

        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertNull(state.weatherData)
            assertNotNull(state.error)
        }
    }

    // --- Refresh ---

    @Test
    fun `refresh sets isRefreshing then clears`() = runTest {
        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 39.7
        every { mockLocation.longitude } returns -104.9
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } returns Result.success(mockLocation)
        coEvery { weatherRepository.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        viewModel = createViewModel()
        viewModel.refresh()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isRefreshing)
            assertNotNull(state.weatherData)
        }
    }

    // --- Preferences ---

    @Test
    fun `particle preference is observed from settings`() = runTest {
        val settingsFlow = MutableStateFlow(NimbusSettings(particlesEnabled = false))
        every { prefs.settings } returns settingsFlow
        every { locationProvider.hasLocationPermission } returns false
        coEvery { weatherRepository.getCachedWeather(any(), any()) } returns null

        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.particlesEnabled)
        }

        settingsFlow.value = NimbusSettings(particlesEnabled = true)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.particlesEnabled)
        }
    }
}
