package com.sysadmindoc.nimbus.smartspacer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.sysadmindoc.nimbus.data.api.NimbusDatabase
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCachePayload
import com.sysadmindoc.nimbus.data.model.toWeatherData
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val Context.smartspacerPrefs: DataStore<Preferences> by preferencesDataStore(name = "nimbus_prefs")

internal class SmartspacerWeatherCache(
    private val context: Context,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val database: NimbusDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(context, NimbusDatabase::class.java, "nimbus.db")
            .addMigrations(NimbusDatabase.MIGRATION_1_2)
            .addMigrations(NimbusDatabase.MIGRATION_2_3)
            .addMigrations(NimbusDatabase.MIGRATION_3_4)
            .addMigrations(NimbusDatabase.MIGRATION_4_5)
            .build()
    }

    suspend fun loadSnapshot(): SmartspacerWeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val cached = database.weatherDao()
                .getLatestWeatherData(WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION)
                ?: return@runCatching null
            if (nowMillis() - cached.cachedAt > WeatherRepository.DEFAULT_STALE_FALLBACK_MS) {
                return@runCatching null
            }
            val payload = json.decodeFromString(WeatherDataCachePayload.serializer(), cached.payloadJson)
            val observedAt = Instant.ofEpochMilli(cached.cachedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            val weather = payload.toWeatherData(lastUpdatedOverride = observedAt)
            buildSmartspacerWeatherSnapshot(
                weather = weather,
                tempUnit = readTempUnit(),
                referenceTime = now(),
            )
        }.getOrNull()
    }

    private suspend fun readTempUnit(): TempUnit {
        val tempUnitKey = stringPreferencesKey("temp_unit")
        return context.smartspacerPrefs.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs -> prefs[tempUnitKey].toTempUnitOrDefault() }
            .first()
    }
}

private fun String?.toTempUnitOrDefault(): TempUnit =
    this?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: TempUnit.FAHRENHEIT
