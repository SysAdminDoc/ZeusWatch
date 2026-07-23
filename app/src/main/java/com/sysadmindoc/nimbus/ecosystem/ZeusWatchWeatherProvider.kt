package com.sysadmindoc.nimbus.ecosystem

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.sysadmindoc.nimbus.data.api.NimbusDatabase
import com.sysadmindoc.nimbus.data.model.WEATHER_DATA_CACHE_SCHEMA_VERSION
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCachePayload
import com.sysadmindoc.nimbus.data.model.toWeatherData
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.sourceOverrides
import com.sysadmindoc.nimbus.data.repository.withUniqueHourlyTimes
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN1
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN1_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN2
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN2_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN3
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN3_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN4
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ADMIN4_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_CITY
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_COUNTRY
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_COUNTRY_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_CUSTOM_NAME
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_DISTRICT
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_ID
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_IS_CURRENT_POSITION
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_LATITUDE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_LONGITUDE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_TIMEZONE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.COLUMN_WEATHER
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.LOCATIONS_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.LOCATIONS_PATH
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.SCHEMA_MAJOR
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.SCHEMA_MINOR
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.VERSION_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.VERSION_PATH
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.WEATHER_CODE
import com.sysadmindoc.nimbus.ecosystem.ZeusWatchWeatherProviderContract.WEATHER_PATH
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Exposes the app's Hilt singletons to the ContentProvider, which Hilt cannot
 * field-inject directly. Reusing the @Singleton [UserPreferences] and
 * [NimbusDatabase] avoids constructing a duplicate Tink AEAD (Android
 * Keystore round-trip) and a second Room instance on binder threads.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ZeusWatchWeatherProviderEntryPoint {
    fun userPreferences(): UserPreferences
    fun database(): NimbusDatabase
}

class ZeusWatchWeatherProvider : ContentProvider() {
    private lateinit var appContext: android.content.Context

