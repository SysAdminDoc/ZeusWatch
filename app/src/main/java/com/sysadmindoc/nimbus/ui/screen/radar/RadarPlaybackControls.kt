package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Playback controls overlay for the native MapLibre radar view.
 * Shows play/pause, frame slider, and current frame timestamp.
 */
@Composable
fun RadarPlaybackControls(
    isPlaying: Boolean,
    currentFrame: Int,
    totalFrames: Int,
    pastFrameCount: Int,
    currentTimestamp: Long?,
    onTogglePlayback: () -> Unit,
    onSeekToFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.86f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Radar Playback",
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.padding(top = 6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/Pause
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NimbusBlueAccent.copy(alpha = 0.95f)),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = NimbusTextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Slider
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = currentFrame.toFloat(),
                    onValueChange = { onSeekToFrame(it.toInt()) },
                    valueRange = 0f..(totalFrames - 1).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = NimbusBlueAccent,
                        activeTrackColor = NimbusBlueAccent,
                        inactiveTrackColor = NimbusTextTertiary.copy(alpha = 0.3f),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Past/Forecast label
                    val isForecast = currentFrame >= pastFrameCount
                    Text(
                        text = if (isForecast) "Forecast" else "Past",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isForecast) NimbusBlueAccent else NimbusTextSecondary,
                    )

                    // Timestamp
                    currentTimestamp?.let { ts ->
                        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        Text(
                            text = dateFormat.format(Date(ts * 1000)),
                            style = MaterialTheme.typography.labelSmall,
                            color = NimbusTextSecondary,
                        )
                    }
                }
            }
        }
    }
}
