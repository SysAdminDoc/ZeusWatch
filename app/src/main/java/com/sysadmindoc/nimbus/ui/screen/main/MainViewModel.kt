package com.sysadmindoc.nimbus.ui.screen.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.location.LocationProvider
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.util.ClothingSuggestion
import com.sysadmindoc.nimbus.util.ClothingSuggestionEvaluator
import com.sysadmindoc.nimbus.util.DrivingAlert
import com.sysadmindoc.nimbus.util.DrivingConditionEvaluator
import com.sysadmindoc.nimbus.util.HealthAlert
import com.sysadmindoc.nimbus.util.HealthAlertEvaluator
import com.sysadmindoc.nimbus.util.PetSafetyAlert
import com.sysadmindoc.nimbus.util.PetSafetyEvaluator
import com.sysadmindoc.nimbus.util.ConnectivityObserver
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.SummaryEngine
import com.sysadmindoc.nimbus.util.WeatherSummaryEngine
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import com.sysadmindoc.nimbus.data.repository.AirQualityRepository
import com.sysadmindoc.nimbus.data.repository.AlertRepository
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SummaryStyle
import com.sysadmindoc.nimbus.data.repository.RadarRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import com.sysadmindoc.nimbus.sync.WearSyncManager
import com.sysadmindoc.nimbus.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: WeatherRepository,
    private val alertRepository: AlertRepository,
    private val airQualityRepository: AirQualityRepository,
    private val weatherSourceManager: WeatherSourceManager,
    private val radarRepository: RadarRepository,
    private val locationRepository: LocationRepository,
    private val locationProvider: LocationProvider,
    private val prefs: UserPreferences,
    private val summaryEngine: SummaryEngine,
    private val connectivityObserver: ConnectivityObserver,
    private val onThisDayRepository: com.sysadmindoc.nimbus.data.repository.OnThisDayRepository,
    private val wearSyncManager: WearSyncManager,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val overrideLocationId: Long = savedStateHandle.get<Long>("locationId") ?: 0L

    /** Track active coordinates for refresh without reverting to GPS. */
    private var activeLatitude: Double? = null
    private var activeLongitude: Double? = null
    private var activeLocationId: Long? = null
    private var activeLocationName: String? = null
    private var useGpsLocation: Boolean = true

    init {
        Log.d(TAG, "init: overrideLocationId=$overrideLocationId")
        observeSettings()
        observeSavedLocations()
        observeLastLocation()
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online) }
            }
        }
        if (overrideLocationId > 0L) {
            loadForLocation(overrideLocationId)
        } else {
            loadWeather()
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────

    private fun observeSettings() {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        particlesEnabled = settings.particlesEnabled,
                    )
                }
            }
        }
    }

    // ── Saved Locations (for HorizontalPager) ────────────────────────────

    private fun observeSavedLocations() {
        viewModelScope.launch {
            locationRepository.savedLocations.collect { locations ->
                var recoveryLocation: SavedLocationEntity? = null
                var shouldReloadGps = false
                _uiState.update { state ->
                    val fallback = locations.firstOrNull { it.isCurrentLocation } ?: locations.firstOrNull()
                    val trackedLocationMissing = activeLocationId != null && locations.none { it.id == activeLocationId }
                    val nextCurrentPage = when {
                        trackedLocationMissing && fallback != null -> locations.indexOfFirst { it.id == fallback.id }.coerceAtLeast(0)
                        locations.isEmpty() -> 0
                        else -> state.currentPage.coerceIn(0, locations.lastIndex)
                    }

                    if (trackedLocationMissing) {
                        useGpsLocation = fallback?.isCurrentLocation != false
                        activeLocationId = fallback?.takeUnless { it.isCurrentLocation }?.id
                        activeLocationName = fallback?.takeUnless { it.isCurrentLocation }?.name
                        if (useGpsLocation) {
                            shouldReloadGps = true
                        } else {
                            recoveryLocation = fallback
                        }
                    }

                    state.copy(
                        savedLocations = locations.toImmutableList(),
                        currentPage = nextCurrentPage,
                    )
                }

                when {
                    recoveryLocation != null -> {
                        val location = recoveryLocation ?: return@collect
                        loadWeatherForCoords(location.latitude, location.longitude, location.id)
                    }
                    shouldReloadGps -> loadWeather()
                }
            }
        }
    }

    private fun observeLastLocation() {
        viewModelScope.launch {
            prefs.lastLocation.collect { lastLocation ->
                _uiState.update { it.copy(lastLocationName = lastLocation?.name) }
            }
        }
    }

    /**
     * Called when the user swipes to a different location page.
     * Loads weather for that location.
     */
    fun onPageChanged(pageIndex: Int) {
        val locations = _uiState.value.savedLocations
        if (pageIndex < 0 || pageIndex >= locations.size) return
        val loc = locations[pageIndex]
        _uiState.update { it.copy(currentPage = pageIndex) }
        Log.d(TAG, "onPageChanged: page=$pageIndex, loc=${loc.name}, isCurrent=${loc.isCurrentLocation}")
        if (loc.isCurrentLocation) {
            activeLocationId = null
            activeLocationName = null
            useGpsLocation = true
            loadWeather()
        } else {
            activeLocationId = loc.id
            activeLocationName = loc.name
            useGpsLocation = false
            loadWeatherForCoords(loc.latitude, loc.longitude, loc.id, loc.name)
        }
    }

    // ── Weather Loading ──────────────────────────────────────────────────

    fun loadWeather() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, needsLocationPermission = false) }

                if (!locationProvider.hasLocationPermission) {
                    val hasCached = tryLoadCached()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLocationPermission = true,
                            error = if (!hasCached) "Location permission required to show weather." else null,
                        )
                    }
                    return@launch
                }

                val result = locationProvider.getCurrentLocation()
                var location: android.location.Location? = null
                var lastError: Throwable? = null
                result.fold(
                    onSuccess = { location = it },
                    onFailure = { lastError = it }
                )

                val resolvedLocation = location
                if (resolvedLocation != null) {
                    fetchWeather(resolvedLocation.latitude, resolvedLocation.longitude)
                } else {
                    val cached = tryLoadCached()
                    if (!cached) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = userFriendlyError(lastError),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadWeather: error", e)
                if (!tryLoadCached()) {
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = userFriendlyError(e))
                    }
                }
            }
        }
    }

    fun loadWeatherForCoords(
        lat: Double,
        lon: Double,
        locationId: Long? = activeLocationId,
        locationName: String? = activeLocationName,
    ) {
        viewModelScope.launch {
            activeLocationId = locationId
            activeLocationName = locationName
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchWeather(lat, lon, locationName)
        }
    }

    private suspend fun fetchWeather(lat: Double, lon: Double, locationName: String? = activeLocationName) {
        try {
            activeLatitude = lat
            activeLongitude = lon

            val result = repository.getWeather(lat, lon, locationName)
            result.fold(
                onSuccess = { data ->
                    prefs.saveLastLocation(lat, lon, data.location.name)
                    if (useGpsLocation) {
                        locationRepository.ensureCurrentLocation(lat, lon, data.location.name)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            weatherData = data,
                            error = null,
                            needsLocationPermission = false,
                            isCached = false,
                        )
                    }
                    // Run independent sub-fetches in parallel
                    coroutineScope {
                        launch { fetchAlerts(lat, lon) }
                        launch { fetchAirQuality(lat, lon) }
                        launch { fetchOnThisDay(lat, lon) }
                        launch { fetchAstronomy(data) }
                        launch { fetchRadarPreview(lat, lon) }
                        launch { fetchNowcast(lat, lon) }
                        launch { wearSyncManager.syncWeather(data) }
                    }
                    // Yesterday comparison must complete before derived data computation
                    fetchYesterdayComparison(lat, lon)
                    computeDerivedData(data)
                },
                onFailure = { e ->
                    Log.e(TAG, "fetchWeather: failed: ${e.message}")
                    if (!tryLoadCached()) {
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, error = userFriendlyError(e))
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchWeather: unexpected", e)
            _uiState.update {
                it.copy(isLoading = false, isRefreshing = false, error = userFriendlyError(e))
            }
        }
    }

    private suspend fun fetchAlerts(lat: Double, lon: Double) {
        try {
            weatherSourceManager.getAlerts(lat, lon).fold(
                onSuccess = { _uiState.update { s -> s.copy(alerts = it.toImmutableList()) } },
                onFailure = { _uiState.update { s -> s.copy(alerts = persistentListOf()) } }
            )
        } catch (_: Exception) {
            _uiState.update { it.copy(alerts = persistentListOf()) }
        }
    }

    private suspend fun fetchAirQuality(lat: Double, lon: Double) {
        try {
            weatherSourceManager.getAirQuality(lat, lon).fold(
                onSuccess = { _uiState.update { s -> s.copy(airQuality = it) } },
                onFailure = { _uiState.update { s -> s.copy(airQuality = null) } }
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchAirQuality failed", e)
            _uiState.update { it.copy(airQuality = null) }
        }
    }

    private fun fetchAstronomy(data: WeatherData) {
        try {
            val astronomy = airQualityRepository.getAstronomy(data.current.sunrise, data.current.sunset)
            _uiState.update { it.copy(astronomy = astronomy) }
        } catch (e: Exception) { Log.w(TAG, "fetchAstronomy failed", e) }
    }

    private suspend fun fetchRadarPreview(lat: Double, lon: Double) {
        try {
            radarRepository.getRadarFrames().fold(
                onSuccess = { frameSet ->
                    val latestFrame = frameSet.past.lastOrNull() ?: return@fold
                    val zoom = 6
                    val (tileX, tileY) = latLonToTile(lat, lon, zoom)
                    val radarUrl = latestFrame.tileUrl
                        .replace("{z}", zoom.toString())
                        .replace("{x}", tileX.toString())
                        .replace("{y}", tileY.toString())
                    val baseUrl = "https://basemaps.cartocdn.com/dark_all/$zoom/$tileX/$tileY@2x.png"
                    _uiState.update {
                        it.copy(radarPreviewTileUrl = radarUrl, radarBaseMapUrl = baseUrl)
                    }
                },
                onFailure = { /* keep placeholder */ }
            )
        } catch (e: Exception) { Log.w(TAG, "fetchRadarPreview failed", e) }
    }

    // ── Nowcast (minutely precipitation) ────────────────────────────────

    private suspend fun fetchNowcast(lat: Double, lon: Double) {
        try {
            repository.getMinutelyPrecipitation(lat, lon).fold(
                onSuccess = { data ->
                    _uiState.update { it.copy(nowcastData = data.toImmutableList()) }
                },
                onFailure = { Log.w(TAG, "fetchNowcast failed: ${it.message}") }
            )
        } catch (e: Exception) { Log.w(TAG, "fetchNowcast failed", e) }
    }

    // ── On This Day (historical same-date snapshot) ──────────────────────

    private suspend fun fetchOnThisDay(lat: Double, lon: Double) {
        try {
            val data = onThisDayRepository.getOnThisDay(lat, lon)
            _uiState.update { it.copy(onThisDay = data) }
        } catch (e: Exception) {
            Log.w(TAG, "fetchOnThisDay failed: ${e.message}")
            // Keep any previously cached state; only overwrite with null on first try.
            if (_uiState.value.onThisDay == null) {
                _uiState.update { it.copy(onThisDay = null) }
            }
        }
    }

    // ── Yesterday Comparison ──────────────────────────────────────────────

    private suspend fun fetchYesterdayComparison(lat: Double, lon: Double) {
        try {
            repository.getYesterdayWeather(lat, lon).fold(
                onSuccess = { yesterday ->
                    val high = yesterday?.temperatureHigh
                    _uiState.update { it.copy(yesterdayHigh = high) }
                },
                onFailure = { Log.w(TAG, "fetchYesterdayComparison failed: ${it.message}") }
            )
        } catch (e: Exception) { Log.w(TAG, "fetchYesterdayComparison failed", e) }
    }

    // ── Derived Computations (summary, scores, alerts) ────────────────────

    private suspend fun computeDerivedData(data: WeatherData) {
        val settings = _uiState.value.settings
        val currentState = _uiState.value
        val derived = withContext(defaultDispatcher) {
            val templateSummary = WeatherSummaryEngine.generate(
                current = data.current,
                today = data.daily.firstOrNull(),
                hourly = data.hourly,
                yesterdayHigh = currentState.yesterdayHigh,
                s = settings,
            )

            val score = WeatherFormatter.outdoorActivityScore(
                tempCelsius = data.current.temperature,
                humidity = data.current.humidity,
                windKmh = data.current.windSpeed,
                uvIndex = data.current.uvIndex,
                precipProbability = data.daily.firstOrNull()?.precipitationProbability ?: 0,
                aqi = currentState.airQuality?.usAqi,
            )

            val drivingAlerts = if (settings.drivingAlerts) {
                DrivingConditionEvaluator.evaluate(data.current)
            } else {
                persistentListOf()
            }

            val healthAlerts = if (settings.healthAlertsEnabled) {
                HealthAlertEvaluator.evaluate(
                    hourly = data.hourly,
                    pressureThresholdHpa = settings.migrainePressureThreshold,
                    enableMigraine = settings.migraineAlerts,
                )
            } else {
                persistentListOf()
            }

            val clothingSuggestions = ClothingSuggestionEvaluator.evaluate(data.current)
            val petSafetyAlerts = PetSafetyEvaluator.evaluate(data.current)

            val goldenHour = WeatherFormatter.goldenHourTimes(
                data.current.sunrise, data.current.sunset, settings,
            )

            DerivedWeatherState(
                weatherSummary = templateSummary,
                outdoorScore = score,
                drivingAlerts = drivingAlerts.toImmutableList(),
                healthAlerts = healthAlerts.toImmutableList(),
                clothingSuggestions = clothingSuggestions.toImmutableList(),
                petSafetyAlerts = petSafetyAlerts.toImmutableList(),
                goldenHourTimes = goldenHour,
            )
        }

        // Single state update — triggers one recomposition instead of many
        _uiState.update {
            it.copy(
                weatherSummary = derived.weatherSummary,
                outdoorScore = derived.outdoorScore,
                drivingAlerts = derived.drivingAlerts,
                healthAlerts = derived.healthAlerts,
                clothingSuggestions = derived.clothingSuggestions,
                petSafetyAlerts = derived.petSafetyAlerts,
                goldenHourTimes = derived.goldenHourTimes,
            )
        }

        // If AI style is selected, launch async generation and update when ready
        if (settings.summaryStyle == SummaryStyle.AI_GENERATED && summaryEngine.isAvailable()) {
            viewModelScope.launch {
                val aiSummary = withContext(defaultDispatcher) {
                    WeatherSummaryEngine.generateWithStyle(
                        current = data.current,
                        today = data.daily.firstOrNull(),
                        hourly = data.hourly,
                        yesterdayHigh = _uiState.value.yesterdayHigh,
                        s = settings,
                        aiEngine = summaryEngine,
                    )
                }
                _uiState.update { it.copy(weatherSummary = aiSummary) }
            }
        }

        // Persistent weather notification
        if (settings.persistentWeatherNotif) {
            com.sysadmindoc.nimbus.util.WeatherNotificationHelper.showOrUpdate(appContext, data, settings)
        }
    }

    private fun userFriendlyError(error: Throwable?): String {
        val message = error?.message?.trim()
        return when {
            error is SecurityException -> "Location permission required to show weather."
            message.equals("Location services are turned off.", ignoreCase = true) ->
                "Turn on location services to load local weather."
            message?.contains("Unable to determine location", ignoreCase = true) == true ->
                "Unable to determine location. Move outdoors or check location services, then try again."
            error is java.net.UnknownHostException -> "No internet connection"
            error is java.net.SocketTimeoutException -> "Server took too long to respond"
            error is java.net.ConnectException -> "Could not connect to weather service"
            error is retrofit2.HttpException -> "Weather service error (${error.code()})"
            else -> "Something went wrong. Please try again."
        }
    }

    private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return Pair(x, y)
    }

    private suspend fun tryLoadCached(): Boolean {
        return try {
            val lastLoc = prefs.lastLocation.first() ?: return false
            val cached = repository.getCachedWeather(lastLoc.latitude, lastLoc.longitude)
            if (cached != null) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, weatherData = cached, error = null, isCached = true)
                }
                true
            } else false
        } catch (_: Exception) { false }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val lat = activeLatitude
            val lon = activeLongitude
            if (!useGpsLocation && lat != null && lon != null) {
                fetchWeather(lat, lon)
            } else {
                loadWeather()
            }
        }
    }

    fun useLastLocation() {
        viewModelScope.launch {
            val lastLoc = prefs.lastLocation.first()
            if (lastLoc == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Choose a location to get started.",
                    )
                }
                return@launch
            }

            useGpsLocation = false
            activeLocationId = null
            activeLocationName = lastLoc.name
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    needsLocationPermission = false,
                )
            }
            fetchWeather(lastLoc.latitude, lastLoc.longitude)
        }
    }

    fun loadForLocation(locationId: Long) {
        viewModelScope.launch {
            try {
                val loc = locationRepository.getAll().find { it.id == locationId }
                _uiState.update { state ->
                    state.copy(
                        isLoading = true,
                        error = null,
                        currentPage = loc?.let { found ->
                            state.savedLocations.indexOfFirst { it.id == found.id }
                                .takeIf { it >= 0 }
                                ?: state.currentPage
                        } ?: state.currentPage,
                    )
                }
                if (loc != null) {
                    if (loc.isCurrentLocation) {
                        activeLocationId = null
                        activeLocationName = null
                        useGpsLocation = true
                        loadWeather()
                    } else {
                        activeLocationId = loc.id
                        activeLocationName = loc.name
                        useGpsLocation = false
                        fetchWeather(loc.latitude, loc.longitude, loc.name)
                    }
                } else {
                    activeLocationId = null
                    activeLocationName = null
                    useGpsLocation = true
                    loadWeather()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = userFriendlyError(e)) }
            }
        }
    }

    fun onPermissionGranted() {
        _uiState.update { it.copy(needsLocationPermission = false) }
        viewModelScope.launch { delay(300); loadWeather() }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(needsLocationPermission = true, isLoading = false, error = "Location permission required.")
        }
    }
}

