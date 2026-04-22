package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

@Composable
fun HourlyScreen(
    hourly: List<HourlyEntry>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()

    if (hourly.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(WearBackground),
            contentAlignment = Alignment.Center,
        ) {
            WearStateCard(
                title = "No hourly forecast",
                message = "Hourly detail will appear after the next phone sync.",
                icon = "\u23F0",
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        return
    }

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WearBackground),
        state = listState,
    ) {
        item {
            WearHeader(title = "Hourly Forecast", subtitle = "Temperature and rain risk for the next stretch")
        }
        items(hourly) { entry ->
            HourlyRow(entry)
        }
    }
}

@Composable
private fun HourlyRow(entry: HourlyEntry) {
    WearPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatHour(entry.time),
                fontSize = 12.sp,
                color = WearTextSecondary,
                modifier = Modifier.width(50.dp),
            )
            Text(
                text = WearWeatherRepository.wmoEmoji(entry.weatherCode),
                fontSize = 16.sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${entry.temperature}\u00B0",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = WearTextPrimary,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
            )
            WearMiniPill(text = "${entry.precipChance}%", accent = WearBlueAccent)
        }
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
