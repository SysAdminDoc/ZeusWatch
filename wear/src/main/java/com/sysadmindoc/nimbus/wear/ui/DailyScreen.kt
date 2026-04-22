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
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

@Composable
fun DailyScreen(
    daily: List<WearDailyEntry>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()

    if (daily.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(WearBackground),
            contentAlignment = Alignment.Center,
        ) {
            WearStateCard(
                title = "No daily forecast",
                message = "Daily outlook will appear after the next phone sync.",
                icon = "\uD83D\uDCC5",
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
            WearHeader(title = "7-Day Forecast", subtitle = "High, low, and rain risk at a glance")
        }
        items(daily) { entry ->
            DailyRow(entry)
        }
    }
}

@Composable
private fun DailyRow(entry: WearDailyEntry) {
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
                text = formatDay(entry.date),
                fontSize = 12.sp,
                color = WearTextSecondary,
                modifier = Modifier.width(44.dp),
            )
            Text(
                text = WearWeatherRepository.wmoEmoji(entry.weatherCode),
                fontSize = 16.sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${entry.high}\u00B0",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = WearTextPrimary,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End,
            )
            Text(
                text = "${entry.low}\u00B0",
                fontSize = 12.sp,
                color = WearTextTertiary,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End,
            )
            WearMiniPill(text = "${entry.precipChance}%", accent = WearBlueAccent)
        }
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
