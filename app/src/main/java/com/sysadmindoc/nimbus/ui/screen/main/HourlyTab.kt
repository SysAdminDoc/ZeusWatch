package com.sysadmindoc.nimbus.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.component.WeatherIcon
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HourlyTab(
    hourly: List<HourlyConditions>,
    locationName: String,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current
    val forecastHours = s.hourlyForecastHours
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    val timePattern = if (s.timeFormat == com.sysadmindoc.nimbus.data.repository.TimeFormat.TWENTY_FOUR_HOUR) "HH:mm" else "h:mm a"
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern)

    val groupedHourly = remember(hourly, forecastHours) {
        hourly.take(forecastHours).groupBy { it.time.toLocalDate() }
            .toSortedMap()
            .entries.toList()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Hourly Forecast",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = NimbusTextPrimary,
            )
            Text(
                locationName,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        groupedHourly.forEach { (date, hours) ->
            stickyHeader(key = "header_$date") {
                val dayLabel = when (date) {
                    LocalDate.now() -> "Today"
                    LocalDate.now().plusDays(1) -> "Tomorrow"
                    else -> date.format(dayFormatter)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NimbusNavyDark),
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = NimbusBlueAccent,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            itemsIndexed(hours, key = { _, h -> h.time.toString() }) { _, hour ->
                HourlyRow(hour = hour, timeFormatter = timeFormatter)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
    }
}

@Composable
private fun HourlyRow(
    hour: HourlyConditions,
    timeFormatter: DateTimeFormatter,
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusCardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time
        Text(
            hour.time.format(timeFormatter),
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
            modifier = Modifier.width(76.dp),
        )

        // Icon
        WeatherIcon(
            weatherCode = hour.weatherCode,
            isDay = hour.isDay,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Temp
        Text(
            WeatherFormatter.formatTemperature(hour.temperature, s),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextPrimary,
            modifier = Modifier.width(52.dp),
        )

        // Condition + feels like
        Column(modifier = Modifier.weight(1f)) {
            Text(
                hour.weatherCode.description,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
            val feelsLike = hour.feelsLike
            if (feelsLike != null && kotlin.math.abs(feelsLike - hour.temperature) >= 3) {
                Text(
                    "Feels ${WeatherFormatter.formatTemperature(feelsLike, s)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }

        // Wind speed
        hour.windSpeed?.let { ws ->
            if (ws > 0) {
                Text(
                    WeatherFormatter.formatWindSpeed(ws, s),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        // Precip probability
        if (hour.precipitationProbability > 0) {
            Text(
                "${hour.precipitationProbability}%",
                style = MaterialTheme.typography.labelMedium,
                color = NimbusBlueAccent,
            )
        }
    }
}
