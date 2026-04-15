package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import java.util.Locale

private const val TAG = "AlertCheckWorker"

/**
 * Periodic background worker that checks for new severe weather alerts.
 *
 * Improvements over naive approach:
 * - Deduplicates: tracks seen alert IDs so the same alert is never re-notified.
 * - Multi-location: optionally checks all saved locations, not just last GPS.
 * - Expired filtering: skips alerts whose `expires` timestamp is in the past.
 * - Respects user preferences: early-exits if notifications disabled; filters by min severity.
 */
@HiltWorker
class AlertCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherSourceManager: WeatherSourceManager,
    private val locationRepository: LocationRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()

        // Respect user preference
        if (!settings.alertNotificationsEnabled) {
            Log.d(TAG, "Alert notifications disabled, skipping")
            return Result.success()
        }

        val seenIds = prefs.getSeenAlertIds()
        val maxSortOrder = settings.alertMinSeverity.maxSortOrder
        val newSeenIds = mutableSetOf<String>()

        // Determine locations to check
        val locations = if (settings.alertCheckAllLocations) {
            distinctAlertCheckLocations(
                locationRepository.getAll().map { Triple(it.latitude, it.longitude, it.name) }
            )
        } else {
            val lastLoc = prefs.lastLocation.first()
            if (lastLoc != null) listOf(Triple(lastLoc.latitude, lastLoc.longitude, lastLoc.name))
            else emptyList()
        }

        if (locations.isEmpty()) {
            Log.d(TAG, "No locations to check")
            return Result.success()
        }

        for ((lat, lon, locationName) in locations) {
            val result = weatherSourceManager.getAlerts(lat, lon)
            val alerts = result.getOrNull() ?: continue

            val filtered = alerts.filter { alert ->
                alert.severity.sortOrder <= maxSortOrder &&
                    alert.id !in seenIds &&
                    alert.id !in newSeenIds &&
                    !isExpired(alert)
            }

            Log.d(TAG, "Location=$locationName: ${alerts.size} total, ${filtered.size} new matching alerts")

            val showLocation = settings.alertCheckAllLocations && locations.size > 1
            for (alert in filtered) {
                val delivered = AlertNotificationHelper.showAlertNotification(
                    context = applicationContext,
                    alert = alert,
                    locationName = if (showLocation) locationName else null,
                )
                if (delivered) {
                    newSeenIds.add(alert.id)
                }
            }
        }

        if (newSeenIds.isNotEmpty()) {
            prefs.addSeenAlertIds(newSeenIds)
        }

        return Result.success()
    }

    private fun isExpired(alert: WeatherAlert): Boolean {
        val expiresStr = alert.expires ?: return false
        return try {
            val expires = OffsetDateTime.parse(expiresStr)
            expires.isBefore(OffsetDateTime.now())
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val WORK_NAME = "nimbus_alert_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
                15, TimeUnit.MINUTES,
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

internal fun distinctAlertCheckLocations(
    locations: List<Triple<Double, Double, String>>,
): List<Triple<Double, Double, String>> {
    val seen = linkedSetOf<String>()
    return locations.filter { (lat, lon, _) ->
        seen.add("${lat.alertLocationKey()}:${lon.alertLocationKey()}")
    }
}

private fun Double.alertLocationKey(): String = "%.4f".format(Locale.US, this)
