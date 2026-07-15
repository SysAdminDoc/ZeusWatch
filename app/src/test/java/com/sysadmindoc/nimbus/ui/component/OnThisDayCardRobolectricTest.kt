package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.OnThisDayData
import com.sysadmindoc.nimbus.data.model.PriorYearEntry
import com.sysadmindoc.nimbus.data.model.TimeTravelDay
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * JVM Compose UI test for the time-travel scrub states of [OnThisDayCard]:
 * loading indicator during a fetch, calm inline rows for a failed fetch and
 * for a date with no archive data (previous result preserved), plus the
 * a11y contract of the "Explore other dates" entry point.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class OnThisDayCardRobolectricTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val data = OnThisDayData(
        priorYears = listOf(
            PriorYearEntry(year = 2025, highC = 21.0, lowC = 9.0, precipMm = null),
            PriorYearEntry(year = 2024, highC = 18.0, lowC = 7.0, precipMm = 0.4),
        ),
        averageHighC = 19.5,
        averageLowC = 8.0,
        recordHighC = 21.0,
        recordLowC = 7.0,
    )

    private val selectedDay = TimeTravelDay(
        date = LocalDate.of(2020, 6, 1),
        weatherCode = WeatherCode.CLEAR_SKY,
        highC = 24.0,
        lowC = 11.0,
        precipMm = null,
        isHistorical = true,
    )

    private fun setCard(status: TimeTravelStatus, day: TimeTravelDay? = null) {
        composeTestRule.setContent {
            NimbusTheme {
                CompositionLocalProvider(LocalUnitSettings provides NimbusSettings(tempUnit = TempUnit.CELSIUS)) {
                    Box(Modifier.width(360.dp)) {
                        OnThisDayCard(
                            data = data,
                            forecastHighC = 20.0,
                            onDateSelected = {},
                            selectedDay = day,
                            timeTravelStatus = status,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun loadingStatus_rendersLoadingRow() {
        setCard(TimeTravelStatus.LOADING)
        composeTestRule
            .onNodeWithText("Loading historical weather…", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun errorStatus_rendersErrorRow_andKeepsSelectedDay() {
        setCard(TimeTravelStatus.ERROR, day = selectedDay)
        composeTestRule
            .onNodeWithText(
                "Couldn't load weather for that date. Check your connection and try again.",
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        // The previous scrub result stays visible alongside the error row.
        val dateText = selectedDay.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        composeTestRule.onNodeWithText(dateText, useUnmergedTree = true).assertExists()
    }

    @Test
    fun unavailableStatus_rendersUnavailableRow() {
        setCard(TimeTravelStatus.DATE_UNAVAILABLE)
        composeTestRule
            .onNodeWithText(
                "No archive data for this date yet. Recent days can take a couple of days to appear.",
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun exploreDates_isAButtonWithMinTouchTarget() {
        setCard(TimeTravelStatus.IDLE)
        composeTestRule
            .onNodeWithText("Explore other dates", useUnmergedTree = true)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
    }
}
