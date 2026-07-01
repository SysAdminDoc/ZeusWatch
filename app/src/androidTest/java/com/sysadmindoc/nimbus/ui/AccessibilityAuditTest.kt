package com.sysadmindoc.nimbus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.testing.assertVisibleTouchTargetsMeetMinimum
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.screen.locations.LocationsContent
import com.sysadmindoc.nimbus.ui.screen.locations.SearchState
import com.sysadmindoc.nimbus.ui.screen.main.MainUiState
import com.sysadmindoc.nimbus.ui.screen.main.TodayContent
import com.sysadmindoc.nimbus.ui.screen.settings.SettingsContent
import com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import com.sysadmindoc.nimbus.ui.theme.WeatherThemeState
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AccessibilityAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainWeatherScreenPassesAccessibilityGateAtOnePointThreeFontScale() {
        setMainWeatherAuditContent(fontScale = 1.3f)
    }

    @Test
    fun mainWeatherScreenPassesAccessibilityGateAtOnePointFiveFontScale() {
        setMainWeatherAuditContent(fontScale = 1.5f)
    }

    @Test
    fun mainWeatherScreenPassesAccessibilityGateAtOnePointEightFontScale() {
        setMainWeatherAuditContent(fontScale = 1.8f)
    }

    @Test
    fun settingsScreenPassesAccessibilityGate() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = com.sysadmindoc.nimbus.data.repository.NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }

    @Test
    fun locationsScreenPassesAccessibilityGate() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                LocationsContent(
                    saved = auditSavedLocations(),
                    search = SearchState(
                        query = "San",
                        results = auditSearchResults(),
                        isSearching = false,
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Locations").assertIsDisplayed()
        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }

    private fun setMainWeatherAuditContent(fontScale: Float) {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                WithFontScale(fontScale) {
                    MainWeatherAuditContent()
                }
            }
        }

        composeTestRule.onNodeWithText("Denver").assertIsDisplayed()
        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }
}

@Composable
private fun WithFontScale(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val current = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(current.density, fontScale),
        content = content,
    )
}

@Composable
private fun MainWeatherAuditContent() {
    val weatherData = auditWeatherData()
    CompositionLocalProvider(
        LocalUnitSettings provides com.sysadmindoc.nimbus.data.repository.NimbusSettings(),
        LocalWeatherThemeState provides WeatherThemeState(
            weatherCode = weatherData.current.weatherCode,
            isDay = weatherData.current.isDay,
        ),
    ) {
        TodayContent(
            state = MainUiState(
                isLoading = false,
                weatherData = weatherData,
            ),
        )
    }
}

private fun auditWeatherData(): WeatherData = WeatherData(
    location = LocationInfo("Denver", "Colorado", "US", 39.7, -104.9),
    current = CurrentConditions(
        temperature = 22.2,
        feelsLike = 21.1,
        humidity = 45,
        weatherCode = WeatherCode.CLEAR_SKY,
        isDay = true,
        windSpeed = 8.0,
        windDirection = 180,
        windGusts = 15.0,
        pressure = 1013.25,
        uvIndex = 5.0,
        visibility = 16000.0,
        dewPoint = 10.0,
        cloudCover = 20,
        precipitation = 0.0,
        dailyHigh = 26.7,
        dailyLow = 12.8,
        sunrise = "2025-01-15T07:00:00",
        sunset = "2025-01-15T17:30:00",
    ),
    hourly = (0 until 12).map { index ->
        HourlyConditions(
            time = LocalDateTime.now().plusHours(index.toLong()),
            temperature = 21.0 + index,
            feelsLike = 20.0 + index,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            precipitationProbability = 0,
            precipitation = null,
            windSpeed = 8.0,
            windDirection = 180,
            humidity = 45,
            uvIndex = 5.0,
            cloudCover = 20,
            visibility = 16000.0,
        )
    },
    daily = (0 until 7).map { index ->
        DailyConditions(
            date = LocalDate.now().plusDays(index.toLong()),
            weatherCode = WeatherCode.PARTLY_CLOUDY,
            temperatureHigh = 26.7 + index,
            temperatureLow = 12.8 - index,
            precipitationProbability = 10 * index,
            precipitationSum = null,
            sunrise = "2025-01-15T07:00:00",
            sunset = "2025-01-15T17:30:00",
            uvIndexMax = 6.0,
            windSpeedMax = 15.0,
            windDirectionDominant = 180,
        )
    },
)

private fun auditSavedLocations(): List<SavedLocationEntity> = listOf(
    SavedLocationEntity(
        id = 1,
        name = "Denver",
        latitude = 39.7,
        longitude = -104.9,
        region = "Colorado",
        country = "United States",
        isCurrentLocation = true,
        sortOrder = -1,
    ),
    SavedLocationEntity(
        id = 2,
        name = "New York",
        latitude = 40.7,
        longitude = -74.0,
        region = "New York",
        country = "United States",
        sortOrder = 0,
    ),
)

private fun auditSearchResults(): List<GeocodingResult> = listOf(
    GeocodingResult(
        id = 100,
        name = "San Francisco",
        latitude = 37.8,
        longitude = -122.4,
        country = "United States",
        admin1 = "California",
    ),
    GeocodingResult(
        id = 101,
        name = "San Diego",
        latitude = 32.7,
        longitude = -117.2,
        country = "United States",
        admin1 = "Southern California",
    ),
)
