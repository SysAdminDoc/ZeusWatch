package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import androidx.compose.ui.graphics.Brush

@Composable
fun WeatherSummaryCard(
    summary: String,
    modifier: Modifier = Modifier,
) {
    val leadSentence = remember(summary) {
        summary.split(Regex("(?<=[.!?])\\s+"), limit = 2)
    }
    val headline = leadSentence.firstOrNull().orEmpty()
    val supporting = leadSentence.getOrNull(1)

    WeatherCard(
        modifier = modifier,
        titleRes = R.string.card_title_forecast_brief,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BriefChip(stringResource(R.string.summary_chip_live_conditions))
            BriefChip(stringResource(R.string.summary_chip_next_swing))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = headline.ifBlank { summary },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = NimbusTextPrimary,
        )
        supporting?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.summary_footer),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextTertiary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun BriefChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NimbusBlueAccent,
        modifier = Modifier
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.52f),
                        NimbusGlassBottom.copy(alpha = 0.92f),
                    ),
                ),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
