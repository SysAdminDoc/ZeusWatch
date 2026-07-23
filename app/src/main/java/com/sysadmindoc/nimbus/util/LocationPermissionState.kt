package com.sysadmindoc.nimbus.util

/**
 * Device-independent classification of the runtime location-permission state,
 * derived from the three signals a caller can observe without side effects:
 * whether the permission is granted, whether the system says a rationale should
 * be shown, and whether the app has ever launched the request before.
 *
 * Extracted as a pure function so the recovery UX (rationale vs. permanent
 * denial vs. first ask) is unit-testable off-device (NX-26). The Android system
 * API `shouldShowRequestPermissionRationale` returns `false` both before the
 * first request and after a permanent denial, so a persisted "have we asked"
 * flag is required to tell those two states apart.
 */
enum class LocationPermissionUiState {
    /** Permission already held — no prompt needed. */
    GRANTED,

    /** Never requested yet — a plain request will show the system dialog. */
    REQUESTABLE,

    /** Denied at least once but the system still allows re-prompting with a rationale. */
    SHOW_RATIONALE,

    /** Denied with "don't ask again" / policy — only recoverable via App Settings. */
    PERMANENTLY_DENIED,
}

/**
 * Maps observable permission signals to a [LocationPermissionUiState].
 *
 * @param granted whether any location permission is currently held.
 * @param hasRequestedBefore whether the app has ever launched the request
 *   (persisted across process death; see `UserPreferences.locationPermissionRequested`).
 * @param shouldShowRationale the value of
 *   `ActivityCompat.shouldShowRequestPermissionRationale` for a location permission.
 */
fun resolveLocationPermissionUiState(
    granted: Boolean,
    hasRequestedBefore: Boolean,
    shouldShowRationale: Boolean,
): LocationPermissionUiState = when {
    granted -> LocationPermissionUiState.GRANTED
    shouldShowRationale -> LocationPermissionUiState.SHOW_RATIONALE
    hasRequestedBefore -> LocationPermissionUiState.PERMANENTLY_DENIED
    else -> LocationPermissionUiState.REQUESTABLE
}

/** True when only a trip to system App Settings can restore location access. */
val LocationPermissionUiState.needsAppSettings: Boolean
    get() = this == LocationPermissionUiState.PERMANENTLY_DENIED
