package com.sysadmindoc.nimbus.widget

import com.sysadmindoc.nimbus.data.model.WeatherCode

/**
 * Shared utility functions for Glance widgets.
 */
object WidgetUtils {

    /**
     * Returns a human-readable weather description for the given WMO code.
     * Uses the [WeatherCode] enum's description property when available.
     */
    fun weatherDescription(code: Int, isDay: Boolean = true): String {
        return try {
            WeatherCode.fromCode(code).description
        } catch (_: Exception) {
            "Weather icon"
        }
    }
}
