package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * 24-hour temperature trend line graph with:
 * - Gradient fill below the curve
 * - Precipitation probability bars behind the line
 * - Interactive drag-to-inspect tooltip showing exact temp/time at touch point
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
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 10.sp)
    val tooltipStyle = TextStyle(color = NimbusTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

    // Interactive state
    var inspectX by remember { mutableFloatStateOf(-1f) }
    var isInspecting by remember { mutableStateOf(false) }

    WeatherCard(
        title = "Temperature Trend",
        modifier = modifier,
    ) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                inspectX = offset.x
                                isInspecting = true
                            },
                            onDragEnd = { isInspecting = false },
                            onDragCancel = { isInspecting = false },
                            onHorizontalDrag = { _, dragAmount ->
                                inspectX = (inspectX + dragAmount).coerceIn(0f, size.width.toFloat())
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                inspectX = offset.x
                                isInspecting = true
                                tryAwaitRelease()
                                isInspecting = false
                            },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                val paddingTop = 24f
                val paddingBottom = 28f
                val graphHeight = h - paddingTop - paddingBottom

                val temps = data.map { it.temperature }
                val rawMin = temps.min()
                val rawMax = temps.max()
                val minTemp = (rawMin - 2).toFloat()
                val maxTemp = if (rawMax == rawMin) (rawMin + 2).toFloat() else (rawMax + 2).toFloat()
                val tempRange = maxTemp - minTemp

                val stepX = w / (data.size - 1).coerceAtLeast(1)
                val points = data.mapIndexed { i, hour ->
                    val x = i * stepX
                    val y = paddingTop + graphHeight * (1f - (hour.temperature.toFloat() - minTemp) / tempRange)
                    Offset(x, y)
                }

                // ── Precipitation bars (background) ──────────────────
                val maxPrecipProb = data.maxOfOrNull { it.precipitationProbability } ?: 0
                if (maxPrecipProb > 0) {
                    val barWidth = stepX * 0.6f
                    data.forEachIndexed { i, hour ->
                        if (hour.precipitationProbability > 5) {
                            val barHeight = (hour.precipitationProbability / 100f) * graphHeight * 0.4f
                            val x = i * stepX - barWidth / 2
                            val y = h - paddingBottom - barHeight
                            drawRect(
                                color = NimbusRainBlue.copy(alpha = 0.15f),
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            )
                        }
                    }
                }

                // ── Build curve paths ────────────────────────────────
                val linePath = Path()
                val fillPath = Path()
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
                fillPath.lineTo(points.last().x, h - paddingBottom)
                fillPath.lineTo(points.first().x, h - paddingBottom)
                fillPath.close()

                // Gradient fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(NimbusBlueAccent.copy(alpha = 0.25f), Color.Transparent),
                        startY = paddingTop, endY = h - paddingBottom,
                    ),
                )

                // Line
                drawPath(
                    path = linePath,
                    color = NimbusBlueAccent,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // ── High/Low dot markers ─────────────────────────────
                val maxIdx = temps.indices.maxByOrNull { temps[it] } ?: 0
                val minIdx = temps.indices.minByOrNull { temps[it] } ?: 0
                listOf(maxIdx, minIdx).forEach { idx ->
                    if (idx < points.size) {
                        val pt = points[idx]
                        drawCircle(NimbusBlueAccent, radius = 4f, center = pt)
                        drawCircle(Color(0xFF0A0E1A), radius = 2f, center = pt)

                        val label = WeatherFormatter.formatTemperature(temps[idx], s)
                        val measured = textMeasurer.measure(label, labelStyle)
                        val labelY = if (idx == maxIdx) pt.y - 16f else pt.y + 6f
                        drawText(measured, topLeft = Offset(pt.x - measured.size.width / 2f, labelY))
                    }
                }

                // ── Time labels (every 6 hours) ─────────────────────
                for (i in data.indices step 6) {
                    if (i < points.size) {
                        val label = WeatherFormatter.formatHourLabel(data[i].time, s)
                        val measured = textMeasurer.measure(label, labelStyle)
                        drawText(measured, topLeft = Offset(points[i].x - measured.size.width / 2f, h - paddingBottom + 6f))
                    }
                }

                // ── Interactive inspection line + tooltip ────────────
                if (isInspecting && inspectX >= 0f) {
                    // Find nearest data point
                    val nearestIdx = ((inspectX / stepX).toInt()).coerceIn(0, data.lastIndex)
                    val nearPt = points[nearestIdx]
                    val nearHour = data[nearestIdx]

                    // Vertical guide line
                    drawLine(
                        color = NimbusTextTertiary.copy(alpha = 0.5f),
                        start = Offset(nearPt.x, paddingTop),
                        end = Offset(nearPt.x, h - paddingBottom),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                    )

                    // Highlighted dot
                    drawCircle(NimbusBlueAccent, radius = 6f, center = nearPt)
                    drawCircle(Color.White, radius = 3f, center = nearPt)

                    // Tooltip
                    val tempText = WeatherFormatter.formatTemperature(nearHour.temperature, s)
                    val timeText = WeatherFormatter.formatHourLabel(nearHour.time, s)
                    val precipText = if (nearHour.precipitationProbability > 0) " ${nearHour.precipitationProbability}%" else ""
                    val tooltipText = "$tempText \u2022 $timeText$precipText"
                    val measured = textMeasurer.measure(tooltipText, tooltipStyle)

                    // Background rect for tooltip
                    val tooltipX = (nearPt.x - measured.size.width / 2f).coerceIn(0f, w - measured.size.width)
                    val tooltipY = 2f
                    drawRoundRect(
                        color = Color(0xFF1A2340),
                        topLeft = Offset(tooltipX - 4f, tooltipY),
                        size = androidx.compose.ui.geometry.Size(measured.size.width + 8f, measured.size.height + 4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                    )
                    drawText(measured, topLeft = Offset(tooltipX, tooltipY + 2f))
                }
            }
        }
    }
}
