package com.sysadmindoc.nimbus.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import com.sysadmindoc.nimbus.MainActivity
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import java.time.Duration
import java.time.LocalDateTime

object AlertNotificationHelper {

    private const val GROUP_ID = "nimbus_alert_group"
    private const val SUMMARY_NOTIFICATION_ID = 0x1000
    private const val URI_MAIN = "zeuswatch://main"
    private const val URI_WEATHER_ALERTS = "zeuswatch://main?target=weather_alerts"
    private const val URI_NOWCAST = "zeuswatch://main?target=nowcast"
    private const val URI_HEALTH = "zeuswatch://main?target=health"
    private const val URI_CUSTOM_ALERTS = "zeuswatch://custom_alerts"

    // Ambient group — nowcast + health + custom rules. Separate from severe
    // alerts so a tornado warning doesn't collapse into an "it's cloudy" stack.
    private const val AMBIENT_GROUP_ID = "nimbus_ambient_group"
    private const val AMBIENT_SUMMARY_NOTIFICATION_ID = 0x1001

    // Separate channels per severity tier
    private const val CHANNEL_EXTREME = "nimbus_alerts_extreme"
    private const val CHANNEL_SEVERE = "nimbus_alerts_severe"
    private const val CHANNEL_MODERATE = "nimbus_alerts_moderate"
    private const val CHANNEL_MINOR = "nimbus_alerts_minor"

    // Nowcasting: "Rain starts in 15 min" / "Rain stops in 10 min". Its own
    // channel because semantically it's not a severity-gated severe-weather
    // warning — it's a short-horizon precipitation heads-up.
    const val CHANNEL_NOWCAST = "nimbus_alerts_nowcast"
    const val NOTIFICATION_ID_NOWCAST = 0x1201

    // Health alerts: migraine pressure, respiratory humidity, arthritis temp swings.
    // Separate channel so users can control health notification volume independently.
    const val CHANNEL_HEALTH = "nimbus_alerts_health"
    private const val NOTIFICATION_ID_HEALTH_BASE = 0x1300

    // User-defined custom alert rules. Separate channel so users can silence
    // custom rules without losing severe alerts. Base offset must not overlap
    // with health range (0x1300 + 0..0xFF = 0x1300..0x13FF).
    const val CHANNEL_CUSTOM = "nimbus_alerts_custom"
    /** Base id for per-rule notifications; we offset by rule hash so multiple rules don't collide. */
    private const val NOTIFICATION_ID_CUSTOM_BASE = 0x1400

