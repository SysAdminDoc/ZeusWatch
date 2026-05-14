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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
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
        cape >= 4000 -> Color(0xFFD32F2F) // Extremely Unstable
        cape >= 2500 -> Color(0xFFFF5722) // Very Unstable
        cape >= 1000 -> Color(0xFFFF9800) // Moderately Unstable
        cape >= 300 -> Color(0xFFFFEB3B)  // Marginally Unstable
        else -> Color(0xFF9E9E9E)
    }

    WeatherCard(
        titleRes = R.string.card_type_severe_weather,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.1f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Thunderstorm,
                contentDescription = potentialDescription,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = color,
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
