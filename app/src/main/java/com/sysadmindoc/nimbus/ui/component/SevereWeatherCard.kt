package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.ScaleColors
import com.sysadmindoc.nimbus.ui.theme.markTint
import com.sysadmindoc.nimbus.ui.theme.textTint
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * CAPE (Convective Available Potential Energy) indicator card.
 * Shows severe thunderstorm potential based on atmospheric instability.
 */
@Composable
fun SevereWeatherCard(
    cape: Double,
    modifier: Modifier = Modifier,
) {
    if (cape < 100) return // Don't show when atmosphere is stable

    val description = stringResource(capeDescriptionRes(cape))
    val potentialDescription = stringResource(R.string.severe_weather_potential_cd)
    val thunderstormWarning = stringResource(R.string.severe_weather_thunderstorm_warning)
    val color = when {
        cape >= 4000 -> ScaleColors.Red700 // Extremely Unstable
        cape >= 2500 -> ScaleColors.DeepOrange500 // Very Unstable
        cape >= 1000 -> ScaleColors.Orange500 // Moderately Unstable
        cape >= 300 -> ScaleColors.Yellow500 // Marginally Unstable
        else -> ScaleColors.Grey500
    }
    val wash = color.copy(alpha = 0.1f)

    WeatherCard(
        titleRes = R.string.card_type_severe_weather,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(wash)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Thunderstorm,
                contentDescription = potentialDescription,
                tint = markTint(color, wash),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = textTint(color, wash),
                )
                Text(
                    WeatherFormatter.formatCape(cape),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
                if (cape >= 1000) {
                    Text(
                        thunderstormWarning,
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextPrimary,
                    )
                }
            }
        }
    }
}

private fun capeDescriptionRes(cape: Double): Int = when {
    cape < 300 -> R.string.severe_cape_stable
    cape < 1000 -> R.string.severe_cape_marginally_unstable
    cape < 2500 -> R.string.severe_cape_moderately_unstable
    cape < 4000 -> R.string.severe_cape_very_unstable
    else -> R.string.severe_cape_extremely_unstable
}
