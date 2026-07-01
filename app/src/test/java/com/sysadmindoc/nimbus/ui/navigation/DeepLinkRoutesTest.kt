package com.sysadmindoc.nimbus.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRoutesTest {

    @Test
    fun `resolveZeusWatchDeepLinkRoute keeps top-level destinations`() {
        assertEquals(Routes.LOCATIONS, resolveZeusWatchDeepLinkRoute("locations"))
        assertEquals(Routes.SETTINGS, resolveZeusWatchDeepLinkRoute("settings"))
        assertEquals(Routes.COMPARE, resolveZeusWatchDeepLinkRoute("compare"))
        assertEquals(Routes.CUSTOM_ALERTS, resolveZeusWatchDeepLinkRoute("custom_alerts"))
    }

    @Test
    fun `resolveZeusWatchDeepLinkRoute routes main target query to focused forecast route`() {
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.NOWCAST),
            resolveZeusWatchDeepLinkRoute(host = "main", target = "nowcast"),
        )
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.HEALTH),
            resolveZeusWatchDeepLinkRoute(host = "main", target = "health"),
        )
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.WEATHER_ALERTS),
            resolveZeusWatchDeepLinkRoute(host = "main", target = "weather_alerts"),
        )
    }

    @Test
    fun `resolveZeusWatchDeepLinkRoute routes main location query to saved location`() {
        assertEquals(
            Routes.mainWithLocation(42L),
            resolveZeusWatchDeepLinkRoute(host = "main", locationId = "42"),
        )
        assertNull(resolveZeusWatchDeepLinkRoute(host = "main", locationId = "0"))
        assertNull(resolveZeusWatchDeepLinkRoute(host = "main", locationId = "not-a-number"))
    }

    @Test
    fun `resolveZeusWatchDeepLinkRoute accepts legacy card query names`() {
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.NOWCAST),
            resolveZeusWatchDeepLinkRoute(host = "main", card = "NOWCAST"),
        )
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.HEALTH),
            resolveZeusWatchDeepLinkRoute(host = "main", card = "HEALTH_ALERTS"),
        )
    }

    @Test
    fun `resolveZeusWatchDeepLinkRoute routes direct notification hosts`() {
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.WEATHER_ALERTS),
            resolveZeusWatchDeepLinkRoute("alerts"),
        )
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.NOWCAST),
            resolveZeusWatchDeepLinkRoute("nowcast"),
        )
        assertEquals(
            Routes.mainTarget(MainDeepLinkTarget.HEALTH),
            resolveZeusWatchDeepLinkRoute("health_alerts"),
        )
    }

    @Test
    fun `resolveZeusWatchDeepLinkRoute carries radar route text`() {
        assertEquals(
            "radar/0.0/0.0?route=Denver%20to%20Boulder",
            resolveZeusWatchDeepLinkRoute(host = "radar", routeText = "Denver to Boulder"),
        )
    }

    @Test
    fun `resolveZeusWatchDeepLinkRoute ignores unknown main targets`() {
        assertNull(resolveZeusWatchDeepLinkRoute(host = "main", target = "widgets"))
        assertNull(resolveZeusWatchDeepLinkRoute(host = "unknown"))
    }
}
