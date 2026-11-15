package com.sysadmindoc.nimbus.util

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager
import java.time.LocalDateTime

/**
 * Posts real notifications at the minSdk, at the API 34 the other suites pin
 * and at the target SDK, so both sides of these guards run: the alert, nowcast
 * and briefing POST_NOTIFICATIONS checks (33 and up), the full-screen intent
 * grant (34 and up) and the nowcast's rain timeline (36 and up). The current
 * weather notification's MetricStyle needs 37, past what this matrix reaches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [26, 34, 36],
    application = Application::class,
    shadows = [FullScreenIntentGrantShadow::class],
)
class NotificationApiLevelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        FullScreenIntentGrantShadow.granted = false
        AlertNotificationHelper.createChannels(app)
        WeatherNotificationHelper.createChannel(app)
    }

    @Test
    fun `without the permission nothing posts from 33, and everything posts below it`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val posts = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

        assertEquals(posts, AlertNotificationHelper.showAlertNotification(app, alert(AlertSeverity.SEVERE)))
        assertEquals(posts, AlertNotificationHelper.showNowcastNotification(app, "Rain in 15 min", "Light rain soon."))
        assertEquals(posts, WeatherNotificationHelper.showDailyBriefing(app, weather(), NimbusSettings(), "Sunny all day."))
        assertEquals(posts, shadowOf(manager).allNotifications.isNotEmpty())
    }

    @Test
    fun `with the permission every kind posts`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(AlertNotificationHelper.showAlertNotification(app, alert(AlertSeverity.SEVERE)))
        assertTrue(AlertNotificationHelper.showNowcastNotification(app, "Rain in 15 min", "Light rain soon."))
        assertTrue(WeatherNotificationHelper.showDailyBriefing(app, weather(), NimbusSettings(), "Sunny all day."))
        assertNotNull(shadowOf(manager).getNotification(AlertNotificationHelper.NOTIFICATION_ID_NOWCAST))
        assertNotNull(shadowOf(manager).getNotification(WeatherNotificationHelper.DAILY_BRIEFING_NOTIFICATION_ID))
    }

    @Test
    fun `an extreme alert goes full screen below 34, and from 34 only with the grant`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        FullScreenIntentGrantShadow.granted = false

        AlertNotificationHelper.showAlertNotification(app, alert(AlertSeverity.EXTREME))

        val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertEquals(fullScreen, postedAlert(AlertSeverity.EXTREME).fullScreenIntent != null)
    }

    @Test
    fun `an extreme alert goes full screen on every level once the system allows it`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        FullScreenIntentGrantShadow.granted = true

        AlertNotificationHelper.showAlertNotification(app, alert(AlertSeverity.EXTREME))

        assertNotNull(postedAlert(AlertSeverity.EXTREME).fullScreenIntent)
    }

    @Test
    fun `a severe alert never goes full screen`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        FullScreenIntentGrantShadow.granted = true

        AlertNotificationHelper.showAlertNotification(app, alert(AlertSeverity.SEVERE))

        assertNull(postedAlert(AlertSeverity.SEVERE).fullScreenIntent)
    }

    @Test
    fun `the nowcast draws its rain timeline from 36 and falls back to big text below`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val start = LocalDateTime.now()
        val series = listOf(
            MinutelyPrecipitation(start, 0.0),
            MinutelyPrecipitation(start.plusMinutes(15), 1.5),
            MinutelyPrecipitation(start.plusMinutes(30), 3.0),
        )
        // The fixture has to produce a timeline, or the 36 branch is never offered.
        assertNotNull(buildNowcastProgressTimeline(series))

        AlertNotificationHelper.showNowcastNotification(app, "Rain in 15 min", "Light rain soon.", series)

        val template = shadowOf(manager)
            .getNotification(AlertNotificationHelper.NOTIFICATION_ID_NOWCAST)
            .extras.getString(Notification.EXTRA_TEMPLATE)
        val expected = if (Build.VERSION.SDK_INT >= 36) PROGRESS_STYLE else BIG_TEXT_STYLE
        assertEquals(expected, template)
    }

    /** The alert itself, not the group summary posted beside it. */
    private fun postedAlert(severity: AlertSeverity): Notification =
        shadowOf(manager).allNotifications.single {
            it.channelId == AlertNotificationHelper.channelForSeverity(severity) &&
                it.flags and Notification.FLAG_GROUP_SUMMARY == 0
        }

    private fun alert(severity: AlertSeverity) = WeatherAlert(
        id = "alert-${severity.name}",
        event = "Tornado Warning",
        headline = "Tornado warning until 5 PM",
        description = "A tornado was observed near Denver.",
        instruction = "Take shelter now.",
        severity = severity,
        urgency = AlertUrgency.IMMEDIATE,
        certainty = "Observed",
        senderName = "NWS Boulder",
        areaDescription = "Denver County",
        effective = null,
        expires = null,
        response = "Shelter",
    )

    private fun weather() = WeatherData(
        location = LocationInfo(name = "Denver", latitude = 39.7, longitude = -104.9),
        current = CurrentConditions(
            temperature = 20.0,
            feelsLike = 20.0,
            humidity = 40,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            windSpeed = 5.0,
            windDirection = 180,
            windGusts = null,
            pressure = 1015.0,
            uvIndex = 3.0,
            visibility = null,
            dewPoint = null,
            cloudCover = 0,
            precipitation = 0.0,
            dailyHigh = 24.0,
            dailyLow = 10.0,
            sunrise = null,
            sunset = null,
        ),
        hourly = emptyList(),
        daily = emptyList(),
    )

    private companion object {
        const val PROGRESS_STYLE = "android.app.Notification\$ProgressStyle"
        const val BIG_TEXT_STYLE = "android.app.Notification\$BigTextStyle"
    }
}

/**
 * Robolectric has no shadow for `canUseFullScreenIntent()` (API 34), so this
 * one lets a test pick the system's answer.
 */
@Implements(NotificationManager::class)
class FullScreenIntentGrantShadow : ShadowNotificationManager() {

    @Implementation(minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun canUseFullScreenIntent(): Boolean = granted

    companion object {
        var granted = false
    }
}
