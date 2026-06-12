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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
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
import com.sysadmindoc.nimbus.ui.component.ForecastEvolutionCard
import com.sysadmindoc.nimbus.ui.component.HourlyForecastStrip
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.component.DrivingAlertCard
import com.sysadmindoc.nimbus.ui.component.GoldenHourCard
import com.sysadmindoc.nimbus.ui.component.HealthAlertCard
import com.sysadmindoc.nimbus.ui.component.HumidityCard
import com.sysadmindoc.nimbus.ui.component.InlineNoticeCard
import com.sysadmindoc.nimbus.ui.component.GlassActionButton
import com.sysadmindoc.nimbus.ui.component.MoonPhaseCard
import com.sysadmindoc.nimbus.ui.component.NimbusScrollableSegmentRow
import com.sysadmindoc.nimbus.ui.component.NimbusSelectableSegment
import com.sysadmindoc.nimbus.ui.component.NimbusStatusBadge
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
import com.sysadmindoc.nimbus.ui.component.PremiumMessageCard
import com.sysadmindoc.nimbus.ui.navigation.BottomTab
import com.sysadmindoc.nimbus.ui.navigation.LocalMainDeepLinkTarget
import com.sysadmindoc.nimbus.ui.navigation.MainDeepLinkTarget
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
import java.util.Locale
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
    val deepLinkTarget = LocalMainDeepLinkTarget.current

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

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null && selectedTab != BottomTab.TODAY.ordinal) {
            selectedTab = BottomTab.TODAY.ordinal
        }
    }

    // Provide unit settings and weather theme state to all child composables
    val weatherThemeState = remember(state.weatherData?.current?.weatherCode, state.weatherData?.current?.isDay) {
        com.sysadmindoc.nimbus.ui.theme.WeatherThemeState(
            weatherCode = state.weatherData?.current?.weatherCode,
            isDay = state.weatherData?.current?.isDay ?: true,
        )
    }
    // Push the derived state up to MainActivity (via the app-scoped bus) so
    // NimbusTheme — an ancestor of this screen — can apply the
    // weather-adaptive color scheme. The local provider below only reaches
    // descendants.
    LaunchedEffect(weatherThemeState) {
        com.sysadmindoc.nimbus.ui.theme.WeatherThemeBus.state.value = weatherThemeState
    }
    val contentActions = MainContentActions(
        onRefresh = { viewModel.refresh() },
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToRadar = onNavigateToRadar,
        onNavigateToLocations = onNavigateToLocations,
        onNavigateToCompare = onNavigateToCompare,
        onLocationSelected = { index -> viewModel.onPageChanged(index) },
    )
    val screenActions = MainScreenActions(
        content = contentActions,
        onLoadWeather = { viewModel.loadWeather() },
        onUseLastLocation = { viewModel.useLastLocation() },
        onRequestLocationPermissions = requestLocationPermissions,
    )
    CompositionLocalProvider(
        LocalUnitSettings provides state.settings,
        com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState provides weatherThemeState,
    ) {
        MainScreenScaffold(
            state = state,
            selectedTab = activeSelectedTab,
            visibleTabs = visibleTabs,
            isTablet = isTablet,
            actions = screenActions,
            onTabSelected = { selectedTab = it },
        )
    }
}
