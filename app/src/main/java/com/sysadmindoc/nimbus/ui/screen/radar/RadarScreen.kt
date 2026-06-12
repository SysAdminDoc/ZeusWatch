package com.sysadmindoc.nimbus.ui.screen.radar

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.api.LightningStrike
import com.sysadmindoc.nimbus.data.model.CommunityReport
import com.sysadmindoc.nimbus.data.model.ReportCondition
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.RadarProvider
import com.sysadmindoc.nimbus.ui.component.GlassActionButton
import com.sysadmindoc.nimbus.ui.component.NimbusStatusBadge
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.PremiumMessageCard
import com.sysadmindoc.nimbus.ui.component.ReportSubmitSheet
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
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
    val coordinates = RadarCoordinates(resolvedLat, resolvedLon)
    val canSubmitReport = canOpenCommunityReport(isOffline, resolvedLat, resolvedLon)
    val renderState = RadarRenderState(
        settings = settings,
        radarState = radarState,
        strikes = strikes,
        nearbyReports = nearbyReports,
        reportSubmitState = reportSubmitState,
        isOffline = isOffline,
        selectedLayer = selectedLayer,
        canSubmitReport = canSubmitReport,
        showReportFab = shouldShowRadarReportFab(
            canSubmitReport = canSubmitReport,
            provider = settings.radarProvider,
            embedded = false,
        ),
    )
    val actions = RadarActions(
        onBack = onBack,
        onLayerSelected = { selectedLayer = it },
        onCameraMoveStarted = viewModel::onMapInteractionStart,
        onCameraIdle = viewModel::onMapInteractionEnd,
        onTogglePlayback = viewModel::togglePlayback,
        onSeekToFrame = viewModel::seekToFrame,
        onRetryFrames = { viewModel.loadFrames(force = true) },
        onOpenReportSheet = { showReportSheet = true },
        onDismissReportSheet = {
            showReportSheet = false
            viewModel.resetReportState()
        },
        onSubmitReport = { coords, condition, note ->
            viewModel.submitReport(coords.latitude, coords.longitude, condition, note)
        },
    )

    RadarLocationReportsEffect(coordinates, viewModel::loadNearbyReports)
    RadarDisconnectOnDisposeEffect(viewModel::pausePlayback, viewModel::disconnectLightning)
    RadarPlaybackConnectionEffect(
        selectedLayer = selectedLayer,
        provider = settings.radarProvider,
        isOffline = isOffline,
        loadFrames = { viewModel.loadFrames() },
        pausePlayback = viewModel::pausePlayback,
        connectLightning = viewModel::connectLightning,
        disconnectLightning = viewModel::disconnectLightning,
    )
    RadarReportSheetEffects(
        result = reportSubmitState.result,
        canSubmitReport = canSubmitReport,
        showReportSheet = showReportSheet,
        dismissReportSheet = actions.onDismissReportSheet,
    )

    PredictiveBackScaffold(onBack = onBack) {
        RadarContentFrame(renderState, coordinates, actions)
        RadarReportSheet(showReportSheet, renderState.reportSubmitState, coordinates, actions)
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
    var selectedLayer by remember { mutableStateOf(RadarLayer.RADAR) }
    val coordinates = RadarCoordinates(latitude, longitude)
    val canSubmitReport = canOpenCommunityReport(isOffline, latitude, longitude)
    val renderState = RadarRenderState(
        settings = settings,
        radarState = radarState,
        strikes = strikes,
        nearbyReports = nearbyReports,
        reportSubmitState = reportSubmitState,
        isOffline = isOffline,
        selectedLayer = selectedLayer,
        canSubmitReport = canSubmitReport,
        showReportFab = shouldShowRadarReportFab(
            canSubmitReport = canSubmitReport,
            provider = settings.radarProvider,
            embedded = true,
        ),
    )
    val actions = RadarActions(
        onBack = null,
        onLayerSelected = { selectedLayer = it },
        onCameraMoveStarted = viewModel::onMapInteractionStart,
        onCameraIdle = viewModel::onMapInteractionEnd,
        onTogglePlayback = viewModel::togglePlayback,
        onSeekToFrame = viewModel::seekToFrame,
        onRetryFrames = { viewModel.loadFrames(force = true) },
        onOpenReportSheet = { showReportSheet = true },
        onDismissReportSheet = {
            showReportSheet = false
            viewModel.resetReportState()
        },
        onSubmitReport = { coords, condition, note ->
            viewModel.submitReport(coords.latitude, coords.longitude, condition, note)
        },
    )

    RadarLocationReportsEffect(coordinates, viewModel::loadNearbyReports)
    RadarDisconnectOnDisposeEffect(viewModel::pausePlayback, viewModel::disconnectLightning)
    RadarPlaybackConnectionEffect(
        selectedLayer = selectedLayer,
        provider = settings.radarProvider,
        isOffline = isOffline,
        loadFrames = { viewModel.loadFrames() },
        pausePlayback = viewModel::pausePlayback,
        connectLightning = viewModel::connectLightning,
        disconnectLightning = viewModel::disconnectLightning,
    )
    RadarReportSheetEffects(
        result = reportSubmitState.result,
        canSubmitReport = canSubmitReport,
        showReportSheet = showReportSheet,
        dismissReportSheet = actions.onDismissReportSheet,
    )

    RadarContentFrame(renderState, coordinates, actions)
    RadarReportSheet(showReportSheet, renderState.reportSubmitState, coordinates, actions)
}

