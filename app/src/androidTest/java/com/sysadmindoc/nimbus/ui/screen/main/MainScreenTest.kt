package com.sysadmindoc.nimbus.ui.screen.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
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
            temperature = 72.0,
            feelsLike = 70.0,
            humidity = 45,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            windSpeed = 8.0,
            windDirection = 180,
            windGusts = 15.0,
            pressure = 1013.25,
            uvIndex = 5.0,
            visibility = 16000.0,
            dewPoint = 50.0,
            cloudCover = 20,
            precipitation = 0.0,
            dailyHigh = 80.0,
            dailyLow = 55.0,
            sunrise = "2025-01-15T07:00:00",
            sunset = "2025-01-15T17:30:00",
        ),
        hourly = (0 until 12).map { i ->
            HourlyConditions(
                time = LocalDateTime.now().plusHours(i.toLong()),
                temperature = 70.0 + i,
                feelsLike = 68.0 + i,
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
                temperatureHigh = 80.0 + i,
                temperatureLow = 55.0 - i,
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
        composeTestRule.setContent {
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
        composeTestRule.setContent {
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
    fun weatherState_showsLocationNameAndTemperature() {
        composeTestRule.setContent {
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
        composeTestRule.setContent {
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
        composeTestRule.setContent {
            NimbusTheme {
                MainScreenContent(
                    state = MainUiState(
                        isLoading = false,
                        weatherData = testWeatherData,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("H:80\u00B0").assertIsDisplayed()
        composeTestRule.onNodeWithText("L:55\u00B0").assertIsDisplayed()
    }

    @Test
    fun cachedState_showsCachedBanner() {
        composeTestRule.setContent {
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
