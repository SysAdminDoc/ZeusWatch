package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

/**
 * Available map overlay layers for the native radar view.
 * Each layer corresponds to a different tile source.
 */
enum class RadarLayer(
    @StringRes val labelRes: Int,
    val tileUrlTemplate: String?,
) {
    RADAR(R.string.radar_layer_radar, null), // Handled separately by RainViewer
    LIGHTNING(R.string.radar_layer_lightning, null), // Real-time Blitzortung WebSocket overlay
    SATELLITE(
        R.string.radar_layer_satellite,
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
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        RadarLayer.entries.forEach { layer ->
            val isSelected = layer == selectedLayer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onLayerSelected(layer) },
                        role = Role.Tab,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(layer.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) NimbusBlueAccent else NimbusTextTertiary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) NimbusBlueAccent else Color.Transparent),
                )
            }
        }
    }
}
