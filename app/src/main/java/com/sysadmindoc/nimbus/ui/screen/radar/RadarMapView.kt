package com.sysadmindoc.nimbus.ui.screen.radar

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import com.sysadmindoc.nimbus.data.api.LightningStrike
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.CommunityReport
import com.sysadmindoc.nimbus.data.model.ReportCondition
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer
import kotlin.math.abs

/**
 * MapLibre composable wrapper displaying radar tiles from RainViewer
 * and optional weather overlay layers (temperature, wind, clouds, precipitation).
 * Uses CartoCDN Dark Matter basemap (no API key).
 * Manages radar tile layers with opacity crossfade for animation,
 * and a separate overlay layer for static weather tile sources.
 */
@Composable
fun RadarMapView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 5.0,
    currentTileUrl: String?,
    previousTileUrl: String?,
    overlayTileUrl: String? = null,
    lightningStrikes: List<LightningStrike> = emptyList(),
    communityReports: List<CommunityReport> = emptyList(),
    alertOverlays: List<WeatherAlert> = emptyList(),
    onMapReady: () -> Unit = {},
    onCameraMoveStarted: () -> Unit = {},
    onCameraIdle: () -> Unit = {},
    onAlertSelected: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnAlertSelected by rememberUpdatedState(onAlertSelected)

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

    // Update overlay tile layer (temperature, wind, clouds, precipitation)
    LaunchedEffect(overlayTileUrl) {
        val style = styleRef ?: return@LaunchedEffect
        updateOverlayLayer(style, overlayTileUrl)
    }

    // Update lightning strike points reactively
    LaunchedEffect(lightningStrikes) {
        val style = styleRef ?: return@LaunchedEffect
        updateLightningLayer(style, lightningStrikes)
    }

    // Update community report markers reactively
    LaunchedEffect(communityReports) {
        val style = styleRef ?: return@LaunchedEffect
        updateCommunityReports(style, communityReports)
    }

    // Update active warning polygons reactively.
    LaunchedEffect(alertOverlays) {
        val style = styleRef ?: return@LaunchedEffect
        updateAlertOverlayLayer(style, alertOverlays)
    }

    // Re-center when the requested coordinates change materially (e.g. a deep
    // link that opened at 0,0 resolves to the real location after the map was
    // created). Keyed on styleRef so it re-runs once the style is ready and
    // wins over the initial camera set in the style callback.
    LaunchedEffect(styleRef, latitude, longitude) {
        if (styleRef == null) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val target = map.cameraPosition.target
        val materiallyMoved = target == null ||
            abs(target.latitude - latitude) > CAMERA_RECENTER_EPSILON_DEGREES ||
            abs(target.longitude - longitude) > CAMERA_RECENTER_EPSILON_DEGREES
        if (materiallyMoved) {
            map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)))
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    mapRef = map

                    // Camera listeners for pause/resume during gestures
                    map.addOnCameraMoveStartedListener { onCameraMoveStarted() }
                    map.addOnCameraIdleListener { onCameraIdle() }
                    map.addOnMapClickListener { latLng ->
                        val screenPoint = map.projection.toScreenLocation(latLng)
                        val alertId = map.queryRenderedFeatures(
                            screenPoint,
                            ALERT_BORDER_LAYER_ID,
                            ALERT_FILL_LAYER_ID,
                        ).firstOrNull { it.hasProperty(ALERT_ID_PROPERTY) }
                            ?.getStringProperty(ALERT_ID_PROPERTY)
                        if (alertId != null) {
                            currentOnAlertSelected(alertId)
                            true
                        } else {
                            false
                        }
                    }

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

                        // Apply initial overlay layer
                        if (overlayTileUrl != null) {
                            updateOverlayLayer(style, overlayTileUrl)
                        }

                        // Apply initial lightning layer
                        if (lightningStrikes.isNotEmpty()) {
                            updateLightningLayer(style, lightningStrikes)
                        }

                        // Apply initial community reports layer
                        if (communityReports.isNotEmpty()) {
                            updateCommunityReports(style, communityReports)
                        }

                        // Apply initial active alert polygons
                        if (alertOverlays.isNotEmpty()) {
                            updateAlertOverlayLayer(style, alertOverlays)
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
            addRadarLayerBelowMarkers(style, layer)
        }

        // Fade in
        style.getLayerAs<RasterLayer>(curLayerId)?.setProperties(
            PropertyFactory.rasterOpacity(0.75f)
        )
    }
}

