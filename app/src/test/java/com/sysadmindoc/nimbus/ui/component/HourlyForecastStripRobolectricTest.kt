package com.sysadmindoc.nimbus.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import com.sysadmindoc.nimbus.util.WeatherFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * JVM Compose UI test for the hourly detail panel's selection lifecycle:
 * the open panel must not survive a payload swap (location change/refresh),
 * which previously left it showing the prior city's stale hour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class HourlyForecastStripRobolectricTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val settings = NimbusSettings(tempUnit = TempUnit.CELSIUS)

    private fun hourAt(time: LocalDateTime, tempC: Double) = HourlyConditions(
        time = time,
        temperature = tempC,
        feelsLike = null,
        weatherCode = WeatherCode.PARTLY_CLOUDY,
        isDay = true,
        precipitationProbability = 10,
        precipitation = null,
        windSpeed = null,
        windDirection = null,
        humidity = null,
        uvIndex = null,
        cloudCover = null,
        visibility = null,
    )

    @Test
    fun detailPanel_closesWhenHourlyPayloadIsReplaced() {
        val cityAStart = LocalDateTime.of(2026, 1, 15, 9, 0)
        val cityBStart = LocalDateTime.of(2026, 1, 16, 14, 0)
        val cityAHours = (0..5).map { hourAt(cityAStart.plusHours(it.toLong()), tempC = 10.0 + it) }
        val cityBHours = (0..5).map { hourAt(cityBStart.plusHours(it.toLong()), tempC = 20.0 + it) }

        var hourly by mutableStateOf(cityAHours)
        composeTestRule.setContent {
            NimbusTheme {
                CompositionLocalProvider(LocalUnitSettings provides settings) {
                    HourlyForecastStrip(hourly = hourly)
                }
            }
        }

        // Open the detail panel for the second hour of city A.
        val context = RuntimeEnvironment.getApplication()
        val secondHourTemp = WeatherFormatter.formatTemperature(cityAHours[1].temperature, settings)
        composeTestRule.onNodeWithText(secondHourTemp).performClick()

        val expectedTitle = context.getString(
            R.string.forecast_detail_hourly_title,
            WeatherFormatter.formatRelativeHourLabel(context, cityAHours[1].time, cityAHours[0].time, settings),
        )
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

        // Swap the payload (location change) — the panel must not keep showing
        // the previous payload's hour.
        composeTestRule.runOnIdle { hourly = cityBHours }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(expectedTitle).assertCountEquals(0)
    }
}
