package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.*
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * UV Index display with a color-coded gradient bar and marker.
 * Scale: 0-2 Low (green), 3-5 Moderate (yellow), 6-7 High (orange),
 * 8-10 Very High (red), 11+ Extreme (purple).
 */
@Composable
fun UvIndexBar(
    uvIndex: Double,
    modifier: Modifier = Modifier,
) {
    val level = WeatherFormatter.uvDescription(uvIndex)
    val levelColor = when {
        uvIndex < 3 -> NimbusUvLow
        uvIndex < 6 -> NimbusUvModerate
        uvIndex < 8 -> NimbusUvHigh
        uvIndex < 11 -> NimbusUvVeryHigh
        else -> NimbusUvExtreme
    }

    WeatherCard(
        title = "UV Index",
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // UV number
            Text(
                text = uvIndex.toInt().toString(),
                style = MaterialTheme.typography.displaySmall,
                color = levelColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level,
                    style = MaterialTheme.typography.titleSmall,
                    color = levelColor,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Gradient bar with marker
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    val w = size.width
                    val h = size.height

                    // Background gradient bar (0-12+ scale)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                NimbusUvLow,
                                NimbusUvModerate,
                                NimbusUvHigh,
                                NimbusUvVeryHigh,
                                NimbusUvExtreme,
                            ),
                        ),
                        cornerRadius = CornerRadius(5f, 5f),
                        size = Size(w, h),
                    )

                    // Position marker
                    val markerX = (uvIndex.toFloat() / 12f).coerceIn(0f, 1f) * w
                    drawCircle(
                        color = Color.White,
                        radius = 7f,
                        center = Offset(markerX, h / 2f),
                    )
                    drawCircle(
                        color = levelColor,
                        radius = 5f,
                        center = Offset(markerX, h / 2f),
                    )
                }
            }
        }
    }
}
