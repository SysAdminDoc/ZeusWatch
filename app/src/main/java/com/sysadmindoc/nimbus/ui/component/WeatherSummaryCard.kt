package com.sysadmindoc.nimbus.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary

/**
 * Natural language weather summary card.
 * Shows a 1-2 sentence human-readable forecast summary.
 */
@Composable
fun WeatherSummaryCard(
    summary: String,
    modifier: Modifier = Modifier,
) {
    WeatherCard(modifier = modifier) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = NimbusTextPrimary,
        )
    }
}
