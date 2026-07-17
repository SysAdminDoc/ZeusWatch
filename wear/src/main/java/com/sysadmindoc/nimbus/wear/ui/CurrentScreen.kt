package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.WearUiState
import com.sysadmindoc.nimbus.wear.data.DataSource
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import kotlin.math.max

@Composable
fun CurrentScreen(
    state: WearUiState,
    modifier: Modifier = Modifier,
    onHourlyTap: () -> Unit = {},
    onDailyTap: () -> Unit = {},
    onAlertsTap: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onTempUnitToggle: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isLoading) {
            WearStateCard(
                title = stringResource(R.string.wear_refreshing_forecast),
                message = stringResource(R.string.wear_refreshing_forecast_message),
                icon = "\uD83C\uDF26\uFE0F",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else if (state.error != null) {
            WearStateCard(
                title = stringResource(R.string.wear_forecast_unavailable),
                message = stringResource(R.string.wear_forecast_unavailable_message),
                icon = "\u26A0\uFE0F",
                actionLabel = stringResource(R.string.retry),
                onAction = onRefresh,
                accent = WearAlertAccent,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            WeatherContent(state, onHourlyTap, onDailyTap, onAlertsTap, onRefresh, onTempUnitToggle)
        }
    }
}

@Composable
private fun WeatherContent(
    state: WearUiState,
    onHourlyTap: () -> Unit,
    onDailyTap: () -> Unit,
    onAlertsTap: () -> Unit,
    onRefresh: () -> Unit,
    onTempUnitToggle: () -> Unit,
) {
    // Scrollable so the footer/link rows aren't clipped on small round
    // screens; when content fits, the centered arrangement keeps the old
    // fixed-layout look.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        if (state.alerts.isNotEmpty()) {
            AlertBanner(
                count = state.alerts.size,
                topEvent = state.alerts.first().event,
                onTap = onAlertsTap,
            )
        }

        WearPanel(modifier = Modifier.fillMaxWidth()) {
            WearHeader(
                title = state.locationName,
                subtitle = if (state.isDay) {
                    stringResource(R.string.wear_daylight)
                } else {
                    stringResource(R.string.wear_overnight)
                },
            )
            Text(
                text = WearWeatherRepository.wmoEmoji(state.weatherCode, state.isDay),
                fontSize = 30.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "${WearUnitFormatter.displayTemp(state.temperature, state.tempUnit)}\u00B0",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = WearTextPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = state.condition,
                fontSize = 12.sp,
                color = WearTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.wear_high_low,
                    WearUnitFormatter.displayTemp(state.high, state.tempUnit),
                    WearUnitFormatter.displayTemp(state.low, state.tempUnit),
                ),
                fontSize = 10.sp,
                color = WearTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TempUnitChip(
                tempUnit = state.tempUnit,
                onToggle = onTempUnitToggle,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        WearPanel(modifier = Modifier.fillMaxWidth()) {
            WearMiniBadge(
                text = stringResource(R.string.wear_right_now),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            DetailRow(state)
        }

        WearLinkRow(
            primary = stringResource(R.string.wear_hourly_link) to onHourlyTap,
            secondary = if (state.daily.isNotEmpty()) {
                stringResource(R.string.wear_daily_link) to onDailyTap
            } else {
                null
            },
            modifier = Modifier.padding(top = 2.dp),
        )

        SyncFooter(
            dataSource = state.dataSource,
            // Freshness reflects data age, not sync age — the phone can push
            // hours-old cached data during an outage and it must not read
            // "just now". Older phone apps don't send data age; fall back.
            updatedAtMs = if (state.updatedAtMs > 0L) state.updatedAtMs else state.syncedAtMs,
            onRefresh = onRefresh,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TempUnitChip(
    tempUnit: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (tempUnit) {
        WearUnitFormatter.TEMP_FAHRENHEIT -> "\u00B0F"
        else -> "\u00B0C"
    }
    Text(
        text = stringResource(R.string.wear_temp_unit_toggle, label),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = WearBlueAccent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WearBlueAccent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .border(1.dp, WearBlueAccent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.wear_temp_unit_toggle_cd, label),
                onClick = onToggle,
                onLongClick = onToggle,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun SyncFooter(
    dataSource: DataSource,
    updatedAtMs: Long,
    onRefresh: () -> Unit,
) {
    val ageLabel = freshnessLabel(updatedAtMs)
    val sourceLabel = when (dataSource) {
        DataSource.PHONE_SYNC -> stringResource(R.string.wear_data_source_phone)
        DataSource.DIRECT_API -> stringResource(R.string.wear_data_source_watch)
        DataSource.UNKNOWN -> "—"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(
                onClick = onRefresh,
                role = Role.Button,
                onClickLabel = stringResource(R.string.wear_action_refresh),
            )
            .padding(top = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.wear_tap_to_refresh, sourceLabel, ageLabel),
            fontSize = 9.sp,
            color = WearTextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun freshnessLabel(updatedAtMs: Long): String {
    if (updatedAtMs <= 0L) return stringResource(R.string.wear_sync_never)
    val ageMs = max(0L, System.currentTimeMillis() - updatedAtMs)
    val minutes = (ageMs / 60_000L).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.wear_sync_just_now)
        minutes < 60 -> stringResource(R.string.wear_sync_minutes_ago, minutes)
        minutes < 1440 -> stringResource(R.string.wear_sync_hours_ago, minutes / 60)
        else -> stringResource(R.string.wear_sync_days_ago, minutes / 1440)
    }
}

@Composable
private fun AlertBanner(count: Int, topEvent: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WearAlertAccent.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .border(1.dp, WearAlertAccent.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .clickable(
                onClick = onTap,
                role = Role.Button,
                onClickLabel = stringResource(R.string.wear_action_view_alerts),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count == 1) "\u26A0 $topEvent" else "\u26A0 ${stringResource(R.string.wear_alert_count, count)}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = WearAlertAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailRow(state: WearUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DetailChip(emoji = "\uD83D\uDCA7", value = "${state.humidity}%")
        DetailChip(
            emoji = "\uD83D\uDCA8",
            value = "${WearUnitFormatter.displayWind(state.windSpeed, state.windUnit)} ${WearUnitFormatter.windLabel(state.windUnit)}",
        )
        DetailChip(emoji = "\u2600\uFE0F", value = "UV${state.uvIndex}")
        // AQI sentinel is -1; 0 is a real (excellent) reading.
        if (state.aqi >= 0) {
            DetailChip(emoji = aqiEmoji(state.aqiLabel), value = "${state.aqi}")
        } else {
            DetailChip(emoji = "\uD83C\uDF27\uFE0F", value = "${state.precipChance}%")
        }
    }
}

@Composable
private fun DetailChip(emoji: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = emoji, fontSize = 10.sp)
        Text(text = value, fontSize = 10.sp, color = WearTextSecondary)
    }
}

private fun aqiEmoji(level: String): String {
    // Phone sends AqiLevel.label strings ("Good", "Moderate",
    // "Unhealthy for Sensitive Groups", "Unhealthy", "Very Unhealthy",
    // "Hazardous"). Use Locale.ROOT so Turkish-locale watches don't
    // mangle the I in "Unhealthy"/"Sensitive" via dotless-i.
    val lower = level.lowercase(java.util.Locale.ROOT)
    return when {
        lower.startsWith("good") -> "\uD83D\uDFE2"           // green circle
        lower.startsWith("very unhealthy") -> "\uD83D\uDFE3" // purple circle (check before plain "unhealthy")
        lower.startsWith("unhealthy for sensitive") -> "\uD83D\uDFE0" // orange circle
        lower.startsWith("unhealthy") -> "\uD83D\uDD34"      // red circle
        lower.startsWith("moderate") -> "\uD83D\uDFE1"       // yellow circle
        lower.startsWith("hazardous") -> "\uD83D\uDFE4"      // brown circle
        else -> "\uD83C\uDF2C\uFE0F"                         // wind face
    }
}
