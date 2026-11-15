package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.ClimateOutlookData
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.ScaleColors
import com.sysadmindoc.nimbus.ui.theme.textTint
import java.util.Locale

@Composable
fun ClimateOutlookCard(
    data: ClimateOutlookData,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val deltaSign = if (data.highDelta >= 0) "+" else ""
    val desc = stringResource(
        R.string.climate_semantics,
        formatOneDecimal(data.projectedAvgHigh, locale),
        formatOneDecimal(data.baselineAvgHigh, locale),
        formatSignedOneDecimal(data.highDelta, locale),
    )

    WeatherCard(
        titleRes = R.string.card_title_climate_outlook,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        Text(
            text = stringResource(R.string.climate_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.climate_projected_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.climate_temp_value, formatOneDecimal(data.projectedAvgHigh, locale)),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextSecondary,
                )
            }
            DeltaBadge(delta = data.highDelta, locale = locale)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.climate_baseline_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.climate_temp_value, formatOneDecimal(data.baselineAvgHigh, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NimbusTextTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.climate_low_delta_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.climate_delta_value, formatSignedOneDecimal(data.lowDelta, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textTint(deltaColor(data.lowDelta)),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.climate_precip_change_label),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            val precipPct = if (data.baselineAvgPrecip > 0) {
                (data.precipDelta / data.baselineAvgPrecip) * 100
            } else 0.0
            val precipSign = if (precipPct >= 0) "+" else ""
            Text(
                text = String.format(locale, "%s%.0f%%", precipSign, precipPct),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun DeltaBadge(delta: Double, locale: Locale) {
    val color = deltaColor(delta)
    val wash = color.copy(alpha = 0.12f)
    Text(
        text = "${formatSignedOneDecimal(delta, locale)}°",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = textTint(color, wash),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(wash)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun deltaColor(delta: Double): Color = when {
    delta > 2.0 -> ScaleColors.Red500
    delta > 1.0 -> ScaleColors.Orange500
    delta > 0.0 -> ScaleColors.Amber500
    delta < -1.0 -> ScaleColors.Blue500
    delta < 0.0 -> ScaleColors.Blue300
    else -> ScaleColors.Grey500
}

private fun formatOneDecimal(value: Double, locale: Locale): String =
    String.format(locale, "%.1f", value)

private fun formatSignedOneDecimal(value: Double, locale: Locale): String =
    String.format(locale, "%s%.1f", if (value >= 0) "+" else "", value)
