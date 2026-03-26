package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.Stable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nimbus_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // Keys
    private object Keys {
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val WIND_UNIT = stringPreferencesKey("wind_unit")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val PRECIP_UNIT = stringPreferencesKey("precip_unit")
        val VISIBILITY_UNIT = stringPreferencesKey("visibility_unit")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val PARTICLES_ENABLED = booleanPreferencesKey("particles_enabled")
        val ALERT_NOTIFICATIONS_ENABLED = booleanPreferencesKey("alert_notifications_enabled")
        val ALERT_MIN_SEVERITY = stringPreferencesKey("alert_min_severity")
        val ALERT_CHECK_ALL_LOCATIONS = booleanPreferencesKey("alert_check_all_locations")
        // Display
        val RADAR_PROVIDER = stringPreferencesKey("radar_provider")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SUMMARY_STYLE = stringPreferencesKey("summary_style")

        // Card config (stored as JSON strings)
        val CARD_ORDER = stringPreferencesKey("card_order")
        val DISABLED_CARDS = stringSetPreferencesKey("disabled_cards")

        // Notifications
        val PERSISTENT_WEATHER_NOTIF = booleanPreferencesKey("persistent_weather_notif")
        val NOWCASTING_ALERTS = booleanPreferencesKey("nowcasting_alerts")
        val DRIVING_ALERTS = booleanPreferencesKey("driving_alerts")
        val HEALTH_ALERTS_ENABLED = booleanPreferencesKey("health_alerts_enabled")
        val MIGRAINE_ALERTS = booleanPreferencesKey("migraine_alerts")

        // Data display toggles
        val SHOW_SNOWFALL = booleanPreferencesKey("show_snowfall")
        val SHOW_CAPE = booleanPreferencesKey("show_cape")
        val SHOW_SUNSHINE_DURATION = booleanPreferencesKey("show_sunshine_duration")
        val SHOW_GOLDEN_HOUR = booleanPreferencesKey("show_golden_hour")
        val SHOW_BEAUFORT_COLORS = booleanPreferencesKey("show_beaufort_colors")
        val SHOW_OUTDOOR_SCORE = booleanPreferencesKey("show_outdoor_score")
        val SHOW_YESTERDAY_COMPARISON = booleanPreferencesKey("show_yesterday_comparison")

        // Health thresholds
        val MIGRAINE_PRESSURE_THRESHOLD = stringPreferencesKey("migraine_pressure_threshold")

        // Haptics
        val HAPTIC_FEEDBACK_FOR_ALERTS = booleanPreferencesKey("haptic_alerts")

        val SEEN_ALERT_IDS = stringSetPreferencesKey("seen_alert_ids")
        val LAST_LAT = stringPreferencesKey("last_lat")
        val LAST_LON = stringPreferencesKey("last_lon")
        val LAST_LOCATION_NAME = stringPreferencesKey("last_location_name")
    }

    val settings: Flow<NimbusSettings> = store.data.map { prefs ->
        NimbusSettings(
            tempUnit = TempUnit.valueOf(prefs[Keys.TEMP_UNIT] ?: TempUnit.FAHRENHEIT.name),
            windUnit = WindUnit.valueOf(prefs[Keys.WIND_UNIT] ?: WindUnit.MPH.name),
            pressureUnit = PressureUnit.valueOf(prefs[Keys.PRESSURE_UNIT] ?: PressureUnit.INHG.name),
            precipUnit = PrecipUnit.valueOf(prefs[Keys.PRECIP_UNIT] ?: PrecipUnit.INCHES.name),
            visibilityUnit = VisibilityUnit.valueOf(prefs[Keys.VISIBILITY_UNIT] ?: VisibilityUnit.MILES.name),
            timeFormat = TimeFormat.valueOf(prefs[Keys.TIME_FORMAT] ?: TimeFormat.TWELVE_HOUR.name),
            particlesEnabled = prefs[Keys.PARTICLES_ENABLED] ?: true,
            alertNotificationsEnabled = prefs[Keys.ALERT_NOTIFICATIONS_ENABLED] ?: true,
            alertMinSeverity = AlertMinSeverity.valueOf(
                prefs[Keys.ALERT_MIN_SEVERITY] ?: AlertMinSeverity.SEVERE.name
            ),
            alertCheckAllLocations = prefs[Keys.ALERT_CHECK_ALL_LOCATIONS] ?: false,
            // Display
            radarProvider = RadarProvider.valueOf(prefs[Keys.RADAR_PROVIDER] ?: RadarProvider.WINDY_WEBVIEW.name),
            iconStyle = IconStyle.valueOf(prefs[Keys.ICON_STYLE] ?: IconStyle.MATERIAL.name),
            themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.STATIC_DARK.name),
            summaryStyle = SummaryStyle.valueOf(prefs[Keys.SUMMARY_STYLE] ?: SummaryStyle.TEMPLATE.name),
            // Card config
            cardOrder = (prefs[Keys.CARD_ORDER] ?: "").let { orderStr ->
                if (orderStr.isBlank()) DEFAULT_CARD_ORDER
                else orderStr.split(",").mapNotNull { name ->
                    try { CardType.valueOf(name) } catch (_: Exception) { null }
                }.let { parsed ->
                    // Add any missing cards at the end
                    val missing = DEFAULT_CARD_ORDER.filter { it !in parsed }
                    parsed + missing
                }
            },
            disabledCards = prefs[Keys.DISABLED_CARDS] ?: emptySet(),
            // Notifications
            persistentWeatherNotif = prefs[Keys.PERSISTENT_WEATHER_NOTIF] ?: false,
            nowcastingAlerts = prefs[Keys.NOWCASTING_ALERTS] ?: false,
            drivingAlerts = prefs[Keys.DRIVING_ALERTS] ?: false,
            healthAlertsEnabled = prefs[Keys.HEALTH_ALERTS_ENABLED] ?: false,
            migraineAlerts = prefs[Keys.MIGRAINE_ALERTS] ?: false,
            // Data display
            showSnowfall = prefs[Keys.SHOW_SNOWFALL] ?: true,
            showCape = prefs[Keys.SHOW_CAPE] ?: true,
            showSunshineDuration = prefs[Keys.SHOW_SUNSHINE_DURATION] ?: true,
            showGoldenHour = prefs[Keys.SHOW_GOLDEN_HOUR] ?: true,
            showBeaufortColors = prefs[Keys.SHOW_BEAUFORT_COLORS] ?: true,
            showOutdoorScore = prefs[Keys.SHOW_OUTDOOR_SCORE] ?: true,
            showYesterdayComparison = prefs[Keys.SHOW_YESTERDAY_COMPARISON] ?: true,
            // Health
            migrainePressureThreshold = prefs[Keys.MIGRAINE_PRESSURE_THRESHOLD]?.toDoubleOrNull() ?: 5.0,
            // Haptics
            hapticFeedbackForAlerts = prefs[Keys.HAPTIC_FEEDBACK_FOR_ALERTS] ?: true,
        )
    }

    val lastLocation: Flow<SavedLocation?> = store.data.map { prefs ->
        val lat = prefs[Keys.LAST_LAT]?.toDoubleOrNull() ?: return@map null
        val lon = prefs[Keys.LAST_LON]?.toDoubleOrNull() ?: return@map null
        val name = prefs[Keys.LAST_LOCATION_NAME] ?: return@map null
        SavedLocation(lat, lon, name)
    }

    suspend fun setTempUnit(unit: TempUnit) = store.edit { it[Keys.TEMP_UNIT] = unit.name }
    suspend fun setWindUnit(unit: WindUnit) = store.edit { it[Keys.WIND_UNIT] = unit.name }
    suspend fun setPressureUnit(unit: PressureUnit) = store.edit { it[Keys.PRESSURE_UNIT] = unit.name }
    suspend fun setPrecipUnit(unit: PrecipUnit) = store.edit { it[Keys.PRECIP_UNIT] = unit.name }
    suspend fun setVisibilityUnit(unit: VisibilityUnit) = store.edit { it[Keys.VISIBILITY_UNIT] = unit.name }
    suspend fun setTimeFormat(format: TimeFormat) = store.edit { it[Keys.TIME_FORMAT] = format.name }
    suspend fun setParticlesEnabled(enabled: Boolean) = store.edit { it[Keys.PARTICLES_ENABLED] = enabled }
    suspend fun setAlertNotificationsEnabled(enabled: Boolean) = store.edit { it[Keys.ALERT_NOTIFICATIONS_ENABLED] = enabled }
    suspend fun setAlertMinSeverity(severity: AlertMinSeverity) = store.edit { it[Keys.ALERT_MIN_SEVERITY] = severity.name }
    suspend fun setAlertCheckAllLocations(enabled: Boolean) = store.edit { it[Keys.ALERT_CHECK_ALL_LOCATIONS] = enabled }

    // Display
    suspend fun setRadarProvider(provider: RadarProvider) = store.edit { it[Keys.RADAR_PROVIDER] = provider.name }
    suspend fun setIconStyle(style: IconStyle) = store.edit { it[Keys.ICON_STYLE] = style.name }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setSummaryStyle(style: SummaryStyle) = store.edit { it[Keys.SUMMARY_STYLE] = style.name }

    // Card config
    suspend fun setCardOrder(order: List<CardType>) = store.edit { it[Keys.CARD_ORDER] = order.joinToString(",") { c -> c.name } }
    suspend fun setCardEnabled(card: CardType, enabled: Boolean) = store.edit { prefs ->
        val current = prefs[Keys.DISABLED_CARDS] ?: emptySet()
        prefs[Keys.DISABLED_CARDS] = if (enabled) current - card.name else current + card.name
    }

    // Notifications
    suspend fun setPersistentWeatherNotif(enabled: Boolean) = store.edit { it[Keys.PERSISTENT_WEATHER_NOTIF] = enabled }
    suspend fun setNowcastingAlerts(enabled: Boolean) = store.edit { it[Keys.NOWCASTING_ALERTS] = enabled }
    suspend fun setDrivingAlerts(enabled: Boolean) = store.edit { it[Keys.DRIVING_ALERTS] = enabled }
    suspend fun setHealthAlertsEnabled(enabled: Boolean) = store.edit { it[Keys.HEALTH_ALERTS_ENABLED] = enabled }
    suspend fun setMigraineAlerts(enabled: Boolean) = store.edit { it[Keys.MIGRAINE_ALERTS] = enabled }

    // Data display
    suspend fun setShowSnowfall(enabled: Boolean) = store.edit { it[Keys.SHOW_SNOWFALL] = enabled }
    suspend fun setShowCape(enabled: Boolean) = store.edit { it[Keys.SHOW_CAPE] = enabled }
    suspend fun setShowSunshineDuration(enabled: Boolean) = store.edit { it[Keys.SHOW_SUNSHINE_DURATION] = enabled }
    suspend fun setShowGoldenHour(enabled: Boolean) = store.edit { it[Keys.SHOW_GOLDEN_HOUR] = enabled }
    suspend fun setShowBeaufortColors(enabled: Boolean) = store.edit { it[Keys.SHOW_BEAUFORT_COLORS] = enabled }
    suspend fun setShowOutdoorScore(enabled: Boolean) = store.edit { it[Keys.SHOW_OUTDOOR_SCORE] = enabled }
    suspend fun setShowYesterdayComparison(enabled: Boolean) = store.edit { it[Keys.SHOW_YESTERDAY_COMPARISON] = enabled }

    // Health
    suspend fun setMigrainePressureThreshold(threshold: Double) = store.edit { it[Keys.MIGRAINE_PRESSURE_THRESHOLD] = threshold.toString() }

    // Haptics
    suspend fun setHapticFeedbackForAlerts(enabled: Boolean) = store.edit { it[Keys.HAPTIC_FEEDBACK_FOR_ALERTS] = enabled }

    /** Returns the set of alert IDs the user has already been notified about. */
    suspend fun getSeenAlertIds(): Set<String> = store.data.map { it[Keys.SEEN_ALERT_IDS] ?: emptySet() }.first()

    /** Mark alert IDs as seen. Caps at 200 entries to prevent unbounded growth. */
    suspend fun addSeenAlertIds(ids: Set<String>) = store.edit { prefs ->
        val existing = prefs[Keys.SEEN_ALERT_IDS] ?: emptySet()
        val merged = (existing + ids).let {
            if (it.size > 200) it.drop(it.size - 200).toSet() else it
        }
        prefs[Keys.SEEN_ALERT_IDS] = merged
    }

    suspend fun saveLastLocation(lat: Double, lon: Double, name: String) = store.edit {
        it[Keys.LAST_LAT] = lat.toString()
        it[Keys.LAST_LON] = lon.toString()
        it[Keys.LAST_LOCATION_NAME] = name
    }
}

