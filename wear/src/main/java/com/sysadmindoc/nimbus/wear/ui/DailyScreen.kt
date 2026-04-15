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
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

private val TextPrimary = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFB0B8CC)
private val TextTertiary = Color(0xFF7A839E)

@Composable
fun DailyScreen(
    daily: List<WearDailyEntry>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()

    if (daily.isEmpty()) {
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
                    "7-Day Forecast",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
        }
        items(daily) { entry ->
            DailyRow(entry)
        }
    }
}

@Composable
private fun DailyRow(entry: WearDailyEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Day label
        Text(
            text = formatDay(entry.date),
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.width(40.dp),
        )

        // Weather emoji
        Text(
            text = WearWeatherRepository.wmoEmoji(entry.weatherCode),
            fontSize = 16.sp,
        )

        Spacer(Modifier.width(4.dp))

        // High
        Text(
            text = "${entry.high}\u00B0",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
        )

        // Low
        Text(
            text = "${entry.low}\u00B0",
            fontSize = 13.sp,
            color = TextTertiary,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
        )

        // Precip chance
        Text(
            text = "${entry.precipChance}%",
            fontSize = 11.sp,
            color = TextTertiary,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End,
        )
    }
}

private fun formatDay(isoDate: String): String {
    // Input: "2026-04-14" → "Mon", "Tue", etc.
    return try {
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate.takeLast(5)
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val date = java.time.LocalDate.of(year, month, day)
        val today = java.time.LocalDate.now()
        when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tmrw"
            else -> date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            )
        }
    } catch (_: Exception) {
        isoDate.takeLast(5)
    }
}
