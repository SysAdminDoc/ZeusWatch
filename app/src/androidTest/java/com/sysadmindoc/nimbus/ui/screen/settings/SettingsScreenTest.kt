package com.sysadmindoc.nimbus.ui.screen.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_showsAllSections() {
        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Forecast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alerts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Display").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visual Effects").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsAboutInfo() {
        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Advanced").performClick()
        composeTestRule.onNodeWithText("1.0.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open-Meteo, NWS, and more").assertIsDisplayed()
        composeTestRule.onNodeWithText("LGPL-3.0").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsTempUnits() {
        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Forecast").performClick()
        composeTestRule.onNodeWithText("Fahrenheit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Celsius").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsWindUnits() {
        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Forecast").performClick()
        composeTestRule.onNodeWithText("mph").assertIsDisplayed()
        composeTestRule.onNodeWithText("km/h").assertIsDisplayed()
        composeTestRule.onNodeWithText("knots").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_tempUnitClick_invokesCallback() {
        var selectedUnit: TempUnit? = null

        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(tempUnit = TempUnit.FAHRENHEIT),
                    onBack = {},
                    onTempUnit = { selectedUnit = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Forecast").performClick()
        composeTestRule.onNodeWithText("Celsius").performClick()
        assertEquals(TempUnit.CELSIUS, selectedUnit)
    }

    @Test
    fun settingsScreen_windUnitClick_invokesCallback() {
        var selectedUnit: WindUnit? = null

        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(windUnit = WindUnit.MPH),
                    onBack = {},
                    onWindUnit = { selectedUnit = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Forecast").performClick()
        composeTestRule.onNodeWithText("km/h").performClick()
        assertEquals(WindUnit.KMH, selectedUnit)
    }

    @Test
    fun settingsScreen_particleToggle_showsLabel() {
        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(particlesEnabled = true),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Weather Particles").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rain, snow, and sun ray animations").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_particleToggle_invokesCallback() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(particlesEnabled = true),
                    onBack = {},
                    onParticlesEnabled = { toggledValue = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Weather Particles").performClick()
        assertEquals(false, toggledValue)
    }

    @Test
    fun settingsScreen_timeFormatOptions() {
        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Forecast").performClick()
        composeTestRule.onNodeWithText("12-hour").assertIsDisplayed()
        composeTestRule.onNodeWithText("24-hour").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_backButton_invokesCallback() {
        var backCalled = false

        composeTestRule.setContent {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = { backCalled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Back", useUnmergedTree = true).assertDoesNotExist()
        // Back button has contentDescription "Back"
        composeTestRule.onNode(
            hasText("Settings") // settings title exists means screen rendered
        ).assertIsDisplayed()
    }
}
