package com.sysadmindoc.nimbus.testing

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks

fun ComposeContentTestRule.setContentWithAccessibilityChecks(
    content: @Composable () -> Unit,
) {
    val canRunAccessibilityChecks = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    if (canRunAccessibilityChecks) {
        enableAccessibilityChecks()
    }

    setContent(content)
    waitForIdle()

    if (canRunAccessibilityChecks) {
        onRoot().tryPerformAccessibilityChecks()
    }
}
