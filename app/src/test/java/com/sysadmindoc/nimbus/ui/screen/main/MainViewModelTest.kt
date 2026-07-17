package com.sysadmindoc.nimbus.ui.screen.main

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import com.sysadmindoc.nimbus.data.location.LocationProvider
import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.data.repository.*
import com.sysadmindoc.nimbus.sync.WearSyncManager
import com.sysadmindoc.nimbus.ui.component.TimeTravelStatus
import com.sysadmindoc.nimbus.util.ConnectivityObserver
import com.sysadmindoc.nimbus.util.SummaryEngine
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
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
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var airQualityRepository: AirQualityRepository
    private lateinit var weatherSourceManager: WeatherSourceManager
    private lateinit var radarRepository: RadarRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var savedLocationsFlow: MutableStateFlow<List<SavedLocationEntity>>
    private lateinit var locationProvider: LocationProvider
    private lateinit var prefs: UserPreferences
    private lateinit var connectivityObserver: ConnectivityObserver
    private lateinit var summaryEngine: SummaryEngine
    private lateinit var wearSyncManager: WearSyncManager
    private lateinit var onThisDayRepository: OnThisDayRepository
    private lateinit var forecastEvolutionRepository: ForecastEvolutionRepository
    private lateinit var pwsRepository: PwsRepository
    private lateinit var timeTravelRepository: TimeTravelRepository

    private lateinit var viewModel: MainViewModel

    private val testWeatherData = WeatherData(
        location = LocationInfo("Denver", "Colorado", "US", 39.7, -104.9),
        current = CurrentConditions(
            temperature = 22.2, feelsLike = 21.1, humidity = 45,
            weatherCode = WeatherCode.CLEAR_SKY,
            observationTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            isDay = true,
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        weatherRepository = mockk()
        airQualityRepository = mockk()
        weatherSourceManager = mockk()
        radarRepository = mockk()
        locationRepository = mockk()
        savedLocationsFlow = MutableStateFlow(emptyList())
        locationProvider = mockk()
        prefs = mockk(relaxed = true)
        connectivityObserver = mockk()
        summaryEngine = mockk()
        wearSyncManager = mockk(relaxed = true)
        onThisDayRepository = mockk(relaxed = true)
        forecastEvolutionRepository = mockk(relaxed = true)
        pwsRepository = mockk(relaxed = true)
        timeTravelRepository = mockk()
        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } returns null
        every { connectivityObserver.isOnline } returns flowOf(true)
        every { summaryEngine.isAvailable() } returns false
        every { summaryEngine.close() } just Runs
        coEvery {
            summaryEngine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } returns null

        every { prefs.settings } returns flowOf(NimbusSettings())
        every { prefs.lastLocation } returns flowOf(null)
        every { prefs.backgroundAlertLocation } returns flowOf(null)
        coEvery { prefs.saveLastLocation(any(), any(), any()) } returns mockk()
        coEvery { prefs.saveBackgroundAlertLocation(any(), any(), any()) } returns mockk()
        coEvery { locationRepository.ensureCurrentLocation(any(), any(), any()) } returns Unit
        every { locationRepository.savedLocations } returns savedLocationsFlow
        coEvery { weatherSourceManager.getAlerts(any(), any(), any(), any(), any()) } coAnswers {
            Result.success(emptyList())
        }
        coEvery { weatherSourceManager.getAirQuality(any(), any()) } coAnswers { Result.success(testAirQuality) }
        every { airQualityRepository.getAstronomy(any(), any(), any(), any(), any(), any()) } returns testAstronomy
        coEvery { onThisDayRepository.getOnThisDay(any(), any(), any()) } returns null
        coEvery { forecastEvolutionRepository.getForecastEvolution(any(), any(), any()) } returns Result.failure(
            IllegalStateException("disabled in default tests"),
        )
        coEvery { weatherRepository.getWeather(any(), any(), any(), any(), any(), any()) } coAnswers {
            val latitude = firstArg<Double>()
            val longitude = secondArg<Double>()
            val locationName = thirdArg<String?>() ?: testWeatherData.location.name
            Result.success(
                testWeatherData.copy(
                    location = LocationInfo(
                        name = locationName,
                        region = testWeatherData.location.region,
                        country = testWeatherData.location.country,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
            )
        }
        coEvery { weatherRepository.getMinutelyPrecipitation(any(), any()) } coAnswers { Result.success(emptyList()) }
        coEvery { weatherRepository.getYesterdayWeather(any(), any()) } coAnswers { Result.success(null) }
        coEvery { weatherRepository.getCachedWeather(any(), any(), any(), any()) } returns null
        coEvery { radarRepository.getRadarFrames(any(), any()) } coAnswers {
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
            dependencies = MainViewModelDependencies(
                repository = weatherRepository,
                locationRepository = locationRepository,
                locationProvider = locationProvider,
                prefs = prefs,
                connectivityObserver = connectivityObserver,
                weatherLoadCoordinator = WeatherLoadCoordinator(
                    core = WeatherLoadCoreDependencies(
                        appContext = mockk(relaxed = true),
                        repository = weatherRepository,
                        weatherSourceManager = weatherSourceManager,
                        radarRepository = radarRepository,
                        airQualityRepository = airQualityRepository,
                        summaryEngine = summaryEngine,
                        wearSyncManager = wearSyncManager,
                        gadgetbridgeBroadcaster = mockk(relaxed = true),
                        defaultDispatcher = testDispatcher,
                    ),
                    optionalRepositories = WeatherLoadOptionalRepositories(
                        onThisDayRepository = onThisDayRepository,
                        forecastEvolutionRepository = forecastEvolutionRepository,
                        forecastAccuracyRepository = mockk(relaxed = true),
                        confidenceBandRepository = mockk(relaxed = true),
                        auroraRepository = mockk(relaxed = true),
                        marineRepository = mockk(relaxed = true),
                        floodRepository = mockk(relaxed = true),
                        climateRepository = mockk(relaxed = true),
                        pwsRepository = pwsRepository,
                        timeTravelRepository = timeTravelRepository,
                    ),
                ),
            ),
            savedStateHandle = savedState,
        )
    }

    private fun TestScope.createAndAdvance(): MainViewModel {
        val vm = createViewModel()
        advanceUntilIdle()
        return vm
    }

    private fun weatherFor(location: SavedLocationEntity): WeatherData {
        return testWeatherData.copy(
            location = LocationInfo(
                name = location.name,
                region = location.region,
                country = location.country,
                latitude = location.latitude,
                longitude = location.longitude,
            )
        )
    }

    // --- Success path ---

    @Test
    fun `loadWeather transitions from loading to success`() = runTest {
        stubLocationSuccess()
        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.weatherData)
        assertEquals("Denver", state.weatherData?.location?.name)
    }

    @Test
    fun `gps weather success stores background alert location`() = runTest {
        stubLocationSuccess()
        viewModel = createAndAdvance()

        coVerify(exactly = 1) {
            prefs.saveBackgroundAlertLocation(39.7, -104.9, "Denver")
        }
    }

    @Test
    fun `loadWeather fetches alerts and air quality after success`() = runTest {
        stubLocationSuccess()
        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertNotNull(state.airQuality)
        assertEquals(42, state.airQuality?.usAqi)
        assertNotNull(state.astronomy)
    }

    @Test
    fun `loadWeather anchors calendar helpers to forecast observation time`() = runTest {
        stubLocationSuccess()
        viewModel = createAndAdvance()

        val observationTime = testWeatherData.current.observationTime ?: error("missing observation time")
        verify {
            airQualityRepository.getAstronomy(
                sunrise = testWeatherData.current.sunrise,
                sunset = testWeatherData.current.sunset,
                latitude = any(),
                longitude = any(),
                zoneId = any(),
                referenceTime = observationTime,
            )
        }
        coVerify {
            onThisDayRepository.getOnThisDay(any(), any(), observationTime.toLocalDate())
        }
    }

    // --- Permission flow ---

    @Test
    fun `loadWeather requests permission when not granted`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertTrue(state.needsLocationPermission)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadWeather uses retained cached location without prompting for permission`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(39.7, -104.9, SourceOverrides(), null) } returns testWeatherData

        viewModel = createAndAdvance()

        val state = viewModel.uiState.value
        assertFalse(state.needsLocationPermission)
        assertFalse(state.isLoading)
        assertTrue(state.isCached)
        assertEquals("Denver", state.weatherData?.location?.name)
        coVerify(exactly = 0) { locationProvider.getCurrentLocation() }
    }

    @Test
    fun `onPermissionGranted reloads weather`() = runTest {
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
    fun `onPermissionDenied sets error state`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        viewModel = createAndAdvance()
        viewModel.onPermissionDenied()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.needsLocationPermission)
        assertNotNull(state.error)
        assertEquals(MainUiErrorKind.LOCATION_PERMISSION_DENIED, state.error?.kind)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `refresh clears spinner when permission is missing`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        viewModel = createAndAdvance()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    // --- Error + cache fallback ---

    @Test
    fun `loadWeather falls back to cache on API error`() = runTest {
        stubLocationSuccess()
        coEvery { weatherRepository.getWeather(any(), any(), any(), any(), any(), any()) } coAnswers {
            Result.failure(Exception("Network error"))
        }
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(39.7, -104.9, SourceOverrides(), null) } returns testWeatherData

        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertNotNull(state.weatherData)
        assertTrue(state.isCached)
    }

    @Test
    fun `loadWeather shows error when API fails and no cache`() = runTest {
        stubLocationSuccess()
        coEvery { weatherRepository.getWeather(any(), any(), any(), any(), any(), any()) } coAnswers {
            Result.failure(Exception("Network error"))
        }
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))

        viewModel = createAndAdvance()
        val state = viewModel.uiState.value
        assertNull(state.weatherData)
        assertNotNull(state.error)
    }

    @Test
    fun `failed fetch for a location does not render another location's cache`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(39.7, -104.9, SourceOverrides(), null) } returns testWeatherData
        coEvery { weatherRepository.getCachedWeather(seattle.latitude, seattle.longitude, SourceOverrides(), seattle.id) } returns null
        coEvery {
            weatherRepository.getWeather(seattle.latitude, seattle.longitude, any(), null, any(), seattle.id)
        } returns Result.failure(Exception("Network error"))

        viewModel = createAndAdvance()
        // Init has no coords (permission denied), so the lastLocation cache is a valid fallback.
        assertEquals("Denver", viewModel.uiState.value.weatherData?.location?.name)

        viewModel.loadWeatherForCoords(seattle.latitude, seattle.longitude, seattle.id, seattle.name)
        advanceUntilIdle()

        // A failed Seattle fetch must NOT fall back to Denver's lastLocation cache.
        val state = viewModel.uiState.value
        assertNull(state.weatherData)
        assertNotNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `failed fetch falls back to the requested location's own cache`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getCachedWeather(39.7, -104.9, SourceOverrides(), null) } returns testWeatherData
        coEvery {
            weatherRepository.getCachedWeather(seattle.latitude, seattle.longitude, SourceOverrides(), seattle.id)
        } returns weatherFor(seattle)
        coEvery {
            weatherRepository.getWeather(seattle.latitude, seattle.longitude, any(), null, any(), seattle.id)
        } returns Result.failure(Exception("Network error"))

        viewModel = createAndAdvance()
        viewModel.loadWeatherForCoords(seattle.latitude, seattle.longitude, seattle.id, seattle.name)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Seattle", state.weatherData?.location?.name)
        assertTrue(state.isCached)
        assertNull(state.error)
    }

    @Test
    fun `saved location switch renders provider cache before live refresh completes`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        val provider = WeatherSourceProvider.MET_NORWAY
        val saved = seattle.copy(forecastSource = provider.name)
        val overrides = SourceOverrides(forecast = provider)
        val cachedWeather = weatherFor(saved).copy(sourceProvider = provider.displayName)
        val liveWeather = weatherFor(saved).copy(
            current = weatherFor(saved).current.copy(temperature = 18.0),
            sourceProvider = provider.displayName,
        )
        val liveStarted = CompletableDeferred<Unit>()
        val releaseLive = CompletableDeferred<Unit>()
        coEvery {
            weatherRepository.getCachedWeather(saved.latitude, saved.longitude, overrides, saved.id)
        } returns cachedWeather
        coEvery {
            weatherRepository.getWeather(saved.latitude, saved.longitude, saved.name, null, overrides, saved.id)
        } coAnswers {
            liveStarted.complete(Unit)
            releaseLive.await()
            Result.success(liveWeather)
        }

        viewModel = createAndAdvance()
        viewModel.loadWeatherForCoords(saved.latitude, saved.longitude, saved.id, saved.name, overrides)
        liveStarted.await()

        val cachedState = viewModel.uiState.value
        assertEquals("Seattle", cachedState.weatherData?.location?.name)
        assertTrue(cachedState.isCached)
        assertFalse(cachedState.isLoading)

        releaseLive.complete(Unit)
        advanceUntilIdle()

        val liveState = viewModel.uiState.value
        assertEquals("Seattle", liveState.weatherData?.location?.name)
        assertFalse(liveState.isCached)
        assertEquals(18.0, liveState.weatherData?.current?.temperature ?: 0.0, 0.01)
    }

    @Test
    fun `saved location fetch does not replace background alert location`() = runTest {
        stubLocationSuccess()
        viewModel = createAndAdvance()
        clearMocks(prefs, answers = false, recordedCalls = true, childMocks = false)

        viewModel.loadWeatherForCoords(seattle.latitude, seattle.longitude, seattle.id, seattle.name)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            prefs.saveBackgroundAlertLocation(any(), any(), any())
        }
    }

    @Test
    fun `loadWeather shows actionable message when location services are off`() = runTest {
        every { locationProvider.hasLocationPermission } returns true
        coEvery { locationProvider.getCurrentLocation() } returns Result.failure(
            IllegalStateException("Location services are turned off.")
        )

        viewModel = createAndAdvance()
        val state = viewModel.uiState.value

        assertNull(state.weatherData)
        assertEquals(MainUiErrorKind.LOCATION_SERVICES_OFF, state.error?.kind)
        assertFalse(state.isLoading)
    }

    // --- Refresh ---

    @Test
    fun `refresh sets isRefreshing then clears`() = runTest {
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
    fun `particle preference is observed from settings`() = runTest {
        val settingsFlow = MutableStateFlow(NimbusSettings(particlesEnabled = false))
        every { prefs.settings } returns settingsFlow
        every { locationProvider.hasLocationPermission } returns false

        viewModel = createAndAdvance()
        assertFalse(viewModel.uiState.value.particlesEnabled)

        settingsFlow.value = NimbusSettings(particlesEnabled = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.particlesEnabled)
    }

    @Test
    fun `deleted selected saved location recovers to remaining saved location`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        savedLocationsFlow.value = listOf(seattle, chicago)

        viewModel = createAndAdvance()
        viewModel.onPageChanged(1)
        advanceUntilIdle()

        clearMocks(weatherRepository, answers = false, recordedCalls = true)

        savedLocationsFlow.value = listOf(seattle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.currentPage)
        assertEquals(listOf(seattle), state.savedLocations)
        assertEquals("Seattle", state.weatherData?.location?.name)
        coVerify { weatherRepository.getWeather(seattle.latitude, seattle.longitude, seattle.name, null, any(), seattle.id) }
    }

    @Test
    fun `failed location change clears previous location scoped state`() = runTest {
        stubLocationSuccess()
        savedLocationsFlow.value = listOf(seattle)
        coEvery { weatherSourceManager.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(
            listOf(
                WeatherAlert(
                    id = "alert-1",
                    event = "Storm",
                    headline = "Storm incoming",
                    description = "Storm details",
                    instruction = "Stay aware",
                    severity = AlertSeverity.MODERATE,
                    urgency = AlertUrgency.EXPECTED,
                    certainty = "Likely",
                    senderName = "NWS",
                    areaDescription = "Denver",
                    effective = "2026-04-15T12:00:00Z",
                    expires = "2026-04-15T18:00:00Z",
                    response = null,
                )
            )
        )
        coEvery {
            weatherRepository.getWeather(seattle.latitude, seattle.longitude, seattle.name, null, any(), seattle.id)
        } returns Result.failure(Exception("Network error"))

        viewModel = createAndAdvance()
        assertNotNull(viewModel.uiState.value.weatherData)
        assertNotNull(viewModel.uiState.value.airQuality)
        assertNotNull(viewModel.uiState.value.astronomy)
        assertEquals(1, viewModel.uiState.value.alerts.size)

        viewModel.onPageChanged(0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.weatherData)
        assertNull(state.airQuality)
        assertNull(state.astronomy)
        assertTrue(state.alerts.isEmpty())
        assertTrue(state.nowcastData.isEmpty())
        assertNull(state.radarPreviewTileUrl)
        assertNotNull(state.error)
    }

    @Test
    fun `latest location load wins when earlier request completes later`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        savedLocationsFlow.value = listOf(seattle, chicago)

        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val releaseSecondRequest = CompletableDeferred<Unit>()
        var requestCount = 0

        coEvery { weatherRepository.getWeather(any(), any(), any(), any(), any(), any()) } coAnswers {
            when (++requestCount) {
                1 -> {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                    Result.success(weatherFor(seattle))
                }
                2 -> {
                    releaseSecondRequest.await()
                    Result.success(weatherFor(chicago))
                }
                else -> Result.success(testWeatherData)
            }
        }

        viewModel = createAndAdvance()

        viewModel.onPageChanged(0)
        firstRequestStarted.await()

        viewModel.onPageChanged(1)
        releaseSecondRequest.complete(Unit)
        advanceUntilIdle()

        releaseFirstRequest.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentPage)
        assertEquals("Chicago", state.weatherData?.location?.name)
        assertEquals(chicago.latitude, state.weatherData?.location?.latitude ?: 0.0, 0.0001)
        assertNull(state.error)
    }

    // --- Time-travel scrub (selectHistoricalDate) ---

    private val sampleOnThisDay = OnThisDayData(
        priorYears = listOf(
            PriorYearEntry(year = 2025, highC = 20.0, lowC = 8.0, precipMm = null),
            PriorYearEntry(year = 2024, highC = 18.0, lowC = 6.0, precipMm = 1.2),
        ),
        averageHighC = 19.0,
        averageLowC = 7.0,
        recordHighC = 20.0,
        recordLowC = 6.0,
    )

    private val sampleTimeTravelDay = TimeTravelDay(
        date = LocalDate.of(2020, 6, 1),
        weatherCode = WeatherCode.CLEAR_SKY,
        highC = 25.0,
        lowC = 12.0,
        precipMm = 0.0,
        isHistorical = true,
    )

    @Test
    fun `selectHistoricalDate success sets scrub day and clears status`() = runTest {
        stubLocationSuccess()
        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } returns sampleTimeTravelDay
        viewModel = createAndAdvance()

        viewModel.selectHistoricalDate(LocalDate.of(2020, 6, 1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(sampleTimeTravelDay, state.timeTravelDay)
        assertEquals(TimeTravelStatus.IDLE, state.timeTravelStatus)
    }

    @Test
    fun `selectHistoricalDate shows loading while fetch is in flight`() = runTest {
        stubLocationSuccess()
        val scrubStarted = CompletableDeferred<Unit>()
        val releaseScrub = CompletableDeferred<Unit>()
        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } coAnswers {
            scrubStarted.complete(Unit)
            releaseScrub.await()
            sampleTimeTravelDay
        }
        viewModel = createAndAdvance()

        viewModel.selectHistoricalDate(LocalDate.of(2020, 6, 1))
        scrubStarted.await()
        assertEquals(TimeTravelStatus.LOADING, viewModel.uiState.value.timeTravelStatus)

        releaseScrub.complete(Unit)
        advanceUntilIdle()
        assertEquals(TimeTravelStatus.IDLE, viewModel.uiState.value.timeTravelStatus)
        assertEquals(sampleTimeTravelDay, viewModel.uiState.value.timeTravelDay)
    }

    @Test
    fun `selectHistoricalDate failure keeps previous data and flags error`() = runTest {
        stubLocationSuccess()
        coEvery { onThisDayRepository.getOnThisDay(any(), any(), any()) } returns sampleOnThisDay
        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } returns sampleTimeTravelDay
        viewModel = createAndAdvance()

        viewModel.selectHistoricalDate(LocalDate.of(2020, 6, 1))
        advanceUntilIdle()
        assertEquals(sampleTimeTravelDay, viewModel.uiState.value.timeTravelDay)

        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } throws java.io.IOException("network down")
        viewModel.selectHistoricalDate(LocalDate.of(2019, 3, 2))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TimeTravelStatus.ERROR, state.timeTravelStatus)
        // Previous scrub result and aggregate must survive the failed fetch.
        assertEquals(sampleTimeTravelDay, state.timeTravelDay)
        assertEquals(sampleOnThisDay, state.onThisDay)
    }

    @Test
    fun `selectHistoricalDate with no archive data keeps previous day and flags unavailable`() = runTest {
        stubLocationSuccess()
        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } returns sampleTimeTravelDay
        viewModel = createAndAdvance()

        viewModel.selectHistoricalDate(LocalDate.of(2020, 6, 1))
        advanceUntilIdle()
        assertEquals(sampleTimeTravelDay, viewModel.uiState.value.timeTravelDay)

        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } returns null
        viewModel.selectHistoricalDate(LocalDate.now().minusDays(1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TimeTravelStatus.DATE_UNAVAILABLE, state.timeTravelStatus)
        assertEquals(sampleTimeTravelDay, state.timeTravelDay)
    }

    @Test
    fun `superseded scrub resolution is discarded when a newer date pick lands first`() = runTest {
        stubLocationSuccess()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstDay = sampleTimeTravelDay.copy(date = LocalDate.of(1990, 6, 1), highC = 10.0)
        val secondDay = sampleTimeTravelDay.copy(date = LocalDate.of(2020, 6, 1))
        coEvery { timeTravelRepository.getDay(any(), any(), LocalDate.of(1990, 6, 1), any(), any()) } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
            firstDay
        }
        coEvery { timeTravelRepository.getDay(any(), any(), LocalDate.of(2020, 6, 1), any(), any()) } returns secondDay
        viewModel = createAndAdvance()

        viewModel.selectHistoricalDate(LocalDate.of(1990, 6, 1))
        firstStarted.await()
        viewModel.selectHistoricalDate(LocalDate.of(2020, 6, 1))
        advanceUntilIdle()
        assertEquals(secondDay, viewModel.uiState.value.timeTravelDay)

        // The superseded 1990 fetch resolves last — it must neither replace
        // the newer pick's day nor strand the status away from IDLE.
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(secondDay, state.timeTravelDay)
        assertEquals(TimeTravelStatus.IDLE, state.timeTravelStatus)
    }

    @Test
    fun `stale scrub response after location swap does not repopulate state`() = runTest {
        every { locationProvider.hasLocationPermission } returns false
        savedLocationsFlow.value = listOf(seattle, chicago)
        val scrubStarted = CompletableDeferred<Unit>()
        val releaseScrub = CompletableDeferred<Unit>()
        coEvery { timeTravelRepository.getDay(any(), any(), any(), any(), any()) } coAnswers {
            scrubStarted.complete(Unit)
            releaseScrub.await()
            sampleTimeTravelDay
        }
        viewModel = createAndAdvance()

        viewModel.onPageChanged(0) // load Seattle
        advanceUntilIdle()
        viewModel.selectHistoricalDate(LocalDate.of(2020, 6, 1))
        scrubStarted.await()

        viewModel.onPageChanged(1) // swap to Chicago while the scrub is in flight
        advanceUntilIdle()
        releaseScrub.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Chicago", state.weatherData?.location?.name)
        // The stale Seattle archive response must not land under Chicago.
        assertNull(state.timeTravelDay)
        assertEquals(TimeTravelStatus.IDLE, state.timeTravelStatus)
    }

    // --- Wear sync alert semantics ---

    @Test
    fun `failed alert fetch syncs null alerts so the watch keeps its stored set`() = runTest {
        stubLocationSuccess()
        coEvery { weatherSourceManager.getAlerts(any(), any(), any(), any(), any()) } returns
            Result.failure(Exception("alerts endpoint down"))
        val syncedAlerts = mutableListOf<List<WeatherAlert>?>()
        coEvery { wearSyncManager.syncWeather(any(), captureNullable(syncedAlerts), any()) } returns Unit

        viewModel = createAndAdvance()

        assertTrue(viewModel.uiState.value.alertsFetchFailed)
        assertTrue(syncedAlerts.isNotEmpty())
        // null omits the alerts key so the watch preserves a live warning
        // instead of interpreting a transient failure as "none active".
        assertNull(syncedAlerts.last())
    }

    @Test
    fun `successful empty alert fetch syncs an empty list so the watch clears alerts`() = runTest {
        stubLocationSuccess()
        val syncedAlerts = mutableListOf<List<WeatherAlert>?>()
        coEvery { wearSyncManager.syncWeather(any(), captureNullable(syncedAlerts), any()) } returns Unit

        viewModel = createAndAdvance()

        assertFalse(viewModel.uiState.value.alertsFetchFailed)
        assertTrue(syncedAlerts.isNotEmpty())
        assertEquals(emptyList<WeatherAlert>(), syncedAlerts.last())
    }

    // --- Supplemental fetch isolation ---

    @Test
    fun `provider agreement throw does not cancel sibling alert and aqi fetches`() = runTest {
        stubLocationSuccess()
        every { prefs.settings } returns flowOf(
            NimbusSettings(disabledCards = DEFAULT_DISABLED_CARDS - CardType.PROVIDER_AGREEMENT.name),
        )
        coEvery {
            weatherSourceManager.getWeatherFromProvider(any(), any(), any(), any(), any())
        } throws RuntimeException("provider comparison blew up")

        viewModel = createAndAdvance()

        coVerify(atLeast = 1) { weatherSourceManager.getWeatherFromProvider(any(), any(), any(), any(), any()) }
        val state = viewModel.uiState.value
        // Siblings in the supplemental coroutineScope must have completed.
        assertNotNull(state.airQuality)
        assertNotNull(state.astronomy)
        assertFalse(state.alertsFetchFailed)
        // The failed comparison degrades to "unavailable" instead of throwing.
        assertFalse(state.isProviderAgreementLoading)
        assertTrue(state.providerAgreementUnavailable)
        assertNull(state.providerAgreement)
    }
}
