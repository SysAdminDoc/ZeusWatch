package com.sysadmindoc.nimbus.ui.screen.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteShareParserTest {

    @Test
    fun `parses origin and destination query parameters`() {
        val fields = parseSharedRouteText(
            "https://www.google.com/maps/dir/?api=1&origin=Denver%2C%20CO&destination=Boulder%2C%20CO"
        )

        assertEquals("Denver, CO", fields.origin)
        assertEquals("Boulder, CO", fields.destination)
        assertFalse(fields.unreadable)
    }

    @Test
    fun `parses maps dir path`() {
        val fields = parseSharedRouteText("https://www.google.com/maps/dir/Denver,+CO/Boulder,+CO")

        assertEquals("Denver, CO", fields.origin)
        assertEquals("Boulder, CO", fields.destination)
        assertFalse(fields.unreadable)
    }

    @Test
    fun `marks opaque short links unreadable`() {
        val fields = parseSharedRouteText("https://maps.app.goo.gl/abcdef")

        assertEquals(null, fields.origin)
        assertEquals(null, fields.destination)
        assertTrue(fields.unreadable)
    }

    @Test
    fun `capSharedRouteText bounds huge external shares`() {
        val huge = "x".repeat(500_000)

        val capped = capSharedRouteText(huge)

        assertEquals(MAX_SHARED_ROUTE_TEXT_CHARS, capped!!.length)
        // The capped text must still be safe to hand to the share parser.
        assertEquals(huge.take(MAX_SHARED_ROUTE_TEXT_CHARS), parseSharedRouteText(capped).destination)
    }

    @Test
    fun `capSharedRouteText passes normal shares through trimmed`() {
        assertEquals(
            "Denver, CO to Boulder, CO",
            capSharedRouteText("  Denver, CO to Boulder, CO  "),
        )
        assertEquals(null, capSharedRouteText(null))
        assertEquals(null, capSharedRouteText("   "))
    }
}
