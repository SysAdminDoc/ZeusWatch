package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private enum class PrecipIntensity(val color: Color, val labelRes: Int) {
    NONE(Color(0xFF2A2A3A), R.string.nowcast_intensity_none),
    LIGHT(Color(0xFF5B8DEF), R.string.nowcast_intensity_light),
    MODERATE(Color(0xFF3A7BD5), R.string.nowcast_intensity_moderate),
    HEAVY(Color(0xFF1B4F9E), R.string.nowcast_intensity_heavy),
}

private fun classifyIntensity(mmPer15min: Double): PrecipIntensity = when {
    mmPer15min > 2.0 -> PrecipIntensity.HEAVY
    mmPer15min > 0.5 -> PrecipIntensity.MODERATE
    mmPer15min > 0.05 -> PrecipIntensity.LIGHT
    else -> PrecipIntensity.NONE
}

@Composable
fun NowcastCard(
    data: List<MinutelyPrecipitation>,
    referenceTime: LocalDateTime? = data.firstOrNull()?.time,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(data, referenceTime) {
        val now = referenceTime ?: return@remember data.take(8)
        data.filter { it.time.isAfter(now.minusMinutes(5)) }
            .take(8)
    }

    if (filtered.isEmpty()) return
    val hasRain = filtered.any { it.precipitation > 0.01 }

    val ref = referenceTime ?: filtered.first().time
    val settings = LocalUnitSettings.current
    val context = LocalContext.current

    val narrative = remember(filtered, ref) {
        buildNowcastNarrative(filtered, ref)
    }
    val narrativeText = narrative.toDisplayString(context, settings)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NimbusTextTertiary, fontSize = 9.sp)

    val semanticSummary = narrativeText

    WeatherCard(
        titleRes = R.string.card_type_nowcast,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticSummary
        },
    ) {
        Text(
            text = narrativeText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasRain) NimbusRainBlue else NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Timeline runs chronologically start-edge → end-edge, so mirror
        // segments and time labels under RTL.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            val w = size.width
            val h = size.height
            val segCount = filtered.size
            val segWidth = w / segCount
            val barHeight = h - 16f
            val cornerR = 6f

            drawRoundRect(
                color = PrecipIntensity.NONE.color,
                topLeft = Offset(0f, 0f),
                size = Size(w, barHeight),
                cornerRadius = CornerRadius(cornerR, cornerR),
            )

            filtered.forEachIndexed { i, entry ->
                val intensity = classifyIntensity(entry.precipitation)
                if (intensity != PrecipIntensity.NONE) {
                    drawRect(
                        color = intensity.color,
                        topLeft = Offset(rtlCanvasRectLeft(i * segWidth, segWidth + 1f, w, isRtl), 0f),
                        size = Size(segWidth + 1f, barHeight),
                    )
                }
            }

            filtered.forEachIndexed { i, entry ->
                if (i % 2 == 0) {
                    val label = WeatherFormatter.formatClockTime(entry.time, settings)
                    val m = textMeasurer.measure(label, labelStyle)
                    val labelLeft = rtlCanvasRectLeft(i * segWidth, m.size.width.toFloat(), w, isRtl)
                    drawText(m, topLeft = Offset(labelLeft, barHeight + 2f))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.width(12.dp).height(8.dp)) {
                drawRect(PrecipIntensity.LIGHT.color, size = Size(size.width, size.height))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.nowcast_intensity_light),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Canvas(modifier = Modifier.width(12.dp).height(8.dp)) {
                drawRect(PrecipIntensity.MODERATE.color, size = Size(size.width, size.height))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.nowcast_intensity_moderate),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Canvas(modifier = Modifier.width(12.dp).height(8.dp)) {
                drawRect(PrecipIntensity.HEAVY.color, size = Size(size.width, size.height))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.nowcast_intensity_heavy),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

private data class NowcastNarrative(
    val segments: List<NarrativeSegment>,
)

private data class NarrativeSegment(
    val intensity: PrecipIntensity,
    val startMinutes: Long,
    val endMinutes: Long,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)

private fun buildNowcastNarrative(
    data: List<MinutelyPrecipitation>,
    ref: LocalDateTime,
): NowcastNarrative {
    if (data.isEmpty()) return NowcastNarrative(emptyList())
    val segments = mutableListOf<NarrativeSegment>()
    var currentIntensity = classifyIntensity(data[0].precipitation)
    var segStart = data[0].time

    for (i in 1 until data.size) {
        val newIntensity = classifyIntensity(data[i].precipitation)
        if (newIntensity != currentIntensity) {
            segments += NarrativeSegment(
                intensity = currentIntensity,
                startMinutes = ChronoUnit.MINUTES.between(ref, segStart),
                endMinutes = ChronoUnit.MINUTES.between(ref, data[i].time),
                startTime = segStart,
                endTime = data[i].time,
            )
            currentIntensity = newIntensity
            segStart = data[i].time
        }
    }
    val lastEntry = data.last()
    segments += NarrativeSegment(
        intensity = currentIntensity,
        startMinutes = ChronoUnit.MINUTES.between(ref, segStart),
        endMinutes = ChronoUnit.MINUTES.between(ref, lastEntry.time) + 15,
        startTime = segStart,
        endTime = lastEntry.time.plusMinutes(15),
    )
    return NowcastNarrative(segments)
}

private fun NowcastNarrative.toDisplayString(
    context: android.content.Context,
    settings: com.sysadmindoc.nimbus.data.repository.NimbusSettings,
): String {
    val hasRain = segments.any { it.intensity != PrecipIntensity.NONE }
    if (!hasRain) return context.getString(R.string.nowcast_summary_none)

    val firstRain = segments.firstOrNull { it.intensity != PrecipIntensity.NONE } ?: return context.getString(R.string.nowcast_summary_none)
    val lastRain = segments.lastOrNull { it.intensity != PrecipIntensity.NONE } ?: firstRain

    val intensityLabel = context.getString(firstRain.intensity.labelRes).lowercase()

    return when {
        firstRain.startMinutes <= 0 && lastRain == segments.last() -> {
            context.getString(R.string.nowcast_timeline_continuing, intensityLabel)
        }
        firstRain.startMinutes <= 0 -> {
            val endLabel = WeatherFormatter.formatClockTime(lastRain.endTime, settings)
            context.getString(R.string.nowcast_timeline_ending, endLabel)
        }
        else -> {
            val minutesUntil = firstRain.startMinutes
            context.getString(R.string.nowcast_timeline_starting, intensityLabel, minutesUntil)
        }
    }
}
