package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
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
 * Swipeable tab chips switch between data dimensions:
 * Temperature, Feels Like, Wind, Precipitation, Cloud Cover, Humidity.
 */

private enum class HourlyTrendTab(val label: String) {
    TEMPERATURE("Temp"),
    FEELS_LIKE("Feels"),
    WIND("Wind"),
    PRECIPITATION("Precip"),
    CLOUD_COVER("Clouds"),
    HUMIDITY("Humid"),
}

@Composable
fun HourlyForecastStrip(
    hourly: List<HourlyConditions>,
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = HourlyTrendTab.entries
    // Bounds-check restored tab index against current enum size
    val safeTab = selectedTab.coerceIn(0, tabs.lastIndex)
    if (safeTab != selectedTab) selectedTab = safeTab

    // Smart precipitation summary
    val precipSummary = remember(hourly) { precipitationSummary(hourly.take(12)) }

    WeatherCard(
        title = "Hourly Forecast",
        modifier = modifier,
    ) {
        // Tab selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                TrendTabChip(
                    label = tab.label,
                    isSelected = index == selectedTab,
                    onClick = { selectedTab = index },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (precipSummary != null && selectedTab == HourlyTrendTab.PRECIPITATION.ordinal) {
            Text(
                text = precipSummary,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusRainBlue,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        val activeTab = tabs[selectedTab]
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = hourly.take(s.hourlyForecastHours),
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
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        ),
        color = if (isSelected) NimbusBlueAccent else NimbusTextTertiary,
        modifier = Modifier
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
    val shape = RoundedCornerShape(22.dp)
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
            .width(74.dp)
            .clip(shape)
            .background(cardBrush)
            .border(1.dp, if (highlighted) NimbusBlueAccent.copy(alpha = 0.55f) else NimbusCardBorder, shape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
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
        val feelsLike = hour.feelsLike
        if (feelsLike != null && kotlin.math.abs(feelsLike - hour.temperature) >= 3) {
            Text(
                text = WeatherFormatter.formatTemperature(feelsLike, s),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
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
            text = "Actual",
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Text(
            text = WeatherFormatter.formatTemperature(hour.temperature, s),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (kotlin.math.abs(diff) >= 1) {
            val diffInt = diff.toInt()
            Text(
                text = if (diffInt > 0) "+$diffInt" else "$diffInt",
                style = MaterialTheme.typography.labelSmall,
                color = diffColor,
            )
        }
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
        if (windDir != null) {
            Text(
                text = windArrow(windDir),
                style = MaterialTheme.typography.titleMedium,
                color = NimbusTextSecondary,
            )
        }
        hour.windGusts?.let { gusts ->
            if (gusts > windSpeed * 1.3) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "G: ${WeatherFormatter.formatWindSpeed(gusts, s)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }
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
        hour.snowfall?.let { snow ->
            if (snow > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = WeatherFormatter.formatSnowfall(snow, s),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE8EAF6),
                )
            }
        }
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
                cloud <= 10 -> "Clear"
                cloud <= 50 -> "Partly"
                cloud <= 75 -> "Mostly"
                else -> "Overcast"
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
                humidity < 30 -> "Dry"
                humidity < 60 -> "Comfort"
                humidity < 80 -> "Humid"
                else -> "Muggy"
            },
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
    }
}

/** Wind direction to arrow character. */
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

private fun precipitationSummary(hours: List<HourlyConditions>): String? {
    if (hours.size < 3) return null
    val isCurrentlyRaining = hours.firstOrNull()?.precipitationProbability?.let { it > 40 } ?: false
    val firstRainIdx = hours.indexOfFirst { it.precipitationProbability > 40 }
    val lastRainIdx = hours.indexOfLast { it.precipitationProbability > 40 }
    val maxProb = hours.maxOfOrNull { it.precipitationProbability } ?: 0

    if (maxProb < 25) return null

    return when {
        isCurrentlyRaining && lastRainIdx <= 2 -> "Rain ending soon"
        isCurrentlyRaining && lastRainIdx < hours.size - 1 -> {
            val hoursLeft = lastRainIdx + 1
            "Rain for the next ${hoursLeft}h"
        }
        isCurrentlyRaining -> "Rain expected to continue"
        firstRainIdx in 1..3 -> "Rain likely within ${firstRainIdx}h"
        firstRainIdx in 4..8 -> {
            val time = hours[firstRainIdx].time
            "Rain expected around ${WeatherFormatter.formatHourLabel(time)}"
        }
        firstRainIdx > 8 -> "Rain possible later today"
        else -> null
    }
}
