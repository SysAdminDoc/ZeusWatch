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
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings

/**
 * Manages the persistent current-weather notification in the notification shade.
 * Shows temp, condition, high/low, and updates periodically via worker.
 */
object WeatherNotificationHelper {

    private const val CHANNEL_ID = "nimbus_current_weather"
    private const val DAILY_BRIEFING_CHANNEL_ID = "nimbus_daily_briefing"
    private const val NOTIFICATION_ID = 9000
    private const val DAILY_BRIEFING_NOTIFICATION_ID = 9001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.current_weather_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.current_weather_channel_desc)
            setShowBadge(false)
        }
        val dailyBriefingChannel = NotificationChannel(
            DAILY_BRIEFING_CHANNEL_ID,
            context.getString(R.string.daily_briefing_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.daily_briefing_channel_desc)
            setShowBadge(false)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        nm.createNotificationChannel(dailyBriefingChannel)
    }

    fun showOrUpdate(context: Context, data: WeatherData, s: NimbusSettings) {
        if (!hasNotificationPermission(context)) return

        // NEW_TASK only — MainActivity is singleTask, so tapping the
        // persistent notification surfaces the existing task instead of
        // CLEAR_TASK wiping whatever the user had in progress.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
            .setSmallIcon(weatherNotificationIcon(data.current.weatherCode, data.current.isDay))
            .setContentTitle(
                context.getString(
                    R.string.current_weather_notification_title,
                    temp,
                    condition,
                    data.location.name,
                ),
            )
            .setContentText(
                context.getString(R.string.current_weather_notification_body, feelsLike, high, low),
            )
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

    fun showDailyBriefing(
        context: Context,
        data: WeatherData,
        settings: NimbusSettings,
        summary: String,
    ): Boolean {
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_BRIEFING_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val temp = WeatherFormatter.formatTemperature(data.current.temperature, settings)
        val title = context.getString(
            R.string.daily_briefing_notification_title,
            data.location.name,
            temp,
            data.current.weatherCode.description,
        )
        val notification = NotificationCompat.Builder(context, DAILY_BRIEFING_CHANNEL_ID)
            .setSmallIcon(weatherNotificationIcon(data.current.weatherCode, data.current.isDay))
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(DAILY_BRIEFING_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun dismissDailyBriefing(context: Context) {
        NotificationManagerCompat.from(context).cancel(DAILY_BRIEFING_NOTIFICATION_ID)
    }

    private fun weatherNotificationIcon(code: WeatherCode, isDay: Boolean): Int = when {
        code.isStormy -> R.drawable.ic_w_thunderstorm
        code.isSnowy -> R.drawable.ic_w_snow
        code.isRainy -> R.drawable.ic_w_rain
        code.isFoggy -> R.drawable.ic_w_fog
        code == WeatherCode.OVERCAST -> R.drawable.ic_w_cloudy
        code == WeatherCode.PARTLY_CLOUDY -> if (isDay) R.drawable.ic_w_partly_cloudy else R.drawable.ic_w_cloudy
        code == WeatherCode.MAINLY_CLEAR -> if (isDay) R.drawable.ic_w_sunny else R.drawable.ic_w_night
        code == WeatherCode.CLEAR_SKY -> if (isDay) R.drawable.ic_w_sunny else R.drawable.ic_w_night
        !isDay -> R.drawable.ic_w_night
        else -> R.drawable.ic_w_sunny
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
