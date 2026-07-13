package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandData
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandEntry
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
 * JVM Compose UI test that runs under Robolectric — no device/emulator. This is
 * the device-free replacement for the on-device instrumented harness, which
 * fails tree-wide with "No compose hierarchies found". Ported from
 * `androidTest/.../ForecastDetailSheetTest`; if this renders and finds the
 * confidence text, `createComposeRule()` registers Compose roots on the JVM.
 *
 * Uses a plain [android.app.Application] to bypass `NimbusApplication`'s Hilt /
 * WorkManager init, which this pure-composable test does not need.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ForecastDetailSheetRobolectricTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hourlyDetailSheet_rendersUncertaintyExplanationOnJvm() {
        val time = LocalDateTime.of(2026, 1, 15, 9, 0)
        val hour = HourlyConditions(
            time = time,
            temperature = 10.0,
            feelsLike = null,
            weatherCode = WeatherCode.PARTLY_CLOUDY,
            isDay = true,
            precipitationProbability = 20,
            precipitation = null,
            windSpeed = null,
            windDirection = null,
            humidity = null,
            uvIndex = null,
            cloudCover = null,
            visibility = null,
        )
        val bands = ConfidenceBandData(
            entries = listOf(
                ConfidenceBandEntry(
                    time = time,
                    temperatureMean = 11.0,
                    temperatureLower = 8.0,
                    temperatureUpper = 14.0,
                ),
            ),
        )

        composeTestRule.setContent {
            NimbusTheme {
                CompositionLocalProvider(LocalUnitSettings provides NimbusSettings(tempUnit = TempUnit.CELSIUS)) {
                    Box(Modifier.width(320.dp)) {
                        HourlyForecastDetailSheet(
                            hour = hour,
                            referenceTime = time,
                            confidenceBands = bands,
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Forecast confidence").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Likely range 8° to 14° (6°C spread): wide band, lower confidence.")
            .assertIsDisplayed()
        composeTestRule
            .onNode(
                hasContentDescription(
                    "Forecast confidence. Likely range 8° to 14° (6°C spread): wide band, lower confidence. " +
                        "This is the 10th-to-90th percentile range from ensemble model runs. A wider range means the forecast is less certain.",
                ),
            )
            .assertIsDisplayed()
    }
}
