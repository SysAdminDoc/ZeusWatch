package com.sysadmindoc.nimbus.ui.screen.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.location.LocationProvider
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.AirQualityRepository
import com.sysadmindoc.nimbus.data.repository.AlertRepository
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.Stable
import javax.inject.Inject

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val alertRepository: AlertRepository,
    private val airQualityRepository: AirQualityRepository,
    private val locationRepository: LocationRepository,
    private val locationProvider: LocationProvider,
    private val prefs: UserPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val overrideLocationId: Long = savedStateHandle.get<Long>("locationId") ?: 0L

    /** Track active coordinates for refresh without reverting to GPS. */
    private var activeLatitude: Double? = null
    private var activeLongitude: Double? = null
    private var useGpsLocation: Boolean = true

    init {
        Log.d(TAG, "init: overrideLocationId=$overrideLocationId")
        observeSettings()
        observeSavedLocations()
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
                _uiState.update { it.copy(savedLocations = locations) }
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
            useGpsLocation = true
            loadWeather()
        } else {
            useGpsLocation = false
            loadWeatherForCoords(loc.latitude, loc.longitude)
        }
    }

    // ── Weather Loading ──────────────────────────────────────────────────

    fun loadWeather() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                if (!locationProvider.hasLocationPermission) {
                    tryLoadCached()
                    _uiState.update { it.copy(isLoading = false, needsLocationPermission = true) }
                    return@launch
                }

                var location: android.location.Location? = null
                var lastError: Throwable? = null

                for (attempt in 1..3) {
                    val result = locationProvider.getCurrentLocation()
                    result.fold(
                        onSuccess = { location = it },
                        onFailure = { lastError = it }
                    )
                    if (location != null) break
                    if (attempt < 3) delay(1500L)
                }

                if (location != null) {
                    fetchWeather(location!!.latitude, location!!.longitude)
                } else {
                    val cached = tryLoadCached()
                    if (!cached) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = lastError?.message ?: "Unable to determine location.",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadWeather: error", e)
                if (!tryLoadCached()) {
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = "Error: ${e.message}")
                    }
                }
            }
        }
    }

    fun loadWeatherForCoords(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchWeather(lat, lon)
        }
    }

    private suspend fun fetchWeather(lat: Double, lon: Double) {
        try {
            activeLatitude = lat
            activeLongitude = lon

            val result = repository.getWeather(lat, lon)
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
                    fetchAlerts(lat, lon)
                    fetchAirQuality(lat, lon)
                    fetchAstronomy(data)
                },
                onFailure = { e ->
                    Log.e(TAG, "fetchWeather: failed: ${e.message}")
                    if (!tryLoadCached()) {
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Failed to load weather")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchWeather: unexpected", e)
            _uiState.update {
                it.copy(isLoading = false, isRefreshing = false, error = "Network error: ${e.message}")
            }
        }
    }

    private suspend fun fetchAlerts(lat: Double, lon: Double) {
        try {
            alertRepository.getAlerts(lat, lon).fold(
                onSuccess = { _uiState.update { s -> s.copy(alerts = it) } },
                onFailure = { _uiState.update { s -> s.copy(alerts = emptyList()) } }
            )
        } catch (_: Exception) {
            _uiState.update { it.copy(alerts = emptyList()) }
        }
    }

    private suspend fun fetchAirQuality(lat: Double, lon: Double) {
        try {
            airQualityRepository.getAirQuality(lat, lon).fold(
                onSuccess = { _uiState.update { s -> s.copy(airQuality = it) } },
                onFailure = { _uiState.update { s -> s.copy(airQuality = null) } }
            )
        } catch (_: Exception) { _uiState.update { it.copy(airQuality = null) } }
    }

    private fun fetchAstronomy(data: WeatherData) {
        try {
            val astronomy = airQualityRepository.getAstronomy(data.current.sunrise, data.current.sunset)
            _uiState.update { it.copy(astronomy = astronomy) }
        } catch (_: Exception) {}
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
            if (!useGpsLocation && activeLatitude != null && activeLongitude != null) {
                fetchWeather(activeLatitude!!, activeLongitude!!)
            } else {
                loadWeather()
            }
        }
    }

    fun loadForLocation(locationId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val loc = locationRepository.getAll().find { it.id == locationId }
                if (loc != null) {
                    if (loc.isCurrentLocation) { useGpsLocation = true; loadWeather() }
                    else { useGpsLocation = false; fetchWeather(loc.latitude, loc.longitude) }
                } else { useGpsLocation = true; loadWeather() }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
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
    val alerts: List<WeatherAlert> = emptyList(),
    val airQuality: AirQualityData? = null,
    val astronomy: AstronomyData? = null,
    val settings: NimbusSettings = NimbusSettings(),
    val savedLocations: List<SavedLocationEntity> = emptyList(),
    val currentPage: Int = 0,
)
