package com.sysadmindoc.nimbus.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The notices asset is what the app shows users to satisfy the licence terms
 * of everything it ships. A silently empty or mis-filtered list is a licence
 * problem, not a cosmetic one, so the packaged file is asserted directly.
 */
class OssNoticesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val notices: OssNotices by lazy {
        json.decodeFromString<OssNotices>(
            repositoryRoot().resolve("app/src/main/assets/oss_notices.json").toFile().readText(),
        )
    }

    @Test
    fun `the packaged asset carries dependencies and data providers`() {
        assertTrue("expected dependencies", notices.dependencies.size > 40)
        assertTrue("expected data providers", notices.providers.size > 10)
    }

    @Test
    fun `every entry names a licence and a source people can open`() {
        notices.dependencies.forEach { notice ->
            assertTrue("${notice.name} has no licence", notice.license.isNotBlank())
            assertTrue("${notice.name} has no version", notice.version.isNotBlank())
            assertTrue("${notice.name} has a non-http url: ${notice.url}", notice.url.startsWith("http"))
        }
        notices.providers.forEach { provider ->
            assertTrue("${provider.name} has no licence", provider.license.isNotBlank())
            assertTrue("${provider.name} has a non-http url: ${provider.url}", provider.url.startsWith("http"))
        }
    }

    @Test
    fun `no entry is left with an unknown licence`() {
        // The generator fails on an unmapped group, but a placeholder slipping
        // into the licence map would defeat that.
        val unknown = notices.dependencies.filter {
            it.license.equals("unknown", ignoreCase = true) || it.license.equals("todo", ignoreCase = true)
        }
        assertEquals(emptyList<OssNotice>(), unknown)
    }

    @Test
    fun `the freenet build drops the proprietary dependencies it does not ship`() {
        val standardOnly = notices.dependencies.filter { it.standardOnly }
        assertTrue("expected some standard-only entries", standardOnly.isNotEmpty())

        val freenet = notices.forFlavor("freenet")

        // Claiming a Play Services or ML Kit dependency the freenet APK does
        // not contain is exactly the kind of inaccuracy F-Droid rejects.
        assertTrue(freenet.dependencies.none { it.standardOnly })
        assertEquals(
            notices.dependencies.size - standardOnly.size,
            freenet.dependencies.size,
        )
        assertEquals(notices.providers, freenet.providers)
    }

    @Test
    fun `the standard build keeps every entry`() {
        assertEquals(notices.dependencies, notices.forFlavor("standard").dependencies)
    }

    @Test
    fun `search matches on both name and licence`() {
        val byLicense = notices.filter("apache")
        assertTrue(byLicense.dependencies.isNotEmpty())
        assertTrue(byLicense.dependencies.all { it.license.contains("Apache", ignoreCase = true) })

        val byName = notices.filter("okhttp")
        assertTrue(byName.dependencies.any { it.name.contains("okhttp") })

        assertTrue(notices.filter("no-such-library-anywhere").isEmpty)
        assertFalse(notices.filter("   ").isEmpty)
    }

    private fun repositoryRoot(): Path {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isDirectory(candidate.resolve("app/src/main"))) {
            candidate = candidate.parent ?: error("Could not locate repository root")
        }
        return candidate
    }
}
