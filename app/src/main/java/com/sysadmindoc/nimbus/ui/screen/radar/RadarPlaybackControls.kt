package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
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
    playbackEnabled: Boolean,
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
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.86f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Radar Playback",
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )

            if (!playbackEnabled) {
                Text(
                    text = "Static",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
            }
        }

        Spacer(modifier = Modifier.size(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/Pause
            IconButton(
                enabled = playbackEnabled,
                onClick = onTogglePlayback,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (playbackEnabled) NimbusBlueAccent.copy(alpha = 0.95f)
                        else Color.White.copy(alpha = 0.08f),
                    ),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (playbackEnabled) NimbusTextPrimary else NimbusTextTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Slider
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    enabled = playbackEnabled,
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
                    val isForecast = currentFrame >= pastFrameCount
                    val timestampText = currentTimestamp?.let { ts ->
                        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        dateFormat.format(Date(ts * 1000))
                    } ?: "No frames"

                    Text(
                        text = if (isForecast) "Forecast" else "Past",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isForecast) NimbusBlueAccent else NimbusTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isForecast) NimbusBlueAccent.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.06f),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )

                    Text(
                        text = if (playbackEnabled) timestampText else "Animation unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextSecondary,
                    )
                }
            }
        }
    }
}
