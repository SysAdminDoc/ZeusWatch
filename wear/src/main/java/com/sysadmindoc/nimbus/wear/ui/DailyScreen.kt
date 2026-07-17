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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

@Composable
fun DailyScreen(
    daily: List<WearDailyEntry>,
    modifier: Modifier = Modifier,
    tempUnit: String = WearUnitFormatter.TEMP_CELSIUS,
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
                title = stringResource(R.string.wear_no_daily_forecast),
                message = stringResource(R.string.wear_no_daily_forecast_message),
                icon = "\uD83D\uDCC5",
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        return
    }

    // Anchor "Today" on the first synced entry's date (the forecast location's
    // calendar), not the watch clock — across a date boundary the watch's
    // LocalDate.now() would label tomorrow's row "Today".
    val anchorDate = dailyAnchorDate(daily)

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WearBackground),
        state = listState,
    ) {
        item {
            WearHeader(
                title = stringResource(R.string.wear_daily_forecast),
                subtitle = stringResource(R.string.wear_daily_subtitle),
            )
        }
        items(daily) { entry ->
            DailyRow(entry, tempUnit, anchorDate)
        }
    }
}

@Composable
private fun DailyRow(
    entry: WearDailyEntry,
    tempUnit: String,
    anchorDate: java.time.LocalDate?,
) {
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
                text = formatDay(
                    isoDate = entry.date,
                    todayLabel = stringResource(R.string.wear_today),
                    tomorrowLabel = stringResource(R.string.wear_tomorrow_short),
                    today = anchorDate,
                ),
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
                text = "${WearUnitFormatter.displayTemp(entry.high, tempUnit)}\u00B0",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = WearTextPrimary,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End,
            )
            Text(
                text = "${WearUnitFormatter.displayTemp(entry.low, tempUnit)}\u00B0",
                fontSize = 12.sp,
                color = WearTextTertiary,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End,
            )
            WearMiniBadge(text = "${entry.precipChance}%", accent = WearBlueAccent)
        }
    }
}

/**
 * "Today" for daily forecasts means the *forecast location's* first entry —
 * the phone always syncs the daily list starting at the location's current
 * day, so the first parseable date is the anchor. Falls back to the watch
 * clock only when nothing parses (defensive; matches WidgetRefreshWorker's
 * location-anchored pattern on the phone).
 */
internal fun dailyAnchorDate(daily: List<WearDailyEntry>): java.time.LocalDate? =
    daily.firstNotNullOfOrNull { parseIsoDate(it.date) }

private fun parseIsoDate(isoDate: String): java.time.LocalDate? {
    val parts = isoDate.split("-")
    if (parts.size != 3) return null
    return try {
        java.time.LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        null
    }
}

internal fun formatDay(
    isoDate: String,
    todayLabel: String,
    tomorrowLabel: String,
    today: java.time.LocalDate?,
): String {
    // Input: "2026-04-14" → "Mon", "Tue", etc.
    return try {
        val date = parseIsoDate(isoDate) ?: return isoDate.takeLast(5)
        val anchor = today ?: java.time.LocalDate.now()
        when (date) {
            anchor -> todayLabel
            anchor.plusDays(1) -> tomorrowLabel
            else -> date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            )
        }
    } catch (_: Exception) {
        isoDate.takeLast(5)
    }
}
