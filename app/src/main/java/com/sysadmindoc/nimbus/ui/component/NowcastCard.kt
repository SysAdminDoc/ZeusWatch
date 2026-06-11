package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDateTime

/**
 * Rain-in-next-hour nowcasting card.
 * Shows a 60-minute bar chart of expected precipitation at 15-minute intervals.
 */
@Composable
fun NowcastCard(
    data: List<MinutelyPrecipitation>,
    referenceTime: LocalDateTime? = data.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    // Filter to next ~90 minutes of data
    val filtered = remember(data, referenceTime) {
        val now = referenceTime ?: return@remember data.take(8)
        data.filter { it.time.isAfter(now.minusMinutes(5)) }
            .take(8) // 8 x 15min = 2 hours
    }

    if (filtered.isEmpty()) return

    val maxPrecip = filtered.maxOfOrNull { it.precipitation }?.coerceAtLeast(0.5) ?: return
    val hasRain = filtered.any { it.precipitation > 0.01 }

    // Generate summary text
    val summary = remember(filtered) {
        val firstRainIdx = filtered.indexOfFirst { it.precipitation > 0.05 }
        val lastRainIdx = filtered.indexOfLast { it.precipitation > 0.05 }
        when {
            firstRainIdx < 0 -> NowcastSummary(R.string.nowcast_summary_none)
            firstRainIdx == 0 && lastRainIdx >= filtered.size - 2 -> NowcastSummary(R.string.nowcast_summary_continuing)
            firstRainIdx == 0 -> NowcastSummary(R.string.nowcast_summary_ending, (lastRainIdx + 1) * 15)
            else -> NowcastSummary(R.string.nowcast_summary_starting, firstRainIdx * 15)
        }
    }
    val summaryText = if (summary.minutes != null) {
        stringResource(summary.labelRes, summary.minutes)
    } else {
        stringResource(summary.labelRes)
    }

    val settings = LocalUnitSettings.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)
    val peak = filtered.maxByOrNull { it.precipitation }
    val peakTimeRaw = if (peak != null) WeatherFormatter.formatRelativeHourLabel(peak.time, referenceTime, settings) else null
    val peakTimeLabel = if (peakTimeRaw == "Now") stringResource(R.string.common_now) else peakTimeRaw
    val semanticSummary = if (peak != null && peak.precipitation > 0.0) {
        stringResource(
            R.string.nowcast_semantics_with_peak,
            summaryText,
            WeatherFormatter.formatPrecipitation(peak.precipitation, settings),
            peakTimeLabel ?: "",
        )
    } else {
        stringResource(R.string.nowcast_semantics, summaryText)
    }

    WeatherCard(
        titleRes = R.string.card_type_nowcast,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasRain) NimbusRainBlue else NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        ) {
            val w = size.width
            val h = size.height
            val barCount = filtered.size
            val barGap = 4f
            val barWidth = (w - barGap * (barCount - 1)) / barCount

            filtered.forEachIndexed { i, entry ->
                val barHeight = (entry.precipitation / maxPrecip * (h - 16f)).toFloat()
                    .coerceAtLeast(2f)
                val x = i * (barWidth + barGap)
                val y = h - 16f - barHeight

                val barColor = when {
                    entry.precipitation > 2.0 -> NimbusRainBlue
                    entry.precipitation > 0.5 -> NimbusBlueAccent.copy(alpha = 0.8f)
                    entry.precipitation > 0.05 -> NimbusBlueAccent.copy(alpha = 0.5f)
                    else -> NimbusTextTertiary.copy(alpha = 0.15f)
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                )

                // Time labels every other bar. Minutes-aware: these are 15-minute
                // buckets, so the hour-only label rendered duplicates ("4 PM, 4 PM").
                if (i % 2 == 0) {
                    val label = WeatherFormatter.formatClockTime(entry.time, settings)
                    val m = textMeasurer.measure(label, labelStyle)
                    drawText(m, topLeft = Offset(x, h - 14f))
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.width(12.dp).height(8.dp)) {
                drawRect(NimbusBlueAccent.copy(alpha = 0.5f), size = Size(size.width, size.height))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.nowcast_legend_light),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Canvas(modifier = Modifier.width(12.dp).height(8.dp)) {
                drawRect(NimbusRainBlue, size = Size(size.width, size.height))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.nowcast_legend_heavy),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

private data class NowcastSummary(
    val labelRes: Int,
    val minutes: Int? = null,
)
