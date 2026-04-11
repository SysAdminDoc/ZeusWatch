package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.runBlocking

abstract class NimbusWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        runBlocking {
            WidgetRefreshWorker.syncFromPreferences(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        runBlocking {
            appWidgetIds.forEach { appWidgetId ->
                WidgetLocationPrefs.removeWidget(context, appWidgetId)
                WidgetDataProvider.remove(context, appWidgetId)
            }
            WidgetRefreshWorker.syncFromPreferences(context)
        }
    }
}
