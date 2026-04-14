package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.sysadmindoc.nimbus.wear.WearWeatherViewModel

object WearRoutes {
    const val CURRENT = "current"
    const val HOURLY = "hourly"
}

@Composable
fun WearNavHost(
    viewModel: WearWeatherViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.CURRENT,
    ) {
        composable(WearRoutes.CURRENT) {
            CurrentScreen(
                state = state,
                onHourlyTap = { navController.navigate(WearRoutes.HOURLY) },
                onRefresh = { viewModel.loadWeather() },
            )
        }
        composable(WearRoutes.HOURLY) {
            HourlyScreen(hourly = state.hourly)
        }
    }
}
