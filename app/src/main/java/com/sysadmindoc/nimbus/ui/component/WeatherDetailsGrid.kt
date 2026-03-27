package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun WeatherDetailsGrid(
    current: CurrentConditions,
    modifier: Modifier = Modifier,
    hourly: List<com.sysadmindoc.nimbus.data.model.HourlyConditions> = emptyList(),
) {
    val s = LocalUnitSettings.current

    WeatherCard(
        title = "Today's Details",
        modifier = modifier,
    ) {
        DetailRow(
            left = DetailItem(Icons.Filled.WaterDrop, "Humidity", WeatherFormatter.formatHumidity(current.humidity)),
            right = DetailItem(
                Icons.Outlined.Air, "Wind",
                WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s),
                subtitle = current.windGusts?.let { "Gusts ${WeatherFormatter.formatWindSpeed(it, s)}" },
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            left = DetailItem(Icons.Outlined.WbSunny, "UV Index", WeatherFormatter.formatUvIndex(current.uvIndex), WeatherFormatter.uvDescription(current.uvIndex)),
            right = DetailItem(
                Icons.Filled.Compress, "Pressure",
                WeatherFormatter.formatPressure(current.pressure, s),
                subtitle = WeatherFormatter.pressureTrend(hourly),
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            left = DetailItem(Icons.Filled.Visibility, "Visibility", WeatherFormatter.formatVisibility(current.visibility, s)),
            right = run {
                val dewComfort = current.dewPoint?.let { WeatherFormatter.dewPointComfort(it) }
                DetailItem(
                    Icons.Outlined.Thermostat, "Dew Point",
                    if (current.dewPoint != null) WeatherFormatter.formatDewPoint(current.dewPoint, s) else "--",
                    subtitle = dewComfort,
                    subtitleColor = current.dewPoint?.let { dewPointComfortColor(it) },
                )
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            left = DetailItem(Icons.Outlined.WbSunny, "Sunrise", WeatherFormatter.formatTime(current.sunrise, s)),
            right = DetailItem(Icons.Outlined.WbTwilight, "Sunset", WeatherFormatter.formatTime(current.sunset, s)),
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            left = DetailItem(Icons.Outlined.Cloud, "Cloud Cover", WeatherFormatter.formatCloudCover(current.cloudCover)),
            right = DetailItem(Icons.Filled.WaterDrop, "Precipitation", WeatherFormatter.formatPrecipitation(current.precipitation, s)),
        )
        // Snowfall row (shown when snow is present)
        if ((current.snowfall ?: 0.0) > 0 || (current.snowDepth ?: 0.0) > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(
                left = DetailItem(Icons.Outlined.AcUnit, "Snowfall", current.snowfall?.let { WeatherFormatter.formatSnowfall(it, s) } ?: "--"),
                right = DetailItem(Icons.Outlined.AcUnit, "Snow Depth", current.snowDepth?.let { WeatherFormatter.formatSnowDepth(it, s) } ?: "--"),
            )
        }
    }
}

private data class DetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val subtitle: String? = null,
    val subtitleColor: androidx.compose.ui.graphics.Color? = null,
)

@Composable
private fun DetailRow(left: DetailItem, right: DetailItem) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DetailCell(item = left, modifier = Modifier.weight(1f))
        DetailCell(item = right, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailCell(item: DetailItem, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp), tint = NimbusTextTertiary)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = item.label, style = MaterialTheme.typography.labelMedium, color = NimbusTextSecondary)
            Text(text = item.value, style = MaterialTheme.typography.titleSmall)
            if (item.subtitle != null) {
                Text(text = item.subtitle, style = MaterialTheme.typography.labelSmall, color = item.subtitleColor ?: NimbusTextTertiary)
            }
        }
    }
}

private fun dewPointComfortColor(dewPointCelsius: Double): Color = when {
    dewPointCelsius < 10 -> Color(0xFF64B5F6)   // Dry — cool blue
    dewPointCelsius < 16 -> Color(0xFF81C784)   // Comfortable — green
    dewPointCelsius < 21 -> Color(0xFFFFB74D)   // Slightly humid — amber
    dewPointCelsius < 24 -> Color(0xFFFF9800)   // Muggy — orange
    else -> Color(0xFFEF5350)                    // Oppressive — red
}
