package com.sysadmindoc.nimbus.ui.screen.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.IconPack
import com.sysadmindoc.nimbus.data.repository.*
import com.sysadmindoc.nimbus.util.AlertCheckWorker
import com.sysadmindoc.nimbus.util.AlertNotificationHelper
import com.sysadmindoc.nimbus.util.DailyBriefingWorker
import com.sysadmindoc.nimbus.util.HealthAlertWorker
import com.sysadmindoc.nimbus.util.NowcastAlertWorker
import com.sysadmindoc.nimbus.util.WeatherNotificationHelper
import com.sysadmindoc.nimbus.util.IconPackManager
import com.sysadmindoc.nimbus.widget.WidgetRefreshWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: UserPreferences,
    private val iconPackManager: IconPackManager,
    private val settingsTransferManager: SettingsTransferManager,
    private val providerHealthRepository: ProviderHealthRepository,
) : ViewModel() {

    val settings = prefs.settings
    val providerHealth = providerHealthRepository.snapshot

    /**
     * Discovered icon packs (bundled + external APKs). Exposed as a StateFlow
     * because `IconPackManager.getAvailablePacks()` reaches into external APKs
     * via `PackageManager.getResourcesForApplication()` + `AssetManager.open()`,
     * which is blocking disk I/O. Opening Settings used to run that on the main
     * thread during ViewModel construction; now it kicks off on `Dispatchers.IO`
     * and the UI observes the result.
     */
    private val _availableIconPacks = MutableStateFlow<List<IconPack>>(emptyList())
    val availableIconPacks: StateFlow<List<IconPack>> = _availableIconPacks.asStateFlow()

    private val _transferStatus = MutableStateFlow<SettingsTransferStatus?>(null)
    val transferStatus: StateFlow<SettingsTransferStatus?> = _transferStatus.asStateFlow()

    private val _transferInProgress = MutableStateFlow(false)
    val transferInProgress: StateFlow<Boolean> = _transferInProgress.asStateFlow()

    private val _pendingImportPreview = MutableStateFlow<SettingsImportPreview?>(null)
    val pendingImportPreview: StateFlow<SettingsImportPreview?> = _pendingImportPreview.asStateFlow()
    private var pendingImportRaw: String? = null

    init {
        viewModelScope.launch {
            val packs = withContext(Dispatchers.IO) {
                runCatching { iconPackManager.getAvailablePacks() }.getOrDefault(emptyList())
            }
            _availableIconPacks.value = packs
        }
    }

    fun setTempUnit(unit: TempUnit) = viewModelScope.launch { prefs.setTempUnit(unit) }
    fun setWindUnit(unit: WindUnit) = viewModelScope.launch { prefs.setWindUnit(unit) }
    fun setPressureUnit(unit: PressureUnit) = viewModelScope.launch { prefs.setPressureUnit(unit) }
    fun setPrecipUnit(unit: PrecipUnit) = viewModelScope.launch { prefs.setPrecipUnit(unit) }
    fun setTimeFormat(format: TimeFormat) = viewModelScope.launch { prefs.setTimeFormat(format) }
    fun setVisibilityUnit(unit: VisibilityUnit) = viewModelScope.launch { prefs.setVisibilityUnit(unit) }
    fun setParticlesEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setParticlesEnabled(enabled) }
    fun setAlertNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAlertNotificationsEnabled(enabled)
        if (enabled) {
            AlertCheckWorker.schedule(appContext)
        } else {
            AlertCheckWorker.cancel(appContext)
            AlertNotificationHelper.dismissAll(appContext)
        }
    }
    fun setAlertMinSeverity(severity: AlertMinSeverity) = viewModelScope.launch { prefs.setAlertMinSeverity(severity) }
    fun setAlertCheckAllLocations(enabled: Boolean) = viewModelScope.launch { prefs.setAlertCheckAllLocations(enabled) }
    fun setAlertSourcePref(pref: AlertSourcePreference) = viewModelScope.launch { prefs.setAlertSourcePref(pref) }

    // Display
    fun setRadarProvider(provider: RadarProvider) = viewModelScope.launch { prefs.setRadarProvider(provider) }
    fun setIconStyle(style: IconStyle) = viewModelScope.launch { prefs.setIconStyle(style) }
    fun setCustomIconPackId(id: String) = viewModelScope.launch { prefs.setCustomIconPackId(id) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setSummaryStyle(style: SummaryStyle) = viewModelScope.launch { prefs.setSummaryStyle(style) }
    fun setCustomSummaryTemplate(template: String) = viewModelScope.launch { prefs.setCustomSummaryTemplate(template) }

    // Card config
    /** Atomic single-step card move — see [UserPreferences.moveCardInOrder]. */
    fun moveCard(card: CardType, delta: Int) = viewModelScope.launch { prefs.moveCardInOrder(card, delta) }
    fun setCardEnabled(card: CardType, enabled: Boolean) = viewModelScope.launch { prefs.setCardEnabled(card, enabled) }
    fun resetCardPreferences() = viewModelScope.launch { prefs.resetCardPreferences() }

    // Notifications
    fun setPersistentWeatherNotif(enabled: Boolean) = viewModelScope.launch {
        prefs.setPersistentWeatherNotif(enabled)
        WidgetRefreshWorker.sync(appContext, enabled)
        if (!enabled) {
            WeatherNotificationHelper.dismiss(appContext)
        }
    }
    fun setNowcastingAlerts(enabled: Boolean) = viewModelScope.launch {
        prefs.setNowcastingAlerts(enabled)
        if (enabled) {
            NowcastAlertWorker.schedule(appContext)
        } else {
            NowcastAlertWorker.cancel(appContext)
        }
    }
    fun setDailyBriefingEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setDailyBriefingEnabled(enabled)
        val settings = prefs.settings.first()
        if (enabled) {
            DailyBriefingWorker.schedule(appContext, settings.dailyBriefingMinutes)
        } else {
            DailyBriefingWorker.cancel(appContext)
            WeatherNotificationHelper.dismissDailyBriefing(appContext)
        }
    }
    fun setDailyBriefingMinutes(minutes: Int) = viewModelScope.launch {
        prefs.setDailyBriefingMinutes(minutes)
        val settings = prefs.settings.first()
        if (settings.dailyBriefingEnabled) {
            DailyBriefingWorker.schedule(appContext, settings.dailyBriefingMinutes)
        }
    }
    fun setDrivingAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setDrivingAlerts(enabled) }
    fun setHealthAlertsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setHealthAlertsEnabled(enabled)
        if (enabled) {
            HealthAlertWorker.schedule(appContext)
        } else {
            HealthAlertWorker.cancel(appContext)
        }
    }
    fun setMigraineAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setMigraineAlerts(enabled) }

    // Data display
    fun setShowSnowfall(enabled: Boolean) = viewModelScope.launch { prefs.setShowSnowfall(enabled) }
    fun setShowCape(enabled: Boolean) = viewModelScope.launch { prefs.setShowCape(enabled) }
    fun setShowSunshineDuration(enabled: Boolean) = viewModelScope.launch { prefs.setShowSunshineDuration(enabled) }
    fun setShowGoldenHour(enabled: Boolean) = viewModelScope.launch { prefs.setShowGoldenHour(enabled) }
    fun setShowBeaufortColors(enabled: Boolean) = viewModelScope.launch { prefs.setShowBeaufortColors(enabled) }
    fun setShowOutdoorScore(enabled: Boolean) = viewModelScope.launch { prefs.setShowOutdoorScore(enabled) }
    fun setShowYesterdayComparison(enabled: Boolean) = viewModelScope.launch { prefs.setShowYesterdayComparison(enabled) }
    fun setShowForecastAccuracy(enabled: Boolean) = viewModelScope.launch { prefs.setShowForecastAccuracy(enabled) }
    fun setShowConfidenceBands(enabled: Boolean) = viewModelScope.launch { prefs.setShowConfidenceBands(enabled) }

    // Forecast range
    fun setHourlyForecastHours(hours: Int) = viewModelScope.launch { prefs.setHourlyForecastHours(hours) }

    // Health
    fun setMigrainePressureThreshold(threshold: Double) = viewModelScope.launch { prefs.setMigrainePressureThreshold(threshold) }

    // Haptics
    fun setHapticFeedbackForAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setHapticFeedbackForAlerts(enabled) }
    fun setAccessibilityLayout(enabled: Boolean) = viewModelScope.launch { prefs.setAccessibilityLayout(enabled) }

    // Cache
    fun setCacheTtlMinutes(minutes: Int) = viewModelScope.launch { prefs.setCacheTtlMinutes(minutes) }

    // Data sources
    fun setSourceForecast(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceForecast(provider) }
    fun setSourceForecastFallback(provider: WeatherSourceProvider?) = viewModelScope.launch { prefs.setSourceForecastFallback(provider) }
    fun setSourceAlerts(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceAlerts(provider) }
    fun setSourceAlertsFallback(provider: WeatherSourceProvider?) = viewModelScope.launch { prefs.setSourceAlertsFallback(provider) }
    fun setSourceAirQuality(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceAirQuality(provider) }
    fun setSourceMinutely(provider: WeatherSourceProvider) = viewModelScope.launch { prefs.setSourceMinutely(provider) }
    fun setOpenMeteoFlatBuffersEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setOpenMeteoFlatBuffersEnabled(enabled)
    }
    fun setGadgetbridgeBroadcastEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setGadgetbridgeBroadcastEnabled(enabled)
        if (enabled) WidgetRefreshWorker.sync(appContext, true)
    }

    // API keys
    fun setOwmApiKey(key: String) = viewModelScope.launch { prefs.setOwmApiKey(key) }
    fun setPirateWeatherApiKey(key: String) = viewModelScope.launch { prefs.setPirateWeatherApiKey(key) }
    fun setTempestAccessToken(token: String) = viewModelScope.launch { prefs.setTempestAccessToken(token) }
    fun setTempestDeviceId(deviceId: String) = viewModelScope.launch { prefs.setTempestDeviceId(deviceId) }

    fun exportSettings(uri: Uri) = viewModelScope.launch {
        if (!beginTransfer()) return@launch
        clearPendingImport()
        try {
            runCatching {
                val json = withContext(Dispatchers.IO) { settingsTransferManager.exportJson() }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: error("Could not open export file.")
                }
            }.fold(
                onSuccess = { _transferStatus.value = SettingsTransferStatus.ExportSuccess },
                onFailure = {
                    Log.w(TAG, "Failed to export settings", it)
                    _transferStatus.value = SettingsTransferStatus.ExportError
                },
            )
        } finally {
            _transferInProgress.value = false
        }
    }

    fun exportProviderDiagnostics(uri: Uri) = viewModelScope.launch {
        if (!beginTransfer()) return@launch
        clearPendingImport()
        try {
            runCatching {
                val text = withContext(Dispatchers.IO) { providerHealthRepository.diagnosticsText() }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(text)
                    } ?: error("Could not open diagnostics file.")
                }
            }.fold(
                onSuccess = { _transferStatus.value = SettingsTransferStatus.DiagnosticsExportSuccess },
                onFailure = {
                    Log.w(TAG, "Failed to export provider diagnostics", it)
                    _transferStatus.value = SettingsTransferStatus.DiagnosticsExportError
                },
            )
        } finally {
            _transferInProgress.value = false
        }
    }

    fun importSettings(uri: Uri) = viewModelScope.launch {
        if (!beginTransfer()) return@launch
        clearPendingImport()
        try {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        readSettingsBackupText(stream)
                    } ?: error("Could not open import file.")
                }
                val preview = withContext(Dispatchers.IO) {
                    settingsTransferManager.previewImportJson(raw)
                }
                pendingImportRaw = raw
                _pendingImportPreview.value = preview
            }.fold(
                onSuccess = {},
                onFailure = {
                    Log.w(TAG, "Failed to preview settings import", it)
                    pendingImportRaw = null
                    _pendingImportPreview.value = null
                    _transferStatus.value = SettingsTransferStatus.ImportError
                },
            )
        } finally {
            _transferInProgress.value = false
        }
    }

    fun confirmPendingImport() = viewModelScope.launch {
        val raw = pendingImportRaw ?: return@launch
        if (!beginTransfer()) return@launch
        try {
            runCatching {
                withContext(Dispatchers.IO) { settingsTransferManager.importJson(raw) }
            }.fold(
                onSuccess = { result ->
                    pendingImportRaw = null
                    _pendingImportPreview.value = null
                    _transferStatus.value = SettingsTransferStatus.ImportSuccess(
                        savedLocationCount = result.savedLocationCount,
                        customAlertCount = result.customAlertCount,
                    )
                },
                onFailure = {
                    Log.w(TAG, "Failed to import settings", it)
                    _transferStatus.value = SettingsTransferStatus.ImportError
                },
            )
        } finally {
            _transferInProgress.value = false
        }
    }

    fun cancelPendingImport() {
        clearPendingImport()
    }

    fun clearTransferStatus() {
        _transferStatus.value = null
    }

    private fun beginTransfer(): Boolean {
        if (_transferInProgress.value) return false
        _transferInProgress.value = true
        _transferStatus.value = null
        return true
    }

    private fun clearPendingImport() {
        pendingImportRaw = null
        _pendingImportPreview.value = null
    }
}

