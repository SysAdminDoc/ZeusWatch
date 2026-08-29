package com.sysadmindoc.nimbus.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * The Hourly tab lists up to 72 hours while the graph above it drew 24, so the
 * chart looked like it had run out of forecast. These pin the window the graph
 * actually renders, through the accessibility summary, which reports the hour
 * count it drew.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class TemperatureGraphWindowRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val start = LocalDateTime.of(2026, 5, 17, 0, 0)

    @Test
    fun graph_defaultsToTwentyFourHours() {
        renderGraph(hourly = hours(72), window = null)

        // The Today card must not silently widen when the Hourly setting changes.
        composeTestRule.onNode(hasContentDescription("Next 24 hours", substring = true))
            .assertIsDisplayed()
    }

    // The compose rule allows one setContent per test, so each window gets its
    // own case rather than a loop.
    @Test
    fun graph_spansTwentyFourHours() = assertWindow(24)

    @Test
    fun graph_spansFortyEightHours() = assertWindow(48)

    @Test
    fun graph_spansSeventyTwoHours() = assertWindow(72)

    @Test
    fun graph_windowIsClampedToTheDataAvailable() {
        // Asking for 72 with only 30 hours of forecast must describe 30, not
        // claim a span the provider never returned.
        renderGraph(hourly = hours(30), window = 72)

        composeTestRule.onNode(hasContentDescription("Next 30 hours", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun graph_rendersTheWideWindowUnderRtl() {
        renderGraph(hourly = hours(72), window = 72, layoutDirection = LayoutDirection.Rtl)

        composeTestRule.onNode(hasContentDescription("Next 72 hours", substring = true))
            .assertIsDisplayed()
    }

    private fun assertWindow(window: Int) {
        renderGraph(hourly = hours(72), window = window)

        composeTestRule.onNode(hasContentDescription("Next $window hours", substring = true))
            .assertIsDisplayed()
    }

    private fun renderGraph(
        hourly: List<HourlyConditions>,
        window: Int?,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalUnitSettings provides NimbusSettings(tempUnit = TempUnit.CELSIUS),
            ) {
                NimbusTheme {
                    if (window == null) {
                        TemperatureGraph(hourly = hourly, referenceTime = start)
                    } else {
                        TemperatureGraph(hourly = hourly, referenceTime = start, hours = window)
                    }
                }
            }
        }
    }

    private fun hours(count: Int): List<HourlyConditions> = (0 until count).map { offset ->
        HourlyConditions(
            time = start.plusHours(offset.toLong()),
            temperature = 10.0 + (offset % 12),
            feelsLike = null,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            precipitationProbability = 0,
            precipitation = null,
            windSpeed = null,
            windDirection = null,
            humidity = null,
            uvIndex = null,
            cloudCover = null,
            visibility = null,
        )
    }
}
