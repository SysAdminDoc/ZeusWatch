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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.time.format.DateTimeFormatter

@Composable
fun HourlyTab(
    hourly: List<HourlyConditions>,
    locationName: String,
) {
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

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

        // Group by day
        var lastDay: String? = null
        itemsIndexed(hourly.take(48)) { index, hour ->
            val dayLabel = hour.time.format(dayFormatter)
            if (dayLabel != lastDay) {
                lastDay = dayLabel
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = NimbusBlueAccent,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            HourlyRow(hour = hour, timeFormatter = timeFormatter)
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
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

        // Condition
        Text(
            hour.weatherCode.description,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.weight(1f),
        )

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
