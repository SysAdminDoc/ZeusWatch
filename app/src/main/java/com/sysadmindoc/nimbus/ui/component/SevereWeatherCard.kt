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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    val description = WeatherFormatter.capeDescription(cape)
    val color = when {
        cape >= 4000 -> Color(0xFFD32F2F) // Extremely Unstable
        cape >= 2500 -> Color(0xFFFF5722) // Very Unstable
        cape >= 1000 -> Color(0xFFFF9800) // Moderately Unstable
        cape >= 300 -> Color(0xFFFFEB3B)  // Marginally Unstable
        else -> Color(0xFF9E9E9E)
    }

    WeatherCard(
        title = "Severe Weather Potential",
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
                contentDescription = "Severe weather potential",
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
                        "Thunderstorms may produce strong winds, hail, or tornadoes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextPrimary,
                    )
                }
            }
        }
    }
}
