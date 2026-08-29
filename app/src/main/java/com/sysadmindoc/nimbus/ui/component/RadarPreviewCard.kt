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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.RadarProvider
import com.sysadmindoc.nimbus.ui.screen.radar.RadarWebView
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
 * Matches the selected radar provider. Web providers render their live view,
 * while native providers show the latest available radar tile.
 * Tapping opens the full-screen radar.
 */
@Composable
fun RadarPreviewCard(
    onOpenRadar: () -> Unit,
    provider: RadarProvider,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    radarTileUrl: String? = null,
    baseMapTileUrl: String? = null,
    statusLabel: String? = null,
    statusTint: Color? = null,
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
            .border(1.dp, NimbusCardBorder, shape),
    ) {
        RadarPreviewMap(
            onOpenRadar = onOpenRadar,
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            radarTileUrl = radarTileUrl,
            baseMapTileUrl = baseMapTileUrl,
            statusLabel = statusLabel,
            statusTint = statusTint,
        )
        RadarPreviewFooter(
            onOpenRadar = onOpenRadar,
            provider = provider,
        )
    }
}

@Composable
private fun RadarPreviewMap(
    onOpenRadar: () -> Unit,
    provider: RadarProvider,
    latitude: Double,
    longitude: Double,
    radarTileUrl: String?,
    baseMapTileUrl: String?,
    statusLabel: String?,
    statusTint: Color?,
) {
    val usesEmbeddedRadar = !provider.supportsNativePlayback
    val hasRadarContent = usesEmbeddedRadar || baseMapTileUrl != null || radarTileUrl != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
            .background(NimbusSurfaceVariant),
    ) {
        if (usesEmbeddedRadar) {
            RadarWebView(
                provider = provider,
                latitude = latitude,
                longitude = longitude,
                modifier = Modifier.fillMaxSize(),
                zoom = 7,
                interactive = false,
                onPreviewClick = onOpenRadar,
            )
        } else {
            RadarPreviewImages(
                radarTileUrl = radarTileUrl,
                baseMapTileUrl = baseMapTileUrl,
            )
        }
        if (!usesEmbeddedRadar) {
            RadarPreviewGradient()
            if (baseMapTileUrl != null) {
                Text(
                    text = stringResource(R.string.radar_preview_map_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.58f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            if (hasRadarContent) {
                RadarPreviewCaption(
                    title = stringResource(R.string.radar_preview_title),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                RadarPreviewEmptyState(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 26.dp),
                )
            }
            RadarPreviewStatusBadge(
                hasRadarTile = hasRadarContent,
                statusLabel = statusLabel,
                statusTint = statusTint,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
            )
        }
        if (!usesEmbeddedRadar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        onClickLabel = stringResource(R.string.radar_preview_open_cd),
                        onClick = onOpenRadar,
                    ),
            )
        }
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
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.radar_preview_empty_message),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RadarPreviewStatusBadge(
    hasRadarTile: Boolean,
    statusLabel: String?,
    statusTint: Color?,
    modifier: Modifier = Modifier,
) {
    NimbusStatusBadge(
        text = statusLabel ?: if (hasRadarTile) {
            stringResource(R.string.radar_preview_live)
        } else {
            stringResource(R.string.radar_preview_map_preview)
        },
        tint = statusTint ?: NimbusTextPrimary,
        modifier = modifier,
        emphasized = statusLabel == null,
        maxLines = 1,
    )
}

@Composable
private fun RadarPreviewCaption(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
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
private fun RadarPreviewFooter(
    onOpenRadar: () -> Unit,
    provider: RadarProvider,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.radar_preview_open_cd),
                onClick = onOpenRadar,
            )
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
                text = if (provider.supportsNativePlayback) {
                    stringResource(R.string.radar_preview_open_title)
                } else {
                    stringResource(R.string.radar_preview_open_provider, provider.label)
                },
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
