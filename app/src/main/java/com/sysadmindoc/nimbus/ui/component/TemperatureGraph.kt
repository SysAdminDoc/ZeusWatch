package com.sysadmindoc.nimbus.ui.component

import android.content.Context
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * 24-hour temperature trend line graph with:
 * - Gradient fill below the curve
 * - Precipitation probability bars behind the line
 * - Interactive drag-to-inspect tooltip showing exact temp/time at touch point
 */
@Composable
fun TemperatureGraph(
    hourly: List<HourlyConditions>,
    referenceTime: LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
    normalHigh: Double? = null,
    normalLow: Double? = null,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    val data = remember(hourly) { hourly.take(24) }
    if (data.size < 2) return

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 10.sp)
    val tooltipStyle = TextStyle(color = NimbusTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

    // Interactive state
    var inspectX by remember { mutableFloatStateOf(-1f) }
    var isInspecting by remember { mutableStateOf(false) }

    // Summarize the series for TalkBack — exact drag interaction isn't
    // meaningful to a screen reader, but the shape of the curve is.
    val trendSummary = remember(data, s.tempUnit) { buildTemperatureTrendSummary(data, s) }
    val trendSummaryText = stringResource(
        R.string.temperature_graph_summary,
        trendSummary.hours,
        trendSummary.low,
        trendSummary.high,
        stringResource(trendSummary.directionRes),
    )
    val semanticSummary = stringResource(R.string.temperature_graph_semantics, trendSummaryText)

    WeatherCard(
        titleRes = R.string.card_type_temperature_graph,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
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
                val metrics = temperatureGraphMetrics(size.width, size.height, data, density)
                val points = temperaturePoints(data, metrics)
                val paths = buildTemperaturePaths(points, metrics.baselineY)

                drawPrecipitationBars(data, metrics)
                drawNormalBand(metrics, normalHigh, normalLow)
                drawTemperatureCurve(paths, metrics)
                drawFeelsLikeOverlay(data, metrics)
                drawHighLowMarkers(data, points, textMeasurer, labelStyle, s)
                drawTimeLabels(data, points, referenceTime, s, textMeasurer, labelStyle, metrics, context)
                val inspectionText = TemperatureInspectionText(
                    textMeasurer = textMeasurer,
                    tooltipStyle = tooltipStyle,
                    settings = s,
                    referenceTime = referenceTime,
                    context = context,
                )
                drawInspectionOverlay(
                    data = data,
                    points = points,
                    inspectX = inspectX,
                    isInspecting = isInspecting,
                    metrics = metrics,
                    inspectionText = inspectionText,
                )
            }
        }
    }
}

private data class TemperatureGraphMetrics(
    val width: Float,
    val height: Float,
    val paddingTop: Float,
    val paddingBottom: Float,
    val graphHeight: Float,
    val minTemp: Float,
    val maxTemp: Float,
    val tempRange: Float,
    val stepX: Float,
) {
    val baselineY: Float = height - paddingBottom
}

private data class TemperatureInspectionText(
    val textMeasurer: TextMeasurer,
    val tooltipStyle: TextStyle,
    val settings: NimbusSettings,
    val referenceTime: LocalDateTime?,
    val context: Context,
)

private data class TemperatureGraphPaths(
    val line: Path,
    val fill: Path,
)

private fun temperatureGraphMetrics(
    width: Float,
    height: Float,
    data: List<HourlyConditions>,
    density: Float = 1f,
): TemperatureGraphMetrics {
    val paddingTop = 24f * density
    val paddingBottom = 28f * density
    val graphHeight = height - paddingTop - paddingBottom
    val temps = data.map { it.temperature }
    val allTemps = temps + data.mapNotNull { it.feelsLike }
    val rawMin = allTemps.min()
    val rawMax = allTemps.max()
    val minTemp = (rawMin - 2).toFloat()
    val maxTemp = if (rawMax == rawMin) (rawMin + 2).toFloat() else (rawMax + 2).toFloat()

    return TemperatureGraphMetrics(
        width = width,
        height = height,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        graphHeight = graphHeight,
        minTemp = minTemp,
        maxTemp = maxTemp,
        tempRange = maxTemp - minTemp,
        stepX = width / (data.size - 1).coerceAtLeast(1),
    )
}

private fun temperaturePoints(
    data: List<HourlyConditions>,
    metrics: TemperatureGraphMetrics,
): List<Offset> = data.mapIndexed { index, hour ->
    Offset(
        x = index * metrics.stepX,
        y = temperatureY(hour.temperature, metrics),
    )
}

private fun temperatureY(
    temperature: Double,
    metrics: TemperatureGraphMetrics,
): Float = metrics.paddingTop +
    metrics.graphHeight * (1f - (temperature.toFloat() - metrics.minTemp) / metrics.tempRange)

