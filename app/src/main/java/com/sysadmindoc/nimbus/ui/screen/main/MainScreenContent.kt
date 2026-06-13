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
import androidx.compose.material3.HorizontalDivider
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
import com.sysadmindoc.nimbus.data.model.AlertSeverity
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
import com.sysadmindoc.nimbus.ui.component.ExtremeAlertTakeover
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
import com.sysadmindoc.nimbus.data.repository.accessibilityCardOrder
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

internal data class MainContentActions(
    val onRefresh: () -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onNavigateToRadar: (Double, Double) -> Unit,
    val onNavigateToLocations: () -> Unit,
    val onNavigateToCompare: () -> Unit,
    val onLocationSelected: (Int) -> Unit,
)

internal data class MainScreenActions(
    val content: MainContentActions,
    val onLoadWeather: () -> Unit,
    val onUseLastLocation: () -> Unit,
    val onRequestLocationPermissions: () -> Unit,
)

@Composable
internal fun MainScreenScaffold(
    state: MainUiState,
    selectedTab: Int,
    visibleTabs: List<BottomTab>,
    isTablet: Boolean,
    actions: MainScreenActions,
    onTabSelected: (Int) -> Unit,
) {
    Scaffold(
        containerColor = NimbusNavyDark,
        bottomBar = {
            ZeusWatchBottomNav(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                visibleTabs = visibleTabs,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MainScreenBody(
                state = state,
                selectedTab = selectedTab,
                isTablet = isTablet,
                actions = actions,
            )
        }
    }
}

@Composable
private fun MainScreenBody(
    state: MainUiState,
    selectedTab: Int,
    isTablet: Boolean,
    actions: MainScreenActions,
) {
    val chooseLocationLabel = stringResource(R.string.common_choose_location)
    val retryGpsLabel = stringResource(R.string.common_retry_gps)
    val useLastLocationLabel = stringResource(R.string.main_use_last_location)
    val lastLocationName = state.lastLocationName

    when {
        state.isLoading && state.weatherData == null -> StartupState(
            title = stringResource(R.string.main_finding_forecast_title),
            message = if (lastLocationName != null) {
                stringResource(R.string.main_finding_forecast_with_last, lastLocationName)
            } else {
                stringResource(R.string.main_finding_forecast_without_last)
            },
            primaryActionLabel = if (lastLocationName != null) useLastLocationLabel else chooseLocationLabel,
            onPrimaryAction = if (lastLocationName != null) actions.onUseLastLocation else actions.content.onNavigateToLocations,
            secondaryActionLabel = if (lastLocationName != null) chooseLocationLabel else retryGpsLabel,
            onSecondaryAction = if (lastLocationName != null) actions.content.onNavigateToLocations else actions.onLoadWeather,
            tertiaryActionLabel = if (lastLocationName != null) retryGpsLabel else null,
            onTertiaryAction = if (lastLocationName != null) actions.onLoadWeather else null,
        )
        state.error != null && state.weatherData == null -> MainErrorState(
            state = state,
            actions = actions,
        )
        state.weatherData != null -> WeatherTabHost(
            state = state,
            selectedTab = selectedTab,
            isTablet = isTablet,
            actions = actions,
        )
        else -> ErrorState(
            message = stringResource(R.string.main_loading_weather_data),
            onRetry = actions.onLoadWeather,
        )
    }
}

@Composable
private fun MainErrorState(
    state: MainUiState,
    actions: MainScreenActions,
) {
    val error = state.error ?: return
    val hasLocationPermissionError = isLocationPermissionError(error)
    ErrorState(
        message = mainErrorMessage(error),
        onRetry = if (hasLocationPermissionError) actions.onRequestLocationPermissions else actions.onLoadWeather,
        icon = errorIconForError(error),
        actionLabel = if (hasLocationPermissionError) {
            stringResource(R.string.main_grant_location)
        } else {
            stringResource(R.string.retry)
        },
        actionIcon = if (hasLocationPermissionError) Icons.Filled.LocationOn else Icons.Filled.Refresh,
        secondaryActionLabel = stringResource(R.string.common_choose_location),
        onSecondaryAction = actions.content.onNavigateToLocations,
        isLocationPermissionError = hasLocationPermissionError,
    )
}

@Composable
private fun mainErrorMessage(error: MainUiError): String = when (error.kind) {
    MainUiErrorKind.LOCATION_PERMISSION_REQUIRED -> stringResource(R.string.main_error_location_permission_required)
    MainUiErrorKind.LOCATION_PERMISSION_DENIED -> stringResource(R.string.main_error_location_permission_short)
    MainUiErrorKind.LOCATION_SERVICES_OFF -> stringResource(R.string.main_error_location_services_off)
    MainUiErrorKind.LOCATION_UNAVAILABLE -> stringResource(R.string.main_error_location_unavailable)
    MainUiErrorKind.NO_INTERNET -> stringResource(R.string.main_error_no_internet)
    MainUiErrorKind.TIMEOUT -> stringResource(R.string.main_error_timeout)
    MainUiErrorKind.CONNECTION -> stringResource(R.string.main_error_connect_weather_service)
    MainUiErrorKind.WEATHER_SERVICE -> stringResource(R.string.main_error_weather_service, error.serviceCode ?: 0)
    MainUiErrorKind.GENERIC -> stringResource(R.string.main_error_generic_retry)
    MainUiErrorKind.CHOOSE_LOCATION -> stringResource(R.string.main_error_choose_location)
}

private fun errorIconForError(error: MainUiError): ImageVector = when (error.kind) {
    MainUiErrorKind.LOCATION_PERMISSION_REQUIRED,
    MainUiErrorKind.LOCATION_PERMISSION_DENIED,
    MainUiErrorKind.LOCATION_SERVICES_OFF,
    MainUiErrorKind.LOCATION_UNAVAILABLE -> Icons.Filled.LocationOff
    MainUiErrorKind.NO_INTERNET,
    MainUiErrorKind.TIMEOUT,
    MainUiErrorKind.CONNECTION -> Icons.Filled.CloudOff
    else -> Icons.Filled.ErrorOutline
}

@Composable
private fun WeatherTabHost(
    state: MainUiState,
    selectedTab: Int,
    isTablet: Boolean,
    actions: MainScreenActions,
) {
    val data = state.weatherData ?: return
    val referenceTime = data.current.observationTime
    val referenceDate = referenceTime?.toLocalDate() ?: data.daily.firstOrNull()?.date

    val layout = LocalAdaptiveLayout.current
    if (layout.isTabletop) {
        TabletopWeatherTabs(
            state = state,
            selectedTab = selectedTab,
            referenceDate = referenceDate,
            actions = actions,
        )
    } else if (isTablet) {
        TabletWeatherTabs(
            state = state,
            selectedTab = selectedTab,
            referenceDate = referenceDate,
            actions = actions,
        )
    } else {
        PhoneWeatherTabs(
            state = state,
            selectedTab = selectedTab,
            referenceDate = referenceDate,
            actions = actions,
        )
    }
}

@Composable
private fun TabletopWeatherTabs(
    state: MainUiState,
    selectedTab: Int,
    referenceDate: java.time.LocalDate?,
    actions: MainScreenActions,
) {
    val data = state.weatherData ?: return
    val referenceTime = data.current.observationTime
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(0.4f)) {
            RadarTab(
                latitude = data.location.latitude,
                longitude = data.location.longitude,
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = NimbusCardBg,
        )
        Box(modifier = Modifier.weight(0.6f)) {
            Crossfade(targetState = selectedTab, animationSpec = tween(300), label = "tabletopTab") { tab ->
                when (tab) {
                    BottomTab.TODAY.ordinal -> TodayContent(state = state, actions = actions)
                    BottomTab.HOURLY.ordinal -> HourlyTab(
                        hourly = data.hourly,
                        locationName = data.location.name,
                        referenceTime = referenceTime,
                        isRefreshing = state.isRefreshing,
                        onRefresh = actions.content.onRefresh,
                    )
                    BottomTab.DAILY.ordinal -> DailyTab(
                        daily = data.daily,
                        locationName = data.location.name,
                        referenceDate = referenceDate,
                        isRefreshing = state.isRefreshing,
                        onRefresh = actions.content.onRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabletWeatherTabs(
    state: MainUiState,
    selectedTab: Int,
    referenceDate: java.time.LocalDate?,
    actions: MainScreenActions,
) {
    val data = state.weatherData ?: return
    val referenceTime = data.current.observationTime
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(0.55f)) {
            Crossfade(targetState = selectedTab, animationSpec = tween(300), label = "tabletTab") { tab ->
                when (tab) {
                    BottomTab.TODAY.ordinal -> TodayContent(state = state, actions = actions)
                    BottomTab.HOURLY.ordinal -> HourlyTab(
                        hourly = data.hourly,
                        locationName = data.location.name,
                        referenceTime = referenceTime,
                        isRefreshing = state.isRefreshing,
                        onRefresh = actions.content.onRefresh,
                    )
                    BottomTab.DAILY.ordinal -> DailyTab(
                        daily = data.daily,
                        locationName = data.location.name,
                        referenceDate = referenceDate,
                        isRefreshing = state.isRefreshing,
                        onRefresh = actions.content.onRefresh,
                    )
                }
            }
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = NimbusCardBg,
        )
        Box(modifier = Modifier.weight(0.45f)) {
            RadarTab(
                latitude = data.location.latitude,
                longitude = data.location.longitude,
            )
        }
    }
}

@Composable
private fun PhoneWeatherTabs(
    state: MainUiState,
    selectedTab: Int,
    referenceDate: java.time.LocalDate?,
    actions: MainScreenActions,
) {
    val data = state.weatherData ?: return
    val referenceTime = data.current.observationTime
    Crossfade(targetState = selectedTab, animationSpec = tween(300), label = "phoneTab") { tab ->
        when (tab) {
            BottomTab.TODAY.ordinal -> TodayContent(state = state, actions = actions)
            BottomTab.HOURLY.ordinal -> HourlyTab(
                hourly = data.hourly,
                locationName = data.location.name,
                referenceTime = referenceTime,
                isRefreshing = state.isRefreshing,
                onRefresh = actions.content.onRefresh,
            )
            BottomTab.DAILY.ordinal -> DailyTab(
                daily = data.daily,
                locationName = data.location.name,
                referenceDate = referenceDate,
                isRefreshing = state.isRefreshing,
                onRefresh = actions.content.onRefresh,
            )
            BottomTab.RADAR.ordinal -> RadarTab(
                latitude = data.location.latitude,
                longitude = data.location.longitude,
            )
        }
    }
}

@Composable
private fun TodayContent(
    state: MainUiState,
    actions: MainScreenActions,
) {
    TodayContent(
        state = state,
        onRetry = actions.onLoadWeather,
        onRefresh = actions.content.onRefresh,
        onNavigateToSettings = actions.content.onNavigateToSettings,
        onNavigateToRadar = actions.content.onNavigateToRadar,
        onNavigateToLocations = actions.content.onNavigateToLocations,
        onNavigateToCompare = actions.content.onNavigateToCompare,
        onLocationSelected = actions.content.onLocationSelected,
        onUseLastLocation = actions.onUseLastLocation,
    )
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
    val chooseLocationLabel = stringResource(R.string.common_choose_location)
    val retryGpsLabel = stringResource(R.string.common_retry_gps)
    val useLastLocationLabel = stringResource(R.string.main_use_last_location)
    val grantLocationLabel = stringResource(R.string.main_grant_location)
    val retryLabel = stringResource(R.string.retry)
    val lastLocationName = state.lastLocationName
    val contentActions = MainContentActions(
        onRefresh = onRefresh,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToRadar = onNavigateToRadar,
        onNavigateToLocations = onNavigateToLocations,
        onNavigateToCompare = onNavigateToCompare,
        onLocationSelected = onLocationSelected,
    )

    when {
        state.isLoading && state.weatherData == null -> StartupState(
            title = stringResource(R.string.main_loading_weather_title),
            message = if (lastLocationName != null) {
                stringResource(R.string.main_loading_weather_with_last, lastLocationName)
            } else {
                stringResource(R.string.main_loading_weather_without_last)
            },
            primaryActionLabel = if (lastLocationName != null) useLastLocationLabel else chooseLocationLabel,
            onPrimaryAction = if (lastLocationName != null) onUseLastLocation else onNavigateToLocations,
            secondaryActionLabel = if (lastLocationName != null) chooseLocationLabel else retryGpsLabel,
            onSecondaryAction = if (lastLocationName != null) onNavigateToLocations else onRetry,
            tertiaryActionLabel = if (lastLocationName != null) retryGpsLabel else null,
            onTertiaryAction = if (lastLocationName != null) onRetry else null,
        )
        state.error != null && state.weatherData == null -> {
            val error = state.error
            ErrorState(
                message = mainErrorMessage(error),
                onRetry = onRetry,
                icon = errorIconForError(error),
                actionLabel = if (isLocationPermissionError(error)) grantLocationLabel else retryLabel,
                actionIcon = if (isLocationPermissionError(error)) Icons.Filled.LocationOn else Icons.Filled.Refresh,
                secondaryActionLabel = chooseLocationLabel,
                onSecondaryAction = onNavigateToLocations,
                isLocationPermissionError = isLocationPermissionError(error),
            )
        }
        state.weatherData != null -> WeatherContent(
            state = state,
            actions = contentActions,
        )
        else -> ShimmerLoadingSkeleton()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherContent(
    state: MainUiState = MainUiState(),
    actions: MainContentActions,
) {
    val data = state.weatherData ?: return
    val airQuality = state.airQuality
    val astronomy = state.astronomy
    val alerts = state.alerts
    val bgBrush = skyGradient(
        isDay = data.current.isDay,
        weatherCode = data.current.weatherCode.code,
    )
    val layout = LocalAdaptiveLayout.current
    val deepLinkTarget = LocalMainDeepLinkTarget.current

    var selectedAlert by remember { mutableStateOf<WeatherAlert?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showShareMenu by remember { mutableStateOf(false) }
    val settings = LocalUnitSettings.current
    val justNowLabel = stringResource(R.string.main_just_now)
    val minuteAgoFormat = stringResource(R.string.main_minutes_ago)
    val hourAgoFormat = stringResource(R.string.main_hours_ago)

    selectedAlert?.let { alert ->
        AlertDetailSheet(
            alert = alert,
            sheetState = sheetState,
            onDismiss = { selectedAlert = null },
        )
    }

    val enabledCards = remember(settings.cardOrder, settings.disabledCards, settings.accessibilityLayout) {
        val ordered = settings.cardOrder.filter { card -> card.name !in settings.disabledCards }
        if (settings.accessibilityLayout) accessibilityCardOrder(ordered) else ordered
    }
    val focusedCard = mainDeepLinkCardTarget(deepLinkTarget)
    val visibleCards = remember(enabledCards, focusedCard) {
        cardsForDeepLinkTarget(enabledCards, focusedCard)
    }
    val listState = rememberLazyListState()

    val todayPrecipChance = data.daily.firstOrNull()?.precipitationProbability ?: 0
    var updatedAgo by remember { mutableStateOf("") }
    var updatedAgeMinutes by remember { mutableStateOf(0L) }
    LaunchedEffect(data.lastUpdated, justNowLabel, minuteAgoFormat, hourAgoFormat) {
        while (true) {
            val minutes = Duration.between(data.lastUpdated, LocalDateTime.now()).toMinutes()
            updatedAgeMinutes = minutes
            updatedAgo = when {
                minutes < 1 -> justNowLabel
                minutes < 60 -> String.format(Locale.getDefault(), minuteAgoFormat, minutes)
                else -> String.format(Locale.getDefault(), hourAgoFormat, minutes / 60)
            }
            kotlinx.coroutines.delay(60_000L)
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val cardPad = Modifier.padding(horizontal = layout.contentPadding)
        val hasLocationBar = state.savedLocations.size > 1
        val hasAlertBanner = alerts.isNotEmpty()
        val focusedItemIndex = weatherContentItemIndexForDeepLinkTarget(
            target = deepLinkTarget,
            visibleCards = visibleCards,
            hasOfflineBanner = state.isOffline,
            hasLocationBar = hasLocationBar,
            hasAlertBanner = hasAlertBanner,
        )

        LaunchedEffect(
            deepLinkTarget,
            focusedItemIndex,
            alerts.size,
            state.nowcastData.size,
            state.healthAlerts.size,
        ) {
            focusedItemIndex?.let { listState.animateScrollToItem(it) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(bottom = 8.dp),
        ) {
            item(key = "toolbar") {
                WeatherToolbar(
                    layout = layout,
                    data = data,
                    airQuality = airQuality,
                    settings = settings,
                    actions = actions,
                    showShareMenu = showShareMenu,
                    onShareMenuChange = { showShareMenu = it },
                )
            }

            if (state.isOffline) {
                item(key = "offline_banner") {
                    InlineNoticeCard(
                        title = stringResource(R.string.main_offline_mode_title),
                        message = stringResource(R.string.main_offline_mode_message),
                        icon = Icons.Filled.CloudOff,
                        tint = NimbusWarning,
                        modifier = Modifier.padding(horizontal = layout.contentPadding, vertical = 4.dp),
                    )
                }
            }

            if (hasLocationBar) {
                item(key = "location_bar") {
                    LocationSelectorBar(
                        locations = state.savedLocations,
                        currentIndex = state.currentPage,
                        onSelected = actions.onLocationSelected,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            if (hasAlertBanner) {
                item(key = "alert_banner") {
                    WeatherAlertSection(
                        alerts = alerts,
                        onAlertClick = { selectedAlert = it },
                        bannerModifier = Modifier.padding(horizontal = layout.contentPadding, vertical = 4.dp),
                    )
                }
            }

            item(key = "hero") {
                WeatherHero(
                    data = data,
                    state = state,
                    settings = settings,
                )
            }

            item(key = "updated_row") {
                WeatherUpdatedRow(
                    layout = layout,
                    todayPrecipChance = todayPrecipChance,
                    updatedAgeMinutes = updatedAgeMinutes,
                    updatedAgo = updatedAgo,
                    isCached = state.isCached,
                    sourceProvider = data.sourceProvider,
                    usedFallback = data.usedFallback,
                )
            }

            item(key = "pre_cards_spacer") {
                Spacer(modifier = Modifier.height(18.dp))
            }

            weatherCardItems(
                visibleCards = visibleCards,
                cardPad = cardPad,
                cardSpacing = layout.cardSpacing,
                context = CardRenderContext(
                    state = state,
                    data = data,
                    airQuality = airQuality,
                    astronomy = astronomy,
                    settings = settings,
                    radarPreviewTileUrl = state.radarPreviewTileUrl,
                    radarBaseMapUrl = state.radarBaseMapUrl,
                    onNavigateToRadar = actions.onNavigateToRadar,
                ),
            )

            weatherFooterItem()
        }
    }
}

@Composable
private fun WeatherAlertSection(
    alerts: List<WeatherAlert>,
    onAlertClick: (WeatherAlert) -> Unit,
    bannerModifier: Modifier = Modifier,
) {
    val extremeAlert = remember(alerts) { alerts.firstOrNull { it.severity == AlertSeverity.EXTREME } }
    val bannerAlerts = remember(alerts, extremeAlert?.id) {
        if (extremeAlert == null) alerts else alerts.filterNot { it.id == extremeAlert.id }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        extremeAlert?.let { alert ->
            ExtremeAlertTakeover(
                alert = alert,
                onAlertClick = onAlertClick,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (bannerAlerts.isNotEmpty()) {
            AlertBanner(
                alerts = bannerAlerts,
                onAlertClick = onAlertClick,
                modifier = bannerModifier,
            )
        }
    }
}

@Composable
private fun WeatherToolbar(
    layout: AdaptiveLayoutInfo,
    data: WeatherData,
    airQuality: AirQualityData?,
    settings: NimbusSettings,
    actions: MainContentActions,
    showShareMenu: Boolean,
    onShareMenuChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.contentPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumToolbarButton(
            icon = Icons.Filled.LocationOn,
            contentDescription = stringResource(R.string.main_manage_locations_cd),
            onClick = actions.onNavigateToLocations,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                PremiumToolbarButton(
                    icon = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.main_share_weather_cd),
                    onClick = { onShareMenuChange(true) },
                )
                DropdownMenu(
                    expanded = showShareMenu,
                    onDismissRequest = { onShareMenuChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_as_text)) },
                        onClick = {
                            onShareMenuChange(false)
                            ShareWeatherHelper.share(context, data, airQuality, settings)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_as_image)) },
                        onClick = {
                            onShareMenuChange(false)
                            ShareWeatherHelper.shareAsImage(context, data, settings)
                        },
                    )
                }
            }
            PremiumToolbarButton(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = stringResource(R.string.main_compare_locations_cd),
                onClick = actions.onNavigateToCompare,
            )
            PremiumToolbarButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.main_settings_cd),
                onClick = actions.onNavigateToSettings,
            )
        }
    }
}

@Composable
private fun WeatherHero(
    data: WeatherData,
    state: MainUiState,
    settings: NimbusSettings,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = AccessibilityHelper.currentConditions(
                context, data.current, data.location.name, settings,
            )
        },
    ) {
        if (state.particlesEnabled) {
            WeatherParticles(
                weatherCode = data.current.weatherCode,
                isDay = data.current.isDay,
                modifier = Modifier.matchParentSize(),
            )
        }
        CurrentConditionsHeader(
            current = data.current,
            locationName = data.location.name,
            yesterdayHigh = state.yesterdayHigh.takeIf { settings.showYesterdayComparison },
        )
    }
}

@Composable
private fun WeatherUpdatedRow(
    layout: AdaptiveLayoutInfo,
    todayPrecipChance: Int,
    updatedAgeMinutes: Long,
    updatedAgo: String,
    isCached: Boolean,
    sourceProvider: String? = null,
    usedFallback: Boolean = false,
) {
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
                NimbusStatusBadge(
                    icon = Icons.Filled.WaterDrop,
                    text = stringResource(R.string.main_rain_risk, todayPrecipChance),
                    tint = NimbusBlueAccent,
                )
            }
            val stalenessColor = when {
                isCached -> NimbusTextTertiary.copy(alpha = 0.78f)
                updatedAgeMinutes < 60 -> NimbusTextTertiary
                updatedAgeMinutes < 120 -> NimbusWarning.copy(alpha = 0.7f)
                else -> NimbusWarning
            }
            NimbusStatusBadge(
                text = if (isCached) {
                    stringResource(R.string.main_offline_ready_status, updatedAgo)
                } else {
                    stringResource(R.string.main_refreshed_status, updatedAgo)
                },
                tint = stalenessColor,
            )
            if (sourceProvider != null) {
                NimbusStatusBadge(
                    text = if (usedFallback) {
                        stringResource(R.string.main_source_fallback, sourceProvider)
                    } else {
                        stringResource(R.string.main_source_via, sourceProvider)
                    },
                    tint = if (usedFallback) NimbusWarning.copy(alpha = 0.7f) else NimbusTextTertiary,
                )
            }
        }
    }
}

