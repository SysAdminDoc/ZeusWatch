package com.sysadmindoc.nimbus.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.screen.compare.CompareScreen
import com.sysadmindoc.nimbus.ui.screen.customalerts.CustomAlertsScreen
import com.sysadmindoc.nimbus.ui.screen.locations.LocationsScreen
import com.sysadmindoc.nimbus.ui.screen.locations.LocationsViewModel
import com.sysadmindoc.nimbus.ui.screen.locations.MapLocationPickerScreen
import com.sysadmindoc.nimbus.ui.screen.main.MainScreen
import com.sysadmindoc.nimbus.ui.screen.onboarding.OnboardingScreen
import com.sysadmindoc.nimbus.ui.screen.onboarding.OnboardingViewModel
import com.sysadmindoc.nimbus.ui.screen.radar.RadarScreen
import com.sysadmindoc.nimbus.ui.screen.settings.SettingsScreen
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusNavSurface
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import java.net.URLEncoder

object Routes {
    const val ONBOARDING_GATE = "onboarding_gate"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val MAIN_LOCATION = "main/{locationId}"
    const val MAIN_TARGET = "main/target/{target}"
    const val SETTINGS = "settings"
    const val RADAR = "radar/{lat}/{lon}?route={route}"
    const val LOCATIONS = "locations"
    const val LOCATION_PICKER = "location_picker"
    const val COMPARE = "compare"
    const val CUSTOM_ALERTS = "custom_alerts"

    fun radar(lat: Double, lon: Double, routeText: String? = null): String {
        val base = "radar/$lat/$lon"
        val route = routeText?.trim()?.takeIf { it.isNotBlank() } ?: return base
        return "$base?route=${encodeRouteArg(route)}"
    }
    fun mainWithLocation(id: Long): String = "main/$id"
    fun mainTarget(target: MainDeepLinkTarget): String = "main/target/${target.routeValue}"
}

private fun encodeRouteArg(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

enum class MainDeepLinkTarget(val routeValue: String) {
    WEATHER_ALERTS("weather_alerts"),
    NOWCAST("nowcast"),
    HEALTH("health"),
    ;

    companion object {
        fun fromRouteValue(value: String?): MainDeepLinkTarget? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.routeValue == normalized } ?: when (normalized) {
                "alerts", "alert", "severe", "severe_weather" -> WEATHER_ALERTS
                "rain", "rain_next_hour" -> NOWCAST
                "health_alerts", "migraine", "respiratory", "arthritis" -> HEALTH
                else -> null
            }
        }
    }
}

val LocalMainDeepLinkTarget = staticCompositionLocalOf<MainDeepLinkTarget?> { null }

internal fun resolveZeusWatchDeepLinkRoute(
    host: String?,
    target: String? = null,
    card: String? = null,
    locationId: String? = null,
    routeText: String? = null,
): String? {
    val parsedLocationId = locationId?.toLongOrNull()?.takeIf { it > 0L }
    return when (host?.lowercase()) {
        "locations" -> Routes.LOCATIONS
        "settings" -> Routes.SETTINGS
        "radar" -> Routes.radar(0.0, 0.0, routeText)
        "compare" -> Routes.COMPARE
        "custom_alerts" -> Routes.CUSTOM_ALERTS
        "alerts", "weather_alerts" -> Routes.mainTarget(MainDeepLinkTarget.WEATHER_ALERTS)
        "nowcast" -> Routes.mainTarget(MainDeepLinkTarget.NOWCAST)
        "health", "health_alerts" -> Routes.mainTarget(MainDeepLinkTarget.HEALTH)
        "main" -> parsedLocationId?.let(Routes::mainWithLocation)
            ?: MainDeepLinkTarget.fromRouteValue(target ?: card)?.let(Routes::mainTarget)
        else -> null
    }
}

enum class BottomTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY(R.string.nav_today, Icons.Filled.WbSunny),
    HOURLY(R.string.nav_hourly, Icons.Filled.Schedule),
    DAILY(R.string.nav_daily, Icons.Filled.CalendarMonth),
    RADAR(R.string.nav_radar, Icons.Filled.Map),
}

/**
 * One-shot deep-link delivery. The monotonic [id] makes each notification tap a
 * distinct event, so re-tapping the same target (or two notifications for the
 * same route) still triggers navigation — keying the effect on the route string
 * alone silently dropped repeat deliveries.
 */
data class DeepLinkRequest(val route: String, val id: Long)

