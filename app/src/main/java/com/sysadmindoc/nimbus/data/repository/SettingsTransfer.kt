package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

private const val SETTINGS_BACKUP_SCHEMA_VERSION = 1

private val settingsTransferJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

@Singleton
class SettingsTransferManager @Inject constructor(
    private val prefs: UserPreferences,
    private val locationRepository: LocationRepository,
) {
    suspend fun exportJson(): String {
        val settings = prefs.settings.first()
        val customAlerts = prefs.customAlertRules.first()
        val savedLocations = locationRepository.getAll()
        val lastLocation = prefs.lastLocation.first()
        val backup = SettingsBackup(
            settings = settings.toBackup(),
            customAlerts = customAlerts,
            savedLocations = savedLocations.map { it.toBackup() },
            lastLocation = lastLocation?.toBackup(),
        )
        return settingsTransferJson.encodeToString(SettingsBackup.serializer(), backup)
    }

    suspend fun previewImportJson(rawJson: String): SettingsImportPreview {
        val currentLocationCount = locationRepository.getAll().size
        return previewSettingsImport(rawJson, currentLocationCount)
    }

    suspend fun importJson(rawJson: String): SettingsTransferResult {
        val snapshot = currentSnapshot()
        val plan = parseSettingsBackup(rawJson).toImportPlan(snapshot.savedLocations.size)
        return try {
            applyImportPlan(plan)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            runCatching { restoreSnapshot(snapshot) }
                .onFailure { rollbackFailure -> failure.addSuppressed(rollbackFailure) }
            throw failure
        }
    }

    private suspend fun currentSnapshot(): SettingsImportSnapshot = SettingsImportSnapshot(
        settings = prefs.settings.first(),
        customAlerts = prefs.customAlertRules.first(),
        savedLocations = locationRepository.getAll(),
        lastLocation = prefs.lastLocation.first(),
    )

    private suspend fun restoreSnapshot(snapshot: SettingsImportSnapshot) {
        prefs.applyImportedSettings(snapshot.settings)
        prefs.setCustomAlertRules(snapshot.customAlerts)
        prefs.applyImportedLastLocation(snapshot.lastLocation)
        locationRepository.restoreAll(snapshot.savedLocations)
    }

    private suspend fun applyImportPlan(plan: SettingsImportPlan): SettingsTransferResult {
        prefs.applyImportedSettings(plan.settings)
        prefs.setCustomAlertRules(plan.backup.customAlerts)
        prefs.applyImportedLastLocation(plan.backup.lastLocation?.toSavedLocation())
        locationRepository.replaceAll(plan.importedLocations)
        return SettingsTransferResult(
            savedLocationCount = plan.importedLocations.size,
            customAlertCount = plan.backup.customAlerts.size,
        )
    }
}

data class SettingsTransferResult(
    val savedLocationCount: Int,
    val customAlertCount: Int,
)

data class SettingsImportPreview(
    val schemaVersion: Int,
    val currentSavedLocationCount: Int,
    val savedLocationCount: Int,
    val skippedLocationCount: Int,
    val duplicateLocationCount: Int,
    val customAlertCount: Int,
    val cardCount: Int,
    val hasLastLocation: Boolean,
    val warnings: List<SettingsImportWarning>,
)

enum class SettingsImportWarning {
    BLANK_LOCATION_NAMES,
    DUPLICATE_LOCATIONS,
}

private data class SettingsImportSnapshot(
    val settings: NimbusSettings,
    val customAlerts: List<CustomAlertRule>,
    val savedLocations: List<SavedLocationEntity>,
    val lastLocation: SavedLocation?,
)

private data class SettingsImportPlan(
    val backup: SettingsBackup,
    val settings: NimbusSettings,
    val importedLocations: List<SavedLocationEntity>,
    val preview: SettingsImportPreview,
)

@Serializable
data class SettingsBackup(
    val schemaVersion: Int = SETTINGS_BACKUP_SCHEMA_VERSION,
    val app: String = "ZeusWatch",
    val settings: SettingsBackupPreferences,
    val customAlerts: List<CustomAlertRule> = emptyList(),
    val savedLocations: List<SettingsBackupLocation> = emptyList(),
    val lastLocation: SettingsBackupLastLocation? = null,
)

