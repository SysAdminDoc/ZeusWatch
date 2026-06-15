package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.MarineData
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import java.util.Locale

@Composable
fun MarineCard(
    data: MarineData,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(
        R.string.marine_semantics,
        formatOneDecimal(data.waveHeight),
        formatOneDecimal(data.wavePeriod),
        data.waveDirection,
    )

    WeatherCard(
        titleRes = R.string.card_title_marine,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MarineMetric(
                label = stringResource(R.string.marine_wave_height),
                value = stringResource(R.string.marine_meters_value, formatOneDecimal(data.waveHeight)),
            )
            MarineMetric(
                label = stringResource(R.string.marine_wave_period),
                value = stringResource(R.string.marine_seconds_value, formatWholeNumber(data.wavePeriod)),
            )
            MarineMetric(
                label = stringResource(R.string.marine_wave_direction),
                value = "${data.waveDirection}°",
            )
            val sst = data.hourlySst.firstOrNull()
            if (sst != null) {
                MarineMetric(
                    label = stringResource(R.string.marine_sst),
                    value = "${formatOneDecimal(sst)}°C",
                )
            }
        }
    }
}

@Composable
private fun MarineMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextSecondary,
        )
    }
}

private fun formatOneDecimal(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)

private fun formatWholeNumber(value: Double): String =
    String.format(Locale.getDefault(), "%.0f", value)
