package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * 24-hour precipitation forecast chart showing probability bars and
 * accumulation amounts. Highlights the peak rain window.
 */
@Composable
fun PrecipitationChartCard(
    hourly: List<HourlyConditions>,
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    val data = remember(hourly) { hourly.take(24) }
    if (data.isEmpty()) return

    val maxProb = data.maxOf { it.precipitationProbability }
    val totalPrecip = data.sumOf { it.precipitation ?: 0.0 }

    // Find peak rain window
    val peakIdx = data.indices.maxByOrNull { data[it].precipitationProbability } ?: 0
    val peakHour = data.getOrNull(peakIdx)

    val peakTimeRaw = if (peakHour != null) {
        WeatherFormatter.formatRelativeHourLabel(context, peakHour.time, referenceTime, s)
    } else null
    val peakTimeLabel = peakTimeRaw
    val semanticBase = when {
        maxProb == 0 -> stringResource(R.string.precip_semantics_no_rain)
        peakTimeLabel != null -> stringResource(R.string.precip_semantics_chance_peak, maxProb, peakTimeLabel)
        else -> stringResource(R.string.precip_semantics_chance, maxProb)
    }
    val totalSemantic = if (totalPrecip > 0.0) {
        stringResource(R.string.precip_semantics_total, WeatherFormatter.formatPrecipitation(totalPrecip, s))
    } else null
    val semanticSummary = listOfNotNull(semanticBase, totalSemantic).joinToString(separator = " ")
    val maxProbCue = ColorSafeRiskCues.precipitation(maxProb)

    WeatherCard(
        titleRes = R.string.card_type_precipitation_chart,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        // Summary row
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (maxProb == 0) {
                        stringResource(R.string.precip_no_rain_expected)
                    } else {
                        stringResource(R.string.precip_up_to_chance, maxProb)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (maxProb > 0) NimbusRainBlue else NimbusTextSecondary,
                )
                if (maxProb > 0) {
                    ColorSafeCueBadge(cue = maxProbCue)
                }
                if (totalPrecip > 0) {
                    Text(
                        text = stringResource(R.string.precip_total, WeatherFormatter.formatPrecipitation(totalPrecip, s)),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                }
            }
            if (peakHour != null && peakHour.precipitationProbability > 0) {
                Column {
                    Text(
                        text = stringResource(R.string.precip_peak_at, peakTimeLabel ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextSecondary,
                    )
                    Text(
                        text = "${peakHour.precipitationProbability}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = NimbusRainBlue,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bar chart
        if (maxProb > 0) {
            val probabilities = remember(data) { data.map { it.precipitationProbability.toDouble() } }
            val labels = remember(data, referenceTime, s) {
                data.indices
                    .filter { it % 6 == 0 }
                    .map { WeatherFormatter.formatRelativeHourLabel(context, data[it].time, referenceTime, s) }
            }
            VicoColumnTrendChart(
                values = probabilities,
                labels = labels,
                columnColor = NimbusRainBlue.copy(alpha = 0.72f),
            )
        }
    }
}