@Composable
fun NimbusNavHost(
    deepLink: DeepLinkRequest? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val onboardingComplete by onboardingViewModel.onboardingComplete.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(onboardingComplete) {
        when (onboardingComplete) {
            true -> navController.navigate(Routes.MAIN) {
                popUpTo(Routes.ONBOARDING_GATE) { inclusive = true }
                launchSingleTop = true
            }
            false -> navController.navigate(Routes.ONBOARDING) {
                popUpTo(Routes.ONBOARDING_GATE)
                launchSingleTop = true
            }
            null -> Unit
        }
    }

    // Navigate to deep-linked route on startup or re-intent after onboarding is
    // resolved. Keyed on the delivery id (not the route) so repeat taps re-fire.
    LaunchedEffect(deepLink?.id, onboardingComplete) {
        if (onboardingComplete != true) return@LaunchedEffect
        val route = deepLink?.route
        if (route != null && route != Routes.MAIN) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        if (deepLink != null) onDeepLinkConsumed()
    }

    NavHost(navController = navController, startDestination = Routes.ONBOARDING_GATE) {
        composable(Routes.ONBOARDING_GATE) {
            OnboardingGateScreen()
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING_GATE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToRadar = { lat, lon ->
                    navController.navigate(Routes.radar(lat, lon))
                },
                onNavigateToLocations = { navController.navigate(Routes.LOCATIONS) },
                onNavigateToCompare = { navController.navigate(Routes.COMPARE) },
            )
        }
        composable(
            route = Routes.MAIN_LOCATION,
            arguments = listOf(navArgument("locationId") { type = NavType.LongType }),
        ) { backStack ->
            MainScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToRadar = { lat, lon ->
                    navController.navigate(Routes.radar(lat, lon))
                },
                onNavigateToLocations = { navController.navigate(Routes.LOCATIONS) },
                onNavigateToCompare = { navController.navigate(Routes.COMPARE) },
            )
        }
        composable(
            route = Routes.MAIN_TARGET,
            arguments = listOf(navArgument("target") { type = NavType.StringType }),
        ) { backStack ->
            val target = MainDeepLinkTarget.fromRouteValue(backStack.arguments?.getString("target"))
            CompositionLocalProvider(LocalMainDeepLinkTarget provides target) {
                MainScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToRadar = { lat, lon ->
                        navController.navigate(Routes.radar(lat, lon))
                    },
                    onNavigateToLocations = { navController.navigate(Routes.LOCATIONS) },
                    onNavigateToCompare = { navController.navigate(Routes.COMPARE) },
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCustomAlerts = { navController.navigate(Routes.CUSTOM_ALERTS) },
            )
        }
        composable(Routes.CUSTOM_ALERTS) {
            CustomAlertsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.RADAR,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; defaultValue = "0.0" },
                navArgument("lon") { type = NavType.StringType; defaultValue = "0.0" },
                navArgument("route") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val lat = backStack.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lon = backStack.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0
            val routeText = backStack.arguments?.getString("route")
            RadarScreen(
                latitude = lat,
                longitude = lon,
                onBack = { navController.popBackStack() },
                sharedRouteText = routeText,
            )
        }
        composable(Routes.LOCATIONS) {
            LocationsScreen(
                onBack = { navController.popBackStack() },
                onLocationSelected = { id ->
                    navController.navigate(Routes.mainWithLocation(id)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onNavigateToMapPicker = { navController.navigate(Routes.LOCATION_PICKER) },
            )
        }
        composable(Routes.LOCATION_PICKER) {
            val locationsViewModel: LocationsViewModel = hiltViewModel()
            MapLocationPickerScreen(
                onLocationPicked = { lat, lon, name ->
                    locationsViewModel.addMapPickedLocation(lat, lon, name) { id ->
                        navController.navigate(Routes.mainWithLocation(id)) {
                            popUpTo(Routes.MAIN)
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.COMPARE) {
            CompareScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLocations = { navController.navigate(Routes.LOCATIONS) },
            )
        }
    }
}

@Composable
private fun OnboardingGateScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = NimbusBlueAccent,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
fun ZeusWatchBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: List<BottomTab> = BottomTab.entries,
) {
    val dockShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassBottom.copy(alpha = 1f),
                        NimbusNavyDark,
                    ),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .shadow(18.dp, dockShape)
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusNavSurface.copy(alpha = 0.98f),
                            NimbusGlassBottom.copy(alpha = 0.96f),
                        ),
                    ),
                )
                .border(1.dp, NimbusCardBorder, dockShape)
                .selectableGroup()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleTabs.forEach { tab ->
                val index = tab.ordinal
                val isSelected = selectedTab == index
                val tabLabel = stringResource(tab.labelRes)
                val tabClickLabel = stringResource(R.string.nav_show_tab, tabLabel)
                val selectedLabel = stringResource(R.string.common_selected)
                val notSelectedLabel = stringResource(R.string.common_not_selected)
                val tabShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                val indicatorWidth by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 8.dp,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "bottomNavIndicatorWidth",
                )
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White.copy(alpha = 0.95f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "bottomNavIndicatorColor",
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else NimbusTextTertiary,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    label = "bottomNavIconColor",
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else NimbusTextTertiary,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    label = "bottomNavLabelColor",
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) NimbusBlueAccent.copy(alpha = 0.38f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    label = "bottomNavBorderColor",
                )
                val containerTop by animateColorAsState(
                    targetValue = if (isSelected) NimbusBlueAccent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.03f),
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    label = "bottomNavContainerTop",
                )
                val containerBottom by animateColorAsState(
                    targetValue = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    label = "bottomNavContainerBottom",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(tabShape)
                        .background(
                            Brush.verticalGradient(colors = listOf(containerTop, containerBottom)),
                        )
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = tabShape,
                        )
                        .selectable(
                            selected = isSelected,
                            onClick = { onTabSelected(index) },
                            role = Role.Tab,
                        )
                        .clearAndSetSemantics {
                            contentDescription = tabLabel
                            selected = isSelected
                            stateDescription = if (isSelected) selectedLabel else notSelectedLabel
                            role = Role.Tab
                            onClick(label = tabClickLabel) {
                                onTabSelected(index)
                                true
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .widthIn(min = indicatorWidth, max = indicatorWidth)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .background(indicatorColor),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconColor,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tabLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