@Serializable
data class SettingsBackupPreferences(
    val tempUnit: String = TempUnit.FAHRENHEIT.name,
    val windUnit: String = WindUnit.MPH.name,
    val pressureUnit: String = PressureUnit.INHG.name,
    val precipUnit: String = PrecipUnit.INCHES.name,
    val visibilityUnit: String = VisibilityUnit.MILES.name,
    val timeFormat: String = TimeFormat.TWELVE_HOUR.name,
    val particlesEnabled: Boolean = true,
    val alertNotificationsEnabled: Boolean = true,
    val alertMinSeverity: String = AlertMinSeverity.SEVERE.name,
    val alertCheckAllLocations: Boolean = true,
    val alertSourcePref: String = AlertSourcePreference.AUTO.name,
    val radarProvider: String = RadarProvider.WINDY_WEBVIEW.name,
    val iconStyle: String = IconStyle.METEOCONS.name,
    val customIconPackId: String = "",
    val themeMode: String = ThemeMode.STATIC_DARK.name,
    val summaryStyle: String = SummaryStyle.AI_GENERATED.name,
    val disabledCards: Set<String> = DEFAULT_DISABLED_CARDS,
    val cardOrder: List<String> = DEFAULT_CARD_ORDER.map { it.name },
    val persistentWeatherNotif: Boolean = true,
    val nowcastingAlerts: Boolean = true,
    val dailyBriefingEnabled: Boolean = false,
    val dailyBriefingMinutes: Int = DEFAULT_DAILY_BRIEFING_MINUTES,
    val drivingAlerts: Boolean = false,
    val healthAlertsEnabled: Boolean = false,
    val migraineAlerts: Boolean = false,
    val showSnowfall: Boolean = true,
    val showCape: Boolean = true,
    val showSunshineDuration: Boolean = true,
    val showGoldenHour: Boolean = true,
    val showBeaufortColors: Boolean = true,
    val showOutdoorScore: Boolean = true,
    val showYesterdayComparison: Boolean = true,
    val hourlyForecastHours: Int = 72,
    val migrainePressureThreshold: Double = 5.0,
    val hapticFeedbackForAlerts: Boolean = true,
    val cacheTtlMinutes: Int = 30,
    val sourceForecast: String = WeatherSourceProvider.defaultFor(WeatherDataType.FORECAST).name,
    val sourceForecastFallback: String? = null,
    val sourceAlerts: String = WeatherSourceProvider.defaultFor(WeatherDataType.ALERTS).name,
    val sourceAlertsFallback: String? = null,
    val sourceAirQuality: String = WeatherSourceProvider.defaultFor(WeatherDataType.AIR_QUALITY).name,
    val sourceMinutely: String = WeatherSourceProvider.defaultFor(WeatherDataType.MINUTELY).name,
    val onboardingComplete: Boolean = true,
)

@Serializable
data class SettingsBackupLocation(
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int = 0,
    val isCurrentLocation: Boolean = false,
    val addedAt: Long = 0,
    val forecastSource: String? = null,
    val alertSource: String? = null,
)

@Serializable
data class SettingsBackupLastLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
)

fun previewSettingsImport(rawJson: String, currentSavedLocationCount: Int = 0): SettingsImportPreview =
    parseSettingsBackup(rawJson).toImportPlan(currentSavedLocationCount).preview

private fun parseSettingsBackup(rawJson: String): SettingsBackup {
    val backup = settingsTransferJson.decodeFromString(SettingsBackup.serializer(), rawJson)
    require(backup.schemaVersion == SETTINGS_BACKUP_SCHEMA_VERSION) {
        "Unsupported settings backup version ${backup.schemaVersion}."
    }
    return backup
}

