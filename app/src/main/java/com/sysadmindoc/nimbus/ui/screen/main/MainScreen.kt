package com.sysadmindoc.nimbus.ui.screen.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.ui.component.AdaptiveLayoutInfo
import com.sysadmindoc.nimbus.ui.component.AlertBanner
import com.sysadmindoc.nimbus.ui.component.AlertDetailSheet
import com.sysadmindoc.nimbus.ui.component.AqiCard
import com.sysadmindoc.nimbus.ui.component.CurrentConditionsHeader
import com.sysadmindoc.nimbus.ui.component.DailyForecastList
import com.sysadmindoc.nimbus.ui.component.HourlyForecastStrip
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.component.MoonPhaseCard
import com.sysadmindoc.nimbus.ui.component.PollenCard
import com.sysadmindoc.nimbus.ui.component.RadarPreviewCard
import com.sysadmindoc.nimbus.ui.component.ShimmerLoadingSkeleton
import com.sysadmindoc.nimbus.ui.component.TemperatureGraph
import com.sysadmindoc.nimbus.ui.component.UvIndexBar
import com.sysadmindoc.nimbus.ui.component.WeatherDetailsGrid
import com.sysadmindoc.nimbus.ui.component.WeatherParticles
import com.sysadmindoc.nimbus.ui.component.WindCompass
import com.sysadmindoc.nimbus.ui.navigation.BottomTab
import com.sysadmindoc.nimbus.ui.navigation.ZeusWatchBottomNav
import com.sysadmindoc.nimbus.ui.screen.radar.RadarTab
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.skyGradient
import com.sysadmindoc.nimbus.util.AccessibilityHelper
import com.sysadmindoc.nimbus.util.ShareWeatherHelper
import com.sysadmindoc.nimbus.util.WeatherShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRadar: (Double, Double) -> Unit = { _, _ -> },
    onNavigateToLocations: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) viewModel.onPermissionGranted()
        else viewModel.onPermissionDenied()
    }

    LaunchedEffect(state.needsLocationPermission) {
        if (state.needsLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    // Provide unit settings to all child composables
    CompositionLocalProvider(LocalUnitSettings provides state.settings) {
        Scaffold(
            containerColor = NimbusNavyDark,
            bottomBar = {
                ZeusWatchBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when {
                    state.isLoading && state.weatherData == null -> ShimmerLoadingSkeleton()
                    state.error != null && state.weatherData == null -> ErrorState(
                        message = state.error!!,
                        onRetry = { viewModel.loadWeather() },
                    )
                    state.weatherData != null -> {
                        val data = state.weatherData!!
                        when (selectedTab) {
                            BottomTab.TODAY.ordinal -> TodayContent(
                                state = state,
                                onRetry = { viewModel.loadWeather() },
                                onRefresh = { viewModel.refresh() },
                                onNavigateToSettings = onNavigateToSettings,
                                onNavigateToRadar = onNavigateToRadar,
                                onNavigateToLocations = onNavigateToLocations,
                                onLocationSelected = { index -> viewModel.onPageChanged(index) },
                            )
                            BottomTab.HOURLY.ordinal -> HourlyTab(
                                hourly = data.hourly,
                                locationName = data.location.name,
                            )
                            BottomTab.DAILY.ordinal -> DailyTab(
                                daily = data.daily,
                                locationName = data.location.name,
                            )
                            BottomTab.RADAR.ordinal -> RadarTab(
                                latitude = data.location.latitude,
                                longitude = data.location.longitude,
                            )
                        }
                    }
                    else -> ErrorState(
                        message = "Loading weather data...",
                        onRetry = { viewModel.loadWeather() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun MainScreenContent(
    state: MainUiState,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRadar: (Double, Double) -> Unit = { _, _ -> },
    onNavigateToLocations: () -> Unit = {},
) {
    TodayContent(state, onRetry, onRefresh, onNavigateToSettings, onNavigateToRadar, onNavigateToLocations)
}

@Composable
internal fun TodayContent(
    state: MainUiState,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRadar: (Double, Double) -> Unit = { _, _ -> },
    onNavigateToLocations: () -> Unit = {},
    onLocationSelected: (Int) -> Unit = {},
) {
    when {
        state.isLoading && state.weatherData == null -> ShimmerLoadingSkeleton()
        state.error != null && state.weatherData == null -> ErrorState(
            message = state.error!!,
            onRetry = onRetry,
        )
        state.weatherData != null -> WeatherContent(
            data = state.weatherData!!,
            alerts = state.alerts,
            airQuality = state.airQuality,
            astronomy = state.astronomy,
            isRefreshing = state.isRefreshing,
            particlesEnabled = state.particlesEnabled,
            onRefresh = onRefresh,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRadar = onNavigateToRadar,
            onNavigateToLocations = onNavigateToLocations,
            savedLocations = state.savedLocations,
            currentPage = state.currentPage,
            onLocationSelected = onLocationSelected,
        )
        else -> ErrorState(message = "Loading weather data...", onRetry = onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WeatherContent(
    data: WeatherData,
    alerts: List<WeatherAlert>,
    airQuality: AirQualityData?,
    astronomy: AstronomyData?,
    isRefreshing: Boolean,
    particlesEnabled: Boolean,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRadar: (Double, Double) -> Unit,
    onNavigateToLocations: () -> Unit,
    savedLocations: List<SavedLocationEntity> = emptyList(),
    currentPage: Int = 0,
    onLocationSelected: (Int) -> Unit = {},
) {
    val bgBrush = skyGradient(
        isDay = data.current.isDay,
        weatherCode = data.current.weatherCode.code,
    )
    val context = LocalContext.current
    val layout = LocalAdaptiveLayout.current

    var selectedAlert by remember { mutableStateOf<WeatherAlert?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showShareMenu by remember { mutableStateOf(false) }
    val settings = LocalUnitSettings.current

    selectedAlert?.let { alert ->
        AlertDetailSheet(
            alert = alert,
            sheetState = sheetState,
            onDismiss = { selectedAlert = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onNavigateToLocations) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Manage locations",
                        tint = NimbusTextPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Row {
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share weather",
                                tint = NimbusTextPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share as Text") },
                                onClick = {
                                    showShareMenu = false
                                    ShareWeatherHelper.share(context, data, airQuality, settings)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share as Image") },
                                onClick = {
                                    showShareMenu = false
                                    WeatherShareHelper.shareAsImage(context, data, settings)
                                },
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = NimbusTextPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // ── Location Selector Bar ────────────────────────────────
            if (savedLocations.size > 1) {
                LocationSelectorBar(
                    locations = savedLocations,
                    currentIndex = currentPage,
                    onSelected = onLocationSelected,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Alert banner
            if (alerts.isNotEmpty()) {
                AlertBanner(
                    alerts = alerts,
                    onAlertClick = { selectedAlert = it },
                    modifier = Modifier.padding(horizontal = layout.contentPadding, vertical = 4.dp),
                )
            }

            // Hero
            Box(
                modifier = Modifier.semantics {
                    contentDescription = AccessibilityHelper.currentConditions(
                        data.current, data.location.name,
                    )
                },
            ) {
                if (particlesEnabled) {
                    WeatherParticles(
                        weatherCode = data.current.weatherCode,
                        isDay = data.current.isDay,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                CurrentConditionsHeader(
                    current = data.current,
                    locationName = data.location.name,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HourlyForecastStrip(
                hourly = data.hourly,
                modifier = Modifier
                    .padding(horizontal = layout.contentPadding)
                    .semantics {
                        contentDescription = AccessibilityHelper.hourlyForecast(data.hourly)
                    },
            )

            Spacer(modifier = Modifier.height(layout.cardSpacing))

            // ── Compact layout (phone) ───────────────────────────────
            if (layout.isCompact) {
                RadarPreviewCard(
                    onOpenRadar = { onNavigateToRadar(data.location.latitude, data.location.longitude) },
                    modifier = Modifier.padding(horizontal = layout.contentPadding),
                )
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                if (data.hourly.size >= 4) {
                    TemperatureGraph(
                        hourly = data.hourly,
                        modifier = Modifier.padding(horizontal = layout.contentPadding),
                    )
                    Spacer(modifier = Modifier.height(layout.cardSpacing))
                }

                DailyForecastList(
                    daily = data.daily,
                    modifier = Modifier.padding(horizontal = layout.contentPadding),
                )
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                UvIndexBar(
                    uvIndex = data.current.uvIndex,
                    modifier = Modifier.padding(horizontal = layout.contentPadding),
                )
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                WindCompass(
                    windSpeed = data.current.windSpeed,
                    windDirection = data.current.windDirection,
                    windGusts = data.current.windGusts,
                    modifier = Modifier.padding(horizontal = layout.contentPadding),
                )
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                airQuality?.let { aq ->
                    AqiCard(
                        data = aq,
                        modifier = Modifier.padding(horizontal = layout.contentPadding),
                    )
                    Spacer(modifier = Modifier.height(layout.cardSpacing))
                    PollenCard(
                        pollen = aq.pollen,
                        modifier = Modifier.padding(horizontal = layout.contentPadding),
                    )
                    Spacer(modifier = Modifier.height(layout.cardSpacing))
                }

                astronomy?.let { astro ->
                    MoonPhaseCard(
                        astronomy = astro,
                        sunrise = data.current.sunrise,
                        sunset = data.current.sunset,
                        modifier = Modifier.padding(horizontal = layout.contentPadding),
                    )
                    Spacer(modifier = Modifier.height(layout.cardSpacing))
                }

                WeatherDetailsGrid(
                    current = data.current,
                    modifier = Modifier.padding(horizontal = layout.contentPadding),
                )
            } else {
                // ── Wide layout (tablet) ─────────────────────────────
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = layout.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(layout.cardSpacing),
                    maxItemsInEachRow = 2,
                ) {
                    RadarPreviewCard(
                        onOpenRadar = { onNavigateToRadar(data.location.latitude, data.location.longitude) },
                        modifier = Modifier.weight(1f),
                    )
                    if (data.hourly.size >= 4) {
                        TemperatureGraph(hourly = data.hourly, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = layout.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(layout.cardSpacing),
                    maxItemsInEachRow = 2,
                ) {
                    UvIndexBar(uvIndex = data.current.uvIndex, modifier = Modifier.weight(1f))
                    WindCompass(
                        windSpeed = data.current.windSpeed,
                        windDirection = data.current.windDirection,
                        windGusts = data.current.windGusts,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                DailyForecastList(
                    daily = data.daily,
                    modifier = Modifier.padding(horizontal = layout.contentPadding),
                )
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = layout.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(layout.cardSpacing),
                    maxItemsInEachRow = 2,
                ) {
                    airQuality?.let { aq ->
                        AqiCard(data = aq, modifier = Modifier.weight(1f))
                        PollenCard(pollen = aq.pollen, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(layout.cardSpacing))

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = layout.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(layout.cardSpacing),
                    maxItemsInEachRow = 2,
                ) {
                    astronomy?.let { astro ->
                        MoonPhaseCard(
                            astronomy = astro,
                            sunrise = data.current.sunrise,
                            sunset = data.current.sunset,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    WeatherDetailsGrid(
                        current = data.current,
                        modifier = if (astronomy != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ZeusWatch v1.2.0 \u2022 Data: Open-Meteo.com",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

// ── Location Selector Bar ────────────────────────────────────────────────

@Composable
private fun LocationSelectorBar(
    locations: List<SavedLocationEntity>,
    currentIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        locations.forEachIndexed { index, loc ->
            val isActive = index == currentIndex
            val bgColor = if (isActive) NimbusBlueAccent.copy(alpha = 0.2f) else NimbusCardBg.copy(alpha = 0.4f)
            val borderColor = if (isActive) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.2f)

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onSelected(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loc.isCurrentLocation) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = if (isActive) NimbusBlueAccent else NimbusTextTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (loc.isCurrentLocation) "My Location" else loc.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isActive) NimbusBlueAccent else NimbusTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Error State ──────────────────────────────────────────────────────────

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = NimbusTextSecondary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = NimbusTextSecondary,
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = NimbusBlueAccent),
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Retry")
            }
        }
    }
}
