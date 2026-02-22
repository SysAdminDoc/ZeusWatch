package com.sysadmindoc.nimbus

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sysadmindoc.nimbus.util.AlertCheckWorker
import com.sysadmindoc.nimbus.util.AlertNotificationHelper
import com.sysadmindoc.nimbus.widget.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NimbusApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AlertNotificationHelper.createChannels(this)
        AlertCheckWorker.schedule(this)
        WidgetRefreshWorker.schedule(this)
    }
}
