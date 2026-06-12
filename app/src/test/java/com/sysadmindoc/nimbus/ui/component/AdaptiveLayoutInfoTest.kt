package com.sysadmindoc.nimbus.ui.component

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutInfoTest {

    @Test
    fun `expanded width supports two-pane weather`() {
        val layout = AdaptiveLayoutInfo.from(WindowWidthSizeClass.Expanded)

        assertTrue(layout.supportsTwoPaneWeather)
        assertFalse(layout.isTabletop)
    }

    @Test
    fun `book posture supports two-pane weather below expanded width`() {
        val layout = AdaptiveLayoutInfo.from(
            widthClass = WindowWidthSizeClass.Compact,
            foldPosture = FoldPosture.BOOK,
        )

        assertTrue(layout.supportsTwoPaneWeather)
        assertTrue(layout.isBookMode)
    }

    @Test
    fun `tabletop posture uses dedicated split instead of side-by-side weather`() {
        val layout = AdaptiveLayoutInfo.from(
            widthClass = WindowWidthSizeClass.Compact,
            foldPosture = FoldPosture.TABLETOP,
        )

        assertFalse(layout.supportsTwoPaneWeather)
        assertTrue(layout.isTabletop)
    }
}
