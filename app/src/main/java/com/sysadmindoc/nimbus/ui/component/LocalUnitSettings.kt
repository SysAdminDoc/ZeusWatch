package com.sysadmindoc.nimbus.ui.component

import androidx.compose.runtime.compositionLocalOf
import com.sysadmindoc.nimbus.data.repository.NimbusSettings

/**
 * CompositionLocal providing unit settings to all weather components.
 * Provided at the MainScreen level so all child composables can
 * access it without explicit parameter passing.
 */
val LocalUnitSettings = compositionLocalOf { NimbusSettings() }
