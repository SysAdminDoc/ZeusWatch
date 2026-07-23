package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionStateTest {

    @Test
    fun `granted always wins regardless of other signals`() {
        assertEquals(
            LocationPermissionUiState.GRANTED,
            resolveLocationPermissionUiState(granted = true, hasRequestedBefore = true, shouldShowRationale = true),
        )
        assertEquals(
            LocationPermissionUiState.GRANTED,
            resolveLocationPermissionUiState(granted = true, hasRequestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `never requested is requestable`() {
        assertEquals(
            LocationPermissionUiState.REQUESTABLE,
            resolveLocationPermissionUiState(granted = false, hasRequestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `denied once but rationale allowed shows rationale`() {
        assertEquals(
            LocationPermissionUiState.SHOW_RATIONALE,
            resolveLocationPermissionUiState(granted = false, hasRequestedBefore = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `denied with no rationale after asking is permanent`() {
        assertEquals(
            LocationPermissionUiState.PERMANENTLY_DENIED,
            resolveLocationPermissionUiState(granted = false, hasRequestedBefore = true, shouldShowRationale = false),
        )
    }

    @Test
    fun `only permanent denial needs app settings`() {
        assertTrue(LocationPermissionUiState.PERMANENTLY_DENIED.needsAppSettings)
        assertFalse(LocationPermissionUiState.REQUESTABLE.needsAppSettings)
        assertFalse(LocationPermissionUiState.SHOW_RATIONALE.needsAppSettings)
        assertFalse(LocationPermissionUiState.GRANTED.needsAppSettings)
    }
}
