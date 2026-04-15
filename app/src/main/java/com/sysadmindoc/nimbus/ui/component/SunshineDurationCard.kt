package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusSunYellow
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Sunshine duration card showing today's total sunshine hours
 * with a visual progress ring based on daylight hours.
 */
@Composable
fun SunshineDurationCard(
    sunshineDurationSeconds: Double,
    modifier: Modifier = Modifier,
    dayLengthMinutes: Long? = null,
) {
    val sunshineText = WeatherFormatter.formatSunshineDuration(sunshineDurationSeconds)
    val sunshineHours = sunshineDurationSeconds / 3600.0
    val maxHours = (dayLengthMinutes?.toDouble() ?: (14 * 60.0)) / 60.0
    val progress = (sunshineHours / maxHours).coerceIn(0.0, 1.0).toFloat()

    WeatherCard(
        title = "Sunshine",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Progress ring
            Canvas(modifier = Modifier.size(56.dp)) {
                val strokeWidth = 6f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                drawArc(
                    color = NimbusTextTertiary.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = NimbusSunYellow,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Icon(Icons.Outlined.WbSunny, null, tint = NimbusSunYellow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        sunshineText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = NimbusSunYellow,
                    )
                }
                Text(
                    "of sunshine today",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }
    }
}