internal enum class SubFetchStatusKind {
    FAILED,
    STALE,
}

internal data class SubFetchStatus(
    val kind: SubFetchStatusKind,
    val ageMinutes: Long? = null,
)

private data class CardStatusBadge(
    val text: String,
    val tint: Color,
)

internal fun subFetchStatus(
    now: LocalDateTime,
    updatedAt: LocalDateTime?,
    fetchFailed: Boolean,
    staleAfterMinutes: Long = 60,
): SubFetchStatus? {
    if (fetchFailed) return SubFetchStatus(SubFetchStatusKind.FAILED)
    val lastUpdated = updatedAt ?: return null
    val ageMinutes = Duration.between(lastUpdated, now).toMinutes().coerceAtLeast(0)
    return if (ageMinutes >= staleAfterMinutes) {
        SubFetchStatus(SubFetchStatusKind.STALE, ageMinutes)
    } else {
        null
    }
}

@Composable
private fun subFetchStatusBadge(
    updatedAt: LocalDateTime?,
    fetchFailed: Boolean,
): CardStatusBadge? {
    return when (val status = subFetchStatus(LocalDateTime.now(), updatedAt, fetchFailed)) {
        null -> null
        else -> when (status.kind) {
            SubFetchStatusKind.FAILED -> CardStatusBadge(
                text = stringResource(R.string.card_status_update_failed),
                tint = NimbusWarning,
            )
            SubFetchStatusKind.STALE -> {
                val ageMinutes = status.ageMinutes ?: 0L
                val label = if (ageMinutes >= 120) {
                    stringResource(R.string.card_status_stale_hours, ageMinutes / 60)
                } else {
                    stringResource(R.string.card_status_stale_minutes, ageMinutes)
                }
                CardStatusBadge(
                    text = label,
                    tint = NimbusWarning.copy(alpha = if (ageMinutes >= 120) 1f else 0.72f),
                )
            }
        }
    }
}

