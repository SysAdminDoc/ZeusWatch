package com.sysadmindoc.nimbus.wear.ui

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

@Composable
fun CurrentScreen(
    state: WearUiState,
    onHourlyTap: () -> Unit = {},
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
            WeatherContent(state, onHourlyTap)
        }
    }
}

@Composable
private fun WeatherContent(state: WearUiState, onHourlyTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Weather emoji
        Text(
            text = WearWeatherRepository.wmoEmoji(state.weatherCode, state.isDay),
            fontSize = 30.sp,
        )

        // Temperature
        Text(
            text = "${state.temperature}\u00B0",
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        // Condition
        Text(
            text = state.condition,
            fontSize = 13.sp,
            color = TextSecondary,
        )
        Spacer(Modifier.height(1.dp))

        // High / Low
        Text(
            text = "H:${state.high}\u00B0  L:${state.low}\u00B0",
            fontSize = 12.sp,
            color = TextTertiary,
        )
        Spacer(Modifier.height(6.dp))

        // Detail chips
        DetailRow(state)
        Spacer(Modifier.height(4.dp))

        // Location
        Text(
            text = state.locationName,
            fontSize = 11.sp,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))

        // Hourly nav
        Text(
            text = "Hourly \u203A",
            fontSize = 11.sp,
            color = BlueAccent,
            modifier = Modifier.clickable(onClick = onHourlyTap),
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
        DetailChip(emoji = "\uD83C\uDF27\uFE0F", value = "${state.precipChance}%")
    }
}

@Composable
private fun DetailChip(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 10.sp)
        Text(text = value, fontSize = 10.sp, color = TextSecondary)
    }
}
