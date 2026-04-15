package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.WearUiState
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository

private val TextPrimary = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFB0B8CC)
private val TextTertiary = Color(0xFF7A839E)
private val BlueAccent = Color(0xFF4A90D9)
private val AlertBannerBg = Color(0x33FF5722)
private val AlertBannerText = Color(0xFFFF7043)

@Composable
fun CurrentScreen(
    state: WearUiState,
    onHourlyTap: () -> Unit = {},
    onDailyTap: () -> Unit = {},
    onAlertsTap: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isLoading) {
            Text("Loading...", fontSize = 14.sp, color = TextSecondary)
        } else if (state.error != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(
                    state.error,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap to retry",
                    fontSize = 12.sp,
                    color = BlueAccent,
                    modifier = Modifier.clickable { onRefresh() },
                )
            }
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
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Alert banner (if active alerts)
        if (state.alerts.isNotEmpty()) {
            AlertBanner(
                count = state.alerts.size,
                topEvent = state.alerts.first().event,
                onTap = onAlertsTap,
            )
            Spacer(Modifier.height(4.dp))
        }

        // Weather emoji
        Text(
            text = WearWeatherRepository.wmoEmoji(state.weatherCode, state.isDay),
            fontSize = 28.sp,
        )

        // Temperature
        Text(
            text = "${state.temperature}\u00B0",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        // Condition
        Text(
            text = state.condition,
            fontSize = 12.sp,
            color = TextSecondary,
        )

        // High / Low
        Text(
            text = "H:${state.high}\u00B0  L:${state.low}\u00B0",
            fontSize = 11.sp,
            color = TextTertiary,
        )
        Spacer(Modifier.height(4.dp))

        // Detail chips
        DetailRow(state)
        Spacer(Modifier.height(3.dp))

        // Location
        Text(
            text = state.locationName,
            fontSize = 10.sp,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))

        // Navigation links
        NavLinks(
            hasDaily = state.daily.isNotEmpty(),
            onHourlyTap = onHourlyTap,
            onDailyTap = onDailyTap,
        )
    }
}

@Composable
private fun AlertBanner(count: Int, topEvent: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlertBannerBg, RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count == 1) "\u26A0 $topEvent" else "\u26A0 $count alerts",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = AlertBannerText,
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
private fun NavLinks(hasDaily: Boolean, onHourlyTap: () -> Unit, onDailyTap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            text = "Hourly \u203A",
            fontSize = 10.sp,
            color = BlueAccent,
            modifier = Modifier.clickable(onClick = onHourlyTap),
        )
        if (hasDaily) {
            Text(
                text = "7-Day \u203A",
                fontSize = 10.sp,
                color = BlueAccent,
                modifier = Modifier.clickable(onClick = onDailyTap),
            )
        }
    }
}

@Composable
private fun DetailChip(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 10.sp)
        Text(text = value, fontSize = 10.sp, color = TextSecondary)
    }
}

private fun aqiEmoji(level: String): String = when (level.lowercase()) {
    "good" -> "\uD83D\uDFE2"           // green circle
    "moderate" -> "\uD83D\uDFE1"       // yellow circle
    "unhealthy for sensitive" -> "\uD83D\uDFE0" // orange circle
    "unhealthy" -> "\uD83D\uDD34"      // red circle
    "very unhealthy" -> "\uD83D\uDFE3" // purple circle
    "hazardous" -> "\uD83D\uDFE4"      // brown circle
    else -> "\uD83C\uDF2C\uFE0F"       // wind face
}
