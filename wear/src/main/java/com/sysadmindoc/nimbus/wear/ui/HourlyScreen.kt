package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

private val TextPrimary = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFB0B8CC)
private val TextTertiary = Color(0xFF7A839E)

@Composable
fun HourlyScreen(
    hourly: List<HourlyEntry>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()

    if (hourly.isEmpty()) {
        Text(
            text = "No forecast data",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        )
        return
    }

    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            ListHeader {
                Text(
                    "Hourly Forecast",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
        }
        items(hourly) { entry ->
            HourlyRow(entry)
        }
    }
}

@Composable
private fun HourlyRow(entry: HourlyEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Time
        Text(
            text = formatHour(entry.time),
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.width(48.dp),
        )

        // Weather emoji
        Text(
            text = WearWeatherRepository.wmoEmoji(entry.weatherCode),
            fontSize = 16.sp,
        )

        Spacer(Modifier.width(4.dp))

        // Temperature
        Text(
            text = "${entry.temperature}\u00B0",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )

        // Precip chance
        Text(
            text = "${entry.precipChance}%",
            fontSize = 11.sp,
            color = TextTertiary,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
        )
    }
}

private fun formatHour(isoTime: String): String {
    val hourStr = isoTime.substringAfter("T").substringBefore(":")
    val hour = hourStr.toIntOrNull() ?: return hourStr
    return when {
        hour == 0 -> "12AM"
        hour < 12 -> "${hour}AM"
        hour == 12 -> "12PM"
        else -> "${hour - 12}PM"
    }
}
