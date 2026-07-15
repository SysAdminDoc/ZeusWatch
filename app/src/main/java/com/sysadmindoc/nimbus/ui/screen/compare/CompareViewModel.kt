package com.sysadmindoc.nimbus.ui.screen.compare

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherDataType
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import com.sysadmindoc.nimbus.data.repository.sourceOverrides
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CompareViewModel"

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val weatherSourceManager: WeatherSourceManager,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private enum class Slot { PRIMARY, SECONDARY }

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    // @Volatile: incremented on Main.immediate but read from background fetch
    // continuations — ensures those reads see the latest token (stale-result guard).
    @Volatile private var requestTokenCounter = 0L
    @Volatile private var primaryRequestToken = 0L
    @Volatile private var secondaryRequestToken = 0L
    @Volatile private var overlayRequestToken = 0L
    private var activeLoads = 0

    init {
        viewModelScope.launch {
            userPreferences.settings.collect { settings ->
                applyPersistedOverlayVisibility(settings.showCompareChartOverlay)
            }
        }
        viewModelScope.launch {
            locationRepository.savedLocations.collect { locations ->
                syncSavedLocations(locations)
            }
        }
    }

    fun selectLocation1(location: SavedLocationEntity) {
        primaryRequestToken = consumeRequestToken()
        overlayRequestToken = consumeRequestToken()
        _uiState.update {
            it.copy(
                location1 = location,
                weather1 = null,
                failedLocation1 = null,
                overlayForecasts = emptyList(),
                isOverlayLoading = false,
                overlayUnavailable = false,
                overlayLoadFailed = false,
            )
        }
        fetchWeather(location, Slot.PRIMARY, primaryRequestToken)
    }

    fun selectLocation2(location: SavedLocationEntity) {
        secondaryRequestToken = consumeRequestToken()
        _uiState.update { it.copy(location2 = location, weather2 = null, failedLocation2 = null) }
        fetchWeather(location, Slot.SECONDARY, secondaryRequestToken)
    }

    fun retry() {
        _uiState.update {
            it.copy(
                failedLocation1 = null,
                failedLocation2 = null,
                overlayForecasts = emptyList(),
                isOverlayLoading = false,
                overlayUnavailable = false,
                overlayLoadFailed = false,
            )
        }
        val loc1 = _uiState.value.location1
        val loc2 = _uiState.value.location2
        if (loc1 != null) {
            primaryRequestToken = consumeRequestToken()
            fetchWeather(loc1, Slot.PRIMARY, primaryRequestToken)
        }
        if (loc2 != null) {
            secondaryRequestToken = consumeRequestToken()
            fetchWeather(loc2, Slot.SECONDARY, secondaryRequestToken)
        }
    }

    fun setChartOverlayVisible(visible: Boolean) {
        _uiState.update { it.copy(showChartOverlay = visible) }
        viewModelScope.launch { userPreferences.setShowCompareChartOverlay(visible) }
        if (visible) fetchChartOverlayIfMissing()
    }

    fun retryChartOverlay() {
        val state = _uiState.value
        val location = state.location1 ?: return
        val weather = state.weather1 ?: return
        if (!state.showChartOverlay || state.isOverlayLoading) return
        fetchChartOverlay(location, weather, primaryRequestToken)
    }

    private fun applyPersistedOverlayVisibility(visible: Boolean) {
        if (_uiState.value.showChartOverlay == visible) return
        _uiState.update { it.copy(showChartOverlay = visible) }
        if (visible) fetchChartOverlayIfMissing()
    }

    /** Lazy fetch for the case where the overlay was toggled on after weather loaded. */
    private fun fetchChartOverlayIfMissing() {
        val state = _uiState.value
        val location = state.location1 ?: return
        val weather = state.weather1 ?: return
        if (state.isOverlayLoading || state.overlayUnavailable) return
        if (state.overlayForecasts.size >= 2) return
        fetchChartOverlay(location, weather, primaryRequestToken)
    }

    private fun syncSavedLocations(locations: List<SavedLocationEntity>) {
        val validIds = locations.map { it.id }.toSet()
        val preferredPrimary = locations.firstOrNull { it.isCurrentLocation } ?: locations.firstOrNull()

        // Compute new assignments BEFORE the CAS-retryable update lambda
        val currentState = _uiState.value
        val primary = currentState.location1?.takeIf { it.id in validIds } ?: preferredPrimary
        val secondary = currentState.location2?.takeIf { it.id in validIds && it.id != primary?.id }
            ?: locations.firstOrNull { it.id != primary?.id }

        var fetchPrimary: Pair<SavedLocationEntity, Long>? = null
        var fetchSecondary: Pair<SavedLocationEntity, Long>? = null

        if (currentState.location1?.id != primary?.id && primary != null) {
            primaryRequestToken = consumeRequestToken()
            overlayRequestToken = consumeRequestToken()
            fetchPrimary = primary to primaryRequestToken
        }
        if (currentState.location2?.id != secondary?.id && secondary != null) {
            secondaryRequestToken = consumeRequestToken()
            fetchSecondary = secondary to secondaryRequestToken
        }

        _uiState.update { state ->
            val keepOverlay = state.location1?.id == primary?.id
            state.copy(
                savedLocations = locations,
                location1 = primary,
                location2 = secondary,
                weather1 = if (state.location1?.id == primary?.id) state.weather1 else null,
                weather2 = if (state.location2?.id == secondary?.id) state.weather2 else null,
                failedLocation1 = state.failedLocation1?.takeIf { locations.isNotEmpty() && it.id == primary?.id },
                failedLocation2 = state.failedLocation2?.takeIf { locations.isNotEmpty() && it.id == secondary?.id },
                overlayForecasts = if (keepOverlay) state.overlayForecasts else emptyList(),
                isOverlayLoading = if (keepOverlay) state.isOverlayLoading else false,
                overlayUnavailable = if (keepOverlay) state.overlayUnavailable else false,
                overlayLoadFailed = if (keepOverlay) state.overlayLoadFailed else false,
            )
        }

        fetchPrimary?.let { (location, token) -> fetchWeather(location, Slot.PRIMARY, token) }
        fetchSecondary?.let { (location, token) -> fetchWeather(location, Slot.SECONDARY, token) }
    }

    private fun fetchWeather(
        location: SavedLocationEntity,
        slot: Slot,
        requestToken: Long,
    ) {
        viewModelScope.launch {
            incrementLoading()
            try {
                weatherRepository.getWeather(location).fold(
                    onSuccess = { data ->
                        when (slot) {
                            Slot.PRIMARY -> {
                                if (requestToken == primaryRequestToken && _uiState.value.location1?.id == location.id) {
                                    _uiState.update {
                                        it.copy(weather1 = data, failedLocation1 = null)
                                    }
                                    // Don't burn network on an overlay the user has hidden;
                                    // setChartOverlayVisible fetches lazily on re-enable.
                                    if (_uiState.value.showChartOverlay) {
                                        fetchChartOverlay(location, data, requestToken)
                                    }
                                }
                            }
                            Slot.SECONDARY -> {
                                if (requestToken == secondaryRequestToken && _uiState.value.location2?.id == location.id) {
                                    _uiState.update {
                                        it.copy(weather2 = data, failedLocation2 = null)
                                    }
                                }
                            }
                        }
                    },
                    onFailure = {
                        _uiState.update { state ->
                            when (slot) {
                                Slot.PRIMARY -> {
                                    if (requestToken != primaryRequestToken || state.location1?.id != location.id) {
                                        state
                                    } else {
                                        state.copy(
                                            weather1 = null,
                                            failedLocation1 = location,
                                            overlayForecasts = emptyList(),
                                            isOverlayLoading = false,
                                            overlayUnavailable = false,
                                            overlayLoadFailed = false,
                                        )
                                    }
                                }
                                Slot.SECONDARY -> {
                                    if (requestToken != secondaryRequestToken || state.location2?.id != location.id) {
                                        state
                                    } else {
                                        state.copy(
                                            weather2 = null,
                                            failedLocation2 = location,
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            } finally {
                decrementLoading()
            }
        }
    }

    private fun fetchChartOverlay(
        location: SavedLocationEntity,
        primaryData: WeatherData,
        parentRequestToken: Long,
    ) {
        val token = consumeRequestToken()
        overlayRequestToken = token
        viewModelScope.launch {
            if (!isCurrentOverlayRequest(location, parentRequestToken, token)) return@launch
            _uiState.update {
                it.copy(
                    overlayForecasts = emptyList(),
                    isOverlayLoading = true,
                    overlayUnavailable = false,
                    overlayLoadFailed = false,
                )
            }

            val primaryProvider = compareOverlayPrimaryProvider(primaryData, location)
            val candidates = compareOverlayCandidates(primaryProvider)
            if (candidates.size < 2) {
                markOverlayUnavailable(location, parentRequestToken, token)
                return@launch
            }

            val forecasts = mutableListOf(
                CompareOverlayForecast(
                    provider = primaryProvider,
                    weather = primaryData.copy(sourceProvider = primaryProvider.displayName),
                )
            )
            var fetchFailureCount = 0
            val alternateForecasts = coroutineScope {
                candidates
                    .filterNot { it == primaryProvider }
                    .map { provider ->
                        async {
                            provider to weatherSourceManager.getWeatherFromProvider(
                                provider = provider,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                locationName = primaryData.location.name.ifBlank { location.name },
                                locationTimeZone = primaryData.location.timeZone ?: location.timeZone,
                            )
                        }
                    }
                    .awaitAll()
            }.mapNotNull { (provider, result) ->
                result.fold(
                    onSuccess = { CompareOverlayForecast(provider = provider, weather = it) },
                    onFailure = {
                        Log.d(TAG, "Compare overlay ${provider.displayName} unavailable: ${it.message}")
                        fetchFailureCount += 1
                        null
                    },
                )
            }
            forecasts += alternateForecasts
            val usableForecasts = forecasts.filter { it.weather.hourly.size >= 2 }

            if (!isCurrentOverlayRequest(location, parentRequestToken, token)) return@launch
            // Failed source fetches are retryable load errors; too few usable
            // forecasts without any fetch failure is a genuine capability gap.
            val notEnoughSources = usableForecasts.size < 2
            _uiState.update {
                it.copy(
                    overlayForecasts = usableForecasts,
                    isOverlayLoading = false,
                    overlayUnavailable = notEnoughSources && fetchFailureCount == 0,
                    overlayLoadFailed = notEnoughSources && fetchFailureCount > 0,
                )
            }
        }
    }

    private fun markOverlayUnavailable(
        location: SavedLocationEntity,
        parentRequestToken: Long,
        token: Long,
    ) {
        if (!isCurrentOverlayRequest(location, parentRequestToken, token)) return
        _uiState.update {
            it.copy(
                overlayForecasts = emptyList(),
                isOverlayLoading = false,
                overlayUnavailable = true,
                overlayLoadFailed = false,
            )
        }
    }

    private fun isCurrentOverlayRequest(
        location: SavedLocationEntity,
        parentRequestToken: Long,
        token: Long,
    ): Boolean = token == overlayRequestToken &&
        parentRequestToken == primaryRequestToken &&
        _uiState.value.location1?.id == location.id

    private fun consumeRequestToken(): Long = ++requestTokenCounter

    private fun incrementLoading() {
        activeLoads += 1
        _uiState.update { it.copy(isLoading = true) }
    }

    private fun decrementLoading() {
        activeLoads = (activeLoads - 1).coerceAtLeast(0)
        _uiState.update { it.copy(isLoading = activeLoads > 0) }
    }
}

@Stable
data class CompareOverlayForecast(
    val provider: WeatherSourceProvider,
    val weather: WeatherData,
) {
    val label: String get() = provider.displayName
}

@Stable
data class CompareUiState(
    val savedLocations: List<SavedLocationEntity> = emptyList(),
    val location1: SavedLocationEntity? = null,
    val location2: SavedLocationEntity? = null,
    val weather1: WeatherData? = null,
    val weather2: WeatherData? = null,
    val overlayForecasts: List<CompareOverlayForecast> = emptyList(),
    val isLoading: Boolean = false,
    val isOverlayLoading: Boolean = false,
    val overlayUnavailable: Boolean = false,
    val overlayLoadFailed: Boolean = false,
    val showChartOverlay: Boolean = true,
    val failedLocation1: SavedLocationEntity? = null,
    val failedLocation2: SavedLocationEntity? = null,
) {
    val hasError: Boolean get() = failedLocation1 != null || failedLocation2 != null
    val failedLocation: SavedLocationEntity? get() = failedLocation1 ?: failedLocation2
}

internal fun compareOverlayPrimaryProvider(
    data: WeatherData,
    location: SavedLocationEntity,
): WeatherSourceProvider {
    return WeatherSourceProvider.entries
        .firstOrNull { it.displayName == data.sourceProvider }
        ?.takeIf { it.isSelectableFor(WeatherDataType.FORECAST) }
        ?: location.sourceOverrides().forecast
        ?: WeatherSourceProvider.OPEN_METEO
}

internal fun compareOverlayCandidates(primaryProvider: WeatherSourceProvider): List<WeatherSourceProvider> =
    listOf(
        primaryProvider,
        WeatherSourceProvider.OPEN_METEO,
        WeatherSourceProvider.MET_NORWAY,
    )
        .filter { it.isSelectableFor(WeatherDataType.FORECAST) }
        .distinct()
        .take(3)
