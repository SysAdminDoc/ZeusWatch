package com.sysadmindoc.nimbus.ui.screen.compare

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
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
}
