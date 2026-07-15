package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusMoonBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusSunYellow
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Enhanced ephemeris arc visualization showing sun AND moon paths.
 * Sun: golden arc from sunrise to sunset with glowing position dot.
 * Moon: silver arc (when moonrise/moonset data is available).
 * Twilight zones: dawn/dusk shaded regions before sunrise and after sunset.
 * Inspired by breezy-weather's EphemerisChart.
 */
@Composable
fun SunArc(
    sunrise: String?,
    sunset: String?,
    modifier: Modifier = Modifier,
    moonrise: String? = null,
    moonset: String? = null,
    referenceTime: LocalDateTime? = null,
) {
    if (sunrise == null || sunset == null) return

    val settings = LocalUnitSettings.current
    val now = referenceTime ?: LocalDateTime.now()
    val times = parseSunArcTimes(sunrise, sunset, moonrise, moonset) ?: return
    val totalMinutes = Duration.between(times.sunrise, times.sunset).toMinutes().toFloat()
    if (totalMinutes <= 0f) return // Guard: bad data (sunrise >= sunset)
    val elapsedMinutes = Duration.between(times.sunrise, now).toMinutes().toFloat()
    val sunProgress = (elapsedMinutes / totalMinutes).coerceIn(0f, 1f)
    val isDaylight = now.isAfter(times.sunrise) && now.isBefore(times.sunset)
    val twilightLabel = twilightLabel(times.sunrise, times.sunset, now)?.let { stringResource(it) }
    val moonState = moonArcState(times.moonrise, times.moonset, now)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 10.sp)
    val twilightColor = Color(0xFF1A237E).copy(alpha = 0.25f)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val sunriseLabel = WeatherFormatter.formatTime(sunrise, settings)
    val sunsetLabel = WeatherFormatter.formatTime(sunset, settings)
    val semanticSummary = sunArcSemanticSummary(
        sunriseLabel = sunriseLabel,
        sunsetLabel = sunsetLabel,
        sunStateLabel = stringResource(sunStateRes(times.sunrise, times.sunset, now)),
        moonrise = moonrise,
        moonset = moonset,
        hasMoon = moonState != null,
    )

    Canvas(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = semanticSummary
            }
            .fillMaxWidth()
            .height(if (moonState != null) 100.dp else 70.dp)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val layout = SunArcLayout(
                    width = w,
                    horizonY = h * 0.80f,
                    arcRadius = w * 0.42f,
                    centerX = w / 2f,
                    isRtl = isRtl,
                )

                onDrawBehind {
                    drawTwilightZones(layout, totalMinutes, twilightColor)
                    drawHorizonLine(layout)
                    drawSunTrack(layout, sunProgress, isDaylight)
                    drawSunPosition(layout, sunProgress, isDaylight)
                    drawMoonTrack(layout, moonState)
                    drawSunArcLabels(
                        layout = layout,
                        textMeasurer = textMeasurer,
                        labelStyle = labelStyle,
                        sunriseLabel = sunriseLabel,
                        sunsetLabel = sunsetLabel,
                        twilightLabel = twilightLabel,
                    )
                }
            },
    ) { }
}

private data class SunArcTimes(
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val moonrise: LocalDateTime?,
    val moonset: LocalDateTime?,
)

internal data class MoonArcState(
    val progress: Float,
    val isUp: Boolean,
)

private data class SunArcLayout(
    val width: Float,
    val horizonY: Float,
    val arcRadius: Float,
    val centerX: Float,
    val isRtl: Boolean,
)

private fun parseSunArcTimes(
    sunrise: String,
    sunset: String,
    moonrise: String?,
    moonset: String?,
): SunArcTimes? {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    return SunArcTimes(
        sunrise = parseLocalDateTime(sunrise, fmt) ?: return null,
        sunset = parseLocalDateTime(sunset, fmt) ?: return null,
        moonrise = moonrise?.let { parseLocalDateTime(it, fmt) },
        moonset = moonset?.let { parseLocalDateTime(it, fmt) },
    )
}

