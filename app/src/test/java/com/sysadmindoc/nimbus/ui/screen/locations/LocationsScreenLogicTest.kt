package com.sysadmindoc.nimbus.ui.screen.locations

import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationsScreenLogicTest {

    @Test
    fun `filterDuplicateSearchResults hides already saved locations but keeps current location matches`() {
        val saved = listOf(
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
                region = "Colorado",
                country = "United States",
                latitude = 39.7392,
                longitude = -104.9903,
                sortOrder = 0,
            ),
        )
        val results = listOf(
            GeocodingResult(
                id = 10L,
                name = "Denver",
                latitude = 39.7392,
                longitude = -104.9903,
                country = "United States",
                admin1 = "Colorado",
            ),
            GeocodingResult(
                id = 11L,
                name = "Boulder",
                latitude = 40.01499,
                longitude = -105.27055,
                country = "United States",
                admin1 = "Colorado",
            ),
        )

        val visible = filterDuplicateSearchResults(results, saved)

        assertEquals(listOf("Boulder"), visible.map { it.name })
    }

    @Test
    fun `locationsSearchEmptyMessage explains when results are already saved`() {
        val search = SearchState(
            query = "Denver",
            results = listOf(
                GeocodingResult(
                    id = 10L,
                    name = "Denver",
                    latitude = 39.7392,
                    longitude = -104.9903,
                    country = "United States",
                    admin1 = "Colorado",
                )
            ),
            isSearching = false,
        )

        val message = locationsSearchEmptyMessage(search, visibleResults = emptyList())

        assertEquals("Location already saved", message)
    }

    @Test
    fun `locationsSearchEmptyMessage stays empty while still searching or showing results`() {
        assertNull(
            locationsSearchEmptyMessage(
                search = SearchState(query = "De", isSearching = true),
                visibleResults = emptyList(),
            )
        )
        assertNull(
            locationsSearchEmptyMessage(
                search = SearchState(query = "Denver"),
                visibleResults = listOf(
                    GeocodingResult(
                        id = 11L,
                        name = "Boulder",
                        latitude = 40.01499,
                        longitude = -105.27055,
                        country = "United States",
                        admin1 = "Colorado",
                    )
                ),
            )
        )
    }

    @Test
    fun `computeDraggedLocationTargetIndex uses current index and pixel threshold`() {
        val itemHeightPx = 124f

        assertEquals(
            3,
            computeDraggedLocationTargetIndex(
                currentIndex = 2,
                dragOffsetPx = 130f,
                itemHeightPx = itemHeightPx,
                lastIndex = 5,
            )
        )
        assertEquals(
            4,
            computeDraggedLocationTargetIndex(
                currentIndex = 3,
                dragOffsetPx = 130f,
                itemHeightPx = itemHeightPx,
                lastIndex = 5,
            )
        )
    }

    @Test
    fun `computeDraggedLocationTargetIndex respects minimum movable index`() {
        assertEquals(
            1,
            computeDraggedLocationTargetIndex(
                currentIndex = 2,
                dragOffsetPx = -500f,
                itemHeightPx = 124f,
                minimumIndex = 1,
                lastIndex = 5,
            )
        )
    }
}
