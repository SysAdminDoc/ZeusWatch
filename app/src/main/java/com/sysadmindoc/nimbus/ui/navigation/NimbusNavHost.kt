package com.sysadmindoc.nimbus.ui.navigation

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sysadmindoc.nimbus.ui.screen.compare.CompareScreen
import com.sysadmindoc.nimbus.ui.screen.customalerts.CustomAlertsScreen
import com.sysadmindoc.nimbus.ui.screen.locations.LocationsScreen
import com.sysadmindoc.nimbus.ui.screen.main.MainScreen
import com.sysadmindoc.nimbus.ui.screen.radar.RadarScreen
import com.sysadmindoc.nimbus.ui.screen.settings.SettingsScreen
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusNavSurface
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

object Routes {
    const val MAIN = "main"
    const val MAIN_LOCATION = "main/{locationId}"
    const val SETTINGS = "settings"
    const val RADAR = "radar/{lat}/{lon}"
    const val LOCATIONS = "locations"
    const val COMPARE = "compare"
    const val CUSTOM_ALERTS = "custom_alerts"

    fun radar(lat: Double, lon: Double): String = "radar/$lat/$lon"
    fun mainWithLocation(id: Long): String = "main/$id"
}

enum class BottomTab(
    val label: String,
    val icon: ImageVector,
) {
    TODAY("Today", Icons.Filled.WbSunny),
    HOURLY("Hourly", Icons.Filled.Schedule),
    DAILY("Daily", Icons.Filled.CalendarMonth),
    RADAR("Radar", Icons.Filled.Map),
}

@Composable
fun NimbusNavHost(
    startRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // Navigate to deep-linked route on startup or re-intent
    LaunchedEffect(startRoute) {
        if (startRoute != null && startRoute != Routes.MAIN) {
            navController.navigate(startRoute) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.MAIN) {
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
            ),
        ) { backStack ->
            val lat = backStack.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lon = backStack.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0
            RadarScreen(
                latitude = lat,
                longitude = lon,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.LOCATIONS) {
            LocationsScreen(
                onBack = { navController.popBackStack() },
                onLocationSelected = { id ->
                    navController.navigate(Routes.mainWithLocation(id)) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
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
fun ZeusWatchBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: List<BottomTab> = BottomTab.entries,
) {
    val dockShape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
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
                val tabShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(tabShape)
                        .background(
                            if (isSelected) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NimbusBlueAccent.copy(alpha = 0.20f),
                                        Color.White.copy(alpha = 0.08f),
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.03f),
                                        Color.Transparent,
                                    ),
                                )
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NimbusBlueAccent.copy(alpha = 0.38f) else Color.Transparent,
                            shape = tabShape,
                        )
                        .selectable(
                            selected = isSelected,
                            onClick = { onTabSelected(index) },
                            role = Role.Tab,
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) Color.White else NimbusTextTertiary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isSelected) Color.White else NimbusTextTertiary,
                    )
                }
            }
        }
    }
}
