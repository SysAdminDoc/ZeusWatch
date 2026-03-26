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
import com.sysadmindoc.nimbus.util.DrivingAlert
import com.sysadmindoc.nimbus.util.DrivingConditionEvaluator
import com.sysadmindoc.nimbus.util.HealthAlert
import com.sysadmindoc.nimbus.util.HealthAlertEvaluator
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.WeatherSummaryEngine
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import com.sysadmindoc.nimbus.data.repository.AirQualityRepository
import com.sysadmindoc.nimbus.data.repository.AlertRepository
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.RadarRepository
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
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val alertRepository: AlertRepository,
    private val airQualityRepository: AirQualityRepository,
    private val radarRepository: RadarRepository,
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
                    fetchRadarPreview(lat, lon)
                    fetchNowcast(lat, lon)
                    // Yesterday comparison must complete before derived data computation
                    fetchYesterdayComparison(lat, lon)
                    computeDerivedData(data)
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

    private fun fetchRadarPreview(lat: Double, lon: Double) {
        viewModelScope.launch {
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
            } catch (_: Exception) {}
        }
    }

    // ── Nowcast (minutely precipitation) ────────────────────────────────

    private fun fetchNowcast(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                repository.getMinutelyPrecipitation(lat, lon).fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(nowcastData = data) }
                    },
                    onFailure = { _uiState.update { it.copy(nowcastData = emptyList()) } }
                )
            } catch (_: Exception) {}
        }
    }

    // ── Yesterday Comparison ──────────────────────────────────────────────

    private suspend fun fetchYesterdayComparison(lat: Double, lon: Double) {
        try {
            repository.getYesterdayWeather(lat, lon).fold(
                onSuccess = { yesterday ->
                    _uiState.update { it.copy(yesterdayHigh = yesterday?.temperatureHigh) }
                },
                onFailure = {}
            )
        } catch (_: Exception) {}
    }

    // ── Derived Computations (summary, scores, alerts) ────────────────────

    private fun computeDerivedData(data: WeatherData) {
        val settings = _uiState.value.settings

        // Weather summary
        val summary = WeatherSummaryEngine.generate(
            current = data.current,
            today = data.daily.firstOrNull(),
            hourly = data.hourly,
            yesterdayHigh = _uiState.value.yesterdayHigh,
            s = settings,
        )
        _uiState.update { it.copy(weatherSummary = summary) }

        // Outdoor activity score
        val score = WeatherFormatter.outdoorActivityScore(
            tempCelsius = data.current.temperature,
            humidity = data.current.humidity,
            windKmh = data.current.windSpeed,
            uvIndex = data.current.uvIndex,
            precipProbability = data.daily.firstOrNull()?.precipitationProbability ?: 0,
            aqi = _uiState.value.airQuality?.usAqi,
        )
        _uiState.update { it.copy(outdoorScore = score) }

        // Driving condition alerts
        val drivingAlerts = DrivingConditionEvaluator.evaluate(data.current)
        _uiState.update { it.copy(drivingAlerts = drivingAlerts) }

        // Health alerts
        val healthAlerts = HealthAlertEvaluator.evaluate(
            hourly = data.hourly,
            pressureThresholdHpa = settings.migrainePressureThreshold,
        )
        _uiState.update { it.copy(healthAlerts = healthAlerts) }

        // Golden hour times
        val goldenHour = WeatherFormatter.goldenHourTimes(
            data.current.sunrise, data.current.sunset, settings,
        )
        _uiState.update { it.copy(goldenHourTimes = goldenHour) }
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
    val radarPreviewTileUrl: String? = null,
    val radarBaseMapUrl: String? = null,
    // Phase 1-2 additions
    val weatherSummary: String = "",
    val yesterdayHigh: Double? = null,
    val nowcastData: List<MinutelyPrecipitation> = emptyList(),
    val outdoorScore: Int = 0,
    val drivingAlerts: List<DrivingAlert> = emptyList(),
    val healthAlerts: List<HealthAlert> = emptyList(),
    val goldenHourTimes: Pair<String, String>? = null,
)
