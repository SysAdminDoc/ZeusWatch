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
import com.sysadmindoc.nimbus.util.ActivityIndex

@Composable
fun ActivityIndexCard(
    indices: List<ActivityIndex>,
    modifier: Modifier = Modifier,
) {
    if (indices.isEmpty()) return

    val semanticDesc = indices.joinToString("; ") { "${it.type.name}: ${it.score}" }
    val desc = stringResource(R.string.activity_index_semantics, semanticDesc)

    WeatherCard(
        titleRes = R.string.card_title_activity_index,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        indices.forEach { index ->
            ActivityRow(index)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ActivityRow(index: ActivityIndex) {
    val color = scoreColor(index.score)
    val label = when {
        index.score >= 80 -> stringResource(R.string.activity_rating_great)
        index.score >= 60 -> stringResource(R.string.activity_rating_good)
        index.score >= 40 -> stringResource(R.string.activity_rating_fair)
        else -> stringResource(R.string.activity_rating_poor)
    }

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
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF4CAF50)
    score >= 60 -> Color(0xFF8BC34A)
    score >= 40 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}