    private val entryPoint: ZeusWatchWeatherProviderEntryPoint by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EntryPointAccessors.fromApplication(appContext, ZeusWatchWeatherProviderEntryPoint::class.java)
    }

    private val database: NimbusDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        entryPoint.database()
    }

    private val userPreferences: UserPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        entryPoint.userPreferences()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val uriMatcher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        android.content.UriMatcher(android.content.UriMatcher.NO_MATCH).apply {
            val authority = ZeusWatchWeatherProviderContract.authority(appContext.packageName)
            addURI(authority, VERSION_PATH, VERSION_CODE)
            addURI(authority, LOCATIONS_PATH, LOCATIONS_CODE)
            addURI(authority, WEATHER_PATH, WEATHER_CODE)
        }
    }

    override fun onCreate(): Boolean {
        appContext = requireNotNull(context).applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = when (uriMatcher.match(uri)) {
        VERSION_CODE -> versionCursor()
        LOCATIONS_CODE -> locationsCursor(uri)
        WEATHER_CODE -> weatherCursor(uri, selection, selectionArgs)
        else -> null
    }

    override fun getType(uri: Uri): String? {
        // Same opt-in gate as the data paths: while the provider is disabled,
        // permission-holding callers see it as unavailable entirely.
        if (!providerEnabled()) return null
        return when (uriMatcher.match(uri)) {
            VERSION_CODE -> "vnd.android.cursor.item/vnd.com.sysadmindoc.nimbus.weather.version"
            LOCATIONS_CODE -> "vnd.android.cursor.dir/vnd.com.sysadmindoc.nimbus.weather.location"
            WEATHER_CODE -> "vnd.android.cursor.item/vnd.com.sysadmindoc.nimbus.weather.location"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun providerEnabled(): Boolean = runBlocking(Dispatchers.IO) {
        userPreferences.weatherContentProviderEnabled()
    }

    private fun versionCursor(): Cursor =
        MatrixCursor(ZeusWatchWeatherProviderContract.VERSION_COLUMNS).apply {
            if (providerEnabled()) {
                addRow(arrayOf(SCHEMA_MAJOR, SCHEMA_MINOR))
            }
        }

    private fun locationsCursor(uri: Uri): Cursor = runBlocking(Dispatchers.IO) {
        val cursor = MatrixCursor(ZeusWatchWeatherProviderContract.LOCATION_COLUMNS)
        if (!userPreferences.weatherContentProviderEnabled()) return@runBlocking cursor

        val coarse = userPreferences.weatherContentProviderCoarseLocation()
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.takeIf { it > 0 }
        val locations = database.savedLocationDao().getAll()
            .let { rows -> if (limit != null) rows.take(limit) else rows }
        locations.forEach { location ->
            cursor.addLocationRow(location.toBreezyLocationRow().maybeCoarsen(coarse))
        }
        cursor
    }

    private fun weatherCursor(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Cursor = runBlocking(Dispatchers.IO) {
        val cursor = MatrixCursor(ZeusWatchWeatherProviderContract.LOCATION_COLUMNS)
        if (!userPreferences.weatherContentProviderEnabled()) return@runBlocking cursor

        val selectedId = selectedLocationId(uri, selection, selectionArgs) ?: return@runBlocking cursor
        val location = selectedId.toLongOrNull()
            ?.let { database.savedLocationDao().getById(it) }
            ?: return@runBlocking cursor
        val settings = userPreferences.settings.first()
        val entity = cachedWeatherEntity(location, settings.cacheTtlMs) ?: return@runBlocking cursor
        val payload = runCatching {
            json.decodeFromString(WeatherDataCachePayload.serializer(), entity.payloadJson)
        }.getOrNull() ?: return@runBlocking cursor
        // Normalize pre-fix cached rows that may carry duplicate DST
        // fall-back hourly timestamps (live writes are deduped upstream).
        val weather = payload.toWeatherData(
            lastUpdatedOverride = Instant.ofEpochMilli(entity.cachedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime(),
        ).withUniqueHourlyTimes()
        val units = BreezyUnitPreferences.from(settings, uri)
        val row = location.toBreezyLocationRow(
            weather = weather.toBreezyWeatherBlob(
                units = units,
                refreshEpochMillis = entity.cachedAt,
                conditionLabel = { code -> code.localizedDescription(appContext) },
            ),
        ).maybeCoarsen(settings.weatherContentProviderCoarseLocation)
        cursor.addLocationRow(row)
        cursor
    }

    /** Applies coordinate coarsening only when the opt-in coarse mode is on. */
    private fun BreezyLocationRow.maybeCoarsen(coarse: Boolean): BreezyLocationRow =
        if (coarse) withCoarseCoordinates() else this

    private suspend fun cachedWeatherEntity(
        location: com.sysadmindoc.nimbus.data.model.SavedLocationEntity,
        cacheTtlMs: Long,
    ): WeatherDataCacheEntity? {
        val config = userPreferences.settings.first().sourceConfig.withOverrides(location.sourceOverrides())
        val providers = listOfNotNull(config.forecast, config.forecastFallback)
            .map { it.name }
            .distinct()
            .ifEmpty { listOf(com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider.OPEN_METEO.name) }
        return database.weatherDao().getCachedWeatherData(
            locationKey = WeatherCacheEntity.makeKey(location.latitude, location.longitude),
            savedLocationId = location.id.takeIf { it > 0L },
            sourceProviders = providers,
            schemaVersion = WEATHER_DATA_CACHE_SCHEMA_VERSION,
        )?.takeUnless { it.isExpired(cacheTtlMs) }
    }

    private fun MatrixCursor.addLocationRow(row: BreezyLocationRow) {
        newRow()
            .add(COLUMN_ID, row.id)
            .add(COLUMN_LATITUDE, row.latitude)
            .add(COLUMN_LONGITUDE, row.longitude)
            .add(COLUMN_IS_CURRENT_POSITION, if (row.isCurrentPosition) 1 else 0)
            .add(COLUMN_TIMEZONE, row.timeZone)
            .add(COLUMN_CUSTOM_NAME, row.customName)
            .add(COLUMN_COUNTRY, row.country)
            .add(COLUMN_COUNTRY_CODE, row.countryCode)
            .add(COLUMN_ADMIN1, row.admin1)
            .add(COLUMN_ADMIN1_CODE, row.admin1Code)
            .add(COLUMN_ADMIN2, row.admin2)
            .add(COLUMN_ADMIN2_CODE, row.admin2Code)
            .add(COLUMN_ADMIN3, row.admin3)
            .add(COLUMN_ADMIN3_CODE, row.admin3Code)
            .add(COLUMN_ADMIN4, row.admin4)
            .add(COLUMN_ADMIN4_CODE, row.admin4Code)
            .add(COLUMN_CITY, row.city)
            .add(COLUMN_DISTRICT, row.district)
            .add(COLUMN_WEATHER, row.weather)
    }

    private fun selectedLocationId(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): String? {
        if (selection != null) {
            if (selection.trim().matches(Regex("""id\s*=\s*\?"""))) {
                return selectionArgs?.firstOrNull()?.takeUnlessBlank()
            }
            Regex("""id\s*=\s*['"]?([^'"\s]+)['"]?""")
                .find(selection)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeUnlessBlank()
                ?.let { return it }
        }
        return uri.getQueryParameter("id")?.takeUnlessBlank()
    }
}

private fun String?.takeUnlessBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

