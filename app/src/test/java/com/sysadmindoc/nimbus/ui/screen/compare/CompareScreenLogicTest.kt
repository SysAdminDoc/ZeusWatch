package com.sysadmindoc.nimbus.ui.screen.compare

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareScreenLogicTest {

    private val savedLocation = SavedLocationEntity(
        id = 1,
        name = "Denver",
        latitude = 39.7392,
        longitude = -104.9903,
        region = "Colorado",
        country = "US",
        sortOrder = 0,
    )

    @Test
    fun `shouldShowCompareFullScreenError only triggers when no locations are available`() {
        assertTrue(
            shouldShowCompareFullScreenError(
                CompareUiState(error = "Network failure")
            )
        )

        assertFalse(
            shouldShowCompareFullScreenError(
                CompareUiState(
                    savedLocations = listOf(savedLocation),
                    error = "Network failure",
                )
            )
        )
    }

    @Test
    fun `highlightedCompareSides highlights the lower side when requested`() {
        assertEquals(true to false, highlightedCompareSides(highlightLower = true, raw1 = 40.0, raw2 = 65.0))
        assertEquals(false to true, highlightedCompareSides(highlightLower = true, raw1 = 70.0, raw2 = 30.0))
        assertEquals(false to false, highlightedCompareSides(highlightLower = true, raw1 = 30.0, raw2 = 30.0))
        assertEquals(false to false, highlightedCompareSides(highlightLower = false, raw1 = 30.0, raw2 = 10.0))
        assertEquals(false to false, highlightedCompareSides(highlightLower = true, raw1 = null, raw2 = 10.0))
    }
}
