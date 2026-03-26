package com.sysadmindoc.nimbus.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sysadmindoc.nimbus.MainActivity
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.WeatherAlert

object AlertNotificationHelper {

    private const val GROUP_ID = "nimbus_alert_group"
    private const val GROUP_NAME = "Weather Alerts"
    private const val SUMMARY_NOTIFICATION_ID = 0

    // Separate channels per severity tier
    private const val CHANNEL_EXTREME = "nimbus_alerts_extreme"
    private const val CHANNEL_SEVERE = "nimbus_alerts_severe"
    private const val CHANNEL_MODERATE = "nimbus_alerts_moderate"
    private const val CHANNEL_MINOR = "nimbus_alerts_minor"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel group
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, GROUP_NAME)
        )

        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Extreme: tornado, tsunami, etc — max priority, alarm sound
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EXTREME, "Extreme Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                group = GROUP_ID
                description = "Life-threatening weather: tornado warnings, tsunami warnings, etc."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttr)
                setBypassDnd(true)
            }
        )

        // Severe: thunderstorm warnings, blizzard — high priority
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SEVERE, "Severe Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                group = GROUP_ID
                description = "Severe thunderstorm warnings, blizzard warnings, flood warnings, etc."
                enableVibration(true)
                enableLights(true)
            }
        )

        // Moderate: watches, advisories — default
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MODERATE, "Moderate Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                group = GROUP_ID
                description = "Weather watches and advisories."
            }
        )

        // Minor: statements, special weather statements — low
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MINOR, "Minor Alerts", NotificationManager.IMPORTANCE_LOW).apply {
                group = GROUP_ID
                description = "Special weather statements and minor advisories."
            }
        )

        // Delete the old single channel if it exists (clean migration)
        nm.deleteNotificationChannel("nimbus_weather_alerts")
    }

    fun channelForSeverity(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.EXTREME -> CHANNEL_EXTREME
        AlertSeverity.SEVERE -> CHANNEL_SEVERE
        AlertSeverity.MODERATE -> CHANNEL_MODERATE
        AlertSeverity.MINOR, AlertSeverity.UNKNOWN -> CHANNEL_MINOR
    }

    /**
     * Show individual alert notification + group summary.
     * [locationName] is included when monitoring multiple locations.
     */
    fun showAlertNotification(
        context: Context,
        alert: WeatherAlert,
        locationName: String? = null,
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, alert.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channel = channelForSeverity(alert.severity)

        val title = if (locationName != null) {
            "${alert.event} — $locationName"
        } else {
            alert.event
        }

        // Rich expanded content: headline + instruction summary
        val bigText = buildString {
            append(alert.headline)
            if (!alert.instruction.isNullOrBlank()) {
                append("\n\n")
                append(alert.instruction.take(300))
                if (alert.instruction.length > 300) append("...")
            }
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(alert.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(priorityForSeverity(alert.severity))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .setCategory(categoryForSeverity(alert.severity))
            .setColor(alert.severity.color.hashCode())
            .build()

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(alert.id.hashCode(), notification)

            // Group summary so multiple alerts collapse
            val summary = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle("Weather Alerts")
                .setContentText("Active weather alerts in your area")
                .setGroup(GROUP_ID)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            nm.notify(SUMMARY_NOTIFICATION_ID, summary)
        } catch (_: SecurityException) {
            // Permission revoked after check
        }
    }

    private fun priorityForSeverity(severity: AlertSeverity): Int = when (severity) {
        AlertSeverity.EXTREME -> NotificationCompat.PRIORITY_MAX
        AlertSeverity.SEVERE -> NotificationCompat.PRIORITY_HIGH
        AlertSeverity.MODERATE -> NotificationCompat.PRIORITY_DEFAULT
        AlertSeverity.MINOR, AlertSeverity.UNKNOWN -> NotificationCompat.PRIORITY_LOW
    }

    private fun categoryForSeverity(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.EXTREME -> NotificationCompat.CATEGORY_ALARM
        AlertSeverity.SEVERE -> NotificationCompat.CATEGORY_ALARM
        else -> NotificationCompat.CATEGORY_RECOMMENDATION
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
