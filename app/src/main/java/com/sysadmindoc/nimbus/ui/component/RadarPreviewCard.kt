package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
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
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.78f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, shape)
            .clickable(onClick = onOpenRadar),
    ) {
        RadarPreviewMap(
            radarTileUrl = radarTileUrl,
            baseMapTileUrl = baseMapTileUrl,
        )
        RadarPreviewFooter()
    }
}

@Composable
private fun RadarPreviewMap(
    radarTileUrl: String?,
    baseMapTileUrl: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
            .background(NimbusSurfaceVariant),
    ) {
        RadarPreviewImages(
            radarTileUrl = radarTileUrl,
            baseMapTileUrl = baseMapTileUrl,
        )
        RadarPreviewGradient()
        if (baseMapTileUrl == null && radarTileUrl == null) {
            RadarPreviewEmptyState(modifier = Modifier.align(Alignment.Center))
        }
        RadarPreviewStatusPill(
            hasRadarTile = radarTileUrl != null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        )
        RadarPreviewCaption(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun RadarPreviewImages(
    radarTileUrl: String?,
    baseMapTileUrl: String?,
) {
    val context = LocalContext.current
    if (baseMapTileUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(baseMapTileUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
    if (radarTileUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(radarTileUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.radar_preview_overlay_cd),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.75f,
        )
    }
}

@Composable
private fun RadarPreviewGradient() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.32f),
                    ),
                ),
            ),
    )
}

@Composable
private fun RadarPreviewEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Map,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = NimbusTextSecondary.copy(alpha = 0.45f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.radar_preview_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = NimbusTextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.radar_preview_empty_message),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
    }
}

@Composable
private fun RadarPreviewStatusPill(
    hasRadarTile: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (hasRadarTile) {
                    stringResource(R.string.radar_preview_live)
                } else {
                    stringResource(R.string.radar_preview_map_preview)
                },
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextPrimary,
            )
        }
    }
}

@Composable
private fun RadarPreviewCaption(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.radar_preview_title),
            style = MaterialTheme.typography.headlineSmall,
            color = NimbusTextPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.radar_preview_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
    }
}

@Composable
private fun RadarPreviewFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Radar,
            contentDescription = null,
            tint = NimbusBlueAccent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.radar_preview_open_title),
                style = MaterialTheme.typography.titleSmall,
                color = NimbusTextPrimary,
            )
            Text(
                text = stringResource(R.string.radar_preview_open_desc),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.radar_preview_open_cd),
            tint = NimbusBlueAccent,
            modifier = Modifier.size(18.dp),
        )
    }
}
