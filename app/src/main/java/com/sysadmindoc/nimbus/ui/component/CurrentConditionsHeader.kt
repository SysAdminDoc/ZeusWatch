package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Hero current conditions display matching TWC's layout:
 * Large temperature, condition text, feels like, day high/low.
 */
@Composable
fun CurrentConditionsHeader(
    current: CurrentConditions,
    locationName: String,
    modifier: Modifier = Modifier,
) {
    val s = LocalUnitSettings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Location name
        Text(
            text = locationName,
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Large temperature + weather icon row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = WeatherFormatter.formatTemperature(current.temperature, s),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(modifier = Modifier.width(12.dp))
            WeatherIcon(
                weatherCode = current.weatherCode,
                isDay = current.isDay,
                modifier = Modifier.size(64.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Condition description
        Text(
            text = current.weatherCode.description,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Feels like
        Text(
            text = "Feels like ${WeatherFormatter.formatTemperature(current.feelsLike, s)}",
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Day high / Night low
        Text(
            text = "Day ${WeatherFormatter.formatTemperature(current.dailyHigh, s)} " +
                "\u2022 Night ${WeatherFormatter.formatTemperature(current.dailyLow, s)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = NimbusTextSecondary,
        )
    }
}
