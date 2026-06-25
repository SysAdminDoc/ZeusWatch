package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val savedLocations: StateFlow<List<SavedLocationEntity>> =
        locationRepository.savedLocations.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val recentSearches: StateFlow<List<GeocodingResult>> = _recentSearches.asStateFlow()

    /** Cached temperatures keyed by location ID. */
    private val _locationTemps = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val locationTemps: StateFlow<Map<Long, Double>> = _locationTemps.asStateFlow()

    /** Cached weather codes keyed by location ID. */
    private val _locationConditions = MutableStateFlow<Map<Long, Pair<WeatherCode, Boolean>>>(emptyMap())
    val locationConditions: StateFlow<Map<Long, Pair<WeatherCode, Boolean>>> = _locationConditions.asStateFlow()

    private var searchJob: Job? = null
    // @Volatile: read from background fetch/cache continuations after being
    // incremented on Main.immediate, so those reads see the latest value.
    @Volatile private var searchRequestId = 0L
    @Volatile private var tempsRequestId = 0L

    init {
        // Load cached temperatures whenever saved locations change
        viewModelScope.launch {
            savedLocations.collect { locations ->
                loadCachedTemps(locations)
            }
        }
        viewModelScope.launch {
            userPreferences.recentLocationSearches.collect { searches ->
                _recentSearches.value = searches
            }
        }
    }

    private suspend fun loadCachedTemps(locations: List<SavedLocationEntity>) {
        val requestId = ++tempsRequestId
        // Read every location's cache concurrently (each hop is a DB read on
        // Dispatchers.IO) instead of serially, so a long saved-locations list
        // doesn't stall the temp/condition refresh.
        val cachedList = coroutineScope {
            locations.map { loc ->
                async { loc to weatherRepository.getCachedWeather(loc.latitude, loc.longitude) }
            }.awaitAll()
        }
        // A newer saved-locations emission started a fresher scan — drop this
        // stale result so it can't clobber the newer map (lost-update guard).
        if (requestId != tempsRequestId) return

        val temps = mutableMapOf<Long, Double>()
        val conditions = mutableMapOf<Long, Pair<WeatherCode, Boolean>>()
        for ((loc, cached) in cachedList) {
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
                            errorRes = R.string.locations_search_failed,
                        )
                    }
                }
            )
        }
    }

    fun addLocation(
        result: GeocodingResult,
        onAdded: (Long) -> Unit = {},
    ) {
        viewModelScope.launch {
            val locationId = locationRepository.addLocation(result)
            userPreferences.addRecentLocationSearch(result)
            _searchState.update { it.copy(query = "", results = emptyList()) }
            onAdded(locationId)
        }
    }

    fun addMapPickedLocation(
        lat: Double,
        lon: Double,
        name: String,
        onAdded: (Long) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = GeocodingResult(
                id = 0,
                name = name,
                latitude = lat,
                longitude = lon,
            )
            val locationId = locationRepository.addLocation(result)
            onAdded(locationId)
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

    fun commitReorder(orderedIds: List<Long>) {
        viewModelScope.launch {
            locationRepository.reorderLocations(orderedIds)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchRequestId += 1
        _searchState.value = SearchState()
    }

    fun setForecastSource(locationId: Long, provider: WeatherSourceProvider?) {
        viewModelScope.launch {
            locationRepository.updateForecastSource(locationId, provider)
        }
    }

    fun setAlertSource(locationId: Long, provider: WeatherSourceProvider?) {
        viewModelScope.launch {
            locationRepository.updateAlertSource(locationId, provider)
        }
    }
}

@Stable
data class SearchState(
    val query: String = "",
    val results: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Localized error message resource — a resource id (not a baked English string) so locale changes apply. */
    @StringRes val errorRes: Int? = null,
)
