package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class ForecastDetailSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hourlyDetailSheet_rendersUncertaintyExplanationWithAccessibilitySummary() {
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

        composeTestRule.setContentWithAccessibilityChecks {
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
            .onNodeWithText("Likely range 8\u00B0 to 14\u00B0 (6\u00B0C spread): wide band, lower confidence.")
            .assertIsDisplayed()
        composeTestRule
            .onNode(
                hasContentDescription(
                    "Forecast confidence. Likely range 8\u00B0 to 14\u00B0 (6\u00B0C spread): wide band, lower confidence. " +
                        "This is the 10th-to-90th percentile range from ensemble model runs. A wider range means the forecast is less certain.",
                ),
            )
            .assertIsDisplayed()
            .captureToImage()
    }
}
