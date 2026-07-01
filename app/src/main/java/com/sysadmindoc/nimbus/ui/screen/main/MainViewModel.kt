package com.sysadmindoc.nimbus.ui.screen.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.BuildConfig
import com.sysadmindoc.nimbus.data.location.LocationProvider
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.util.ClothingSuggestion
import com.sysadmindoc.nimbus.util.DrivingAlert
import com.sysadmindoc.nimbus.util.HealthAlert
import com.sysadmindoc.nimbus.util.PetSafetyAlert
import com.sysadmindoc.nimbus.util.ConnectivityObserver
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.data.repository.AuroraKpData
import com.sysadmindoc.nimbus.data.repository.FloodData
import com.sysadmindoc.nimbus.data.repository.MarineData
import com.sysadmindoc.nimbus.util.ActivityIndex
import com.sysadmindoc.nimbus.data.repository.CardType
import com.sysadmindoc.nimbus.data.repository.ClimateOutlookData
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandData
import com.sysadmindoc.nimbus.data.repository.ForecastAccuracyData
import com.sysadmindoc.nimbus.data.repository.ForecastEvolutionData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SourceOverrides
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.sourceOverrides
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "MainViewModel"

enum class MainUiErrorKind {
    LOCATION_PERMISSION_REQUIRED,
    LOCATION_PERMISSION_DENIED,
    LOCATION_SERVICES_OFF,
    LOCATION_UNAVAILABLE,
    NO_INTERNET,
    TIMEOUT,
    CONNECTION,
    WEATHER_SERVICE,
    GENERIC,
    CHOOSE_LOCATION,
}

@Stable
data class MainUiError(
    val kind: MainUiErrorKind,
    val serviceCode: Int? = null,
)

