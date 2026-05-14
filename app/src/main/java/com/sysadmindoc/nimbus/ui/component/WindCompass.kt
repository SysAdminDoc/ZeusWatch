package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular wind compass showing direction with an arrow pointer
 * and cardinal direction labels. Shows speed in center.
 */
@Composable
fun WindCompass(
    windSpeed: Double,
    windDirection: Int,
    windGusts: Double?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val s = LocalUnitSettings.current
    val windDirectionLabel = WeatherFormatter.formatWindDirection(windDirection)
    val compassDescription = if (windGusts != null && windGusts > windSpeed) {
        stringResource(
            R.string.wind_compass_semantics_gusts,
            windDirectionLabel,
            WeatherFormatter.formatWindSpeed(windSpeed, s),
            WeatherFormatter.formatWindSpeed(windGusts, s),
        )
    } else {
        stringResource(
            R.string.wind_compass_semantics,
            windDirectionLabel,
            WeatherFormatter.formatWindSpeed(windSpeed, s),
        )
    }

    WeatherCard(
        titleRes = R.string.card_type_wind_compass,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = compassDescription
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Compass
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ringColor = if (s.showBeaufortColors) {
                    Color(WeatherFormatter.beaufortScale(windSpeed).colorHex)
                } else {
                    Color.White.copy(alpha = 0.1f)
                }
                val ringWidth = if (s.showBeaufortColors) 3f else 1.5f
                Canvas(modifier = Modifier.size(120.dp).semantics { contentDescription = compassDescription }) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.width / 2f - 16f
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = ringWidth),
                    )

                    // Tick marks for cardinal + ordinal directions
                    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                    val cardinalStyle = TextStyle(
                        color = NimbusTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    directions.forEachIndexed { i, dir ->
                        val angle = (i * 45f - 90f) * (PI.toFloat() / 180f)
                        val isCardinal = i % 2 == 0

                        // Tick marks
                        val innerR = radius - if (isCardinal) 8f else 5f
                        val outerR = radius
                        drawLine(
                            color = Color.White.copy(alpha = if (isCardinal) 0.3f else 0.15f),
                            start = Offset(cx + cos(angle) * innerR, cy + sin(angle) * innerR),
                            end = Offset(cx + cos(angle) * outerR, cy + sin(angle) * outerR),
                            strokeWidth = if (isCardinal) 1.5f else 1f,
                        )

                        // Cardinal labels
                        if (isCardinal) {
                            val labelR = radius + 12f
                            val lx = cx + cos(angle) * labelR
                            val ly = cy + sin(angle) * labelR
                            val measured = textMeasurer.measure(dir, cardinalStyle)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    lx - measured.size.width / 2f,
                                    ly - measured.size.height / 2f,
                                ),
                            )
                        }
                    }

                    // Direction arrow
                    rotate(windDirection.toFloat(), pivot = Offset(cx, cy)) {
                        val arrowPath = Path().apply {
                            moveTo(cx, cy - radius + 12f) // tip
                            lineTo(cx - 6f, cy - radius + 26f)
                            lineTo(cx + 6f, cy - radius + 26f)
                            close()
                        }
                        drawPath(arrowPath, NimbusBlueAccent)

                        // Arrow line to center
                        drawLine(
                            color = NimbusBlueAccent.copy(alpha = 0.6f),
                            start = Offset(cx, cy - radius + 26f),
                            end = Offset(cx, cy),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Center dot
                    drawCircle(
                        color = NimbusBlueAccent,
                        radius = 3f,
                        center = Offset(cx, cy),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Wind details
            Column {
                Text(
                    text = WeatherFormatter.formatWindSpeed(windSpeed, s),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = WeatherFormatter.formatWindDirection(windDirection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                )
                // Beaufort scale label (when enabled in settings)
                if (s.showBeaufortColors) {
                    val beaufort = WeatherFormatter.beaufortScale(windSpeed)
                    val beaufortLabel = stringResource(beaufortLabelRes(beaufort.scale))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.wind_beaufort_value, beaufort.scale, beaufortLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(beaufort.colorHex),
                    )
                }
                if (windGusts != null && windGusts > windSpeed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.wind_gusts),
                        style = MaterialTheme.typography.labelMedium,
                        color = NimbusTextTertiary,
                    )
                    Text(
                        text = WeatherFormatter.formatWindSpeed(windGusts, s),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

private fun beaufortLabelRes(scale: Int): Int = when (scale) {
    0 -> R.string.beaufort_calm
    1 -> R.string.beaufort_light_air
    2 -> R.string.beaufort_light_breeze
    3 -> R.string.beaufort_gentle_breeze
    4 -> R.string.beaufort_moderate_breeze
    5 -> R.string.beaufort_fresh_breeze
    6 -> R.string.beaufort_strong_breeze
    7 -> R.string.beaufort_near_gale
    8 -> R.string.beaufort_gale
    9 -> R.string.beaufort_strong_gale
    10 -> R.string.beaufort_storm
    11 -> R.string.beaufort_violent_storm
    else -> R.string.beaufort_hurricane
}
