package com.sysadmindoc.nimbus.testing

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.fail

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

fun ComposeContentTestRule.assertVisibleTouchTargetsMeetMinimum(
    minSize: Dp = 48.dp,
) {
    waitForIdle()
    val minPx = with(density) { minSize.toPx() }
    val failures = onAllNodes(hasClickAction(), useUnmergedTree = false)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { node ->
            val bounds = node.boundsInRoot
            bounds.width > 0f && bounds.height > 0f &&
                (bounds.width < minPx || bounds.height < minPx)
        }
        .map { node ->
            val bounds = node.boundsInRoot
            "${node.accessibilityLabel()} ${bounds.width.toInt()}x${bounds.height.toInt()}px"
        }

    if (failures.isNotEmpty()) {
        fail("Clickable nodes below ${minSize.value.toInt()}dp touch target: ${failures.joinToString()}")
    }
}

private fun SemanticsNode.accessibilityLabel(): String {
    val text = config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }
    if (!text.isNullOrBlank()) return "'$text'"

    val contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
    if (!contentDescription.isNullOrBlank()) return "'$contentDescription'"

    return "node#${id}"
}
