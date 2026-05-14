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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

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
    val label = stringResource(outdoorScoreLabelRes(score))
    val description = stringResource(outdoorScoreDescriptionRes(score))
    val scoreColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFF8BC34A)
        score >= 40 -> Color(0xFFFF9800)
        score >= 20 -> Color(0xFFFF5722)
        else -> Color(0xFFF44336)
    }
    val tempScore = factorScore(tempCelsius, 15.0, 25.0, 5.0, 35.0)
    val windScore = (100 - (windKmh / 50.0 * 100).toInt()).coerceIn(0, 100)
    val rainScore = (100 - precipProbability).coerceIn(0, 100)
    val uvScore = (100 - (uvIndex / 11.0 * 100).toInt()).coerceIn(0, 100)
    val humidityScore = factorScore(humidity.toDouble(), 30.0, 60.0, 10.0, 85.0)
    val factors = listOf(
        stringResource(R.string.outdoor_factor_temp) to tempScore,
        stringResource(R.string.outdoor_factor_wind) to windScore,
        stringResource(R.string.outdoor_factor_rain) to rainScore,
        stringResource(R.string.outdoor_factor_uv) to uvScore,
        stringResource(R.string.outdoor_factor_humidity) to humidityScore,
    )
    val semanticSummary = stringResource(
        R.string.outdoor_score_semantics,
        score,
        label,
        tempScore,
        windScore,
        rainScore,
        uvScore,
        humidityScore,
    )

    WeatherCard(
        titleRes = R.string.card_title_outdoor_activity,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
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
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }

        // Factor breakdown
        Spacer(modifier = Modifier.height(12.dp))
        factors.forEach { (name, value) ->
            FactorBar(name = name, value = value)
        }
    }
}

private fun outdoorScoreLabelRes(score: Int): Int = when {
    score >= 80 -> R.string.outdoor_score_excellent
    score >= 60 -> R.string.outdoor_score_good
    score >= 40 -> R.string.outdoor_score_fair
    score >= 20 -> R.string.outdoor_score_poor
    else -> R.string.outdoor_score_stay_inside
}

private fun outdoorScoreDescriptionRes(score: Int): Int = when {
    score >= 80 -> R.string.outdoor_desc_great
    score >= 60 -> R.string.outdoor_desc_good
    score >= 40 -> R.string.outdoor_desc_indoor
    score >= 20 -> R.string.outdoor_desc_not_recommended
    else -> R.string.outdoor_desc_stay_inside
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
