package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

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
        WidgetRefreshWorker.enqueueImmediate(context)
    }
}
