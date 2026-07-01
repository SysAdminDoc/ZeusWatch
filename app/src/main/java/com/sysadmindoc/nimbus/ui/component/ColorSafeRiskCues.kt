package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusUvExtreme
import com.sysadmindoc.nimbus.ui.theme.NimbusUvHigh
import com.sysadmindoc.nimbus.ui.theme.NimbusUvLow
import com.sysadmindoc.nimbus.ui.theme.NimbusUvModerate
import com.sysadmindoc.nimbus.ui.theme.NimbusUvVeryHigh

internal data class ColorSafeRiskCue(
    val code: String,
    val ordinal: Int,
    val total: Int,
    val color: Color,
)

internal object ColorSafeRiskCues {
    val aqiBoundaryFractions = listOf(
        50f / AQI_VISUAL_MAX,
        100f / AQI_VISUAL_MAX,
        150f / AQI_VISUAL_MAX,
        200f / AQI_VISUAL_MAX,
        250f / AQI_VISUAL_MAX,
    )

    val uvBoundaryFractions = listOf(
        3f / UV_VISUAL_MAX,
        6f / UV_VISUAL_MAX,
        8f / UV_VISUAL_MAX,
        11f / UV_VISUAL_MAX,
    )

    fun aqi(level: AqiLevel): ColorSafeRiskCue {
        val ordinal = level.ordinal + 1
        return ColorSafeRiskCue(
            code = "AQI $ordinal/${AqiLevel.entries.size}",
            ordinal = ordinal,
            total = AqiLevel.entries.size,
            color = level.color,
        )
    }

    fun uv(uvIndex: Double): ColorSafeRiskCue {
        val ordinal = when {
            uvIndex < 3 -> 1
            uvIndex < 6 -> 2
            uvIndex < 8 -> 3
            uvIndex < 11 -> 4
            else -> 5
        }
        val color = when (ordinal) {
            1 -> NimbusUvLow
            2 -> NimbusUvModerate
            3 -> NimbusUvHigh
            4 -> NimbusUvVeryHigh
            else -> NimbusUvExtreme
        }
        return ColorSafeRiskCue(
            code = "UV $ordinal/$UV_LEVEL_COUNT",
            ordinal = ordinal,
            total = UV_LEVEL_COUNT,
            color = color,
        )
    }

    fun pollen(level: PollenLevel): ColorSafeRiskCue {
        val ordinal = when (level) {
            PollenLevel.NONE -> 0
            PollenLevel.LOW -> 1
            PollenLevel.MODERATE -> 2
            PollenLevel.HIGH -> 3
            PollenLevel.VERY_HIGH -> 4
        }
        return ColorSafeRiskCue(
            code = "P$ordinal/$POLLEN_LEVEL_COUNT",
            ordinal = ordinal,
            total = POLLEN_LEVEL_COUNT,
            color = level.color,
        )
    }

    fun precipitation(probability: Int): ColorSafeRiskCue {
        val ordinal = when {
            probability <= 0 -> 0
            probability < 25 -> 1
            probability < 50 -> 2
            probability < 70 -> 3
            else -> 4
        }
        return ColorSafeRiskCue(
            code = "R$ordinal/$PRECIP_LEVEL_COUNT",
            ordinal = ordinal,
            total = PRECIP_LEVEL_COUNT,
            color = NimbusRainBlue,
        )
    }
}

@Composable
internal fun ColorSafeCueBadge(
    cue: ColorSafeRiskCue,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cue.color.copy(alpha = 0.12f))
            .border(1.dp, cue.color.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = cue.code,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val AQI_VISUAL_MAX = 300f
private const val UV_VISUAL_MAX = 12f
private const val UV_LEVEL_COUNT = 5
private const val POLLEN_LEVEL_COUNT = 4
private const val PRECIP_LEVEL_COUNT = 4
