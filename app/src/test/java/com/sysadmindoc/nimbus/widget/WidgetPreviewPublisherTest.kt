package com.sysadmindoc.nimbus.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPreviewPublisherTest {

    @Test
    fun `previews are published once per installed version`() {
        assertTrue(WidgetPreviewPublisher.shouldRepublish(publishedVersionCode = -1, currentVersionCode = 110))
        // An app update can change what a widget renders, so a newer version
        // republishes rather than trusting the previous version's previews.
        assertTrue(WidgetPreviewPublisher.shouldRepublish(publishedVersionCode = 109, currentVersionCode = 110))
        assertFalse(WidgetPreviewPublisher.shouldRepublish(publishedVersionCode = 110, currentVersionCode = 110))
    }

    @Test
    fun `the publisher covers every widget receiver exactly once`() {
        val receivers = WidgetPreviewPublisher.receivers

        assertEquals(8, receivers.size)
        assertEquals(receivers.size, receivers.distinct().size)
    }
}
