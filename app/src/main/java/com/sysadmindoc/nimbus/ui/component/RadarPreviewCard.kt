package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusSurfaceVariant
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary

/**
 * Tappable radar preview card on the main screen.
 * Shows a live radar tile from RainViewer overlaid on a dark basemap.
 * Falls back to a map icon placeholder while loading.
 * Tapping opens the full-screen radar.
 */
@Composable
fun RadarPreviewCard(
    onOpenRadar: () -> Unit,
    modifier: Modifier = Modifier,
    radarTileUrl: String? = null,
    baseMapTileUrl: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NimbusCardBg)
            .clickable(onClick = onOpenRadar)
            .padding(1.dp),
    ) {
        // Map preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                .background(NimbusSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (baseMapTileUrl != null) {
                AsyncImage(
                    model = baseMapTileUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (radarTileUrl != null) {
                AsyncImage(
                    model = radarTileUrl,
                    contentDescription = "Radar overlay",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.75f,
                )
            }
            if (baseMapTileUrl == null && radarTileUrl == null) {
                Icon(
                    Icons.Filled.Map,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = NimbusTextSecondary.copy(alpha = 0.4f),
                )
            }
        }

        // Bottom bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(NimbusCardBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Radar",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                color = NimbusBlueAccent,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open radar",
                tint = NimbusBlueAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
