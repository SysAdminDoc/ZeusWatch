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

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            locationRepository.savedLocations.collect { locations ->
                syncSavedLocations(locations)
            }
        }
    }

    fun selectLocation1(location: SavedLocationEntity) {
        _uiState.update { it.copy(location1 = location, weather1 = null) }
        fetchWeather(location, isFirst = true)
    }

    fun selectLocation2(location: SavedLocationEntity) {
        _uiState.update { it.copy(location2 = location, weather2 = null) }
        fetchWeather(location, isFirst = false)
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
        val loc1 = _uiState.value.location1
        val loc2 = _uiState.value.location2
        if (loc1 != null) fetchWeather(loc1, isFirst = true)
        if (loc2 != null) fetchWeather(loc2, isFirst = false)
    }

    private fun syncSavedLocations(locations: List<SavedLocationEntity>) {
        val validIds = locations.map { it.id }.toSet()
        val preferredPrimary = locations.firstOrNull { it.isCurrentLocation } ?: locations.firstOrNull()
        var fetchPrimary: SavedLocationEntity? = null
        var fetchSecondary: SavedLocationEntity? = null

        _uiState.update { state ->
            val primary = state.location1?.takeIf { it.id in validIds } ?: preferredPrimary
            val secondary = state.location2?.takeIf { it.id in validIds && it.id != primary?.id }
                ?: locations.firstOrNull { it.id != primary?.id }

            if (state.location1?.id != primary?.id) fetchPrimary = primary
            if (state.location2?.id != secondary?.id) fetchSecondary = secondary

            state.copy(
                savedLocations = locations,
                location1 = primary,
                location2 = secondary,
                weather1 = if (state.location1?.id == primary?.id) state.weather1 else null,
                weather2 = if (state.location2?.id == secondary?.id) state.weather2 else null,
            )
        }

        fetchPrimary?.let { fetchWeather(it, isFirst = true) }
        fetchSecondary?.let { fetchWeather(it, isFirst = false) }
    }

    private fun fetchWeather(location: SavedLocationEntity, isFirst: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            weatherRepository.getWeather(
                location.latitude,
                location.longitude,
                location.name,
            ).fold(
                onSuccess = { data ->
                    _uiState.update {
                        if (isFirst) it.copy(weather1 = data, isLoading = false)
                        else it.copy(weather2 = data, isLoading = false)
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load weather") }
                }
            )
        }
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
