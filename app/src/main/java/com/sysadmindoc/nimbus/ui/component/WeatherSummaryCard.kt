package com.sysadmindoc.nimbus.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary

@Composable
fun WeatherSummaryCard(
    summary: String,
    modifier: Modifier = Modifier,
) {
    val compactSummary = remember(summary) {
        summary
            .split(Regex("(?<=[.!?])\\s+"), limit = 3)
            .take(2)
            .joinToString(" ")
    }

    WeatherCard(
        modifier = modifier,
        titleRes = R.string.card_title_forecast_brief,
    ) {
        Text(
            text = compactSummary.ifBlank { summary },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = NimbusTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
