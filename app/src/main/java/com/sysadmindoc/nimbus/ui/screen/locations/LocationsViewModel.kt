package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.repository.LocationRepository
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
) : ViewModel() {

    val savedLocations: StateFlow<List<SavedLocationEntity>> =
        locationRepository.savedLocations.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _searchState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // Debounce
            _searchState.update { it.copy(isSearching = true) }
            locationRepository.search(query).fold(
                onSuccess = { results ->
                    _searchState.update { it.copy(results = results, isSearching = false) }
                },
                onFailure = {
                    _searchState.update { it.copy(results = emptyList(), isSearching = false) }
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
)
