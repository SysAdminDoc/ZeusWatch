package com.sysadmindoc.nimbus.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.sysadmindoc.nimbus.BuildConfig
import kotlin.reflect.KClass

private const val TAG = "WidgetPreviewPublisher"
private const val PREVIEW_PREFS = "widget_previews"
private const val KEY_PUBLISHED_VERSION = "published_version_code"

/**
 * Pushes each widget's `providePreview` output to the launcher's widget picker.
 *
 * Android 15 replaced the static `previewLayout` with previews the app supplies
 * at runtime. The platform rate-limits this call, so it runs once per installed
 * version and only records success once every receiver has been accepted —
 * a rate-limited attempt is retried on the next launch instead of being lost.
 */
internal object WidgetPreviewPublisher {

    /** Every receiver the manifest registers. `WidgetSurfaceContractTest` guards the list. */
    val receivers: List<KClass<out GlanceAppWidgetReceiver>> = listOf(
        NimbusSmallWidgetReceiver::class,
        NimbusMediumWidgetReceiver::class,
        NimbusLargeWidgetReceiver::class,
        NimbusForecastStripWidgetReceiver::class,
        NimbusSavedCitiesWidgetReceiver::class,
        NimbusTempWidgetReceiver::class,
        NimbusCompactWidgetReceiver::class,
        NimbusDailyWidgetReceiver::class,
    )

    suspend fun publishIfNeeded(context: Context) {
        // setWidgetPreviews is a VANILLA_ICE_CREAM API. The guard is inline
        // because that is the only shape lint's NewApi check recognises —
        // hiding it behind a helper turns the call below into a lint error.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (!shouldRepublish(publishedVersion(context), BuildConfig.VERSION_CODE)) return
        val manager = GlanceAppWidgetManager(context)
        // Not `all { }`: short-circuiting would skip the remaining receivers
        // after the first rate-limited one, leaving those previews unset even
        // on the retry, because the retry starts from the same first receiver.
        var published = 0
        receivers.forEach { receiver ->
            val result = runCatching { manager.setWidgetPreviews(receiver) }
                .onFailure { Log.w(TAG, "Preview publish failed for ${receiver.simpleName}", it) }
                .getOrNull()
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) published++
        }
        if (published == receivers.size) {
            context.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PUBLISHED_VERSION, BuildConfig.VERSION_CODE)
                .apply()
        } else {
            Log.i(TAG, "Published $published/${receivers.size} widget previews; will retry next launch")
        }
    }

    /**
     * Previews are republished when the installed version has not published
     * them yet — an app update can change what a widget renders.
     */
    fun shouldRepublish(publishedVersionCode: Int, currentVersionCode: Int): Boolean =
        publishedVersionCode != currentVersionCode

    private fun publishedVersion(context: Context): Int =
        context.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PUBLISHED_VERSION, -1)
}
