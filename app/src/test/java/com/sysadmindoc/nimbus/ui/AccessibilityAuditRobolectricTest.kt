package com.sysadmindoc.nimbus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import com.sysadmindoc.nimbus.testing.assertHasMergedDescription
import com.sysadmindoc.nimbus.testing.assertMeasuredAtLeast
import com.sysadmindoc.nimbus.testing.assertTextContrastMeetsMinimum
import com.sysadmindoc.nimbus.testing.assertVisibleTouchTargetsMeetMinimum
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.component.OnThisDayCard
import com.sysadmindoc.nimbus.ui.component.ProviderAgreementCard
import com.sysadmindoc.nimbus.ui.component.PwsObservationCard
import com.sysadmindoc.nimbus.ui.screen.locations.LocationsContent
import com.sysadmindoc.nimbus.ui.screen.locations.SearchState
import com.sysadmindoc.nimbus.ui.screen.main.MainUiState
import com.sysadmindoc.nimbus.ui.screen.main.TodayContent
import com.sysadmindoc.nimbus.ui.screen.settings.SettingsContent
import com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import com.sysadmindoc.nimbus.ui.theme.WeatherThemeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ported from the instrumented suite, which fails tree-wide with "No compose
 * hierarchies found" on the local device harness. Robolectric runs the same
 * assertions on the JVM so the accessibility gate has something that runs.
 *
 * Explicit qualifiers matter: without them Robolectric gives the window no
 * size and every assertIsDisplayed fails on a node it can otherwise find.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class AccessibilityAuditRobolectricTest {

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

    @Test
    fun settingsScreenPassesAccessibilityGateAtOnePointEightFontScale() =
        auditSettings(fontScale = 1.8f)

    @Test
    fun locationsScreenPassesAccessibilityGateAtOnePointEightFontScale() =
        auditLocations(fontScale = 1.8f)

    @Test
    fun licensesScreenPassesAccessibilityGate() {
        // Added with the open-source notices screen; its rows are the smallest
        // tappable targets in the app. The view model is built here rather than
        // inside the composable: lint rejects that, and a recomposition would
        // otherwise rebuild it and reload the asset.
        val viewModel = com.sysadmindoc.nimbus.ui.screen.licenses.LicensesViewModel(
            com.sysadmindoc.nimbus.data.repository.OssNoticesRepository(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ),
        )

        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                com.sysadmindoc.nimbus.ui.screen.licenses.LicensesScreen(
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }

    private fun auditSettings(fontScale: Float) {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                WithFontScale(fontScale) {
                    SettingsContent(
                        settings = com.sysadmindoc.nimbus.data.repository.NimbusSettings(),
                        onBack = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }

    private fun auditLocations(fontScale: Float) {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                WithFontScale(fontScale) {
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
        }

        composeTestRule.onNodeWithText("Locations").assertIsDisplayed()
        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
    }

    @Test
    fun pwsObservationCardPassesAccessibilityGate() {
        auditCard { PwsObservationCard(observation = auditPwsObservation()) }
    }

    @Test
    fun providerAgreementCardPassesAccessibilityGate() {
        auditCard { ProviderAgreementCard(data = auditProviderAgreement()) }
    }

    @Test
    fun onThisDayCardPassesAccessibilityGate() {
        auditCard {
            OnThisDayCard(
                data = auditOnThisDay(),
                forecastHighC = 26.7,
                onDateSelected = {},
            )
        }
    }

    @Test
    fun pwsObservationCardPassesAtOnePointEightFontScale() {
        auditCard(fontScale = 1.8f) { PwsObservationCard(observation = auditPwsObservation()) }
    }

    @Test
    fun providerAgreementCardPassesAtOnePointEightFontScale() {
        auditCard(fontScale = 1.8f) { ProviderAgreementCard(data = auditProviderAgreement()) }
    }

    @Test
    fun onThisDayCardPassesAtOnePointEightFontScale() {
        auditCard(fontScale = 1.8f) {
            OnThisDayCard(
                data = auditOnThisDay(),
                forecastHighC = 26.7,
                onDateSelected = {},
            )
        }
    }

    /**
     * Audits one card on its own.
     *
     * These three were added to the whole-screen fixture, but TodayContent
     * renders through a LazyColumn, which never composes what is below the
     * fold: none of their text reached the semantics tree, so the fixture
     * addition audited nothing. Rendered alone they fit on screen, so their
     * contrast is measured rather than skipped as uncapturable.
     */
    private fun auditCard(
        fontScale: Float = 1f,
        card: @Composable () -> Unit,
    ) {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                CompositionLocalProvider(
                    LocalUnitSettings provides com.sysadmindoc.nimbus.data.repository.NimbusSettings(),
                    LocalWeatherThemeState provides WeatherThemeState(
                        weatherCode = WeatherCode.CLEAR_SKY,
                        isDay = true,
                    ),
                ) {
                    // The app background, not the default window grey: these
                    // cards have translucent glass fills, so on a bare window
                    // they composite to a mid-grey nothing in the app renders
                    // and every header reads as a contrast failure.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NimbusNavyDark),
                    ) {
                        WithFontScale(fontScale) { card() }
                    }
                }
            }
        }

        composeTestRule.assertVisibleTouchTargetsMeetMinimum()
        // A card is not clickable, so the labelling check inside
        // setContentWithAccessibilityChecks says nothing about it: all three
        // of these passed with their merged descriptions removed.
        composeTestRule.assertHasMergedDescription()
        composeTestRule.assertTextContrastMeetsMinimum().assertMeasuredAtLeast(1.0)
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

        // Says out loud how much of this screen the contrast check could
        // actually look at. Most of it is below the fold in one frame and
        // cannot be captured, so the honest floor is low; the point is that it
        // cannot quietly get lower. Touch targets and semantics above still
        // cover the whole tree, which is composed in full.
        composeTestRule.assertTextContrastMeetsMinimum().assertMeasuredAtLeast(0.20)
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
                // The three newest cards were absent from this fixture, so
                // their touch targets and semantics were never audited.
                pwsObservation = auditPwsObservation(),
                providerAgreement = auditProviderAgreement(),
                onThisDay = auditOnThisDay(),
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

private fun auditPwsObservation() = com.sysadmindoc.nimbus.data.repository.PwsObservation(
    observedAt = java.time.LocalDateTime.of(2026, 5, 17, 9, 0),
    temperatureC = 18.0,
    humidityPercent = 55,
    windSpeedKmh = 12.0,
    windGustKmh = 20.0,
    windDirectionDegrees = 220,
    pressureHpa = 1012.0,
    uvIndex = 4.0,
    rainLastMinuteMm = 0.2,
    precipitationType = com.sysadmindoc.nimbus.data.repository.TempestPrecipitationType.RAIN,
    lightningStrikeCount = 3,
    lightningStrikeAverageDistanceKm = 8.0,
    reportIntervalMinutes = 1,
)

private fun auditProviderAgreement() = com.sysadmindoc.nimbus.data.repository.ProviderAgreementData(
    agreement = com.sysadmindoc.nimbus.data.repository.ProviderAgreementLevel.MODERATE,
    providers = listOf(
        com.sysadmindoc.nimbus.data.repository.ProviderAgreementSnapshot(
            provider = com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider.OPEN_METEO,
            displayName = "Open-Meteo",
            averageTemperatureC = 18.0,
            precipitationTotalMm = 1.2,
            hourCount = 24,
        ),
        com.sysadmindoc.nimbus.data.repository.ProviderAgreementSnapshot(
            provider = com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider.MET_NORWAY,
            displayName = "MET Norway",
            averageTemperatureC = 19.4,
            precipitationTotalMm = 2.6,
            hourCount = 24,
        ),
    ),
    temperatureSpreadC = 1.4,
    precipitationSpreadMm = 1.4,
)

private fun auditOnThisDay() = com.sysadmindoc.nimbus.data.model.OnThisDayData(
    priorYears = (1..5).map { offset ->
        com.sysadmindoc.nimbus.data.model.PriorYearEntry(
            year = 2025 - offset,
            highC = 20.0 + offset,
            lowC = 8.0 + offset,
            precipMm = offset.toDouble(),
        )
    },
    averageHighC = 23.0,
    averageLowC = 11.0,
    recordHighC = 25.0,
    recordLowC = 9.0,
)
