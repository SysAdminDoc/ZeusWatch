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
import com.sysadmindoc.nimbus.ui.theme.NimbusSunYellow
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun arc visualization showing the sun's position in the sky.
 * Draws a semicircular arc from sunrise (left) to sunset (right),
 * with the current sun position as a glowing dot.
 */
@Composable
fun SunArc(
    sunrise: String?,
    sunset: String?,
    modifier: Modifier = Modifier,
) {
    if (sunrise == null || sunset == null) return

    val settings = LocalUnitSettings.current
    val now = LocalDateTime.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val riseTime = try { LocalDateTime.parse(sunrise, fmt) } catch (_: Exception) { return }
    val setTime = try { LocalDateTime.parse(sunset, fmt) } catch (_: Exception) { return }

    // Calculate sun position (0 = sunrise, 1 = sunset)
    val totalMinutes = java.time.Duration.between(riseTime, setTime).toMinutes().toFloat()
    val elapsedMinutes = java.time.Duration.between(riseTime, now).toMinutes().toFloat()
    val sunProgress = (elapsedMinutes / totalMinutes).coerceIn(0f, 1f)
    val isDaylight = now.isAfter(riseTime) && now.isBefore(setTime)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 10.sp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
    ) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.85f
        val arcRadius = w * 0.42f
        val centerX = w / 2f

        // Horizon line
        drawLine(
            color = NimbusTextTertiary.copy(alpha = 0.3f),
            start = Offset(w * 0.05f, horizonY),
            end = Offset(w * 0.95f, horizonY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )

        // Arc path (dashed for below horizon, solid above)
        val arcStroke = Stroke(
            width = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
        )
        drawArc(
            color = NimbusSunYellow.copy(alpha = 0.3f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - arcRadius, horizonY - arcRadius),
            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
            style = arcStroke,
        )

        // Sun position dot
        if (isDaylight) {
            val angle = PI.toFloat() * (1f - sunProgress) // 180 to 0 degrees
            val sunX = centerX + arcRadius * cos(angle)
            val sunY = horizonY - arcRadius * sin(angle)

            // Glow
            drawCircle(
                color = NimbusSunYellow.copy(alpha = 0.2f),
                radius = 12f,
                center = Offset(sunX, sunY),
            )
            // Sun dot
            drawCircle(
                color = NimbusSunYellow,
                radius = 6f,
                center = Offset(sunX, sunY),
            )
        }

        // Sunrise label (uses user time format)
        val riseLabel = com.sysadmindoc.nimbus.util.WeatherFormatter.formatTime(sunrise, settings)
        val riseM = textMeasurer.measure(riseLabel, labelStyle)
        drawText(riseM, topLeft = Offset(w * 0.05f, horizonY + 4f))

        // Sunset label (uses user time format)
        val setLabel = com.sysadmindoc.nimbus.util.WeatherFormatter.formatTime(sunset, settings)
        val setM = textMeasurer.measure(setLabel, labelStyle)
        drawText(setM, topLeft = Offset(w * 0.95f - setM.size.width, horizonY + 4f))
    }
}
