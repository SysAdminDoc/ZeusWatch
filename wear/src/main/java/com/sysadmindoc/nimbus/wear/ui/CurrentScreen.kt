package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.WearUiState
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

@Composable
fun CurrentScreen(
    state: WearUiState,
    modifier: Modifier = Modifier,
    onHourlyTap: () -> Unit = {},
    onDailyTap: () -> Unit = {},
    onAlertsTap: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isLoading) {
            WearStateCard(
                title = "Refreshing forecast",
                message = "Pulling the latest weather for your current place.",
                icon = "\uD83C\uDF26\uFE0F",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else if (state.error != null) {
            WearStateCard(
                title = "Forecast unavailable",
                message = state.error,
                icon = "\u26A0\uFE0F",
                actionLabel = "Retry",
                onAction = onRefresh,
                accent = WearAlertAccent,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            WeatherContent(state, onHourlyTap, onDailyTap, onAlertsTap)
        }
    }
}

@Composable
private fun WeatherContent(
    state: WearUiState,
    onHourlyTap: () -> Unit,
    onDailyTap: () -> Unit,
    onAlertsTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                subtitle = if (state.isDay) "Daylight" else "Overnight",
            )
            Text(
                text = WearWeatherRepository.wmoEmoji(state.weatherCode, state.isDay),
                fontSize = 30.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "${state.temperature}\u00B0",
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
                text = "High ${state.high}\u00B0 • Low ${state.low}\u00B0",
                fontSize = 10.sp,
                color = WearTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        WearPanel(modifier = Modifier.fillMaxWidth()) {
            WearMiniPill(
                text = "Right now",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            DetailRow(state)
        }

        WearLinkRow(
            primary = "Hourly ›" to onHourlyTap,
            secondary = if (state.daily.isNotEmpty()) "7-Day ›" to onDailyTap else null,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AlertBanner(count: Int, topEvent: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WearAlertAccent.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count == 1) "\u26A0 $topEvent" else "\u26A0 $count alerts",
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
        DetailChip(emoji = "\uD83D\uDCA8", value = "${state.windSpeed}")
        DetailChip(emoji = "\u2600\uFE0F", value = "UV${state.uvIndex}")
        if (state.aqi > 0) {
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
