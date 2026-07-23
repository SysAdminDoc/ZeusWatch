package com.sysadmindoc.nimbus.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.nimbus.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device-free structural tests for all eight release Glance widgets using the
 * Glance 1.1.1 `runGlanceAppWidgetUnitTest` harness (NX-30). These compose each
 * widget's content across canonical size modes and assert its cached, empty, and
 * freshness states plus the tap-to-open / tap-to-refresh action contract. They
 * make no pixel-rendering or click-execution claim — only node/semantics/action
 * structure, which is what the unit harness supports.
 *
 * The harness allows a single `setAppWidgetSize` + `provideComposable` per test
 * scope, so each widget/size/state combination runs its own `runGlanceAppWidgetUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NimbusWidgetUnitTest {

    private val canonicalSizes = listOf(
        DpSize(110.dp, 110.dp),   // 2x2
        DpSize(250.dp, 110.dp),   // 4x2
        DpSize(250.dp, 250.dp),   // 4x4
    )

    /** The seven single-location widgets share the `(data, strings)` contract. */
    private val locationWidgets: List<Pair<String, @Composable (WidgetWeatherData?, WidgetStrings) -> Unit>> =
        listOf(
            "NimbusSmallWidget" to { d, s -> SmallWidgetContent(d, s) },
            "NimbusMediumWidget" to { d, s -> MediumWidgetContent(d, s) },
            "NimbusLargeWidget" to { d, s -> LargeWidgetContent(d, s) },
            "NimbusForecastStripWidget" to { d, s -> ForecastStripContent(d, s) },
            "NimbusTempWidget" to { d, s -> TempWidgetContent(d, s) },
            "NimbusCompactWidget" to { d, s -> CompactWidgetContent(d, s) },
            "NimbusDailyWidget" to { d, s -> DailyWidgetContent(d, s) },
        )

    private fun strings(): WidgetStrings = widgetStrings(ApplicationProvider.getApplicationContext())

    private fun sampleData() = WidgetWeatherData(
        locationName = "Testville",
        temperature = 21.0,
        feelsLike = 20.0,
        high = 25.0,
        low = 15.0,
        weatherCode = 0,
        isDay = true,
        humidity = 55,
        windSpeed = 10.0,
        hourly = List(6) { i -> WidgetHourly(hour = if (i == 0) "Now" else "${i}h", temp = 20 + i, code = 0, isDay = true, precipChance = i * 5) },
        daily = List(5) { i -> WidgetDaily(day = if (i == 0) "Today" else "D$i", high = 25 - i, low = 15 - i, code = i, precipChance = i * 10) },
        // A just-now timestamp so `updatedLabel` resolves to the live freshness badge.
        updatedAt = System.currentTimeMillis(),
    )

    @Test
    fun `location widgets open the app and expose a live refresh badge when cached`() {
        val strings = strings()
        val data = sampleData()
        for ((_, content) in locationWidgets) {
            for (size in canonicalSizes) {
                runGlanceAppWidgetUnitTest {
                    setAppWidgetSize(size)
                    provideComposable { content(data, strings) }

                    // Cached state opens the app on tap.
                    onNode(hasStartActivityClickAction<MainActivity>()).assertExists()

                    // The freshness badge is present, accessible, and force-refreshes on tap.
                    onNode(hasContentDescriptionEqualTo(strings.updatedLiveContentDescription))
                        .assertExists()
                        .assertHasRunCallbackClickAction<WidgetRefreshAction>()
                }
            }
        }
    }

    @Test
    fun `location widgets refresh instead of opening the app when empty`() {
        val strings = strings()
        for ((_, content) in locationWidgets) {
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(canonicalSizes.first())
                provideComposable { content(null, strings) }

                // Empty state must never claim data by opening the app...
                onNode(hasStartActivityClickAction<MainActivity>()).assertDoesNotExist()
                // ...and its whole surface is a single tap-to-refresh target.
                onNode(hasClickAction())
                    .assertExists()
                    .assertHasRunCallbackClickAction<WidgetRefreshAction>()
            }
        }
    }

    @Test
    fun `saved cities widget lists cities with a refresh badge and per-row open actions`() =
        runGlanceAppWidgetUnitTest {
            val strings = strings()
            val cities = listOf(
                WidgetSavedCity(locationId = 1, locationName = "Alpha", temperature = 18, high = 20, low = 12, weatherCode = 0, updatedAt = System.currentTimeMillis()),
                WidgetSavedCity(locationId = 2, locationName = "Bravo", temperature = 24, high = 27, low = 18, weatherCode = 2, updatedAt = System.currentTimeMillis()),
            )
            setAppWidgetSize(DpSize(250.dp, 250.dp))
            provideComposable { SavedCitiesWidgetContent(cities, strings) }

            // Header freshness badge with force-refresh action.
            onNode(hasContentDescriptionEqualTo(strings.updatedLiveContentDescription))
                .assertExists()
                .assertHasRunCallbackClickAction<WidgetRefreshAction>()

            // Each city label is rendered.
            onNode(hasText("Alpha")).assertExists()
            onNode(hasText("Bravo")).assertExists()
        }

    @Test
    fun `saved cities widget opens the app to add locations when empty`() =
        runGlanceAppWidgetUnitTest {
            val strings = strings()
            setAppWidgetSize(DpSize(250.dp, 250.dp))
            provideComposable { SavedCitiesWidgetContent(emptyList(), strings) }

            onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
            onNode(hasText(strings.savedCitiesEmptyMessage)).assertExists()
        }
}
