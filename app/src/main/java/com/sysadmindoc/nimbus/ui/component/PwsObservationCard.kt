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
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PwsObservation
import com.sysadmindoc.nimbus.data.repository.TempestPrecipitationType
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.util.Locale

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
    // Detail rows are built once so the merged content description and the
    // visible rows can never drift apart. mergeDescendants replaces child text
    // for TalkBack, so every optional row — including the safety-relevant
    // lightning proximity — must be part of the description too.
    val detailRows = buildList {
        observation.windGustKmh?.let {
            add(stringResource(R.string.pws_gust_label) to WeatherFormatter.formatWindSpeed(it, settings))
        }
        observation.pressureHpa?.let {
            add(stringResource(R.string.pws_pressure_label) to WeatherFormatter.formatPressure(it, settings))
        }
        observation.uvIndex?.let {
            add(stringResource(R.string.pws_uv_label) to stringResource(R.string.pws_uv_value, it))
        }
        observation.rainLastMinuteMm?.let {
            add(
                stringResource(R.string.pws_rain_label) to stringResource(
                    R.string.pws_rain_value,
                    WeatherFormatter.formatPrecipitation(it, settings),
                    stringResource(observation.precipitationType.labelRes),
                ),
            )
        }
        observation.lightningStrikeCount?.takeIf { it > 0 }?.let { count ->
            val value = observation.lightningStrikeAverageDistanceKm?.let { distanceKm ->
                stringResource(
                    R.string.pws_lightning_value_with_distance,
                    count,
                    formatLightningDistance(distanceKm, settings),
                )
            } ?: stringResource(R.string.pws_lightning_value, count)
            add(stringResource(R.string.pws_lightning_label) to value)
        }
    }
    val semantics = buildString {
        append(stringResource(R.string.pws_semantics, temperature, humidity, wind, observedTime))
        detailRows.forEach { (label, value) ->
            append(' ')
            append(label)
            append(' ')
            append(value)
            append('.')
        }
    }

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
            detailRows.forEach { (label, value) ->
                PwsDetailRow(
                    label = label,
                    value = value,
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

/**
 * Lightning distance arrives in kilometres; render it in the user's chosen
 * distance unit, matching [WeatherFormatter]'s visibility formatting style.
 */
internal fun formatLightningDistance(distanceKm: Double, settings: NimbusSettings): String =
    when (settings.visibilityUnit) {
        VisibilityUnit.MILES -> String.format(Locale.US, "%.1f mi", distanceKm / 1.609344)
        VisibilityUnit.KM -> String.format(Locale.US, "%.1f km", distanceKm)
    }

private val TempestPrecipitationType.labelRes: Int
    get() = when (this) {
        TempestPrecipitationType.NONE -> R.string.pws_precip_none
        TempestPrecipitationType.RAIN -> R.string.pws_precip_rain
        TempestPrecipitationType.HAIL -> R.string.pws_precip_hail
        TempestPrecipitationType.RAIN_AND_HAIL -> R.string.pws_precip_rain_hail
        TempestPrecipitationType.UNKNOWN -> R.string.pws_precip_unknown
    }
