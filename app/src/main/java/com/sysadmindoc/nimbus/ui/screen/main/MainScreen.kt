package com.sysadmindoc.nimbus.ui.screen.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.sysadmindoc.nimbus.ui.component.ClothingSuggestionCard
import com.sysadmindoc.nimbus.ui.component.CurrentConditionsHeader
import com.sysadmindoc.nimbus.ui.component.DailyForecastList
import com.sysadmindoc.nimbus.ui.component.HourlyForecastStrip
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.component.DrivingAlertCard
import com.sysadmindoc.nimbus.ui.component.GoldenHourCard
import com.sysadmindoc.nimbus.ui.component.HealthAlertCard
import com.sysadmindoc.nimbus.ui.component.HumidityCard
import com.sysadmindoc.nimbus.ui.component.MoonPhaseCard
import com.sysadmindoc.nimbus.ui.component.PrecipitationChartCard
import com.sysadmindoc.nimbus.ui.component.PressureTrendCard
import com.sysadmindoc.nimbus.ui.component.NowcastCard
import com.sysadmindoc.nimbus.ui.component.OutdoorScoreCard
import com.sysadmindoc.nimbus.ui.component.PetSafetyCard
import com.sysadmindoc.nimbus.ui.component.PollenCard
import com.sysadmindoc.nimbus.ui.component.SevereWeatherCard
import com.sysadmindoc.nimbus.ui.component.SnowfallCard
import com.sysadmindoc.nimbus.ui.component.SunshineDurationCard
import com.sysadmindoc.nimbus.ui.component.RadarPreviewCard
import com.sysadmindoc.nimbus.ui.component.SunArc
import com.sysadmindoc.nimbus.ui.component.WeatherSummaryCard
import com.sysadmindoc.nimbus.ui.component.WindTrendCard
import com.sysadmindoc.nimbus.data.repository.CardType
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
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
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import java.time.Duration
import java.time.LocalDateTime
import com.sysadmindoc.nimbus.ui.theme.skyGradient
import com.sysadmindoc.nimbus.util.AccessibilityHelper
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.ShareWeatherHelper
// WeatherShareHelper consolidated into ShareWeatherHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRadar: (Double, Double) -> Unit = { _, _ -> },
    onNavigateToLocations: () -> Unit = {},
    onNavigateToCompare: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Track whether we've already prompted in this session
    var hasPromptedPermissions by rememberSaveable { mutableStateOf(false) }

    // Location permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) viewModel.onPermissionGranted()
        else viewModel.onPermissionDenied()
    }

    // Notification permission launcher (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* System handles the grant/deny visual */ }

    // Background location launcher (Android 11+, requested AFTER foreground granted)
    val bgLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Background location is optional — no action needed on deny */ }

    // Sequential permission flow on first launch:
    // 1. Location (required) -> 2. Notifications (Android 13+) -> 3. Background location (optional)
    LaunchedEffect(state.needsLocationPermission) {
        if (state.needsLocationPermission && !hasPromptedPermissions) {
            hasPromptedPermissions = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    // After location is granted, request notification + background location permissions
    val permContext = LocalContext.current
    LaunchedEffect(state.weatherData) {
        if (state.weatherData != null && hasPromptedPermissions) {
            // Small delay so location permission dialog fully dismisses
            kotlinx.coroutines.delay(500)

            // Request notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    permContext, Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!notifGranted) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    kotlinx.coroutines.delay(500)
                }
            }

            // Request background location on Android 11+ (for widgets/alert worker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bgGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    permContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!bgGranted) {
                    bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }

    // Detect tablet: screen width >= 840dp
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 840

    // Provide unit settings and weather theme state to all child composables
    val weatherThemeState = com.sysadmindoc.nimbus.ui.theme.WeatherThemeState(
        weatherCode = state.weatherData?.current?.weatherCode,
        isDay = state.weatherData?.current?.isDay ?: true,
    )
    CompositionLocalProvider(
        LocalUnitSettings provides state.settings,
        com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState provides weatherThemeState,
    ) {
        // On tablet, hide the Radar tab from bottom nav (it's always visible in the right pane)
        val visibleTabs = if (isTablet) {
            BottomTab.entries.filter { it != BottomTab.RADAR }
        } else {
            BottomTab.entries
        }

        Scaffold(
            containerColor = NimbusNavyDark,
            bottomBar = {
                ZeusWatchBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    visibleTabs = visibleTabs,
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
                        icon = when {
                            state.error!!.contains("permission", ignoreCase = true) ||
                            state.error!!.contains("location", ignoreCase = true) -> Icons.Filled.LocationOff
                            state.error!!.contains("network", ignoreCase = true) ||
                            state.error!!.contains("connect", ignoreCase = true) ||
                            state.error!!.contains("offline", ignoreCase = true) ||
                            state.error!!.contains("internet", ignoreCase = true) -> Icons.Filled.CloudOff
                            else -> Icons.Filled.ErrorOutline
                        },
                    )
                    state.weatherData != null -> {
                        val data = state.weatherData!!

                        if (isTablet) {
                            // ── Two-pane tablet layout ──────────────────
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left pane: Weather content (tab-switched)
                                Box(modifier = Modifier.weight(0.55f)) {
                                    Crossfade(targetState = selectedTab, animationSpec = tween(300), label = "tabletTab") { tab ->
                                        when (tab) {
                                            BottomTab.TODAY.ordinal -> TodayContent(
                                                state = state,
                                                onRetry = { viewModel.loadWeather() },
                                                onRefresh = { viewModel.refresh() },
                                                onNavigateToSettings = onNavigateToSettings,
                                                onNavigateToRadar = onNavigateToRadar,
                                                onNavigateToLocations = onNavigateToLocations,
                                                onNavigateToCompare = onNavigateToCompare,
                                                onLocationSelected = { index -> viewModel.onPageChanged(index) },
                                            )
                                            BottomTab.HOURLY.ordinal -> HourlyTab(
                                                hourly = data.hourly,
                                                locationName = data.location.name,
                                                isRefreshing = state.isRefreshing,
                                                onRefresh = { viewModel.refresh() },
                                            )
                                            BottomTab.DAILY.ordinal -> DailyTab(
                                                daily = data.daily,
                                                locationName = data.location.name,
                                                isRefreshing = state.isRefreshing,
                                                onRefresh = { viewModel.refresh() },
                                            )
                                        }
                                    }
                                }

                                // Vertical divider between panes
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    thickness = 1.dp,
                                    color = NimbusCardBg,
                                )

                                // Right pane: Radar always visible
                                Box(modifier = Modifier.weight(0.45f)) {
                                    RadarTab(
                                        latitude = data.location.latitude,
                                        longitude = data.location.longitude,
                                    )
                                }
                            }
                        } else {
                            // ── Phone layout with tab switching ─────────
                            Crossfade(targetState = selectedTab, animationSpec = tween(300), label = "phoneTab") { tab ->
                                when (tab) {
                                    BottomTab.TODAY.ordinal -> TodayContent(
                                        state = state,
                                        onRetry = { viewModel.loadWeather() },
                                        onRefresh = { viewModel.refresh() },
                                        onNavigateToSettings = onNavigateToSettings,
                                        onNavigateToRadar = onNavigateToRadar,
                                        onNavigateToLocations = onNavigateToLocations,
                                        onNavigateToCompare = onNavigateToCompare,
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
internal fun TodayContent(
    state: MainUiState,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRadar: (Double, Double) -> Unit = { _, _ -> },
    onNavigateToLocations: () -> Unit = {},
    onNavigateToCompare: () -> Unit = {},
    onLocationSelected: (Int) -> Unit = {},
) {
    when {
        state.isLoading && state.weatherData == null -> ShimmerLoadingSkeleton()
        state.error != null && state.weatherData == null -> ErrorState(
            message = state.error!!,
            onRetry = onRetry,
            icon = when {
                state.error!!.contains("permission", ignoreCase = true) ||
                state.error!!.contains("location", ignoreCase = true) -> Icons.Filled.LocationOff
                state.error!!.contains("network", ignoreCase = true) ||
                state.error!!.contains("connect", ignoreCase = true) ||
                state.error!!.contains("offline", ignoreCase = true) ||
                state.error!!.contains("internet", ignoreCase = true) -> Icons.Filled.CloudOff
                else -> Icons.Filled.ErrorOutline
            },
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
            onNavigateToCompare = onNavigateToCompare,
            savedLocations = state.savedLocations,
            currentPage = state.currentPage,
            onLocationSelected = onLocationSelected,
            radarPreviewTileUrl = state.radarPreviewTileUrl,
            radarBaseMapUrl = state.radarBaseMapUrl,
            isCached = state.isCached,
            state = state,
        )
        else -> ErrorState(message = "Loading weather data...", onRetry = onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onNavigateToCompare: () -> Unit = {},
    savedLocations: List<SavedLocationEntity> = emptyList(),
    currentPage: Int = 0,
    onLocationSelected: (Int) -> Unit = {},
    radarPreviewTileUrl: String? = null,
    radarBaseMapUrl: String? = null,
    isCached: Boolean = false,
    state: MainUiState = MainUiState(),
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

    // Precompute enabled cards for LazyColumn
    val enabledCards = remember(settings.cardOrder, settings.disabledCards) {
        settings.cardOrder.filter { card -> card.name !in settings.disabledCards }
    }

    // Precipitation chance + updated time state
    val todayPrecipChance = data.daily.firstOrNull()?.precipitationProbability ?: 0
    var updatedAgo by remember { mutableStateOf("") }
    LaunchedEffect(data.lastUpdated) {
        while (true) {
            val minutes = Duration.between(data.lastUpdated, LocalDateTime.now()).toMinutes()
            updatedAgo = when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                else -> "${minutes / 60}h ago"
            }
            kotlinx.coroutines.delay(60_000L)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val cardPad = Modifier.padding(horizontal = layout.contentPadding)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 8.dp),
        ) {
            // ── Toolbar ─────────────────────────────────────────────
            item(key = "toolbar") {
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
                                        ShareWeatherHelper.shareAsImage(context, data, settings)
                                    },
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToCompare) {
                            Icon(
                                Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = "Compare locations",
                                tint = NimbusTextPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp),
                            )
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
            }

            // ── Offline Banner ──────────────────────────────────────
            if (state.isOffline) {
                item(key = "offline_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "You're offline. Showing cached data.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // ── Location Selector Bar ────────────────────────────────
            if (savedLocations.size > 1) {
                item(key = "location_bar") {
                    LocationSelectorBar(
                        locations = savedLocations,
                        currentIndex = currentPage,
                        onSelected = onLocationSelected,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            // ── Alert Banner ─────────────────────────────────────────
            if (alerts.isNotEmpty()) {
                item(key = "alert_banner") {
                    AlertBanner(
                        alerts = alerts,
                        onAlertClick = { selectedAlert = it },
                        modifier = Modifier.padding(horizontal = layout.contentPadding, vertical = 4.dp),
                    )
                }
            }

            // ── Hero ─────────────────────────────────────────────────
            item(key = "hero") {
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
                        yesterdayHigh = state.yesterdayHigh,
                    )
                }
            }

            // ── Precip chance + updated time ─────────────────────────
            item(key = "updated_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (todayPrecipChance > 0) {
                        Icon(
                            Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = NimbusBlueAccent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$todayPrecipChance% chance of rain",
                            style = MaterialTheme.typography.bodySmall,
                            color = NimbusBlueAccent,
                        )
                        Text(
                            text = " \u2022 ",
                            style = MaterialTheme.typography.bodySmall,
                            color = NimbusTextTertiary,
                        )
                    }
                    val stalenessColor = when {
                        isCached -> NimbusTextTertiary.copy(alpha = 0.7f)
                        updatedAgo == "Just now" || updatedAgo.endsWith("m ago") -> NimbusTextTertiary
                        updatedAgo.contains("1h") -> NimbusWarning.copy(alpha = 0.7f)
                        updatedAgo.contains("h") -> NimbusWarning
                        else -> NimbusTextTertiary
                    }
                    Text(
                        text = if (isCached) "Cached \u2022 $updatedAgo" else "Updated $updatedAgo",
                        style = MaterialTheme.typography.bodySmall,
                        color = stalenessColor,
                    )
                }
            }

            // ── Hourly Forecast Strip ────────────────────────────────
            item(key = "hourly_strip") {
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
            }

            // ── Dynamic Cards (truly lazy now) ───────────────────────
            items(
                items = enabledCards,
                key = { it.name },
                contentType = { it.name },
            ) { cardType ->
                RenderCard(
                    cardType = cardType,
                    modifier = cardPad,
                    state = state,
                    data = data,
                    airQuality = airQuality,
                    astronomy = astronomy,
                    settings = settings,
                    radarPreviewTileUrl = radarPreviewTileUrl,
                    radarBaseMapUrl = radarBaseMapUrl,
                    onNavigateToRadar = onNavigateToRadar,
                )
                Spacer(modifier = Modifier.height(layout.cardSpacing))
            }

            // ── Footer ──────────────────────────────────────────────
            item(key = "footer") {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ZeusWatch v${com.sysadmindoc.nimbus.BuildConfig.VERSION_NAME} \u2022 Data: Open-Meteo.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// ── Shared Card Renderer ─────────────────────────────────────────────────

@Composable
private fun RenderCard(
    cardType: CardType,
    modifier: Modifier,
    state: MainUiState,
    data: WeatherData,
    airQuality: AirQualityData?,
    astronomy: AstronomyData?,
    settings: NimbusSettings,
    radarPreviewTileUrl: String?,
    radarBaseMapUrl: String?,
    onNavigateToRadar: (Double, Double) -> Unit,
) {
    when (cardType) {
        CardType.WEATHER_SUMMARY -> {
            if (state.weatherSummary.isNotBlank()) {
                WeatherSummaryCard(summary = state.weatherSummary, modifier = modifier)
            }
        }
        CardType.RADAR_PREVIEW -> RadarPreviewCard(
            onOpenRadar = { onNavigateToRadar(data.location.latitude, data.location.longitude) },
            modifier = modifier,
            radarTileUrl = radarPreviewTileUrl,
            baseMapTileUrl = radarBaseMapUrl,
        )
        CardType.NOWCAST -> {
            if (state.nowcastData.isNotEmpty()) {
                NowcastCard(data = state.nowcastData, modifier = modifier)
            }
        }
        CardType.HOURLY_FORECAST -> HourlyForecastStrip(
            hourly = data.hourly,
            modifier = modifier,
        )
        CardType.TEMPERATURE_GRAPH -> {
            if (data.hourly.size >= 4) {
                val avgHigh = data.daily.takeIf { it.size > 2 }?.map { it.temperatureHigh }?.average()
                val avgLow = data.daily.takeIf { it.size > 2 }?.map { it.temperatureLow }?.average()
                TemperatureGraph(hourly = data.hourly, modifier = modifier, normalHigh = avgHigh, normalLow = avgLow)
            }
        }
        CardType.DAILY_FORECAST -> DailyForecastList(
            daily = data.daily,
            modifier = modifier,
        )
        CardType.UV_INDEX -> UvIndexBar(
            uvIndex = data.current.uvIndex,
            modifier = modifier,
            hourly = data.hourly,
        )
        CardType.WIND_COMPASS -> WindCompass(
            windSpeed = data.current.windSpeed,
            windDirection = data.current.windDirection,
            windGusts = data.current.windGusts,
            modifier = modifier,
        )
        CardType.AIR_QUALITY -> airQuality?.let { aq ->
            AqiCard(data = aq, modifier = modifier)
        }
        CardType.POLLEN -> airQuality?.let { aq ->
            PollenCard(pollen = aq.pollen, modifier = modifier)
        }
        CardType.OUTDOOR_SCORE -> {
            if (state.outdoorScore > 0) {
                OutdoorScoreCard(
                    score = state.outdoorScore,
                    modifier = modifier,
                    tempCelsius = data.current.temperature,
                    humidity = data.current.humidity,
                    windKmh = data.current.windSpeed,
                    uvIndex = data.current.uvIndex,
                    precipProbability = data.daily.firstOrNull()?.precipitationProbability ?: 0,
                )
            }
        }
        CardType.SNOWFALL -> SnowfallCard(
            snowfall = data.current.snowfall,
            snowDepth = data.current.snowDepth,
            modifier = modifier,
            dailySnowfallSum = data.daily.firstOrNull()?.snowfallSum,
        )
        CardType.SEVERE_WEATHER -> {
            data.current.cape?.let { cape ->
                SevereWeatherCard(cape = cape, modifier = modifier)
            }
        }
        CardType.GOLDEN_HOUR -> {
            state.goldenHourTimes?.let { (morning, evening) ->
                GoldenHourCard(
                    morningGoldenEnd = morning,
                    eveningGoldenStart = evening,
                    sunrise = WeatherFormatter.formatTime(data.current.sunrise, settings),
                    sunset = WeatherFormatter.formatTime(data.current.sunset, settings),
                    modifier = modifier,
                )
            }
        }
        CardType.SUNSHINE -> {
            data.daily.firstOrNull()?.sunshineDuration?.let { seconds ->
                SunshineDurationCard(
                    sunshineDurationSeconds = seconds,
                    modifier = modifier,
                )
            }
        }
        CardType.DRIVING_CONDITIONS -> {
            if (state.drivingAlerts.isNotEmpty()) {
                DrivingAlertCard(alerts = state.drivingAlerts, modifier = modifier)
            }
        }
        CardType.HEALTH_ALERTS -> {
            if (state.healthAlerts.isNotEmpty()) {
                HealthAlertCard(alerts = state.healthAlerts, modifier = modifier)
            }
        }
        CardType.CLOTHING -> {
            if (state.clothingSuggestions.isNotEmpty()) {
                ClothingSuggestionCard(suggestions = state.clothingSuggestions, modifier = modifier)
            }
        }
        CardType.PET_SAFETY -> {
            if (state.petSafetyAlerts.isNotEmpty()) {
                PetSafetyCard(alerts = state.petSafetyAlerts, modifier = modifier)
            }
        }
        CardType.MOON_PHASE -> astronomy?.let { astro ->
            MoonPhaseCard(
                astronomy = astro,
                sunrise = data.current.sunrise,
                sunset = data.current.sunset,
                modifier = modifier,
            )
        }
        CardType.HUMIDITY -> HumidityCard(
            humidity = data.current.humidity,
            dewPoint = data.current.dewPoint,
            modifier = modifier,
        )
        CardType.PRECIPITATION_CHART -> PrecipitationChartCard(
            hourly = data.hourly,
            modifier = modifier,
        )
        CardType.PRESSURE_TREND -> PressureTrendCard(
            hourly = data.hourly,
            currentPressure = data.current.pressure,
            modifier = modifier,
        )
        CardType.WIND_TREND -> WindTrendCard(
            hourly = data.hourly,
            modifier = modifier,
        )
        CardType.DETAILS_GRID -> WeatherDetailsGrid(
            current = data.current,
            modifier = modifier,
            hourly = data.hourly,
        )
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
                    .heightIn(min = 48.dp)
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
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    icon: ImageVector = Icons.Filled.ErrorOutline,
) {
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
                imageVector = icon,
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
