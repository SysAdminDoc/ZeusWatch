package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    val savedLocations: StateFlow<List<SavedLocationEntity>> =
        locationRepository.savedLocations.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    /** Cached temperatures keyed by location ID. */
    private val _locationTemps = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val locationTemps: StateFlow<Map<Long, Double>> = _locationTemps.asStateFlow()

    /** Cached weather codes keyed by location ID. */
    private val _locationConditions = MutableStateFlow<Map<Long, Pair<WeatherCode, Boolean>>>(emptyMap())
    val locationConditions: StateFlow<Map<Long, Pair<WeatherCode, Boolean>>> = _locationConditions.asStateFlow()

    private var searchJob: Job? = null
    private var searchRequestId = 0L

    init {
        // Load cached temperatures whenever saved locations change
        viewModelScope.launch {
            savedLocations.collect { locations ->
                loadCachedTemps(locations)
            }
        }
    }

    private suspend fun loadCachedTemps(locations: List<SavedLocationEntity>) {
        val temps = mutableMapOf<Long, Double>()
        val conditions = mutableMapOf<Long, Pair<WeatherCode, Boolean>>()
        locations.forEach { loc ->
            val cached = weatherRepository.getCachedWeather(loc.latitude, loc.longitude)
            if (cached != null) {
                temps[loc.id] = cached.current.temperature
                conditions[loc.id] = cached.current.weatherCode to cached.current.isDay
            }
        }
        _locationTemps.value = temps
        _locationConditions.value = conditions
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        val requestId = ++searchRequestId
        if (query.length < 2) {
            _searchState.value = SearchState(query = query)
            return
        }
        _searchState.value = SearchState(query = query, isSearching = true)
        searchJob = viewModelScope.launch {
            delay(350) // Debounce
            if (requestId != searchRequestId) return@launch
            locationRepository.search(query).fold(
                onSuccess = { results ->
                    if (requestId == searchRequestId) {
                        _searchState.value = SearchState(
                            query = query,
                            results = results,
                            isSearching = false,
                        )
                    }
                },
                onFailure = {
                    if (requestId == searchRequestId) {
                        _searchState.value = SearchState(
                            query = query,
                            isSearching = false,
                            error = "Search failed",
                        )
                    }
                }
            )
        }
    }

    fun addLocation(result: GeocodingResult) {
        viewModelScope.launch {
            locationRepository.addLocation(result)
            _searchState.update { it.copy(query = "", results = emptyList()) }
        }
    }

    fun removeLocation(id: Long) {
        viewModelScope.launch {
            locationRepository.removeLocation(id)
        }
    }

    fun moveLocation(fromIndex: Int, toIndex: Int) {
        val current = savedLocations.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val currentLocationIndex = current.indexOfFirst { it.isCurrentLocation }
        if (fromIndex == currentLocationIndex) return

        val minTargetIndex = if (currentLocationIndex >= 0) currentLocationIndex + 1 else 0
        val adjustedTargetIndex = toIndex.coerceIn(minTargetIndex, current.lastIndex)
        val item = current.removeAt(fromIndex)
        current.add(adjustedTargetIndex, item)
        viewModelScope.launch {
            locationRepository.reorderLocations(current.map { it.id })
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchRequestId += 1
        _searchState.value = SearchState()
    }
}

@Stable
data class SearchState(
    val query: String = "",
    val results: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)
