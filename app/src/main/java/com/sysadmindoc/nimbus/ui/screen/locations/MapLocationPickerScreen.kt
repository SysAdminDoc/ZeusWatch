package com.sysadmindoc.nimbus.ui.screen.locations

import android.location.Geocoder
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.Locale

private const val DARK_BASEMAP_URL =
    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val PIN_SOURCE = "picker-pin-source"
private const val PIN_LAYER = "picker-pin-layer"

@Composable
fun MapLocationPickerScreen(
    onLocationPicked: (lat: Double, lon: Double, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    remember { MapLibre.getInstance(context) }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var selectedLat by remember { mutableStateOf<Double?>(null) }
    var selectedLon by remember { mutableStateOf<Double?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var isGeocoding by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        }
    }

    MapPickerLifecycleEffect(mapView, lifecycleOwner)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapRef = map
                        map.setStyle(Style.Builder().fromUri(DARK_BASEMAP_URL)) { style ->
                            styleRef = style
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(20.0, 0.0))
                                .zoom(2.0)
                                .build()

                            style.addSource(GeoJsonSource(PIN_SOURCE))
                            style.addLayer(
                                CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                                    PropertyFactory.circleRadius(10f),
                                    PropertyFactory.circleColor(NimbusBlueAccent.hashCode()),
                                    PropertyFactory.circleStrokeWidth(3f),
                                    PropertyFactory.circleStrokeColor(
                                        android.graphics.Color.WHITE,
                                    ),
                                ),
                            )
                        }

                        map.addOnMapClickListener { latLng ->
                            selectedLat = latLng.latitude
                            selectedLon = latLng.longitude
                            selectedName = null
                            isGeocoding = true

                            val pinPoint = Point.fromLngLat(latLng.longitude, latLng.latitude)
                            styleRef?.getSourceAs<GeoJsonSource>(PIN_SOURCE)?.setGeoJson(
                                FeatureCollection.fromFeature(Feature.fromGeometry(pinPoint)),
                            )

                            scope.launch {
                                val name = reverseGeocode(context, latLng.latitude, latLng.longitude)
                                selectedName = name
                                isGeocoding = false
                            }
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = NimbusTextPrimary,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NimbusGlassTop.copy(alpha = 0.95f), NimbusCardBg),
                    ),
                )
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.map_picker_title),
                style = MaterialTheme.typography.titleSmall,
                color = NimbusTextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    selectedName != null -> selectedName!!
                    isGeocoding -> stringResource(R.string.map_picker_geocoding)
                    else -> stringResource(R.string.map_picker_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val lat = selectedLat ?: return@Button
                    val lon = selectedLon ?: return@Button
                    val name = selectedName ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)
                    onLocationPicked(lat, lon, name)
                },
                enabled = selectedLat != null && !isGeocoding,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NimbusBlueAccent,
                ),
            ) {
                Text(stringResource(R.string.map_picker_save))
            }
        }
    }
}

@Composable
private fun MapPickerLifecycleEffect(mapView: MapView, lifecycleOwner: LifecycleOwner) {
    DisposableEffect(mapView, lifecycleOwner) {
        var destroyed = false
        fun destroyMapOnce() {
            if (!destroyed) {
                mapView.onDestroy()
                destroyed = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (destroyed) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyMapOnce()
                else -> Unit
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            destroyMapOnce()
        }
    }
}

private suspend fun reverseGeocode(
    context: android.content.Context,
    lat: Double,
    lon: Double,
): String = withContext(Dispatchers.IO) {
    try {
        @Suppress("DEPRECATION")
        val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
        val addr = addresses?.firstOrNull()
        when {
            addr?.locality != null -> addr.locality
            addr?.subAdminArea != null -> addr.subAdminArea
            addr?.adminArea != null -> addr.adminArea
            addr?.countryName != null -> addr.countryName
            else -> String.format(Locale.US, "%.4f, %.4f", lat, lon)
        }
    } catch (_: Exception) {
        String.format(Locale.US, "%.4f, %.4f", lat, lon)
    }
}
