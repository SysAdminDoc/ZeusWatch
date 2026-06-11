package com.sysadmindoc.nimbus.ui.component

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusFogGray
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Tabbed hourly trend system inspired by breezy-weather.
 * Swipeable tab segments switch between data dimensions:
 * Temperature, Feels Like, Wind, Precipitation, Cloud Cover, Humidity.
 */

private enum class HourlyTrendTab(@StringRes val labelRes: Int) {
    TEMPERATURE(R.string.forecast_tab_temp),
    FEELS_LIKE(R.string.forecast_tab_feels),
    WIND(R.string.forecast_tab_wind),
    PRECIPITATION(R.string.forecast_tab_precip),
    CLOUD_COVER(R.string.forecast_tab_clouds),
    HUMIDITY(R.string.forecast_tab_humid),
}

@Composable
fun HourlyForecastStrip(
    hourly: List<HourlyConditions>,
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = HourlyTrendTab.entries
    // Bounds-check restored tab index against current enum size
    val safeTab = selectedTab.coerceIn(0, tabs.lastIndex)
    if (safeTab != selectedTab) selectedTab = safeTab

    // Smart precipitation summary
    val precipSummary = remember(hourly, context) { precipitationSummary(context, hourly.take(12)) }
    val forecastHours = remember(hourly, s.hourlyForecastHours) { hourly.take(s.hourlyForecastHours) }
    val activeTab = tabs[selectedTab]
    val headerSummary = remember(activeTab, forecastHours, referenceTime, s, context) {
        hourlyHeaderSummary(context, activeTab, forecastHours, referenceTime, s)
    }

    WeatherCard(
        titleRes = R.string.card_type_hourly_forecast,
        modifier = modifier,
    ) {
        // Tab selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                TrendTabChip(
                    label = stringResource(tab.labelRes),
                    isSelected = index == selectedTab,
                    onClick = { selectedTab = index },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = headerSummary,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )

        if (precipSummary != null && selectedTab == HourlyTrendTab.PRECIPITATION.ordinal) {
            Text(
                text = precipSummary,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusRainBlue,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = forecastHours,
                key = { _, item -> item.time },
                contentType = { _, _ -> activeTab.name },
            ) { index, hour ->
                when (activeTab) {
                    HourlyTrendTab.TEMPERATURE -> HourlyItemTemp(hour = hour, highlighted = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime), referenceTime = referenceTime)
                    HourlyTrendTab.FEELS_LIKE -> HourlyItemFeelsLike(hour = hour, highlighted = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime), referenceTime = referenceTime)
                    HourlyTrendTab.WIND -> HourlyItemWind(hour = hour, highlighted = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime), referenceTime = referenceTime)
                    HourlyTrendTab.PRECIPITATION -> HourlyItemPrecip(hour = hour, highlighted = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime), referenceTime = referenceTime)
                    HourlyTrendTab.CLOUD_COVER -> HourlyItemCloudCover(hour = hour, highlighted = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime), referenceTime = referenceTime)
                    HourlyTrendTab.HUMIDITY -> HourlyItemHumidity(hour = hour, highlighted = referenceTime != null && WeatherFormatter.isSameForecastHour(hour.time, referenceTime), referenceTime = referenceTime)
                }
            }
        }
    }
}

@Composable
private fun TrendTabChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        ),
        color = if (isSelected) NimbusBlueAccent else NimbusTextTertiary,
        modifier = Modifier
            .heightIn(min = 48.dp) // a11y minimum touch target
            .clip(shape)
            .background(
                if (isSelected) NimbusBlueAccent.copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.05f),
            )
            .border(
                1.dp,
                if (isSelected) NimbusBlueAccent.copy(alpha = 0.4f)
                else Color.Transparent,
                shape,
            )
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

// ── Column wrapper for all hourly items ─────────────────────────────────

