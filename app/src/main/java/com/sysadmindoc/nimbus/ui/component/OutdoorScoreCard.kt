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
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun OutdoorScoreCard(
    breakdown: WeatherFormatter.OutdoorScoreBreakdown,
    modifier: Modifier = Modifier,
) {
    val score = breakdown.score
    val label = stringResource(outdoorScoreLabelRes(score))
    val description = stringResource(outdoorScoreDescriptionRes(score))
    val scoreColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFF8BC34A)
        score >= 40 -> Color(0xFFFF9800)
        score >= 20 -> Color(0xFFFF5722)
        else -> Color(0xFFF44336)
    }
    val factors = listOf(
        stringResource(R.string.outdoor_factor_temp) to breakdown.tempScore,
        stringResource(R.string.outdoor_factor_wind) to breakdown.windScore,
        stringResource(R.string.outdoor_factor_rain) to breakdown.precipScore,
        stringResource(R.string.outdoor_factor_uv) to breakdown.uvScore,
        stringResource(R.string.outdoor_factor_humidity) to breakdown.humidityScore,
        stringResource(R.string.outdoor_factor_aqi) to breakdown.aqiScore,
    )
    val semanticSummary = stringResource(
        R.string.outdoor_score_semantics,
        score,
        label,
        breakdown.tempScore,
        breakdown.windScore,
        breakdown.precipScore,
        breakdown.uvScore,
        breakdown.humidityScore,
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp),
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 8f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
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
