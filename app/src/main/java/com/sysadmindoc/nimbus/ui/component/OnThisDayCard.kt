package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.OnThisDayData
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * "On This Day" card — surfaces historical context for today's forecast by
 * comparing the forecast high to the 10-year average high for this calendar
 * date at this location, and plotting prior years' highs as a sparkline.
 *
 * Empty state: rendered when the archive returned no usable observations
 * (polar regions, brand-new settlements, first-ever run with no cache + no
 * network). Loading is handled upstream — if [data] is null and we are
 * still fetching, the card is not rendered at all.
 */
@Composable
fun OnThisDayCard(
    data: OnThisDayData?,
    forecastHighC: Double?,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current

    WeatherCard(title = "On This Day", modifier = modifier) {
        if (data == null || data.priorYears.isEmpty()) {
            Text(
                text = "Historical data unavailable for this location.",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextTertiary,
            )
            return@WeatherCard
        }

        // Anomaly delta: today's forecast vs. 10-year average high (both °C).
        val avgHigh = data.averageHighC
        val delta = forecastHighC?.let { it - avgHigh }
        val deltaFmt = delta?.let { deltaLabel(it, s) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${data.priorYears.size}-year avg high",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                    Text(
                        text = WeatherFormatter.formatTemperature(avgHigh, s),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = NimbusTextPrimary,
                    )
                }
                if (deltaFmt != null) {
                    DeltaBadge(deltaCelsius = delta, label = deltaFmt)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Record high / low mini-row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiniStat(
                    label = "Record high",
                    value = WeatherFormatter.formatTemperature(data.recordHighC, s),
                    color = NimbusWarning,
                )
                MiniStat(
                    label = "Record low",
                    value = WeatherFormatter.formatTemperature(data.recordLowC, s),
                    color = NimbusBlueAccent,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Prior-year highs sparkline
            Text(
                text = "Prior years (newest first)",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PriorYearsSparkline(
                highsCelsius = data.priorYears.map { it.highC }.asReversed(), // chronological left→right
                avgHighCelsius = avgHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics {
                        contentDescription = "Prior-year highs for this date, " +
                            "ranging from ${WeatherFormatter.formatTemperature(data.recordLowC, s)} " +
                            "to ${WeatherFormatter.formatTemperature(data.recordHighC, s)}."
                    },
            )

            // Year tick labels under sparkline
            val oldestYear = data.priorYears.last().year
            val newestYear = data.priorYears.first().year
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = oldestYear.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = newestYear.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }
    }
}

@Composable
private fun DeltaBadge(deltaCelsius: Double, label: String) {
    val bg = when {
        deltaCelsius > 3.0 -> NimbusWarning.copy(alpha = 0.20f)
        deltaCelsius < -3.0 -> NimbusRainBlue.copy(alpha = 0.20f)
        else -> NimbusTextTertiary.copy(alpha = 0.15f)
    }
    val fg = when {
        deltaCelsius > 3.0 -> NimbusWarning
        deltaCelsius < -3.0 -> NimbusRainBlue
        else -> NimbusTextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun PriorYearsSparkline(
    highsCelsius: List<Double>,
    avgHighCelsius: Double,
    modifier: Modifier = Modifier,
) {
    // Caller already guarded against empty lists, but defend anyway.
    if (highsCelsius.size < 2) {
        Spacer(modifier = modifier)
        return
    }

    val minY = highsCelsius.min()
    val maxY = highsCelsius.max()
    // Avoid a degenerate zero range (all years identical) — forces a flat line.
    val range = (maxY - minY).coerceAtLeast(0.5)

    val lineColor = NimbusBlueAccent
    val avgLineColor = NimbusTextTertiary.copy(alpha = 0.6f)
    val pointColor = NimbusBlueAccent

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = if (highsCelsius.size == 1) w / 2f else w / (highsCelsius.size - 1)

        // Average guideline
        val avgY = h - ((avgHighCelsius - minY) / range * h).toFloat()
        drawLine(
            color = avgLineColor,
            start = Offset(0f, avgY.coerceIn(0f, h)),
            end = Offset(w, avgY.coerceIn(0f, h)),
            strokeWidth = 1f,
        )

        // Polyline
        var prev: Offset? = null
        highsCelsius.forEachIndexed { i, v ->
            val x = if (highsCelsius.size == 1) w / 2f else i * stepX
            val y = h - ((v - minY) / range * h).toFloat()
            val pt = Offset(x, y.coerceIn(0f, h))
            prev?.let { p ->
                drawLine(
                    color = lineColor,
                    start = p,
                    end = pt,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            prev = pt
        }

        // Points (after polyline so they sit on top)
        highsCelsius.forEachIndexed { i, v ->
            val x = if (highsCelsius.size == 1) w / 2f else i * stepX
            val y = h - ((v - minY) / range * h).toFloat()
            drawCircle(
                color = pointColor,
                radius = 3f,
                center = Offset(x, y.coerceIn(0f, h)),
                style = Stroke(width = 2f),
            )
        }
    }
}

/** Build a "+5° warmer" / "3° cooler" label in the user's temperature unit. */
private fun deltaLabel(
    deltaCelsius: Double,
    s: com.sysadmindoc.nimbus.data.repository.NimbusSettings,
): String {
    // |delta| in °C is equivalent to |delta| × 1.8 in °F; direction is
    // unit-agnostic.
    val magnitudeC = deltaCelsius.absoluteValue
    val magnitudeUser = if (s.tempUnit == com.sysadmindoc.nimbus.data.repository.TempUnit.FAHRENHEIT) {
        magnitudeC * 1.8
    } else {
        magnitudeC
    }
    // Round to nearest whole degree for readability.
    val rounded = kotlin.math.round(magnitudeUser).toInt()
    if (rounded == 0) return "near normal"
    val unitSymbol = if (s.tempUnit == com.sysadmindoc.nimbus.data.repository.TempUnit.FAHRENHEIT) "°F" else "°C"
    val direction = if (deltaCelsius >= 0) "warmer" else "cooler"
    return "${rounded}${unitSymbol} $direction"
}