private data class RadarCoordinates(
    val latitude: Double,
    val longitude: Double,
)

private data class RadarRenderState(
    val settings: NimbusSettings,
    val radarState: RadarUiState,
    val strikes: List<LightningStrike>,
    val nearbyReports: List<CommunityReport>,
    val reportSubmitState: ReportSubmitState,
    val isOffline: Boolean,
    val selectedLayer: RadarLayer,
    val canSubmitReport: Boolean,
    val showReportFab: Boolean,
)

private data class RadarActions(
    val onBack: (() -> Unit)?,
    val onLayerSelected: (RadarLayer) -> Unit,
    val onCameraMoveStarted: () -> Unit,
    val onCameraIdle: () -> Unit,
    val onTogglePlayback: () -> Unit,
    val onSeekToFrame: (Int) -> Unit,
    val onRetryFrames: () -> Unit,
    val onOpenReportSheet: () -> Unit,
    val onDismissReportSheet: () -> Unit,
    val onSubmitReport: (RadarCoordinates, ReportCondition, String) -> Unit,
)

@Composable
private fun RadarLocationReportsEffect(
    coordinates: RadarCoordinates,
    loadNearbyReports: (Double, Double) -> Unit,
) {
    LaunchedEffect(coordinates.latitude, coordinates.longitude) {
        if (coordinates.latitude != 0.0 && coordinates.longitude != 0.0) {
            loadNearbyReports(coordinates.latitude, coordinates.longitude)
        }
    }
}

@Composable
private fun RadarDisconnectOnDisposeEffect(
    pausePlayback: () -> Unit,
    disconnectLightning: () -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose {
            // Stop the off-screen frame loop and the lightning socket when radar
            // leaves composition (e.g. a phone tab switch where the VM survives).
            pausePlayback()
            disconnectLightning()
        }
    }
}

@Composable
private fun RadarPlaybackConnectionEffect(
    selectedLayer: RadarLayer,
    provider: RadarProvider,
    isOffline: Boolean,
    loadFrames: () -> Unit,
    pausePlayback: () -> Unit,
    connectLightning: () -> Unit,
    disconnectLightning: () -> Unit,
) {
    LaunchedEffect(selectedLayer, provider, isOffline) {
        val useNativeRadar = provider.supportsNativePlayback && !isOffline
        if (useNativeRadar && selectedLayer == RadarLayer.RADAR) {
            loadFrames()
        } else {
            // Frame playback is only meaningful on the native radar layer; stop
            // the loop when the provider or layer switches away from it.
            pausePlayback()
        }
        if (useNativeRadar && (selectedLayer == RadarLayer.LIGHTNING || selectedLayer == RadarLayer.RADAR)) {
            connectLightning()
        } else {
            disconnectLightning()
        }
    }
}

@Composable
private fun RadarReportSheetEffects(
    result: String?,
    canSubmitReport: Boolean,
    showReportSheet: Boolean,
    dismissReportSheet: () -> Unit,
) {
    LaunchedEffect(result) {
        if (result == "success") {
            kotlinx.coroutines.delay(1500)
            dismissReportSheet()
        }
    }

    LaunchedEffect(canSubmitReport) {
        if (!canSubmitReport && showReportSheet) {
            dismissReportSheet()
        }
    }
}

@Composable
private fun RadarContentFrame(
    state: RadarRenderState,
    coordinates: RadarCoordinates,
    actions: RadarActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient),
    ) {
        RadarMapSurface(state, coordinates, actions)
        RadarReportFab(state, actions)
    }
}

