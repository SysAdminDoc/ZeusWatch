package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular wind compass showing direction with an arrow pointer
 * and cardinal direction labels. Shows speed in center.
 */
@Composable
fun WindCompass(
    windSpeed: Double,
    windDirection: Int,
    windGusts: Double?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    WeatherCard(
        title = "Wind",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Compass
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.width / 2f - 16f

                    // Outer circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f),
                    )

                    // Tick marks for cardinal + ordinal directions
                    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                    val cardinalStyle = TextStyle(
                        color = NimbusTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    directions.forEachIndexed { i, dir ->
                        val angle = (i * 45f - 90f) * (PI.toFloat() / 180f)
                        val isCardinal = i % 2 == 0

                        // Tick marks
                        val innerR = radius - if (isCardinal) 8f else 5f
                        val outerR = radius
                        drawLine(
                            color = Color.White.copy(alpha = if (isCardinal) 0.3f else 0.15f),
                            start = Offset(cx + cos(angle) * innerR, cy + sin(angle) * innerR),
                            end = Offset(cx + cos(angle) * outerR, cy + sin(angle) * outerR),
                            strokeWidth = if (isCardinal) 1.5f else 1f,
                        )

                        // Cardinal labels
                        if (isCardinal) {
                            val labelR = radius + 12f
                            val lx = cx + cos(angle) * labelR
                            val ly = cy + sin(angle) * labelR
                            val measured = textMeasurer.measure(dir, cardinalStyle)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    lx - measured.size.width / 2f,
                                    ly - measured.size.height / 2f,
                                ),
                            )
                        }
                    }

                    // Direction arrow
                    rotate(windDirection.toFloat(), pivot = Offset(cx, cy)) {
                        val arrowPath = Path().apply {
                            moveTo(cx, cy - radius + 12f) // tip
                            lineTo(cx - 6f, cy - radius + 26f)
                            lineTo(cx + 6f, cy - radius + 26f)
                            close()
                        }
                        drawPath(arrowPath, NimbusBlueAccent)

                        // Arrow line to center
                        drawLine(
                            color = NimbusBlueAccent.copy(alpha = 0.6f),
                            start = Offset(cx, cy - radius + 26f),
                            end = Offset(cx, cy),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Center dot
                    drawCircle(
                        color = NimbusBlueAccent,
                        radius = 3f,
                        center = Offset(cx, cy),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Wind details
            Column {
                val s = LocalUnitSettings.current
                Text(
                    text = WeatherFormatter.formatWindSpeed(windSpeed, s),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = WeatherFormatter.formatWindDirection(windDirection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                )
                if (windGusts != null && windGusts > windSpeed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gusts",
                        style = MaterialTheme.typography.labelMedium,
                        color = NimbusTextTertiary,
                    )
                    Text(
                        text = WeatherFormatter.formatWindSpeed(windGusts, s),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}
