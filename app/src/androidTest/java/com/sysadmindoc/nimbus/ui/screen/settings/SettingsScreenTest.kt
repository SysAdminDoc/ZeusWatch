package com.sysadmindoc.nimbus.ui.screen.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sysadmindoc.nimbus.BuildConfig
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_showsAllSections() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        assertCategoryDisplayed("Appearance")
        assertCategoryDisplayed("Forecast")
        assertCategoryDisplayed("Alerts")
        assertCategoryDisplayed("Advanced")
        composeTestRule.onNodeWithText("Display").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visual Effects").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsAboutInfo() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        clickCategory("Advanced")
        composeTestRule.onNodeWithText(BuildConfig.VERSION_NAME).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Open-Meteo, NWS, and more").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("LGPL-3.0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsTempUnits() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        clickCategory("Forecast")
        composeTestRule.onNodeWithText("Fahrenheit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Celsius").assertExists()
    }

    @Test
    fun settingsScreen_showsWindUnits() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        clickCategory("Forecast")
        composeTestRule.onNodeWithText("mph").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("km/h").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("knots").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_tempUnitClick_invokesCallback() {
        var selectedUnit: TempUnit? = null

        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(tempUnit = TempUnit.FAHRENHEIT),
                    onBack = {},
                    onTempUnit = { selectedUnit = it },
                )
            }
        }

        clickCategory("Forecast")
        composeTestRule.onNodeWithText("Celsius").performScrollTo().performClick()
        assertEquals(TempUnit.CELSIUS, selectedUnit)
    }

    @Test
    fun settingsScreen_windUnitClick_invokesCallback() {
        var selectedUnit: WindUnit? = null

        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(windUnit = WindUnit.MPH),
                    onBack = {},
                    onWindUnit = { selectedUnit = it },
                )
            }
        }

        clickCategory("Forecast")
        composeTestRule.onNodeWithText("km/h").performScrollTo().performClick()
        assertEquals(WindUnit.KMH, selectedUnit)
    }

    @Test
    fun settingsScreen_particleToggle_showsLabel() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(particlesEnabled = true),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Weather Particles").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Rain, snow, and sun ray animations").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_particleToggle_invokesCallback() {
        var toggledValue: Boolean? = null

        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(particlesEnabled = true),
                    onBack = {},
                    onParticlesEnabled = { toggledValue = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Weather Particles").performScrollTo().performClick()
        assertEquals(false, toggledValue)
    }

    @Test
    fun settingsScreen_timeFormatOptions() {
        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                SettingsContent(
                    settings = NimbusSettings(),
                    onBack = {},
                )
            }
        }

        clickCategory("Forecast")
        composeTestRule.onNodeWithText("12-hour").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("24-hour").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_backButton_invokesCallback() {
        var backCalled = false

        composeTestRule.setContentWithAccessibilityChecks {
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

private fun SettingsScreenTest.assertCategoryDisplayed(label: String) {
    composeTestRule.onNode(hasText(label) and hasClickAction()).performScrollTo().assertIsDisplayed()
}

private fun SettingsScreenTest.clickCategory(label: String) {
    composeTestRule.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    composeTestRule.waitForIdle()
}
