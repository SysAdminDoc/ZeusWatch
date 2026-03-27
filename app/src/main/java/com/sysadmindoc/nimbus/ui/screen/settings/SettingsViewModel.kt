package com.sysadmindoc.nimbus.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.model.IconPack
import com.sysadmindoc.nimbus.data.repository.*
import com.sysadmindoc.nimbus.util.IconPackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val iconPackManager: IconPackManager,
) : ViewModel() {

    val settings = prefs.settings

    /** Discovered icon packs (bundled + external APKs). */
    val availableIconPacks: List<IconPack> = iconPackManager.getAvailablePacks()

    fun setTempUnit(unit: TempUnit) = viewModelScope.launch { prefs.setTempUnit(unit) }
    fun setWindUnit(unit: WindUnit) = viewModelScope.launch { prefs.setWindUnit(unit) }
    fun setPressureUnit(unit: PressureUnit) = viewModelScope.launch { prefs.setPressureUnit(unit) }
    fun setPrecipUnit(unit: PrecipUnit) = viewModelScope.launch { prefs.setPrecipUnit(unit) }
    fun setTimeFormat(format: TimeFormat) = viewModelScope.launch { prefs.setTimeFormat(format) }
    fun setVisibilityUnit(unit: VisibilityUnit) = viewModelScope.launch { prefs.setVisibilityUnit(unit) }
    fun setParticlesEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setParticlesEnabled(enabled) }
    fun setAlertNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAlertNotificationsEnabled(enabled) }
    fun setAlertMinSeverity(severity: AlertMinSeverity) = viewModelScope.launch { prefs.setAlertMinSeverity(severity) }
    fun setAlertCheckAllLocations(enabled: Boolean) = viewModelScope.launch { prefs.setAlertCheckAllLocations(enabled) }
    fun setAlertSourcePref(pref: AlertSourcePreference) = viewModelScope.launch { prefs.setAlertSourcePref(pref) }

    // Display
    fun setRadarProvider(provider: RadarProvider) = viewModelScope.launch { prefs.setRadarProvider(provider) }
    fun setIconStyle(style: IconStyle) = viewModelScope.launch { prefs.setIconStyle(style) }
    fun setCustomIconPackId(id: String) = viewModelScope.launch { prefs.setCustomIconPackId(id) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setSummaryStyle(style: SummaryStyle) = viewModelScope.launch { prefs.setSummaryStyle(style) }

    // Card config
    fun setCardOrder(order: List<CardType>) = viewModelScope.launch { prefs.setCardOrder(order) }
    fun setCardEnabled(card: CardType, enabled: Boolean) = viewModelScope.launch { prefs.setCardEnabled(card, enabled) }

    // Notifications
    fun setPersistentWeatherNotif(enabled: Boolean) = viewModelScope.launch { prefs.setPersistentWeatherNotif(enabled) }
    fun setNowcastingAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setNowcastingAlerts(enabled) }
    fun setDrivingAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setDrivingAlerts(enabled) }
    fun setHealthAlertsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setHealthAlertsEnabled(enabled) }
    fun setMigraineAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setMigraineAlerts(enabled) }

    // Data display
    fun setShowSnowfall(enabled: Boolean) = viewModelScope.launch { prefs.setShowSnowfall(enabled) }
    fun setShowCape(enabled: Boolean) = viewModelScope.launch { prefs.setShowCape(enabled) }
    fun setShowSunshineDuration(enabled: Boolean) = viewModelScope.launch { prefs.setShowSunshineDuration(enabled) }
    fun setShowGoldenHour(enabled: Boolean) = viewModelScope.launch { prefs.setShowGoldenHour(enabled) }
    fun setShowBeaufortColors(enabled: Boolean) = viewModelScope.launch { prefs.setShowBeaufortColors(enabled) }
    fun setShowOutdoorScore(enabled: Boolean) = viewModelScope.launch { prefs.setShowOutdoorScore(enabled) }
    fun setShowYesterdayComparison(enabled: Boolean) = viewModelScope.launch { prefs.setShowYesterdayComparison(enabled) }

    // Forecast range
    fun setHourlyForecastHours(hours: Int) = viewModelScope.launch { prefs.setHourlyForecastHours(hours) }

    // Health
    fun setMigrainePressureThreshold(threshold: Double) = viewModelScope.launch { prefs.setMigrainePressureThreshold(threshold) }

    // Haptics
    fun setHapticFeedbackForAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setHapticFeedbackForAlerts(enabled) }

    // Cache
    fun setCacheTtlMinutes(minutes: Int) = viewModelScope.launch { prefs.setCacheTtlMinutes(minutes) }

    // Data sources
    fun setSourceForecast(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceForecast(provider) }
    fun setSourceForecastFallback(provider: WeatherSourceProvider?) = viewModelScope.launch { prefs.setSourceForecastFallback(provider) }
    fun setSourceAlerts(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceAlerts(provider) }
    fun setSourceAlertsFallback(provider: WeatherSourceProvider?) = viewModelScope.launch { prefs.setSourceAlertsFallback(provider) }
    fun setSourceAirQuality(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceAirQuality(provider) }
    fun setSourceMinutely(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceMinutely(provider) }

    // API keys
    fun setOwmApiKey(key: String) = viewModelScope.launch { prefs.setOwmApiKey(key) }
    fun setPirateWeatherApiKey(key: String) = viewModelScope.launch { prefs.setPirateWeatherApiKey(key) }
}
