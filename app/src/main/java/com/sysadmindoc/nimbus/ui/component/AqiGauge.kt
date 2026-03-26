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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary

/**
 * Circular AQI gauge (270-degree arc) with color gradient from green to brown.
 * Displays current AQI value and level label in the center.
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
                color = Color.White.copy(alpha = 0.08f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Colored progress arc with gradient
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color(0xFF4CAF50),     // Good - green
                    0.25f to Color(0xFFFFEB3B),   // Moderate - yellow
                    0.50f to Color(0xFFFF9800),   // USG - orange
                    0.75f to Color(0xFFF44336),   // Unhealthy - red
                    1.0f to Color(0xFF880E4F),    // Hazardous - maroon
                ),
                startAngle = startAngle,
                sweepAngle = totalSweep * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
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
