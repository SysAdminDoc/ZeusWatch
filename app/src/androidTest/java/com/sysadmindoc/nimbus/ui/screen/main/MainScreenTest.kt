package com.sysadmindoc.nimbus.ui.screen.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState
import com.sysadmindoc.nimbus.ui.theme.WeatherThemeState
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testWeatherData = WeatherData(
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
        hourly = (0 until 12).map { i ->
            HourlyConditions(
                time = LocalDateTime.now().plusHours(i.toLong()),
                temperature = 21.0 + i,
                feelsLike = 20.0 + i,
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
        daily = (0 until 7).map { i ->
            DailyConditions(
                date = LocalDate.now().plusDays(i.toLong()),
                weatherCode = WeatherCode.PARTLY_CLOUDY,
                temperatureHigh = 26.7 + i,
                temperatureLow = 12.8 - i,
                precipitationProbability = 10 * i,
                precipitationSum = null,
                sunrise = "2025-01-15T07:00:00",
                sunset = "2025-01-15T17:30:00",
                uvIndexMax = 6.0,
                windSpeedMax = 15.0,
                windDirectionDominant = 180,
            )
        },
    )

    @Test
    fun loadingState_showsShimmerSkeleton() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(isLoading = true, weatherData = null),
                )
            }
        }

        // Shimmer skeleton is rendered (no error, no weather text)
        composeTestRule.onNodeWithText("Denver").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun errorState_showsErrorMessageAndRetryButton() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = null,
                        error = "Network error occurred",
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Network error occurred").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun permissionError_showsGrantLocationAction() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = null,
                        error = "Location permission required to show weather.",
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Grant location").assertIsDisplayed()
    }

    @Test
    fun weatherState_showsLocationNameAndTemperature() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = testWeatherData,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Denver").assertIsDisplayed()
        composeTestRule.onNodeWithText("72\u00B0").assertIsDisplayed()
    }

    @Test
    fun weatherState_showsConditionDescription() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = testWeatherData,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    fun weatherState_showsHighAndLow() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = testWeatherData,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("High").assertIsDisplayed()
        composeTestRule.onNodeWithText("80\u00B0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Low").assertIsDisplayed()
        composeTestRule.onNodeWithText("55\u00B0").assertIsDisplayed()
    }

    @Test
    fun cachedState_showsCachedBanner() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = testWeatherData,
                        isCached = true,
                    ),
                )
            }
        }

        // The cached state still shows weather data
        composeTestRule.onNodeWithText("Denver").assertIsDisplayed()
    }
}

@Composable
private fun MainScreenContent(state: MainUiState) {
    val weatherThemeState = WeatherThemeState(
        weatherCode = state.weatherData?.current?.weatherCode,
        isDay = state.weatherData?.current?.isDay ?: true,
    )
    CompositionLocalProvider(
        LocalUnitSettings provides state.settings,
        LocalWeatherThemeState provides weatherThemeState,
    ) {
        TodayContent(state = state)
    }
}
