package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadarLayer.entries.forEach { layer ->
            val isSelected = layer == selectedLayer
            val bg = if (isSelected) NimbusBlueAccent.copy(alpha = 0.3f) else NimbusCardBg.copy(alpha = 0.7f)
            val textColor = if (isSelected) NimbusBlueAccent else NimbusTextTertiary

            Text(
                text = layer.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = textColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable { onLayerSelected(layer) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
