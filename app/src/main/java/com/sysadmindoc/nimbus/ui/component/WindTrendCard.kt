package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * 24-hour wind speed forecast card with a line graph for sustained wind
 * and bars for gusts when they exceed sustained speed significantly.
 */
@Composable
fun WindTrendCard(
    hourly: List<HourlyConditions>,
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val data = remember(hourly) { hourly.take(24) }
    if (data.size < 3) return

    val maxWind = data.maxOfOrNull { it.windSpeed ?: 0.0 } ?: return
    val maxGust = data.maxOfOrNull { it.windGusts ?: 0.0 } ?: 0.0
    val peakIdx = data.indices.maxByOrNull { data[it].windSpeed ?: 0.0 } ?: 0
    val peakHour = data[peakIdx]

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)

    WeatherCard(title = "Wind Forecast", modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Peak ${WeatherFormatter.formatWindSpeed(maxWind, s)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "at ${WeatherFormatter.formatRelativeHourLabel(peakHour.time, referenceTime, s)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextSecondary,
                )
            }
            if (maxGust > maxWind * 1.2) {
                Column {
                    Text(
                        text = "Gusts to",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                    Text(
                        text = WeatherFormatter.formatWindSpeed(maxGust, s),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFFF9800),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
        ) {
            val w = size.width
            val h = size.height
            val paddingBottom = 18f
            val graphH = h - paddingBottom

            val ceiling = maxOf(maxWind, maxGust).coerceAtLeast(5.0)
            val stepX = w / (data.size - 1).coerceAtLeast(1)

            // Gust bars (background)
            data.forEachIndexed { i, hour ->
                val gust = hour.windGusts ?: 0.0
                val wind = hour.windSpeed ?: 0.0
                if (gust > wind * 1.2 && gust > 1.0) {
                    val barH = (gust / ceiling * graphH * 0.85).toFloat()
                    val barW = stepX * 0.5f
                    val x = i * stepX - barW / 2
                    drawRoundRect(
                        color = Color(0xFFFF9800).copy(alpha = 0.15f),
                        topLeft = Offset(x, graphH - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(2f),
                    )
                }
            }

            // Wind speed line
            val points = data.mapIndexed { i, hour ->
                val x = i * stepX
                val speed = hour.windSpeed ?: 0.0
                val y = (graphH * (1.0 - speed / ceiling)).toFloat()
                Offset(x, y)
            }

            // Gradient fill
            val fillPath = Path()
            points.forEachIndexed { i, pt ->
                if (i == 0) fillPath.moveTo(pt.x, pt.y)
                else {
                    val prev = points[i - 1]
                    val cx = (prev.x + pt.x) / 2f
                    fillPath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                }
            }
            fillPath.lineTo(points.last().x, graphH)
            fillPath.lineTo(points.first().x, graphH)
            fillPath.close()
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(NimbusBlueAccent.copy(alpha = 0.2f), Color.Transparent),
                    startY = 0f, endY = graphH,
                ),
            )

            // Line
            val linePath = Path()
            points.forEachIndexed { i, pt ->
                if (i == 0) linePath.moveTo(pt.x, pt.y)
                else {
                    val prev = points[i - 1]
                    val cx = (prev.x + pt.x) / 2f
                    linePath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                }
            }
            drawPath(
                path = linePath,
                color = NimbusBlueAccent,
                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Peak dot
            if (peakIdx < points.size) {
                drawCircle(NimbusBlueAccent, radius = 4f, center = points[peakIdx])
                drawCircle(Color(0xFF0A0E1A), radius = 2f, center = points[peakIdx])
            }

            // Time labels every 6h
            for (i in data.indices step 6) {
                if (i < points.size) {
                    val label = WeatherFormatter.formatRelativeHourLabel(data[i].time, referenceTime, s)
                    val m = textMeasurer.measure(label, labelStyle)
                    drawText(m, topLeft = Offset(
                        (points[i].x - m.size.width / 2f).coerceIn(0f, w - m.size.width),
                        graphH + 2f,
                    ))
                }
            }
        }
    }
}
