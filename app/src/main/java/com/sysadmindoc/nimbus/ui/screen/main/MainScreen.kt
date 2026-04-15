package com.sysadmindoc.nimbus.ui.screen.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.sysadmindoc.nimbus.ui.component.VisibilityCard
import com.sysadmindoc.nimbus.ui.component.OnThisDayCard
import com.sysadmindoc.nimbus.ui.component.WindTrendCard
import com.sysadmindoc.nimbus.ui.component.CloudCoverCard
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
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusToolbarSurface
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import java.time.Duration
import java.time.LocalDateTime
import com.sysadmindoc.nimbus.ui.theme.skyGradient
import com.sysadmindoc.nimbus.util.AccessibilityHelper
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.ShareWeatherHelper
// WeatherShareHelper consolidated into ShareWeatherHelper

private const val LOCATION_PERMISSION_ACTION_LABEL = "Grant location"

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
    var hasPromptedOptionalPermissions by rememberSaveable { mutableStateOf(false) }

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

    val requestLocationPermissions = {
        hasPromptedPermissions = true
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // Prompt for required location permission automatically once per session.
    LaunchedEffect(state.needsLocationPermission) {
        if (state.needsLocationPermission && !hasPromptedPermissions) {
            requestLocationPermissions()
        }
    }

    // Optional permissions should not disrupt the first-run flow. Request
    // notifications once per session after weather loads and defer background
    // location to feature-specific opt-ins such as alerts/widgets.
    val permContext = LocalContext.current
    LaunchedEffect(state.weatherData) {
        if (state.weatherData != null && hasPromptedPermissions && !hasPromptedOptionalPermissions) {
            hasPromptedOptionalPermissions = true

            // Small delay so location permission dialog fully dismisses
            kotlinx.coroutines.delay(500)

            // Request notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    permContext, Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!notifGranted) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Detect tablet: screen width >= 840dp
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 840
    val visibleTabs = remember(isTablet) { visibleMainTabs(isTablet) }
    val activeSelectedTab = normalizeSelectedMainTab(isTablet, selectedTab)

    LaunchedEffect(isTablet, selectedTab, activeSelectedTab) {
        if (activeSelectedTab != selectedTab) {
            selectedTab = activeSelectedTab
        }
    }

    // Provide unit settings and weather theme state to all child composables
    val weatherThemeState = remember(state.weatherData?.current?.weatherCode, state.weatherData?.current?.isDay) {
        com.sysadmindoc.nimbus.ui.theme.WeatherThemeState(
            weatherCode = state.weatherData?.current?.weatherCode,
            isDay = state.weatherData?.current?.isDay ?: true,
        )
    }
    CompositionLocalProvider(
        LocalUnitSettings provides state.settings,
        com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState provides weatherThemeState,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                ZeusWatchBottomNav(
                    selectedTab = activeSelectedTab,
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
                val hasLocationPermissionError =
                    state.weatherData == null && isLocationPermissionError(state.error)
                val retryLabel = if (hasLocationPermissionError) LOCATION_PERMISSION_ACTION_LABEL else "Retry"
                val retryIcon = if (hasLocationPermissionError) Icons.Filled.LocationOn else Icons.Filled.Refresh
                val retryAction = if (hasLocationPermissionError) {
                    requestLocationPermissions
                } else {
                    { viewModel.loadWeather() }
                }

                when {
                    state.isLoading && state.weatherData == null -> StartupState(
                        title = "Finding Your Forecast",
                        message = if (state.lastLocationName != null) {
                            "Current location can take a few seconds. You can jump into ${state.lastLocationName} or choose another place manually."
                        } else {
                            "Current location can take a few seconds. Choose a city manually if you want to skip GPS."
                        },
                        primaryActionLabel = if (state.lastLocationName != null) "Use Last Location" else "Choose Location",
                        onPrimaryAction = if (state.lastLocationName != null) {
                            { viewModel.useLastLocation() }
                        } else {
                            onNavigateToLocations
                        },
                        secondaryActionLabel = if (state.lastLocationName != null) "Choose Location" else "Retry GPS",
                        onSecondaryAction = if (state.lastLocationName != null) {
                            onNavigateToLocations
                        } else {
                            { viewModel.loadWeather() }
                        },
                        tertiaryActionLabel = if (state.lastLocationName != null) "Retry GPS" else null,
                        onTertiaryAction = if (state.lastLocationName != null) {
                            { viewModel.loadWeather() }
                        } else {
                            null
                        },
                    )
                    state.error != null && state.weatherData == null -> ErrorState(
                        message = state.error!!,
                        onRetry = retryAction,
                        icon = when {
                            state.error!!.contains("permission", ignoreCase = true) ||
                            state.error!!.contains("location", ignoreCase = true) -> Icons.Filled.LocationOff
                            state.error!!.contains("network", ignoreCase = true) ||
                            state.error!!.contains("connect", ignoreCase = true) ||
                            state.error!!.contains("offline", ignoreCase = true) ||
                            state.error!!.contains("internet", ignoreCase = true) -> Icons.Filled.CloudOff
                            else -> Icons.Filled.ErrorOutline
                        },
                        actionLabel = retryLabel,
                        actionIcon = retryIcon,
                        secondaryActionLabel = "Choose Location",
                        onSecondaryAction = onNavigateToLocations,
                    )
                    state.weatherData != null -> {
                        val data = state.weatherData!!
                        val referenceTime = data.current.observationTime
                        val referenceDate = referenceTime?.toLocalDate() ?: data.daily.firstOrNull()?.date

                        if (isTablet) {
                            // ── Two-pane tablet layout ──────────────────
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left pane: Weather content (tab-switched)
                                Box(modifier = Modifier.weight(0.55f)) {
                                    Crossfade(targetState = activeSelectedTab, animationSpec = tween(300), label = "tabletTab") { tab ->
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
                                                onUseLastLocation = { viewModel.useLastLocation() },
                                            )
                                            BottomTab.HOURLY.ordinal -> HourlyTab(
                                                hourly = data.hourly,
                                                locationName = data.location.name,
                                                referenceTime = referenceTime,
                                                isRefreshing = state.isRefreshing,
                                                onRefresh = { viewModel.refresh() },
                                            )
                                            BottomTab.DAILY.ordinal -> DailyTab(
                                                daily = data.daily,
                                                locationName = data.location.name,
                                                referenceDate = referenceDate,
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
                            Crossfade(targetState = activeSelectedTab, animationSpec = tween(300), label = "phoneTab") { tab ->
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
                                        onUseLastLocation = { viewModel.useLastLocation() },
                                    )
                                    BottomTab.HOURLY.ordinal -> HourlyTab(
                                        hourly = data.hourly,
                                        locationName = data.location.name,
                                        referenceTime = referenceTime,
                                    )
                                    BottomTab.DAILY.ordinal -> DailyTab(
                                        daily = data.daily,
                                        locationName = data.location.name,
                                        referenceDate = referenceDate,
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
    onUseLastLocation: () -> Unit = {},
) {
    when {
        state.isLoading && state.weatherData == null -> StartupState(
            title = "Loading Weather",
            message = if (state.lastLocationName != null) {
                "Still checking your current position. You can open ${state.lastLocationName} instead or choose another saved place."
            } else {
                "Still checking your current position. Choose a city manually if GPS is taking too long."
            },
            primaryActionLabel = if (state.lastLocationName != null) "Use Last Location" else "Choose Location",
            onPrimaryAction = if (state.lastLocationName != null) onUseLastLocation else onNavigateToLocations,
            secondaryActionLabel = if (state.lastLocationName != null) "Choose Location" else "Retry GPS",
            onSecondaryAction = if (state.lastLocationName != null) onNavigateToLocations else onRetry,
            tertiaryActionLabel = if (state.lastLocationName != null) "Retry GPS" else null,
            onTertiaryAction = if (state.lastLocationName != null) onRetry else null,
        )
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
            actionLabel = if (isLocationPermissionError(state.error)) LOCATION_PERMISSION_ACTION_LABEL else "Retry",
            actionIcon = if (isLocationPermissionError(state.error)) Icons.Filled.LocationOn else Icons.Filled.Refresh,
            secondaryActionLabel = "Choose Location",
            onSecondaryAction = onNavigateToLocations,
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
        else -> ShimmerLoadingSkeleton()
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
                .padding(bottom = 8.dp),
        ) {
            // ── Toolbar ─────────────────────────────────────────────
            item(key = "toolbar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.contentPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PremiumToolbarButton(
                        icon = Icons.Filled.LocationOn,
                        contentDescription = "Manage locations",
                        onClick = onNavigateToLocations,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box {
                            PremiumToolbarButton(
                                icon = Icons.Filled.Share,
                                contentDescription = "Share weather",
                                onClick = { showShareMenu = true },
                            )
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
                        PremiumToolbarButton(
                            icon = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Compare locations",
                            onClick = onNavigateToCompare,
                        )
                        PremiumToolbarButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            onClick = onNavigateToSettings,
                        )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (todayPrecipChance > 0) {
                            StatusBadge(
                                icon = Icons.Filled.WaterDrop,
                                text = "$todayPrecipChance% rain today",
                                tint = NimbusBlueAccent,
                            )
                        }
                        val stalenessColor = when {
                            isCached -> NimbusTextTertiary.copy(alpha = 0.78f)
                            updatedAgo == "Just now" || updatedAgo.endsWith("m ago") -> NimbusTextTertiary
                            updatedAgo.contains("1h") -> NimbusWarning.copy(alpha = 0.7f)
                            updatedAgo.contains("h") -> NimbusWarning
                            else -> NimbusTextTertiary
                        }
                        StatusBadge(
                            text = if (isCached) "Cached • $updatedAgo" else "Updated $updatedAgo",
                            tint = stalenessColor,
                        )
                    }
                }
            }

            // ── Spacer before cards ───────────────────────────────
            item(key = "pre_cards_spacer") {
                Spacer(modifier = Modifier.height(18.dp))
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

@Composable
private fun PremiumToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusToolbarSurface,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NimbusTextPrimary.copy(alpha = 0.88f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = tint,
        )
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
    val referenceTime = data.current.observationTime
    val referenceDate = referenceTime?.toLocalDate() ?: data.daily.firstOrNull()?.date

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
                NowcastCard(
                    data = state.nowcastData,
                    referenceTime = referenceTime,
                    modifier = modifier,
                )
            }
        }
        CardType.HOURLY_FORECAST -> HourlyForecastStrip(
            hourly = data.hourly,
            referenceTime = referenceTime,
            modifier = modifier,
        )
        CardType.TEMPERATURE_GRAPH -> {
            if (data.hourly.size >= 4) {
                val avgHigh = data.daily.takeIf { it.size > 2 }?.map { it.temperatureHigh }?.average()
                val avgLow = data.daily.takeIf { it.size > 2 }?.map { it.temperatureLow }?.average()
                TemperatureGraph(
                    hourly = data.hourly,
                    referenceTime = referenceTime,
                    modifier = modifier,
                    normalHigh = avgHigh,
                    normalLow = avgLow,
                )
            }
        }
        CardType.DAILY_FORECAST -> DailyForecastList(
            daily = data.daily,
            referenceDate = referenceDate,
            modifier = modifier,
        )
        CardType.UV_INDEX -> UvIndexBar(
            uvIndex = data.current.uvIndex,
            modifier = modifier,
            hourly = data.hourly,
            referenceTime = referenceTime,
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
                referenceTime = referenceTime,
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
            referenceTime = referenceTime,
            modifier = modifier,
        )
        CardType.PRESSURE_TREND -> PressureTrendCard(
            hourly = data.hourly,
            currentPressure = data.current.pressure,
            referenceTime = referenceTime,
            modifier = modifier,
        )
        CardType.WIND_TREND -> WindTrendCard(
            hourly = data.hourly,
            referenceTime = referenceTime,
            modifier = modifier,
        )
        CardType.DETAILS_GRID -> WeatherDetailsGrid(
            current = data.current,
            modifier = modifier,
            hourly = data.hourly,
        )
        CardType.CLOUD_COVER -> CloudCoverCard(
            hourly = data.hourly,
            referenceTime = referenceTime,
            modifier = modifier,
        )
        CardType.VISIBILITY -> VisibilityCard(
            visibilityMeters = data.current.visibility,
            hourly = data.hourly,
            modifier = modifier,
        )
        CardType.ON_THIS_DAY -> OnThisDayCard(
            data = state.onThisDay,
            forecastHighC = data.daily.firstOrNull()?.temperatureHigh,
            modifier = modifier,
        )
    }
}

// ── Location Selector Bar with Animated Dot Indicator ─────────────────────

@Composable
private fun LocationSelectorBar(
    locations: List<SavedLocationEntity>,
    currentIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Chip row (tappable location names)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            locations.forEachIndexed { index, loc ->
                val isActive = index == currentIndex
                val chipShape = RoundedCornerShape(22.dp)
                val chipBrush = if (isActive) {
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusBlueAccent.copy(alpha = 0.26f),
                            NimbusGlassBottom,
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop.copy(alpha = 0.7f),
                            NimbusCardBg,
                        ),
                    )
                }
                val borderColor = if (isActive) NimbusBlueAccent.copy(alpha = 0.65f) else NimbusCardBorder

                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(chipShape)
                        .background(chipBrush)
                        .border(1.dp, borderColor, chipShape)
                        .clickable { onSelected(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NimbusBlueAccent),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (loc.isCurrentLocation) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = if (isActive) NimbusTextPrimary else NimbusTextTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (loc.isCurrentLocation) "My Location" else loc.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isActive) NimbusTextPrimary else NimbusTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Animated dot indicator (breezy-weather InkPageIndicator style)
        if (locations.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            InkPageIndicator(
                pageCount = locations.size,
                currentPage = currentIndex,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Animated page indicator dots inspired by breezy-weather's InkPageIndicator.
 * Active dot is larger and colored, inactive dots are smaller and dimmed.
 */
@Composable
private fun InkPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val dotSize = 6.dp
    val activeDotSize = 10.dp
    val spacing = 8.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val size = if (isActive) activeDotSize else dotSize
            val color = if (isActive) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.4f)

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size / 2))
                    .background(color),
            )
            if (index < pageCount - 1) {
                Spacer(modifier = Modifier.width(spacing))
            }
        }
    }
}

// ── Error State ──────────────────────────────────────────────────────────

@Composable
private fun StartupState(
    title: String,
    message: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    tertiaryActionLabel: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop,
                            NimbusGlassBottom,
                        ),
                    ),
                )
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = NimbusBlueAccent,
                    strokeWidth = 2.5.dp,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NimbusTextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
                textAlign = TextAlign.Center,
            )

            StatusBadge(
                text = "Current location can take a few seconds",
                tint = NimbusBlueAccent,
                icon = Icons.Filled.MyLocation,
            )

            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NimbusBlueAccent),
            ) {
                Text(primaryActionLabel)
            }
            OutlinedButton(
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, NimbusCardBorder),
            ) {
                Text(secondaryActionLabel, color = NimbusTextPrimary)
            }
            if (tertiaryActionLabel != null && onTertiaryAction != null) {
                Text(
                    text = tertiaryActionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = NimbusBlueAccent,
                    modifier = Modifier.clickable(onClick = onTertiaryAction),
                )
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    icon: ImageVector = Icons.Filled.ErrorOutline,
    actionLabel: String = "Retry",
    actionIcon: ImageVector = Icons.Filled.Refresh,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop,
                            NimbusGlassBottom,
                        ),
                    ),
                )
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 28.dp, vertical = 30.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = NimbusTextSecondary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = NimbusTextSecondary,
                textAlign = TextAlign.Center,
            )
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                StatusBadge(
                    text = "Manual locations are still available",
                    tint = NimbusBlueAccent,
                    icon = Icons.Filled.LocationOn,
                )
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NimbusBlueAccent),
            ) {
                Icon(actionIcon, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(4.dp))
                Text(actionLabel)
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                OutlinedButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NimbusCardBorder),
                ) {
                    Text(secondaryActionLabel, color = NimbusTextPrimary)
                }
            }
        }
    }
}

private fun isLocationPermissionError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return message.contains("permission", ignoreCase = true) ||
        message.contains("grant location", ignoreCase = true)
}

internal fun visibleMainTabs(isTablet: Boolean): List<BottomTab> {
    return if (isTablet) {
        BottomTab.entries.filter { it != BottomTab.RADAR }
    } else {
        BottomTab.entries
    }
}

internal fun normalizeSelectedMainTab(isTablet: Boolean, selectedTab: Int): Int {
    val visibleOrdinals = visibleMainTabs(isTablet).map { it.ordinal }.toSet()
    return if (selectedTab in visibleOrdinals) selectedTab else BottomTab.TODAY.ordinal
}