@Composable
private fun HourlyItemShell(
    hour: HourlyConditions,
    highlighted: Boolean,
    referenceTime: java.time.LocalDateTime?,
    content: @Composable () -> Unit,
) {
    val s = LocalUnitSettings.current
    val shape = RoundedCornerShape(10.dp)
    val cardBrush = if (highlighted) {
        Brush.verticalGradient(
            colors = listOf(
                NimbusBlueAccent.copy(alpha = 0.28f),
                NimbusGlassBottom,
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                NimbusGlassTop.copy(alpha = 0.75f),
                NimbusGlassBottom,
            ),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(82.dp)
            .clip(shape)
            .background(cardBrush)
            .border(1.dp, if (highlighted) NimbusBlueAccent.copy(alpha = 0.55f) else NimbusCardBorder, shape)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Text(
            text = WeatherFormatter.formatRelativeHourLabel(hour.time, referenceTime, s),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (highlighted) NimbusBlueAccent else NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

// ── Tab-specific item renderers ─────────────────────────────────────────

@Composable
private fun HourlyItemTemp(hour: HourlyConditions, highlighted: Boolean, referenceTime: java.time.LocalDateTime?) {
    val s = LocalUnitSettings.current
    HourlyItemShell(hour, highlighted, referenceTime) {
        AnimatedWeatherIcon(
            weatherCode = hour.weatherCode,
            isDay = hour.isDay,
            iconStyle = s.iconStyle,
            modifier = Modifier.size(30.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = WeatherFormatter.formatTemperature(hour.temperature, s),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        // Always render the feels-like line (blank when it's close to the
        // actual temp) so every card is the same height — otherwise cards that
        // show a feels-like value are taller than the ones that don't.
        val feelsText = hour.feelsLike
            ?.takeIf { kotlin.math.abs(it - hour.temperature) >= 3 }
            ?.let { WeatherFormatter.formatTemperature(it, s) }
            ?: " "
        Text(
            text = feelsText,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${hour.precipitationProbability}%",
            style = MaterialTheme.typography.labelLarge,
            color = if (hour.precipitationProbability > 20) NimbusRainBlue else NimbusTextSecondary,
        )
    }
}

@Composable
private fun HourlyItemFeelsLike(hour: HourlyConditions, highlighted: Boolean, referenceTime: java.time.LocalDateTime?) {
    val s = LocalUnitSettings.current
    val feelsLike = hour.feelsLike ?: hour.temperature
    val diff = feelsLike - hour.temperature
    val diffColor = when {
        diff > 3 -> Color(0xFFFF8A65) // warmer
        diff < -3 -> NimbusRainBlue   // cooler
        else -> NimbusTextSecondary
    }
    HourlyItemShell(hour, highlighted, referenceTime) {
        Text(
            text = WeatherFormatter.formatTemperature(feelsLike, s),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.forecast_hourly_actual),
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Text(
            text = WeatherFormatter.formatTemperature(hour.temperature, s),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Always render the delta line (blank when negligible) for equal height.
        val diffInt = diff.toInt()
        Text(
            text = when {
                kotlin.math.abs(diff) < 1 -> " "
                diffInt > 0 -> "+$diffInt"
                else -> "$diffInt"
            },
            style = MaterialTheme.typography.labelSmall,
            color = diffColor,
        )
    }
}

@Composable
private fun HourlyItemWind(
    hour: HourlyConditions,
    highlighted: Boolean,
    referenceTime: java.time.LocalDateTime?,
) {
    val s = LocalUnitSettings.current
    val windSpeed = hour.windSpeed ?: 0.0
    val windDir = hour.windDirection

    HourlyItemShell(hour, highlighted, referenceTime) {
        Text(
            text = WeatherFormatter.formatWindSpeed(windSpeed, s),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Reserve both the arrow and gust lines (blank when absent) so every
        // wind card is the same height regardless of which hours are gusty.
        Text(
            text = if (windDir != null) windArrow(windDir) else " ",
            style = MaterialTheme.typography.titleMedium,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val gusts = hour.windGusts
        Text(
            text = if (gusts != null && gusts > windSpeed * 1.3) {
                stringResource(R.string.forecast_gust_abbrev, WeatherFormatter.formatWindSpeed(gusts, s))
            } else {
                " "
            },
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
    }
}

@Composable
private fun HourlyItemPrecip(
    hour: HourlyConditions,
    highlighted: Boolean,
    referenceTime: java.time.LocalDateTime?,
) {
    val s = LocalUnitSettings.current
    HourlyItemShell(hour, highlighted, referenceTime) {
        Text(
            text = "${hour.precipitationProbability}%",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (hour.precipitationProbability > 40) NimbusRainBlue else NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val precip = hour.precipitation
        if (precip != null && precip > 0.0) {
            Text(
                text = WeatherFormatter.formatPrecipitation(precip, s),
                style = MaterialTheme.typography.labelMedium,
                color = NimbusRainBlue,
            )
        } else {
            Text(
                text = "--",
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextTertiary,
            )
        }
        // Reserve the snowfall line (blank when none) so cards stay equal height.
        Spacer(modifier = Modifier.height(4.dp))
        val snow = hour.snowfall
        Text(
            text = if (snow != null && snow > 0) WeatherFormatter.formatSnowfall(snow, s) else " ",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE8EAF6),
        )
    }
}

@Composable
private fun HourlyItemCloudCover(
    hour: HourlyConditions,
    highlighted: Boolean,
    referenceTime: java.time.LocalDateTime?,
) {
    val cloud = hour.cloudCover ?: 0
    val cloudColor = when {
        cloud <= 25 -> NimbusBlueAccent
        cloud <= 50 -> NimbusFogGray
        cloud <= 75 -> NimbusTextSecondary
        else -> NimbusTextTertiary
    }
    HourlyItemShell(hour, highlighted, referenceTime) {
        Text(
            text = "$cloud%",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = cloudColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Mini bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(cloud.toFloat() / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cloudColor.copy(alpha = 0.7f)),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                cloud <= 10 -> stringResource(R.string.forecast_cloud_clear)
                cloud <= 50 -> stringResource(R.string.forecast_cloud_partly)
                cloud <= 75 -> stringResource(R.string.forecast_cloud_mostly)
                else -> stringResource(R.string.forecast_cloud_overcast)
            },
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
    }
}

@Composable
private fun HourlyItemHumidity(
    hour: HourlyConditions,
    highlighted: Boolean,
    referenceTime: java.time.LocalDateTime?,
) {
    val humidity = hour.humidity ?: 0
    val humidColor = when {
        humidity < 30 -> Color(0xFFFFB74D) // dry
        humidity > 70 -> NimbusRainBlue     // humid
        else -> NimbusTextSecondary
    }
    HourlyItemShell(hour, highlighted, referenceTime) {
        Text(
            text = "$humidity%",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = humidColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Mini bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(humidity.toFloat() / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(humidColor.copy(alpha = 0.7f)),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                humidity < 30 -> stringResource(R.string.forecast_humidity_dry)
                humidity < 60 -> stringResource(R.string.forecast_humidity_comfort)
                humidity < 80 -> stringResource(R.string.forecast_humidity_humid)
                else -> stringResource(R.string.forecast_humidity_muggy)
            },
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
    }
}

/** Wind direction to arrow character. Convention: arrow points where the wind blows TO (bearing + 180°). */
private fun windArrow(degrees: Int): String = when {
    degrees < 23 -> "\u2193"   // N wind blows south
    degrees < 68 -> "\u2199"   // NE
    degrees < 113 -> "\u2190"  // E
    degrees < 158 -> "\u2196"  // SE
    degrees < 203 -> "\u2191"  // S
    degrees < 248 -> "\u2197"  // SW
    degrees < 293 -> "\u2192"  // W
    degrees < 338 -> "\u2198"  // NW
    else -> "\u2193"           // N
}

private fun precipitationSummary(context: Context, hours: List<HourlyConditions>): String? {
    if (hours.size < 3) return null
    val isCurrentlyRaining = hours.firstOrNull()?.precipitationProbability?.let { it > 40 } ?: false
    val firstRainIdx = hours.indexOfFirst { it.precipitationProbability > 40 }
    val lastRainIdx = hours.indexOfLast { it.precipitationProbability > 40 }
    val maxProb = hours.maxOfOrNull { it.precipitationProbability } ?: 0

    if (maxProb < 25) return null

    return when {
        isCurrentlyRaining && lastRainIdx <= 2 -> context.getString(R.string.forecast_precip_rain_ending_soon)
        isCurrentlyRaining && lastRainIdx < hours.size - 1 -> {
            val hoursLeft = lastRainIdx + 1
            context.getString(R.string.forecast_precip_rain_next_hours, hoursLeft)
        }
        isCurrentlyRaining -> context.getString(R.string.forecast_precip_continue)
        firstRainIdx in 1..3 -> context.getString(R.string.forecast_precip_within_hours, firstRainIdx)
        firstRainIdx in 4..8 -> {
            val time = hours[firstRainIdx].time
            context.getString(R.string.forecast_precip_around, WeatherFormatter.formatHourLabel(time))
        }
        firstRainIdx > 8 -> context.getString(R.string.forecast_precip_later_today)
        else -> null
    }
}

private fun hourlyHeaderSummary(
    context: Context,
    activeTab: HourlyTrendTab,
    hours: List<HourlyConditions>,
    referenceTime: java.time.LocalDateTime?,
    settings: NimbusSettings,
): String {
    if (hours.isEmpty()) return context.getString(R.string.forecast_hourly_empty)

    return when (activeTab) {
        HourlyTrendTab.TEMPERATURE -> {
            val min = hours.minOf { it.temperature }
            val max = hours.maxOf { it.temperature }
            context.getString(
                R.string.forecast_hourly_temp_range,
                WeatherFormatter.formatTemperature(min, settings),
                WeatherFormatter.formatTemperature(max, settings),
                hours.size,
            )
        }
        HourlyTrendTab.FEELS_LIKE -> {
            val maxGap = hours.maxOfOrNull { kotlin.math.abs((it.feelsLike ?: it.temperature) - it.temperature) } ?: 0.0
            if (maxGap >= 3) {
                context.getString(R.string.forecast_hourly_feels_swing, kotlin.math.round(maxGap).toInt())
            } else {
                context.getString(R.string.forecast_hourly_feels_close)
            }
        }
        HourlyTrendTab.WIND -> {
            val peakWind = hours.maxOfOrNull { it.windSpeed ?: 0.0 } ?: 0.0
            val peakGust = hours.maxOfOrNull { it.windGusts ?: 0.0 } ?: 0.0
            if (peakGust > peakWind) {
                context.getString(
                    R.string.forecast_hourly_wind_peak_gusts,
                    WeatherFormatter.formatWindSpeed(peakWind, settings),
                    WeatherFormatter.formatWindSpeed(peakGust, settings),
                )
            } else {
                context.getString(R.string.forecast_hourly_wind_peak, WeatherFormatter.formatWindSpeed(peakWind, settings))
            }
        }
        HourlyTrendTab.PRECIPITATION -> precipitationSummary(context, hours.take(12))
            ?: context.getString(R.string.forecast_hourly_precip_low)
        HourlyTrendTab.CLOUD_COVER -> {
            val avgCloud = hours.mapNotNull { it.cloudCover }.average().takeIf { !it.isNaN() } ?: 0.0
            when {
                avgCloud < 20 -> context.getString(R.string.forecast_hourly_cloud_clear)
                avgCloud < 50 -> context.getString(R.string.forecast_hourly_cloud_partly)
                avgCloud < 75 -> context.getString(R.string.forecast_hourly_cloud_elevated)
                else -> context.getString(R.string.forecast_hourly_cloud_overcast)
            }
        }
        HourlyTrendTab.HUMIDITY -> {
            val minHumidity = hours.minOfOrNull { it.humidity ?: 0 } ?: 0
            val maxHumidity = hours.maxOfOrNull { it.humidity ?: 0 } ?: 0
            context.getString(R.string.forecast_hourly_humidity_range, minHumidity, maxHumidity, hours.size)
        }
    }
}