private fun SettingsBackup.toImportPlan(currentSavedLocationCount: Int): SettingsImportPlan {
    val locationPlan = savedLocations.toImportLocationPlan()
    val warnings = buildList {
        if (locationPlan.skippedBlankCount > 0) add(SettingsImportWarning.BLANK_LOCATION_NAMES)
        if (locationPlan.duplicateCount > 0) add(SettingsImportWarning.DUPLICATE_LOCATIONS)
    }
    val settings = settings.toSettings()
    return SettingsImportPlan(
        backup = this,
        settings = settings,
        importedLocations = locationPlan.locations,
        preview = SettingsImportPreview(
            schemaVersion = schemaVersion,
            currentSavedLocationCount = currentSavedLocationCount.coerceAtLeast(0),
            savedLocationCount = locationPlan.locations.size,
            skippedLocationCount = locationPlan.skippedBlankCount,
            duplicateLocationCount = locationPlan.duplicateCount,
            customAlertCount = customAlerts.size,
            cardCount = settings.cardOrder.size,
            hasLastLocation = lastLocation != null,
            warnings = warnings,
        ),
    )
}

private data class ImportLocationPlan(
    val locations: List<SavedLocationEntity>,
    val skippedBlankCount: Int,
    val duplicateCount: Int,
)

private fun List<SettingsBackupLocation>.toImportLocationPlan(): ImportLocationPlan {
    var skippedBlankCount = 0
    var duplicateCount = 0
    val seen = LinkedHashSet<String>()
    val locations = mapNotNull { location ->
        if (location.name.isBlank()) {
            skippedBlankCount++
            return@mapNotNull null
        }
        val entity = location.toEntity()
        if (!seen.add(entity.importKey())) {
            duplicateCount++
            return@mapNotNull null
        }
        entity
    }
    return ImportLocationPlan(
        locations = locations,
        skippedBlankCount = skippedBlankCount,
        duplicateCount = duplicateCount,
    )
}

private fun SavedLocationEntity.importKey(): String = listOf(
    name.trim().lowercase(Locale.ROOT),
    region.trim().lowercase(Locale.ROOT),
    country.trim().lowercase(Locale.ROOT),
    (latitude * 10_000).roundToLong().toString(),
    (longitude * 10_000).roundToLong().toString(),
    isCurrentLocation.toString(),
).joinToString("|")

fun NimbusSettings.toBackup(): SettingsBackupPreferences = SettingsBackupPreferences(
    tempUnit = tempUnit.name,
    windUnit = windUnit.name,
    pressureUnit = pressureUnit.name,
    precipUnit = precipUnit.name,
    visibilityUnit = visibilityUnit.name,
    timeFormat = timeFormat.name,
    particlesEnabled = particlesEnabled,
    alertNotificationsEnabled = alertNotificationsEnabled,
    alertMinSeverity = alertMinSeverity.name,
    alertCheckAllLocations = alertCheckAllLocations,
    alertSourcePref = alertSourcePref.name,
    radarProvider = radarProvider.name,
    iconStyle = iconStyle.name,
    customIconPackId = customIconPackId,
    themeMode = themeMode.name,
    summaryStyle = summaryStyle.name,
    disabledCards = disabledCards,
    cardOrder = cardOrder.map { it.name },
    persistentWeatherNotif = persistentWeatherNotif,
    nowcastingAlerts = nowcastingAlerts,
    dailyBriefingEnabled = dailyBriefingEnabled,
    dailyBriefingMinutes = dailyBriefingMinutes,
    drivingAlerts = drivingAlerts,
    healthAlertsEnabled = healthAlertsEnabled,
    migraineAlerts = migraineAlerts,
    showSnowfall = showSnowfall,
    showCape = showCape,
    showSunshineDuration = showSunshineDuration,
    showGoldenHour = showGoldenHour,
    showBeaufortColors = showBeaufortColors,
    showOutdoorScore = showOutdoorScore,
    showYesterdayComparison = showYesterdayComparison,
    hourlyForecastHours = hourlyForecastHours,
    migrainePressureThreshold = migrainePressureThreshold,
    hapticFeedbackForAlerts = hapticFeedbackForAlerts,
    cacheTtlMinutes = cacheTtlMinutes,
    sourceForecast = sourceConfig.forecast.name,
    sourceForecastFallback = sourceConfig.forecastFallback?.name,
    sourceAlerts = sourceConfig.alerts.name,
    sourceAlertsFallback = sourceConfig.alertsFallback?.name,
    sourceAirQuality = sourceConfig.airQuality.name,
    sourceMinutely = sourceConfig.minutely.name,
    onboardingComplete = onboardingComplete,
)

