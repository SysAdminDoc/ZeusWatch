package com.sysadmindoc.nimbus.data.model

import com.sysadmindoc.nimbus.data.api.GeocodingResult
import java.text.Normalizer
import java.util.Locale
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

private val COMBINING_MARKS = Regex("\\p{Mn}+")

// Decompose to NFD then strip combining marks, then ASCII-lowercase. This makes
// normalization locale-independent and robust to accent/case variations like
// "İstanbul"/"Istanbul"/"ISTANBUL" and "Paris"/"París". Raw `lowercase()` with
// the default locale is unstable across devices (Turkish dotless-i, German ß,
// etc.), and even `lowercase(Locale.ROOT)` turns "İ" into "i\u0307", which
// would then fail to match a plain "i".
private fun normalizeLocationToken(value: String?): String {
    val trimmed = value?.trim() ?: return ""
    if (trimmed.isEmpty()) return ""
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
    val stripped = COMBINING_MARKS.replace(decomposed, "")
    return stripped.lowercase(Locale.ROOT)
}
