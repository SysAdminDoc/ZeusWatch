package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusHeroGlow
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.conditionDescription
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CurrentConditionsHeader(
    current: CurrentConditions,
    locationName: String,
    modifier: Modifier = Modifier,
    yesterdayHigh: Double? = null,
) {
    val s = LocalUnitSettings.current
    val shape = RoundedCornerShape(12.dp)
    val copy = currentHeaderCopy(current, yesterdayHigh, s)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop,
                        Color.White.copy(alpha = 0.03f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, shape)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        CurrentHeaderTopRow(
            locationName = locationName,
            daylightLabel = copy.daylightLabel,
        )
        Spacer(modifier = Modifier.height(18.dp))
        CurrentHeaderConditionsRow(
            current = current,
            settings = s,
            copy = copy,
        )
        Spacer(modifier = Modifier.height(20.dp))
        CurrentHeaderMetricsRow(
            current = current,
            settings = s,
            comparisonLabel = copy.comparisonLabel,
            comparisonColor = copy.comparisonColor,
        )
    }
}

private data class CurrentHeaderCopy(
    val feelsLikeText: String,
    val secondaryMeta: String,
    val daylightLabel: String,
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
        secondaryMeta = currentSecondaryMeta(current, settings),
        daylightLabel = if (current.isDay) {
            stringResource(R.string.current_daylight)
        } else {
            stringResource(R.string.current_overnight)
        },
        comparisonLabel = currentComparisonLabel(current, yesterdayHigh, settings),
        comparisonColor = currentComparisonColor(current, yesterdayHigh, settings),
    )
}

@Composable
private fun currentSecondaryMeta(
    current: CurrentConditions,
    settings: NimbusSettings,
): String = buildList {
    add(
        stringResource(
            R.string.current_wind_meta,
            WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, settings),
        ),
    )
    add(stringResource(R.string.current_humidity_meta, current.humidity))
    if (current.uvIndex > 0) {
        add(stringResource(R.string.current_uv_meta, WeatherFormatter.formatUvIndex(current.uvIndex)))
    }
}.joinToString(" • ")

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
private fun CurrentHeaderTopRow(
    locationName: String,
    daylightLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroBadge(
            text = locationName,
            modifier = Modifier.weight(1f),
            icon = {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = NimbusBlueAccent,
                    modifier = Modifier.size(14.dp),
                )
            },
        )
        Spacer(modifier = Modifier.width(10.dp))
        HeroBadge(text = daylightLabel)
    }
}

@Composable
private fun CurrentHeaderConditionsRow(
    current: CurrentConditions,
    settings: NimbusSettings,
    copy: CurrentHeaderCopy,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrentHeaderTextBlock(
            current = current,
            settings = settings,
            copy = copy,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        CurrentWeatherIconFrame(current = current, settings = settings)
    }
}

@Composable
private fun CurrentHeaderTextBlock(
    current: CurrentConditions,
    settings: NimbusSettings,
    copy: CurrentHeaderCopy,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.current_conditions_label),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusBlueAccent,
        )
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedTemperature(
            temperatureCelsius = current.temperature,
            settings = settings,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = current.conditionDescription(context),
            style = MaterialTheme.typography.titleLarge,
            color = NimbusTextPrimary.copy(alpha = 0.94f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = copy.feelsLikeText,
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = copy.secondaryMeta,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun CurrentWeatherIconFrame(
    current: CurrentConditions,
    settings: NimbusSettings,
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        NimbusHeroGlow,
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedWeatherIcon(
            weatherCode = current.weatherCode,
            isDay = current.isDay,
            iconStyle = settings.iconStyle,
            modifier = Modifier.size(54.dp),
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
private fun HeroBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        if (icon != null) {
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor,
        )
    }
}
