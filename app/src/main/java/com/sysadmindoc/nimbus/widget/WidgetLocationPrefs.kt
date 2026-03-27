package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
        val prefs = context.widgetLocationStore.data.first()
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
        val prefs = context.widgetLocationStore.data.first()
        return prefs.asMap().mapNotNull { (key, value) ->
            val widgetId = key.name.removePrefix("widget_loc_").toIntOrNull() ?: return@mapNotNull null
            val locId = (value as? Long) ?: return@mapNotNull null
            if (locId == 0L) return@mapNotNull null
            widgetId to locId
        }.toMap()
    }
}
