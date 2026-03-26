package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusSnowWhite
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Snowfall card showing current snowfall rate and snow depth.
 */
@Composable
fun SnowfallCard(
    snowfall: Double?,
    snowDepth: Double?,
    modifier: Modifier = Modifier,
) {
    if ((snowfall ?: 0.0) <= 0 && (snowDepth ?: 0.0) <= 0) return

    val s = LocalUnitSettings.current

    WeatherCard(
        title = "Snowfall",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            snowfall?.let {
                if (it > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AcUnit, null, tint = NimbusSnowWhite, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            WeatherFormatter.formatSnowfall(it, s),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = NimbusSnowWhite,
                        )
                        Text(" /hr", style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
                    }
                }
            }
            snowDepth?.let {
                if (it > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Depth: ", style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
                        Text(
                            WeatherFormatter.formatSnowDepth(it, s),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = NimbusSnowWhite,
                        )
                    }
                }
            }
        }
    }
}
