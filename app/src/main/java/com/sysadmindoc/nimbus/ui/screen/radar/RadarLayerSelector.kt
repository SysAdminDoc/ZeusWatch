package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.82f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Text(
                text = layer.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = textColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .border(1.dp, if (isSelected) NimbusBlueAccent.copy(alpha = 0.5f) else NimbusCardBorder, RoundedCornerShape(18.dp))
                    .clickable { onLayerSelected(layer) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}
