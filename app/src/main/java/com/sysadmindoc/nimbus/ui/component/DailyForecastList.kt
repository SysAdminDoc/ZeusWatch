package com.sysadmindoc.nimbus.ui.component

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
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

private enum class DailyTrendTab(@StringRes val labelRes: Int) {
    OVERVIEW(R.string.forecast_tab_overview),
    TEMPERATURE(R.string.forecast_tab_temp),
    WIND(R.string.forecast_tab_wind),
    UV(R.string.forecast_tab_uv),
    PRECIPITATION(R.string.forecast_tab_precip),
}

@Composable
fun DailyForecastList(
    daily: List<DailyConditions>,
    referenceDate: java.time.LocalDate? = daily.firstOrNull()?.date,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
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
    val weeklySummary = remember(daily, referenceDate, s, context) {
        dailyHeaderSummary(context, daily.take(7), referenceDate, s)
    }
    var selectedDay by remember { mutableStateOf<DailyConditions?>(null) }

    selectedDay?.let { day ->
        DailyForecastDetailSheet(
            day = day,
            referenceDate = referenceDate,
            onDismiss = { selectedDay = null },
        )
    }

    WeatherCard(titleRes = R.string.card_type_daily_forecast, modifier = modifier) {
        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                DailyTrendTabChip(
                    label = stringResource(tab.labelRes),
                    isSelected = index == selectedTab,
                    onClick = { selectedTab = index },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = weeklySummary,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column {
            daily.forEachIndexed { index, day ->
                when (tabs[selectedTab]) {
                    DailyTrendTab.OVERVIEW -> DailyOverviewRow(
                        day = day,
                        referenceDate = referenceDate,
                        isWarmest = index == warmestIndex && daily.size > 1,
                        weeklyMin = weeklyMin,
                        weeklyMax = weeklyMax,
                        onDetailClick = { selectedDay = day },
                    )
                    DailyTrendTab.TEMPERATURE -> DailyTempRow(
                        day = day,
                        referenceDate = referenceDate,
                        weeklyMin = weeklyMin,
                        weeklyMax = weeklyMax,
                        onClick = { selectedDay = day },
                    )
                    DailyTrendTab.WIND -> DailyWindRow(day = day, referenceDate = referenceDate, onClick = { selectedDay = day })
                    DailyTrendTab.UV -> DailyUvRow(day = day, referenceDate = referenceDate, onClick = { selectedDay = day })
                    DailyTrendTab.PRECIPITATION -> DailyPrecipRow(day = day, referenceDate = referenceDate, onClick = { selectedDay = day })
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

// ── Trend-specific row renderers ────────────────────────────────────────

@Composable
private fun DailyTempRow(
    day: DailyConditions,
    referenceDate: java.time.LocalDate?,
    weeklyMin: Double,
    weeklyMax: Double,
    onClick: () -> Unit,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatRelativeDayLabel(context, day.date, referenceDate),
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
private fun DailyWindRow(
    day: DailyConditions,
    referenceDate: java.time.LocalDate?,
    onClick: () -> Unit,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatRelativeDayLabel(context, day.date, referenceDate),
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
                    text = stringResource(
                        R.string.forecast_gusts_value,
                        WeatherFormatter.formatWindSpeed(gusts, s),
                    ),
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
private fun DailyUvRow(
    day: DailyConditions,
    referenceDate: java.time.LocalDate?,
    onClick: () -> Unit,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    val uv = day.uvIndexMax ?: 0.0
    val uvColor = when {
        uv < 3 -> Color(0xFF4CAF50)
        uv < 6 -> Color(0xFFFFEB3B)
        uv < 8 -> Color(0xFFFF9800)
        uv < 11 -> Color(0xFFF44336)
        else -> Color(0xFF9C27B0)
    }
    val uvLabel = stringResource(uvLevelLabelRes(uv))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatRelativeDayLabel(context, day.date, referenceDate),
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
private fun DailyPrecipRow(
    day: DailyConditions,
    referenceDate: java.time.LocalDate?,
    onClick: () -> Unit,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WeatherFormatter.formatRelativeDayLabel(context, day.date, referenceDate),
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

// ── Overview row (kept as default tab) ──────────────────────────────────

@Composable
private fun DailyOverviewRow(
    day: DailyConditions,
    referenceDate: java.time.LocalDate? = null,
    isWarmest: Boolean = false,
    weeklyMin: Double = 0.0,
    weeklyMax: Double = 0.0,
    onDetailClick: () -> Unit,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    val rowShape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(rowShape)
            .clickable(
                onClick = onDetailClick,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(92.dp)) {
            Text(
                WeatherFormatter.formatRelativeDayLabel(context, day.date, referenceDate),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = stringResource(day.weatherCode.descriptionRes()),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isWarmest) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NimbusWarning.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.forecast_warmest_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusWarning,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        AnimatedWeatherIcon(weatherCode = day.weatherCode, isDay = true, iconStyle = s.iconStyle, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.width(52.dp), horizontalArrangement = Arrangement.End) {
            if (day.precipitationProbability > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
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
            stringResource(R.string.forecast_detail_open),
            tint = NimbusTextTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(4.dp)
                .size(20.dp),
        )
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

// Convention: arrow points where the wind blows TO (bearing + 180°).
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

private fun dailyHeaderSummary(
    context: Context,
    daily: List<DailyConditions>,
    referenceDate: java.time.LocalDate?,
    settings: NimbusSettings,
): String {
    if (daily.isEmpty()) return context.getString(R.string.forecast_daily_empty)

    val warmest = daily.maxByOrNull { it.temperatureHigh }
    val wettest = daily.maxByOrNull { it.precipitationProbability }
    val warmestLabel = warmest?.let { WeatherFormatter.formatRelativeDayLabel(context, it.date, referenceDate) }
        ?: context.getString(R.string.forecast_later)
    val warmestTemp = warmest?.let { WeatherFormatter.formatTemperature(it.temperatureHigh, settings) } ?: "--"

    return if ((wettest?.precipitationProbability ?: 0) >= 40) {
        val wettestLabel = WeatherFormatter.formatRelativeDayLabel(context, wettest!!.date, referenceDate)
        context.getString(R.string.forecast_daily_warm_rain, warmestLabel, warmestTemp, wettestLabel)
    } else {
        context.getString(R.string.forecast_daily_warm_steady, warmestLabel, warmestTemp)
    }
}

@StringRes
private fun uvLevelLabelRes(uv: Double): Int = when {
    uv < 3 -> R.string.forecast_uv_low
    uv < 6 -> R.string.forecast_uv_moderate
    uv < 8 -> R.string.forecast_uv_high
    uv < 11 -> R.string.forecast_uv_very_high
    else -> R.string.forecast_uv_extreme
}