/**
 * Insert a radar raster layer beneath the lightning/community marker layers
 * (or, failing that, beneath the basemap's first symbol layer) so strikes,
 * reports, and labels stay visible while frames animate above the basemap.
 */
private fun addRadarLayerBelowMarkers(style: Style, layer: RasterLayer) {
    // style.layers is ordered bottom-to-top; anchor below the lowest marker layer.
    val anchorId = style.layers.firstOrNull { it.id in RADAR_ANCHOR_LAYER_IDS }?.id
        ?: style.layers.firstOrNull { it is SymbolLayer }?.id
    if (anchorId != null) {
        style.addLayerBelow(layer, anchorId)
    } else {
        style.addLayer(layer)
    }
}

private fun addOverlayLayerBelowMarkers(style: Style, layer: Layer) {
    val anchorId = style.layers.firstOrNull { it.id in MARKER_LAYER_IDS }?.id
        ?: style.layers.firstOrNull { it is SymbolLayer }?.id
    if (anchorId != null) {
        style.addLayerBelow(layer, anchorId)
    } else {
        style.addLayer(layer)
    }
}

private fun updateOverlayLayer(
    style: Style,
    overlayUrl: String?,
) {
    // Remove any existing overlay layers/sources
    style.layers.forEach { layer ->
        if (layer.id.startsWith(OVERLAY_LAYER_PREFIX)) {
            style.removeLayer(layer.id)
        }
    }
    // Remove overlay sources (iterate separately to avoid concurrent modification)
    style.sources.forEach { source ->
        if (source.id.startsWith(OVERLAY_LAYER_PREFIX)) {
            try { style.removeSource(source.id) } catch (_: Exception) {}
        }
    }

    // Add new overlay if URL is provided
    if (overlayUrl != null) {
        val oSourceId = "${OVERLAY_LAYER_PREFIX}source"
        val oLayerId = "${OVERLAY_LAYER_PREFIX}layer"

        val tileSet = TileSet("2.2.0", overlayUrl).apply {
            minZoom = 1f
            maxZoom = 18f
        }
        style.addSource(RasterSource(oSourceId, tileSet, 256))

        val layer = RasterLayer(oLayerId, oSourceId).apply {
            setProperties(
                PropertyFactory.rasterOpacity(OVERLAY_OPACITY)
            )
        }
        style.addLayer(layer)
    }
}

private fun updateLightningLayer(
    style: Style,
    strikes: List<LightningStrike>,
) {
    if (strikes.isEmpty()) {
        // Remove lightning layers and source when no strikes
        style.getLayer(LIGHTNING_POINT_LAYER)?.let { style.removeLayer(it) }
        style.getLayer(LIGHTNING_GLOW_LAYER)?.let { style.removeLayer(it) }
        style.getSource(LIGHTNING_SOURCE_ID)?.let { style.removeSource(it) }
        return
    }

    // Build GeoJSON FeatureCollection from strike points
    val features = strikes.map { strike ->
        Feature.fromGeometry(Point.fromLngLat(strike.lon, strike.lat))
    }
    val featureCollection = FeatureCollection.fromFeatures(features)

    // Update or create the GeoJSON source
    val existingSource = style.getSourceAs<GeoJsonSource>(LIGHTNING_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(featureCollection)
    } else {
        val source = GeoJsonSource(LIGHTNING_SOURCE_ID, featureCollection)
        style.addSource(source)

        // Outer glow circle (semi-transparent, larger radius)
        val glowLayer = CircleLayer(LIGHTNING_GLOW_LAYER, LIGHTNING_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor("#FFEB3B"), // Yellow glow
                PropertyFactory.circleOpacity(0.25f),
                PropertyFactory.circleBlur(1f),
            )
        }
        style.addLayer(glowLayer)

        // Inner solid circle (bright white-yellow, small radius)
        val pointLayer = CircleLayer(LIGHTNING_POINT_LAYER, LIGHTNING_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(4f),
                PropertyFactory.circleColor("#FFFFFF"), // White core
                PropertyFactory.circleOpacity(0.9f),
                PropertyFactory.circleStrokeWidth(1f),
                PropertyFactory.circleStrokeColor("#FFEB3B"), // Yellow outline
            )
        }
        style.addLayer(pointLayer)
    }
}

