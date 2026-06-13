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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Humidity card with comfort level indicator, dew point, and a visual gauge.
 */
@Composable
fun HumidityCard(
    humidity: Int,
    dewPoint: Double?,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val comfort = humidityComfort(humidity)
    val comfortLabel = stringResource(comfort.labelRes)
    val comfortDescription = stringResource(comfort.descriptionRes)
    val dewPointValue = dewPoint?.let { WeatherFormatter.formatTemperature(it, s) }
    val dewComfort = dewPoint?.let { stringResource(dewPointComfortRes(it)) }
    val semanticSummary = if (dewPointValue != null && dewComfort != null) {
        stringResource(R.string.humidity_semantics_with_dew, humidity, comfortLabel, dewPointValue, dewComfort)
    } else {
        stringResource(R.string.humidity_semantics, humidity, comfortLabel)
    }

    WeatherCard(
        titleRes = R.string.humidity,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Percentage + comfort label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$humidity%",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = comfortLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = comfort.color,
                )
                if (dewPointValue != null && dewComfort != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.humidity_dew_point_value, dewPointValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                    Text(
                        text = dewComfort,
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Visual gauge arc
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HumidityGauge(
                    humidity = humidity,
                    color = comfort.color,
                    modifier = Modifier.height(80.dp).width(120.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comfortDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }
    }
}

@Composable
private fun HumidityGauge(
    humidity: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = Color.White.copy(alpha = 0.08f)
    // Defensive clamp so an upstream out-of-range value (>100 or <0) doesn't
    // paint the gauge past the track edge.
    val fraction = (humidity / 100f).coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val barHeight = 10.dp.toPx()
        val y = size.height / 2f - barHeight / 2f
        val cornerRadius = CornerRadius(barHeight / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, y),
            size = Size(size.width, barHeight),
            cornerRadius = cornerRadius,
        )

        val fillWidth = size.width * fraction
        if (fillWidth > 0) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, y),
                size = Size(fillWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }

        listOf(0.3f, 0.6f).forEach { mark ->
            val x = size.width * mark
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(x, y - 4.dp.toPx()),
                end = Offset(x, y + barHeight + 4.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

private data class HumidityComfortLevel(
    val labelRes: Int,
    val descriptionRes: Int,
    val color: Color,
)

private fun humidityComfort(humidity: Int): HumidityComfortLevel = when {
    humidity < 25 -> HumidityComfortLevel(R.string.humidity_level_very_dry, R.string.humidity_desc_very_dry, Color(0xFFFFB74D))
    humidity < 30 -> HumidityComfortLevel(R.string.humidity_level_dry, R.string.humidity_desc_dry, Color(0xFFFFCC80))
    humidity in 30..60 -> HumidityComfortLevel(R.string.humidity_level_comfortable, R.string.humidity_desc_comfortable, Color(0xFF81C784))
    humidity in 61..70 -> HumidityComfortLevel(R.string.humidity_level_slightly_humid, R.string.humidity_desc_slightly_humid, Color(0xFF4FC3F7))
    humidity in 71..85 -> HumidityComfortLevel(R.string.humidity_level_humid, R.string.humidity_desc_humid, Color(0xFF42A5F5))
    else -> HumidityComfortLevel(R.string.humidity_level_very_humid, R.string.humidity_desc_very_humid, Color(0xFFEF5350))
}

private fun dewPointComfortRes(dewPointCelsius: Double): Int = when {
    dewPointCelsius < 10 -> R.string.dew_point_comfort_dry
    dewPointCelsius < 16 -> R.string.dew_point_comfort_comfortable
    dewPointCelsius < 18 -> R.string.dew_point_comfort_pleasant
    dewPointCelsius < 21 -> R.string.dew_point_comfort_slightly_humid
    dewPointCelsius < 24 -> R.string.dew_point_comfort_muggy
    else -> R.string.dew_point_comfort_oppressive
}