    /**
     * Base id for per-alert severe-weather notifications. A raw
     * `alert.id.hashCode()` spans the full Int range and can collide with the
     * reserved fixed ids (0x1000–0x14FF summaries/nowcast/health/custom, 9000
     * persistent weather); folding into this band keeps them disjoint.
     */
    private const val NOTIFICATION_ID_SEVERE_BASE = 0x20000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel group
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, context.getString(R.string.alert_channel_group_weather))
        )

        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Extreme: tornado, tsunami, etc — max priority, alarm sound
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EXTREME,
                context.getString(R.string.alert_channel_extreme_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_extreme_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttr)
                setBypassDnd(true)
            }
        )

        // Severe: thunderstorm warnings, blizzard — high priority
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SEVERE,
                context.getString(R.string.alert_channel_severe_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_severe_desc)
                enableVibration(true)
                enableLights(true)
            }
        )

        // Moderate: watches, advisories — default
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MODERATE,
                context.getString(R.string.alert_channel_moderate_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_moderate_desc)
            }
        )

        // Minor: statements, special weather statements — low
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MINOR,
                context.getString(R.string.alert_channel_minor_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_minor_desc)
            }
        )

        // Nowcast: short-horizon precipitation heads-up ("Rain in 15 min").
        // Default importance so it makes a sound but doesn't bypass DND.
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NOWCAST,
                context.getString(R.string.alert_channel_nowcast_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_nowcast_desc)
            }
        )

        // Health alerts: migraine pressure, respiratory, arthritis triggers.
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HEALTH,
                context.getString(R.string.alert_channel_health_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_health_desc)
            }
        )

        // Custom alert rules: user-configured thresholds.
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CUSTOM,
                context.getString(R.string.alert_channel_custom_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.alert_channel_custom_desc)
            }
        )

        // Delete the old single channel if it exists (clean migration)
        nm.deleteNotificationChannel("nimbus_weather_alerts")
    }

    /**
     * Show a precipitation nowcast notification. [title] / [body] are composed
     * by the caller ([NowcastAlertWorker]) so this helper stays presentation-only.
     * Uses a stable notification id so repeated notifications replace (not stack).
     */
    fun showNowcastNotification(
        context: Context,
        title: String,
        body: String,
        series: List<MinutelyPrecipitation> = emptyList(),
    ): Boolean {
        if (!hasNotificationPermission(context)) return false

        val pendingIntent = deepLinkPendingIntent(
            context, requestCode = NOTIFICATION_ID_NOWCAST, uri = URI_NOWCAST,
        )

        val timeline = buildNowcastProgressTimeline(series)
        val notification = if (Build.VERSION.SDK_INT >= 36 && timeline != null) {
            progressStyleNowcastNotification(context, title, body, pendingIntent, timeline)
        } else {
            bigTextNowcastNotification(context, title, body, pendingIntent)
        }

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(NOTIFICATION_ID_NOWCAST, notification)
            nm.notify(AMBIENT_SUMMARY_NOTIFICATION_ID, ambientSummary(context, CHANNEL_NOWCAST))
            return true
        } catch (_: SecurityException) {
            // Permission revoked after check
            return false
        }
    }

    private fun bigTextNowcastNotification(
        context: Context,
        title: String,
        body: String,
        pendingIntent: PendingIntent,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_NOWCAST)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(AMBIENT_GROUP_ID)
            .build()
    }

    @androidx.annotation.RequiresApi(36)
    private fun progressStyleNowcastNotification(
        context: Context,
        title: String,
        body: String,
        pendingIntent: PendingIntent,
        timeline: NowcastProgressTimeline,
    ): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(timeline.progressMinutes)
            .setProgressSegments(
                timeline.segments.map { segment ->
                    Notification.ProgressStyle.Segment(segment.minutes)
                        .setColor(segment.intensity.color)
                }
            )
            .setProgressPoints(
                timeline.points.map { point ->
                    Notification.ProgressStyle.Point(point.minute)
                        .setColor(point.color)
                }
            )

        return Notification.Builder(context, CHANNEL_NOWCAST)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setGroup(AMBIENT_GROUP_ID)
            .build()
    }

    /**
     * Show a health-related weather notification (migraine, respiratory, arthritis).
     * Uses the dedicated CHANNEL_HEALTH. Notification ID is offset by alert type
     * ordinal so different health alert types don't clobber each other.
     */
    fun showHealthNotification(
        context: Context,
        type: HealthAlertType,
        title: String,
        body: String,
        detail: String = "",
        severity: HealthSeverity = HealthSeverity.ADVISORY,
    ): Boolean {
        if (!hasNotificationPermission(context)) return false

        // Offset by the type ordinal (not a localized-title hash): stable
        // across locale changes, and a re-fired type replaces its predecessor.
        val notifId = NOTIFICATION_ID_HEALTH_BASE + type.ordinal
        val pendingIntent = deepLinkPendingIntent(
            context, requestCode = notifId, uri = URI_HEALTH,
        )

        val bigText = if (detail.isNotBlank()) "$body\n\n$detail" else body

        val notification = NotificationCompat.Builder(context, CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(
                if (severity == HealthSeverity.WARNING) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(severity.color.toArgb())
            .setGroup(AMBIENT_GROUP_ID)
            .build()

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(notifId, notification)
            nm.notify(AMBIENT_SUMMARY_NOTIFICATION_ID, ambientSummary(context, CHANNEL_HEALTH))
            return true
        } catch (_: SecurityException) {
            // Permission revoked after check
            return false
        }
    }

    /**
     * Show a user-defined custom-alert-rule notification. [ruleKey] is a
     * per-rule stable hash so multiple rules don't clobber each other; the
     * same rule firing tomorrow will replace today's notification.
     */
    fun showCustomAlertNotification(
        context: Context,
        ruleKey: String,
        title: String,
        body: String,
    ): Boolean {
        if (!hasNotificationPermission(context)) return false

        val id = NOTIFICATION_ID_CUSTOM_BASE + (ruleKey.hashCode() and 0xFF)
        val pendingIntent = deepLinkPendingIntent(
            context, requestCode = id, uri = URI_CUSTOM_ALERTS,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CUSTOM)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setGroup(AMBIENT_GROUP_ID)
            .build()
        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(id, notification)
            nm.notify(AMBIENT_SUMMARY_NOTIFICATION_ID, ambientSummary(context, CHANNEL_CUSTOM))
            return true
        } catch (_: SecurityException) {
            // Permission revoked after check
            return false
        }
    }

    private fun deepLinkPendingIntent(
        context: Context,
        requestCode: Int,
        uri: String,
    ): PendingIntent {
        // NEW_TASK only — MainActivity is singleTask, so the deep link is
        // routed through onNewIntent without nuking existing task state
        // (CLEAR_TASK used to wipe in-progress navigation on every tap).
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // The summary must live on the SAME channel as the child that triggered it.
    // Building it always on CHANNEL_NOWCAST meant a health/custom notification's
    // summary landed on an unrelated channel — if the user muted nowcast, the
    // summary was suppressed and the whole ambient stack broke.
    private fun ambientSummary(context: Context, channelId: String): android.app.Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(context.getString(R.string.alert_ambient_summary_title))
            .setContentText(context.getString(R.string.alert_ambient_summary_text))
            .setContentIntent(
                deepLinkPendingIntent(
                    context,
                    requestCode = AMBIENT_SUMMARY_NOTIFICATION_ID,
                    uri = URI_MAIN,
                ),
            )
            .setGroup(AMBIENT_GROUP_ID)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

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
    ): Boolean {
        if (!hasNotificationPermission(context)) return false

        val notificationId = NOTIFICATION_ID_SEVERE_BASE + (alert.id.hashCode() and 0xFFFF)
        val pendingIntent = deepLinkPendingIntent(
            context,
            requestCode = notificationId,
            uri = URI_WEATHER_ALERTS,
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
            .setColor(alert.severity.color.toArgb())
            .build()

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(notificationId, notification)

            // Group summary so multiple alerts collapse. Always posted on
            // CHANNEL_SEVERE — building it on the last child's channel meant a
            // disabled MINOR channel could break the summary while EXTREME
            // children were still active.
            val summary = NotificationCompat.Builder(context, CHANNEL_SEVERE)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(context.getString(R.string.alert_weather_summary_title))
                .setContentText(context.getString(R.string.alert_weather_summary_text))
                .setContentIntent(
                    deepLinkPendingIntent(
                        context,
                        requestCode = SUMMARY_NOTIFICATION_ID,
                        uri = URI_WEATHER_ALERTS,
                    ),
                )
                .setGroup(GROUP_ID)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            nm.notify(SUMMARY_NOTIFICATION_ID, summary)
            return true
        } catch (_: SecurityException) {
            // Permission revoked after check
            return false
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

    fun dismissAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.activeNotifications
            .filter {
                it.id == SUMMARY_NOTIFICATION_ID ||
                    it.id == AMBIENT_SUMMARY_NOTIFICATION_ID ||
                    it.notification.group == GROUP_ID ||
                    it.notification.group == AMBIENT_GROUP_ID
            }
            .forEach { nm.cancel(it.id) }
    }
}

