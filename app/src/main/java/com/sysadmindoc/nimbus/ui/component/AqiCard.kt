package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.HourlyAqi
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

@Composable
fun AqiCard(
    data: AirQualityData,
    modifier: Modifier = Modifier,
) {
    WeatherCard(title = "Air Quality", modifier = modifier) {

        // Gauge + info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // AQI arc gauge (uses gradient version from AqiGauge.kt)
            AqiGauge(
                aqi = data.usAqi,
                level = data.aqiLevel,
                size = 100.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Level label + advice
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    data.aqiLevel.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = data.aqiLevel.color,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    data.aqiLevel.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "EU AQI: ${data.europeanAqi}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pollutant breakdown — highlight the dominant contributor
        // Normalized scores: higher = worse relative to EPA breakpoints
        val pollutantScores = listOf(
            "PM2.5" to data.pm25 / 35.0,
            "PM10" to data.pm10 / 154.0,
            "O3" to data.ozone / 100.0,
            "NO2" to data.nitrogenDioxide / 100.0,
        )
        val worstPollutant = pollutantScores.maxByOrNull { it.second }?.first

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PollutantChip("PM2.5", "%.1f".format(data.pm25), "\u00B5g/m\u00B3", isWorst = worstPollutant == "PM2.5")
            PollutantChip("PM10", "%.1f".format(data.pm10), "\u00B5g/m\u00B3", isWorst = worstPollutant == "PM10")
            PollutantChip("O\u2083", "%.1f".format(data.ozone), "\u00B5g/m\u00B3", isWorst = worstPollutant == "O3")
            PollutantChip("NO\u2082", "%.1f".format(data.nitrogenDioxide), "\u00B5g/m\u00B3", isWorst = worstPollutant == "NO2")
        }

        // Hourly AQI trend
        if (data.hourlyAqi.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "24h Trend",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(data.hourlyAqi) { hour ->
                    HourlyAqiChip(hour)
                }
            }
        }

        // 5-day daily AQI forecast
        if (data.dailyAqi.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "5-Day Forecast",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                data.dailyAqi.forEach { day ->
                    DailyAqiBar(day)
                }
            }
        }
    }
}

@Composable
private fun PollutantChip(label: String, value: String, unit: String, isWorst: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (isWorst) Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1AFF9800))
            .padding(horizontal = 6.dp, vertical = 4.dp)
        else Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isWorst) Color(0xFFFF9800) else NimbusTextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = NimbusTextPrimary)
        Text(unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = NimbusTextTertiary)
    }
}

@Composable
private fun HourlyAqiChip(hour: HourlyAqi) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(hour.level.color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(hour.hour, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = NimbusTextTertiary)
        Text("${hour.aqi}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = hour.level.color)
    }
}

@Composable
private fun DailyAqiBar(day: com.sysadmindoc.nimbus.data.model.DailyAqi) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp),
    ) {
        Text(day.dayLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = NimbusTextTertiary)
        Spacer(modifier = Modifier.height(4.dp))
        // Vertical bar proportional to AQI (max 300 for scale)
        val barHeight = (day.maxAqi.coerceIn(0, 300) / 300f * 40f).coerceAtLeast(4f)
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(barHeight.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(day.level.color.copy(alpha = 0.6f)),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text("${day.maxAqi}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = day.level.color)
    }
}
