package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Horizontal scrolling hourly forecast matching TWC's layout:
 * Time | Temp | Icon | Precip%
 */
@Composable
fun HourlyForecastStrip(
    hourly: List<HourlyConditions>,
    modifier: Modifier = Modifier,
) {
    WeatherCard(
        title = "Hourly Forecast",
        modifier = modifier,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(hourly.take(24)) { hour ->
                HourlyItem(hour)
            }
        }
    }
}

@Composable
private fun HourlyItem(hour: HourlyConditions) {
    val s = LocalUnitSettings.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp),
    ) {
        // Time label
        Text(
            text = WeatherFormatter.formatHourLabel(hour.time, s),
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Temperature
        Text(
            text = WeatherFormatter.formatTemperature(hour.temperature, s),
            style = MaterialTheme.typography.titleSmall,
        )

        // Feels-like when significantly different (3+ degrees)
        val feelsLike = hour.feelsLike
        if (feelsLike != null && kotlin.math.abs(feelsLike - hour.temperature) >= 3) {
            Text(
                text = WeatherFormatter.formatTemperature(feelsLike, s),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Weather icon (supports Meteocons Lottie when enabled)
        AnimatedWeatherIcon(
            weatherCode = hour.weatherCode,
            isDay = hour.isDay,
            iconStyle = s.iconStyle,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Precipitation probability
        Text(
            text = "${hour.precipitationProbability}%",
            style = MaterialTheme.typography.labelSmall,
            color = if (hour.precipitationProbability > 20) NimbusRainBlue
                else NimbusTextSecondary,
        )

        // Precipitation amount (when > 0)
        val precip = hour.precipitation
        if (precip != null && precip > 0.0) {
            Text(
                text = WeatherFormatter.formatPrecipitation(precip, s),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}
