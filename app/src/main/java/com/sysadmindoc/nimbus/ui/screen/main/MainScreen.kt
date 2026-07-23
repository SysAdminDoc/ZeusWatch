package com.sysadmindoc.nimbus.ui.screen.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sysadmindoc.nimbus.util.needsAppSettings
import com.sysadmindoc.nimbus.util.resolveLocationPermissionUiState
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.BuildConfig
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
    val whatsNewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showWhatsNewSheet = state.settings.onboardingComplete &&
        state.lastSeenVersionCode < BuildConfig.VERSION_CODE

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
        // Persist that we've asked so a later "denied with no rationale" reads as
        // a permanent denial (App Settings) rather than a never-asked state.
        viewModel.onLocationPermissionRequested()
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // NX-26: app launch never triggers a permission dialog. Location is only
    // requested from an explicit "Use my location" affordance (the location-error
    // recovery card / retry), so search and saved places stay usable without any
    // grant. The old auto-prompt LaunchedEffect was removed deliberately.

    // Resolve the recoverable permission state so the error card can choose
    // between re-requesting (with rationale) and sending the user to App Settings.
    val permissionStateContext = LocalContext.current
    val activity = permissionStateContext.findActivity()
    val locationGranted = ContextCompat.checkSelfPermission(
        permissionStateContext, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val shouldShowRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
    } ?: false
    val locationPermissionUiState = resolveLocationPermissionUiState(
        granted = locationGranted,
        hasRequestedBefore = state.settings.locationPermissionRequested,
        shouldShowRationale = shouldShowRationale,
    )

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

    val adaptiveLayout = LocalAdaptiveLayout.current
    val isTablet = adaptiveLayout.supportsTwoPaneWeather || adaptiveLayout.isTabletop
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
    // Remembered so the holders keep a stable identity across recompositions —
    // fresh instances every pass would flow into @Immutable CardRenderContext
    // and defeat card skip optimizations. The lambdas only capture stable
    // references (viewModel, nav callbacks) and read state at invocation time,
    // so nothing here can go stale.
    val contentActions = remember(viewModel, onNavigateToSettings, onNavigateToRadar, onNavigateToLocations, onNavigateToCompare) {
        MainContentActions(
            onRefresh = { viewModel.refresh() },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRadar = onNavigateToRadar,
            onNavigateToLocations = onNavigateToLocations,
            onNavigateToCompare = onNavigateToCompare,
            onLocationSelected = { index -> viewModel.onPageChanged(index) },
            onHistoricalDateSelected = { date -> viewModel.selectHistoricalDate(date) },
        )
    }
    val settingsContext = LocalContext.current
    val openAppSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", settingsContext.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { settingsContext.startActivity(intent) }
    }
    val openLocationServices = {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { settingsContext.startActivity(intent) }
    }
    val permanentlyDenied = locationPermissionUiState.needsAppSettings
    val screenActions = remember(
        contentActions, viewModel, requestLocationPermissions,
        openAppSettings, openLocationServices, permanentlyDenied,
    ) {
        MainScreenActions(
            content = contentActions,
            onLoadWeather = { viewModel.loadWeather() },
            onUseLastLocation = { viewModel.useLastLocation() },
            onRequestLocationPermissions = requestLocationPermissions,
            onOpenAppSettings = { openAppSettings() },
            onOpenLocationServices = { openLocationServices() },
            locationPermissionPermanentlyDenied = permanentlyDenied,
        )
    }
    CompositionLocalProvider(
        LocalUnitSettings provides state.settings,
        com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState provides weatherThemeState,
    ) {
        if (showWhatsNewSheet) {
            WhatsNewSheet(
                sheetState = whatsNewSheetState,
                onDismiss = { viewModel.dismissWhatsNew() },
            )
        }

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

/** Walks the [ContextWrapper] chain to find the hosting [Activity], if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsNewSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val items = stringArrayResource(R.array.whats_new_items)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NimbusNavyDark,
        contentColor = NimbusTextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NimbusStatusBadge(
                text = stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                tint = NimbusBlueAccent,
                emphasized = true,
            )
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.headlineSmall,
                color = NimbusTextPrimary,
            )
            Text(
                text = stringResource(R.string.whats_new_body),
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NimbusBlueAccent),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NimbusTextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.whats_new_done))
            }
        }
    }
}
