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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

@Composable
fun HourlyScreen(
    hourly: List<HourlyEntry>,
    modifier: Modifier = Modifier,
    tempUnit: String = WearUnitFormatter.TEMP_CELSIUS,
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
                title = stringResource(R.string.wear_no_hourly_forecast),
                message = stringResource(R.string.wear_no_hourly_forecast_message),
                icon = "\u23F0",
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        return
    }

    // Locale-aware hour labels that honor the system 12/24-hour preference.
    // LocalConfiguration keys the remember so a locale change recomposes.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val is24Hour = remember(configuration) {
        android.text.format.DateFormat.is24HourFormat(context)
    }
    val hourFormatter = remember(configuration, is24Hour) {
        hourLabelFormatter(is24Hour, java.util.Locale.getDefault())
    }

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WearBackground),
        state = listState,
    ) {
        item {
            WearHeader(
                title = stringResource(R.string.wear_hourly_forecast),
                subtitle = stringResource(R.string.wear_hourly_subtitle),
            )
        }
        items(hourly) { entry ->
            HourlyRow(entry, tempUnit, hourFormatter)
        }
    }
}

@Composable
private fun HourlyRow(
    entry: HourlyEntry,
    tempUnit: String,
    hourFormatter: java.time.format.DateTimeFormatter,
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
                text = formatHour(entry.time, hourFormatter),
                fontSize = 12.sp,
                color = WearTextSecondary,
                modifier = Modifier.width(50.dp),
            )
            Text(
                text = WearWeatherRepository.wmoEmoji(entry.weatherCode, entry.isDay),
                fontSize = 16.sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${WearUnitFormatter.displayTemp(entry.temperature, tempUnit)}\u00B0",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = WearTextPrimary,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
            )
            WearMiniBadge(text = "${entry.precipChance}%", accent = WearBlueAccent)
        }
    }
}

/** "HH:mm" for 24-hour devices, localized "ha" (e.g. "3PM") otherwise. */
internal fun hourLabelFormatter(
    is24Hour: Boolean,
    locale: java.util.Locale,
): java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "ha", locale)

internal fun formatHour(
    isoTime: String,
    formatter: java.time.format.DateTimeFormatter,
): String {
    // Input: ISO local date-time from the phone sync or the direct API,
    // e.g. "2026-05-17T15:00". Malformed input degrades to the raw hour text.
    return try {
        formatter.format(java.time.LocalDateTime.parse(isoTime))
    } catch (_: Exception) {
        isoTime.substringAfter("T").substringBefore(":")
    }
}
