package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnThisDayCacheEvictionTest {

    @Test
    fun `stamped payload round-trips body and timestamp`() {
        val payload = """{"priorYears":[],"averageHighC":1.0}"""
        val stamped = stampOnThisDayPayload(payload, 1_700_000_000_000L)

        assertEquals(payload, onThisDayPayloadBody(stamped))
        assertEquals(1_700_000_000_000L, onThisDayPayloadTimestamp(stamped))
    }

    @Test
    fun `legacy un-stamped payload passes through and reports timestamp zero`() {
        val legacy = """{"priorYears":[{"year":2015,"highC":20.0,"lowC":10.0,"precipMm":null}]}"""

        assertEquals(legacy, onThisDayPayloadBody(legacy))
        assertEquals(0L, onThisDayPayloadTimestamp(legacy))
    }

    @Test
    fun `payload containing pipe but non-numeric prefix is treated as legacy`() {
        val raw = """{"note":"a|b"}"""

        assertEquals(raw, onThisDayPayloadBody(raw))
        assertEquals(0L, onThisDayPayloadTimestamp(raw))
    }

    @Test
    fun `eviction selects oldest entries first regardless of key order`() {
        // Southern-hemisphere keys (negative latitude) sort lexicographically
        // first; with timestamps they must survive when they are newest.
        val existing = mapOf(
            "-33.87,151.21,2026-07-17" to stampOnThisDayPayload("{}", 3_000L),
            "40.71,-74.01,2026-07-17" to stampOnThisDayPayload("{}", 1_000L),
            "51.51,-0.13,2026-07-17" to stampOnThisDayPayload("{}", 2_000L),
        )

        val evicted = selectOnThisDayEvictions(existing, currentKey = "0.00,0.00,2026-07-17", maxEntries = 2)

        assertEquals(listOf("40.71,-74.01,2026-07-17", "51.51,-0.13,2026-07-17"), evicted)
    }

    @Test
    fun `legacy entries are treated as oldest and evicted before stamped ones`() {
        val existing = mapOf(
            "-10.00,10.00,2026-07-17" to stampOnThisDayPayload("{}", 1L),
            "20.00,20.00,2026-07-17" to "{}",
        )

        val evicted = selectOnThisDayEvictions(existing, currentKey = "0.00,0.00,2026-07-17", maxEntries = 2)

        assertEquals(listOf("20.00,20.00,2026-07-17"), evicted)
    }

    @Test
    fun `current key is never evicted even when it is the oldest`() {
        val current = "0.00,0.00,2026-07-17"
        val existing = mapOf(
            current to stampOnThisDayPayload("{}", 0L),
            "10.00,10.00,2026-07-17" to stampOnThisDayPayload("{}", 5_000L),
            "20.00,20.00,2026-07-17" to stampOnThisDayPayload("{}", 6_000L),
        )

        val evicted = selectOnThisDayEvictions(existing, currentKey = current, maxEntries = 2)

        assertTrue(current !in evicted)
        assertEquals(listOf("10.00,10.00,2026-07-17"), evicted)
    }

    @Test
    fun `no eviction while at or under the cap`() {
        val existing = mapOf(
            "10.00,10.00,2026-07-17" to stampOnThisDayPayload("{}", 1L),
        )

        assertTrue(selectOnThisDayEvictions(existing, "10.00,10.00,2026-07-17", maxEntries = 2).isEmpty())
        assertTrue(selectOnThisDayEvictions(emptyMap<String, String>(), "x", maxEntries = 1).isEmpty())
    }

    @Test
    fun `eviction keeps the cache at the cap including the new write`() {
        val existing = (1..500).associate { index ->
            String.format(java.util.Locale.US, "%03d", index) to
                stampOnThisDayPayload("{}", index.toLong())
        }

        val evicted = selectOnThisDayEvictions(existing, currentKey = "new-key", maxEntries = 500)

        assertEquals(1, evicted.size)
        assertEquals("001", evicted.single())
    }
}
