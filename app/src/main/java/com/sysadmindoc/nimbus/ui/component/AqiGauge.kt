package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Segmented AQI arc gauge (270-degree) with distinct color segments per tier.
 * Inspired by breezy-weather's ArcProgress widget.
 * Each AQI tier gets its own colored segment on the arc.
 * A needle indicator shows the current position.
 */
@Composable
fun AqiGauge(
    aqi: Int,
    level: AqiLevel,
    size: Dp = 100.dp,
    modifier: Modifier = Modifier,
) {
    val maxAqi = 300f
    val progress = (aqi.toFloat() / maxAqi).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 10f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val startAngle = 135f
            val totalSweep = 270f

            // Background arc
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Segmented colored arc — each AQI tier gets its own segment
            val segments = listOf(
                AqiSegment(0f, 50f / maxAqi, Color(0xFF4CAF50)),     // Good
                AqiSegment(50f / maxAqi, 100f / maxAqi, Color(0xFFFFEB3B)),   // Moderate
                AqiSegment(100f / maxAqi, 150f / maxAqi, Color(0xFFFF9800)),  // USG
                AqiSegment(150f / maxAqi, 200f / maxAqi, Color(0xFFF44336)),  // Unhealthy
                AqiSegment(200f / maxAqi, 250f / maxAqi, Color(0xFF9C27B0)),  // Very Unhealthy
                AqiSegment(250f / maxAqi, 1f, Color(0xFF880E4F)),             // Hazardous
            )

            // Determine which segments are visible for correct cap styling
            val visibleSegments = segments.filter { seg ->
                progress > seg.startFrac
            }

            visibleSegments.forEachIndexed { idx, seg ->
                val segStart = startAngle + totalSweep * seg.startFrac
                val effectiveEnd = progress.coerceAtMost(seg.endFrac)
                val effectiveSweep = totalSweep * (effectiveEnd - seg.startFrac)

                // Round cap only on first segment start and last segment end
                // to avoid overlap artifacts at tier boundaries
                val cap = if (visibleSegments.size == 1) {
                    StrokeCap.Round
                } else when (idx) {
                    0 -> StrokeCap.Round
                    visibleSegments.lastIndex -> StrokeCap.Round
                    else -> StrokeCap.Butt
                }

                drawArc(
                    color = seg.color,
                    startAngle = segStart,
                    sweepAngle = effectiveSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = cap),
                )
            }

            // Needle indicator at current position
            val needleAngle = Math.toRadians((startAngle + totalSweep * progress).toDouble())
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val needleRadius = (this.size.width - strokeWidth) / 2f
            val needleX = cx + (needleRadius * cos(needleAngle)).toFloat()
            val needleY = cy + (needleRadius * sin(needleAngle)).toFloat()

            // White outline
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = Offset(needleX, needleY),
            )
            // Colored center
            drawCircle(
                color = level.color,
                radius = 4f,
                center = Offset(needleX, needleY),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$aqi",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = level.color,
            )
            Text(
                text = level.label,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextSecondary,
            )
        }
    }
}

private data class AqiSegment(
    val startFrac: Float,
    val endFrac: Float,
    val color: Color,
)
