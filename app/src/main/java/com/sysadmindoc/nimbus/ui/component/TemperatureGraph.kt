package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * 24-hour temperature trend line graph with gradient fill below the curve.
 */
@Composable
fun TemperatureGraph(
    hourly: List<HourlyConditions>,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val data = remember(hourly) { hourly.take(24) }
    if (data.size < 2) return

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = NimbusTextTertiary,
        fontSize = 10.sp,
    )

    WeatherCard(
        title = "Temperature Trend",
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            val paddingTop = 20f
            val paddingBottom = 28f
            val graphHeight = h - paddingTop - paddingBottom

            val temps = data.map { it.temperature }
            val minTemp = (temps.min() - 2).toFloat()
            val maxTemp = (temps.max() + 2).toFloat()
            val tempRange = maxTemp - minTemp

            if (tempRange == 0f) return@Canvas

            val stepX = w / (data.size - 1).coerceAtLeast(1)

            // Build path
            val linePath = Path()
            val fillPath = Path()
            val points = data.mapIndexed { i, hour ->
                val x = i * stepX
                val y = paddingTop + graphHeight * (1f - (hour.temperature.toFloat() - minTemp) / tempRange)
                Offset(x, y)
            }

            // Smooth curve using cubic bezier
            points.forEachIndexed { i, point ->
                if (i == 0) {
                    linePath.moveTo(point.x, point.y)
                    fillPath.moveTo(point.x, point.y)
                } else {
                    val prev = points[i - 1]
                    val cx = (prev.x + point.x) / 2f
                    linePath.cubicTo(cx, prev.y, cx, point.y, point.x, point.y)
                    fillPath.cubicTo(cx, prev.y, cx, point.y, point.x, point.y)
                }
            }

            // Close fill path
            fillPath.lineTo(points.last().x, h - paddingBottom)
            fillPath.lineTo(points.first().x, h - paddingBottom)
            fillPath.close()

            // Draw gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NimbusBlueAccent.copy(alpha = 0.25f),
                        Color.Transparent,
                    ),
                    startY = paddingTop,
                    endY = h - paddingBottom,
                ),
            )

            // Draw line
            drawPath(
                path = linePath,
                color = NimbusBlueAccent,
                style = Stroke(
                    width = 2.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Draw dots at high and low points
            val maxIdx = temps.indices.maxByOrNull { temps[it] } ?: 0
            val minIdx = temps.indices.minByOrNull { temps[it] } ?: 0

            listOf(maxIdx, minIdx).forEach { idx ->
                if (idx < points.size) {
                    val pt = points[idx]
                    drawCircle(
                        color = NimbusBlueAccent,
                        radius = 4f,
                        center = pt,
                    )
                    drawCircle(
                        color = Color(0xFF0A0E1A),
                        radius = 2f,
                        center = pt,
                    )

                    // Label
                    val label = WeatherFormatter.formatTemperature(temps[idx], s)
                    val measured = textMeasurer.measure(label, labelStyle)
                    val labelY = if (idx == maxIdx) pt.y - 16f else pt.y + 6f
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            pt.x - measured.size.width / 2f,
                            labelY,
                        ),
                    )
                }
            }

            // Time labels (every 6 hours)
            for (i in data.indices step 6) {
                if (i < data.size && i < points.size) {
                    val label = WeatherFormatter.formatHourLabel(data[i].time, s)
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            points[i].x - measured.size.width / 2f,
                            h - paddingBottom + 6f,
                        ),
                    )
                }
            }
        }
    }
}
