package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun DailyForecastList(
    daily: List<DailyConditions>,
    modifier: Modifier = Modifier,
) {
    // Find the warmest day index and weekly temp range (within first 7 days)
    val warmestIndex = remember(daily) {
        daily.take(7).indices.maxByOrNull { daily[it].temperatureHigh } ?: -1
    }
    val weeklyMin = remember(daily) { daily.minOfOrNull { it.temperatureLow } ?: 0.0 }
    val weeklyMax = remember(daily) { daily.maxOfOrNull { it.temperatureHigh } ?: 0.0 }

    WeatherCard(title = "Daily Forecast", modifier = modifier) {
        Column {
            daily.forEachIndexed { index, day ->
                ExpandableDailyRow(
                    day = day,
                    isWarmest = index == warmestIndex && daily.size > 1,
                    weeklyMin = weeklyMin,
                    weeklyMax = weeklyMax,
                )
                if (index < daily.lastIndex) {
                    HorizontalDivider(color = NimbusCardBorder, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandableDailyRow(
    day: DailyConditions,
    isWarmest: Boolean = false,
    weeklyMin: Double = 0.0,
    weeklyMax: Double = 0.0,
) {
    val s = LocalUnitSettings.current
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "arrow")

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(80.dp)) {
                Text(WeatherFormatter.formatDayLabel(day.date), style = MaterialTheme.typography.bodyLarge)
                if (isWarmest) {
                    Text(
                        "Warmest",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusWarning,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            AnimatedWeatherIcon(weatherCode = day.weatherCode, isDay = true, iconStyle = s.iconStyle, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Row(Modifier.width(44.dp), horizontalArrangement = Arrangement.End) {
                if (day.precipitationProbability > 0) {
                    Text("${day.precipitationProbability}%", style = MaterialTheme.typography.labelMedium, color = NimbusRainBlue, textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(WeatherFormatter.formatTemperature(day.temperatureLow, s), style = MaterialTheme.typography.labelMedium, color = NimbusTextTertiary, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            // Temperature range bar
            if (weeklyMax > weeklyMin) {
                val range = weeklyMax - weeklyMin
                val startFraction = ((day.temperatureLow - weeklyMin) / range).toFloat().coerceIn(0f, 1f)
                val endFraction = ((day.temperatureHigh - weeklyMin) / range).toFloat().coerceIn(0f, 1f)
                TempRangeBar(
                    startFraction = startFraction,
                    endFraction = endFraction,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(WeatherFormatter.formatTemperature(day.temperatureHigh, s), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(34.dp))
            Icon(Icons.Filled.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", tint = NimbusTextTertiary, modifier = Modifier.size(20.dp).rotate(rotation).padding(start = 4.dp))
        }

        AnimatedVisibility(expanded, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(200))) {
            DailyDetail(day, s)
        }
    }
}

@Composable
private fun DailyDetail(day: DailyConditions, s: NimbusSettings) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
        Text(
            "${day.weatherCode.description}. High ${WeatherFormatter.formatTemperature(day.temperatureHigh, s)}, Low ${WeatherFormatter.formatTemperature(day.temperatureLow, s)}.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                DetailMini(Icons.Filled.WaterDrop, "Precip", if (day.precipitationSum != null) WeatherFormatter.formatPrecipitation(day.precipitationSum, s) else "0.0")
                Spacer(Modifier.height(6.dp))
                day.precipitationHours?.let {
                    DetailMini(Icons.Filled.WaterDrop, "Rain Hours", WeatherFormatter.formatPrecipitationHours(it))
                    Spacer(Modifier.height(6.dp))
                }
                DetailMini(Icons.Filled.Air, "Wind", if (day.windSpeedMax != null && day.windDirectionDominant != null) WeatherFormatter.formatWindSpeed(day.windSpeedMax, day.windDirectionDominant, s) else "--")
                day.windGustsMax?.let {
                    Spacer(Modifier.height(6.dp))
                    DetailMini(Icons.Filled.Air, "Gusts", WeatherFormatter.formatWindSpeed(it, s))
                }
            }
            Column {
                DetailMini(Icons.Outlined.WbSunny, "Sunrise", WeatherFormatter.formatTime(day.sunrise, s))
                Spacer(Modifier.height(6.dp))
                DetailMini(Icons.Outlined.WbTwilight, "Sunset", WeatherFormatter.formatTime(day.sunset, s))
                day.sunshineDuration?.let {
                    Spacer(Modifier.height(6.dp))
                    DetailMini(Icons.Outlined.WbSunny, "Sunshine", WeatherFormatter.formatSunshineDuration(it))
                }
            }
            Column {
                DetailMini(Icons.Outlined.WbSunny, "UV Max", day.uvIndexMax?.let { WeatherFormatter.formatUvIndex(it) } ?: "--")
                day.snowfallSum?.let {
                    if (it > 0) {
                        Spacer(Modifier.height(6.dp))
                        DetailMini(Icons.Outlined.AcUnit, "Snow", WeatherFormatter.formatSnowfall(it, s))
                    }
                }
            }
        }
    }
}

/**
 * Horizontal temperature range bar showing this day's range within the weekly min-max.
 * Gradient from cool blue (left) to warm orange (right).
 */
@Composable
private fun TempRangeBar(
    startFraction: Float,
    endFraction: Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = Color.White.copy(alpha = 0.06f)
    val barShape = RoundedCornerShape(3.dp)
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF64B5F6), // cool blue
            Color(0xFF81C784), // green
            Color(0xFFFFB74D), // warm orange
        ),
    )

    Box(
        modifier = modifier.height(6.dp).clip(barShape).background(trackColor),
    ) {
        val barWidth = endFraction - startFraction
        if (barWidth > 0.01f) {
            Row(Modifier.matchParentSize()) {
                if (startFraction > 0f) {
                    Spacer(Modifier.weight(startFraction))
                }
                Box(
                    modifier = Modifier
                        .weight(barWidth.coerceAtLeast(0.05f))
                        .height(6.dp)
                        .clip(barShape)
                        .background(gradientBrush),
                )
                val endSpace = 1f - endFraction
                if (endSpace > 0f) {
                    Spacer(Modifier.weight(endSpace))
                }
            }
        }
    }
}

@Composable
private fun DetailMini(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, Modifier.size(14.dp), tint = NimbusTextTertiary)
        Column(Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}
