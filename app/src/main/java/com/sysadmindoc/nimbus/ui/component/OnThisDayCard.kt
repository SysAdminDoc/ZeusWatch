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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
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
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.OnThisDayData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.TimeTravelRange
import com.sysadmindoc.nimbus.data.repository.TimeTravelSource
import com.sysadmindoc.nimbus.data.repository.toZoneIdOrNull
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

/** UI status of the time-travel scrub fetch for the selected date. */
enum class TimeTravelStatus { IDLE, LOADING, ERROR, DATE_UNAVAILABLE }

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
    onDateSelected: ((LocalDate) -> Unit)? = null,
    selectedDay: com.sysadmindoc.nimbus.data.model.TimeTravelDay? = null,
    timeTravelStatus: TimeTravelStatus = TimeTravelStatus.IDLE,
    locationTimeZone: String? = null,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker && onDateSelected != null) {
        ExploreDatesDialog(
            locationTimeZone = locationTimeZone,
            onDismiss = { showDatePicker = false },
            onDateSelected = onDateSelected,
        )
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

            ScrubSection(
                selectedDay = selectedDay,
                status = timeTravelStatus,
                s = s,
                onExploreDates = if (onDateSelected != null) {
                    { showDatePicker = true }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Date picker bounded to the range the time-travel lookup can actually answer:
 * the Open-Meteo archive floor (1940) through today plus the loaded forecast
 * horizon. Today and near-future dates resolve against the loaded forecast,
 * which also covers the archive's ~2-day publication lag. "Today" is anchored
 * to the viewed location's timezone when known, else the device zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreDatesDialog(
    locationTimeZone: String?,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val today = remember(locationTimeZone) {
        LocalDate.now(locationTimeZone.toZoneIdOrNull() ?: ZoneId.systemDefault())
    }
    val maxDate = TimeTravelRange.maxSelectableDate(today)
    val datePickerState = rememberDatePickerState(
        // Default to today: it always resolves from the loaded forecast, while
        // yesterday usually sits inside the archive's publication lag.
        initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = TimeTravelRange.MIN_DATE.year..maxDate.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return TimeTravelRange.classify(date, today) != TimeTravelSource.OUT_OF_RANGE
            }

            override fun isSelectableYear(year: Int): Boolean =
                year in TimeTravelRange.MIN_DATE.year..maxDate.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Scrub result area: the selected-date row plus the fetch status. On failure
 * or an unavailable date the previous result stays visible and a calm inline
 * row explains what happened — the "explore" entry point never disappears.
 */
@Composable
private fun ScrubSection(
    selectedDay: com.sysadmindoc.nimbus.data.model.TimeTravelDay?,
    status: TimeTravelStatus,
    s: NimbusSettings,
    onExploreDates: (() -> Unit)?,
) {
    if (status == TimeTravelStatus.LOADING) {
        Spacer(modifier = Modifier.height(10.dp))
        ScrubLoadingRow()
    } else {
        if (selectedDay != null) {
            Spacer(modifier = Modifier.height(10.dp))
            SelectedDayRow(day = selectedDay, s = s)
        }
        if (status == TimeTravelStatus.ERROR) {
            Spacer(modifier = Modifier.height(8.dp))
            ScrubStatusRow(textRes = R.string.on_this_day_scrub_error)
        }
        if (status == TimeTravelStatus.DATE_UNAVAILABLE) {
            Spacer(modifier = Modifier.height(8.dp))
            ScrubStatusRow(textRes = R.string.on_this_day_date_unavailable)
        }
    }

    if (onExploreDates != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.on_this_day_explore_dates),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusBlueAccent,
            modifier = Modifier
                .heightIn(min = 48.dp) // a11y minimum touch target
                .clickable(onClick = onExploreDates, role = Role.Button)
                .wrapContentHeight()
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun ScrubLoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusTextTertiary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = NimbusBlueAccent,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.on_this_day_loading),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
    }
}

@Composable
private fun ScrubStatusRow(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = NimbusTextTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusTextTertiary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Compact row showing the actual weather for the exact date the user scrubbed
 * to — the archived observation (or in-window forecast) for that single day,
 * distinct from the "across prior years" aggregate above.
 */
@Composable
private fun SelectedDayRow(
    day: com.sysadmindoc.nimbus.data.model.TimeTravelDay,
    s: com.sysadmindoc.nimbus.data.repository.NimbusSettings,
) {
    val dateText = day.date.format(
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM),
    )
    val highLow = stringResource(
        R.string.on_this_day_selected_high_low,
        WeatherFormatter.formatTemperature(day.highC, s),
        WeatherFormatter.formatTemperature(day.lowC, s),
    )
    val precip = day.precipMm?.let { WeatherFormatter.formatPrecipitation(it, s) }
    val description = stringResource(
        R.string.on_this_day_selected_semantics,
        dateText,
        WeatherFormatter.formatTemperature(day.highC, s),
        WeatherFormatter.formatTemperature(day.lowC, s),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusTextTertiary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (precip != null) "$highLow · $precip" else highLow,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
        )
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
            strokeWidth = 1.dp.toPx(),
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
                    strokeWidth = 2.dp.toPx(),
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
                radius = 2.5.dp.toPx(),
                center = Offset(x, y.coerceIn(0f, h)),
                style = Stroke(width = 1.5.dp.toPx()),
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
