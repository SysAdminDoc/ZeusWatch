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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.data.model.PollenReading
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

@Composable
fun PollenCard(
    pollen: PollenData,
    modifier: Modifier = Modifier,
) {
    WeatherCard(titleRes = R.string.card_type_pollen, modifier = modifier) {
        if (!pollen.hasData) {
            Text(
                "Pollen readings are low or unavailable for this location right now.",
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
                "Overall",
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )
            Text(
                pollen.overallLevel.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = pollen.overallLevel.color,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Each pollen type row
        val readings = listOf(
            pollen.alder, pollen.birch, pollen.grass,
            pollen.mugwort, pollen.olive, pollen.ragweed,
        ).filter { it.level != PollenLevel.NONE }

        readings.forEach { reading ->
            PollenRow(reading)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PollenRow(reading: PollenReading) {
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
            reading.name,
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
            reading.level.label,
            style = MaterialTheme.typography.labelSmall,
            color = reading.level.color,
            modifier = Modifier.width(56.dp),
        )
    }
}