fun SettingsBackupPreferences.toSettings(): NimbusSettings = NimbusSettings(
    tempUnit = enumOrDefault(tempUnit, TempUnit.FAHRENHEIT),
    windUnit = enumOrDefault(windUnit, WindUnit.MPH),
    pressureUnit = enumOrDefault(pressureUnit, PressureUnit.INHG),
    precipUnit = enumOrDefault(precipUnit, PrecipUnit.INCHES),
    visibilityUnit = enumOrDefault(visibilityUnit, VisibilityUnit.MILES),
    timeFormat = enumOrDefault(timeFormat, TimeFormat.TWELVE_HOUR),
    particlesEnabled = particlesEnabled,
    alertNotificationsEnabled = alertNotificationsEnabled,
    alertMinSeverity = enumOrDefault(alertMinSeverity, AlertMinSeverity.SEVERE),
    alertCheckAllLocations = alertCheckAllLocations,
    alertSourcePref = enumOrDefault(alertSourcePref, AlertSourcePreference.AUTO),
    radarProvider = enumOrDefault(radarProvider, RadarProvider.WINDY_WEBVIEW),
    iconStyle = enumOrDefault(iconStyle, IconStyle.METEOCONS),
    customIconPackId = customIconPackId,
    themeMode = enumOrDefault(themeMode, ThemeMode.STATIC_DARK),
    summaryStyle = enumOrDefault(summaryStyle, SummaryStyle.AI_GENERATED),
    disabledCards = disabledCards.filter { runCatching { CardType.valueOf(it) }.isSuccess }.toSet(),
    cardOrder = parseCardOrder(cardOrder.joinToString(",")),
    persistentWeatherNotif = persistentWeatherNotif,
    nowcastingAlerts = nowcastingAlerts,
    dailyBriefingEnabled = dailyBriefingEnabled,
    dailyBriefingMinutes = normalizeDailyBriefingMinutes(dailyBriefingMinutes),
    drivingAlerts = drivingAlerts,
    healthAlertsEnabled = healthAlertsEnabled,
    migraineAlerts = migraineAlerts,
    showSnowfall = showSnowfall,
    showCape = showCape,
    showSunshineDuration = showSunshineDuration,
    showGoldenHour = showGoldenHour,
    showBeaufortColors = showBeaufortColors,
    showOutdoorScore = showOutdoorScore,
    showYesterdayComparison = showYesterdayComparison,
    hourlyForecastHours = hourlyForecastHours.coerceIn(24, 72),
    migrainePressureThreshold = migrainePressureThreshold.coerceIn(1.0, 20.0),
    hapticFeedbackForAlerts = hapticFeedbackForAlerts,
    cacheTtlMinutes = cacheTtlMinutes.coerceIn(15, 120),
    sourceConfig = SourceConfig(
        forecast = WeatherSourceProvider.fromStoredName(sourceForecast, WeatherDataType.FORECAST)
            ?: WeatherSourceProvider.defaultFor(WeatherDataType.FORECAST),
        forecastFallback = WeatherSourceProvider.fromStoredName(sourceForecastFallback, WeatherDataType.FORECAST),
        alerts = WeatherSourceProvider.fromStoredName(sourceAlerts, WeatherDataType.ALERTS)
            ?: WeatherSourceProvider.defaultFor(WeatherDataType.ALERTS),
        alertsFallback = WeatherSourceProvider.fromStoredName(sourceAlertsFallback, WeatherDataType.ALERTS),
        airQuality = WeatherSourceProvider.fromStoredName(sourceAirQuality, WeatherDataType.AIR_QUALITY)
            ?: WeatherSourceProvider.defaultFor(WeatherDataType.AIR_QUALITY),
        minutely = WeatherSourceProvider.fromStoredName(sourceMinutely, WeatherDataType.MINUTELY)
            ?: WeatherSourceProvider.defaultFor(WeatherDataType.MINUTELY),
    ).normalized(),
    onboardingComplete = onboardingComplete,
)

fun SavedLocationEntity.toBackup(): SettingsBackupLocation = SettingsBackupLocation(
    name = name,
    region = region,
    country = country,
    latitude = latitude,
    longitude = longitude,
    sortOrder = sortOrder,
    isCurrentLocation = isCurrentLocation,
    addedAt = addedAt,
    forecastSource = forecastSource,
    alertSource = alertSource,
)

