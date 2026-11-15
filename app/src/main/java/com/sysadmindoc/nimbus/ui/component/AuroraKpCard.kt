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
import androidx.compose.runtime.remember
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
import com.sysadmindoc.nimbus.ui.theme.LocalNimbusPalette
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.ScaleColors
import com.sysadmindoc.nimbus.ui.theme.textTint

@Composable
fun AuroraKpCard(
    data: AuroraKpData,
    modifier: Modifier = Modifier,
) {
    val kp = data.kpValue
    val level = data.level
    val color = textTint(kpColor(level))
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
        ScaleColors.Green500 to 3,
        ScaleColors.Yellow500 to 1,
        ScaleColors.Orange500 to 1,
        ScaleColors.DeepOrange500 to 2,
        ScaleColors.Red500 to 2,
    )
    val totalWeight = segments.sumOf { it.second }
    val palette = LocalNimbusPalette.current
    val markerColor = palette.markTint(kpColor(KpLevel.from(kp)))
    // The bands are a faint backdrop: the card names the level in words, and
    // the ringed marker carries the reading (its dot reads at 3:1 on the
    // white ring in light mode, the ring on the bands in dark).
    val bandColors = remember(palette) { segments.map { palette.markTint(it.first).copy(alpha = 0.3f) } }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val barH = size.height
        val barW = size.width
        val cornerPx = 4.dp.toPx()
        var x = 0f
        segments.forEachIndexed { i, (_, weight) ->
            val segW = barW * weight / totalWeight
            drawRoundRect(
                color = bandColors[i],
                topLeft = Offset(x, 0f),
                size = Size(segW, barH),
                cornerRadius = CornerRadius(cornerPx),
            )
            x += segW
        }
        val markerX = (kp.toFloat() / 9f * barW).coerceIn(0f, barW)
        val markerR = 6.dp.toPx()
        drawCircle(Color.White, radius = markerR, center = Offset(markerX, barH / 2))
        drawCircle(markerColor, radius = 4.dp.toPx(), center = Offset(markerX, barH / 2))
    }
}

private fun kpColor(level: KpLevel): Color = when (level) {
    KpLevel.QUIET -> ScaleColors.Green500
    KpLevel.UNSETTLED -> ScaleColors.Yellow500
    KpLevel.ACTIVE -> ScaleColors.Orange500
    KpLevel.MINOR_STORM -> ScaleColors.DeepOrange500
    KpLevel.MODERATE_STORM -> ScaleColors.Red500
    KpLevel.STRONG_STORM -> ScaleColors.Pink500
    KpLevel.SEVERE_STORM -> ScaleColors.Purple500
    KpLevel.EXTREME_STORM -> ScaleColors.Purple800
}

private fun auroraVisibility(kp: Double): String = when {
    kp >= 7 -> "Aurora visible at low latitudes"
    kp >= 5 -> "Aurora visible at mid latitudes"
    kp >= 3 -> "Aurora possible at high latitudes"
    else -> "Aurora unlikely"
}