enum class SettingsTransferStatusTone {
    SUCCESS,
    ERROR,
}

sealed interface SettingsTransferStatus {
    @get:StringRes val messageRes: Int
    val tone: SettingsTransferStatusTone

    data object ExportSuccess : SettingsTransferStatus {
        override val messageRes: Int = R.string.settings_transfer_export_success
        override val tone: SettingsTransferStatusTone = SettingsTransferStatusTone.SUCCESS
    }

    data object ExportError : SettingsTransferStatus {
        override val messageRes: Int = R.string.settings_transfer_export_error
        override val tone: SettingsTransferStatusTone = SettingsTransferStatusTone.ERROR
    }

    data object DiagnosticsExportSuccess : SettingsTransferStatus {
        override val messageRes: Int = R.string.settings_provider_health_export_success
        override val tone: SettingsTransferStatusTone = SettingsTransferStatusTone.SUCCESS
    }

    data object DiagnosticsExportError : SettingsTransferStatus {
        override val messageRes: Int = R.string.settings_provider_health_export_error
        override val tone: SettingsTransferStatusTone = SettingsTransferStatusTone.ERROR
    }

    data class ImportSuccess(
        val savedLocationCount: Int,
        val customAlertCount: Int,
    ) : SettingsTransferStatus {
        override val messageRes: Int = R.string.settings_transfer_import_success
        override val tone: SettingsTransferStatusTone = SettingsTransferStatusTone.SUCCESS
    }

    data object ImportError : SettingsTransferStatus {
        override val messageRes: Int = R.string.settings_transfer_import_error
        override val tone: SettingsTransferStatusTone = SettingsTransferStatusTone.ERROR
    }
}
