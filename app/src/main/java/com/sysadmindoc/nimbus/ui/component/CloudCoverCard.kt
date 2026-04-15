package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusFogGray
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Cloud cover trend card showing hourly cloud coverage as vertical bars
 * with reference threshold lines for "Clear" and "Partly Cloudy".
 * Inspired by breezy-weather's cloud cover trend visualization.
 */
@Composable
fun CloudCoverCard(
    hourly: List<HourlyConditions>,
    referenceTime: LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val currentCloud = hourly.firstOrNull()?.cloudCover ?: return

    val next24 = remember(hourly) {
        hourly.filter { it.cloudCover != null }.take(24)
    }
    if (next24.size < 4) return

    val cloudLabel = when {
        currentCloud <= 10 -> "Clear Sky"
        currentCloud <= 25 -> "Mostly Clear"
        currentCloud <= 50 -> "Partly Cloudy"
        currentCloud <= 75 -> "Mostly Cloudy"
        else -> "Overcast"
    }

    val cloudColor = when {
        currentCloud <= 25 -> NimbusBlueAccent
        currentCloud <= 50 -> NimbusFogGray
        currentCloud <= 75 -> NimbusTextSecondary
        else -> NimbusTextTertiary
    }

    WeatherCard(modifier = modifier, title = "Cloud Cover") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "$currentCloud%",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = cloudColor,
                )
                Text(
                    text = cloudLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CloudCoverChart(
            hours = next24,
            referenceTime = referenceTime,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
    }
}

@Composable
private fun CloudCoverChart(
    hours: List<HourlyConditions>,
    referenceTime: LocalDateTime?,
    modifier: Modifier = Modifier,
) {
    val settings = LocalUnitSettings.current
    val timePattern = if (settings.timeFormat == com.sysadmindoc.nimbus.data.repository.TimeFormat.TWENTY_FOUR_HOUR) "HH" else "ha"
    val timeFmt = DateTimeFormatter.ofPattern(timePattern)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bottomPad = 18f
        val chartH = h - bottomPad
        val barCount = hours.size
        val barSpacing = 3f
        val barWidth = (w - (barCount - 1) * barSpacing) / barCount

        // Reference lines
        val clearY = chartH * (1f - 25f / 100f)
        val partlyY = chartH * (1f - 50f / 100f)

        drawLine(
            color = NimbusBlueAccent.copy(alpha = 0.25f),
            start = Offset(0f, clearY),
            end = Offset(w, clearY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )
        drawLine(
            color = NimbusFogGray.copy(alpha = 0.25f),
            start = Offset(0f, partlyY),
            end = Offset(w, partlyY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )

        // Bars
        hours.forEachIndexed { i, hour ->
            val cloud = (hour.cloudCover ?: 0).coerceIn(0, 100)
            val barH = chartH * (cloud.toFloat() / 100f)
            val x = i * (barWidth + barSpacing)

            val barColor = when {
                cloud <= 25 -> NimbusBlueAccent.copy(alpha = 0.7f)
                cloud <= 50 -> NimbusFogGray.copy(alpha = 0.6f)
                cloud <= 75 -> NimbusTextSecondary.copy(alpha = 0.5f)
                else -> NimbusTextTertiary.copy(alpha = 0.55f)
            }

            val isCurrent = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime)
            val finalColor = if (isCurrent) NimbusBlueAccent else barColor

            drawRoundRect(
                color = finalColor,
                topLeft = Offset(x, chartH - barH),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(3f, 3f),
            )

            // Time labels every 3 hours
            if (i % 3 == 0) {
                val label = hour.time.format(timeFmt).lowercase()
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        x + barWidth / 2 - measured.size.width / 2,
                        chartH + 4f,
                    ),
                )
            }
        }
    }
}