private fun parseLocalDateTime(value: String, formatter: DateTimeFormatter): LocalDateTime? =
    try {
        LocalDateTime.parse(value, formatter)
    } catch (_: Exception) {
        null
    }

private fun twilightLabel(
    sunrise: LocalDateTime,
    sunset: LocalDateTime,
    now: LocalDateTime,
): Int? {
    val dawnStart = sunrise.minusMinutes(30)
    val duskEnd = sunset.plusMinutes(30)
    return when {
        now.isAfter(dawnStart) && now.isBefore(sunrise) -> R.string.sun_arc_dawn
        now.isAfter(sunset) && now.isBefore(duskEnd) -> R.string.sun_arc_dusk
        else -> null
    }
}

internal fun moonArcState(
    moonrise: LocalDateTime?,
    moonset: LocalDateTime?,
    now: LocalDateTime,
): MoonArcState? {
    if (moonrise == null || moonset == null) return null
    // Providers label both events with the same calendar date, so roughly half
    // the lunar month the moon "sets" before it rises that day (an overnight
    // transit). Roll the set forward a day for the arc math; display labels
    // keep the producer's timestamps.
    val arcMoonset = if (moonset.isAfter(moonrise)) moonset else moonset.plusDays(1)
    val moonTotal = Duration.between(moonrise, arcMoonset).toMinutes().toFloat()
    if (moonTotal <= 0f) return null
    val moonElapsed = Duration.between(moonrise, now).toMinutes().toFloat()
    return MoonArcState(
        progress = (moonElapsed / moonTotal).coerceIn(0f, 1f),
        isUp = now.isAfter(moonrise) && now.isBefore(arcMoonset),
    )
}

@Composable
private fun sunArcSemanticSummary(
    sunriseLabel: String,
    sunsetLabel: String,
    sunStateLabel: String,
    moonrise: String?,
    moonset: String?,
    hasMoon: Boolean,
): String {
    val settings = LocalUnitSettings.current
    return if (hasMoon) {
        stringResource(
            R.string.sun_arc_semantics_with_moon,
            sunriseLabel,
            sunsetLabel,
            sunStateLabel,
            WeatherFormatter.formatTime(moonrise, settings),
            WeatherFormatter.formatTime(moonset, settings),
        )
    } else {
        stringResource(R.string.sun_arc_semantics, sunriseLabel, sunsetLabel, sunStateLabel)
    }
}

private fun DrawScope.drawTwilightZones(
    layout: SunArcLayout,
    totalMinutes: Float,
    twilightColor: Color,
) {
    val dawnSweep = ((30f / totalMinutes).coerceIn(0f, 0.15f)) * 180f
    val twilightStroke = Stroke(width = 14f, cap = StrokeCap.Round)
    val arcSize = Size(layout.arcRadius * 2, layout.arcRadius * 2)
    val topLeft = Offset(layout.centerX - layout.arcRadius, layout.horizonY - layout.arcRadius)
    if (layout.isRtl) {
        drawArc(twilightColor, 0f, -dawnSweep, false, topLeft, arcSize, style = twilightStroke)
        drawArc(twilightColor, 180f, dawnSweep, false, topLeft, arcSize, style = twilightStroke)
    } else {
        drawArc(twilightColor, 180f, dawnSweep, false, topLeft, arcSize, style = twilightStroke)
        drawArc(twilightColor, -dawnSweep, dawnSweep, false, topLeft, arcSize, style = twilightStroke)
    }
}

private fun DrawScope.drawHorizonLine(layout: SunArcLayout) {
    drawLine(
        color = NimbusTextTertiary.copy(alpha = 0.3f),
        start = Offset(layout.width * 0.05f, layout.horizonY),
        end = Offset(layout.width * 0.95f, layout.horizonY),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
    )
}

