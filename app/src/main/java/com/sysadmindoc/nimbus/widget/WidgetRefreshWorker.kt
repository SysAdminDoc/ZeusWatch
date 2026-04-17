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
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.sync.WearSyncManager
import com.sysadmindoc.nimbus.data.repository.readPersistentWeatherNotificationEnabled
import com.sysadmindoc.nimbus.util.WeatherNotificationHelper
import com.sysadmindoc.nimbus.util.WeatherFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

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
        val widgetMappings = WidgetLocationPrefs.getAllMappings(applicationContext)

        // Skip refresh on critically low battery to preserve device life.
        // BATTERY_PROPERTY_CAPACITY returns Integer.MIN_VALUE when capacity is unknown
        // (emulators, devices without fuel gauge, read failure) — treat that as "unknown"
        // and proceed, rather than incorrectly skipping forever.
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        if (batteryLevel in 0..15) return Result.success()

        if (lastLoc == null && widgetMappings.isEmpty()) {
            if (settings.persistentWeatherNotif) {
                WeatherNotificationHelper.dismiss(applicationContext)
            }
            WidgetDataProvider.clearDefault(applicationContext)
            return Result.success()
        }

        // Helper to convert celsius to user's preferred unit
        val convertTemp: (Double) -> Double = { celsius ->
            when (settings.tempUnit) {
                com.sysadmindoc.nimbus.data.repository.TempUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
                com.sysadmindoc.nimbus.data.repository.TempUnit.CELSIUS -> celsius
            }
        }

        return try {
            val refreshedLocationKeys = mutableSetOf<String>()
            var refreshedAnyLocation = false
            var attemptedNetworkRefresh = false
            var primaryWeather: WeatherData? = null
            val savedLocations = runCatching { locationRepository.getAll() }.getOrElse { emptyList() }

            if (lastLoc != null) {
                attemptedNetworkRefresh = true
                primaryWeather = weatherRepository
                    .getWeather(lastLoc.latitude, lastLoc.longitude, lastLoc.name)
                    .getOrNull()

                if (primaryWeather != null) {
                    refreshedLocationKeys += locationKey(lastLoc.latitude, lastLoc.longitude)
                    WidgetDataProvider.save(applicationContext, buildWidgetData(primaryWeather, convertTemp))
                    refreshedAnyLocation = true
                    // Sync to watch in background
                    try { wearSyncManager.syncWeather(primaryWeather) } catch (_: Exception) {}
                }
            } else {
                WidgetDataProvider.clearDefault(applicationContext)
            }

            if (widgetMappings.isNotEmpty()) {
                val refreshPlan = buildWidgetRefreshPlan(widgetMappings, savedLocations)

                for (widgetId in refreshPlan.orphanedWidgetIds) {
                    WidgetLocationPrefs.removeWidget(applicationContext, widgetId)
                    WidgetDataProvider.remove(applicationContext, widgetId)
                }

                val primaryLocationKey = lastLoc?.let { locationKey(it.latitude, it.longitude) }

                for (request in refreshPlan.requests) {
                    try {
                        val locWeather = if (primaryWeather != null && request.key == primaryLocationKey) {
                            primaryWeather
                        } else {
                            attemptedNetworkRefresh = true
                            weatherRepository
                                .getWeather(request.latitude, request.longitude, request.representativeName)
                                .getOrNull()
                        } ?: continue

                        refreshedLocationKeys += request.key

                        for (assignment in request.assignments) {
                            WidgetDataProvider.save(
                                applicationContext,
                                buildWidgetData(locWeather, convertTemp, assignment.displayName),
                                assignment.appWidgetId,
                            )
                        }
                        refreshedAnyLocation = true
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "WidgetRefreshWorker",
                            "Failed to refresh widget location ${request.representativeName}",
                            e,
                        )
                    }
                }
            }

            if (refreshedAnyLocation || lastLoc == null) {
                NimbusSmallWidget().updateAll(applicationContext)
                NimbusMediumWidget().updateAll(applicationContext)
                NimbusLargeWidget().updateAll(applicationContext)
                NimbusForecastStripWidget().updateAll(applicationContext)
            }

            // Update persistent weather notification if enabled
            if (settings.persistentWeatherNotif) {
                if (primaryWeather != null) {
                    try {
                        WeatherNotificationHelper.showOrUpdate(applicationContext, primaryWeather, settings)
                    } catch (_: Exception) {}
                } else if (lastLoc == null) {
                    WeatherNotificationHelper.dismiss(applicationContext)
                }
            } else {
                WeatherNotificationHelper.dismiss(applicationContext)
            }

            // Proactively cache weather for all saved locations
            try {
                for (loc in savedLocations) {
                    if (!refreshedLocationKeys.add(locationKey(loc.latitude, loc.longitude))) continue
                    try {
                        weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
                    } catch (_: Exception) { /* Individual location failure is non-fatal */ }
                }
            } catch (_: Exception) { /* Non-fatal; widget update already succeeded */ }

            when {
                refreshedAnyLocation -> Result.success()
                attemptedNetworkRefresh -> Result.retry()
                else -> Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
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
            daily = buildWidgetDailyItems(weatherData.daily, locationToday, convertTemp),
            updatedAt = weatherData.lastUpdated.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    private fun locationKey(latitude: Double, longitude: Double): String {
        return widgetLocationKey(latitude, longitude)
    }

    companion object {
        private const val WORK_NAME = "nimbus_widget_refresh"

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
                30, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
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
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_manual_refresh",
                ExistingWorkPolicy.REPLACE,
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
                assignments = request.assignments.toList(),
            )
        },
        orphanedWidgetIds = orphanedWidgetIds,
    )
}

internal fun widgetLocationKey(latitude: Double, longitude: Double): String {
    return "${latitude.formatWidgetLocationKey()}:${longitude.formatWidgetLocationKey()}"
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
    convertTemp: (Double) -> Double,
): List<WidgetDaily> {
    return daily.take(7).map { day ->
        val label = when {
            today != null && day.date == today -> "Today"
            today != null && day.date == today.plusDays(1) -> "Tmrw"
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
    val assignments: MutableList<WidgetRefreshAssignment> = mutableListOf(),
)

private fun Double.formatWidgetLocationKey(): String = "%.4f".format(Locale.US, this)