@Stable
data class MainUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val weatherData: WeatherData? = null,
    val error: String? = null,
    val needsLocationPermission: Boolean = false,
    val particlesEnabled: Boolean = true,
    val isCached: Boolean = false,
    val alerts: ImmutableList<WeatherAlert> = persistentListOf(),
    val airQuality: AirQualityData? = null,
    val astronomy: AstronomyData? = null,
    val settings: NimbusSettings = NimbusSettings(),
    val savedLocations: ImmutableList<SavedLocationEntity> = persistentListOf(),
    val currentPage: Int = 0,
    val lastLocationName: String? = null,
    val radarPreviewTileUrl: String? = null,
    val radarBaseMapUrl: String? = null,
    // Phase 1-2 additions
    val weatherSummary: String = "",
    val yesterdayHigh: Double? = null,
    val nowcastData: ImmutableList<MinutelyPrecipitation> = persistentListOf(),
    val outdoorScore: Int = 0,
    val drivingAlerts: ImmutableList<DrivingAlert> = persistentListOf(),
    val healthAlerts: ImmutableList<HealthAlert> = persistentListOf(),
    val clothingSuggestions: ImmutableList<ClothingSuggestion> = persistentListOf(),
    val petSafetyAlerts: ImmutableList<PetSafetyAlert> = persistentListOf(),
    val goldenHourTimes: Pair<String, String>? = null,
    val isOffline: Boolean = false,
    val onThisDay: com.sysadmindoc.nimbus.data.model.OnThisDayData? = null,
)

private data class DerivedWeatherState(
    val weatherSummary: String,
    val outdoorScore: Int,
    val drivingAlerts: ImmutableList<DrivingAlert>,
    val healthAlerts: ImmutableList<HealthAlert>,
    val clothingSuggestions: ImmutableList<ClothingSuggestion>,
    val petSafetyAlerts: ImmutableList<PetSafetyAlert>,
    val goldenHourTimes: Pair<String, String>?,
)
