package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.ForecastEvolutionData
import com.sysadmindoc.nimbus.data.repository.ForecastEvolutionPoint
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val RUN_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ForecastEvolutionCard(
    data: ForecastEvolutionData,
    modifier: Modifier = Modifier,
) {
    val settings = LocalUnitSettings.current
    val avgTempDelta = formatTemperatureDelta(data.averageTemperatureDeltaC, settings.tempUnit)
    val avgRainDelta = data.averagePrecipitationProbabilityDelta.roundToInt()
    val avgRainDeltaText = stringResource(R.string.forecast_evolution_rain_delta, avgRainDelta)
    val semantics = stringResource(
        R.string.forecast_evolution_semantics,
        avgTempDelta,
        avgRainDeltaText,
    )

    WeatherCard(
        titleRes = R.string.card_type_forecast_evolution,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semantics
        },
    ) {
        Text(
            text = stringResource(R.string.forecast_evolution_model, data.modelName),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )
        Text(
            text = stringResource(
                R.string.forecast_evolution_runs,
                data.latestRun.format(RUN_TIME_FORMATTER),
                data.previousRun.format(RUN_TIME_FORMATTER),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ForecastEvolutionMetric(
                label = stringResource(R.string.forecast_evolution_temp_shift),
                value = avgTempDelta,
                modifier = Modifier.weight(1f),
                emphasized = kotlin.math.abs(data.averageTemperatureDeltaC) >= 1.0,
            )
            ForecastEvolutionMetric(
                label = stringResource(R.string.forecast_evolution_rain_shift),
                value = avgRainDeltaText,
                modifier = Modifier.weight(1f),
                emphasized = kotlin.math.abs(avgRainDelta) >= 10,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.points.forEach { point ->
                ForecastEvolutionPointRow(point = point)
            }
        }
    }
}

@Composable
private fun ForecastEvolutionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (emphasized) NimbusWarning else NimbusBlueAccent,
        )
    }
}

@Composable
private fun ForecastEvolutionPointRow(point: ForecastEvolutionPoint) {
    val settings = LocalUnitSettings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = WeatherFormatter.formatRelativeHourLabel(point.time, null, settings),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.forecast_evolution_point,
                point.time.format(RUN_TIME_FORMATTER),
                formatTemperatureDelta(point.temperatureDeltaC, settings.tempUnit),
                stringResource(
                    R.string.forecast_evolution_rain_delta,
                    point.precipitationProbabilityDelta,
                ),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTemperatureDelta(deltaC: Double, unit: TempUnit): String {
    val converted = when (unit) {
        TempUnit.FAHRENHEIT -> deltaC * 9.0 / 5.0
        TempUnit.CELSIUS -> deltaC
    }
    return String.format(Locale.US, "%+.0f\u00B0", converted)
}
