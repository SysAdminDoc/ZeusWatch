package com.sysadmindoc.nimbus.widget

import android.content.Context
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
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.model.CurrentWeather
import com.sysadmindoc.nimbus.data.model.DailyWeather
import com.sysadmindoc.nimbus.data.model.HourlyWeather
import com.sysadmindoc.nimbus.data.model.WeatherCode
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import android.os.BatteryManager
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherApi: OpenMeteoApi,
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
            val response = weatherApi.getForecast(lastLoc.latitude, lastLoc.longitude)
            val current = response.current ?: return Result.failure()
            val hourly = response.hourly
            val daily = response.daily

            val data = buildWidgetData(lastLoc.name, current, hourly, daily, convertTemp)
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
                        val locResponse = weatherApi.getForecast(loc.latitude, loc.longitude)
                        val locCurrent = locResponse.current ?: continue

                        val locData = buildWidgetData(loc.name, locCurrent, locResponse.hourly, locResponse.daily, convertTemp)

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
                    val weatherData = weatherRepository.getWeather(lastLoc.latitude, lastLoc.longitude, lastLoc.name)
                    weatherData.getOrNull()?.let {
                        WeatherNotificationHelper.showOrUpdate(applicationContext, it, settings)
                    }
                } catch (_: Exception) {}
            } else {
                WeatherNotificationHelper.dismiss(applicationContext)
            }

            // Proactively cache weather for all saved locations
            try {
                val savedLocations = locationRepository.getAll()
                for (loc in savedLocations) {
                    if (loc.latitude == lastLoc.latitude && loc.longitude == lastLoc.longitude) continue
                    weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
                }
            } catch (_: Exception) { /* Non-fatal; widget update already succeeded */ }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun buildHourlyItems(
        hourly: HourlyWeather?,
        now: LocalDateTime,
        convertTemp: (Double) -> Double,
    ): List<WidgetHourly> {
        if (hourly == null) return emptyList()
        val items = mutableListOf<WidgetHourly>()
        for (i in hourly.time.indices) {
            val t = try {
                LocalDateTime.parse(hourly.time[i], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: Exception) { continue }
            if (t.isBefore(now.minusHours(1))) continue
            if (items.size >= 12) break
            items.add(WidgetHourly(
                hour = WeatherFormatter.formatHourLabel(t),
                temp = convertTemp(hourly.temperature?.getOrNull(i) ?: 0.0).toInt(),
                code = hourly.weatherCode?.getOrNull(i) ?: 0,
                isDay = (hourly.isDay?.getOrNull(i) ?: 1) == 1,
                precipChance = hourly.precipitationProbability?.getOrNull(i) ?: 0,
            ))
        }
        return items
    }

    private fun buildDailyItems(
        daily: DailyWeather?,
        today: LocalDate,
        convertTemp: (Double) -> Double,
    ): List<WidgetDaily> {
        if (daily == null) return emptyList()
        val items = mutableListOf<WidgetDaily>()
        for (i in daily.time.indices) {
            if (items.size >= 7) break
            val d = try {
                LocalDate.parse(daily.time[i], DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) { continue }
            val label = when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tmrw"
                else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            items.add(WidgetDaily(
                day = label,
                high = convertTemp(daily.temperatureMax?.getOrNull(i) ?: 0.0).toInt(),
                low = convertTemp(daily.temperatureMin?.getOrNull(i) ?: 0.0).toInt(),
                code = daily.weatherCode?.getOrNull(i) ?: 0,
                precipChance = daily.precipitationProbabilityMax?.getOrNull(i) ?: 0,
            ))
        }
        return items
    }

    private fun buildWidgetData(
        locationName: String,
        current: CurrentWeather,
        hourly: HourlyWeather?,
        daily: DailyWeather?,
        convertTemp: (Double) -> Double,
    ): WidgetWeatherData {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        return WidgetWeatherData(
            locationName = locationName,
            temperature = convertTemp(current.temperature ?: 0.0),
            feelsLike = convertTemp(current.apparentTemperature ?: current.temperature ?: 0.0),
            high = convertTemp(daily?.temperatureMax?.getOrNull(0) ?: current.temperature ?: 0.0),
            low = convertTemp(daily?.temperatureMin?.getOrNull(0) ?: current.temperature ?: 0.0),
            weatherCode = current.weatherCode ?: 0,
            isDay = (current.isDay ?: 1) == 1,
            humidity = current.humidity ?: 0,
            windSpeed = current.windSpeed ?: 0.0,
            hourly = buildHourlyItems(hourly, now, convertTemp),
            daily = buildDailyItems(daily, today, convertTemp),
        )
    }

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
