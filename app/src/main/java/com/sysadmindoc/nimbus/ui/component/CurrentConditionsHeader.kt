package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.conditionDescription
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CurrentConditionsHeader(
    current: CurrentConditions,
    modifier: Modifier = Modifier,
    yesterdayHigh: Double? = null,
) {
    val settings = LocalUnitSettings.current
    val copy = currentHeaderCopy(current, yesterdayHigh, settings)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            CurrentHeaderTextBlock(
                current = current,
                settings = settings,
                copy = copy,
                modifier = Modifier.weight(1.05f),
            )
            Spacer(modifier = Modifier.width(22.dp))
            CurrentMetricStack(
                current = current,
                settings = settings,
                modifier = Modifier.weight(0.95f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NimbusCardBorder.copy(alpha = 0.72f)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        CurrentHeaderMetricsRow(
            current = current,
            settings = settings,
            comparisonLabel = copy.comparisonLabel,
            comparisonColor = copy.comparisonColor,
        )
    }
}

private data class CurrentHeaderCopy(
    val feelsLikeText: String,
    val comparisonLabel: String?,
    val comparisonColor: Color,
)

@Composable
private fun currentHeaderCopy(
    current: CurrentConditions,
    yesterdayHigh: Double?,
    settings: NimbusSettings,
): CurrentHeaderCopy {
    val feelsLikeReason = WeatherFormatter.feelsLikeReasonRes(
        current.temperature,
        current.feelsLike,
        current.windSpeed,
        current.humidity,
    )?.let { stringResource(it) }
    val formattedFeelsLike = WeatherFormatter.formatTemperature(current.feelsLike, settings)
    val feelsLikeText = if (feelsLikeReason != null) {
        stringResource(R.string.current_feels_like_with_reason, formattedFeelsLike, feelsLikeReason)
    } else {
        stringResource(R.string.feels_like, formattedFeelsLike)
    }
    return CurrentHeaderCopy(
        feelsLikeText = feelsLikeText,
        comparisonLabel = currentComparisonLabel(current, yesterdayHigh, settings),
        comparisonColor = currentComparisonColor(current, yesterdayHigh, settings),
    )
}

@Composable
private fun currentComparisonLabel(
    current: CurrentConditions,
    yesterdayHigh: Double?,
    settings: NimbusSettings,
): String? {
    if (yesterdayHigh == null) return null
    val todayConverted = WeatherFormatter.convertedTemp(current.dailyHigh, settings)
    val yesterdayConverted = WeatherFormatter.convertedTemp(yesterdayHigh, settings)
    val diff = (todayConverted - yesterdayConverted).roundToInt()
    if (abs(diff) < 2) return null
    return if (diff > 0) {
        stringResource(R.string.current_trend_warmer, diff)
    } else {
        stringResource(R.string.current_trend_cooler, abs(diff))
    }
}

private fun currentComparisonColor(
    current: CurrentConditions,
    yesterdayHigh: Double?,
    settings: NimbusSettings,
): Color = yesterdayHigh?.let {
    val todayConverted = WeatherFormatter.convertedTemp(current.dailyHigh, settings)
    val yesterdayConverted = WeatherFormatter.convertedTemp(it, settings)
    if (todayConverted - yesterdayConverted >= 0) NimbusWarning else NimbusRainBlue
} ?: NimbusTextSecondary

@Composable
private fun CurrentHeaderTextBlock(
    current: CurrentConditions,
    settings: NimbusSettings,
    copy: CurrentHeaderCopy,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        AnimatedTemperature(
            temperatureCelsius = current.temperature,
            settings = settings,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedWeatherIcon(
                weatherCode = current.weatherCode,
                isDay = current.isDay,
                iconStyle = settings.iconStyle,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = current.conditionDescription(context),
                style = MaterialTheme.typography.titleMedium,
                color = NimbusTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = copy.feelsLikeText,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
    }
}

@Composable
private fun CurrentMetricStack(
    current: CurrentConditions,
    settings: NimbusSettings,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.current_metric_unavailable)
    Column(modifier = modifier) {
        HeaderDetailRow(
            label = stringResource(R.string.wind),
            value = WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, settings),
        )
        HeaderDetailRow(
            label = stringResource(R.string.humidity),
            value = "${current.humidity}%",
        )
        HeaderDetailRow(
            label = stringResource(R.string.uv_index),
            value = WeatherFormatter.formatUvIndex(current.uvIndex),
        )
        HeaderDetailRow(
            label = stringResource(R.string.dew_point),
            value = current.dewPoint?.let { WeatherFormatter.formatDewPoint(it, settings) } ?: unavailable,
        )
        HeaderDetailRow(
            label = stringResource(R.string.pressure),
            value = WeatherFormatter.formatPressure(current.pressure, settings),
        )
        HeaderDetailRow(
            label = stringResource(R.string.visibility),
            value = current.visibility?.let { WeatherFormatter.formatVisibility(it, settings) } ?: unavailable,
            showDivider = false,
        )
    }
}

@Composable
private fun HeaderDetailRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NimbusCardBorder.copy(alpha = 0.42f)),
        )
    }
}

@Composable
private fun CurrentHeaderMetricsRow(
    current: CurrentConditions,
    settings: NimbusSettings,
    comparisonLabel: String?,
    comparisonColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HeroMetricTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.current_metric_high),
            value = WeatherFormatter.formatTemperature(current.dailyHigh, settings),
        )
        HeroMetricTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.current_metric_low),
            value = WeatherFormatter.formatTemperature(current.dailyLow, settings),
        )
        comparisonLabel?.let {
            HeroMetricTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.current_metric_trend),
                value = it,
                accentColor = comparisonColor,
            )
        }
    }
}

@Composable
private fun HeroMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = NimbusTextPrimary,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor,
        )
    }
}
