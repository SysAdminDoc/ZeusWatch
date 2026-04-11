package com.sysadmindoc.nimbus.ui.screen.main

import com.sysadmindoc.nimbus.ui.navigation.BottomTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenLogicTest {

    @Test
    fun `normalizeSelectedMainTab keeps visible phone tab`() {
        val result = normalizeSelectedMainTab(
            isTablet = false,
            selectedTab = BottomTab.RADAR.ordinal,
        )

        assertEquals(BottomTab.RADAR.ordinal, result)
    }

    @Test
    fun `normalizeSelectedMainTab falls back when radar is hidden on tablet`() {
        val result = normalizeSelectedMainTab(
            isTablet = true,
            selectedTab = BottomTab.RADAR.ordinal,
        )

        assertEquals(BottomTab.TODAY.ordinal, result)
    }

    @Test
    fun `visibleMainTabs excludes radar on tablet`() {
        val result = visibleMainTabs(isTablet = true)

        assertFalse(result.contains(BottomTab.RADAR))
        assertEquals(listOf(BottomTab.TODAY, BottomTab.HOURLY, BottomTab.DAILY), result)
    }

    @Test
    fun `visibleMainTabs keeps radar on phone`() {
        val result = visibleMainTabs(isTablet = false)

        assertTrue(result.contains(BottomTab.RADAR))
        assertEquals(BottomTab.entries.toList(), result)
    }
}
