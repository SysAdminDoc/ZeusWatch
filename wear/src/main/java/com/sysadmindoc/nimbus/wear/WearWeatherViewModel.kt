package com.sysadmindoc.nimbus.wear

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WearWeatherViewModel @Inject constructor(
    private val repository: WearWeatherRepository,
    private val locationProvider: WearLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    init {
        loadWeather()
    }

    fun loadWeather() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val loc = locationProvider.getLocation()
            repository.getCurrentWeather(
                lat = loc.lat,
                lon = loc.lon,
                locationName = loc.name,
            ).fold(
                onSuccess = { data ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            temperature = data.temperature,
                            condition = data.condition,
                            high = data.high,
                            low = data.low,
                            locationName = data.locationName,
                            humidity = data.humidity,
                            windSpeed = data.windSpeed,
                            uvIndex = data.uvIndex,
                            precipChance = data.precipChance,
                            isDay = data.isDay,
                            weatherCode = data.weatherCode,
                            hourly = data.hourly,
                            daily = data.daily,
                            alerts = data.alerts,
                            aqi = data.aqi,
                            aqiLabel = data.aqiLabel,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load weather")
                    }
                },
            )
        }
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()
}

@Stable
data class WearUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val temperature: Int = 0,
    val condition: String = "",
    val high: Int = 0,
    val low: Int = 0,
    val locationName: String = "",
    val humidity: Int = 0,
    val windSpeed: Int = 0,
    val uvIndex: Int = 0,
    val precipChance: Int = 0,
    val isDay: Boolean = true,
    val weatherCode: Int = 0,
    val hourly: List<HourlyEntry> = emptyList(),
    val daily: List<WearDailyEntry> = emptyList(),
    val alerts: List<WearAlertEntry> = emptyList(),
    val aqi: Int = -1,
    val aqiLabel: String = "",
)
