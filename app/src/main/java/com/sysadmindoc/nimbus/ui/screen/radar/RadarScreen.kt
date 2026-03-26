package com.sysadmindoc.nimbus.ui.screen.radar

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.RadarProvider
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary

@Composable
fun RadarScreen(
    latitude: Double,
    longitude: Double,
    onBack: () -> Unit,
    viewModel: RadarViewModel = hiltViewModel(),
) {
    var resolvedLat by remember { mutableDoubleStateOf(latitude) }
    var resolvedLon by remember { mutableDoubleStateOf(longitude) }
    LaunchedEffect(latitude, longitude) {
        val (lat, lon) = viewModel.resolveLocation(latitude, longitude)
        resolvedLat = lat
        resolvedLon = lon
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = NimbusSettings())
    val radarState by viewModel.uiState.collectAsStateWithLifecycle()

    PredictiveBackScaffold(onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NimbusNavyDark),
        ) {
            when (settings.radarProvider) {
                RadarProvider.NATIVE_MAPLIBRE -> {
                    RadarMapView(
                        latitude = resolvedLat,
                        longitude = resolvedLon,
                        modifier = Modifier.fillMaxSize(),
                    )
                    RadarPlaybackControls(
                        isPlaying = radarState.isPlaying,
                        currentFrame = radarState.currentFrameIndex,
                        totalFrames = radarState.totalFrames,
                        pastFrameCount = radarState.pastFrameCount,
                        currentTimestamp = radarState.currentFrame?.timestamp,
                        onTogglePlayback = { viewModel.togglePlayback() },
                        onSeekToFrame = { viewModel.seekToFrame(it) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                RadarProvider.WINDY_WEBVIEW -> {
                    RadarWebView(
                        latitude = resolvedLat,
                        longitude = resolvedLon,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Back button overlay
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(NimbusCardBg)
                    .size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NimbusTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Standalone radar composable for embedding in bottom nav tab (no back button).
 * Respects the user's radar provider preference (Windy WebView vs native MapLibre).
 */
@Composable
fun RadarTab(
    latitude: Double,
    longitude: Double,
    viewModel: RadarViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = NimbusSettings())
    val radarState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark),
    ) {
        when (settings.radarProvider) {
            RadarProvider.NATIVE_MAPLIBRE -> {
                RadarMapView(
                    latitude = latitude,
                    longitude = longitude,
                    modifier = Modifier.fillMaxSize(),
                )
                // Playback controls overlay
                RadarPlaybackControls(
                    isPlaying = radarState.isPlaying,
                    currentFrame = radarState.currentFrameIndex,
                    totalFrames = radarState.totalFrames,
                    pastFrameCount = radarState.pastFrameCount,
                    currentTimestamp = radarState.currentFrame?.timestamp,
                    onTogglePlayback = { viewModel.togglePlayback() },
                    onSeekToFrame = { viewModel.seekToFrame(it) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            RadarProvider.WINDY_WEBVIEW -> {
                RadarWebView(
                    latitude = latitude,
                    longitude = longitude,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RadarWebView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val radarUrl = buildRainViewerUrl(latitude, longitude)
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(Unit) {
        onDispose { webViewRef[0]?.destroy() }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                setBackgroundColor(android.graphics.Color.parseColor("#0F1526"))
                webViewRef[0] = this
                loadUrl(radarUrl)
            }
        },
        update = { webView ->
            val newUrl = buildRainViewerUrl(latitude, longitude)
            if (webView.url != newUrl) {
                webView.loadUrl(newUrl)
            }
        },
        modifier = modifier,
    )
}

/**
 * Build a Windy.com embedded map URL: dark overlay, radar layer,
 * centered on location. Windy has significantly better radar quality
 * and global coverage than RainViewer.
 */
private fun buildRainViewerUrl(lat: Double, lon: Double, zoom: Int = 8): String {
    return "https://embed.windy.com/embed.html" +
        "?type=map" +
        "&location=coordinates" +
        "&metricRain=default" +
        "&metricTemp=default" +
        "&metricWind=default" +
        "&zoom=$zoom" +
        "&overlay=radar" +
        "&product=radar" +
        "&level=surface" +
        "&lat=$lat" +
        "&lon=$lon" +
        "&message=true" +
        "&calendar=now" +
        "&pressure=true" +
        "&type=map" +
        "&menu=" +
        "&forecast=12" +
        "&darkMode=true"
}
