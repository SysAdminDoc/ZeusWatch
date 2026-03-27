package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Outdoor activity suitability score card.
 * Shows a circular gauge 0-100 with color-coded rating.
 */
@Composable
fun OutdoorScoreCard(
    score: Int,
    modifier: Modifier = Modifier,
    tempCelsius: Double = 20.0,
    humidity: Int = 50,
    windKmh: Double = 10.0,
    uvIndex: Double = 3.0,
    precipProbability: Int = 0,
) {
    val label = WeatherFormatter.outdoorScoreLabel(score)
    val scoreColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFF8BC34A)
        score >= 40 -> Color(0xFFFF9800)
        score >= 20 -> Color(0xFFFF5722)
        else -> Color(0xFFF44336)
    }

    WeatherCard(
        title = "Outdoor Activity",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Score gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp),
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 8f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Background arc
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    // Score arc
                    drawArc(
                        color = scoreColor,
                        startAngle = 135f,
                        sweepAngle = 270f * (score / 100f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = scoreColor,
                )
            }

            Column(
                modifier = Modifier.padding(start = 16.dp).weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = scoreColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        score >= 80 -> "Great conditions for outdoor activities"
                        score >= 60 -> "Good conditions for most activities"
                        score >= 40 -> "Consider indoor alternatives"
                        score >= 20 -> "Outdoor activities not recommended"
                        else -> "Stay indoors if possible"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }

        // Factor breakdown
        Spacer(modifier = Modifier.height(12.dp))
        val factors = listOf(
            "Temp" to factorScore(tempCelsius, 15.0, 25.0, 5.0, 35.0),
            "Wind" to (100 - (windKmh / 50.0 * 100).toInt()).coerceIn(0, 100),
            "Rain" to (100 - precipProbability).coerceIn(0, 100),
            "UV" to (100 - (uvIndex / 11.0 * 100).toInt()).coerceIn(0, 100),
            "Humidity" to factorScore(humidity.toDouble(), 30.0, 60.0, 10.0, 85.0),
        )
        factors.forEach { (name, value) ->
            FactorBar(name = name, value = value)
        }
    }
}

private fun factorScore(value: Double, idealLow: Double, idealHigh: Double, okLow: Double, okHigh: Double): Int {
    return when {
        value in idealLow..idealHigh -> 100
        value in okLow..okHigh -> 60
        else -> 20
    }
}

@Composable
private fun FactorBar(name: String, value: Int) {
    val barColor = when {
        value >= 80 -> Color(0xFF4CAF50)
        value >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
            modifier = Modifier.width(52.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}
