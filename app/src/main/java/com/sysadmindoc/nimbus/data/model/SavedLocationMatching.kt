package com.sysadmindoc.nimbus.data.model

import com.sysadmindoc.nimbus.data.api.GeocodingResult
import kotlin.math.abs

internal const val SAVED_LOCATION_COORDINATE_EPSILON = 0.0001

internal fun matchesSavedLocation(
    result: GeocodingResult,
    savedLocation: SavedLocationEntity,
    coordinateEpsilon: Double = SAVED_LOCATION_COORDINATE_EPSILON,
): Boolean {
    if (savedLocation.isCurrentLocation) return false

    val sameCoordinates = abs(result.latitude - savedLocation.latitude) < coordinateEpsilon &&
        abs(result.longitude - savedLocation.longitude) < coordinateEpsilon

    val samePlaceLabel = normalizeLocationToken(result.name) == normalizeLocationToken(savedLocation.name) &&
        normalizeLocationToken(result.admin1) == normalizeLocationToken(savedLocation.region) &&
        normalizeLocationToken(result.country) == normalizeLocationToken(savedLocation.country)

    return sameCoordinates || samePlaceLabel
}

private fun normalizeLocationToken(value: String?): String = value?.trim()?.lowercase().orEmpty()
