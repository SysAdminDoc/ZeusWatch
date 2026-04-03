package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusMoonBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusSunYellow
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
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
) {
    if (sunrise == null || sunset == null) return

    val settings = LocalUnitSettings.current
    val now = LocalDateTime.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val riseTime = try { LocalDateTime.parse(sunrise, fmt) } catch (_: Exception) { return }
    val setTime = try { LocalDateTime.parse(sunset, fmt) } catch (_: Exception) { return }

    // Moon times (optional)
    val moonRiseTime = moonrise?.let { try { LocalDateTime.parse(it, fmt) } catch (_: Exception) { null } }
    val moonSetTime = moonset?.let { try { LocalDateTime.parse(it, fmt) } catch (_: Exception) { null } }

    // Sun position (0 = sunrise, 1 = sunset)
    val totalMinutes = java.time.Duration.between(riseTime, setTime).toMinutes().toFloat()
    if (totalMinutes <= 0f) return // Guard: bad data (sunrise >= sunset)
    val elapsedMinutes = java.time.Duration.between(riseTime, now).toMinutes().toFloat()
    val sunProgress = (elapsedMinutes / totalMinutes).coerceIn(0f, 1f)
    val isDaylight = now.isAfter(riseTime) && now.isBefore(setTime)

    // Twilight: civil twilight is ~30 min before sunrise and after sunset
    val twilightMinutes = 30f
    val dawnStart = riseTime.minusMinutes(30)
    val duskEnd = setTime.plusMinutes(30)
    val isInDawn = now.isAfter(dawnStart) && now.isBefore(riseTime)
    val isInDusk = now.isAfter(setTime) && now.isBefore(duskEnd)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 10.sp)
    val twilightColor = Color(0xFF1A237E).copy(alpha = 0.25f)

    val hasMoon = moonRiseTime != null && moonSetTime != null

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (hasMoon) 100.dp else 70.dp),
    ) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.80f
        val arcRadius = w * 0.42f
        val centerX = w / 2f

        // ── Twilight zones (wide stroke arcs at sunrise/sunset edges) ──
        val dawnFrac = (twilightMinutes / totalMinutes).coerceIn(0f, 0.15f)
        val dawnSweep = dawnFrac * 180f
        val twilightStroke = Stroke(width = 14f, cap = StrokeCap.Round)

        // Dawn zone (left arc edge, before sunrise)
        drawArc(
            color = twilightColor,
            startAngle = 180f,
            sweepAngle = dawnSweep,
            useCenter = false,
            topLeft = Offset(centerX - arcRadius, horizonY - arcRadius),
            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
            style = twilightStroke,
        )

        // Dusk zone (right arc edge, after sunset)
        drawArc(
            color = twilightColor,
            startAngle = 0f - dawnSweep,
            sweepAngle = dawnSweep,
            useCenter = false,
            topLeft = Offset(centerX - arcRadius, horizonY - arcRadius),
            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
            style = twilightStroke,
        )

        // ── Horizon line ────────────────────────────────────────────
        drawLine(
            color = NimbusTextTertiary.copy(alpha = 0.3f),
            start = Offset(w * 0.05f, horizonY),
            end = Offset(w * 0.95f, horizonY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )

        // ── Sun arc (dashed background, solid for traversed portion) ──
        val arcStrokeBg = Stroke(
            width = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
        )
        drawArc(
            color = NimbusSunYellow.copy(alpha = 0.2f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - arcRadius, horizonY - arcRadius),
            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
            style = arcStrokeBg,
        )

        // Solid traversed arc
        if (isDaylight) {
            val traversedSweep = sunProgress * 180f
            drawArc(
                color = NimbusSunYellow.copy(alpha = 0.6f),
                startAngle = 180f,
                sweepAngle = traversedSweep,
                useCenter = false,
                topLeft = Offset(centerX - arcRadius, horizonY - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
        }

        // ── Sun position dot ────────────────────────────────────────
        if (isDaylight) {
            val angle = PI.toFloat() * (1f - sunProgress)
            val sunX = centerX + arcRadius * cos(angle)
            val sunY = horizonY - arcRadius * sin(angle)

            // Outer glow
            drawCircle(
                color = NimbusSunYellow.copy(alpha = 0.15f),
                radius = 16f,
                center = Offset(sunX, sunY),
            )
            // Mid glow
            drawCircle(
                color = NimbusSunYellow.copy(alpha = 0.25f),
                radius = 10f,
                center = Offset(sunX, sunY),
            )
            // Sun dot
            drawCircle(
                color = NimbusSunYellow,
                radius = 6f,
                center = Offset(sunX, sunY),
            )
        }

        // ── Moon arc (if data available) ────────────────────────────
        if (moonRiseTime != null && moonSetTime != null) {
            val moonTotal = java.time.Duration.between(moonRiseTime, moonSetTime).toMinutes().toFloat()
            if (moonTotal > 0) {
                val moonElapsed = java.time.Duration.between(moonRiseTime, now).toMinutes().toFloat()
                val moonProgress = (moonElapsed / moonTotal).coerceIn(0f, 1f)
                val isMoonUp = now.isAfter(moonRiseTime) && now.isBefore(moonSetTime)

                val moonArcRadius = arcRadius * 0.75f

                // Moon arc background
                drawArc(
                    color = NimbusMoonBlue.copy(alpha = 0.15f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(centerX - moonArcRadius, horizonY - moonArcRadius),
                    size = androidx.compose.ui.geometry.Size(moonArcRadius * 2, moonArcRadius * 2),
                    style = Stroke(
                        width = 1.5f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
                    ),
                )

                // Moon position dot
                if (isMoonUp) {
                    val moonAngle = PI.toFloat() * (1f - moonProgress)
                    val moonX = centerX + moonArcRadius * cos(moonAngle)
                    val moonY = horizonY - moonArcRadius * sin(moonAngle)

                    drawCircle(
                        color = NimbusMoonBlue.copy(alpha = 0.2f),
                        radius = 10f,
                        center = Offset(moonX, moonY),
                    )
                    drawCircle(
                        color = NimbusMoonBlue,
                        radius = 5f,
                        center = Offset(moonX, moonY),
                    )
                }
            }
        }

        // ── Labels ──────────────────────────────────────────────────
        val riseLabel = com.sysadmindoc.nimbus.util.WeatherFormatter.formatTime(sunrise, settings)
        val riseM = textMeasurer.measure(riseLabel, labelStyle)
        drawText(riseM, topLeft = Offset(w * 0.05f, horizonY + 4f))

        val setLabel = com.sysadmindoc.nimbus.util.WeatherFormatter.formatTime(sunset, settings)
        val setM = textMeasurer.measure(setLabel, labelStyle)
        drawText(setM, topLeft = Offset(w * 0.95f - setM.size.width, horizonY + 4f))

        // Dawn/Dusk labels
        if (isInDawn || isInDusk) {
            val twilightLabel = if (isInDawn) "Dawn" else "Dusk"
            val twilightM = textMeasurer.measure(
                twilightLabel,
                TextStyle(color = NimbusBlueAccent.copy(alpha = 0.6f), fontSize = 9.sp),
            )
            drawText(
                twilightM,
                topLeft = Offset(centerX - twilightM.size.width / 2, horizonY - arcRadius - 14f),
            )
        }
    }
}
