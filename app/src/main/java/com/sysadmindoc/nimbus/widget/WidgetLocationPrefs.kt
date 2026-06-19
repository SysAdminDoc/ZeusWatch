package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.widgetLocationStore: DataStore<Preferences> by preferencesDataStore(
    name = "nimbus_widget_locations"
)

/**
 * Maps Glance widget instance IDs (appWidgetId) to saved location IDs.
 * When a widget has no configured location, it uses the default (last GPS) location.
 */
object WidgetLocationPrefs {

    private fun keyForWidget(appWidgetId: Int) = longPreferencesKey("widget_loc_$appWidgetId")

    /** Get the saved location ID for a widget, or null for default location. */
    suspend fun getLocationId(context: Context, appWidgetId: Int): Long? {
        val prefs = readWidgetLocationPreferences(context)
        val value = prefs[keyForWidget(appWidgetId)]
        return if (value == null || value == 0L) null else value
    }

    /** Set the location ID for a widget instance. Pass 0 or null for default. */
    suspend fun setLocationId(context: Context, appWidgetId: Int, locationId: Long?) {
        context.widgetLocationStore.edit { prefs ->
            prefs[keyForWidget(appWidgetId)] = locationId ?: 0L
        }
    }

    /** Remove the location mapping when a widget is deleted. */
    suspend fun removeWidget(context: Context, appWidgetId: Int) {
        context.widgetLocationStore.edit { prefs ->
            prefs.remove(keyForWidget(appWidgetId))
        }
    }

    /** Get all widget-to-location mappings. Returns Map<appWidgetId, locationId>. */
    suspend fun getAllMappings(context: Context): Map<Int, Long> {
        val prefs = readWidgetLocationPreferences(context)
        return prefs.asMap().mapNotNull { (key, value) ->
            // removePrefix() returns the string unchanged when the prefix is
            // absent, so guard explicitly — otherwise any future non-widget key
            // in this store whose name parses as an Int would be mistaken for a
            // widget mapping and trigger phantom refreshes.
            if (!key.name.startsWith("widget_loc_")) return@mapNotNull null
            val widgetId = key.name.removePrefix("widget_loc_").toIntOrNull() ?: return@mapNotNull null
            val locId = (value as? Long) ?: return@mapNotNull null
            if (locId == 0L) return@mapNotNull null
            widgetId to locId
        }.toMap()
    }

    private suspend fun readWidgetLocationPreferences(context: Context): Preferences =
        context.widgetLocationStore.data
            .catch { failure ->
                if (failure is IOException) emit(emptyPreferences()) else throw failure
            }
            .first()
}