@Composable
private fun BoxScope.RadarMapSurface(
    state: RadarRenderState,
    coordinates: RadarCoordinates,
    actions: RadarActions,
) {
    when {
        state.isOffline -> RadarOfflineCard()
        state.settings.radarProvider.supportsNativePlayback -> RadarNativeContent(state, coordinates, actions)
        else -> RadarWebContent(state.settings.radarProvider, coordinates, actions.onBack)
    }
}

@Composable
private fun BoxScope.RadarNativeContent(
    state: RadarRenderState,
    coordinates: RadarCoordinates,
    actions: RadarActions,
) {
    val isRadarMode = state.selectedLayer == RadarLayer.RADAR
    val isLightningMode = state.selectedLayer == RadarLayer.LIGHTNING
    val showLightning = isLightningMode || isRadarMode
    RadarMapView(
        latitude = coordinates.latitude,
        longitude = coordinates.longitude,
        currentTileUrl = if (isRadarMode) state.radarState.currentFrame?.tileUrl else null,
        previousTileUrl = previousRadarTileUrl(isRadarMode, state.radarState),
        overlayTileUrl = if (isRadarMode || isLightningMode) null else state.selectedLayer.tileUrlTemplate,
        lightningStrikes = if (showLightning) state.strikes else emptyList(),
        communityReports = state.nearbyReports,
        onCameraMoveStarted = actions.onCameraMoveStarted,
        onCameraIdle = actions.onCameraIdle,
        modifier = Modifier.fillMaxSize(),
    )
    RadarTopControls(
        providerLabel = state.settings.radarProvider.label,
        selectedLayer = state.selectedLayer,
        onLayerSelected = actions.onLayerSelected,
        onBack = actions.onBack,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth(),
    )
    if (isRadarMode) {
        RadarPlaybackAndStatus(state, actions)
    }
}

@Composable
private fun BoxScope.RadarWebContent(
    provider: RadarProvider,
    coordinates: RadarCoordinates,
    onBack: (() -> Unit)?,
) {
    RadarWebView(
        provider = provider,
        latitude = coordinates.latitude,
        longitude = coordinates.longitude,
        modifier = Modifier.fillMaxSize(),
    )
    RadarTopControls(
        providerLabel = provider.label,
        selectedLayer = null,
        onLayerSelected = null,
        onBack = onBack,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth(),
    )
}

private fun previousRadarTileUrl(
    isRadarMode: Boolean,
    radarState: RadarUiState,
): String? {
    if (!isRadarMode) return null
    val frames = radarState.frameSet?.allFrames ?: return null
    if (frames.isEmpty()) return null
    // Wrap with modular arithmetic so the loop-around frame (index 0) shows the
    // last frame as its predecessor instead of a blank cross-fade layer, which
    // otherwise flickers on every playback cycle.
    val prevIndex = (radarState.currentFrameIndex - 1 + frames.size) % frames.size
    return frames.getOrNull(prevIndex)?.tileUrl
}

