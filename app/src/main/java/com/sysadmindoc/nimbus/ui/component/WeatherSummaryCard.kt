package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary

@Composable
fun WeatherSummaryCard(
    summary: String,
    modifier: Modifier = Modifier,
) {
    WeatherCard(
        modifier = modifier,
        title = "Forecast Brief",
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = NimbusTextPrimary,
        )
        Text(
            text = "Updated narrative generated from live conditions and daily outlook.",
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
