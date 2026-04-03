package com.sysadmindoc.nimbus.widget

import android.content.Context
import android.os.BatteryManager
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.util.WeatherNotificationHelper
import com.sysadmindoc.nimbus.util.WeatherFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val savedLocationDao: SavedLocationDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val lastLoc = prefs.lastLocation.first() ?: return Result.success()
        val settings = prefs.settings.first()

        // Skip refresh on critically low battery to preserve device life
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        if (batteryLevel <= 15) return Result.success()

        // Helper to convert celsius to user's preferred unit
        val convertTemp: (Double) -> Double = { celsius ->
            when (settings.tempUnit) {
                com.sysadmindoc.nimbus.data.repository.TempUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
                com.sysadmindoc.nimbus.data.repository.TempUnit.CELSIUS -> celsius
            }
        }

        return try {
            val refreshedLocationKeys = mutableSetOf(locationKey(lastLoc.latitude, lastLoc.longitude))
            val primaryWeather = weatherRepository
                .getWeather(lastLoc.latitude, lastLoc.longitude, lastLoc.name)
                .getOrNull()
                ?: return Result.retry()

            val data = buildWidgetData(primaryWeather, convertTemp)
            WidgetDataProvider.save(applicationContext, data)

            // Fetch per-widget location weather data
            val widgetMappings = WidgetLocationPrefs.getAllMappings(applicationContext)
            if (widgetMappings.isNotEmpty()) {
                // Group widgets by locationId to avoid duplicate API calls
                val locationToWidgets = mutableMapOf<Long, MutableList<Int>>()
                for ((widgetId, locId) in widgetMappings) {
                    locationToWidgets.getOrPut(locId) { mutableListOf() }.add(widgetId)
                }

                for ((locId, widgetIds) in locationToWidgets) {
                    try {
                        val loc = savedLocationDao.getById(locId) ?: continue
                        val locWeather = weatherRepository
                            .getWeather(loc.latitude, loc.longitude, loc.name)
                            .getOrNull()
                            ?: continue

                        refreshedLocationKeys += locationKey(loc.latitude, loc.longitude)
                        val locData = buildWidgetData(locWeather, convertTemp)

                        for (widgetId in widgetIds) {
                            WidgetDataProvider.save(applicationContext, locData, widgetId)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WidgetRefreshWorker", "Failed to refresh widget location $locId", e)
                    }
                }
            }

            // Trigger all widget updates
            NimbusSmallWidget().updateAll(applicationContext)
            NimbusMediumWidget().updateAll(applicationContext)
            NimbusLargeWidget().updateAll(applicationContext)
            NimbusForecastStripWidget().updateAll(applicationContext)

            // Update persistent weather notification if enabled
            if (settings.persistentWeatherNotif) {
                try {
                    WeatherNotificationHelper.showOrUpdate(applicationContext, primaryWeather, settings)
                } catch (_: Exception) {}
            } else {
                WeatherNotificationHelper.dismiss(applicationContext)
            }

            // Proactively cache weather for all saved locations
            try {
                val savedLocations = locationRepository.getAll()
                for (loc in savedLocations) {
                    if (!refreshedLocationKeys.add(locationKey(loc.latitude, loc.longitude))) continue
                    try {
                        weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
                    } catch (_: Exception) { /* Individual location failure is non-fatal */ }
                }
            } catch (_: Exception) { /* Non-fatal; widget update already succeeded */ }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun buildHourlyItems(
        hourly: List<HourlyConditions>,
        now: LocalDateTime,
        convertTemp: (Double) -> Double,
    ): List<WidgetHourly> {
        val items = mutableListOf<WidgetHourly>()
        for (hour in hourly) {
            val t = hour.time
            if (t.isBefore(now.minusHours(1))) continue
            if (items.size >= 12) break
            items.add(WidgetHourly(
                hour = WeatherFormatter.formatHourLabel(t),
                temp = convertTemp(hour.temperature).toInt(),
                code = hour.weatherCode.code,
                isDay = hour.isDay,
                precipChance = hour.precipitationProbability,
            ))
        }
        return items
    }

    private fun buildDailyItems(
        daily: List<DailyConditions>,
        today: LocalDate,
        convertTemp: (Double) -> Double,
    ): List<WidgetDaily> {
        val items = mutableListOf<WidgetDaily>()
        for (day in daily) {
            if (items.size >= 7) break
            val d = day.date
            val label = when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tmrw"
                else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            items.add(WidgetDaily(
                day = label,
                high = convertTemp(day.temperatureHigh).toInt(),
                low = convertTemp(day.temperatureLow).toInt(),
                code = day.weatherCode.code,
                precipChance = day.precipitationProbability,
            ))
        }
        return items
    }

    private fun buildWidgetData(
        weatherData: WeatherData,
        convertTemp: (Double) -> Double,
    ): WidgetWeatherData {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        return WidgetWeatherData(
            locationName = weatherData.location.name,
            temperature = convertTemp(weatherData.current.temperature),
            feelsLike = convertTemp(weatherData.current.feelsLike),
            high = convertTemp(weatherData.current.dailyHigh),
            low = convertTemp(weatherData.current.dailyLow),
            weatherCode = weatherData.current.weatherCode.code,
            isDay = weatherData.current.isDay,
            humidity = weatherData.current.humidity,
            windSpeed = weatherData.current.windSpeed,
            hourly = buildHourlyItems(weatherData.hourly, now, convertTemp),
            daily = buildDailyItems(weatherData.daily, today, convertTemp),
            updatedAt = weatherData.lastUpdated.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    private fun locationKey(latitude: Double, longitude: Double): String {
        return "${latitude.formatKey()}:${longitude.formatKey()}"
    }

    private fun Double.formatKey(): String = "%.4f".format(Locale.US, this)

    companion object {
        private const val WORK_NAME = "nimbus_widget_refresh"

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
    }
}