@Stable
data class NimbusSettings(
    val tempUnit: TempUnit = TempUnit.FAHRENHEIT,
    val windUnit: WindUnit = WindUnit.MPH,
    val pressureUnit: PressureUnit = PressureUnit.INHG,
    val precipUnit: PrecipUnit = PrecipUnit.INCHES,
    val visibilityUnit: VisibilityUnit = VisibilityUnit.MILES,
    val timeFormat: TimeFormat = TimeFormat.TWELVE_HOUR,
    val particlesEnabled: Boolean = true,
    val alertNotificationsEnabled: Boolean = true,
    val alertMinSeverity: AlertMinSeverity = AlertMinSeverity.SEVERE,
    val alertCheckAllLocations: Boolean = false,
    // Display
    val radarProvider: RadarProvider = RadarProvider.WINDY_WEBVIEW,
    val iconStyle: IconStyle = IconStyle.MATERIAL,
    val themeMode: ThemeMode = ThemeMode.STATIC_DARK,
    val summaryStyle: SummaryStyle = SummaryStyle.TEMPLATE,
    // Card config
    val disabledCards: Set<String> = emptySet(),
    val cardOrder: List<CardType> = DEFAULT_CARD_ORDER,
    // Notifications
    val persistentWeatherNotif: Boolean = false,
    val nowcastingAlerts: Boolean = false,
    val drivingAlerts: Boolean = false,
    val healthAlertsEnabled: Boolean = false,
    val migraineAlerts: Boolean = false,
    // Data display
    val showSnowfall: Boolean = true,
    val showCape: Boolean = true,
    val showSunshineDuration: Boolean = true,
    val showGoldenHour: Boolean = true,
    val showBeaufortColors: Boolean = true,
    val showOutdoorScore: Boolean = true,
    val showYesterdayComparison: Boolean = true,
    // Health
    val migrainePressureThreshold: Double = 5.0,
    // Haptics
    val hapticFeedbackForAlerts: Boolean = true,
)

