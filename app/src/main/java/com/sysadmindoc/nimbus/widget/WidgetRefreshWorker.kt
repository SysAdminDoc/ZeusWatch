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
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.util.WeatherFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherApi: OpenMeteoApi,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val lastLoc = prefs.lastLocation.first() ?: return Result.success()
        val settings = prefs.settings.first()

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

            val now = LocalDateTime.now()

            // Build hourly list (next 12 hours)
            val hourlyItems = mutableListOf<WidgetHourly>()
            if (hourly != null) {
                val times = hourly.time
                for (i in times.indices) {
                    val t = try {
                        LocalDateTime.parse(times[i], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    } catch (_: Exception) { continue }
                    if (t.isBefore(now.minusHours(1))) continue
                    if (hourlyItems.size >= 12) break
                    hourlyItems.add(WidgetHourly(
                        hour = WeatherFormatter.formatHourLabel(t),
                        temp = convertTemp(hourly.temperature?.getOrNull(i) ?: 0.0).toInt(),
                        code = hourly.weatherCode?.getOrNull(i) ?: 0,
                        isDay = (hourly.isDay?.getOrNull(i) ?: 1) == 1,
                        precipChance = hourly.precipitationProbability?.getOrNull(i) ?: 0,
                    ))
                }
            }

            // Build daily list (next 7 days)
            val dailyItems = mutableListOf<WidgetDaily>()
            if (daily != null) {
                val today = LocalDate.now()
                for (i in daily.time.indices) {
                    if (dailyItems.size >= 7) break
                    val d = try {
                        LocalDate.parse(daily.time[i], DateTimeFormatter.ISO_LOCAL_DATE)
                    } catch (_: Exception) { continue }
                    val label = when (d) {
                        today -> "Today"
                        today.plusDays(1) -> "Tmrw"
                        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                    }
                    dailyItems.add(WidgetDaily(
                        day = label,
                        high = convertTemp(daily.temperatureMax?.getOrNull(i) ?: 0.0).toInt(),
                        low = convertTemp(daily.temperatureMin?.getOrNull(i) ?: 0.0).toInt(),
                        code = daily.weatherCode?.getOrNull(i) ?: 0,
                        precipChance = daily.precipitationProbabilityMax?.getOrNull(i) ?: 0,
                    ))
                }
            }

            val data = WidgetWeatherData(
                locationName = lastLoc.name,
                temperature = convertTemp(current.temperature ?: 0.0),
                feelsLike = convertTemp(current.apparentTemperature ?: current.temperature ?: 0.0),
                high = convertTemp(daily?.temperatureMax?.getOrNull(0) ?: current.temperature ?: 0.0),
                low = convertTemp(daily?.temperatureMin?.getOrNull(0) ?: current.temperature ?: 0.0),
                weatherCode = current.weatherCode ?: 0,
                isDay = (current.isDay ?: 1) == 1,
                humidity = current.humidity ?: 0,
                windSpeed = current.windSpeed ?: 0.0,
                hourly = hourlyItems,
                daily = dailyItems,
            )

            WidgetDataProvider.save(applicationContext, data)

            // Trigger all widget updates
            NimbusSmallWidget().updateAll(applicationContext)
            NimbusMediumWidget().updateAll(applicationContext)
            NimbusLargeWidget().updateAll(applicationContext)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
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
