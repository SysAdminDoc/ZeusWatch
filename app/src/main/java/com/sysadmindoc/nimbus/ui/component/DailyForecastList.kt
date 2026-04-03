package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Daily forecast list with tabbed trend views.
 * Inspired by breezy-weather's daily trend system.
 * Tabs: Overview (default expandable rows), Temp, Wind, UV, Precip.
 */

private enum class DailyTrendTab(val label: String) {
    OVERVIEW("Overview"),
    TEMPERATURE("Temp"),
    WIND("Wind"),
    UV("UV"),
    PRECIPITATION("Precip"),
}

@Composable
fun DailyForecastList(
    daily: List<DailyConditions>,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = DailyTrendTab.entries
    // Bounds-check restored tab index against current enum size
    val safeTab = selectedTab.coerceIn(0, tabs.lastIndex)
    if (safeTab != selectedTab) selectedTab = safeTab

    val warmestIndex = remember(daily) {
        daily.take(7).indices.maxByOrNull { daily[it].temperatureHigh } ?: -1
    }
    val weeklyMin = remember(daily) { daily.minOfOrNull { it.temperatureLow } ?: 0.0 }
    val weeklyMax = remember(daily) { daily.maxOfOrNull { it.temperatureHigh } ?: 0.0 }

    WeatherCard(title = "Daily Forecast", modifier = modifier) {
        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                DailyTrendTabChip(
                    label = tab.label,
                    isSelected = index == selectedTab,
                    onClick = { selectedTab = index },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column {
            daily.forEachIndexed { index, day ->
                when (tabs[selectedTab]) {
                    DailyTrendTab.OVERVIEW -> ExpandableDailyRow(
                        day = day,
                        isWarmest = index == warmestIndex && daily.size > 1,
                        weeklyMin = weeklyMin,
                        weeklyMax = weeklyMax,
                    )
                    DailyTrendTab.TEMPERATURE -> DailyTempRow(
                        day = day,
                        weeklyMin = weeklyMin,
                        weeklyMax = weeklyMax,
                    )
                    DailyTrendTab.WIND -> DailyWindRow(day = day)
                    DailyTrendTab.UV -> DailyUvRow(day = day)
                    DailyTrendTab.PRECIPITATION -> DailyPrecipRow(day = day)
                }
                if (index < daily.lastIndex) {
                    HorizontalDivider(color = NimbusCardBorder, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun DailyTrendTabChip(
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

// ── Trend-specific row renderers ────────────────────────────────────────

@Composable
private fun DailyTempRow(
    day: DailyConditions,
    weeklyMin: Double,
    weeklyMax: Double,
) {
    val s = LocalUnitSettings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatDayLabel(day.date),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.width(72.dp),
        )
        AnimatedWeatherIcon(weatherCode = day.weatherCode, isDay = true, iconStyle = s.iconStyle, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            WeatherFormatter.formatTemperature(day.temperatureLow, s),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
        if (weeklyMax > weeklyMin) {
            val range = weeklyMax - weeklyMin
            val startFrac = ((day.temperatureLow - weeklyMin) / range).toFloat().coerceIn(0f, 1f)
            val endFrac = ((day.temperatureHigh - weeklyMin) / range).toFloat().coerceIn(0f, 1f)
            TempRangeBar(
                startFraction = startFrac,
                endFraction = endFrac,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            WeatherFormatter.formatTemperature(day.temperatureHigh, s),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun DailyWindRow(day: DailyConditions) {
    val s = LocalUnitSettings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatDayLabel(day.date),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.width(72.dp),
        )
        AnimatedWeatherIcon(weatherCode = day.weatherCode, isDay = true, iconStyle = s.iconStyle, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (day.windSpeedMax != null) WeatherFormatter.formatWindSpeed(day.windSpeedMax, s) else "--",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            day.windGustsMax?.let { gusts ->
                Text(
                    text = "Gusts ${WeatherFormatter.formatWindSpeed(gusts, s)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }
        day.windDirectionDominant?.let { dir ->
            Text(
                text = windArrowChar(dir),
                style = MaterialTheme.typography.titleMedium,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = WeatherFormatter.formatWindDirection(dir),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun DailyUvRow(day: DailyConditions) {
    val s = LocalUnitSettings.current
    val uv = day.uvIndexMax ?: 0.0
    val uvColor = when {
        uv < 3 -> Color(0xFF4CAF50)
        uv < 6 -> Color(0xFFFFEB3B)
        uv < 8 -> Color(0xFFFF9800)
        uv < 11 -> Color(0xFFF44336)
        else -> Color(0xFF9C27B0)
    }
    val uvLabel = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very High"
        else -> "Extreme"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatDayLabel(day.date),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.width(72.dp),
        )
        AnimatedWeatherIcon(weatherCode = day.weatherCode, isDay = true, iconStyle = s.iconStyle, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        // UV bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((uv / 12.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(uvColor.copy(alpha = 0.7f)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = WeatherFormatter.formatUvIndex(uv),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = uvColor,
            )
            Text(
                text = uvLabel,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun DailyPrecipRow(day: DailyConditions) {
    val s = LocalUnitSettings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatDayLabel(day.date),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.width(72.dp),
        )
        AnimatedWeatherIcon(weatherCode = day.weatherCode, isDay = true, iconStyle = s.iconStyle, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))

        // Precip probability bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(day.precipitationProbability.toFloat() / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NimbusRainBlue.copy(alpha = 0.7f)),
            )
        }
        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(64.dp)) {
            Text(
                text = "${day.precipitationProbability}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (day.precipitationProbability > 40) NimbusRainBlue else NimbusTextSecondary,
            )
            day.precipitationSum?.let { sum ->
                if (sum > 0) {
                    Text(
                        text = WeatherFormatter.formatPrecipitation(sum, s),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                }
            }
        }
    }
}

// ── Original expandable overview row (kept as default tab) ──────────────

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
    val rowShape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .clip(rowShape)
            .background(if (expanded) Color.White.copy(alpha = 0.05f) else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(92.dp)) {
                Text(
                    WeatherFormatter.formatDayLabel(day.date),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    text = day.weatherCode.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            Row(Modifier.width(52.dp), horizontalArrangement = Arrangement.End) {
                if (day.precipitationProbability > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NimbusRainBlue.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${day.precipitationProbability}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NimbusRainBlue,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                WeatherFormatter.formatTemperature(day.temperatureLow, s),
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextTertiary,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
            )
            if (weeklyMax > weeklyMin) {
                val range = weeklyMax - weeklyMin
                val startFraction = ((day.temperatureLow - weeklyMin) / range).toFloat().coerceIn(0f, 1f)
                val endFraction = ((day.temperatureHigh - weeklyMin) / range).toFloat().coerceIn(0f, 1f)
                TempRangeBar(
                    startFraction = startFraction,
                    endFraction = endFraction,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                WeatherFormatter.formatTemperature(day.temperatureHigh, s),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.width(38.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                if (expanded) "Collapse" else "Expand",
                tint = if (expanded) NimbusBlueAccent else NimbusTextTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
                    .padding(start = 4.dp),
            )
        }

        AnimatedVisibility(expanded, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(200))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.04f)),
            ) {
                DailyDetail(day, LocalUnitSettings.current)
            }
        }
    }
}

@Composable
private fun DailyDetail(day: DailyConditions, s: NimbusSettings) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
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

// ── Shared components ───────────────────────────────────────────────────

@Composable
private fun TempRangeBar(
    startFraction: Float,
    endFraction: Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = Color.White.copy(alpha = 0.08f)
    val barShape = RoundedCornerShape(6.dp)
    val midFraction = (startFraction + endFraction) / 2f
    val barColor = when {
        midFraction < 0.33f -> Color(0xFF73C5FF)
        midFraction < 0.66f -> Color(0xFF7AB8FF)
        else -> Color(0xFFFFB74D)
    }
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            barColor.copy(alpha = 0.7f),
            barColor,
        ),
    )

    Box(
        modifier = modifier
            .height(8.dp)
            .clip(barShape)
            .background(trackColor),
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
                        .height(8.dp)
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(4.dp),
        ) {
            Icon(icon, label, Modifier.size(14.dp), tint = NimbusTextTertiary)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun windArrowChar(degrees: Int): String = when {
    degrees < 23 -> "\u2193"
    degrees < 68 -> "\u2199"
    degrees < 113 -> "\u2190"
    degrees < 158 -> "\u2196"
    degrees < 203 -> "\u2191"
    degrees < 248 -> "\u2197"
    degrees < 293 -> "\u2192"
    degrees < 338 -> "\u2198"
    else -> "\u2193"
}
