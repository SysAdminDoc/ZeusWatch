package com.sysadmindoc.nimbus.smartspacer

import android.content.Context
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sysadmindoc.nimbus.data.api.NimbusDatabase
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCachePayload
import com.sysadmindoc.nimbus.data.model.toWeatherData
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.withUniqueHourlyTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId

internal class SmartspacerWeatherCache(
    private val context: Context,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val database: NimbusDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NimbusDatabase.buildOn(context)
    }

    suspend fun loadSnapshot(): SmartspacerWeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val weatherDao = database.weatherDao()
            val schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION
            // Prefer the GPS current location's cache row: the latest row of
            // ANY location would flip the surface to whichever secondary city
            // happened to refresh last.
            val cached = database.savedLocationDao().getCurrentLocation()
                ?.let { current ->
                    weatherDao.getLatestCurrentLocationWeatherData(
                        currentLocationKey = WeatherCacheEntity.makeKey(current.latitude, current.longitude),
                        schemaVersion = schemaVersion,
                    )
                }
                ?: weatherDao.getLatestWeatherData(schemaVersion)
                ?: return@runCatching null
            if (nowMillis() - cached.cachedAt > WeatherRepository.DEFAULT_STALE_FALLBACK_MS) {
                return@runCatching null
            }
            val payload = json.decodeFromString(WeatherDataCachePayload.serializer(), cached.payloadJson)
            val observedAt = Instant.ofEpochMilli(cached.cachedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            // Normalize pre-fix cached rows that may carry duplicate DST
            // fall-back hourly timestamps (live writes are deduped upstream).
            val weather = payload.toWeatherData(lastUpdatedOverride = observedAt)
                .withUniqueHourlyTimes()
            buildSmartspacerWeatherSnapshot(
                weather = weather,
                tempUnit = readTempUnitFromPreferencesFile(context),
                strings = SmartspacerWeatherStrings.from(context),
                referenceInstant = Instant.ofEpochMilli(nowMillis()),
            )
        }.getOrNull()
    }
}

/**
 * Reads the temperature unit straight from the main process's preferences
 * proto file. This process must NOT open its own DataStore on "nimbus_prefs":
 * `UserPreferences` already declares a `preferencesDataStore` delegate for
 * that file, and two active delegates on one file inside a single process
 * throw IllegalStateException forever after — order-dependent and silently
 * swallowed here. A per-call [PreferencesFileSerializer] read has no
 * single-instance requirement, is safe against concurrent writers (DataStore
 * commits via atomic rename), and always sees the latest committed value
 * instead of caching the first read for the process lifetime.
 */
internal suspend fun readTempUnitFromPreferencesFile(context: Context): TempUnit {
    val tempUnitKey = stringPreferencesKey("temp_unit")
    val file = File(context.filesDir, "datastore/nimbus_prefs.preferences_pb")
    val stored = runCatching {
        if (!file.exists()) return@runCatching null
        file.inputStream().use { input -> PreferencesFileSerializer.readFrom(input) }[tempUnitKey]
    }.getOrNull()
    return stored.toTempUnitOrDefault()
}

private fun String?.toTempUnitOrDefault(): TempUnit =
    this?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: TempUnit.FAHRENHEIT
