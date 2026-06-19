package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.IconPackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackManagerTest {

    @Test
    fun `parseIconPackManifest accepts safe relative icon paths`() {
        val json = """
            {
              "id": "premium-pack",
              "name": "Premium Pack",
              "author": "ZeusWatch",
              "format": "webp",
              "mappings": {
                "0": { "day": "day/clear.webp", "night": "night/clear.webp" },
                "61": { "day": "rain.webp", "night": "rain.webp" }
              }
            }
        """.trimIndent()

        val pack = parseIconPackManifest(json, IconPackSource.Bundled("iconpacks/premium"))

        assertEquals("premium-pack", pack.id)
        assertEquals("webp", pack.format)
        assertEquals("day/clear.webp", pack.mappings.getValue(0).dayIcon)
        assertEquals("rain.webp", pack.mappings.getValue(61).nightIcon)
    }

    @Test
    fun `parseIconPackManifest rejects unsafe icon paths`() {
        val json = """
            {
              "id": "bad-pack",
              "name": "Bad Pack",
              "mappings": {
                "0": { "day": "../clear.png", "night": "clear.png" }
              }
            }
        """.trimIndent()

        val result = runCatching {
            parseIconPackManifest(json, IconPackSource.External("example.bad"))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("unsafe path"))
    }

    @Test
    fun `parseIconPackManifest rejects formats the runtime cannot decode`() {
        val json = """
            {
              "id": "svg-pack",
              "name": "SVG Pack",
              "format": "svg",
              "mappings": {
                "0": { "day": "clear.svg", "night": "clear.svg" }
              }
            }
        """.trimIndent()

        val result = runCatching {
            parseIconPackManifest(json, IconPackSource.External("example.svg"))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not supported"))
    }

    @Test
    fun `parseIconPackManifest rejects oversized mapping tables`() {
        val mappings = (0..256).joinToString(",") { code ->
            """"$code": { "day": "icon-$code.png", "night": "icon-$code.png" }"""
        }
        val json = """
            {
              "id": "huge-pack",
              "name": "Huge Pack",
              "mappings": { $mappings }
            }
        """.trimIndent()

        val result = runCatching {
            parseIconPackManifest(json, IconPackSource.Bundled("iconpacks/huge"))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("too many mappings"))
    }
}
