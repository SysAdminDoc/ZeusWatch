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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.PwsObservation
import com.sysadmindoc.nimbus.data.repository.TempestPrecipitationType
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun PwsObservationCard(
    observation: PwsObservation,
    modifier: Modifier = Modifier,
) {
    val settings = LocalUnitSettings.current
    val observedTime = WeatherFormatter.formatClockTime(observation.observedAt, settings)
    val temperature = WeatherFormatter.formatTemperatureUnit(observation.temperatureC, settings)
    val humidity = stringResource(R.string.pws_humidity_value, observation.humidityPercent)
    val wind = observation.windDirectionDegrees?.let { direction ->
        WeatherFormatter.formatWindSpeed(observation.windSpeedKmh, direction, settings)
    } ?: WeatherFormatter.formatWindSpeed(observation.windSpeedKmh, settings)
    val status = observation.reportIntervalMinutes?.let {
        stringResource(R.string.pws_report_interval_badge, it)
    } ?: stringResource(R.string.pws_live_badge)
    val semantics = stringResource(
        R.string.pws_semantics,
        temperature,
        humidity,
        wind,
        observedTime,
    )

    WeatherCard(
        titleRes = R.string.card_type_pws_observation,
        statusLabel = status,
        statusTint = NimbusSuccess,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semantics
        },
    ) {
        Text(
            text = stringResource(R.string.pws_source_label, observation.sourceLabel, observedTime),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PwsMetric(
                label = stringResource(R.string.pws_temperature_label),
                value = temperature,
                tint = NimbusBlueAccent,
                modifier = Modifier.weight(1f),
            )
            PwsMetric(
                label = stringResource(R.string.pws_humidity_label),
                value = humidity,
                modifier = Modifier.weight(1f),
            )
            PwsMetric(
                label = stringResource(R.string.pws_wind_label),
                value = wind,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            observation.windGustKmh?.let {
                PwsDetailRow(
                    label = stringResource(R.string.pws_gust_label),
                    value = WeatherFormatter.formatWindSpeed(it, settings),
                )
            }
            observation.pressureHpa?.let {
                PwsDetailRow(
                    label = stringResource(R.string.pws_pressure_label),
                    value = WeatherFormatter.formatPressure(it, settings),
                )
            }
            observation.uvIndex?.let {
                PwsDetailRow(
                    label = stringResource(R.string.pws_uv_label),
                    value = stringResource(R.string.pws_uv_value, it),
                )
            }
            observation.rainLastMinuteMm?.let {
                PwsDetailRow(
                    label = stringResource(R.string.pws_rain_label),
                    value = stringResource(
                        R.string.pws_rain_value,
                        WeatherFormatter.formatPrecipitation(it, settings),
                        stringResource(observation.precipitationType.labelRes),
                    ),
                )
            }
            observation.lightningStrikeCount?.takeIf { it > 0 }?.let { count ->
                PwsDetailRow(
                    label = stringResource(R.string.pws_lightning_label),
                    value = observation.lightningStrikeAverageDistanceKm?.let { distance ->
                        stringResource(R.string.pws_lightning_value_with_distance, count, distance)
                    } ?: stringResource(R.string.pws_lightning_value, count),
                )
            }
        }
    }
}

@Composable
private fun PwsMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = NimbusTextPrimary,
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
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PwsDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TempestPrecipitationType.labelRes: Int
    get() = when (this) {
        TempestPrecipitationType.NONE -> R.string.pws_precip_none
        TempestPrecipitationType.RAIN -> R.string.pws_precip_rain
        TempestPrecipitationType.HAIL -> R.string.pws_precip_hail
        TempestPrecipitationType.RAIN_AND_HAIL -> R.string.pws_precip_rain_hail
        TempestPrecipitationType.UNKNOWN -> R.string.pws_precip_unknown
    }
