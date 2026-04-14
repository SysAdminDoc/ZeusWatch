package com.sysadmindoc.nimbus

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.di.DefaultDispatcher
import com.sysadmindoc.nimbus.util.AlertCheckWorker
import com.sysadmindoc.nimbus.util.AlertNotificationHelper
import com.sysadmindoc.nimbus.util.CustomAlertWorker
import com.sysadmindoc.nimbus.util.NowcastAlertWorker
import com.sysadmindoc.nimbus.util.WeatherNotificationHelper
import com.sysadmindoc.nimbus.widget.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NimbusApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var prefs: UserPreferences

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AlertNotificationHelper.createChannels(this)
        WeatherNotificationHelper.createChannel(this)

        CoroutineScope(SupervisorJob() + defaultDispatcher).launch {
            val settings = prefs.settings.first()
            if (settings.alertNotificationsEnabled) {
                AlertCheckWorker.schedule(this@NimbusApplication)
            } else {
                AlertCheckWorker.cancel(this@NimbusApplication)
                AlertNotificationHelper.dismissAll(this@NimbusApplication)
            }

            if (settings.nowcastingAlerts) {
                NowcastAlertWorker.schedule(this@NimbusApplication)
            } else {
                NowcastAlertWorker.cancel(this@NimbusApplication)
            }

            // Custom-rule worker only runs if the user has actually authored
            // enabled rules. Avoids burning battery on a no-op hourly check.
            val hasEnabledCustomRules = prefs.customAlertRules.first().any { it.enabled }
            if (hasEnabledCustomRules) {
                CustomAlertWorker.schedule(this@NimbusApplication)
            } else {
                CustomAlertWorker.cancel(this@NimbusApplication)
            }

            if (!settings.persistentWeatherNotif) {
                WeatherNotificationHelper.dismiss(this@NimbusApplication)
            }

            WidgetRefreshWorker.sync(this@NimbusApplication, settings.persistentWeatherNotif)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .build()
    }
}
