package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.sysadmindoc.nimbus.ui.theme.NimbusFogGray
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusUvHigh
import com.sysadmindoc.nimbus.ui.theme.NimbusUvModerate
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.format.DateTimeFormatter

/**
 * Visibility card with a 6-tier graduated scale and hourly trend chart.
 * Inspired by breezy-weather's visibility block.
 *
 * Tiers (in km): Very Poor 0-1, Poor 1-4, Moderate 4-10, Good 10-20, Clear 20-40, Perfectly Clear 40+
 */
@Composable
fun VisibilityCard(
    visibilityMeters: Double?,
    hourly: List<HourlyConditions>,
    modifier: Modifier = Modifier,
) {
    if (visibilityMeters == null) return
    val settings = LocalUnitSettings.current

    val visKm = visibilityMeters / 1000.0
    val tier = visibilityTier(visKm)
    val tierLabel = stringResource(tier.labelRes)
    val visHours = remember(hourly) {
        hourly.filter { it.visibility != null }.take(24)
    }
    val semanticSummary = if (visHours.size >= 4) {
        val trendValues = visHours.mapNotNull { it.visibility }
        stringResource(
            R.string.visibility_semantics_range,
            WeatherFormatter.formatVisibility(visibilityMeters, settings),
            tierLabel,
            WeatherFormatter.formatVisibility(trendValues.min(), settings),
            WeatherFormatter.formatVisibility(trendValues.max(), settings),
        )
    } else {
        stringResource(
            R.string.visibility_semantics,
            WeatherFormatter.formatVisibility(visibilityMeters, settings),
            tierLabel,
        )
    }

    WeatherCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
        titleRes = R.string.card_type_visibility,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = WeatherFormatter.formatVisibility(visibilityMeters, settings),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = tier.color,
                )
                Text(
                    text = tierLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Graduated scale bar
        VisibilityScaleBar(
            currentKm = visKm,
            labels = listOf(
                stringResource(R.string.visibility_scale_very_poor_short),
                stringResource(R.string.visibility_tier_poor),
                stringResource(R.string.visibility_scale_moderate_short),
                stringResource(R.string.visibility_tier_good),
                stringResource(R.string.visibility_tier_clear),
                stringResource(R.string.visibility_scale_perfectly_clear_short),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        )

        // Hourly trend (if data available)
        if (visHours.size >= 4) {
            Spacer(modifier = Modifier.height(14.dp))
            VisibilityTrendChart(
                hours = visHours,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )
        }
    }
}

private data class VisibilityTier(
    val labelRes: Int,
    val color: Color,
)

private fun visibilityTier(km: Double): VisibilityTier = when {
    km < 1.0 -> VisibilityTier(R.string.visibility_tier_very_poor, NimbusWarning)
    km < 4.0 -> VisibilityTier(R.string.visibility_tier_poor, NimbusUvHigh)
    km < 10.0 -> VisibilityTier(R.string.visibility_tier_moderate, NimbusUvModerate)
    km < 20.0 -> VisibilityTier(R.string.visibility_tier_good, NimbusSuccess)
    km < 40.0 -> VisibilityTier(R.string.visibility_tier_clear, NimbusBlueAccent)
    else -> VisibilityTier(R.string.visibility_tier_perfectly_clear, Color(0xFF80DEEA))
}

@Composable
private fun VisibilityScaleBar(
    currentKm: Double,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 8.sp)

    // Scale thresholds in km
    val thresholds = listOf(0.0, 1.0, 4.0, 10.0, 20.0, 40.0, 50.0)
    val colors = listOf(
        NimbusWarning,          // 0-1
        NimbusUvHigh,           // 1-4
        NimbusUvModerate,       // 4-10
        NimbusSuccess,          // 10-20
        NimbusBlueAccent,       // 20-40
        Color(0xFF80DEEA),      // 40+
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val barH = 10.dp.toPx()
        val barY = 4.dp.toPx()
        val maxKm = 50f

        // Draw segmented bar
        for (i in 0 until 6) {
            val startFrac = (thresholds[i] / maxKm).toFloat()
            val endFrac = (thresholds[i + 1] / maxKm).toFloat()
            val startX = w * startFrac
            val segW = w * (endFrac - startFrac)

            drawRoundRect(
                color = colors[i].copy(alpha = 0.35f),
                topLeft = Offset(startX, barY),
                size = Size(segW, barH),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )

            // Label below
            val label = labels[i]
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft = Offset(startX + segW / 2 - measured.size.width / 2, barY + barH + 4.dp.toPx()),
            )
        }

        // Current position indicator
        val posFrac = (currentKm.coerceIn(0.0, 50.0) / maxKm).toFloat()
        val posX = w * posFrac
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(posX, barY + barH / 2),
        )
        drawCircle(
            color = visibilityTier(currentKm).color,
            radius = 5.dp.toPx(),
            center = Offset(posX, barY + barH / 2),
        )
    }
}

@Composable
private fun VisibilityTrendChart(
    hours: List<HourlyConditions>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val settings = LocalUnitSettings.current
    val timePattern = if (settings.timeFormat == com.sysadmindoc.nimbus.data.repository.TimeFormat.TWENTY_FOUR_HOUR) "HH" else "ha"
    val timeFmt = DateTimeFormatter.ofPattern(timePattern)
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bottomPad = 16.dp.toPx()
        val chartH = h - bottomPad
        val maxVis = 50000f // 50km in meters

        val points = hours.mapIndexed { i, hr ->
            val x = (i.toFloat() / (hours.size - 1).coerceAtLeast(1)) * w
            val vis = (hr.visibility ?: 0.0).toFloat().coerceIn(0f, maxVis)
            val y = chartH * (1f - vis / maxVis)
            Offset(x, y)
        }

        // Draw line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = NimbusBlueAccent.copy(alpha = 0.6f),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Dots at each point
        points.forEach { pt ->
            drawCircle(
                color = NimbusBlueAccent,
                radius = 3.dp.toPx(),
                center = pt,
            )
        }

        // Time labels
        hours.forEachIndexed { i, hr ->
            if (i % 4 == 0) {
                val label = hr.time.format(timeFmt).lowercase()
                val measured = textMeasurer.measure(label, labelStyle)
                val x = (i.toFloat() / (hours.size - 1).coerceAtLeast(1)) * w
                drawText(
                    measured,
                    topLeft = Offset(x - measured.size.width / 2, chartH + 2.dp.toPx()),
                )
            }
        }
    }
}
