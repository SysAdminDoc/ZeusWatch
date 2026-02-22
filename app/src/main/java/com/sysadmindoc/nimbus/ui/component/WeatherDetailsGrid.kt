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
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun WeatherDetailsGrid(
    current: CurrentConditions,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current

    WeatherCard(
        title = "Today's Details",
        modifier = modifier,
    ) {
        DetailRow(
            left = DetailItem(Icons.Filled.WaterDrop, "Humidity", WeatherFormatter.formatHumidity(current.humidity)),
            right = DetailItem(Icons.Outlined.Air, "Wind", WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s)),
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            left = DetailItem(Icons.Outlined.WbSunny, "UV Index", WeatherFormatter.formatUvIndex(current.uvIndex), WeatherFormatter.uvDescription(current.uvIndex)),
            right = DetailItem(Icons.Filled.Compress, "Pressure", WeatherFormatter.formatPressure(current.pressure, s)),
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            left = DetailItem(Icons.Filled.Visibility, "Visibility", WeatherFormatter.formatVisibility(current.visibility, s)),
            right = DetailItem(Icons.Outlined.Thermostat, "Dew Point", if (current.dewPoint != null) WeatherFormatter.formatDewPoint(current.dewPoint, s) else "--"),
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
    }
}

private data class DetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val subtitle: String? = null,
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
                Text(text = item.subtitle, style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
            }
        }
    }
}
