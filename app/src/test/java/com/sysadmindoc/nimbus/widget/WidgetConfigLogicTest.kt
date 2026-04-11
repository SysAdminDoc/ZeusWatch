package com.sysadmindoc.nimbus.widget

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigLogicTest {

    @Test
    fun `widgetSelectableLocations hides current location row`() {
        val locations = listOf(
            SavedLocationEntity(
                id = 1L,
                name = "My Location",
                latitude = 39.7392,
                longitude = -104.9903,
                isCurrentLocation = true,
                sortOrder = -1,
            ),
            SavedLocationEntity(
                id = 2L,
                name = "Denver",
                latitude = 39.7392,
                longitude = -104.9903,
                sortOrder = 0,
            ),
            SavedLocationEntity(
                id = 3L,
                name = "Boulder",
                latitude = 40.01499,
                longitude = -105.2705,
                sortOrder = 1,
            ),
        )

        assertEquals(
            listOf(locations[1], locations[2]),
            widgetSelectableLocations(locations),
        )
    }
}