enum class TempUnit(val label: String, val symbol: String) {
    FAHRENHEIT("Fahrenheit", "\u00B0F"),
    CELSIUS("Celsius", "\u00B0C"),
}

enum class WindUnit(val label: String, val symbol: String) {
    MPH("mph", "mph"),
    KMH("km/h", "km/h"),
    MS("m/s", "m/s"),
    KNOTS("knots", "kn"),
}

enum class PressureUnit(val label: String, val symbol: String) {
    INHG("inHg", "inHg"),
    HPA("hPa", "hPa"),
    MBAR("mbar", "mbar"),
}

enum class PrecipUnit(val label: String, val symbol: String) {
    INCHES("inches", "in"),
    MM("mm", "mm"),
}

enum class VisibilityUnit(val label: String, val symbol: String) {
    MILES("miles", "mi"),
    KM("km", "km"),
}

enum class TimeFormat(val label: String) {
    TWELVE_HOUR("12-hour"),
    TWENTY_FOUR_HOUR("24-hour"),
}

enum class AlertMinSeverity(val label: String, val maxSortOrder: Int) {
    EXTREME("Extreme only", 0),
    SEVERE("Severe and above", 1),
    MODERATE("Moderate and above", 2),
    ALL("All alerts", 4),
}

enum class RadarProvider(val label: String) {
    WINDY_WEBVIEW("Windy (WebView)"),
    NATIVE_MAPLIBRE("Native Map (MapLibre)"),
}

enum class IconStyle(val label: String) {
    MATERIAL("Material Icons"),
    METEOCONS("Animated (Meteocons)"),
}

enum class ThemeMode(val label: String) {
    STATIC_DARK("Dark"),
    WEATHER_ADAPTIVE("Weather Adaptive"),
}

enum class SummaryStyle(val label: String) {
    TEMPLATE("Standard"),
    AI_GENERATED("AI-Generated (Gemini)"),
}

data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
)