private fun LazyListScope.weatherCardItems(
    visibleCards: List<CardType>,
    cardPad: Modifier,
    cardSpacing: androidx.compose.ui.unit.Dp,
    context: CardRenderContext,
) {
    items(
        items = visibleCards,
        key = { it.name },
        contentType = { it.name },
    ) { cardType ->
        RenderCard(
            cardType = cardType,
            modifier = cardPad,
            context = context,
        )
        Spacer(modifier = Modifier.height(cardSpacing))
    }
}

private fun LazyListScope.weatherFooterItem() {
    item(key = "footer") {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.main_footer, com.sysadmindoc.nimbus.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PremiumToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassActionButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Shared Card Renderer ─────────────────────────────────────────────────

private data class CardRenderContext(
    val state: MainUiState,
    val data: WeatherData,
    val airQuality: AirQualityData?,
    val astronomy: AstronomyData?,
    val settings: NimbusSettings,
    val radarPreviewTileUrl: String?,
    val radarBaseMapUrl: String?,
    val onNavigateToRadar: (Double, Double) -> Unit,
)

private val FORECAST_CARD_TYPES = setOf(
    CardType.WEATHER_SUMMARY,
    CardType.RADAR_PREVIEW,
    CardType.NOWCAST,
    CardType.HOURLY_FORECAST,
    CardType.TEMPERATURE_GRAPH,
    CardType.FORECAST_EVOLUTION,
    CardType.DAILY_FORECAST,
)

private val ATMOSPHERE_CARD_TYPES = setOf(
    CardType.UV_INDEX,
    CardType.WIND_COMPASS,
    CardType.AIR_QUALITY,
    CardType.POLLEN,
    CardType.OUTDOOR_SCORE,
    CardType.SNOWFALL,
    CardType.SEVERE_WEATHER,
    CardType.GOLDEN_HOUR,
    CardType.SUNSHINE,
)

private val LIFESTYLE_CARD_TYPES = setOf(
    CardType.DRIVING_CONDITIONS,
    CardType.HEALTH_ALERTS,
    CardType.CLOTHING,
    CardType.PET_SAFETY,
    CardType.MOON_PHASE,
)

@Composable
private fun RenderCard(
    cardType: CardType,
    modifier: Modifier,
    context: CardRenderContext,
) {
    when (cardType) {
        in FORECAST_CARD_TYPES -> RenderForecastCard(cardType, modifier, context)
        in ATMOSPHERE_CARD_TYPES -> RenderAtmosphereCard(cardType, modifier, context)
        in LIFESTYLE_CARD_TYPES -> RenderLifestyleCard(cardType, modifier, context)
        else -> RenderDetailCard(cardType, modifier, context)
    }
}

@Composable
private fun RenderForecastCard(
    cardType: CardType,
    modifier: Modifier,
    context: CardRenderContext,
) {
    val data = context.data
    val referenceTime = data.current.observationTime
    val referenceDate = referenceTime?.toLocalDate() ?: data.daily.firstOrNull()?.date
    val isDeepLinkTarget = cardType == mainDeepLinkCardTarget(LocalMainDeepLinkTarget.current)
    when (cardType) {
        CardType.WEATHER_SUMMARY -> {
            if (context.state.weatherSummary.isNotBlank()) {
                WeatherSummaryCard(summary = context.state.weatherSummary, modifier = modifier)
            }
        }
        CardType.RADAR_PREVIEW -> {
            val radarBadge = subFetchStatusBadge(
                updatedAt = context.state.radarPreviewUpdatedAt,
                fetchFailed = context.state.radarPreviewFetchFailed,
            )
            RadarPreviewCard(
                onOpenRadar = { context.onNavigateToRadar(data.location.latitude, data.location.longitude) },
                modifier = modifier,
                radarTileUrl = context.radarPreviewTileUrl,
                baseMapTileUrl = context.radarBaseMapUrl,
                statusLabel = radarBadge?.text,
                statusTint = radarBadge?.tint,
            )
        }
        CardType.NOWCAST -> {
            if (context.state.nowcastData.isNotEmpty()) {
                NowcastCard(
                    data = context.state.nowcastData,
                    referenceTime = referenceTime,
                    modifier = modifier,
                )
            } else if (isDeepLinkTarget) {
                InlineNoticeCard(
                    title = stringResource(R.string.main_nowcast_unavailable_title),
                    message = stringResource(R.string.main_nowcast_unavailable_message),
                    icon = Icons.Filled.WaterDrop,
                    tint = NimbusBlueAccent,
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
        CardType.FORECAST_EVOLUTION -> {
            when {
                context.state.forecastEvolution != null -> ForecastEvolutionCard(
                    data = context.state.forecastEvolution,
                    modifier = modifier,
                )
                context.state.isForecastEvolutionLoading -> InlineNoticeCard(
                    title = stringResource(R.string.forecast_evolution_loading_title),
                    message = stringResource(R.string.forecast_evolution_loading_message),
                    icon = Icons.Filled.Refresh,
                    tint = NimbusBlueAccent,
                    modifier = modifier,
                )
                context.state.forecastEvolutionUnavailable -> InlineNoticeCard(
                    title = stringResource(R.string.forecast_evolution_unavailable_title),
                    message = stringResource(R.string.forecast_evolution_unavailable_message),
                    icon = Icons.Filled.CloudOff,
                    tint = NimbusTextSecondary,
                    modifier = modifier,
                )
            }
        }
        CardType.DAILY_FORECAST -> DailyForecastList(
            daily = data.daily,
            referenceDate = referenceDate,
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
private fun RenderAtmosphereCard(
    cardType: CardType,
    modifier: Modifier,
    context: CardRenderContext,
) {
    val data = context.data
    val referenceTime = data.current.observationTime
    val airQualityBadge = subFetchStatusBadge(
        updatedAt = context.state.airQualityUpdatedAt,
        fetchFailed = context.state.airQualityFetchFailed,
    )
    when (cardType) {
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
        CardType.AIR_QUALITY -> {
            context.airQuality?.let { aq ->
                AqiCard(
                    data = aq,
                    modifier = modifier,
                    statusLabel = airQualityBadge?.text,
                    statusTint = airQualityBadge?.tint ?: NimbusTextSecondary,
                )
            } ?: if (context.state.airQualityFetchFailed) {
                InlineNoticeCard(
                    title = stringResource(R.string.main_air_quality_unavailable_title),
                    message = stringResource(R.string.main_air_quality_unavailable_message),
                    icon = Icons.Filled.CloudOff,
                    tint = NimbusWarning,
                    modifier = modifier,
                )
            } else {
                Unit
            }
        }
        CardType.POLLEN -> {
            context.airQuality?.let { aq ->
                PollenCard(
                    pollen = aq.pollen,
                    modifier = modifier,
                    statusLabel = airQualityBadge?.text,
                    statusTint = airQualityBadge?.tint ?: NimbusTextSecondary,
                )
            } ?: if (context.state.airQualityFetchFailed) {
                InlineNoticeCard(
                    title = stringResource(R.string.main_pollen_unavailable_title),
                    message = stringResource(R.string.main_pollen_unavailable_message),
                    icon = Icons.Filled.CloudOff,
                    tint = NimbusWarning,
                    modifier = modifier,
                )
            } else {
                Unit
            }
        }
        CardType.OUTDOOR_SCORE -> {
            if (context.state.outdoorScore > 0) {
                OutdoorScoreCard(
                    score = context.state.outdoorScore,
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
            context.state.goldenHourTimes?.let { (morning, evening) ->
                GoldenHourCard(
                    morningGoldenEnd = morning,
                    eveningGoldenStart = evening,
                    sunrise = WeatherFormatter.formatTime(data.current.sunrise, context.settings),
                    sunset = WeatherFormatter.formatTime(data.current.sunset, context.settings),
                    modifier = modifier,
                )
            }
        }
        CardType.SUNSHINE -> {
            data.daily.firstOrNull()?.sunshineDuration?.let { seconds ->
                SunshineDurationCard(
                    sunshineDurationSeconds = seconds,
                    modifier = modifier,
                    // Real daylight window; null (unparseable) keeps the card's default.
                    dayLengthMinutes = WeatherFormatter.dayLengthMinutes(
                        data.current.sunrise, data.current.sunset,
                    ),
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun RenderLifestyleCard(
    cardType: CardType,
    modifier: Modifier,
    context: CardRenderContext,
) {
    val data = context.data
    val referenceTime = data.current.observationTime
    val isDeepLinkTarget = cardType == mainDeepLinkCardTarget(LocalMainDeepLinkTarget.current)
    when (cardType) {
        CardType.DRIVING_CONDITIONS -> {
            if (context.state.drivingAlerts.isNotEmpty()) {
                DrivingAlertCard(alerts = context.state.drivingAlerts, modifier = modifier)
            }
        }
        CardType.HEALTH_ALERTS -> {
            if (context.state.healthAlerts.isNotEmpty()) {
                HealthAlertCard(alerts = context.state.healthAlerts, modifier = modifier)
            } else if (isDeepLinkTarget) {
                InlineNoticeCard(
                    title = stringResource(R.string.main_no_health_trigger_title),
                    message = stringResource(R.string.main_no_health_trigger_message),
                    icon = Icons.Filled.ErrorOutline,
                    tint = NimbusTextSecondary,
                    modifier = modifier,
                )
            }
        }
        CardType.CLOTHING -> {
            if (context.state.clothingSuggestions.isNotEmpty()) {
                ClothingSuggestionCard(suggestions = context.state.clothingSuggestions, modifier = modifier)
            }
        }
        CardType.PET_SAFETY -> {
            if (context.state.petSafetyAlerts.isNotEmpty()) {
                PetSafetyCard(alerts = context.state.petSafetyAlerts, modifier = modifier)
            }
        }
        CardType.MOON_PHASE -> context.astronomy?.let { astro ->
            MoonPhaseCard(
                astronomy = astro,
                sunrise = data.current.sunrise,
                sunset = data.current.sunset,
                referenceTime = referenceTime,
                modifier = modifier,
            )
        }
        else -> Unit
    }
}

@Composable
private fun RenderDetailCard(
    cardType: CardType,
    modifier: Modifier,
    context: CardRenderContext,
) {
    val data = context.data
    val referenceTime = data.current.observationTime
    when (cardType) {
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
            data = context.state.onThisDay,
            forecastHighC = data.daily.firstOrNull()?.temperatureHigh,
            modifier = modifier,
        )
        else -> Unit
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
        NimbusScrollableSegmentRow {
            locations.forEachIndexed { index, loc ->
                val isActive = index == currentIndex
                NimbusSelectableSegment(
                    label = if (loc.isCurrentLocation) stringResource(R.string.common_my_location) else loc.name,
                    selected = isActive,
                    onClick = { onSelected(index) },
                    role = Role.Tab,
                    leadingIcon = if (loc.isCurrentLocation) Icons.Filled.MyLocation else null,
                    maxLines = 1,
                )
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
        PremiumMessageCard(
            title = title,
            message = message,
            icon = Icons.Filled.MyLocation,
            loading = true,
            badgeText = stringResource(R.string.main_current_location_slow_badge),
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
            tertiaryActionLabel = tertiaryActionLabel,
            onTertiaryAction = onTertiaryAction,
            modifier = Modifier
                .padding(28.dp)
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    icon: ImageVector = Icons.Filled.ErrorOutline,
    actionLabel: String? = null,
    actionIcon: ImageVector = Icons.Filled.Refresh,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    isLocationPermissionError: Boolean = false,
) {
    val resolvedActionLabel = actionLabel ?: stringResource(R.string.retry)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark),
        contentAlignment = Alignment.Center,
    ) {
        PremiumMessageCard(
            title = if (isLocationPermissionError) {
                stringResource(R.string.main_location_access_needed)
            } else {
                stringResource(R.string.main_forecast_unavailable)
            },
            message = message,
            icon = icon,
            primaryActionLabel = resolvedActionLabel,
            onPrimaryAction = onRetry,
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
            badgeText = if (secondaryActionLabel != null && onSecondaryAction != null) {
                stringResource(R.string.main_manual_locations_available)
            } else {
                null
            },
            modifier = Modifier
                .padding(32.dp),
            tint = if (isLocationPermissionError) NimbusBlueAccent else NimbusWarning,
        )
    }
}

private fun isLocationPermissionError(error: MainUiError?): Boolean =
    error?.kind == MainUiErrorKind.LOCATION_PERMISSION_REQUIRED ||
        error?.kind == MainUiErrorKind.LOCATION_PERMISSION_DENIED

internal fun mainDeepLinkCardTarget(target: MainDeepLinkTarget?): CardType? = when (target) {
    MainDeepLinkTarget.NOWCAST -> CardType.NOWCAST
    MainDeepLinkTarget.HEALTH -> CardType.HEALTH_ALERTS
    MainDeepLinkTarget.WEATHER_ALERTS, null -> null
}

internal fun cardsForDeepLinkTarget(
    enabledCards: List<CardType>,
    focusedCard: CardType?,
): List<CardType> {
    if (focusedCard == null || focusedCard in enabledCards) return enabledCards
    return listOf(focusedCard) + enabledCards
}

internal fun weatherContentItemIndexForDeepLinkTarget(
    target: MainDeepLinkTarget?,
    visibleCards: List<CardType>,
    hasOfflineBanner: Boolean,
    hasLocationBar: Boolean,
    hasAlertBanner: Boolean,
): Int? {
    if (target == null) return null

    var index = 0 // toolbar
    index++
    if (hasOfflineBanner) index++
    if (hasLocationBar) index++

    if (hasAlertBanner) {
        if (target == MainDeepLinkTarget.WEATHER_ALERTS) return index
        index++
    } else if (target == MainDeepLinkTarget.WEATHER_ALERTS) {
        return 0
    }

    index++ // hero
    index++ // updated row
    index++ // pre-card spacer

    val card = mainDeepLinkCardTarget(target) ?: return null
    val cardIndex = visibleCards.indexOf(card)
    return if (cardIndex >= 0) index + cardIndex else null
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
