package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.Stable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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

data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
)
