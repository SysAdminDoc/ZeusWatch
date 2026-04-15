package com.sysadmindoc.nimbus.ui.screen.radar

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.RadarProvider
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.ReportSubmitSheet
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary

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
    val canSubmitReport = canOpenCommunityReport(isOffline, resolvedLat, resolvedLon)

    // Load community reports when location is resolved
    LaunchedEffect(resolvedLat, resolvedLon) {
        if (resolvedLat != 0.0 && resolvedLon != 0.0) {
            viewModel.loadNearbyReports(resolvedLat, resolvedLon)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnectLightning() }
    }

    LaunchedEffect(selectedLayer, settings.radarProvider, isOffline) {
        val useNativeRadar = settings.radarProvider.supportsNativePlayback && !isOffline
        if (useNativeRadar && selectedLayer == RadarLayer.RADAR) {
            viewModel.loadFrames()
        }
        if (useNativeRadar && (selectedLayer == RadarLayer.LIGHTNING || selectedLayer == RadarLayer.RADAR)) {
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

    LaunchedEffect(canSubmitReport) {
        if (!canSubmitReport && showReportSheet) {
            showReportSheet = false
            viewModel.resetReportState()
        }
    }

    PredictiveBackScaffold(onBack = onBack) {
        val fullScreenPlaybackVisible =
            settings.radarProvider.supportsNativePlayback &&
                !isOffline &&
                selectedLayer == RadarLayer.RADAR &&
                radarState.frameSet != null
        val fullScreenFabBottomPadding = radarFabBottomPadding(showPlaybackControls = fullScreenPlaybackVisible)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NimbusBackgroundGradient),
        ) {
            if (isOffline) {
                RadarOfflineCard()
            } else if (settings.radarProvider.supportsNativePlayback) {
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
                    onCameraMoveStarted = { viewModel.onMapInteractionStart() },
                    onCameraIdle = { viewModel.onMapInteractionEnd() },
                    modifier = Modifier.fillMaxSize(),
                )
                RadarTopControls(
                    providerLabel = settings.radarProvider.label,
                    selectedLayer = selectedLayer,
                    onLayerSelected = { selectedLayer = it },
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
                // Playback controls overlay (bottom) - only shown in radar mode
                if (isRadarMode) {
                    val showLoadingOverlay = shouldShowRadarLoadingOverlay(
                        provider = settings.radarProvider,
                        selectedLayer = selectedLayer,
                        radarState = radarState,
                        isOffline = isOffline,
                    )
                    val showErrorOverlay = shouldShowRadarErrorOverlay(
                        provider = settings.radarProvider,
                        selectedLayer = selectedLayer,
                        radarState = radarState,
                        isOffline = isOffline,
                    )

                    if (radarState.frameSet != null) {
                        RadarPlaybackControls(
                            isPlaying = radarState.isPlaying,
                            playbackEnabled = radarState.canAnimatePlayback,
                            currentFrame = radarState.currentFrameIndex,
                            totalFrames = radarState.totalFrames,
                            pastFrameCount = radarState.pastFrameCount,
                            currentTimestamp = radarState.currentFrame?.timestamp,
                            onTogglePlayback = { viewModel.togglePlayback() },
                            onSeekToFrame = { viewModel.seekToFrame(it) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }

                    when {
                        showLoadingOverlay -> RadarStatusCard(
                            title = "Loading Radar",
                            message = "Fetching the latest radar frames for this area.",
                            isLoading = true,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        showErrorOverlay -> RadarStatusCard(
                            title = "Radar Unavailable",
                            message = radarState.error ?: "We couldn't load radar frames right now.",
                            onRetry = { viewModel.loadFrames(force = true) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            } else {
                RadarWebView(
                    provider = settings.radarProvider,
                    latitude = resolvedLat,
                    longitude = resolvedLon,
                    modifier = Modifier.fillMaxSize(),
                )
                RadarTopControls(
                    providerLabel = settings.radarProvider.label,
                    selectedLayer = null,
                    onLayerSelected = null,
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }

            // Community report FAB
            if (canSubmitReport) {
                FloatingActionButton(
                    onClick = { showReportSheet = true },
                    containerColor = NimbusBlueAccent.copy(alpha = 0.95f),
                    contentColor = NimbusTextPrimary,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(end = 16.dp, bottom = fullScreenFabBottomPadding),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Report weather conditions",
                    )
                }
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
    val canSubmitReport = canOpenCommunityReport(isOffline, latitude, longitude)

    // Load community reports for this location
    LaunchedEffect(latitude, longitude) {
        if (latitude != 0.0 && longitude != 0.0) {
            viewModel.loadNearbyReports(latitude, longitude)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnectLightning() }
    }

    LaunchedEffect(reportSubmitState.result) {
        if (reportSubmitState.result == "success") {
            kotlinx.coroutines.delay(1500)
            showReportSheet = false
            viewModel.resetReportState()
        }
    }

    LaunchedEffect(canSubmitReport) {
        if (!canSubmitReport && showReportSheet) {
            showReportSheet = false
            viewModel.resetReportState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient),
    ) {
        var selectedLayer by remember { mutableStateOf(RadarLayer.RADAR) }
        val tabPlaybackVisible =
            settings.radarProvider.supportsNativePlayback &&
                !isOffline &&
                selectedLayer == RadarLayer.RADAR &&
                radarState.frameSet != null
        val tabFabBottomPadding = radarFabBottomPadding(showPlaybackControls = tabPlaybackVisible)

        LaunchedEffect(selectedLayer, settings.radarProvider, isOffline) {
            val useNativeRadar = settings.radarProvider.supportsNativePlayback && !isOffline
            if (useNativeRadar && selectedLayer == RadarLayer.RADAR) {
                viewModel.loadFrames()
            }
            if (useNativeRadar && (selectedLayer == RadarLayer.LIGHTNING || selectedLayer == RadarLayer.RADAR)) {
                viewModel.connectLightning()
            } else {
                viewModel.disconnectLightning()
            }
        }

        if (isOffline) {
            RadarOfflineCard()
        } else if (settings.radarProvider.supportsNativePlayback) {
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
                onCameraMoveStarted = { viewModel.onMapInteractionStart() },
                onCameraIdle = { viewModel.onMapInteractionEnd() },
                modifier = Modifier.fillMaxSize(),
            )
            RadarTopControls(
                providerLabel = settings.radarProvider.label,
                selectedLayer = selectedLayer,
                onLayerSelected = { selectedLayer = it },
                onBack = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
            // Playback controls overlay (bottom) - only shown in radar mode
            if (isRadarMode && radarState.frameSet != null) {
                RadarPlaybackControls(
                    isPlaying = radarState.isPlaying,
                    playbackEnabled = radarState.canAnimatePlayback,
                    currentFrame = radarState.currentFrameIndex,
                    totalFrames = radarState.totalFrames,
                    pastFrameCount = radarState.pastFrameCount,
                    currentTimestamp = radarState.currentFrame?.timestamp,
                    onTogglePlayback = { viewModel.togglePlayback() },
                    onSeekToFrame = { viewModel.seekToFrame(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }

            when {
                shouldShowRadarLoadingOverlay(
                    provider = settings.radarProvider,
                    selectedLayer = selectedLayer,
                    radarState = radarState,
                    isOffline = isOffline,
                ) -> RadarStatusCard(
                    title = "Loading Radar",
                    message = "Fetching the latest radar frames for this area.",
                    isLoading = true,
                    modifier = Modifier.align(Alignment.Center),
                )
                shouldShowRadarErrorOverlay(
                    provider = settings.radarProvider,
                    selectedLayer = selectedLayer,
                    radarState = radarState,
                    isOffline = isOffline,
                ) -> RadarStatusCard(
                    title = "Radar Unavailable",
                    message = radarState.error ?: "We couldn't load radar frames right now.",
                    onRetry = { viewModel.loadFrames(force = true) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            RadarWebView(
                provider = settings.radarProvider,
                latitude = latitude,
                longitude = longitude,
                modifier = Modifier.fillMaxSize(),
            )
            RadarTopControls(
                providerLabel = settings.radarProvider.label,
                selectedLayer = null,
                onLayerSelected = null,
                onBack = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
        }

        // Community report FAB
        if (canSubmitReport) {
            FloatingActionButton(
                onClick = { showReportSheet = true },
                containerColor = NimbusBlueAccent,
                contentColor = NimbusTextPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 16.dp, bottom = tabFabBottomPadding),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Report weather conditions",
                )
            }
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
private fun RadarTopControls(
    providerLabel: String,
    selectedLayer: RadarLayer?,
    onLayerSelected: ((RadarLayer) -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(radarTopControlsSpacing(showBackButton = onBack != null)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                RadarBackButton(onBack = onBack)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            RadarInfoPill(
                text = providerLabel,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }

        if (selectedLayer != null && onLayerSelected != null) {
            RadarLayerSelector(
                selectedLayer = selectedLayer,
                onLayerSelected = onLayerSelected,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun RadarBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.76f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = NimbusTextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RadarWebView(
    provider: RadarProvider,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val radarUrl = buildRadarProviderUrl(provider, latitude, longitude)
    val webViewRef = remember(provider) { arrayOfNulls<WebView>(1) }

    DisposableEffect(provider) {
        onDispose { webViewRef[0]?.destroy() }
    }

    key(provider) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewClient = object : WebViewClient() {
                        private fun isAllowedUrl(uri: Uri): Boolean {
                            if (uri.scheme in setOf("about", "blob", "data")) {
                                return true
                            }
                            val host = uri.host ?: return false
                            return when (provider) {
                                RadarProvider.WINDY_WEBVIEW -> {
                                    host == "embed.windy.com" ||
                                        WINDY_ALLOWED_HOST_PATTERNS.any { it.matches(host) }
                                }
                                RadarProvider.NWS_WEBVIEW,
                                RadarProvider.NWS_STANDARD_WEBVIEW -> {
                                    host == "radar.weather.gov" ||
                                        NWS_ALLOWED_HOST_PATTERNS.any { it.matches(host) }
                                }
                                RadarProvider.NATIVE_MAPLIBRE -> false
                            }
                        }
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val uri = request?.url ?: return false
                            if (request.isForMainFrame.not()) {
                                return false
                            }
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
                val newUrl = buildRadarProviderUrl(provider, latitude, longitude)
                if (webView.url != newUrl) {
                    webView.loadUrl(newUrl)
                }
            },
            modifier = modifier,
        )
    }
}

/**
 * Build a provider-specific public radar URL.
 */
private fun buildRadarProviderUrl(provider: RadarProvider, lat: Double, lon: Double, zoom: Int = 8): String {
    return when (provider) {
        RadarProvider.WINDY_WEBVIEW -> buildWindyRadarUrl(lat, lon, zoom)
        RadarProvider.NWS_WEBVIEW -> "https://radar.weather.gov/"
        RadarProvider.NWS_STANDARD_WEBVIEW -> "https://radar.weather.gov/standard"
        RadarProvider.NATIVE_MAPLIBRE -> buildWindyRadarUrl(lat, lon, zoom)
    }
}

private fun buildWindyRadarUrl(lat: Double, lon: Double, zoom: Int = 8): String {
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

private val WINDY_ALLOWED_HOST_PATTERNS = listOf(
    Regex(""".*\.openstreetmap\.org$"""),
    Regex(""".*\.cartocdn\.com$"""),
)

private val NWS_ALLOWED_HOST_PATTERNS = listOf(
    Regex("""(^|.*\.)weather\.gov$"""),
    Regex("""(^|.*\.)noaa\.gov$"""),
    Regex("""(^|.*\.)ncep\.noaa\.gov$"""),
    Regex("""(^|.*\.)digitalgov\.gov$"""),
)

@Composable
private fun RadarInfoPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.8f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RadarStatusCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.82f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(30.dp))
            .padding(horizontal = 28.dp, vertical = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLoading) NimbusBlueAccent.copy(alpha = 0.14f)
                        else NimbusTextPrimary.copy(alpha = 0.08f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = NimbusBlueAccent,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = NimbusTextPrimary,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NimbusTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                textAlign = TextAlign.Center,
            )
            onRetry?.let {
                Spacer(Modifier.height(18.dp))
                Button(onClick = it) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun RadarOfflineCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop.copy(alpha = 0.8f),
                            NimbusGlassBottom,
                        ),
                    ),
                )
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 28.dp, vertical = 30.dp),
        ) {
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
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Reconnect to resume live radar, lightning, and community reports.",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun canOpenCommunityReport(
    isOffline: Boolean,
    latitude: Double,
    longitude: Double,
): Boolean = !isOffline && (latitude != 0.0 || longitude != 0.0)

internal fun radarTopControlsSpacing(showBackButton: Boolean): Dp {
    return if (showBackButton) 10.dp else 8.dp
}

internal fun radarFabBottomPadding(showPlaybackControls: Boolean): Dp {
    return if (showPlaybackControls) 124.dp else 16.dp
}

internal fun shouldShowRadarLoadingOverlay(
    provider: RadarProvider,
    selectedLayer: RadarLayer,
    radarState: RadarUiState,
    isOffline: Boolean,
): Boolean {
    return !isOffline &&
        provider.supportsNativePlayback &&
        selectedLayer == RadarLayer.RADAR &&
        radarState.isLoading &&
        radarState.frameSet == null
}

internal fun shouldShowRadarErrorOverlay(
    provider: RadarProvider,
    selectedLayer: RadarLayer,
    radarState: RadarUiState,
    isOffline: Boolean,
): Boolean {
    return !isOffline &&
        provider.supportsNativePlayback &&
        selectedLayer == RadarLayer.RADAR &&
        radarState.error != null &&
        radarState.frameSet == null
}
