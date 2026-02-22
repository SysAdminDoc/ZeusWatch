package com.sysadmindoc.nimbus.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wraps content with a predictive back gesture animation.
 * On back swipe: scales down to 90%, fades to 60%, and shifts sideways
 * in the direction of the swipe. On release, navigates back; on cancel, snaps back.
 */
@Composable
fun PredictiveBackScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val translationX = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { event ->
                val p = event.progress
                scale.snapTo(1f - (p * 0.1f))  // 1.0 -> 0.9
                alpha.snapTo(1f - (p * 0.4f))   // 1.0 -> 0.6
                // Shift left or right based on swipe edge
                val direction = if (event.swipeEdge == 0) -1f else 1f
                translationX.snapTo(direction * p * 80f)
            }
            // Gesture completed — navigate back
            onBack()
        } catch (_: CancellationException) {
            // Gesture cancelled — snap back
            scale.animateTo(1f)
            alpha.animateTo(1f)
            translationX.animateTo(0f)
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
            this.translationX = translationX.value
        },
    ) {
        content()
    }
}
