package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
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
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val data = remember(hourly) { hourly.take(24) }
    if (data.isEmpty()) return

    val maxProb = data.maxOf { it.precipitationProbability }
    val totalPrecip = data.sumOf { it.precipitation ?: 0.0 }

    // Find peak rain window
    val peakIdx = data.indices.maxByOrNull { data[it].precipitationProbability } ?: 0
    val peakHour = data.getOrNull(peakIdx)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)

    WeatherCard(title = "Precipitation Forecast", modifier = modifier) {
        // Summary row
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (maxProb == 0) "No rain expected" else "Up to $maxProb% chance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (maxProb > 0) NimbusRainBlue else NimbusTextSecondary,
                )
                if (totalPrecip > 0) {
                    Text(
                        text = "Total: ${WeatherFormatter.formatPrecipitation(totalPrecip, s)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                }
            }
            if (peakHour != null && peakHour.precipitationProbability > 0) {
                Column {
                    Text(
                        text = "Peak at ${WeatherFormatter.formatHourLabel(peakHour.time, s)}",
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
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(top = 4.dp),
            ) {
                val w = size.width
                val h = size.height
                val paddingBottom = 20f
                val graphHeight = h - paddingBottom
                val barSpacing = 2f
                val barWidth = (w / data.size) - barSpacing

                data.forEachIndexed { i, hour ->
                    val x = i * (barWidth + barSpacing)
                    val prob = hour.precipitationProbability
                    val precip = hour.precipitation ?: 0.0

                    // Probability bar
                    if (prob > 0) {
                        val barH = (prob / 100f) * graphHeight * 0.85f
                        val barColor = when {
                            prob >= 70 -> NimbusRainBlue
                            prob >= 40 -> NimbusRainBlue.copy(alpha = 0.7f)
                            prob >= 20 -> NimbusRainBlue.copy(alpha = 0.45f)
                            else -> NimbusRainBlue.copy(alpha = 0.25f)
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, graphHeight - barH),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(2f),
                        )

                        // Precipitation amount overlay (darker tip)
                        if (precip > 0.1) {
                            val precipH = (precip / 5.0).coerceAtMost(1.0).toFloat() * barH * 0.4f
                            drawRoundRect(
                                color = NimbusBlueAccent.copy(alpha = 0.5f),
                                topLeft = Offset(x, graphHeight - precipH),
                                size = Size(barWidth, precipH),
                                cornerRadius = CornerRadius(2f),
                            )
                        }
                    }

                    // Time labels every 6 hours
                    if (i % 6 == 0) {
                        val label = WeatherFormatter.formatHourLabel(hour.time, s)
                        val measured = textMeasurer.measure(label, labelStyle)
                        drawText(
                            measured,
                            topLeft = Offset(
                                (x + barWidth / 2 - measured.size.width / 2).coerceIn(0f, w - measured.size.width),
                                graphHeight + 4f,
                            ),
                        )
                    }
                }
            }
        }
    }
}
