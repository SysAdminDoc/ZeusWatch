package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

/**
 * Available map overlay layers for the native radar view.
 * Each layer corresponds to a different tile source.
 */
enum class RadarLayer(
    val label: String,
    val tileUrlTemplate: String?,
) {
    RADAR("Radar", null), // Handled separately by RainViewer
    LIGHTNING("Lightning", null), // Real-time Blitzortung WebSocket overlay
    SATELLITE(
        "Satellite",
        "https://tilecache.rainviewer.com/v2/satellite/256/{z}/{x}/{y}/2/0_0.png",
    ),
}

/**
 * Horizontal chip row for selecting radar map layers.
 */
@Composable
fun RadarLayerSelector(
    selectedLayer: RadarLayer,
    onLayerSelected: (RadarLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.82f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(22.dp))
            .selectableGroup()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RadarLayer.entries.forEach { layer ->
            val isSelected = layer == selectedLayer
            val bg = if (isSelected) {
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusBlueAccent.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusCardBg.copy(alpha = 0.9f),
                        NimbusGlassBottom,
                    ),
                )
            }
            val textColor = if (isSelected) NimbusTextPrimary else NimbusTextTertiary

            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .border(1.dp, if (isSelected) NimbusBlueAccent.copy(alpha = 0.5f) else NimbusCardBorder, RoundedCornerShape(18.dp))
                    .selectable(
                        selected = isSelected,
                        onClick = { onLayerSelected(layer) },
                        role = Role.Tab,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isSelected) NimbusBlueAccent.copy(alpha = 0.95f)
                            else NimbusTextTertiary.copy(alpha = 0.35f),
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = layer.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = textColor,
                )
            }
        }
    }
}
