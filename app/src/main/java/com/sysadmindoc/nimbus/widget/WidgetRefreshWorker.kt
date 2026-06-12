package com.sysadmindoc.nimbus.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.BatteryManager
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.SourceOverrides
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.readPersistentWeatherNotificationEnabled
import com.sysadmindoc.nimbus.data.repository.sourceOverrides
import com.sysadmindoc.nimbus.sync.WearSyncManager
import com.sysadmindoc.nimbus.util.WeatherNotificationHelper
import com.sysadmindoc.nimbus.util.WeatherFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetRefreshWorker"

internal const val WIDGET_BACKGROUND_REFRESH_INTERVAL_MINUTES = 15L
private const val WIDGET_BACKGROUND_REFRESH_FLEX_MINUTES = 5L
private const val WIDGET_BACKGROUND_REFRESH_BACKOFF_MINUTES = 10L
private const val WIDGET_REFRESH_MAX_RUN_ATTEMPTS = 3
internal const val WIDGET_BATTERY_SKIP_THRESHOLD_PERCENT = 15

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: UserPreferences,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val wearSyncManager: WearSyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()
        val lastLoc = prefs.lastLocation.first()
        // Cross-check stored widget mappings against the launcher's live ids —
        // a missed onDeleted (force-stop, crash) otherwise leaves orphaned
        // per-widget data refreshing forever.
        val widgetMappings = purgeDeletedWidgetMappings(
            WidgetLocationPrefs.getAllMappings(applicationContext),
        )
        val savedLocations = runCatching { locationRepository.getAll() }.getOrElse { emptyList() }

        // A user-initiated refresh (widget tap) must run even on low battery.
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        if (!forceRefresh && shouldSkipForBattery()) {
            // No network work — but don't let the watch and the persistent
            // notification silently starve: push the cached data we have.
            pushCachedDataOnly(settings, lastLoc)
            return Result.success()
        }

        if (lastLoc == null && widgetMappings.isEmpty() && savedCityLocationsForWidget(savedLocations).isEmpty()) {
            clearWidgetState(settings.persistentWeatherNotif)
            return Result.success()
        }

        return try {
            val state = WidgetRefreshState()
            val convertTemp = tempConverter(settings)

            refreshPrimaryLocation(lastLoc, convertTemp, state)
            refreshMappedWidgets(widgetMappings, savedLocations, lastLoc, convertTemp, state)
            updatePersistentNotification(settings, state.primaryWeather, lastLoc)
            cacheRemainingSavedLocations(savedLocations, state)
            saveSavedCitySummaries(savedLocations, convertTemp, state)
            updateWidgetsIfNeeded(state.refreshedAnyLocation, lastLoc)

            state.toWorkResult()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // WorkManager cancels the worker via the coroutine's Job; re-throw
            // so cooperative cancellation runs the surrounding teardown rather
            // than masking the cancel as a Result.retry() and tying the worker
            // up indefinitely.
            throw cancelled
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Widget refresh failed (attempt $runAttemptCount)", e)
            if (runAttemptCount >= WIDGET_REFRESH_MAX_RUN_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private data class WidgetRefreshState(
        var primaryWeather: WeatherData? = null,
        var refreshedAnyLocation: Boolean = false,
        var attemptedNetworkRefresh: Boolean = false,
        val refreshedLocationKeys: MutableSet<String> = mutableSetOf(),
        val weatherByLocationKey: MutableMap<String, WeatherData> = mutableMapOf(),
    ) {
        fun toWorkResult(): Result = when {
            refreshedAnyLocation -> Result.success()
            attemptedNetworkRefresh -> Result.retry()
            else -> Result.success()
        }
    }

    private fun shouldSkipForBattery(): Boolean {
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return shouldSkipWidgetRefreshForBattery(
            batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isCharging = batteryManager?.isCharging == true,
        )
    }

    /**
     * Removes mappings (and per-widget cached data) for widget ids the
     * launcher no longer knows about, returning only the live mappings.
     */
    private suspend fun purgeDeletedWidgetMappings(
        widgetMappings: Map<Int, Long>,
    ): Map<Int, Long> {
        if (widgetMappings.isEmpty()) return widgetMappings
        val liveIds = try {
            val manager = AppWidgetManager.getInstance(applicationContext)
            listOf(
                NimbusSmallWidgetReceiver::class.java,
                NimbusMediumWidgetReceiver::class.java,
                NimbusLargeWidgetReceiver::class.java,
                NimbusForecastStripWidgetReceiver::class.java,
            ).flatMap { receiver ->
                manager.getAppWidgetIds(ComponentName(applicationContext, receiver)).toList()
            }.toSet()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            // If the launcher query fails, keep everything — wrongly purging
            // a live widget's data is worse than carrying an orphan one cycle.
            android.util.Log.w(TAG, "Could not enumerate live widget ids", e)
            return widgetMappings
        }
        val orphanedIds = widgetMappings.keys.filter { it !in liveIds }
        for (widgetId in orphanedIds) {
            WidgetLocationPrefs.removeWidget(applicationContext, widgetId)
            WidgetDataProvider.remove(applicationContext, widgetId)
        }
        return if (orphanedIds.isEmpty()) widgetMappings else widgetMappings.filterKeys { it in liveIds }
    }

    /**
     * Battery-skip path: refreshes the wear sync and persistent notification
     * from already-cached weather (no network) so glanceable surfaces don't
     * silently go stale while we conserve power.
     */
    private suspend fun pushCachedDataOnly(settings: NimbusSettings, lastLoc: SavedLocation?) {
        if (lastLoc == null) return
        val cached = try {
            weatherRepository.getCachedWeather(lastLoc.latitude, lastLoc.longitude)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return

        try {
            wearSyncManager.syncWeather(cached)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
        if (settings.persistentWeatherNotif) {
            try {
                WeatherNotificationHelper.showOrUpdate(applicationContext, cached, settings)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun clearWidgetState(persistentWeatherNotificationEnabled: Boolean) {
        if (persistentWeatherNotificationEnabled) {
            WeatherNotificationHelper.dismiss(applicationContext)
        }
        WidgetDataProvider.clearDefault(applicationContext)
        WidgetDataProvider.clearSavedCities(applicationContext)
    }

    private fun tempConverter(settings: NimbusSettings): (Double) -> Double =
        { celsius ->
            when (settings.tempUnit) {
                TempUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
                TempUnit.CELSIUS -> celsius
            }
        }

    private suspend fun refreshPrimaryLocation(
        lastLoc: SavedLocation?,
        convertTemp: (Double) -> Double,
        state: WidgetRefreshState,
    ) {
        if (lastLoc == null) {
            WidgetDataProvider.clearDefault(applicationContext)
            return
        }

        state.attemptedNetworkRefresh = true
        state.primaryWeather = weatherRepository
            .getWeather(lastLoc.latitude, lastLoc.longitude, lastLoc.name)
            .getOrNull()

        val primaryWeather = state.primaryWeather ?: return
        state.refreshedLocationKeys += locationKey(lastLoc.latitude, lastLoc.longitude)
        state.weatherByLocationKey[locationKey(lastLoc.latitude, lastLoc.longitude)] = primaryWeather
        WidgetDataProvider.save(applicationContext, buildWidgetData(primaryWeather, convertTemp))
        state.refreshedAnyLocation = true
        try {
            wearSyncManager.syncWeather(primaryWeather)
        } catch (_: Exception) {
        }
    }

    private suspend fun refreshMappedWidgets(
        widgetMappings: Map<Int, Long>,
        savedLocations: List<SavedLocationEntity>,
        lastLoc: SavedLocation?,
        convertTemp: (Double) -> Double,
        state: WidgetRefreshState,
    ) {
        if (widgetMappings.isEmpty()) return

        val refreshPlan = buildWidgetRefreshPlan(widgetMappings, savedLocations)
        removeOrphanedWidgets(refreshPlan.orphanedWidgetIds)

        val primaryLocationKey = lastLoc?.let { locationKey(it.latitude, it.longitude) }
        for (request in refreshPlan.requests) {
            try {
                refreshMappedWidgetRequest(request, primaryLocationKey, convertTemp, state)
            } catch (e: Exception) {
                logWidgetRefreshFailure(request, e)
            }
        }
    }

    private suspend fun removeOrphanedWidgets(orphanedWidgetIds: List<Int>) {
        for (widgetId in orphanedWidgetIds) {
            WidgetLocationPrefs.removeWidget(applicationContext, widgetId)
            WidgetDataProvider.remove(applicationContext, widgetId)
        }
    }

    private suspend fun refreshMappedWidgetRequest(
        request: WidgetRefreshRequest,
        primaryLocationKey: String?,
        convertTemp: (Double) -> Double,
        state: WidgetRefreshState,
    ) {
        val locWeather = weatherForMappedWidget(request, primaryLocationKey, state) ?: return
        state.refreshedLocationKeys += request.key
        state.weatherByLocationKey[request.key] = locWeather

        for (assignment in request.assignments) {
            WidgetDataProvider.save(
                applicationContext,
                buildWidgetData(locWeather, convertTemp, assignment.displayName),
                assignment.appWidgetId,
            )
        }
        state.refreshedAnyLocation = true
    }

    private suspend fun weatherForMappedWidget(
        request: WidgetRefreshRequest,
        primaryLocationKey: String?,
        state: WidgetRefreshState,
    ): WeatherData? {
        val primaryWeather = state.primaryWeather
        if (primaryWeather != null && request.key == primaryLocationKey) {
            return primaryWeather
        }

        state.attemptedNetworkRefresh = true
        return weatherRepository
            .getWeather(
                request.latitude,
                request.longitude,
                request.representativeName,
                request.sourceOverrides,
            )
            .getOrNull()
    }

    private fun logWidgetRefreshFailure(
        request: WidgetRefreshRequest,
        error: Exception,
    ) {
        android.util.Log.w(
            TAG,
            "Failed to refresh widget location ${request.representativeName}",
            error,
        )
    }

    private suspend fun updateWidgetsIfNeeded(
        refreshedAnyLocation: Boolean,
        lastLoc: SavedLocation?,
    ) {
        if (!refreshedAnyLocation && lastLoc != null) return

        NimbusSmallWidget().updateAll(applicationContext)
        NimbusMediumWidget().updateAll(applicationContext)
        NimbusLargeWidget().updateAll(applicationContext)
        NimbusForecastStripWidget().updateAll(applicationContext)
        NimbusSavedCitiesWidget().updateAll(applicationContext)
    }

    private fun updatePersistentNotification(
        settings: NimbusSettings,
        primaryWeather: WeatherData?,
        lastLoc: SavedLocation?,
    ) {
        if (!settings.persistentWeatherNotif) {
            WeatherNotificationHelper.dismiss(applicationContext)
            return
        }

        if (primaryWeather != null) {
            try {
                WeatherNotificationHelper.showOrUpdate(applicationContext, primaryWeather, settings)
            } catch (_: Exception) {
            }
        } else if (lastLoc == null) {
            WeatherNotificationHelper.dismiss(applicationContext)
        }
    }

    private suspend fun cacheRemainingSavedLocations(
        savedLocations: List<SavedLocationEntity>,
        state: WidgetRefreshState,
    ) {
        try {
            for (loc in savedLocations) {
                val key = locationKey(loc.latitude, loc.longitude)
                if (!state.refreshedLocationKeys.add(key)) continue
                try {
                    weatherRepository.getWeather(loc).getOrNull()?.let { weather ->
                        state.weatherByLocationKey[key] = weather
                        state.refreshedAnyLocation = true
                    }
                } catch (_: Exception) {
                    // Individual location failure is non-fatal.
                }
            }
        } catch (_: Exception) {
            // Non-fatal; widget update already succeeded.
        }
    }

    private suspend fun saveSavedCitySummaries(
        savedLocations: List<SavedLocationEntity>,
        convertTemp: (Double) -> Double,
        state: WidgetRefreshState,
    ) {
        val cities = savedCityLocationsForWidget(savedLocations).map { location ->
            val key = locationKey(location.latitude, location.longitude)
            val weather = state.weatherByLocationKey[key]
                ?: weatherRepository.getCachedWeather(location.latitude, location.longitude)
            buildWidgetSavedCity(location, weather, convertTemp)
        }
        if (cities.isEmpty()) {
            WidgetDataProvider.clearSavedCities(applicationContext)
        } else {
            WidgetDataProvider.saveSavedCities(applicationContext, cities)
            state.refreshedAnyLocation = true
        }
    }

    private fun buildWidgetData(
        weatherData: WeatherData,
        convertTemp: (Double) -> Double,
        displayLocationName: String = weatherData.location.name,
    ): WidgetWeatherData {
        val locationToday = weatherData.daily.firstOrNull()?.date
            ?: weatherData.hourly.firstOrNull()?.time?.toLocalDate()
        return WidgetWeatherData(
            locationName = displayLocationName,
            temperature = convertTemp(weatherData.current.temperature),
            feelsLike = convertTemp(weatherData.current.feelsLike),
            high = convertTemp(weatherData.current.dailyHigh),
            low = convertTemp(weatherData.current.dailyLow),
            weatherCode = weatherData.current.weatherCode.code,
            isDay = weatherData.current.isDay,
            humidity = weatherData.current.humidity,
            windSpeed = weatherData.current.windSpeed,
            hourly = buildWidgetHourlyItems(
                weatherData.hourly,
                weatherData.current.observationTime,
                convertTemp,
            ),
            daily = buildWidgetDailyItems(
                daily = weatherData.daily,
                today = locationToday,
                todayLabel = applicationContext.getString(R.string.today),
                tomorrowLabel = applicationContext.getString(R.string.widget_tomorrow_short),
                convertTemp = convertTemp,
            ),
            updatedAt = weatherData.lastUpdated.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    private fun locationKey(latitude: Double, longitude: Double): String {
        return widgetLocationKey(latitude, longitude)
    }

    companion object {
        private const val WORK_NAME = "nimbus_widget_refresh"
        internal const val KEY_FORCE_REFRESH = "force"

        suspend fun syncFromPreferences(context: Context) {
            sync(context, context.readPersistentWeatherNotificationEnabled())
        }

        fun sync(context: Context, persistentWeatherNotif: Boolean) {
            if (persistentWeatherNotif || hasAnyWidgets(context)) {
                schedule(context)
            } else {
                cancel(context)
            }
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                WIDGET_BACKGROUND_REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES,
                WIDGET_BACKGROUND_REFRESH_FLEX_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WIDGET_BACKGROUND_REFRESH_BACKOFF_MINUTES,
                    TimeUnit.MINUTES,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // Manual refresh bypasses the low-battery skip — the user
                // explicitly asked for fresh data.
                .setInputData(workDataOf(KEY_FORCE_REFRESH to true))
                .build()

            // KEEP, not REPLACE: a user double-tapping the widget (or tapping
            // while a refresh is already running its multi-location network
            // loop) must not cancel the in-flight worker. REPLACE would throw
            // CancellationException, discard partial progress, and under flaky
            // network can livelock — each tap killing the previous attempt.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_manual_refresh",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun hasAnyWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return listOf(
                NimbusSmallWidgetReceiver::class.java,
                NimbusMediumWidgetReceiver::class.java,
                NimbusLargeWidgetReceiver::class.java,
                NimbusForecastStripWidgetReceiver::class.java,
                NimbusSavedCitiesWidgetReceiver::class.java,
            ).any { receiver ->
                manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
            }
        }
    }
}

internal data class WidgetRefreshPlan(
    val requests: List<WidgetRefreshRequest>,
    val orphanedWidgetIds: List<Int>,
)

internal data class WidgetRefreshRequest(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val representativeName: String,
    val sourceOverrides: SourceOverrides,
    val assignments: List<WidgetRefreshAssignment>,
)

internal data class WidgetRefreshAssignment(
    val appWidgetId: Int,
    val displayName: String,
)

internal fun buildWidgetRefreshPlan(
    widgetMappings: Map<Int, Long>,
    savedLocations: List<SavedLocationEntity>,
): WidgetRefreshPlan {
    val savedLocationsById = savedLocations.associateBy { it.id }
    val requestsByKey = linkedMapOf<String, MutableWidgetRefreshRequest>()
    val orphanedWidgetIds = mutableListOf<Int>()

    for ((appWidgetId, locationId) in widgetMappings) {
        val location = savedLocationsById[locationId]
        if (location == null) {
            orphanedWidgetIds += appWidgetId
            continue
        }

        val key = widgetLocationKey(location.latitude, location.longitude)
        val request = requestsByKey.getOrPut(key) {
            MutableWidgetRefreshRequest(
                latitude = location.latitude,
                longitude = location.longitude,
                representativeName = location.name,
                sourceOverrides = location.sourceOverrides(),
            )
        }
        request.assignments += WidgetRefreshAssignment(
            appWidgetId = appWidgetId,
            displayName = location.name,
        )
    }

    return WidgetRefreshPlan(
        requests = requestsByKey.map { (key, request) ->
            WidgetRefreshRequest(
                key = key,
                latitude = request.latitude,
                longitude = request.longitude,
                representativeName = request.representativeName,
                sourceOverrides = request.sourceOverrides,
                assignments = request.assignments.toList(),
            )
        },
        orphanedWidgetIds = orphanedWidgetIds,
    )
}

internal fun widgetLocationKey(latitude: Double, longitude: Double): String {
    return "${latitude.formatWidgetLocationKey()}:${longitude.formatWidgetLocationKey()}"
}

internal fun savedCityLocationsForWidget(
    savedLocations: List<SavedLocationEntity>,
    limit: Int = 5,
): List<SavedLocationEntity> {
    return savedLocations
        .asSequence()
        .filterNot { it.isCurrentLocation }
        .sortedWith(
            compareBy<SavedLocationEntity> { it.sortOrder }
                .thenBy { it.name.lowercase(Locale.getDefault()) },
        )
        .take(limit)
        .toList()
}

internal fun buildWidgetSavedCity(
    location: SavedLocationEntity,
    weatherData: WeatherData?,
    convertTemp: (Double) -> Double,
): WidgetSavedCity {
    return WidgetSavedCity(
        locationId = location.id,
        locationName = location.name,
        temperature = weatherData?.current?.temperature?.let(convertTemp)?.toInt(),
        high = weatherData?.current?.dailyHigh?.let(convertTemp)?.toInt(),
        low = weatherData?.current?.dailyLow?.let(convertTemp)?.toInt(),
        weatherCode = weatherData?.current?.weatherCode?.code,
        isDay = weatherData?.current?.isDay ?: true,
        updatedAt = weatherData?.lastUpdated
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: 0L,
    )
}

/**
 * Background refresh is skipped only when the battery is critically low AND
 * the device isn't charging. Unknown capacity (emulators, no fuel gauge,
 * read failure) proceeds rather than skipping widget updates forever.
 */
internal fun shouldSkipWidgetRefreshForBattery(batteryLevel: Int?, isCharging: Boolean): Boolean {
    if (isCharging) return false
    return (batteryLevel ?: 100) in 0..WIDGET_BATTERY_SKIP_THRESHOLD_PERCENT
}

internal fun buildWidgetHourlyItems(
    hourly: List<HourlyConditions>,
    referenceTime: java.time.LocalDateTime?,
    convertTemp: (Double) -> Double,
): List<WidgetHourly> {
    return hourly.take(12).map { hour ->
        WidgetHourly(
            hour = WeatherFormatter.formatRelativeHourLabel(hour.time, referenceTime),
            temp = convertTemp(hour.temperature).toInt(),
            code = hour.weatherCode.code,
            isDay = hour.isDay,
            precipChance = hour.precipitationProbability,
        )
    }
}

internal fun buildWidgetDailyItems(
    daily: List<DailyConditions>,
    today: LocalDate?,
    todayLabel: String = "Today",
    tomorrowLabel: String = "Tmrw",
    convertTemp: (Double) -> Double,
): List<WidgetDaily> {
    return daily.take(7).map { day ->
        val label = when {
            today != null && day.date == today -> todayLabel
            today != null && day.date == today.plusDays(1) -> tomorrowLabel
            else -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        WidgetDaily(
            day = label,
            high = convertTemp(day.temperatureHigh).toInt(),
            low = convertTemp(day.temperatureLow).toInt(),
            code = day.weatherCode.code,
            precipChance = day.precipitationProbability,
        )
    }
}

private data class MutableWidgetRefreshRequest(
    val latitude: Double,
    val longitude: Double,
    val representativeName: String,
    val sourceOverrides: SourceOverrides,
    val assignments: MutableList<WidgetRefreshAssignment> = mutableListOf(),
)

private fun Double.formatWidgetLocationKey(): String = "%.4f".format(Locale.US, this)
