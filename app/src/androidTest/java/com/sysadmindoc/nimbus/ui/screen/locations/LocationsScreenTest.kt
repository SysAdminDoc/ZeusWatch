package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import org.junit.Rule
import org.junit.Test

class LocationsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val savedLocations = listOf(
        SavedLocationEntity(
            id = 1, name = "Denver", latitude = 39.7, longitude = -104.9,
            region = "Colorado", country = "United States",
            isCurrentLocation = true, sortOrder = -1,
        ),
        SavedLocationEntity(
            id = 2, name = "New York", latitude = 40.7, longitude = -74.0,
            region = "New York", country = "United States", sortOrder = 0,
        ),
    )

    private val searchResults = listOf(
        GeocodingResult(
            id = 100, name = "San Francisco", latitude = 37.8, longitude = -122.4,
            country = "United States", admin1 = "California",
        ),
        GeocodingResult(
            id = 101, name = "San Diego", latitude = 32.7, longitude = -117.2,
            country = "United States", admin1 = "California",
        ),
    )

    @Test
    fun locationsScreen_showsTitleAndSearchBar() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = emptyList(),
                    search = SearchState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Locations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search city or zip code...").assertIsDisplayed()
    }

    @Test
    fun locationsScreen_showsSavedLocations() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = savedLocations,
                    search = SearchState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Saved Locations").assertIsDisplayed()
        composeTestRule.onNodeWithText("My Location").assertIsDisplayed()
        composeTestRule.onNodeWithText("New York").assertIsDisplayed()
    }

    @Test
    fun locationsScreen_showsCurrentLocationSubtitle() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = savedLocations,
                    search = SearchState(),
                    onBack = {},
                )
            }
        }

        // Current location shows actual name as subtitle
        composeTestRule.onNodeWithText("Denver").assertIsDisplayed()
    }

    @Test
    fun locationsScreen_showsRegionForSavedLocation() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = savedLocations,
                    search = SearchState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("New York, United States").assertIsDisplayed()
    }

    @Test
    fun locationsScreen_showsSearchResults() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = emptyList(),
                    search = SearchState(
                        query = "San",
                        results = searchResults,
                        isSearching = false,
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Search Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("San Francisco").assertIsDisplayed()
        composeTestRule.onNodeWithText("San Diego").assertIsDisplayed()
        composeTestRule.onNodeWithText("California").assertIsDisplayed()
    }

    @Test
    fun locationsScreen_showsNoResultsMessage() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = emptyList(),
                    search = SearchState(
                        query = "xyznoplace",
                        results = emptyList(),
                        isSearching = false,
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No results found").assertIsDisplayed()
    }

    @Test
    fun locationsScreen_shortQuery_hidesResults() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = savedLocations,
                    search = SearchState(query = "D"),
                    onBack = {},
                )
            }
        }

        // Short query doesn't trigger results section
        composeTestRule.onNodeWithText("Search Results").assertDoesNotExist()
        composeTestRule.onNodeWithText("No results found").assertDoesNotExist()
    }

    @Test
    fun locationsScreen_emptyState_showsSearchBarOnly() {
        composeTestRule.setContent {
            NimbusTheme {
                LocationsContent(
                    saved = emptyList(),
                    search = SearchState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Locations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved Locations").assertDoesNotExist()
        composeTestRule.onNodeWithText("Search Results").assertDoesNotExist()
    }
}