private fun DrawScope.drawSunTrack(
    layout: SunArcLayout,
    sunProgress: Float,
    isDaylight: Boolean,
) {
    val arcSize = Size(layout.arcRadius * 2, layout.arcRadius * 2)
    val topLeft = Offset(layout.centerX - layout.arcRadius, layout.horizonY - layout.arcRadius)
    drawArc(
        color = NimbusSunYellow.copy(alpha = 0.2f),
        startAngle = if (layout.isRtl) 0f else 180f,
        sweepAngle = if (layout.isRtl) -180f else 180f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(
            width = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
        ),
    )
    if (isDaylight) {
        drawArc(
            color = NimbusSunYellow.copy(alpha = 0.6f),
            startAngle = if (layout.isRtl) 0f else 180f,
            sweepAngle = if (layout.isRtl) sunProgress * -180f else sunProgress * 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSunPosition(
    layout: SunArcLayout,
    sunProgress: Float,
    isDaylight: Boolean,
) {
    if (!isDaylight) return
    val angle = PI.toFloat() * (1f - sunProgress)
    val center = Offset(
        x = rtlCanvasX(layout.centerX + layout.arcRadius * cos(angle), layout.width, layout.isRtl),
        y = layout.horizonY - layout.arcRadius * sin(angle),
    )
    drawCircle(NimbusSunYellow.copy(alpha = 0.15f), radius = 16f, center = center)
    drawCircle(NimbusSunYellow.copy(alpha = 0.25f), radius = 10f, center = center)
    drawCircle(NimbusSunYellow, radius = 6f, center = center)
}

private fun DrawScope.drawMoonTrack(
    layout: SunArcLayout,
    moonState: MoonArcState?,
) {
    if (moonState == null) return
    val moonArcRadius = layout.arcRadius * 0.75f
    drawArc(
        color = NimbusMoonBlue.copy(alpha = 0.15f),
        startAngle = if (layout.isRtl) 0f else 180f,
        sweepAngle = if (layout.isRtl) -180f else 180f,
        useCenter = false,
        topLeft = Offset(layout.centerX - moonArcRadius, layout.horizonY - moonArcRadius),
        size = Size(moonArcRadius * 2, moonArcRadius * 2),
        style = Stroke(
            width = 1.5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
        ),
    )
    if (moonState.isUp) {
        val moonAngle = PI.toFloat() * (1f - moonState.progress)
        val center = Offset(
            x = rtlCanvasX(layout.centerX + moonArcRadius * cos(moonAngle), layout.width, layout.isRtl),
            y = layout.horizonY - moonArcRadius * sin(moonAngle),
        )
        drawCircle(NimbusMoonBlue.copy(alpha = 0.2f), radius = 10f, center = center)
        drawCircle(NimbusMoonBlue, radius = 5f, center = center)
    }
}

private fun DrawScope.drawSunArcLabels(
    layout: SunArcLayout,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    sunriseLabel: String,
    sunsetLabel: String,
    twilightLabel: String?,
) {
    val riseM = textMeasurer.measure(sunriseLabel, labelStyle)
    drawText(
        riseM,
        topLeft = Offset(
            rtlCanvasRectLeft(layout.width * 0.05f, riseM.size.width.toFloat(), layout.width, layout.isRtl),
            layout.horizonY + 4f,
        ),
    )

    val setM = textMeasurer.measure(sunsetLabel, labelStyle)
    drawText(
        setM,
        topLeft = Offset(
            rtlCanvasRectLeft(
                left = layout.width * 0.95f - setM.size.width,
                rectWidth = setM.size.width.toFloat(),
                canvasWidth = layout.width,
                isRtl = layout.isRtl,
            ),
            layout.horizonY + 4f,
        ),
    )

    if (twilightLabel != null) {
        val label = textMeasurer.measure(
            text = twilightLabel,
            style = TextStyle(color = NimbusBlueAccent.copy(alpha = 0.6f), fontSize = 9.sp),
        )
        drawText(
            label,
            topLeft = Offset(layout.centerX - label.size.width / 2, layout.horizonY - layout.arcRadius - 14f),
        )
    }
}

private fun sunStateRes(
    sunrise: LocalDateTime,
    sunset: LocalDateTime,
    now: LocalDateTime,
): Int = when {
    now.isBefore(sunrise) -> R.string.sun_arc_state_before_sunrise
    now.isBefore(sunset) -> R.string.sun_arc_state_daylight
    else -> R.string.sun_arc_state_after_sunset
}