data class MainViewModelDependencies @Inject constructor(
    val repository: WeatherRepository,
    val locationRepository: LocationRepository,
    val locationProvider: LocationProvider,
    val prefs: UserPreferences,
    val connectivityObserver: ConnectivityObserver,
    val weatherLoadCoordinator: WeatherLoadCoordinator,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    dependencies: MainViewModelDependencies,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val repository = dependencies.repository
    private val locationRepository = dependencies.locationRepository
    private val locationProvider = dependencies.locationProvider
    private val prefs = dependencies.prefs
    private val connectivityObserver = dependencies.connectivityObserver
    private val weatherLoadCoordinator = dependencies.weatherLoadCoordinator
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val overrideLocationId: Long = savedStateHandle.get<Long>("locationId") ?: 0L

    /** Track active coordinates for refresh without reverting to GPS. */
    private var activeLatitude: Double? = null
    private var activeLongitude: Double? = null
    private var activeLocationId: Long? = null
    private var activeLocationName: String? = null
    private var activeSourceOverrides: SourceOverrides = SourceOverrides()
    private var activeCountryHint: String? = null
    private var useGpsLocation: Boolean = true
    private val weatherRequestCounter = AtomicLong(0L)

    init {
        Log.d(TAG, "init: overrideLocationId=$overrideLocationId")
        observeSettings()
        observeSavedLocations()
        observeLastLocation()
        observeLastSeenVersionCode()
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

    // Settings

    private fun observeSettings() {
        viewModelScope.launch {
            var previousSettings: NimbusSettings? = null
            prefs.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        particlesEnabled = settings.particlesEnabled,
                    )
                }
                val prior = previousSettings
                previousSettings = settings
                if (prior != null) {
                    handleForecastEvolutionSettingChange(prior, settings)
                }
                // Derived data (summary, golden hour, driving/health alerts)
                // bakes units and thresholds in at compute time - recompute it
                // when a relevant setting changes, otherwise it stays stale
                // until the next fetch. Skipped before the first fetch and when
                // the changed fields don't feed the derived computation.
                if (prior != null && derivedSettingsChanged(prior, settings)) {
                    val data = _uiState.value.weatherData ?: return@collect
                    try {
                        weatherLoadCoordinator.recomputeDerivedData(
                            data = data,
                            requestId = weatherRequestCounter.get(),
                            scope = viewModelScope,
                            currentState = { _uiState.value },
                            updateState = ::updateUiState,
                            isLatestRequest = ::isLatestWeatherRequest,
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Derived weather recomputation failed", e)
                    }
                }
            }
        }
    }

    /** True when a settings change affects coordinator-derived output. */
    private fun derivedSettingsChanged(old: NimbusSettings, new: NimbusSettings): Boolean =
        old.tempUnit != new.tempUnit ||
            old.windUnit != new.windUnit ||
            old.pressureUnit != new.pressureUnit ||
            old.precipUnit != new.precipUnit ||
            old.visibilityUnit != new.visibilityUnit ||
            old.timeFormat != new.timeFormat ||
            old.summaryStyle != new.summaryStyle ||
            old.drivingAlerts != new.drivingAlerts ||
            old.healthAlertsEnabled != new.healthAlertsEnabled ||
            old.migraineAlerts != new.migraineAlerts ||
            old.migrainePressureThreshold != new.migrainePressureThreshold

    private fun handleForecastEvolutionSettingChange(old: NimbusSettings, new: NimbusSettings) {
        val wasEnabled = old.isCardEnabled(CardType.FORECAST_EVOLUTION)
        val isEnabled = new.isCardEnabled(CardType.FORECAST_EVOLUTION)
        when {
            !wasEnabled && isEnabled -> {
                val lat = activeLatitude ?: return
                val lon = activeLongitude ?: return
                val requestId = weatherRequestCounter.get()
                viewModelScope.launch {
                    weatherLoadCoordinator.fetchForecastEvolution(
                        lat = lat,
                        lon = lon,
                        requestId = requestId,
                        updateState = ::updateUiState,
                        isLatestRequest = ::isLatestWeatherRequest,
                    )
                }
            }
            wasEnabled && !isEnabled -> {
                _uiState.update {
                    it.copy(
                        forecastEvolution = null,
                        isForecastEvolutionLoading = false,
                        forecastEvolutionUnavailable = false,
                    )
                }
            }
        }
    }

    // Saved locations for HorizontalPager

    private fun observeSavedLocations() {
        viewModelScope.launch {
            locationRepository.savedLocations.collect { locations ->
                // Compute recovery action OUTSIDE _uiState.update to avoid
                // side effects in the CAS-retryable lambda.
                val fallback = locations.firstOrNull { it.isCurrentLocation } ?: locations.firstOrNull()
                val fallbackLocation = fallback?.takeUnless { it.isCurrentLocation }
                val trackedLocationMissing = activeLocationId != null && locations.none { it.id == activeLocationId }

                if (trackedLocationMissing) {
                    useGpsLocation = fallback?.isCurrentLocation != false
                    activeLocationId = fallbackLocation?.id
                    activeLocationName = fallbackLocation?.name
                    activeCountryHint = fallbackLocation.countryHint()
                    activeSourceOverrides = fallbackLocation?.sourceOverrides() ?: SourceOverrides()
                }

                _uiState.update { state ->
                    val nextCurrentPage = when {
                        trackedLocationMissing && fallback != null -> locations.indexOfFirst { it.id == fallback.id }.coerceAtLeast(0)
                        locations.isEmpty() -> 0
                        else -> state.currentPage.coerceIn(0, locations.lastIndex)
                    }
                    state.copy(
                        savedLocations = locations.toImmutableList(),
                        currentPage = nextCurrentPage,
                    )
                }

                if (trackedLocationMissing) {
                    val recoveryLocation = fallback?.takeUnless { it.isCurrentLocation }
                    when {
                        recoveryLocation != null -> loadWeatherForCoords(
                            recoveryLocation.latitude, recoveryLocation.longitude,
                            recoveryLocation.id, recoveryLocation.name,
                            sourceOverrides = recoveryLocation.sourceOverrides(),
                            countryHint = recoveryLocation.countryHint(),
                        )
                        useGpsLocation -> loadWeather(clearDisplayedWeather = true)
                    }
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

    private fun observeLastSeenVersionCode() {
        viewModelScope.launch {
            prefs.lastSeenVersionCode.collect { versionCode ->
                _uiState.update { it.copy(lastSeenVersionCode = versionCode) }
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
            activeSourceOverrides = SourceOverrides()
            activeCountryHint = null
            useGpsLocation = true
            loadWeather(clearDisplayedWeather = true)
        } else {
            activeLocationId = loc.id
            activeLocationName = loc.name
            activeSourceOverrides = loc.sourceOverrides()
            activeCountryHint = loc.countryHint()
            useGpsLocation = false
            loadWeatherForCoords(
                loc.latitude,
                loc.longitude,
                loc.id,
                loc.name,
                sourceOverrides = loc.sourceOverrides(),
                countryHint = loc.countryHint(),
            )
        }
    }

    // Weather loading

    fun loadWeather(
        clearDisplayedWeather: Boolean = false,
        // Default is evaluated in the caller's frame, so the id is allocated
        // synchronously - a queued stale intent cannot out-id a newer one.
        requestId: Long = nextWeatherRequestId(),
    ) {
        viewModelScope.launch {
            try {
                if (!isLatestWeatherRequest(requestId)) return@launch

                useGpsLocation = true
                activeLocationId = null
                activeLocationName = null
                activeSourceOverrides = SourceOverrides()
                activeCountryHint = null

                beginWeatherLoad(clearDisplayedWeather)

                if (!locationProvider.hasLocationPermission) {
                    val hasCached = tryLoadCached(requestId)
                    if (!isLatestWeatherRequest(requestId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            needsLocationPermission = !hasCached,
                            error = if (!hasCached) {
                                MainUiError(MainUiErrorKind.LOCATION_PERMISSION_REQUIRED)
                            } else {
                                null
                            },
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

                if (!isLatestWeatherRequest(requestId)) return@launch

                val resolvedLocation = location
                if (resolvedLocation != null) {
                    fetchWeather(
                        lat = resolvedLocation.latitude,
                        lon = resolvedLocation.longitude,
                        requestId = requestId,
                    )
                } else {
                    val cached = tryLoadCached(requestId)
                    if (!isLatestWeatherRequest(requestId)) return@launch
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
                if (e is CancellationException) throw e
                Log.e(TAG, "loadWeather: error", e)
                if (!tryLoadCached(requestId) && isLatestWeatherRequest(requestId)) {
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
        sourceOverrides: SourceOverrides = activeSourceOverrides,
        countryHint: String? = activeCountryHint,
        requestId: Long = nextWeatherRequestId(),
    ) {
        val clearDisplayedWeather = shouldClearDisplayedWeatherFor(lat, lon)
        viewModelScope.launch {
            if (!isLatestWeatherRequest(requestId)) return@launch
            useGpsLocation = false
            activeLocationId = locationId
            activeLocationName = locationName
            activeSourceOverrides = sourceOverrides
            activeCountryHint = countryHint
            beginWeatherLoad(clearDisplayedWeather)
            fetchWeather(lat, lon, locationName, sourceOverrides, countryHint, requestId)
        }
    }

    private suspend fun fetchWeather(
        lat: Double,
        lon: Double,
        locationName: String? = activeLocationName,
        sourceOverrides: SourceOverrides = activeSourceOverrides,
        countryHint: String? = activeCountryHint,
        requestId: Long,
    ) {
        try {
            if (!isLatestWeatherRequest(requestId)) return
            activeLatitude = lat
            activeLongitude = lon

            val result = repository.getWeather(
                latitude = lat,
                longitude = lon,
                locationName = locationName,
                sourceOverrides = sourceOverrides,
            )
            if (!isLatestWeatherRequest(requestId)) return
            result.fold(
                onSuccess = { data ->
                    if (!isLatestWeatherRequest(requestId)) return@fold
                    try {
                        prefs.saveLastLocation(lat, lon, data.location.name)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist last location", e)
                    }
                    if (!isLatestWeatherRequest(requestId)) return@fold
                    if (useGpsLocation) {
                        try {
                            locationRepository.ensureCurrentLocation(lat, lon, data.location.name)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update current location entry", e)
                        }
                        try {
                            prefs.saveBackgroundAlertLocation(lat, lon, data.location.name)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to persist background alert location", e)
                        }
                    }
                    if (!isLatestWeatherRequest(requestId)) return@fold
                    val alertCountryHint = countryHint ?: data.location.country.ifBlank { null }
                    activeCountryHint = alertCountryHint
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
                    weatherLoadCoordinator.finishSuccessfulWeatherLoad(
                        completion = WeatherLoadCompletion(
                            lat = lat,
                            lon = lon,
                            requestId = requestId,
                            data = data,
                            sourceOverrides = sourceOverrides,
                            alertCountryHint = alertCountryHint,
                        ),
                        callbacks = WeatherLoadCallbacks(
                            scope = viewModelScope,
                            currentState = { _uiState.value },
                            updateState = ::updateUiState,
                            isLatestRequest = ::isLatestWeatherRequest,
                        ),
                    )
                },
                onFailure = { e ->
                    if (!isLatestWeatherRequest(requestId)) return@fold
                    Log.e(TAG, "fetchWeather: failed: ${e.message}")
                    if (!tryLoadCached(requestId, lat, lon) && isLatestWeatherRequest(requestId)) {
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, error = userFriendlyError(e))
                        }
                    }
                }
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isLatestWeatherRequest(requestId)) return
            Log.e(TAG, "fetchWeather: unexpected", e)
            _uiState.update {
                it.copy(isLoading = false, isRefreshing = false, error = userFriendlyError(e))
            }
        }
    }

    fun selectHistoricalDate(date: java.time.LocalDate) {
        viewModelScope.launch {
            val weather = _uiState.value.weatherData ?: return@launch
            weatherLoadCoordinator.selectHistoricalDate(date, weather, ::updateUiState)
        }
    }

    private fun updateUiState(transform: (MainUiState) -> MainUiState) {
        _uiState.update(transform)
    }

    private fun userFriendlyError(error: Throwable?): MainUiError {
        val message = error?.message?.trim()
        return when {
            error is SecurityException -> MainUiError(MainUiErrorKind.LOCATION_PERMISSION_REQUIRED)
            message.equals("Location services are turned off.", ignoreCase = true) ->
                MainUiError(MainUiErrorKind.LOCATION_SERVICES_OFF)
            message?.contains("Unable to determine location", ignoreCase = true) == true ->
                MainUiError(MainUiErrorKind.LOCATION_UNAVAILABLE)
            error is java.net.UnknownHostException -> MainUiError(MainUiErrorKind.NO_INTERNET)
            error is java.net.SocketTimeoutException -> MainUiError(MainUiErrorKind.TIMEOUT)
            error is java.net.ConnectException -> MainUiError(MainUiErrorKind.CONNECTION)
            error is retrofit2.HttpException -> MainUiError(MainUiErrorKind.WEATHER_SERVICE, error.code())
            else -> MainUiError(MainUiErrorKind.GENERIC)
        }
    }

    /**
     * Falls back to cached weather. When [lat]/[lon] are provided (a fetch for
     * a specific location failed), only that location's cache is consulted -
     * never [UserPreferences.lastLocation], which may point at a different
     * place and would render the wrong location's weather. The lastLocation
     * fallback is reserved for the no-coords paths (missing permission, GPS
     * resolution failure).
     */
    private suspend fun tryLoadCached(
        requestId: Long? = null,
        lat: Double? = null,
        lon: Double? = null,
    ): Boolean {
        return try {
            if (requestId != null && !isLatestWeatherRequest(requestId)) return false
            val cacheLat: Double
            val cacheLon: Double
            if (lat != null && lon != null) {
                cacheLat = lat
                cacheLon = lon
            } else {
                val lastLoc = prefs.lastLocation.first() ?: return false
                cacheLat = lastLoc.latitude
                cacheLon = lastLoc.longitude
            }
            val cached = repository.getCachedWeather(cacheLat, cacheLon)
            if (cached != null) {
                if (requestId == null || isLatestWeatherRequest(requestId)) {
                    activeLatitude = cacheLat
                    activeLongitude = cacheLon
                    _uiState.update { state ->
                        state.clearLocationScopedData().copy(
                            isLoading = false,
                            isRefreshing = false,
                            weatherData = cached,
                            error = null,
                            isCached = true,
                        )
                    }
                    true
                } else {
                    false
                }
            } else false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, error = null) }
        val lat = activeLatitude
        val lon = activeLongitude
        if (!useGpsLocation && lat != null && lon != null) {
            // Allocate the id synchronously so a queued stale intent can't
            // out-id this refresh once the coroutine actually runs.
            val requestId = nextWeatherRequestId()
            val sourceOverrides = activeSourceOverrides
            val countryHint = activeCountryHint
            viewModelScope.launch {
                fetchWeather(
                    lat = lat,
                    lon = lon,
                    locationName = activeLocationName,
                    sourceOverrides = sourceOverrides,
                    countryHint = countryHint,
                    requestId = requestId,
                )
            }
        } else {
            loadWeather()
        }
    }

    fun useLastLocation() {
        val requestId = nextWeatherRequestId()
        viewModelScope.launch {
            val lastLoc = prefs.lastLocation.first()
            if (lastLoc == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = MainUiError(MainUiErrorKind.CHOOSE_LOCATION),
                    )
                }
                return@launch
            }

            useGpsLocation = false
            activeLocationId = null
            activeSourceOverrides = SourceOverrides()
            activeCountryHint = null
            loadWeatherForCoords(
                lastLoc.latitude, lastLoc.longitude,
                locationId = null, locationName = lastLoc.name,
                sourceOverrides = SourceOverrides(),
                countryHint = null,
                requestId = requestId,
            )
        }
    }

    fun loadForLocation(locationId: Long) {
        val requestId = nextWeatherRequestId()
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
                        activeSourceOverrides = SourceOverrides()
                        activeCountryHint = null
                        useGpsLocation = true
                        loadWeather(clearDisplayedWeather = true, requestId = requestId)
                    } else {
                        loadWeatherForCoords(
                            loc.latitude,
                            loc.longitude,
                            loc.id,
                            loc.name,
                            sourceOverrides = loc.sourceOverrides(),
                            countryHint = loc.countryHint(),
                            requestId = requestId,
                        )
                    }
                } else {
                    activeLocationId = null
                    activeLocationName = null
                    activeSourceOverrides = SourceOverrides()
                    activeCountryHint = null
                    useGpsLocation = true
                    loadWeather(clearDisplayedWeather = true, requestId = requestId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            it.copy(
                needsLocationPermission = true,
                isLoading = false,
                isRefreshing = false,
                error = MainUiError(MainUiErrorKind.LOCATION_PERMISSION_DENIED),
            )
        }
    }

    fun dismissWhatsNew() {
        _uiState.update { it.copy(lastSeenVersionCode = BuildConfig.VERSION_CODE) }
        viewModelScope.launch {
            prefs.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        }
    }

    private fun nextWeatherRequestId(): Long = weatherRequestCounter.incrementAndGet()

    private fun isLatestWeatherRequest(requestId: Long): Boolean = requestId == weatherRequestCounter.get()

    private fun beginWeatherLoad(clearDisplayedWeather: Boolean) {
        _uiState.update { state ->
            val baseState = if (clearDisplayedWeather) state.clearLocationScopedData() else state
            baseState.copy(
                isLoading = true,
                error = null,
                needsLocationPermission = false,
                isCached = false,
            )
        }
    }

    private fun shouldClearDisplayedWeatherFor(lat: Double, lon: Double): Boolean {
        val currentLocation = _uiState.value.weatherData?.location ?: return false
        return !sameCoordinate(currentLocation.latitude, lat) || !sameCoordinate(currentLocation.longitude, lon)
    }

    private fun sameCoordinate(first: Double, second: Double, tolerance: Double = 0.0001): Boolean =
        abs(first - second) <= tolerance
}

private fun SavedLocationEntity?.countryHint(): String? = this?.country?.ifBlank { null }

@Stable
data class MainUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val weatherData: WeatherData? = null,
    val error: MainUiError? = null,
    val needsLocationPermission: Boolean = false,
    val particlesEnabled: Boolean = true,
    val isCached: Boolean = false,
    val alerts: ImmutableList<WeatherAlert> = persistentListOf(),
    val alertsUpdatedAt: LocalDateTime? = null,
    val alertsFetchFailed: Boolean = false,
    val airQuality: AirQualityData? = null,
    val airQualityUpdatedAt: LocalDateTime? = null,
    val airQualityFetchFailed: Boolean = false,
    val astronomy: AstronomyData? = null,
    val settings: NimbusSettings = NimbusSettings(),
    val savedLocations: ImmutableList<SavedLocationEntity> = persistentListOf(),
    val currentPage: Int = 0,
    val lastLocationName: String? = null,
    val radarPreviewTileUrl: String? = null,
    val radarBaseMapUrl: String? = null,
    val radarPreviewUpdatedAt: LocalDateTime? = null,
    val radarPreviewFetchFailed: Boolean = false,
    // Phase 1-2 additions
    val weatherSummary: String = "",
    val yesterdayHigh: Double? = null,
    val nowcastData: ImmutableList<MinutelyPrecipitation> = persistentListOf(),
    val outdoorScore: WeatherFormatter.OutdoorScoreBreakdown? = null,
    val drivingAlerts: ImmutableList<DrivingAlert> = persistentListOf(),
    val healthAlerts: ImmutableList<HealthAlert> = persistentListOf(),
    val clothingSuggestions: ImmutableList<ClothingSuggestion> = persistentListOf(),
    val petSafetyAlerts: ImmutableList<PetSafetyAlert> = persistentListOf(),
    val goldenHourTimes: Pair<String, String>? = null,
    val isOffline: Boolean = false,
    val onThisDay: com.sysadmindoc.nimbus.data.model.OnThisDayData? = null,
    val auroraKpData: AuroraKpData? = null,
    val activityIndices: ImmutableList<ActivityIndex> = persistentListOf(),
    val marineData: MarineData? = null,
    val floodData: FloodData? = null,
    val forecastEvolution: ForecastEvolutionData? = null,
    val isForecastEvolutionLoading: Boolean = false,
    val forecastEvolutionUnavailable: Boolean = false,
    val forecastAccuracy: ForecastAccuracyData? = null,
    val confidenceBands: ConfidenceBandData? = null,
    val climateOutlook: ClimateOutlookData? = null,
    val lastSeenVersionCode: Int = BuildConfig.VERSION_CODE,
)

private fun MainUiState.clearLocationScopedData(): MainUiState = copy(
    weatherData = null,
    alerts = persistentListOf(),
    alertsUpdatedAt = null,
    alertsFetchFailed = false,
    airQuality = null,
    airQualityUpdatedAt = null,
    airQualityFetchFailed = false,
    astronomy = null,
    isCached = false,
    radarPreviewTileUrl = null,
    radarBaseMapUrl = null,
    radarPreviewUpdatedAt = null,
    radarPreviewFetchFailed = false,
    weatherSummary = "",
    yesterdayHigh = null,
    nowcastData = persistentListOf(),
    outdoorScore = null,
    drivingAlerts = persistentListOf(),
    healthAlerts = persistentListOf(),
    clothingSuggestions = persistentListOf(),
    petSafetyAlerts = persistentListOf(),
    goldenHourTimes = null,
    onThisDay = null,
    auroraKpData = null,
    activityIndices = persistentListOf(),
    marineData = null,
    floodData = null,
    forecastEvolution = null,
    isForecastEvolutionLoading = false,
    forecastEvolutionUnavailable = false,
    forecastAccuracy = null,
    confidenceBands = null,
    climateOutlook = null,
)

private fun NimbusSettings.isCardEnabled(cardType: CardType): Boolean =
    cardType.name !in disabledCards
