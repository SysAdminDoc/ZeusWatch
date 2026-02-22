package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg

/**
 * Shimmer loading skeleton matching the main screen layout.
 */
@Composable
fun ShimmerLoadingSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.05f),
        ),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 400f, 0f),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Location name placeholder
        ShimmerBox(width = 140.dp, height = 24.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(16.dp))

        // Big temperature placeholder
        ShimmerBox(width = 120.dp, height = 80.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Condition text
        ShimmerBox(width = 100.dp, height = 18.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerBox(width = 80.dp, height = 14.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerBox(width = 160.dp, height = 14.dp, brush = shimmerBrush)

        Spacer(modifier = Modifier.height(32.dp))

        // Hourly card placeholder
        ShimmerCard(height = 140.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Daily card placeholder
        ShimmerCard(height = 320.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Details card placeholder
        ShimmerCard(height = 200.dp, brush = shimmerBrush)
    }
}

@Composable
private fun ShimmerBox(
    width: Dp,
    height: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusCardBg)
            .background(brush)
    )
}

@Composable
private fun ShimmerCard(
    height: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(NimbusCardBg)
            .background(brush)
    )
}
