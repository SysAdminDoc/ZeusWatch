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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.MoonPhase
import com.sysadmindoc.nimbus.ui.theme.NimbusMoonBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.labelRes
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
    val s = LocalUnitSettings.current
    val sunriseLabel = sunrise?.let { WeatherFormatter.formatTime(it, s) }
    val sunsetLabel = sunset?.let { WeatherFormatter.formatTime(it, s) }
    val moonriseLabel = astronomy.moonrise?.let { WeatherFormatter.formatTime(it, s) }
    val moonsetLabel = astronomy.moonset?.let { WeatherFormatter.formatTime(it, s) }
    val moonPhaseLabel = stringResource(astronomy.moonPhase.labelRes)
    val sunriseSummary = if (sunriseLabel != null) stringResource(R.string.astronomy_semantics_sunrise, sunriseLabel) else null
    val sunsetSummary = if (sunsetLabel != null) stringResource(R.string.astronomy_semantics_sunset, sunsetLabel) else null
    val dayLengthSummary = if (astronomy.dayLength != null) {
        stringResource(R.string.astronomy_semantics_day_length, astronomy.dayLength)
    } else null
    val moonSummary = stringResource(
        R.string.astronomy_semantics_moon,
        moonPhaseLabel,
        astronomy.moonIllumination.toInt(),
    )
    val moonriseSummary = if (moonriseLabel != null) stringResource(R.string.astronomy_semantics_moonrise, moonriseLabel) else null
    val moonsetSummary = if (moonsetLabel != null) stringResource(R.string.astronomy_semantics_moonset, moonsetLabel) else null
    val astroSummary = listOfNotNull(
        stringResource(R.string.astronomy_semantics_intro),
        sunriseSummary,
        sunsetSummary,
        dayLengthSummary,
        moonSummary,
        moonriseSummary,
        moonsetSummary,
    ).joinToString(separator = " ")
    WeatherCard(
        titleRes = R.string.card_title_astronomy,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = astroSummary
        },
    ) {
        // Sunrise / Sunset row
        if (sunrise != null || sunset != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AstroDetail(R.string.sunrise, WeatherFormatter.formatTime(sunrise, s))
                AstroDetail(R.string.sunset, WeatherFormatter.formatTime(sunset, s))
                astronomy.dayLength?.let { AstroDetail(R.string.astronomy_day_length, it) }
            }

            // Sunrise/sunset countdown
            val countdown = sunCountdown(sunrise, sunset, referenceTime)
            val countdownText = if (countdown != null) {
                stringResource(countdown.labelRes, countdown.hours, countdown.minutes)
            } else null
            if (countdownText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Sun + moon arc showing current positions in sky
            SunArc(
                sunrise = sunrise,
                sunset = sunset,
                moonrise = astronomy.moonrise,
                moonset = astronomy.moonset,
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
                // Southern-hemisphere observers see the moon rotated 180° relative
                // to the northern view — terminator on the opposite side, "upside
                // down" relative to maps printed for northern audiences (issue #16).
                southernHemisphere = (astronomy.observerLatitude ?: 0.0) < 0.0,
                modifier = Modifier.size(72.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    moonPhaseLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NimbusMoonBlue,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.moon_illumination_value, astronomy.moonIllumination.toInt()),
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
            AstroDetail(R.string.astronomy_moonrise, WeatherFormatter.formatTime(astronomy.moonrise, s))
            AstroDetail(R.string.astronomy_moonset, WeatherFormatter.formatTime(astronomy.moonset, s))
        }
    }
}

@Composable
private fun MoonCanvas(
    illumination: Double,
    phase: MoonPhase,
    southernHemisphere: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.drawWithCache {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val illFraction = (illumination / 100.0).coerceIn(0.0, 1.0)
            val isWaxing = (phase.ordinal <= MoonPhase.FULL_MOON.ordinal) xor southernHemisphere
            val rimStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())

            val moonPath = if (illFraction > 0.01) {
                Path().apply {
                    val terminatorX = ((1.0 - 2.0 * illFraction) * radius).toFloat()
                    val steps = 100
                    moveTo(center.x, center.y - radius)
                    for (i in 0..steps) {
                        val angle = PI * i / steps
                        val y = center.y - radius * cos(angle).toFloat()
                        val edgeX = if (isWaxing) {
                            center.x + radius * sin(angle).toFloat()
                        } else {
                            center.x - radius * sin(angle).toFloat()
                        }
                        if (i == 0) moveTo(edgeX, y) else lineTo(edgeX, y)
                    }
                    for (i in steps downTo 0) {
                        val angle = PI * i / steps
                        val y = center.y - radius * cos(angle).toFloat()
                        val tx = if (isWaxing) {
                            center.x + terminatorX * sin(angle).toFloat()
                        } else {
                            center.x - terminatorX * sin(angle).toFloat()
                        }
                        lineTo(tx, y)
                    }
                    close()
                }
            } else null

            onDrawBehind {
                drawCircle(color = Color(0xFF1A1A2E), radius = radius, center = center)
                moonPath?.let { drawPath(it, color = NimbusMoonBlue.copy(alpha = 0.85f), style = Fill) }
                drawCircle(color = NimbusMoonBlue.copy(alpha = 0.15f), radius = radius, center = center, style = rimStroke)
            }
        },
    ) { }
}

@Composable
private fun AstroDetail(labelRes: Int, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = NimbusTextPrimary,
        )
    }
}

private data class SunCountdown(
    val labelRes: Int,
    val hours: Long,
    val minutes: Long,
)

private fun sunCountdown(
    sunrise: String?,
    sunset: String?,
    referenceTime: java.time.LocalDateTime? = null,
): SunCountdown? {
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
                if (h > 0) {
                    SunCountdown(R.string.sunrise_countdown_hours, h, m)
                } else {
                    SunCountdown(R.string.sunrise_countdown_minutes, 0, m)
                }
            }
            now.isBefore(set) -> {
                val dur = java.time.Duration.between(now, set)
                val h = dur.toHours()
                val m = dur.toMinutes() % 60
                if (h > 0) {
                    SunCountdown(R.string.sunset_countdown_hours, h, m)
                } else {
                    SunCountdown(R.string.sunset_countdown_minutes, 0, m)
                }
            }
            else -> {
                // After sunset — show time until tomorrow's sunrise
                val tomorrowRise = rise.plusDays(1)
                val dur = java.time.Duration.between(now, tomorrowRise)
                val h = dur.toHours()
                val m = dur.toMinutes() % 60
                if (h > 0) {
                    SunCountdown(R.string.sunrise_countdown_hours, h, m)
                } else {
                    SunCountdown(R.string.sunrise_countdown_minutes, 0, m)
                }
            }
        }
    } catch (_: Exception) { null }
}
