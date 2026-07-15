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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.TimeFormat
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
import kotlin.math.roundToInt

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
    timeFormat: TimeFormat,
    onTogglePlayback: () -> Unit,
    onSeekToFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackTitle = stringResource(R.string.radar_playback_title)
    val staticLabel = stringResource(R.string.radar_playback_static)
    val forecastLabel = stringResource(R.string.radar_playback_forecast)
    val pastLabel = stringResource(R.string.radar_playback_past)
    val noFramesLabel = stringResource(R.string.radar_playback_no_frames)
    val unavailableLabel = stringResource(R.string.radar_playback_unavailable)
    val playLabel = stringResource(R.string.radar_playback_play)
    val pauseLabel = stringResource(R.string.radar_playback_pause)
    val normalizedFrame = if (totalFrames > 0) currentFrame.coerceIn(0, totalFrames - 1) else 0
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sliderDisplayValue = radarPlaybackDisplayValue(normalizedFrame, totalFrames, isRtl)
    val framePositionLabel = when {
        !playbackEnabled -> staticLabel
        totalFrames <= 0 -> noFramesLabel
        else -> "${normalizedFrame + 1}/$totalFrames"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.86f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = playbackTitle,
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )

            Text(
                text = framePositionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (playbackEnabled && totalFrames > 0) NimbusTextSecondary else NimbusTextTertiary,
            )
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (playbackEnabled) NimbusBlueAccent.copy(alpha = 0.95f)
                        else Color.White.copy(alpha = 0.08f),
                    )
                    .border(
                        1.dp,
                        if (playbackEnabled) NimbusBlueAccent.copy(alpha = 0.38f)
                        else NimbusCardBorder.copy(alpha = 0.72f),
                        RoundedCornerShape(10.dp),
                    )
                    .semantics {
                        stateDescription = if (playbackEnabled) {
                            if (isPlaying) pauseLabel else playLabel
                        } else {
                            unavailableLabel
                        }
                    },
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) pauseLabel else playLabel,
                    tint = if (playbackEnabled) NimbusTextPrimary else NimbusTextTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Slider
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        enabled = playbackEnabled,
                        value = sliderDisplayValue,
                        onValueChange = {
                            onSeekToFrame(radarPlaybackFrameFromDisplayValue(it, totalFrames, isRtl))
                        },
                        // A single (or empty) frame set must not invent a phantom
                        // second frame; collapse the range to 0..0 (the slider is
                        // already disabled via playbackEnabled in that case).
                        valueRange = 0f..(totalFrames - 1).coerceAtLeast(0).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = NimbusBlueAccent,
                            activeTrackColor = NimbusBlueAccent,
                            inactiveTrackColor = NimbusTextTertiary.copy(alpha = 0.3f),
                            disabledThumbColor = NimbusTextTertiary.copy(alpha = 0.38f),
                            disabledActiveTrackColor = NimbusTextTertiary.copy(alpha = 0.24f),
                            disabledInactiveTrackColor = NimbusTextTertiary.copy(alpha = 0.16f),
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val isForecast = currentFrame >= pastFrameCount
                    val timestampText = currentTimestamp?.let { ts ->
                        val pattern = if (timeFormat == TimeFormat.TWENTY_FOUR_HOUR) "HH:mm" else "h:mm a"
                        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
                        dateFormat.format(Date(ts * 1000))
                    } ?: noFramesLabel

                    Text(
                        text = if (isForecast) forecastLabel else pastLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isForecast) NimbusBlueAccent else NimbusTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isForecast) NimbusBlueAccent.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.06f),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )

                    Text(
                        text = if (playbackEnabled) timestampText else unavailableLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextSecondary,
                    )
                }
            }
        }
    }
}

internal fun radarPlaybackDisplayValue(
    frame: Int,
    totalFrames: Int,
    isRtl: Boolean,
): Float {
    val maxFrame = (totalFrames - 1).coerceAtLeast(0)
    val clampedFrame = frame.coerceIn(0, maxFrame)
    return if (isRtl) (maxFrame - clampedFrame).toFloat() else clampedFrame.toFloat()
}

internal fun radarPlaybackFrameFromDisplayValue(
    displayValue: Float,
    totalFrames: Int,
    isRtl: Boolean,
): Int {
    val maxFrame = (totalFrames - 1).coerceAtLeast(0)
    // Round instead of truncate: truncation seeks one frame low in LTR (and,
    // mirrored, one frame high in RTL) for any drag that lands mid-step.
    val displayFrame = displayValue.roundToInt().coerceIn(0, maxFrame)
    return if (isRtl) maxFrame - displayFrame else displayFrame
}
