package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.OnThisDayData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnThisDayCard(
    data: OnThisDayData?,
    forecastHighC: Double?,
    onDateSelected: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker && onDateSelected != null) {
        val yesterday = LocalDate.now().minusDays(1)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            if (selected.isBefore(LocalDate.now())) {
                                onDateSelected(selected)
                            }
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
    val s = LocalUnitSettings.current
    val semanticDescription = if (data == null || data.priorYears.isEmpty()) {
        null
    } else {
        val delta = forecastHighC?.let { it - data.averageHighC }
        val deltaText = if (delta == null) {
            stringResource(R.string.on_this_day_compare_unavailable)
        } else {
            deltaLabel(delta, s)
        }
        stringResource(
            R.string.on_this_day_semantics,
            data.priorYears.size,
            WeatherFormatter.formatTemperature(data.averageHighC, s),
            WeatherFormatter.formatTemperature(data.recordHighC, s),
            WeatherFormatter.formatTemperature(data.recordLowC, s),
            deltaText,
        )
    }
    val cardModifier = if (data == null || data.priorYears.isEmpty()) {
        modifier
    } else {
        modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticDescription.orEmpty()
        }
    }

    WeatherCard(titleRes = R.string.card_type_on_this_day, modifier = cardModifier) {
        if (data == null || data.priorYears.isEmpty()) {
            Text(
                text = stringResource(R.string.on_this_day_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextTertiary,
            )
            return@WeatherCard
        }

        // Anomaly delta: today's forecast vs. 10-year average high (both °C).
        val avgHigh = data.averageHighC
        val delta = forecastHighC?.let { it - avgHigh }
        val deltaFmt = if (delta == null) null else deltaLabel(delta, s)
        val recordLowText = WeatherFormatter.formatTemperature(data.recordLowC, s)
        val recordHighText = WeatherFormatter.formatTemperature(data.recordHighC, s)
        val sparklineDescription = stringResource(R.string.on_this_day_sparkline_semantics, recordLowText, recordHighText)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.on_this_day_avg_high, data.priorYears.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                    Text(
                        text = WeatherFormatter.formatTemperature(avgHigh, s),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = NimbusTextPrimary,
                    )
                }
                if (delta != null && deltaFmt != null) {
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
                    labelRes = R.string.on_this_day_record_high,
                    value = recordHighText,
                    color = NimbusWarning,
                )
                MiniStat(
                    labelRes = R.string.on_this_day_record_low,
                    value = recordLowText,
                    color = NimbusBlueAccent,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Prior-year highs sparkline
            Text(
                text = stringResource(R.string.on_this_day_prior_years),
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
                        contentDescription = sparklineDescription
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

            if (onDateSelected != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.on_this_day_explore_dates),
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusBlueAccent,
                    modifier = Modifier
                        .clickable { showDatePicker = true }
                        .padding(vertical = 4.dp),
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
private fun MiniStat(labelRes: Int, value: String, color: Color) {
    Column {
        Text(
            text = stringResource(labelRes),
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
@Composable
private fun deltaLabel(
    deltaCelsius: Double,
    s: NimbusSettings,
): String {
    // |delta| in °C is equivalent to |delta| × 1.8 in °F; direction is
    // unit-agnostic.
    val magnitudeC = deltaCelsius.absoluteValue
    val magnitudeUser = if (s.tempUnit == TempUnit.FAHRENHEIT) {
        magnitudeC * 1.8
    } else {
        magnitudeC
    }
    // Round to nearest whole degree for readability.
    val rounded = kotlin.math.round(magnitudeUser).toInt()
    if (rounded == 0) return stringResource(R.string.on_this_day_near_normal)
    val unitSymbol = if (s.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
    val labelRes = if (deltaCelsius >= 0) {
        R.string.on_this_day_delta_warmer
    } else {
        R.string.on_this_day_delta_cooler
    }
    return stringResource(labelRes, rounded, unitSymbol)
}
