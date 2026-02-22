package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.HourlyAqi
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

@Composable
fun AqiCard(
    data: AirQualityData,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NimbusCardBg)
            .padding(16.dp),
    ) {
        Text(
            "Air Quality",
            style = MaterialTheme.typography.titleMedium,
            color = NimbusTextPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Gauge + info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // AQI arc gauge
            AqiGauge(
                aqi = data.usAqi,
                level = data.aqiLevel,
                modifier = Modifier.size(100.dp),
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

        // Pollutant breakdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PollutantChip("PM2.5", "%.1f".format(data.pm25), "\u00B5g/m\u00B3")
            PollutantChip("PM10", "%.1f".format(data.pm10), "\u00B5g/m\u00B3")
            PollutantChip("O\u2083", "%.1f".format(data.ozone), "\u00B5g/m\u00B3")
            PollutantChip("NO\u2082", "%.1f".format(data.nitrogenDioxide), "\u00B5g/m\u00B3")
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
    }
}

@Composable
private fun AqiGauge(aqi: Int, level: AqiLevel, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 10.dp.toPx()
            val padding = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(padding, padding)

            // Background arc (220 degrees, starting from bottom-left)
            drawArc(
                color = Color(0xFF2A2A3E),
                startAngle = 160f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Filled arc (proportional to AQI, max 500)
            val sweep = (aqi.coerceIn(0, 500) / 500f) * 220f
            drawArc(
                color = level.color,
                startAngle = 160f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$aqi",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                ),
                color = level.color,
            )
            Text(
                "US AQI",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun PollutantChip(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
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
