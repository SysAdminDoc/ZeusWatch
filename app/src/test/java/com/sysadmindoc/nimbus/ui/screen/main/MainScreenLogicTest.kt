package com.sysadmindoc.nimbus.ui.screen.main

import com.sysadmindoc.nimbus.data.repository.CardType
import com.sysadmindoc.nimbus.ui.navigation.BottomTab
import com.sysadmindoc.nimbus.ui.navigation.MainDeepLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `mainDeepLinkCardTarget maps notification targets to cards`() {
        assertEquals(CardType.NOWCAST, mainDeepLinkCardTarget(MainDeepLinkTarget.NOWCAST))
        assertEquals(CardType.HEALTH_ALERTS, mainDeepLinkCardTarget(MainDeepLinkTarget.HEALTH))
        assertNull(mainDeepLinkCardTarget(MainDeepLinkTarget.WEATHER_ALERTS))
    }

    @Test
    fun `cardsForDeepLinkTarget temporarily exposes hidden target card`() {
        val enabled = listOf(CardType.WEATHER_SUMMARY, CardType.HOURLY_FORECAST)

        val result = cardsForDeepLinkTarget(enabled, CardType.HEALTH_ALERTS)

        assertEquals(
            listOf(CardType.HEALTH_ALERTS, CardType.WEATHER_SUMMARY, CardType.HOURLY_FORECAST),
            result,
        )
    }

    @Test
    fun `cardsForDeepLinkTarget does not duplicate already visible target card`() {
        val enabled = listOf(CardType.NOWCAST, CardType.WEATHER_SUMMARY)

        val result = cardsForDeepLinkTarget(enabled, CardType.NOWCAST)

        assertEquals(enabled, result)
    }

    @Test
    fun `weatherContentItemIndexForDeepLinkTarget points to alert banner when present`() {
        val result = weatherContentItemIndexForDeepLinkTarget(
            target = MainDeepLinkTarget.WEATHER_ALERTS,
            visibleCards = CardType.entries.toList(),
            hasOfflineBanner = true,
            hasLocationBar = true,
            hasAlertBanner = true,
        )

        assertEquals(3, result)
    }

    @Test
    fun `weatherContentItemIndexForDeepLinkTarget points to card after dynamic header rows`() {
        val cards = listOf(CardType.WEATHER_SUMMARY, CardType.NOWCAST, CardType.HOURLY_FORECAST)

        val result = weatherContentItemIndexForDeepLinkTarget(
            target = MainDeepLinkTarget.NOWCAST,
            visibleCards = cards,
            hasOfflineBanner = false,
            hasLocationBar = true,
            hasAlertBanner = false,
        )

        assertEquals(6, result)
    }

    @Test
    fun `weatherContentItemIndexForDeepLinkTarget opens top when alert is no longer active`() {
        val result = weatherContentItemIndexForDeepLinkTarget(
            target = MainDeepLinkTarget.WEATHER_ALERTS,
            visibleCards = CardType.entries.toList(),
            hasOfflineBanner = false,
            hasLocationBar = false,
            hasAlertBanner = false,
        )

        assertEquals(0, result)
    }
}
