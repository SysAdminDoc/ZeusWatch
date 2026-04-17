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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
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

    WeatherCard(title = "Humidity", modifier = modifier) {
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
                    text = comfort.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = comfort.color,
                )
                if (dewPoint != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Dew point ${WeatherFormatter.formatTemperature(dewPoint, s)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                    val dewComfort = WeatherFormatter.dewPointComfort(dewPoint)
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
                    text = comfort.description,
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
        val barHeight = 10f
        val y = size.height / 2f - barHeight / 2f
        val cornerRadius = CornerRadius(barHeight / 2f)

        // Track background
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, y),
            size = Size(size.width, barHeight),
            cornerRadius = cornerRadius,
        )

        // Filled portion
        val fillWidth = size.width * fraction
        if (fillWidth > 0) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, y),
                size = Size(fillWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }

        // Zone markers at 30%, 60%
        listOf(0.3f, 0.6f).forEach { mark ->
            val x = size.width * mark
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(x, y - 4f),
                end = Offset(x, y + barHeight + 4f),
                strokeWidth = 1f,
            )
        }
    }
}

private data class HumidityComfortLevel(
    val label: String,
    val description: String,
    val color: Color,
)

private fun humidityComfort(humidity: Int): HumidityComfortLevel = when {
    humidity < 25 -> HumidityComfortLevel("Very Dry", "May cause dry skin and irritation", Color(0xFFFFB74D))
    humidity < 30 -> HumidityComfortLevel("Dry", "Low moisture in the air", Color(0xFFFFCC80))
    humidity in 30..60 -> HumidityComfortLevel("Comfortable", "Ideal humidity range", Color(0xFF81C784))
    humidity in 61..70 -> HumidityComfortLevel("Slightly Humid", "Noticeable moisture", Color(0xFF4FC3F7))
    humidity in 71..85 -> HumidityComfortLevel("Humid", "Feels sticky and muggy", Color(0xFF42A5F5))
    else -> HumidityComfortLevel("Very Humid", "Oppressive moisture level", Color(0xFFEF5350))
}
