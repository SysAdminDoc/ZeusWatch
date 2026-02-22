package com.sysadmindoc.nimbus.ui.screen.radar

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * MapLibre composable wrapper displaying radar tiles from RainViewer.
 * Uses CartoCDN Dark Matter basemap (no API key).
 * Manages radar tile layers with opacity crossfade for animation.
 */
@Composable
fun RadarMapView(
    latitude: Double,
    longitude: Double,
    zoom: Double = 5.0,
    currentTileUrl: String?,
    previousTileUrl: String?,
    onMapReady: () -> Unit = {},
    onCameraMoveStarted: () -> Unit = {},
    onCameraIdle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre once
    remember { MapLibre.getInstance(context) }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        }
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Update radar tile layer when frame changes
    LaunchedEffect(currentTileUrl, previousTileUrl) {
        val style = styleRef ?: return@LaunchedEffect
        updateRadarLayers(style, currentTileUrl, previousTileUrl)
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    mapRef = map

                    // Camera listeners for pause/resume during gestures
                    map.addOnCameraMoveStartedListener { onCameraMoveStarted() }
                    map.addOnCameraIdleListener { onCameraIdle() }

                    // Dark basemap
                    map.setStyle(
                        Style.Builder()
                            .fromUri(DARK_BASEMAP_URL)
                    ) { style ->
                        styleRef = style

                        // Set initial camera
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(latitude, longitude))
                            .zoom(zoom)
                            .build()

                        // Apply initial radar layer
                        if (currentTileUrl != null) {
                            updateRadarLayers(style, currentTileUrl, null)
                        }

                        // Attribution
                        map.uiSettings.apply {
                            isLogoEnabled = false
                            isAttributionEnabled = true
                        }

                        onMapReady()
                    }
                }
            }
        },
        modifier = modifier,
    )
}

private fun updateRadarLayers(
    style: Style,
    currentUrl: String?,
    previousUrl: String?,
) {
    // Remove old layers that aren't current or previous
    val layersToKeep = mutableSetOf<String>()
    if (currentUrl != null) layersToKeep.add(layerId(currentUrl))
    if (previousUrl != null) layersToKeep.add(layerId(previousUrl))

    style.layers.forEach { layer ->
        if (layer.id.startsWith(RADAR_LAYER_PREFIX) && layer.id !in layersToKeep) {
            style.removeLayer(layer.id)
            val sourceId = layer.id.replace("layer-", "source-")
            try { style.removeSource(sourceId) } catch (_: Exception) {}
        }
    }

    // Fade out previous frame
    if (previousUrl != null) {
        val prevLayerId = layerId(previousUrl)
        style.getLayerAs<RasterLayer>(prevLayerId)?.setProperties(
            PropertyFactory.rasterOpacity(0f)
        )
    }

    // Add/show current frame
    if (currentUrl != null) {
        val curLayerId = layerId(currentUrl)
        val curSourceId = sourceId(currentUrl)

        // Add source if not exists
        if (style.getSource(curSourceId) == null) {
            val tileSet = TileSet("2.2.0", currentUrl).apply {
                minZoom = 1f
                maxZoom = 7f
            }
            style.addSource(RasterSource(curSourceId, tileSet, 512))
        }

        // Add layer if not exists
        if (style.getLayer(curLayerId) == null) {
            val layer = RasterLayer(curLayerId, curSourceId).apply {
                setProperties(
                    PropertyFactory.rasterOpacity(0f)
                )
            }
            style.addLayer(layer)
        }

        // Fade in
        style.getLayerAs<RasterLayer>(curLayerId)?.setProperties(
            PropertyFactory.rasterOpacity(0.75f)
        )
    }
}

private const val RADAR_LAYER_PREFIX = "radar-"
private const val DARK_BASEMAP_URL =
    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

private fun layerId(url: String): String = "${RADAR_LAYER_PREFIX}layer-${url.hashCode()}"
private fun sourceId(url: String): String = "${RADAR_LAYER_PREFIX}source-${url.hashCode()}"