private fun updateCommunityReports(
    style: Style,
    reports: List<CommunityReport>,
) {
    if (reports.isEmpty()) {
        // Remove community report layers and source when empty
        style.getLayer(REPORTS_LABEL_LAYER_ID)?.let { style.removeLayer(it) }
        style.getLayer(REPORTS_LAYER_ID)?.let { style.removeLayer(it) }
        style.getSource(REPORTS_SOURCE_ID)?.let { style.removeSource(it) }
        return
    }

    // Build GeoJSON FeatureCollection from reports
    val features = reports.map { report ->
        Feature.fromGeometry(
            Point.fromLngLat(report.longitude, report.latitude)
        ).apply {
            addStringProperty("emoji", report.condition.emoji)
            addStringProperty("condition", report.condition.name)
            addStringProperty("color", conditionColor(report.condition))
        }
    }
    val featureCollection = FeatureCollection.fromFeatures(features)

    // Update or create the GeoJSON source
    val existingSource = style.getSourceAs<GeoJsonSource>(REPORTS_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(featureCollection)
    } else {
        val source = GeoJsonSource(REPORTS_SOURCE_ID, featureCollection)
        style.addSource(source)

        // Circle marker layer with per-condition colors
        val circleLayer = CircleLayer(REPORTS_LAYER_ID, REPORTS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor(
                    Expression.get("color")
                ),
                PropertyFactory.circleOpacity(0.8f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeOpacity(0.6f),
            )
        }
        style.addLayer(circleLayer)

        // Emoji label layer on top of circles
        val labelLayer = SymbolLayer(REPORTS_LABEL_LAYER_ID, REPORTS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.textField(Expression.get("emoji")),
                PropertyFactory.textSize(14f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.iconAllowOverlap(true),
            )
        }
        style.addLayer(labelLayer)
    }
}

private fun updateAlertOverlayLayer(
    style: Style,
    alertOverlays: List<WeatherAlert>,
) {
    if (alertOverlays.isEmpty()) {
        style.getLayer(ALERT_BORDER_LAYER_ID)?.let { style.removeLayer(it) }
        style.getLayer(ALERT_FILL_LAYER_ID)?.let { style.removeLayer(it) }
        style.getSource(ALERT_SOURCE_ID)?.let { style.removeSource(it) }
        return
    }

    val featureCollection = alertOverlayFeatureCollection(alertOverlays)
    val existingSource = style.getSourceAs<GeoJsonSource>(ALERT_SOURCE_ID)
    if (existingSource != null) {
        existingSource.setGeoJson(featureCollection)
        return
    }

    style.addSource(GeoJsonSource(ALERT_SOURCE_ID, featureCollection))

    val fillLayer = FillLayer(ALERT_FILL_LAYER_ID, ALERT_SOURCE_ID).apply {
        setProperties(
            PropertyFactory.fillColor(Expression.get(ALERT_COLOR_PROPERTY)),
            PropertyFactory.fillOpacity(0.18f),
        )
    }
    addOverlayLayerBelowMarkers(style, fillLayer)

    val borderLayer = LineLayer(ALERT_BORDER_LAYER_ID, ALERT_SOURCE_ID).apply {
        setProperties(
            PropertyFactory.lineColor(Expression.get(ALERT_COLOR_PROPERTY)),
            PropertyFactory.lineOpacity(0.92f),
            PropertyFactory.lineWidth(2.4f),
        )
    }
    addOverlayLayerBelowMarkers(style, borderLayer)
}

internal fun alertOverlayFeatureCollection(alertOverlays: List<WeatherAlert>): FeatureCollection {
    val features = alertOverlays.flatMap { alert ->
        alert.geometry?.polygons?.mapNotNull { polygon ->
            alertOverlayFeature(alert, polygon)
        } ?: emptyList()
    }
    return FeatureCollection.fromFeatures(features)
}

