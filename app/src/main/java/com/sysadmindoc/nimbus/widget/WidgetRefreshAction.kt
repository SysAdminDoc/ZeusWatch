package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Glance action callback that triggers an immediate widget data refresh
 * when the user taps the widget.
 */
class WidgetRefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Enqueue a one-shot refresh (in addition to the periodic worker)
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "nimbus_widget_manual_refresh",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
