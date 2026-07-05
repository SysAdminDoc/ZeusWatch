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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
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
    val context = LocalContext.current
    val data = remember(hourly) { hourly.take(24) }
    if (data.size < 3) return

    val maxWind = data.maxOfOrNull { it.windSpeed ?: 0.0 } ?: return
    val maxGust = data.maxOfOrNull { it.windGusts ?: 0.0 } ?: 0.0
    val peakIdx = data.indices.maxByOrNull { data[it].windSpeed ?: 0.0 } ?: 0
    val peakHour = data[peakIdx]

    val peakTimeLabel = WeatherFormatter.formatRelativeHourLabel(context, peakHour.time, referenceTime, s)
    val semanticSummary = if (maxGust > maxWind * 1.2) {
        stringResource(
            R.string.wind_trend_semantics_gusts,
            WeatherFormatter.formatWindSpeed(maxWind, s),
            peakTimeLabel,
            WeatherFormatter.formatWindSpeed(maxGust, s),
        )
    } else {
        stringResource(
            R.string.wind_trend_semantics,
            WeatherFormatter.formatWindSpeed(maxWind, s),
            peakTimeLabel,
        )
    }

    WeatherCard(
        titleRes = R.string.card_type_wind_trend,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wind_trend_peak, WeatherFormatter.formatWindSpeed(maxWind, s)),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.wind_trend_at, peakTimeLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextSecondary,
                )
            }
            if (maxGust > maxWind * 1.2) {
                Column {
                    Text(
                        text = stringResource(R.string.wind_trend_gusts_to),
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

        val windValues = remember(data) { data.map { it.windSpeed ?: 0.0 } }
        val gustValues = remember(data) {
            data.map { hour ->
                val gust = hour.windGusts ?: 0.0
                val wind = hour.windSpeed ?: 0.0
                if (gust > wind * 1.2 && gust > 1.0) gust else 0.0
            }
        }
        val labels = remember(data, referenceTime, s) {
            data.indices
                .filter { it % 6 == 0 }
                .map { WeatherFormatter.formatRelativeHourLabel(context, data[it].time, referenceTime, s) }
        }
        VicoComboTrendChart(
            lineValues = windValues,
            columnValues = gustValues,
            labels = labels,
            lineColor = NimbusBlueAccent,
            columnColor = Color(0xFFFF9800).copy(alpha = 0.22f),
        )
    }
}
