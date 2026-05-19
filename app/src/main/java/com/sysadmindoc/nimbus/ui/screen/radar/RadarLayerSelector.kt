package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.component.NimbusSelectableSegment
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop

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
 * Horizontal segmented row for selecting radar map layers.
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.82f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .selectableGroup()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RadarLayer.entries.forEach { layer ->
            val isSelected = layer == selectedLayer
            NimbusSelectableSegment(
                label = layer.label,
                selected = isSelected,
                onClick = { onLayerSelected(layer) },
                role = Role.Tab,
                compact = true,
            )
        }
    }
}
