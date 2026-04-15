package com.sysadmindoc.nimbus.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.component.WeatherIcon
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HourlyTab(
    hourly: List<HourlyConditions>,
    locationName: String,
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current
    val forecastHours = s.hourlyForecastHours
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

    val groupedHourly = remember(hourly, forecastHours) {
        hourly.take(forecastHours).groupBy { it.time.toLocalDate() }
            .toSortedMap()
            .entries.toList()
    }
    val referenceDate = referenceTime?.toLocalDate()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Hourly Forecast",
                style = MaterialTheme.typography.headlineLarge,
                color = NimbusTextPrimary,
            )
            Text(
                locationName,
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextSecondary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NimbusCardBg)
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        groupedHourly.forEach { (date, hours) ->
            val currentHourIndex = if (referenceTime == null) {
                -1
            } else {
                hours.indexOfFirst { hour -> WeatherFormatter.isSameForecastHour(hour.time, referenceTime) }
            }
            stickyHeader(key = "header_$date") {
                val dayLabel = when {
                    referenceDate != null && date == referenceDate -> "Today"
                    referenceDate != null && date == referenceDate.plusDays(1) -> "Tomorrow"
                    else -> date.format(dayFormatter)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NimbusBackgroundGradient),
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = NimbusBlueAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(NimbusCardBg)
                            .border(1.dp, NimbusCardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            itemsIndexed(hours, key = { _, h -> h.time.toString() }) { index, hour ->
                HourlyRow(
                    hour = hour,
                    isCurrent = index == currentHourIndex && currentHourIndex >= 0,
                    referenceTime = referenceTime,
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
    }
}

@Composable
private fun HourlyRow(
    hour: HourlyConditions,
    isCurrent: Boolean,
    referenceTime: java.time.LocalDateTime?,
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (isCurrent) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = 0.24f),
                            NimbusGlassBottom,
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.5f),
                            NimbusCardBg,
                            NimbusGlassBottom,
                        )
                    },
                ),
            )
            .border(1.dp, if (isCurrent) NimbusBlueAccent.copy(alpha = 0.55f) else NimbusCardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(76.dp)) {
            Text(
                WeatherFormatter.formatRelativeHourLabel(
                    time = hour.time,
                    referenceTime = if (isCurrent) referenceTime else null,
                    s = s,
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isCurrent) NimbusBlueAccent else NimbusTextSecondary,
            )
            if (hour.precipitationProbability > 0) {
                Text(
                    "${hour.precipitationProbability}% rain",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusBlueAccent,
                )
            }
        }

        WeatherIcon(
            weatherCode = hour.weatherCode,
            isDay = hour.isDay,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            WeatherFormatter.formatTemperature(hour.temperature, s),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextPrimary,
            modifier = Modifier.width(52.dp),
        )

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
