package com.sysadmindoc.nimbus.ui.screen.radar

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.sysadmindoc.nimbus.ui.component.ReportSubmitSheet
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
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
    val strikes by viewModel.lightningStrikes.collectAsStateWithLifecycle()
    val nearbyReports by viewModel.nearbyReports.collectAsStateWithLifecycle()
    val reportSubmitState by viewModel.reportSubmitState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    var selectedLayer by remember { mutableStateOf(RadarLayer.RADAR) }
    var showReportSheet by remember { mutableStateOf(false) }

    // Load community reports when location is resolved
    LaunchedEffect(resolvedLat, resolvedLon) {
        if (resolvedLat != 0.0 && resolvedLon != 0.0) {
            viewModel.loadNearbyReports(resolvedLat, resolvedLon)
        }
    }

    // Reload radar frames when switching back to radar mode;
    // connect/disconnect Blitzortung WebSocket based on layer selection.
    LaunchedEffect(selectedLayer) {
        if (selectedLayer == RadarLayer.RADAR) {
            viewModel.loadFrames()
        }
        if (selectedLayer == RadarLayer.LIGHTNING || selectedLayer == RadarLayer.RADAR) {
            viewModel.connectLightning()
        } else {
            viewModel.disconnectLightning()
        }
    }

    // Auto-dismiss report sheet on success
    LaunchedEffect(reportSubmitState.result) {
        if (reportSubmitState.result == "success") {
            kotlinx.coroutines.delay(1500)
            showReportSheet = false
            viewModel.resetReportState()
        }
    }

    PredictiveBackScaffold(onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NimbusNavyDark),
        ) {
            if (isOffline) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = NimbusTextPrimary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Radar requires an internet connection",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NimbusTextPrimary,
                        )
                    }
                }
            } else when (settings.radarProvider) {
                RadarProvider.NATIVE_MAPLIBRE -> {
                    val isRadarMode = selectedLayer == RadarLayer.RADAR
                    val isLightningMode = selectedLayer == RadarLayer.LIGHTNING
                    val showLightning = isLightningMode || isRadarMode
                    RadarMapView(
                        latitude = resolvedLat,
                        longitude = resolvedLon,
                        currentTileUrl = if (isRadarMode) radarState.currentFrame?.tileUrl else null,
                        previousTileUrl = if (isRadarMode) {
                            radarState.frameSet?.allFrames
                                ?.getOrNull(radarState.currentFrameIndex - 1)?.tileUrl
                        } else null,
                        overlayTileUrl = if (isRadarMode || isLightningMode) null else selectedLayer.tileUrlTemplate,
                        lightningStrikes = if (showLightning) strikes else emptyList(),
                        communityReports = nearbyReports,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Layer selector chips (top, offset for back button)
                    RadarLayerSelector(
                        selectedLayer = selectedLayer,
                        onLayerSelected = { selectedLayer = it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 56.dp),
                    )
                    // Playback controls overlay (bottom) - only shown in radar mode
                    if (isRadarMode) {
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

            // Community report FAB
            FloatingActionButton(
                onClick = { showReportSheet = true },
                containerColor = NimbusBlueAccent,
                contentColor = NimbusTextPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Report weather conditions",
                )
            }
        }

        // Report submission bottom sheet
        if (showReportSheet) {
            ReportSubmitSheet(
                isSubmitting = reportSubmitState.isSubmitting,
                submitResult = reportSubmitState.result,
                onSubmit = { condition, note ->
                    viewModel.submitReport(resolvedLat, resolvedLon, condition, note)
                },
                onDismiss = {
                    showReportSheet = false
                    viewModel.resetReportState()
                },
            )
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
    val strikes by viewModel.lightningStrikes.collectAsStateWithLifecycle()
    val nearbyReports by viewModel.nearbyReports.collectAsStateWithLifecycle()
    val reportSubmitState by viewModel.reportSubmitState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    var showReportSheet by remember { mutableStateOf(false) }

    // Load community reports for this location
    LaunchedEffect(latitude, longitude) {
        if (latitude != 0.0 && longitude != 0.0) {
            viewModel.loadNearbyReports(latitude, longitude)
        }
    }

    // Auto-dismiss report sheet on success
    LaunchedEffect(reportSubmitState.result) {
        if (reportSubmitState.result == "success") {
            kotlinx.coroutines.delay(1500)
            showReportSheet = false
            viewModel.resetReportState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark),
    ) {
        var selectedLayer by remember { mutableStateOf(RadarLayer.RADAR) }

        // Stop/resume radar playback when switching layers;
        // connect/disconnect Blitzortung WebSocket based on layer selection.
        LaunchedEffect(selectedLayer) {
            if (selectedLayer == RadarLayer.RADAR) {
                viewModel.loadFrames()
            }
            if (selectedLayer == RadarLayer.LIGHTNING || selectedLayer == RadarLayer.RADAR) {
                viewModel.connectLightning()
            } else {
                viewModel.disconnectLightning()
            }
        }

        if (isOffline) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = NimbusTextPrimary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Radar requires an internet connection",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NimbusTextPrimary,
                    )
                }
            }
        } else when (settings.radarProvider) {
            RadarProvider.NATIVE_MAPLIBRE -> {
                val isRadarMode = selectedLayer == RadarLayer.RADAR
                val isLightningMode = selectedLayer == RadarLayer.LIGHTNING
                val showLightning = isLightningMode || isRadarMode
                RadarMapView(
                    latitude = latitude,
                    longitude = longitude,
                    currentTileUrl = if (isRadarMode) radarState.currentFrame?.tileUrl else null,
                    previousTileUrl = if (isRadarMode) {
                        radarState.frameSet?.allFrames
                            ?.getOrNull(radarState.currentFrameIndex - 1)?.tileUrl
                    } else null,
                    overlayTileUrl = if (isRadarMode || isLightningMode) null else selectedLayer.tileUrlTemplate,
                    lightningStrikes = if (showLightning) strikes else emptyList(),
                    communityReports = nearbyReports,
                    modifier = Modifier.fillMaxSize(),
                )
                // Layer selector chips (top)
                RadarLayerSelector(
                    selectedLayer = selectedLayer,
                    onLayerSelected = { selectedLayer = it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                )
                // Playback controls overlay (bottom) - only shown in radar mode
                if (isRadarMode) {
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
            }
            RadarProvider.WINDY_WEBVIEW -> {
                RadarWebView(
                    latitude = latitude,
                    longitude = longitude,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Community report FAB
        FloatingActionButton(
            onClick = { showReportSheet = true },
            containerColor = NimbusBlueAccent,
            contentColor = NimbusTextPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Report weather conditions",
            )
        }
    }

    // Report submission bottom sheet
    if (showReportSheet) {
        ReportSubmitSheet(
            isSubmitting = reportSubmitState.isSubmitting,
            submitResult = reportSubmitState.result,
            onSubmit = { condition, note ->
                viewModel.submitReport(latitude, longitude, condition, note)
            },
            onDismiss = {
                showReportSheet = false
                viewModel.resetReportState()
            },
        )
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
                webViewClient = object : WebViewClient() {
                    private val allowedHosts = listOf(
                        "embed.windy.com",
                    )
                    private val allowedPatterns = listOf(
                        Regex(""".*\.openstreetmap\.org$"""),
                        Regex(""".*\.cartocdn\.com$"""),
                    )
                    private fun isAllowedUrl(uri: Uri): Boolean {
                        val host = uri.host ?: return false
                        return host in allowedHosts ||
                            allowedPatterns.any { it.matches(host) }
                    }
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val uri = request?.url ?: return false
                        return !isAllowedUrl(uri)
                    }
                }
                webChromeClient = WebChromeClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
