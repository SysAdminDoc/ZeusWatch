package com.sysadmindoc.nimbus.wear.data

import kotlin.math.roundToInt

/**
 * Converts the watch's canonical metric values (°C / km/h) to the user's
 * display units at render time. Unit strings are the phone's enum names
 * synced via the DataLayer ("FAHRENHEIT", "MPH", ...) — unknown or missing
 * strings fall back to metric so raw values are never shown mislabeled.
 */
object WearUnitFormatter {

    const val TEMP_CELSIUS = "CELSIUS"
    const val TEMP_FAHRENHEIT = "FAHRENHEIT"

    const val WIND_KMH = "KMH"
    const val WIND_MPH = "MPH"
    const val WIND_MS = "MS"
    const val WIND_KNOTS = "KNOTS"

    /** Converts a metric temperature (°C) to the display unit, rounded. */
    fun displayTemp(celsius: Int, tempUnit: String): Int = when (tempUnit) {
        TEMP_FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
        else -> celsius
    }

    /** Converts a metric wind speed (km/h) to the display unit, rounded. */
    fun displayWind(kmh: Int, windUnit: String): Int = when (windUnit) {
        WIND_MPH -> (kmh / 1.609344).roundToInt()
        WIND_MS -> (kmh / 3.6).roundToInt()
        WIND_KNOTS -> (kmh / 1.852).roundToInt()
        else -> kmh
    }

    /** Short label for the wind display unit ("mph", "km/h", "m/s", "kn"). */
    fun windLabel(windUnit: String): String = when (windUnit) {
        WIND_MPH -> "mph"
        WIND_MS -> "m/s"
        WIND_KNOTS -> "kn"
        else -> "km/h"
    }
}
