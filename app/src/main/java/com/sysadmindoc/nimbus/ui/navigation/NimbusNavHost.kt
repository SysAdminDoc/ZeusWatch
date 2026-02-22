package com.sysadmindoc.nimbus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sysadmindoc.nimbus.ui.screen.locations.LocationsScreen
import com.sysadmindoc.nimbus.ui.screen.main.MainScreen
import com.sysadmindoc.nimbus.ui.screen.radar.RadarScreen
import com.sysadmindoc.nimbus.ui.screen.settings.SettingsScreen
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

object Routes {
    const val MAIN = "main"
    const val MAIN_LOCATION = "main/{locationId}"
    const val SETTINGS = "settings"
    const val RADAR = "radar/{lat}/{lon}"
    const val LOCATIONS = "locations"

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
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.RADAR,
            arguments = listOf(
                navArgument("lat") { type = NavType.FloatType },
                navArgument("lon") { type = NavType.FloatType },
            ),
        ) { backStack ->
            val lat = backStack.arguments?.getFloat("lat")?.toDouble() ?: 0.0
            val lon = backStack.arguments?.getFloat("lon")?.toDouble() ?: 0.0
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
    }
}

/**
 * Bottom navigation bar matching TWC app style.
 */
@Composable
fun ZeusWatchBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color(0xFF0A0E1A),
        tonalElevation = 0.dp,
    ) {
        BottomTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NimbusBlueAccent,
                    selectedTextColor = NimbusBlueAccent,
                    unselectedIconColor = NimbusTextTertiary,
                    unselectedTextColor = NimbusTextTertiary,
                    indicatorColor = NimbusBlueAccent.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
