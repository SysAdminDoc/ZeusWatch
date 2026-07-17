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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
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
    hourly: List<HourlyConditions> = emptyList(),
    referenceTime: java.time.LocalDateTime? = hourly.firstOrNull()?.time,
) {
    val s = LocalUnitSettings.current
    val context = LocalContext.current
    val level = stringResource(uvLevelRes(uvIndex))

    // Find peak UV hour from hourly data
    val peakHour = hourly.take(12).maxByOrNull { it.uvIndex ?: 0.0 }
    val peakTimeRaw = if (peakHour != null) {
        WeatherFormatter.formatRelativeHourLabel(context, peakHour.time, referenceTime, s)
    } else null
    val peakTimeLabel = peakTimeRaw
    val peakUvNote = if (hourly.isNotEmpty()) {
        if (peakHour != null && (peakHour.uvIndex ?: 0.0) > uvIndex) {
            stringResource(R.string.uv_peak_at, peakTimeLabel ?: "")
        } else null
    } else null
    val safeMinutes = if (uvIndex >= 1) (200.0 / (uvIndex * 3.0)).toInt().coerceIn(5, 120) else null
    val peakSemantic = if (peakHour != null && peakTimeLabel != null && (peakHour.uvIndex ?: 0.0) > uvIndex) {
        stringResource(R.string.uv_semantics_peak, peakTimeLabel, (peakHour.uvIndex ?: 0.0).toInt())
    } else null
    val safeSemantic = if (safeMinutes != null) {
        stringResource(R.string.uv_semantics_safe_exposure, safeMinutes)
    } else null
    val semanticSummary = listOfNotNull(
        stringResource(R.string.uv_semantics, uvIndex.toInt(), level),
        peakSemantic,
        safeSemantic,
    ).joinToString(separator = " ")
    val levelColor = when {
        uvIndex < 3 -> NimbusUvLow
        uvIndex < 6 -> NimbusUvModerate
        uvIndex < 8 -> NimbusUvHigh
        uvIndex < 11 -> NimbusUvVeryHigh
        else -> NimbusUvExtreme
    }
    val cue = ColorSafeRiskCues.uv(uvIndex)

    WeatherCard(
        titleRes = R.string.card_type_uv_index,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = level,
                        style = MaterialTheme.typography.titleSmall,
                        color = levelColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ColorSafeCueBadge(cue = cue)
                }
                if (peakUvNote != null) {
                    Text(
                        text = peakUvNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                }
                // Safe sun exposure estimate (average skin type, SPF-free)
                if (safeMinutes != null) {
                    Text(
                        text = stringResource(R.string.uv_safe_exposure_without_spf, safeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Gradient bar with marker (mirrored under RTL like VisibilityScaleBar)
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    val w = size.width
                    val h = size.height

                    // Background gradient bar (0-12+ scale)
                    val gradientColors = listOf(
                        NimbusUvLow,
                        NimbusUvModerate,
                        NimbusUvHigh,
                        NimbusUvVeryHigh,
                        NimbusUvExtreme,
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = if (isRtl) gradientColors.asReversed() else gradientColors,
                        ),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        size = Size(w, h),
                    )

                    // Position marker
                    val markerX = rtlCanvasX((uvIndex.toFloat() / 12f).coerceIn(0f, 1f) * w, w, isRtl)
                    ColorSafeRiskCues.uvBoundaryFractions.forEach { fraction ->
                        val x = rtlCanvasX(fraction.coerceIn(0f, 1f) * w, w, isRtl)
                        drawLine(
                            color = Color.White.copy(alpha = 0.68f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(markerX, h / 2f),
                    )
                    drawCircle(
                        color = levelColor,
                        radius = 5.dp.toPx(),
                        center = Offset(markerX, h / 2f),
                    )
                }
            }
        }
    }
}

private fun uvLevelRes(uvIndex: Double): Int = when {
    uvIndex < 3 -> R.string.forecast_uv_low
    uvIndex < 6 -> R.string.forecast_uv_moderate
    uvIndex < 8 -> R.string.forecast_uv_high
    uvIndex < 11 -> R.string.forecast_uv_very_high
    else -> R.string.forecast_uv_extreme
}
