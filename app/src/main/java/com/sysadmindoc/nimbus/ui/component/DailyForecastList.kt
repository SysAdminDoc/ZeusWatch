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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
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
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun DailyForecastList(
    daily: List<DailyConditions>,
    modifier: Modifier = Modifier,
) {
    WeatherCard(title = "Daily Forecast", modifier = modifier) {
        Column {
            daily.forEachIndexed { index, day ->
                ExpandableDailyRow(day)
                if (index < daily.lastIndex) {
                    HorizontalDivider(color = NimbusCardBorder, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandableDailyRow(day: DailyConditions) {
    val s = LocalUnitSettings.current
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "arrow")

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(WeatherFormatter.formatDayLabel(day.date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(80.dp))
            Spacer(Modifier.width(8.dp))
            WeatherIcon(day.weatherCode, isDay = true, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Row(Modifier.width(44.dp), horizontalArrangement = Arrangement.End) {
                if (day.precipitationProbability > 0) {
                    Text("${day.precipitationProbability}%", style = MaterialTheme.typography.labelMedium, color = NimbusRainBlue, textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(WeatherFormatter.formatTemperature(day.temperatureHigh, s), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            Spacer(Modifier.width(8.dp))
            Text(WeatherFormatter.formatTemperature(day.temperatureLow, s), style = MaterialTheme.typography.titleSmall, color = NimbusTextTertiary, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
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
                DetailMini(Icons.Filled.Air, "Wind", if (day.windSpeedMax != null && day.windDirectionDominant != null) WeatherFormatter.formatWindSpeed(day.windSpeedMax, day.windDirectionDominant, s) else "--")
            }
            Column {
                DetailMini(Icons.Outlined.WbSunny, "Sunrise", WeatherFormatter.formatTime(day.sunrise, s))
                Spacer(Modifier.height(6.dp))
                DetailMini(Icons.Outlined.WbTwilight, "Sunset", WeatherFormatter.formatTime(day.sunset, s))
            }
            Column {
                DetailMini(Icons.Outlined.WbSunny, "UV Max", day.uvIndexMax?.let { WeatherFormatter.formatUvIndex(it) } ?: "--")
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
