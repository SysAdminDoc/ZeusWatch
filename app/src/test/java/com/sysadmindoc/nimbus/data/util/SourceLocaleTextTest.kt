package com.sysadmindoc.nimbus.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SourceLocaleTextTest {

    @Test
    fun `selectText prefers source text matching user language`() {
        val selected = SourceLocaleText.selectText(
            options = listOf(
                LocalizedTextOption("en", "Heavy rain warning"),
                LocalizedTextOption("de", "Warnung vor Starkregen"),
            ),
            fallback = "Heavy rain warning",
            locale = Locale.GERMANY,
        )

        assertEquals("Warnung vor Starkregen", selected)
    }

    @Test
    fun `selectText falls back when no source language matches`() {
        val selected = SourceLocaleText.selectText(
            options = listOf(LocalizedTextOption("de", "Warnung vor Starkregen")),
            fallback = "Heavy rain warning",
            locale = Locale.US,
        )

        assertEquals("Heavy rain warning", selected)
    }

    @Test
    fun `HKO language keeps simplified and traditional Chinese separate`() {
        assertEquals("sc", SourceLocaleText.preferredHkoLanguage(Locale.SIMPLIFIED_CHINESE))
        assertEquals("tc", SourceLocaleText.preferredHkoLanguage(Locale.TRADITIONAL_CHINESE))
        assertEquals("tc", SourceLocaleText.preferredHkoLanguage(Locale.Builder().setLanguage("zh").setRegion("HK").build()))
        assertEquals("en", SourceLocaleText.preferredHkoLanguage(Locale.US))
    }

    @Test
    fun `filterByLocale returns only matching language blocks when present`() {
        val blocks = listOf(
            LocalizedTextOption("en", "Thunderstorm"),
            LocalizedTextOption("id", "Badai petir"),
        )

        val selected = SourceLocaleText.filterByLocale(
            blocks,
            { it.languageTag },
            Locale.Builder().setLanguage("id").setRegion("ID").build(),
        )

        assertEquals(listOf(LocalizedTextOption("id", "Badai petir")), selected)
    }
}
