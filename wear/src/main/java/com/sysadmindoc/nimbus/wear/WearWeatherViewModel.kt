package com.sysadmindoc.nimbus.wear

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.wear.data.DataSource
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WearWeatherViewModel"
private const val WEATHER_LOAD_FAILED = "weather_load_failed"

@HiltViewModel
class WearWeatherViewModel @Inject constructor(
    private val repository: WearWeatherRepository,
    private val locationProvider: WearLocationProvider,
    private val syncedWeatherStore: SyncedWeatherStore,
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
                            dataSource = data.dataSource,
                            syncedAtMs = data.syncedAtMs,
                            updatedAtMs = data.updatedAtMs,
                            tempUnit = data.tempUnit,
                            windUnit = data.windUnit,
                        )
                    }
                },
                onFailure = { e ->
                    Log.w(TAG, "Failed to load wear weather", e)
                    _uiState.update {
                        it.copy(isLoading = false, error = WEATHER_LOAD_FAILED)
                    }
                },
            )
        }
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    fun cycleTempUnit() {
        val nextUnit = syncedWeatherStore.cycleTempUnitOverride()
        _uiState.update { it.copy(tempUnit = nextUnit) }
    }
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
    /** Whether the last successful payload came from the phone or a watch API call. */
    val dataSource: DataSource = DataSource.UNKNOWN,
    /** Epoch ms the current payload was received (phone-sync time or fetch time). */
    val syncedAtMs: Long = 0L,
    /** Epoch ms the weather data itself was produced; 0 = unknown (use [syncedAtMs]). */
    val updatedAtMs: Long = 0L,
    /** Display units (phone enum names); raw state values are always metric. */
    val tempUnit: String = WearUnitFormatter.TEMP_CELSIUS,
    val windUnit: String = WearUnitFormatter.WIND_KMH,
)