private fun alertOverlayFeature(alert: WeatherAlert, polygon: AlertPolygon): Feature? {
    val ring = closedPolygonRing(polygon)
    if (ring.size < MIN_CLOSED_POLYGON_POINTS) return null
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
        addStringProperty(ALERT_ID_PROPERTY, alert.id)
        addStringProperty("event", alert.event)
        addStringProperty("severity", alert.severity.name)
        addStringProperty(ALERT_COLOR_PROPERTY, alert.severity.overlayColor)
    }
}

private fun closedPolygonRing(polygon: AlertPolygon): List<Point> {
    val points = polygon.points
    if (points.size < MIN_POLYGON_POINTS) return emptyList()
    val closedPoints = if (points.first() == points.last()) {
        points
    } else {
        points + points.first()
    }
    return closedPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
}

private val AlertSeverity.overlayColor: String
    get() = when (this) {
        AlertSeverity.EXTREME -> "#D32F2F"
        AlertSeverity.SEVERE -> "#FF5722"
        AlertSeverity.MODERATE -> "#FF9800"
        AlertSeverity.MINOR -> "#FFD54F"
        AlertSeverity.UNKNOWN -> "#9E9E9E"
    }

/** Map ReportCondition to a hex color for the circle marker. */
private fun conditionColor(condition: ReportCondition): String = when (condition) {
    ReportCondition.SUNNY -> "#FFD54F"          // Yellow
    ReportCondition.PARTLY_CLOUDY -> "#90A4AE"  // Gray-blue
    ReportCondition.CLOUDY -> "#78909C"          // Blue-gray
    ReportCondition.RAIN -> "#64B5F6"           // Light blue
    ReportCondition.HEAVY_RAIN -> "#1E88E5"     // Blue
    ReportCondition.SNOW -> "#E8EAF6"           // White-lavender
    ReportCondition.FOG -> "#B0BEC5"            // Light gray
    ReportCondition.WIND -> "#80CBC4"           // Teal
    ReportCondition.HAIL -> "#CE93D8"           // Purple
    ReportCondition.TORNADO -> "#EF5350"        // Red
}

private const val RADAR_LAYER_PREFIX = "radar-"
private const val OVERLAY_LAYER_PREFIX = "overlay-"
private const val OVERLAY_OPACITY = 0.6f
private const val LIGHTNING_SOURCE_ID = "lightning-source"
private const val LIGHTNING_GLOW_LAYER = "lightning-glow"
private const val LIGHTNING_POINT_LAYER = "lightning-points"
private const val REPORTS_SOURCE_ID = "community-reports-source"
private const val REPORTS_LAYER_ID = "community-reports-circles"
private const val REPORTS_LABEL_LAYER_ID = "community-reports-labels"
private const val ALERT_SOURCE_ID = "active-alert-polygons-source"
private const val ALERT_FILL_LAYER_ID = "active-alert-polygons-fill"
private const val ALERT_BORDER_LAYER_ID = "active-alert-polygons-border"
private const val ALERT_ID_PROPERTY = "alertId"
private const val ALERT_COLOR_PROPERTY = "alertColor"
private const val MIN_POLYGON_POINTS = 3
private const val MIN_CLOSED_POLYGON_POINTS = 4
private const val CAMERA_RECENTER_EPSILON_DEGREES = 0.001

/** Marker layers radar rasters must always render beneath. */
private val RADAR_ANCHOR_LAYER_IDS = setOf(
    ALERT_FILL_LAYER_ID,
    ALERT_BORDER_LAYER_ID,
    LIGHTNING_GLOW_LAYER,
    LIGHTNING_POINT_LAYER,
    REPORTS_LAYER_ID,
    REPORTS_LABEL_LAYER_ID,
)

private val MARKER_LAYER_IDS = setOf(
    LIGHTNING_GLOW_LAYER,
    LIGHTNING_POINT_LAYER,
    REPORTS_LAYER_ID,
    REPORTS_LABEL_LAYER_ID,
)
private const val DARK_BASEMAP_URL =
    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

private fun layerId(url: String): String = "${RADAR_LAYER_PREFIX}layer-${url.hashCode()}"
private fun sourceId(url: String): String = "${RADAR_LAYER_PREFIX}source-${url.hashCode()}"
