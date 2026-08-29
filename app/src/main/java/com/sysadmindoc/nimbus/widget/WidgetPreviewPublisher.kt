package com.sysadmindoc.nimbus.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.sysadmindoc.nimbus.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass

private const val TAG = "WidgetPreviewPublisher"
private const val PREVIEW_PREFS = "widget_previews"
private const val KEY_PUBLISHED_VERSION = "published_version_code"
private const val KEY_PUBLISHED_RECEIVERS = "published_receivers"

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
        val prefs = context.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)
        val alreadyPublished = publishedReceivers(prefs)
        val pending = receivers.filter { it.java.name !in alreadyPublished }
        if (pending.isEmpty()) return

        val manager = GlanceAppWidgetManager(context)
        val published = alreadyPublished.toMutableSet()
        // Not `all { }`: short-circuiting would skip the remaining receivers
        // after the first rate-limited one, so a single stuck preview would
        // hold up all the others.
        pending.forEach { receiver ->
            val result = try {
                manager.setWidgetPreviews(receiver)
            } catch (cancelled: CancellationException) {
                // Never swallow cancellation — persist what landed so far and
                // let the caller's structured concurrency see the cancel.
                persist(prefs, published)
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Preview publish failed for ${receiver.simpleName}", e)
                null
            }
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                published += receiver.java.name
            }
        }
        // Record per receiver, not all-or-nothing: a preview that keeps failing
        // would otherwise make every launch re-push the seven that already
        // landed, burning the platform's rate-limit budget on redundant calls.
        persist(prefs, published)
        if (published.size < receivers.size) {
            Log.i(
                TAG,
                "Published ${published.size}/${receivers.size} widget previews; will retry the rest",
            )
        }
    }

    private fun persist(prefs: android.content.SharedPreferences, published: Set<String>) {
        prefs.edit()
            .putInt(KEY_PUBLISHED_VERSION, BuildConfig.VERSION_CODE)
            .putStringSet(KEY_PUBLISHED_RECEIVERS, published)
            .apply()
    }

    /**
     * Receivers already published for this exact app version. An app update
     * can change what a widget renders, so a version bump clears the record.
     */
    private fun publishedReceivers(prefs: android.content.SharedPreferences): Set<String> =
        if (shouldRepublish(prefs.getInt(KEY_PUBLISHED_VERSION, -1), BuildConfig.VERSION_CODE)) {
            emptySet()
        } else {
            prefs.getStringSet(KEY_PUBLISHED_RECEIVERS, emptySet()).orEmpty()
        }

    /**
     * Previews are republished when the installed version has not published
     * them yet — an app update can change what a widget renders.
     */
    fun shouldRepublish(publishedVersionCode: Int, currentVersionCode: Int): Boolean =
        publishedVersionCode != currentVersionCode

}