private fun buildTemperaturePaths(
    points: List<Offset>,
    baselineY: Float,
): TemperatureGraphPaths {
    val linePath = Path()
    val fillPath = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) {
            linePath.moveTo(point.x, point.y)
            fillPath.moveTo(point.x, point.y)
        } else {
            val previous = points[index - 1]
            val controlX = (previous.x + point.x) / 2f
            linePath.cubicTo(controlX, previous.y, controlX, point.y, point.x, point.y)
            fillPath.cubicTo(controlX, previous.y, controlX, point.y, point.x, point.y)
        }
    }
    fillPath.lineTo(points.last().x, baselineY)
    fillPath.lineTo(points.first().x, baselineY)
    fillPath.close()
    return TemperatureGraphPaths(line = linePath, fill = fillPath)
}

private fun DrawScope.drawPrecipitationBars(
    data: List<HourlyConditions>,
    metrics: TemperatureGraphMetrics,
) {
    if ((data.maxOfOrNull { it.precipitationProbability } ?: 0) <= 0) return

    val barWidth = metrics.stepX * 0.6f
    data.forEachIndexed { index, hour ->
        if (hour.precipitationProbability > 5) {
            val barHeight = (hour.precipitationProbability / 100f) * metrics.graphHeight * 0.4f
            drawRect(
                color = NimbusRainBlue.copy(alpha = 0.15f),
                topLeft = Offset(index * metrics.stepX - barWidth / 2, metrics.baselineY - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

private fun DrawScope.drawNormalBand(
    metrics: TemperatureGraphMetrics,
    normalHigh: Double?,
    normalLow: Double?,
) {
    val high = normalHigh ?: return
    val low = normalLow ?: return
    if (high.isNaN() || low.isNaN() || high <= low) return

    val highY = temperatureY(high, metrics)
    val lowY = temperatureY(low, metrics)
    drawRect(
        color = Color(0x15FFFFFF),
        topLeft = Offset(0f, highY.coerceAtLeast(metrics.paddingTop)),
        size = Size(metrics.width, (lowY - highY).coerceAtLeast(2f)),
    )
    drawNormalLine(highY, metrics)
    drawNormalLine(lowY, metrics)
}

private fun DrawScope.drawNormalLine(
    y: Float,
    metrics: TemperatureGraphMetrics,
) {
    drawLine(
        color = NimbusTextTertiary.copy(alpha = 0.3f),
        start = Offset(0f, y),
        end = Offset(metrics.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
}

private fun DrawScope.drawTemperatureCurve(
    paths: TemperatureGraphPaths,
    metrics: TemperatureGraphMetrics,
) {
    drawPath(
        path = paths.fill,
        brush = Brush.verticalGradient(
            listOf(NimbusBlueAccent.copy(alpha = 0.25f), Color.Transparent),
            startY = metrics.paddingTop,
            endY = metrics.baselineY,
        ),
    )
    drawPath(
        path = paths.line,
        color = NimbusBlueAccent,
        style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawFeelsLikeOverlay(
    data: List<HourlyConditions>,
    metrics: TemperatureGraphMetrics,
) {
    if (!shouldDrawFeelsLikeOverlay(data)) return
    drawPath(
        path = buildFeelsLikePath(data, metrics),
        color = Color(0xFFFF9800).copy(alpha = 0.6f),
        style = Stroke(
            width = 1.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        ),
    )
}

private fun shouldDrawFeelsLikeOverlay(data: List<HourlyConditions>): Boolean {
    if (data.any { it.feelsLike == null }) return false
    val maxDiff = data.maxOfOrNull { abs((it.feelsLike ?: it.temperature) - it.temperature) } ?: 0.0
    return maxDiff >= 3.0
}

private fun buildFeelsLikePath(
    data: List<HourlyConditions>,
    metrics: TemperatureGraphMetrics,
): Path {
    val path = Path()
    data.forEachIndexed { index, hour ->
        val x = index * metrics.stepX
        val y = temperatureY(hour.feelsLike ?: hour.temperature, metrics)
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            val previous = data[index - 1]
            val previousX = (index - 1) * metrics.stepX
            val previousY = temperatureY(previous.feelsLike ?: previous.temperature, metrics)
            val controlX = (previousX + x) / 2f
            path.cubicTo(controlX, previousY, controlX, y, x, y)
        }
    }
    return path
}

private fun DrawScope.drawHighLowMarkers(
    data: List<HourlyConditions>,
    points: List<Offset>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    settings: NimbusSettings,
) {
    val temps = data.map { it.temperature }
    val maxIdx = temps.indices.maxByOrNull { temps[it] } ?: 0
    val minIdx = temps.indices.minByOrNull { temps[it] } ?: 0
    listOf(maxIdx, minIdx).forEach { index ->
        if (index < points.size) {
            drawTemperatureMarker(points[index], temps[index], index == maxIdx, textMeasurer, labelStyle, settings)
        }
    }
}

private fun DrawScope.drawTemperatureMarker(
    point: Offset,
    temperature: Double,
    isHigh: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    settings: NimbusSettings,
) {
    drawCircle(NimbusBlueAccent, radius = 4.dp.toPx(), center = point)
    drawCircle(Color(0xFF0A0E1A), radius = 2.dp.toPx(), center = point)

    val label = WeatherFormatter.formatTemperature(temperature, settings)
    val measured = textMeasurer.measure(label, labelStyle)
    val labelY = if (isHigh) point.y - 16.dp.toPx() else point.y + 6.dp.toPx()
    drawText(measured, topLeft = Offset(point.x - measured.size.width / 2f, labelY))
}

private fun DrawScope.drawTimeLabels(
    data: List<HourlyConditions>,
    points: List<Offset>,
    referenceTime: LocalDateTime?,
    settings: NimbusSettings,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    metrics: TemperatureGraphMetrics,
    context: Context,
) {
    for (index in data.indices step 6) {
        if (index < points.size) {
            val label = WeatherFormatter.formatRelativeHourLabel(context, data[index].time, referenceTime, settings)
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft = Offset(points[index].x - measured.size.width / 2f, metrics.baselineY + 6f),
            )
        }
    }
}

private fun DrawScope.drawInspectionOverlay(
    data: List<HourlyConditions>,
    points: List<Offset>,
    inspectX: Float,
    isInspecting: Boolean,
    metrics: TemperatureGraphMetrics,
    inspectionText: TemperatureInspectionText,
) {
    if (!isInspecting || inspectX < 0f || metrics.stepX <= 0f) return

    val nearestIndex = ((inspectX / metrics.stepX).toInt()).coerceIn(0, data.lastIndex)
    val nearPoint = points[nearestIndex]
    val nearHour = data[nearestIndex]
    drawInspectionGuide(nearPoint, metrics)
    drawCircle(NimbusBlueAccent, radius = 6.dp.toPx(), center = nearPoint)
    drawCircle(Color.White, radius = 3.dp.toPx(), center = nearPoint)
    drawInspectionTooltip(nearHour, nearPoint, metrics, inspectionText)
}

private fun DrawScope.drawInspectionGuide(
    point: Offset,
    metrics: TemperatureGraphMetrics,
) {
    drawLine(
        color = NimbusTextTertiary.copy(alpha = 0.5f),
        start = Offset(point.x, metrics.paddingTop),
        end = Offset(point.x, metrics.baselineY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
}

private fun DrawScope.drawInspectionTooltip(
    hour: HourlyConditions,
    point: Offset,
    metrics: TemperatureGraphMetrics,
    inspectionText: TemperatureInspectionText,
) {
    val tooltipText = inspectionTooltipText(
        hour = hour,
        settings = inspectionText.settings,
        referenceTime = inspectionText.referenceTime,
        context = inspectionText.context,
    )
    val measured = inspectionText.textMeasurer.measure(tooltipText, inspectionText.tooltipStyle)
    val tooltipX = (point.x - measured.size.width / 2f).coerceIn(0f, metrics.width - measured.size.width)
    val tooltipY = 2.dp.toPx()
    val padH = 4.dp.toPx()
    val padV = 2.dp.toPx()
    drawRoundRect(
        color = Color(0xFF1A2340),
        topLeft = Offset(tooltipX - padH, tooltipY),
        size = Size(measured.size.width + padH * 2, measured.size.height + padH),
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
    drawText(measured, topLeft = Offset(tooltipX, tooltipY + padV))
}

private fun inspectionTooltipText(
    hour: HourlyConditions,
    settings: NimbusSettings,
    referenceTime: LocalDateTime?,
    context: Context,
): String {
    val tempText = WeatherFormatter.formatTemperature(hour.temperature, settings)
    val timeText = WeatherFormatter.formatRelativeHourLabel(context, hour.time, referenceTime, settings)
    val precipText = if (hour.precipitationProbability > 0) " ${hour.precipitationProbability}%" else ""
    return "$tempText \u2022 $timeText$precipText"
}

private data class TemperatureTrendSummary(
    val hours: Int,
    val low: Int,
    val high: Int,
    val directionRes: Int,
)

private fun buildTemperatureTrendSummary(
    data: List<HourlyConditions>,
    settings: NimbusSettings,
): TemperatureTrendSummary {
    val temps = data.map { WeatherFormatter.convertedTemp(it.temperature, settings) }
    val first = temps.first()
    val last = temps.last()
    val low = temps.min()
    val high = temps.max()
    val directionRes = when {
        last > first + 1 -> R.string.temperature_trend_warmer
        last < first - 1 -> R.string.temperature_trend_cooler
        else -> R.string.temperature_trend_steady
    }
    return TemperatureTrendSummary(
        hours = data.size,
        low = low.toInt(),
        high = high.toInt(),
        directionRes = directionRes,
    )
}
