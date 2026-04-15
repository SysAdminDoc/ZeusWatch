package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.MoonPhase
import com.sysadmindoc.nimbus.ui.theme.NimbusMoonBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MoonPhaseCard(
    astronomy: AstronomyData,
    modifier: Modifier = Modifier,
    sunrise: String? = null,
    sunset: String? = null,
    referenceTime: java.time.LocalDateTime? = null,
) {
    WeatherCard(title = "Astronomy", modifier = modifier) {
        // Sunrise / Sunset row
        if (sunrise != null || sunset != null) {
            val s = LocalUnitSettings.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AstroDetail("Sunrise", com.sysadmindoc.nimbus.util.WeatherFormatter.formatTime(sunrise, s))
                AstroDetail("Sunset", com.sysadmindoc.nimbus.util.WeatherFormatter.formatTime(sunset, s))
                astronomy.dayLength?.let { AstroDetail("Day Length", it) }
            }

            // Sunrise/sunset countdown
            val countdown = sunCountdown(sunrise, sunset, referenceTime)
            if (countdown != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Sun arc showing current position in sky
            SunArc(
                sunrise = sunrise,
                sunset = sunset,
                referenceTime = referenceTime,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Moon visualization
            MoonCanvas(
                illumination = astronomy.moonIllumination,
                phase = astronomy.moonPhase,
                modifier = Modifier.size(72.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    astronomy.moonPhase.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NimbusMoonBlue,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Illumination: ${astronomy.moonIllumination.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Moon times row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AstroDetail("Moonrise", astronomy.moonrise ?: "--")
            AstroDetail("Moonset", astronomy.moonset ?: "--")
        }
    }
}

@Composable
private fun MoonCanvas(
    illumination: Double,
    phase: MoonPhase,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Dark background circle (the "dark side")
        drawCircle(
            color = Color(0xFF1A1A2E),
            radius = radius,
            center = center,
        )

        // Illuminated portion
        val illFraction = (illumination / 100.0).coerceIn(0.0, 1.0)
        val isWaxing = phase.ordinal <= MoonPhase.FULL_MOON.ordinal

        // Build illuminated path using bezier approximation
        val path = Path()
        val steps = 100

        if (illFraction > 0.01) {
            // Right or left half illuminated (waxing = right, waning = left)
            // The terminator curve is an ellipse
            val terminatorX = ((1.0 - 2.0 * illFraction) * radius).toFloat()

            path.moveTo(center.x, center.y - radius)
            for (i in 0..steps) {
                val angle = PI * i / steps
                val y = center.y - radius * cos(angle).toFloat()

                // Edge of moon (always semicircle)
                val edgeX = if (isWaxing) {
                    center.x + radius * sin(angle).toFloat()
                } else {
                    center.x - radius * sin(angle).toFloat()
                }

                if (i == 0) path.moveTo(edgeX, y)
                else path.lineTo(edgeX, y)
            }

            // Terminator curve back
            for (i in steps downTo 0) {
                val angle = PI * i / steps
                val y = center.y - radius * cos(angle).toFloat()
                val tx = if (isWaxing) {
                    center.x + terminatorX * sin(angle).toFloat()
                } else {
                    center.x - terminatorX * sin(angle).toFloat()
                }
                path.lineTo(tx, y)
            }

            path.close()

            drawPath(
                path = path,
                color = NimbusMoonBlue.copy(alpha = 0.85f),
                style = Fill,
            )
        }

        // Subtle rim highlight
        drawCircle(
            color = NimbusMoonBlue.copy(alpha = 0.15f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun AstroDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = NimbusTextPrimary,
        )
    }
}

private fun sunCountdown(
    sunrise: String?,
    sunset: String?,
    referenceTime: java.time.LocalDateTime? = null,
): String? {
    if (sunrise == null || sunset == null) return null
    return try {
        val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val now = referenceTime ?: java.time.LocalDateTime.now()
        val rise = java.time.LocalDateTime.parse(sunrise, fmt)
        val set = java.time.LocalDateTime.parse(sunset, fmt)

        when {
            now.isBefore(rise) -> {
                val dur = java.time.Duration.between(now, rise)
                val h = dur.toHours()
                val m = dur.toMinutes() % 60
                if (h > 0) "Sunrise in ${h}h ${m}m" else "Sunrise in ${m}m"
            }
            now.isBefore(set) -> {
                val dur = java.time.Duration.between(now, set)
                val h = dur.toHours()
                val m = dur.toMinutes() % 60
                if (h > 0) "Sunset in ${h}h ${m}m" else "Sunset in ${m}m"
            }
            else -> {
                // After sunset — show time until tomorrow's sunrise
                val tomorrowRise = rise.plusDays(1)
                val dur = java.time.Duration.between(now, tomorrowRise)
                val h = dur.toHours()
                val m = dur.toMinutes() % 60
                if (h > 0) "Sunrise in ${h}h ${m}m" else "Sunrise in ${m}m"
            }
        }
    } catch (_: Exception) { null }
}
