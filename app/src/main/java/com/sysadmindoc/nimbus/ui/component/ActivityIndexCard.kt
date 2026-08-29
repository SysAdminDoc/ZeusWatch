package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.ActivityFactor
import com.sysadmindoc.nimbus.util.ActivityIndex
import com.sysadmindoc.nimbus.util.ActivityWindow
import com.sysadmindoc.nimbus.util.ActivityWindowConfidence

@Composable
fun ActivityIndexCard(
    indices: List<ActivityIndex>,
    modifier: Modifier = Modifier,
    windows: List<ActivityWindow> = emptyList(),
) {
    if (indices.isEmpty()) return

    val windowByType = remember(windows) { windows.associateBy { it.type } }
    val semanticDesc = indices.joinToString("; ") { "${it.type.name}: ${it.score}" }
    val desc = stringResource(R.string.activity_index_semantics, semanticDesc)

    WeatherCard(
        titleRes = R.string.card_title_activity_index,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        indices.forEach { index ->
            ActivityRow(index, windowByType[index.type])
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ActivityRow(index: ActivityIndex, window: ActivityWindow?) {
    val color = scoreColor(index.score)
    val label = when {
        index.score >= 80 -> stringResource(R.string.activity_rating_great)
        index.score >= 60 -> stringResource(R.string.activity_rating_good)
        index.score >= 40 -> stringResource(R.string.activity_rating_fair)
        else -> stringResource(R.string.activity_rating_poor)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(index.type.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
            modifier = Modifier.width(90.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(index.score / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${index.score}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
            modifier = Modifier.width(36.dp),
        )
    }
        window?.let { ActivityWindowLine(it) }
    }
}

/**
 * The best stretch of the next 24 hours, and what is wrong with it.
 *
 * The score above answers "right now". This is the line people actually plan
 * around, so when there is no good window it says so rather than leaving the
 * current score to imply one.
 */
@Composable
private fun ActivityWindowLine(window: ActivityWindow) {
    val text = if (window.hasWindow) {
        stringResource(
            R.string.activity_window_range,
            formatWindowHour(window.start),
            formatWindowHour(window.end),
        )
    } else {
        stringResource(R.string.activity_window_none)
    }
    val limiting = window.limitingFactors.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 90.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (window.hasWindow) NimbusTextSecondary else NimbusTextTertiary,
        )
        limiting?.let { factor ->
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.activity_window_limited_by, stringResource(factor.labelRes)),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
        if (window.confidence == ActivityWindowConfidence.LOW ||
            window.confidence == ActivityWindowConfidence.NONE
        ) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.activity_window_low_confidence),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun formatWindowHour(time: java.time.LocalDateTime?): String =
    time?.format(java.time.format.DateTimeFormatter.ofPattern("h a")) ?: ""

private val ActivityFactor.labelRes: Int
    get() = when (this) {
        ActivityFactor.TEMPERATURE -> R.string.activity_factor_temperature
        ActivityFactor.RAIN -> R.string.activity_factor_rain
        ActivityFactor.WIND -> R.string.activity_factor_wind
        ActivityFactor.UV -> R.string.activity_factor_uv
        ActivityFactor.HUMIDITY -> R.string.activity_factor_humidity
        ActivityFactor.CLOUD -> R.string.activity_factor_cloud
        ActivityFactor.AIR_QUALITY -> R.string.activity_factor_air_quality
    }

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF4CAF50)
    score >= 60 -> Color(0xFF8BC34A)
    score >= 40 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}