fun SettingsBackupLocation.toEntity(): SavedLocationEntity = SavedLocationEntity(
    name = name,
    region = region,
    country = country,
    latitude = latitude,
    longitude = longitude,
    sortOrder = if (isCurrentLocation) -1 else sortOrder,
    isCurrentLocation = isCurrentLocation,
    addedAt = addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    forecastSource = WeatherSourceProvider.fromStoredName(forecastSource, WeatherDataType.FORECAST)?.name,
    alertSource = WeatherSourceProvider.fromStoredName(alertSource, WeatherDataType.ALERTS)?.name,
)

fun SavedLocation.toBackup(): SettingsBackupLastLocation = SettingsBackupLastLocation(
    latitude = latitude,
    longitude = longitude,
    name = name,
)

fun SettingsBackupLastLocation.toSavedLocation(): SavedLocation = SavedLocation(
    latitude = latitude,
    longitude = longitude,
    name = name,
)

suspend fun UserPreferences.applyImportedSettings(settings: NimbusSettings) {
    setTempUnit(settings.tempUnit)
    setWindUnit(settings.windUnit)
    setPressureUnit(settings.pressureUnit)
    setPrecipUnit(settings.precipUnit)
    setVisibilityUnit(settings.visibilityUnit)
    setTimeFormat(settings.timeFormat)
    setParticlesEnabled(settings.particlesEnabled)
    setAlertNotificationsEnabled(settings.alertNotificationsEnabled)
    setAlertMinSeverity(settings.alertMinSeverity)
    setAlertCheckAllLocations(settings.alertCheckAllLocations)
    setAlertSourcePref(settings.alertSourcePref)
    setRadarProvider(settings.radarProvider)
    setIconStyle(settings.iconStyle)
    setCustomIconPackId(settings.customIconPackId)
    setThemeMode(settings.themeMode)
    setSummaryStyle(settings.summaryStyle)
    setCardOrder(settings.cardOrder)
    settings.disabledCards.forEach { cardName ->
        runCatching { CardType.valueOf(cardName) }.getOrNull()?.let { card ->
            setCardEnabled(card, false)
        }
    }
    CardType.entries
        .filterNot { it.name in settings.disabledCards }
        .forEach { setCardEnabled(it, true) }
    setPersistentWeatherNotif(settings.persistentWeatherNotif)
    setNowcastingAlerts(settings.nowcastingAlerts)
    setDailyBriefingEnabled(settings.dailyBriefingEnabled)
    setDailyBriefingMinutes(settings.dailyBriefingMinutes)
    setDrivingAlerts(settings.drivingAlerts)
    setHealthAlertsEnabled(settings.healthAlertsEnabled)
    setMigraineAlerts(settings.migraineAlerts)
    setShowSnowfall(settings.showSnowfall)
    setShowCape(settings.showCape)
    setShowSunshineDuration(settings.showSunshineDuration)
    setShowGoldenHour(settings.showGoldenHour)
    setShowBeaufortColors(settings.showBeaufortColors)
    setShowOutdoorScore(settings.showOutdoorScore)
    setShowYesterdayComparison(settings.showYesterdayComparison)
    setHourlyForecastHours(settings.hourlyForecastHours)
    setMigrainePressureThreshold(settings.migrainePressureThreshold)
    setHapticFeedbackForAlerts(settings.hapticFeedbackForAlerts)
    setCacheTtlMinutes(settings.cacheTtlMinutes)
    setSourceForecast(settings.sourceConfig.forecast)
    setSourceForecastFallback(settings.sourceConfig.forecastFallback)
    setSourceAlerts(settings.sourceConfig.alerts)
    setSourceAlertsFallback(settings.sourceConfig.alertsFallback)
    setSourceAirQuality(settings.sourceConfig.airQuality)
    setSourceMinutely(settings.sourceConfig.minutely)
    setOnboardingComplete(settings.onboardingComplete)
}

private suspend fun UserPreferences.applyImportedLastLocation(location: SavedLocation?) {
    if (location == null) {
        clearLastLocation()
    } else {
        saveLastLocation(location.latitude, location.longitude, location.name)
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)
