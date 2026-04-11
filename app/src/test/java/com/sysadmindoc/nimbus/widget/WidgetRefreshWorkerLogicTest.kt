package com.sysadmindoc.nimbus.widget

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshWorkerLogicTest {

    @Test
    fun `buildWidgetRefreshPlan groups duplicate coordinates but preserves widget labels`() {
        val savedLocations = listOf(
            SavedLocationEntity(
                id = 1L,
                name = "My Location",
                latitude = 39.73921,
                longitude = -104.99031,
                isCurrentLocation = true,
                sortOrder = -1,
            ),
            SavedLocationEntity(
                id = 2L,
                name = "Denver",
                latitude = 39.73924,
                longitude = -104.99034,
                sortOrder = 0,
            ),
            SavedLocationEntity(
                id = 3L,
                name = "Boulder",
                latitude = 40.01499,
                longitude = -105.27050,
                sortOrder = 1,
            ),
        )

        val plan = buildWidgetRefreshPlan(
            widgetMappings = linkedMapOf(
                101 to 1L,
                102 to 2L,
                103 to 3L,
            ),
            savedLocations = savedLocations,
        )

        assertEquals(emptyList<Int>(), plan.orphanedWidgetIds)
        assertEquals(2, plan.requests.size)

        val denverRequest = plan.requests.first { it.assignments.size == 2 }
        assertEquals(widgetLocationKey(39.73921, -104.99031), denverRequest.key)
        assertEquals(
            listOf(
                WidgetRefreshAssignment(appWidgetId = 101, displayName = "My Location"),
                WidgetRefreshAssignment(appWidgetId = 102, displayName = "Denver"),
            ),
            denverRequest.assignments,
        )

        val boulderRequest = plan.requests.first { it.assignments.size == 1 }
        assertEquals(widgetLocationKey(40.01499, -105.27050), boulderRequest.key)
        assertEquals(
            listOf(WidgetRefreshAssignment(appWidgetId = 103, displayName = "Boulder")),
            boulderRequest.assignments,
        )
    }

    @Test
    fun `buildWidgetRefreshPlan reports orphaned widget mappings`() {
        val plan = buildWidgetRefreshPlan(
            widgetMappings = linkedMapOf(
                101 to 1L,
                102 to 99L,
            ),
            savedLocations = listOf(
                SavedLocationEntity(
                    id = 1L,
                    name = "Denver",
                    latitude = 39.7392,
                    longitude = -104.9903,
                    sortOrder = 0,
                ),
            ),
        )

        assertEquals(listOf(102), plan.orphanedWidgetIds)
        assertEquals(1, plan.requests.size)
        assertEquals(
            listOf(WidgetRefreshAssignment(appWidgetId = 101, displayName = "Denver")),
            plan.requests.single().assignments,
        )
    }
}
