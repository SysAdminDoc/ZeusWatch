package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
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

    private var searchJob: Job? = null

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
        locations.forEach { loc ->
            val cached = weatherRepository.getCachedWeather(loc.latitude, loc.longitude)
            if (cached != null) {
                temps[loc.id] = cached.current.temperature
            }
        }
        _locationTemps.value = temps
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchState.update { it.copy(query = query, results = emptyList(), isSearching = false) }
            return
        }
        // Set isSearching=true immediately so UI shows spinner, not "No results"
        _searchState.update { it.copy(query = query, isSearching = true) }
        searchJob = viewModelScope.launch {
            delay(350) // Debounce
            locationRepository.search(query).fold(
                onSuccess = { results ->
                    _searchState.update { it.copy(results = results, isSearching = false) }
                },
                onFailure = {
                    _searchState.update { it.copy(results = emptyList(), isSearching = false, error = "Search failed") }
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
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        viewModelScope.launch {
            locationRepository.reorderLocations(current.map { it.id })
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchState.update { SearchState() }
    }
}

@Stable
data class SearchState(
    val query: String = "",
    val results: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)
