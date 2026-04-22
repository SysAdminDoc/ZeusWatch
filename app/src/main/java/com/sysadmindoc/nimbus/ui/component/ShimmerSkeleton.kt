package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.util.isReducedMotionEnabled

/**
 * Shimmer loading skeleton matching the main screen layout.
 */
@Composable
fun ShimmerLoadingSkeleton(modifier: Modifier = Modifier) {
    val reducedMotion = isReducedMotionEnabled()

    // When reduced motion is enabled, use a static gray brush instead of animating
    val shimmerBrush = if (reducedMotion) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.08f),
            ),
        )
    } else {
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
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.05f),
            ),
            start = Offset(translateX, 0f),
            end = Offset(translateX + 400f, 0f),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient)
            .padding(horizontal = LocalAdaptiveLayout.current.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NimbusBlueAccent.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Refreshing forecast",
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop.copy(alpha = 0.86f),
                            NimbusCardBg,
                            NimbusGlassBottom,
                        ),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShimmerBox(width = 132.dp, height = 28.dp, brush = shimmerBrush)
                    ShimmerBox(width = 88.dp, height = 28.dp, brush = shimmerBrush)
                }
                Spacer(modifier = Modifier.height(20.dp))
                ShimmerBox(width = 168.dp, height = 16.dp, brush = shimmerBrush)
                Spacer(modifier = Modifier.height(10.dp))
                ShimmerBox(width = 142.dp, height = 72.dp, brush = shimmerBrush)
                Spacer(modifier = Modifier.height(10.dp))
                ShimmerBox(width = 180.dp, height = 18.dp, brush = shimmerBrush)
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(width = 224.dp, height = 14.dp, brush = shimmerBrush)
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(width = 92.dp, height = 52.dp, brush = shimmerBrush)
                    ShimmerBox(width = 92.dp, height = 52.dp, brush = shimmerBrush)
                    ShimmerBox(width = 92.dp, height = 52.dp, brush = shimmerBrush)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Hourly card placeholder
        ShimmerCard(height = 130.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Summary card placeholder
        ShimmerCard(height = 60.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Temp graph placeholder
        ShimmerCard(height = 180.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Daily card placeholder
        ShimmerCard(height = 280.dp, brush = shimmerBrush)
        Spacer(modifier = Modifier.height(12.dp))

        // Details card placeholder
        ShimmerCard(height = 180.dp, brush = shimmerBrush)
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
            .clip(RoundedCornerShape(12.dp))
            .background(NimbusCardBg)
            .border(1.dp, NimbusCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.02f))
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
            .clip(RoundedCornerShape(22.dp))
            .background(NimbusCardBg)
            .background(Color.White.copy(alpha = 0.02f))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent,
                    ),
                ),
            )
            .background(brush)
    )
}
