package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.AuroraKpData
import com.sysadmindoc.nimbus.data.repository.KpLevel
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

@Composable
fun AuroraKpCard(
    data: AuroraKpData,
    modifier: Modifier = Modifier,
) {
    val kp = data.kpValue
    val level = data.level
    val color = kpColor(level)
    val desc = stringResource(R.string.aurora_kp_semantics, kp.toString(), level.label)

    WeatherCard(
        titleRes = R.string.card_title_aurora_kp,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.aurora_kp_value, kp.toString()),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = color,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = level.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = auroraVisibility(kp),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        KpScaleBar(kp = kp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
            Text("9", style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
        }
    }
}

@Composable
private fun KpScaleBar(kp: Double) {
    val segments = listOf(
        Color(0xFF4CAF50) to 3,
        Color(0xFFFFEB3B) to 1,
        Color(0xFFFF9800) to 1,
        Color(0xFFFF5722) to 2,
        Color(0xFFF44336) to 2,
    )
    val totalWeight = segments.sumOf { it.second }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val barH = size.height
        val barW = size.width
        val cornerPx = 4.dp.toPx()
        var x = 0f
        for ((color, weight) in segments) {
            val segW = barW * weight / totalWeight
            drawRoundRect(
                color = color.copy(alpha = 0.3f),
                topLeft = Offset(x, 0f),
                size = Size(segW, barH),
                cornerRadius = CornerRadius(cornerPx),
            )
            x += segW
        }
        val markerX = (kp.toFloat() / 9f * barW).coerceIn(0f, barW)
        val markerR = 6.dp.toPx()
        drawCircle(Color.White, radius = markerR, center = Offset(markerX, barH / 2))
        drawCircle(kpColor(KpLevel.from(kp)), radius = 4.dp.toPx(), center = Offset(markerX, barH / 2))
    }
}

private fun kpColor(level: KpLevel): Color = when (level) {
    KpLevel.QUIET -> Color(0xFF4CAF50)
    KpLevel.UNSETTLED -> Color(0xFFFFEB3B)
    KpLevel.ACTIVE -> Color(0xFFFF9800)
    KpLevel.MINOR_STORM -> Color(0xFFFF5722)
    KpLevel.MODERATE_STORM -> Color(0xFFF44336)
    KpLevel.STRONG_STORM -> Color(0xFFE91E63)
    KpLevel.SEVERE_STORM -> Color(0xFF9C27B0)
    KpLevel.EXTREME_STORM -> Color(0xFF6A1B9A)
}

private fun auroraVisibility(kp: Double): String = when {
    kp >= 7 -> "Aurora visible at low latitudes"
    kp >= 5 -> "Aurora visible at mid latitudes"
    kp >= 3 -> "Aurora possible at high latitudes"
    else -> "Aurora unlikely"
}
