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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Barometric pressure trend card showing a 24h line graph
 * with trend direction and current reading.
 */
@Composable
fun PressureTrendCard(
    hourly: List<HourlyConditions>,
    currentPressure: Double,
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val data = remember(hourly) {
        hourly.take(24).mapNotNull { h ->
            h.surfacePressure?.let { p -> h.time to p }
        }
    }
    if (data.size < 3) return

    val trend = pressureTrend(hourly)
    val trendLabel = trend?.let { stringResource(it.labelRes) }
    val trendSemanticLabel = trend?.let { stringResource(it.semanticLabelRes) }
        ?: stringResource(R.string.pressure_trend_steady_semantic)
    val trendColor = trend?.color ?: NimbusTextSecondary

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)

    val firstPressure = data.first().second
    val lastPressure = data.last().second
    val delta24h = lastPressure - firstPressure
    val semanticSummary = stringResource(
        R.string.pressure_trend_semantics,
        WeatherFormatter.formatPressure(currentPressure, s),
        trendSemanticLabel,
        "%+.1f".format(delta24h),
    )

    WeatherCard(
        titleRes = R.string.card_type_pressure_trend,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WeatherFormatter.formatPressure(currentPressure, s),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                if (trendLabel != null) {
                    Text(
                        text = trendLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = trendColor,
                    )
                }
            }
            // Delta over 24h
            val first = data.first().second
            val last = data.last().second
            val delta = last - first
            Column {
                Text(
                    text = stringResource(R.string.pressure_24h_change),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = "%+.1f hPa".format(delta),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = trendColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mini pressure graph
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
        ) {
            val w = size.width
            val h = size.height
            val paddingBottom = 18f
            val graphH = h - paddingBottom

            val pressures = data.map { it.second }
            val minP = pressures.min() - 1
            val maxP = pressures.max() + 1
            val range = (maxP - minP).coerceAtLeast(2.0)

            val stepX = w / (data.size - 1).coerceAtLeast(1)

            // Reference line at average
            val avg = pressures.average()
            val avgY = (graphH * (1.0 - (avg - minP) / range)).toFloat()
            drawLine(
                color = NimbusTextTertiary.copy(alpha = 0.2f),
                start = Offset(0f, avgY),
                end = Offset(w, avgY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )

            // Pressure line
            val path = Path()
            val points = data.mapIndexed { i, (_, p) ->
                val x = i * stepX
                val y = (graphH * (1.0 - (p - minP) / range)).toFloat()
                Offset(x, y)
            }
            points.forEachIndexed { i, pt ->
                if (i == 0) path.moveTo(pt.x, pt.y)
                else {
                    val prev = points[i - 1]
                    val cx = (prev.x + pt.x) / 2f
                    path.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                }
            }
            drawPath(
                path = path,
                color = NimbusBlueAccent,
                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Current position dot
            val lastPt = points.last()
            drawCircle(NimbusBlueAccent, radius = 4f, center = lastPt)
            drawCircle(Color(0xFF0A0E1A), radius = 2f, center = lastPt)

            // Time labels every 6h
            for (i in data.indices step 6) {
                if (i < points.size) {
                    val label = WeatherFormatter.formatRelativeHourLabel(data[i].first, referenceTime, s)
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

private data class PressureTrendLabel(
    val labelRes: Int,
    val semanticLabelRes: Int,
    val color: Color,
)

private fun pressureTrend(hourly: List<HourlyConditions>): PressureTrendLabel? {
    val pressures = hourly.take(6).mapNotNull { it.surfacePressure }
    if (pressures.size < 3) return null
    val delta = pressures.last() - pressures.first()
    return when {
        delta > 2.0 -> PressureTrendLabel(
            labelRes = R.string.pressure_trend_rising,
            semanticLabelRes = R.string.pressure_trend_rising_semantic,
            color = Color(0xFF4CAF50),
        )
        delta > 0.5 -> PressureTrendLabel(
            labelRes = R.string.pressure_trend_slowly_rising,
            semanticLabelRes = R.string.pressure_trend_slowly_rising_semantic,
            color = Color(0xFF4CAF50),
        )
        delta < -2.0 -> PressureTrendLabel(
            labelRes = R.string.pressure_trend_falling,
            semanticLabelRes = R.string.pressure_trend_falling_semantic,
            color = Color(0xFFFF9800),
        )
        delta < -0.5 -> PressureTrendLabel(
            labelRes = R.string.pressure_trend_slowly_falling,
            semanticLabelRes = R.string.pressure_trend_slowly_falling_semantic,
            color = Color(0xFFFF9800),
        )
        else -> PressureTrendLabel(
            labelRes = R.string.pressure_trend_steady,
            semanticLabelRes = R.string.pressure_trend_steady_semantic,
            color = NimbusTextSecondary,
        )
    }
}