@Composable
private fun BoxScope.RadarPlaybackAndStatus(
    state: RadarRenderState,
    actions: RadarActions,
) {
    if (state.radarState.frameSet != null) {
        RadarPlaybackControls(
            isPlaying = state.radarState.isPlaying,
            playbackEnabled = state.radarState.canAnimatePlayback,
            currentFrame = state.radarState.currentFrameIndex,
            totalFrames = state.radarState.totalFrames,
            pastFrameCount = state.radarState.pastFrameCount,
            currentTimestamp = state.radarState.currentFrame?.timestamp,
            timeFormat = state.settings.timeFormat,
            onTogglePlayback = actions.onTogglePlayback,
            onSeekToFrame = actions.onSeekToFrame,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
    RadarStatusOverlay(state, actions.onRetryFrames)
}

@Composable
private fun BoxScope.RadarStatusOverlay(
    state: RadarRenderState,
    onRetryFrames: () -> Unit,
) {
    val radarLoadingTitle = stringResource(R.string.radar_loading_title)
    val radarLoadingMessage = stringResource(R.string.radar_loading_message)
    val radarUnavailableTitle = stringResource(R.string.radar_unavailable_title)
    val radarUnavailableMessage = stringResource(R.string.radar_unavailable_message)

    when {
        shouldShowRadarLoadingOverlay(
            provider = state.settings.radarProvider,
            selectedLayer = state.selectedLayer,
            radarState = state.radarState,
            isOffline = state.isOffline,
        ) -> RadarStatusCard(
            title = radarLoadingTitle,
            message = radarLoadingMessage,
            isLoading = true,
            modifier = Modifier.align(Alignment.Center),
        )
        shouldShowRadarErrorOverlay(
            provider = state.settings.radarProvider,
            selectedLayer = state.selectedLayer,
            radarState = state.radarState,
            isOffline = state.isOffline,
        ) -> RadarStatusCard(
            title = radarUnavailableTitle,
            message = state.radarState.error ?: radarUnavailableMessage,
            onRetry = onRetryFrames,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun BoxScope.RadarReportFab(
    state: RadarRenderState,
    actions: RadarActions,
) {
    if (!state.showReportFab) return

    val reportWeatherConditionsDescription = stringResource(R.string.report_weather_conditions_cd)
    val playbackVisible =
        state.settings.radarProvider.supportsNativePlayback &&
            !state.isOffline &&
            state.selectedLayer == RadarLayer.RADAR &&
            state.radarState.frameSet != null
    FloatingActionButton(
        onClick = actions.onOpenReportSheet,
        containerColor = NimbusBlueAccent.copy(alpha = 0.95f),
        contentColor = NimbusTextPrimary,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { contentDescription = reportWeatherConditionsDescription }
            .padding(end = 16.dp, bottom = radarFabBottomPadding(showPlaybackControls = playbackVisible)),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
    }
}

@Composable
private fun RadarReportSheet(
    showReportSheet: Boolean,
    reportSubmitState: ReportSubmitState,
    coordinates: RadarCoordinates,
    actions: RadarActions,
) {
    if (!showReportSheet) return

    ReportSubmitSheet(
        isSubmitting = reportSubmitState.isSubmitting,
        submitResult = reportSubmitState.result,
        onSubmit = { condition, note -> actions.onSubmitReport(coordinates, condition, note) },
        onDismiss = actions.onDismissReportSheet,
    )
}

@Composable
private fun RadarTopControls(
    providerLabel: String,
    selectedLayer: RadarLayer?,
    onLayerSelected: ((RadarLayer) -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val radarTitle = stringResource(R.string.nav_radar)
    val interactiveMapLabel = stringResource(R.string.radar_interactive_map)
    val layerProviderLabel = selectedLayer?.let {
        stringResource(R.string.radar_layer_with_provider, it.label, providerLabel)
    } ?: providerLabel

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = radarTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = NimbusTextPrimary,
                )
                Text(
                    text = layerProviderLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadarInfoBadge(
                text = if (selectedLayer == null) interactiveMapLabel else selectedLayer.label,
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
    GlassActionButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.common_back),
        onClick = onBack,
        modifier = modifier,
    )
}

@SuppressLint("SetJavaScriptEnabled")
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
                            if (isAllowedUrl(uri)) {
                                return false
                            }
                            // Blocked main-frame navigations are handed to the
                            // browser so external links don't silently dead-end;
                            // non-allowlisted subframe navigations are blocked
                            // outright (same allowlist as the main frame).
                            if (request.isForMainFrame) {
                                try {
                                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: Exception) {
                                    // No browser available — swallow and just block.
                                }
                            }
                            return true
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
                        allowFileAccess = false
                        allowContentAccess = false
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
private fun RadarInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    NimbusStatusBadge(
        text = text,
        tint = NimbusTextSecondary,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
private fun RadarStatusCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    PremiumMessageCard(
        title = title,
        message = message,
        icon = Icons.Filled.CloudOff,
        loading = isLoading,
        primaryActionLabel = if (isLoading) null else stringResource(R.string.retry),
        onPrimaryAction = if (isLoading) null else onRetry,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp)
    )
}

@Composable
private fun RadarOfflineCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        PremiumMessageCard(
            title = stringResource(R.string.radar_offline_title),
            message = stringResource(R.string.radar_offline_message),
            icon = Icons.Filled.CloudOff,
            modifier = Modifier
                .padding(24.dp),
            badgeText = stringResource(R.string.radar_offline_badge),
        )
    }
}

internal fun canOpenCommunityReport(
    isOffline: Boolean,
    latitude: Double,
    longitude: Double,
): Boolean = !isOffline && (latitude != 0.0 || longitude != 0.0)

internal fun shouldShowRadarReportFab(
    canSubmitReport: Boolean,
    provider: RadarProvider,
    embedded: Boolean,
): Boolean {
    return canSubmitReport && (!embedded || provider.supportsNativePlayback)
}

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
