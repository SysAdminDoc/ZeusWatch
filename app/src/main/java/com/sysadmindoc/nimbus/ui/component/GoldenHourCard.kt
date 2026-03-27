package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning

/**
 * Golden Hour / Blue Hour card for photographers.
 * Shows the best times for warm/cool natural lighting.
 */
@Composable
fun GoldenHourCard(
    morningGoldenEnd: String,
    eveningGoldenStart: String,
    sunrise: String,
    sunset: String,
    modifier: Modifier = Modifier,
) {
    val goldenColor = NimbusWarning
    val blueColor = NimbusRainBlue

    WeatherCard(
        title = "Golden Hour",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Morning golden hour
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = goldenColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Morning",
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusTextSecondary,
                )
                Text(
                    "$sunrise \u2013 $morningGoldenEnd",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = goldenColor,
                )
            }

            // Evening golden hour
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.WbTwilight,
                    contentDescription = null,
                    tint = goldenColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Evening",
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusTextSecondary,
                )
                Text(
                    "$eveningGoldenStart \u2013 $sunset",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = goldenColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = NimbusTextTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Best natural lighting for photography",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}
