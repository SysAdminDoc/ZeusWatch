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
}
