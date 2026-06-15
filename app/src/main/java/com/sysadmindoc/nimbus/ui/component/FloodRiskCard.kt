package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.FloodData
import com.sysadmindoc.nimbus.data.repository.FloodRiskLevel
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import java.util.Locale

@Composable
fun FloodRiskCard(
    data: FloodData,
    modifier: Modifier = Modifier,
) {
    val riskColor = floodRiskColor(data.riskLevel)
    val riskLabel = stringResource(data.riskLevel.labelRes)
    val desc = stringResource(
        R.string.flood_semantics,
        riskLabel,
        formatWholeNumber(data.currentDischarge),
        formatWholeNumber(data.meanDischarge),
    )

    WeatherCard(
        titleRes = R.string.card_title_flood_risk,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = riskLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = riskColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.flood_discharge_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.flood_m3s_value, formatWholeNumber(data.currentDischarge)),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextSecondary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.flood_mean_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.flood_m3s_value, formatWholeNumber(data.meanDischarge)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextTertiary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.flood_max_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.flood_m3s_value, formatWholeNumber(data.maxDischarge)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextTertiary,
                )
            }
        }
    }
}

private fun floodRiskColor(level: FloodRiskLevel): Color = when (level) {
    FloodRiskLevel.LOW -> Color(0xFF4CAF50)
    FloodRiskLevel.MODERATE -> Color(0xFFFF9800)
    FloodRiskLevel.HIGH -> Color(0xFFFF5722)
    FloodRiskLevel.EXTREME -> Color(0xFFF44336)
}

private val FloodRiskLevel.labelRes: Int
    get() = when (this) {
        FloodRiskLevel.LOW -> R.string.flood_risk_low
        FloodRiskLevel.MODERATE -> R.string.flood_risk_moderate
        FloodRiskLevel.HIGH -> R.string.flood_risk_high
        FloodRiskLevel.EXTREME -> R.string.flood_risk_extreme
    }

private fun formatWholeNumber(value: Double): String =
    String.format(Locale.getDefault(), "%.0f", value)
