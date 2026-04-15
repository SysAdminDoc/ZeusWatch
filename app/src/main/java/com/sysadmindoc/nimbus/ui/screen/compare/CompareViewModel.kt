package com.sysadmindoc.nimbus.ui.screen.compare

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private enum class Slot { PRIMARY, SECONDARY }

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    private var requestTokenCounter = 0L
    private var primaryRequestToken = 0L
    private var secondaryRequestToken = 0L
    private var activeLoads = 0

    init {
        viewModelScope.launch {
            locationRepository.savedLocations.collect { locations ->
                syncSavedLocations(locations)
            }
        }
    }

    fun selectLocation1(location: SavedLocationEntity) {
        primaryRequestToken = consumeRequestToken()
        _uiState.update { it.copy(location1 = location, weather1 = null, error = null) }
        fetchWeather(location, Slot.PRIMARY, primaryRequestToken)
    }

    fun selectLocation2(location: SavedLocationEntity) {
        secondaryRequestToken = consumeRequestToken()
        _uiState.update { it.copy(location2 = location, weather2 = null, error = null) }
        fetchWeather(location, Slot.SECONDARY, secondaryRequestToken)
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
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
            fetchPrimary = primary to primaryRequestToken
        }
        if (currentState.location2?.id != secondary?.id && secondary != null) {
            secondaryRequestToken = consumeRequestToken()
            fetchSecondary = secondary to secondaryRequestToken
        }

        _uiState.update { state ->
            state.copy(
                savedLocations = locations,
                location1 = primary,
                location2 = secondary,
                weather1 = if (state.location1?.id == primary?.id) state.weather1 else null,
                weather2 = if (state.location2?.id == secondary?.id) state.weather2 else null,
                error = if (locations.isEmpty()) null else state.error,
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
                weatherRepository.getWeather(
                    location.latitude,
                    location.longitude,
                    location.name,
                ).fold(
                    onSuccess = { data ->
                        _uiState.update { state ->
                            when (slot) {
                                Slot.PRIMARY -> {
                                    if (requestToken != primaryRequestToken || state.location1?.id != location.id) {
                                        state
                                    } else {
                                        state.copy(weather1 = data, error = null)
                                    }
                                }
                                Slot.SECONDARY -> {
                                    if (requestToken != secondaryRequestToken || state.location2?.id != location.id) {
                                        state
                                    } else {
                                        state.copy(weather2 = data, error = null)
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
                                            error = "Couldn't load weather for ${location.name}.",
                                        )
                                    }
                                }
                                Slot.SECONDARY -> {
                                    if (requestToken != secondaryRequestToken || state.location2?.id != location.id) {
                                        state
                                    } else {
                                        state.copy(
                                            weather2 = null,
                                            error = "Couldn't load weather for ${location.name}.",
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
data class CompareUiState(
    val savedLocations: List<SavedLocationEntity> = emptyList(),
    val location1: SavedLocationEntity? = null,
    val location2: SavedLocationEntity? = null,
    val weather1: WeatherData? = null,
    val weather2: WeatherData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
