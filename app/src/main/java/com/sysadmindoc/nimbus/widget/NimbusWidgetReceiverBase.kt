package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class NimbusWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Must not block the main thread — DataStore reads + WorkManager
        // scheduling from these lifecycle callbacks can ANR. goAsync() keeps
        // the broadcast alive while we finish the cleanup on a background
        // dispatcher. Budget is ~10 seconds before Android kills the process.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRefreshWorker.syncFromPreferences(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    WidgetLocationPrefs.removeWidget(context, appWidgetId)
                    WidgetDataProvider.remove(context, appWidgetId)
                }
                WidgetRefreshWorker.syncFromPreferences(context)
            } finally {
                pending.finish()
            }
        }
    }
}
