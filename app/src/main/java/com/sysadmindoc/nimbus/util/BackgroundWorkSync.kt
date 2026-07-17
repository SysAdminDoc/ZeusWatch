package com.sysadmindoc.nimbus.util

import android.content.Context
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.widget.WidgetRefreshWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Single home for the "make every background worker match current settings"
 * sync. Cold start ([com.sysadmindoc.nimbus.NimbusApplication.onCreate]) and
 * settings import ([com.sysadmindoc.nimbus.ui.screen.settings.SettingsViewModel])
 * both run this, so the two paths cannot drift — before this existed, an
 * imported settings file toggled alert/nowcast/health/briefing workers in
 * DataStore but scheduled nothing until the next cold start.
 */
object BackgroundWorkSync {

    /** Hilt entry point so static worker companions can read settings from a bare [Context]. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface PreferencesEntryPoint {
        fun userPreferences(): UserPreferences
    }

    internal fun preferencesFrom(context: Context): UserPreferences =
        EntryPointAccessors
            .fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)
            .userPreferences()

    /**
     * Schedule or cancel every settings-driven background worker to match the
     * current persisted settings, dismissing each category's notifications
     * when its toggle is off. Scoped per category: disabling severe alerts
     * must never wipe live nowcast/health/custom notifications (their dedupe
     * stores would then suppress a re-fire).
     */
    suspend fun syncAll(context: Context, prefs: UserPreferences) {
        val settings = prefs.settings.first()

        if (settings.alertNotificationsEnabled) {
            AlertCheckWorker.schedule(context)
        } else {
            AlertCheckWorker.cancel(context)
            AlertNotificationHelper.dismissSevere(context)
        }

        if (settings.nowcastingAlerts) {
            NowcastAlertWorker.schedule(context)
        } else {
            NowcastAlertWorker.cancel(context)
            AlertNotificationHelper.dismissNowcast(context)
        }

        if (settings.healthAlertsEnabled) {
            HealthAlertWorker.schedule(context)
        } else {
            HealthAlertWorker.cancel(context)
            AlertNotificationHelper.dismissHealth(context)
        }

        // Custom-rule worker only runs if the user has actually authored
        // enabled rules. Avoids burning battery on a no-op hourly check.
        val hasEnabledCustomRules = prefs.customAlertRules.first().any { it.enabled }
        if (hasEnabledCustomRules) {
            CustomAlertWorker.schedule(context)
        } else {
            CustomAlertWorker.cancel(context)
            AlertNotificationHelper.dismissCustom(context)
        }

        if (!settings.persistentWeatherNotif) {
            WeatherNotificationHelper.dismiss(context)
        }

        if (settings.dailyBriefingEnabled) {
            DailyBriefingWorker.schedule(context, settings.dailyBriefingMinutes)
        } else {
            DailyBriefingWorker.cancel(context)
            WeatherNotificationHelper.dismissDailyBriefing(context)
        }

        WidgetRefreshWorker.sync(
            context,
            persistentWeatherNotif = settings.persistentWeatherNotif,
            gadgetbridgeBroadcastEnabled = settings.gadgetbridgeBroadcastEnabled,
        )
    }
}
