package com.sysadmindoc.nimbus.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sysadmindoc.nimbus.MainActivity
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings

/**
 * Manages the persistent current-weather notification in the notification shade.
 * Shows temp, condition, high/low, and updates periodically via worker.
 */
object WeatherNotificationHelper {

    private const val CHANNEL_ID = "nimbus_current_weather"
    private const val CHANNEL_NAME = "Current Weather"
    private const val NOTIFICATION_ID = 9000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent notification showing current weather conditions"
            setShowBadge(false)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun showOrUpdate(context: Context, data: WeatherData, s: NimbusSettings) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val temp = WeatherFormatter.formatTemperature(data.current.temperature, s)
        val condition = data.current.weatherCode.description
        val high = WeatherFormatter.formatTemperature(data.current.dailyHigh, s)
        val low = WeatherFormatter.formatTemperature(data.current.dailyLow, s)
        val feelsLike = WeatherFormatter.formatTemperature(data.current.feelsLike, s)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("$temp $condition \u2022 ${data.location.name}")
            .setContentText("Feels like $feelsLike \u2022 H:$high L:$low")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
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
