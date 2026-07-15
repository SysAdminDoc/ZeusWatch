package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusSurface
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.ForecastUncertaintyDetail
import com.sysadmindoc.nimbus.util.ForecastUncertaintyExplainer
import com.sysadmindoc.nimbus.util.ForecastUncertaintyLevel
import com.sysadmindoc.nimbus.util.conditionDescription
import com.sysadmindoc.nimbus.util.WeatherFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HourlyForecastDetailSheet(
    hour: HourlyConditions,
    referenceTime: java.time.LocalDateTime?,
    confidenceBands: ConfidenceBandData? = null,
    onDismiss: () -> Unit,
) {
    val settings = LocalUnitSettings.current
    val context = LocalContext.current
    val title = WeatherFormatter.formatRelativeHourLabel(context, hour.time, referenceTime, settings)
    ForecastDetailSheetFrame(
        title = stringResource(R.string.forecast_detail_hourly_title, title),
        subtitle = hour.conditionDescription(context),
        weatherCode = hour.weatherCode,
        isDay = hour.isDay,
        heroValue = WeatherFormatter.formatTemperature(hour.temperature, settings),
        heroLabel = stringResource(R.string.forecast_detail_temperature),
        metrics = hourlyMetrics(hour, settings),
        uncertainty = ForecastUncertaintyExplainer.detailForHour(hour, confidenceBands)?.let {
            DetailSheetUncertainty(detail = it, isDaily = false)
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HourlyForecastDetailPanel(
    hour: HourlyConditions,
    referenceTime: java.time.LocalDateTime?,
    confidenceBands: ConfidenceBandData? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalUnitSettings.current
    val context = LocalContext.current
    val title = WeatherFormatter.formatRelativeHourLabel(context, hour.time, referenceTime, settings)
    ForecastDetailPanelFrame(
        title = stringResource(R.string.forecast_detail_hourly_title, title),
        subtitle = hour.conditionDescription(context),
        weatherCode = hour.weatherCode,
        isDay = hour.isDay,
        heroValue = WeatherFormatter.formatTemperature(hour.temperature, settings),
        heroLabel = stringResource(R.string.forecast_detail_temperature),
        metrics = hourlyMetrics(hour, settings),
        uncertainty = ForecastUncertaintyExplainer.detailForHour(hour, confidenceBands)?.let {
            DetailSheetUncertainty(detail = it, isDaily = false)
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyForecastDetailSheet(
    day: DailyConditions,
    referenceDate: java.time.LocalDate?,
    confidenceBands: ConfidenceBandData? = null,
    onDismiss: () -> Unit,
) {
    val settings = LocalUnitSettings.current
    val context = LocalContext.current
    val dayLabel = WeatherFormatter.formatRelativeDayLabel(context, day.date, referenceDate)
    ForecastDetailSheetFrame(
        title = stringResource(R.string.forecast_detail_daily_title, dayLabel),
        subtitle = day.conditionDescription(context),
        weatherCode = day.weatherCode,
        isDay = true,
        heroValue = stringResource(
            R.string.forecast_detail_temp_range,
            WeatherFormatter.formatTemperature(day.temperatureHigh, settings),
            WeatherFormatter.formatTemperature(day.temperatureLow, settings),
        ),
        heroLabel = stringResource(R.string.forecast_detail_high_low),
        metrics = dailyMetrics(day, settings),
        uncertainty = ForecastUncertaintyExplainer.detailForDay(day, confidenceBands)?.let {
            DetailSheetUncertainty(detail = it, isDaily = true)
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ForecastDetailSheetFrame(
    title: String,
    subtitle: String,
    weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode,
    isDay: Boolean,
    heroValue: String,
    heroLabel: String,
    metrics: List<DetailMetric>,
    uncertainty: DetailSheetUncertainty?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bottomSheetHandleDescription = stringResource(R.string.common_bottom_sheet_handle)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NimbusSurface,
        scrimColor = NimbusNavyDark.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clearAndSetSemantics {
                        contentDescription = bottomSheetHandleDescription
                    },
            )
        },
    ) {
        ForecastDetailContent(
            title = title,
            subtitle = subtitle,
            weatherCode = weatherCode,
            isDay = isDay,
            heroValue = heroValue,
            heroLabel = heroLabel,
            metrics = metrics,
            uncertainty = uncertainty,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastDetailPanelFrame(
    title: String,
    subtitle: String,
    weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode,
    isDay: Boolean,
    heroValue: String,
    heroLabel: String,
    metrics: List<DetailMetric>,
    uncertainty: DetailSheetUncertainty?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val backDescription = stringResource(R.string.common_back)
    ForecastDetailContent(
        title = title,
        subtitle = subtitle,
        weatherCode = weatherCode,
        isDay = isDay,
        heroValue = heroValue,
        heroLabel = heroLabel,
        metrics = metrics,
        uncertainty = uncertainty,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NimbusSurface.copy(alpha = 0.94f))
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.26f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        trailingContent = {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                    tint = NimbusTextSecondary,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastDetailContent(
    title: String,
    subtitle: String,
    weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode,
    isDay: Boolean,
    heroValue: String,
    heroLabel: String,
    metrics: List<DetailMetric>,
    uncertainty: DetailSheetUncertainty?,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedWeatherIcon(
                weatherCode = weatherCode,
                isDay = isDay,
                iconStyle = LocalUnitSettings.current.iconStyle,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = heroValue,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = NimbusBlueAccent,
                )
                Text(
                    text = heroLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
            trailingContent?.let {
                Spacer(modifier = Modifier.width(4.dp))
                it()
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            metrics.forEach { metric ->
                DetailMetricCard(metric)
            }
        }

        if (uncertainty != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ForecastUncertaintyDetailCard(
                detail = uncertainty.detail,
                isDaily = uncertainty.isDaily,
            )
        }
    }
}

@Composable
private fun ForecastUncertaintyDetailCard(
    detail: ForecastUncertaintyDetail,
    isDaily: Boolean,
) {
    val settings = LocalUnitSettings.current
    val label = stringResource(R.string.forecast_uncertainty_label)
    val levelText = stringResource(detail.level.descriptionRes())
    val lower = WeatherFormatter.formatTemperature(detail.lowerC, settings)
    val upper = WeatherFormatter.formatTemperature(detail.upperC, settings)
    val spread = WeatherFormatter.formatTemperatureDelta(detail.spreadC, settings)
    val summary = stringResource(
        if (isDaily) R.string.forecast_uncertainty_day_summary
        else R.string.forecast_uncertainty_hour_summary,
        lower,
        upper,
        spread,
        levelText,
    )
    val explainer = stringResource(R.string.forecast_uncertainty_explainer)
    val semanticText = stringResource(
        R.string.forecast_uncertainty_semantics,
        label,
        summary,
        explainer,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = semanticText
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Thermostat,
            contentDescription = null,
            tint = NimbusBlueAccent,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NimbusTextPrimary,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
            Text(
                text = explainer,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun DetailMetricCard(metric: DetailMetric) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = metric.icon,
            contentDescription = null,
            tint = metric.tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NimbusTextPrimary,
            )
        }
    }
}

@Composable
private fun hourlyMetrics(hour: HourlyConditions, settings: NimbusSettings): List<DetailMetric> = buildList {
    val feelsLike = hour.feelsLike ?: hour.temperature
    add(
        DetailMetric(
            icon = Icons.Filled.Thermostat,
            label = stringResource(R.string.forecast_detail_feels_like),
            value = WeatherFormatter.formatTemperature(feelsLike, settings),
            tint = NimbusBlueAccent,
        )
    )
    add(
        DetailMetric(
            icon = Icons.Outlined.Umbrella,
            label = stringResource(R.string.forecast_detail_rain_chance),
            value = "${hour.precipitationProbability}%",
            tint = NimbusRainBlue,
        )
    )
    hour.precipitation?.let {
        add(
            DetailMetric(
                icon = Icons.Filled.WaterDrop,
                label = stringResource(R.string.forecast_detail_precip),
                value = WeatherFormatter.formatPrecipitation(it, settings),
                tint = NimbusRainBlue,
            )
        )
    }
    hour.snowfall?.takeIf { it > 0.0 }?.let {
        add(
            DetailMetric(
                icon = Icons.Outlined.AcUnit,
                label = stringResource(R.string.forecast_detail_snow),
                value = WeatherFormatter.formatSnowfall(it, settings),
                tint = Color(0xFFE8EAF6),
            )
        )
    }
    hour.windSpeed?.let { windSpeed ->
        add(
            DetailMetric(
                icon = Icons.Filled.Air,
                label = stringResource(R.string.forecast_detail_wind),
                value = hour.windDirection?.let { WeatherFormatter.formatWindSpeed(windSpeed, it, settings) }
                    ?: WeatherFormatter.formatWindSpeed(windSpeed, settings),
                tint = NimbusTextSecondary,
            )
        )
    }
    hour.windGusts?.let {
        add(
            DetailMetric(
                icon = Icons.Filled.Air,
                label = stringResource(R.string.forecast_detail_gusts),
                value = WeatherFormatter.formatWindSpeed(it, settings),
                tint = NimbusTextSecondary,
            )
        )
    }
    hour.humidity?.let {
        add(DetailMetric(Icons.Filled.WaterDrop, stringResource(R.string.humidity), "$it%", NimbusTextSecondary))
    }
    hour.uvIndex?.let {
        add(
            DetailMetric(
                Icons.Outlined.WbSunny,
                stringResource(R.string.uv_index),
                "${WeatherFormatter.formatUvIndex(it)} ${stringResource(WeatherFormatter.uvDescriptionRes(it))}",
                NimbusBlueAccent,
            )
        )
    }
    hour.cloudCover?.let {
        add(DetailMetric(Icons.Filled.Cloud, stringResource(R.string.cloud_cover), "$it%", NimbusTextSecondary))
    }
    hour.visibility?.let {
        add(DetailMetric(Icons.Filled.Visibility, stringResource(R.string.visibility), WeatherFormatter.formatVisibility(it, settings), NimbusTextSecondary))
    }
    hour.surfacePressure?.let {
        add(DetailMetric(Icons.Filled.Compress, stringResource(R.string.pressure), WeatherFormatter.formatPressure(it, settings), NimbusTextSecondary))
    }
    hour.sunshineDuration?.let {
        add(DetailMetric(Icons.Outlined.WbSunny, stringResource(R.string.forecast_detail_sunshine), WeatherFormatter.formatSunshineDuration(it), NimbusBlueAccent))
    }
}

@Composable
private fun dailyMetrics(day: DailyConditions, settings: NimbusSettings): List<DetailMetric> = buildList {
    add(
        DetailMetric(
            icon = Icons.Outlined.Umbrella,
            label = stringResource(R.string.forecast_detail_rain_chance),
            value = "${day.precipitationProbability}%",
            tint = NimbusRainBlue,
        )
    )
    day.precipitationSum?.let {
        add(
            DetailMetric(
                icon = Icons.Filled.WaterDrop,
                label = stringResource(R.string.forecast_detail_precip),
                value = WeatherFormatter.formatPrecipitation(it, settings),
                tint = NimbusRainBlue,
            )
        )
    }
    day.snowfallSum?.takeIf { it > 0.0 }?.let {
        add(
            DetailMetric(
                icon = Icons.Outlined.AcUnit,
                label = stringResource(R.string.forecast_detail_snow),
                value = WeatherFormatter.formatSnowfall(it, settings),
                tint = Color(0xFFE8EAF6),
            )
        )
    }
    day.precipitationHours?.let {
        add(
            DetailMetric(
                icon = Icons.Filled.WaterDrop,
                label = stringResource(R.string.forecast_detail_rain_hours),
                value = WeatherFormatter.formatPrecipitationHours(it),
                tint = NimbusRainBlue,
            )
        )
    }
    day.windSpeedMax?.let { windSpeed ->
        add(
            DetailMetric(
                icon = Icons.Filled.Air,
                label = stringResource(R.string.forecast_detail_wind),
                value = day.windDirectionDominant?.let { WeatherFormatter.formatWindSpeed(windSpeed, it, settings) }
                    ?: WeatherFormatter.formatWindSpeed(windSpeed, settings),
                tint = NimbusTextSecondary,
            )
        )
    }
    day.windGustsMax?.let {
        add(DetailMetric(Icons.Filled.Air, stringResource(R.string.forecast_detail_gusts), WeatherFormatter.formatWindSpeed(it, settings), NimbusTextSecondary))
    }
    day.uvIndexMax?.let {
        add(
            DetailMetric(
                Icons.Outlined.WbSunny,
                stringResource(R.string.forecast_detail_uv_max),
                "${WeatherFormatter.formatUvIndex(it)} ${stringResource(WeatherFormatter.uvDescriptionRes(it))}",
                NimbusBlueAccent,
            )
        )
    }
    add(DetailMetric(Icons.Outlined.WbSunny, stringResource(R.string.forecast_detail_sunrise), WeatherFormatter.formatTime(day.sunrise, settings), NimbusBlueAccent))
    add(DetailMetric(Icons.Outlined.WbTwilight, stringResource(R.string.forecast_detail_sunset), WeatherFormatter.formatTime(day.sunset, settings), NimbusTextSecondary))
    day.sunshineDuration?.let {
        add(DetailMetric(Icons.Outlined.WbSunny, stringResource(R.string.forecast_detail_sunshine), WeatherFormatter.formatSunshineDuration(it), NimbusBlueAccent))
    }
}

private data class DetailMetric(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val tint: Color,
)

private data class DetailSheetUncertainty(
    val detail: ForecastUncertaintyDetail,
    val isDaily: Boolean,
)

private fun ForecastUncertaintyLevel.descriptionRes(): Int = when (this) {
    ForecastUncertaintyLevel.LOW -> R.string.temperature_graph_uncertainty_low
    ForecastUncertaintyLevel.MEDIUM -> R.string.temperature_graph_uncertainty_medium
    ForecastUncertaintyLevel.HIGH -> R.string.temperature_graph_uncertainty_high
}