internal data class NowcastProgressTimeline(
    val segments: List<NowcastProgressSegment>,
    val points: List<NowcastProgressPoint>,
    val progressMinutes: Int = 1,
)

internal data class NowcastProgressSegment(
    val minutes: Int,
    val intensity: NowcastProgressIntensity,
)

internal data class NowcastProgressPoint(
    val minute: Int,
    val color: Int,
)

internal enum class NowcastProgressIntensity(val color: Int) {
    DRY(AndroidColor.rgb(82, 93, 112)),
    LIGHT(AndroidColor.rgb(95, 180, 255)),
    STEADY(AndroidColor.rgb(33, 150, 243)),
    HEAVY(AndroidColor.rgb(126, 87, 194)),
}

internal fun buildNowcastProgressTimeline(
    series: List<MinutelyPrecipitation>,
    referenceTime: LocalDateTime = nowcastReferenceTime(series),
): NowcastProgressTimeline? {
    if (series.size < 2) return null
    val sorted = series.sortedBy { it.time }
    val currentIndex = sorted.indexOfLast { !it.time.isAfter(referenceTime) }
        .takeIf { it >= 0 }
        ?: 0
    val cutoff = referenceTime.plusMinutes(NOWCAST_LOOK_AHEAD_MIN.toLong())
    val buckets = sorted
        .drop(currentIndex)
        .takeWhile { !it.time.isAfter(cutoff) }
        .take(4)
    if (buckets.size < 2) return null

    val segments = buckets.mapIndexed { index, bucket ->
        val segmentStart = if (index == 0 && referenceTime.isAfter(bucket.time)) {
            referenceTime
        } else {
            bucket.time
        }
        val nextTime = buckets.getOrNull(index + 1)?.time ?: bucket.time.plusMinutes(15)
        val minutes = Duration.between(segmentStart, nextTime).toMinutes().toInt()
            .coerceIn(1, 15)
        NowcastProgressSegment(
            minutes = minutes,
            intensity = nowcastProgressIntensity(bucket.precipitation),
        )
    }
    val points = segments
        .zipWithNext()
        .runningFold(0) { total, (segment, _) -> total + segment.minutes }
        .drop(1)
        .mapIndexedNotNull { index, minute ->
            val before = segments[index].intensity
            val after = segments[index + 1].intensity
            if (before == after) {
                null
            } else {
                NowcastProgressPoint(
                    minute = minute.coerceAtLeast(1),
                    color = after.color,
                )
            }
        }
    return NowcastProgressTimeline(
        segments = segments,
        points = points,
    )
}

private fun nowcastProgressIntensity(precipitationMm: Double): NowcastProgressIntensity = when {
    precipitationMm < NOWCAST_WET_THRESHOLD_MM -> NowcastProgressIntensity.DRY
    precipitationMm >= 2.5 -> NowcastProgressIntensity.HEAVY
    precipitationMm >= 1.0 -> NowcastProgressIntensity.STEADY
    else -> NowcastProgressIntensity.LIGHT
}
