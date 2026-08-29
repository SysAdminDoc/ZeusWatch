package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reconfiguring a widget back to "Follow app location" shipped without
 * coverage because the logic sat inside the config activity, tangled with
 * WorkManager and the activity result. It is now a plain suspend function.
 *
 * The bug it guards against is silent and permanent: refreshes only write the
 * global default for follow-app widgets, so a leftover per-widget row keeps
 * the old pinned city on screen for as long as the widget exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetLocationSelectionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appWidgetId = 42

    @Test
    fun followingAppLocation_purgesThePinnedCityData() = runTest {
        WidgetDataProvider.save(context, sampleData("Pinned City"), appWidgetId)
        WidgetLocationPrefs.setLocationId(context, appWidgetId, 7L)

        applyWidgetLocationSelection(context, appWidgetId, locationId = null)

        assertNull("per-widget data must be purged", WidgetDataProvider.load(context, appWidgetId))
        assertNull("pin must be cleared", WidgetLocationPrefs.getLocationId(context, appWidgetId))
    }

    @Test
    fun pinningACity_keepsThePerWidgetDataItWillOverwrite() = runTest {
        WidgetDataProvider.save(context, sampleData("Pinned City"), appWidgetId)

        applyWidgetLocationSelection(context, appWidgetId, locationId = 9L)

        // Pinning a city is followed by a refresh that writes this widget's own
        // row, so wiping it here would only blank the widget until that lands.
        assertNotNull(WidgetDataProvider.load(context, appWidgetId))
        assertEquals(9L, WidgetLocationPrefs.getLocationId(context, appWidgetId))
    }

    @Test
    fun purgingOneWidget_leavesTheOthersAlone() = runTest {
        val other = 43
        WidgetDataProvider.save(context, sampleData("Other City"), other)
        WidgetLocationPrefs.setLocationId(context, other, 11L)
        WidgetDataProvider.save(context, sampleData("Pinned City"), appWidgetId)

        applyWidgetLocationSelection(context, appWidgetId, locationId = null)

        assertNotNull("a sibling widget must not be purged", WidgetDataProvider.load(context, other))
        assertEquals(11L, WidgetLocationPrefs.getLocationId(context, other))
    }

    private fun sampleData(name: String) = WidgetWeatherData(
        locationName = name,
        temperature = 20.0,
        feelsLike = 19.0,
        high = 24.0,
        low = 15.0,
        weatherCode = 0,
        isDay = true,
        humidity = 50,
        windSpeed = 10.0,
        hourly = emptyList(),
        daily = emptyList(),
        updatedAt = 1_700_000_000_000L,
    )
}
