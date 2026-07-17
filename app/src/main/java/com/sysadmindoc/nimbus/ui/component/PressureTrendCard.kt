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
    val context = LocalContext.current
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

    val firstPressure = data.first().second
    val lastPressure = data.last().second
    val delta24h = lastPressure - firstPressure
    // Announce the same formatted, unit-aware delta the visible badge shows.
    val semanticSummary = stringResource(
        R.string.pressure_trend_semantics,
        WeatherFormatter.formatPressure(currentPressure, s),
        trendSemanticLabel,
        WeatherFormatter.formatPressureDelta(delta24h, s),
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
                    // Honor the user's pressure unit like the headline value does.
                    text = WeatherFormatter.formatPressureDelta(delta, s),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = trendColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val pressures = remember(data) { data.map { it.second } }
        val labels = remember(data, referenceTime, s) {
            trendLabelIndices(data.size)
                .map { WeatherFormatter.formatRelativeHourLabel(context, data[it].first, referenceTime, s) }
        }
        VicoLineTrendChart(
            values = pressures,
            labels = labels,
            lineColor = NimbusBlueAccent,
        )
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
