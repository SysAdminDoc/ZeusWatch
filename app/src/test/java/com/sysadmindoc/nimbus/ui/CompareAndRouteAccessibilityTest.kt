package com.sysadmindoc.nimbus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.testing.assertVisibleTouchTargetsMeetMinimum
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.screen.compare.CompareScreenActions
import com.sysadmindoc.nimbus.ui.screen.compare.CompareScreenBody
import com.sysadmindoc.nimbus.ui.screen.compare.CompareUiState
import com.sysadmindoc.nimbus.ui.screen.radar.RoutePlannerActions
import com.sysadmindoc.nimbus.ui.screen.radar.RoutePlannerError
import com.sysadmindoc.nimbus.ui.screen.radar.RoutePlannerUiState
import com.sysadmindoc.nimbus.ui.screen.radar.RouteWeatherPlannerSheet
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The last two surfaces the accessibility audit did not reach.
 *
 * Both are full of controls: the Compare screen is two location pickers and a
 * chart toggle, the route planner is two text fields, a file picker and a
 * submit button. Neither had ever been checked for a labelled control or a
 * reachable touch target.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class CompareAndRouteAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compareScreenWithTwoLocationsPassesAccessibilityGate() {
        auditSurface {
            CompareScreenBody(
                state = CompareUiState(
                    savedLocations = listOf(denver(), boulder()),
                    location1 = denver(),
                    location2 = boulder(),
                ),
                settings = NimbusSettings(),
                actions = compareActions(),
            )
        }
    }

    @Test
    fun compareScreenEmptyStatePassesAccessibilityGate() {
        auditSurface {
            CompareScreenBody(
                state = CompareUiState(),
                settings = NimbusSettings(),
                actions = compareActions(),
            )
        }
    }

    @Test
    fun compareScreenErrorStatePassesAccessibilityGate() {
        // The retry control is the one a user in trouble has to be able to hit.
        auditSurface {
            CompareScreenBody(
                state = CompareUiState(
                    savedLocations = listOf(denver(), boulder()),
                    location1 = denver(),
                    failedLocation1 = denver(),
                ),
                settings = NimbusSettings(),
                actions = compareActions(),
            )
        }
    }

    @Test
    fun routePlannerSheetPassesAccessibilityGate() {
        auditSurface {
            RouteWeatherPlannerSheet(
                state = RoutePlannerUiState(isSheetOpen = true),
                settings = NimbusSettings(),
                actions = routeActions(),
            )
        }
    }

    @Test
    fun routePlannerSheetErrorPassesAccessibilityGate() {
        auditSurface {
            RouteWeatherPlannerSheet(
                state = RoutePlannerUiState(
                    isSheetOpen = true,
                    originQuery = "Golden, CO",
                    error = RoutePlannerError.DESTINATION_REQUIRED,
                ),
                settings = NimbusSettings(),
                actions = routeActions(),
            )
        }
    }

    /**
     * Touch targets and labelling only.
     *
     * Contrast is left to the per-card audits: the sheet renders in its own
     * window and the Compare screen scrolls, so on a single frame most of both
     * is not capturable, and a coverage floor here would assert nothing.
     */
    private fun auditSurface(content: @Composable () -> Unit) {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                CompositionLocalProvider(LocalUnitSettings provides NimbusSettings()) {
                    Box(Modifier.fillMaxSize().background(NimbusNavyDark)) { content() }
                }
            }
        }

        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }

    private fun denver() = SavedLocationEntity(
        id = 1,
        name = "Denver",
        latitude = 39.7,
        longitude = -104.9,
        sortOrder = 0,
    )

    private fun boulder() = SavedLocationEntity(
        id = 2,
        name = "Boulder",
        latitude = 40.0,
        longitude = -105.3,
        sortOrder = 1,
    )

    private fun compareActions() = CompareScreenActions(
        onBack = {},
        onNavigateToLocations = {},
        onRetry = {},
        onSelectLocation1 = {},
        onSelectLocation2 = {},
        onChartOverlayVisible = {},
        onChartOverlayRetry = {},
    )

    private fun routeActions() = RoutePlannerActions(
        onOriginChange = {},
        onDestinationChange = {},
        onDepartureOffsetChange = {},
        onGpxImported = {},
        onGpxImportFailed = {},
        onClearGpx = {},
        onPlanRoute = {},
        onDismiss = {},
    )
}
