package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.data.model.PollenReading
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.labelRes

@Composable
fun PollenCard(
    pollen: PollenData,
    modifier: Modifier = Modifier,
) {
    val readings = listOf(
        PollenRowText(
            label = stringResource(R.string.pollen_alder),
            levelLabel = stringResource(pollen.alder.level.labelRes),
            reading = pollen.alder,
        ),
        PollenRowText(
            label = stringResource(R.string.pollen_birch),
            levelLabel = stringResource(pollen.birch.level.labelRes),
            reading = pollen.birch,
        ),
        PollenRowText(
            label = stringResource(R.string.pollen_grass),
            levelLabel = stringResource(pollen.grass.level.labelRes),
            reading = pollen.grass,
        ),
        PollenRowText(
            label = stringResource(R.string.pollen_mugwort),
            levelLabel = stringResource(pollen.mugwort.level.labelRes),
            reading = pollen.mugwort,
        ),
        PollenRowText(
            label = stringResource(R.string.pollen_olive),
            levelLabel = stringResource(pollen.olive.level.labelRes),
            reading = pollen.olive,
        ),
        PollenRowText(
            label = stringResource(R.string.pollen_ragweed),
            levelLabel = stringResource(pollen.ragweed.level.labelRes),
            reading = pollen.ragweed,
        ),
        PollenRowText(
            label = stringResource(R.string.pollen_mold),
            levelLabel = stringResource(pollen.moldSpores.level.labelRes),
            reading = pollen.moldSpores,
        ),
    ).filter { it.reading.level != PollenLevel.NONE }
    val overallLabel = stringResource(pollen.overallLevel.labelRes)
    val semanticSummary = if (!pollen.hasData) {
        stringResource(R.string.pollen_semantics_unavailable)
    } else {
        val readingSummary = readings.joinToString(separator = ", ") { row ->
            "${row.label} ${row.levelLabel}"
        }
        stringResource(R.string.pollen_semantics, overallLabel, readingSummary)
    }

    WeatherCard(
        titleRes = R.string.card_type_pollen,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        if (!pollen.hasData) {
            Text(
                stringResource(R.string.pollen_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextTertiary,
            )
            return@WeatherCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pollen_overall),
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )
            Text(
                overallLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = pollen.overallLevel.color,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        readings.forEach { row ->
            PollenRow(
                label = row.label,
                levelLabel = row.levelLabel,
                reading = row.reading,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PollenRow(label: String, levelLabel: String, reading: PollenReading) {
    val barFraction = when (reading.level) {
        PollenLevel.NONE -> 0f
        PollenLevel.LOW -> 0.25f
        PollenLevel.MODERATE -> 0.5f
        PollenLevel.HIGH -> 0.75f
        PollenLevel.VERY_HIGH -> 1.0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = barFraction,
        animationSpec = tween(600),
        label = "pollenBar",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.width(64.dp),
        )

        // Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NimbusTextTertiary.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(reading.level.color),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Level label
        Text(
            levelLabel,
            style = MaterialTheme.typography.labelSmall,
            color = reading.level.color,
            modifier = Modifier.width(56.dp),
        )
    }
}

private data class PollenRowText(
    val label: String,
    val levelLabel: String,
    val reading: PollenReading,
)
