package com.sysadmindoc.nimbus.ui.screen.radar

/**
 * Where the radar map should centre, or that there is nowhere to centre it.
 *
 * Modelled as a type rather than a coordinate pair so "we do not know" cannot
 * be spelled as a plausible-looking latitude and longitude.
 */
sealed interface RadarLocation {

    data class Known(val latitude: Double, val longitude: Double) : RadarLocation

    data object Unknown : RadarLocation
}
