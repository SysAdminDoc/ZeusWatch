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
        // Derived from the resolved runtime classpath, not the version
        // catalog: the catalog listed 71 entries while the app actually
        // ships around 300, and included test-only artifacts it does not.
        assertTrue("expected the full runtime classpath", notices.dependencies.size > 250)
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
    fun `every dependency carries a real resolved version`() {
        // "managed" was the old catalog's placeholder for BOM-managed
        // artifacts, which is not a version anyone can look up.
        val unresolved = notices.dependencies.filter {
            it.version == "managed" || it.version == "unknown"
        }
        assertEquals(emptyList<OssNotice>(), unresolved)
    }

    @Test
    fun `test-only artifacts are not claimed as shipped`() {
        val testOnly = listOf(
            "junit:junit",
            "io.mockk:mockk",
            "org.robolectric:robolectric",
            "app.cash.turbine:turbine",
            "androidx.test.espresso:espresso-core",
        )
        val claimed = notices.dependencies.map { it.name }.filter { it in testOnly }
        assertEquals(emptyList<String>(), claimed)
    }

    @Test
    fun `the Firebase stack is present and marked standard-only`() {
        // Declared as string literals, so the catalog-driven generator missed
        // all of it while the standard APK shipped every one.
        val firebase = notices.dependencies.filter { it.name.startsWith("com.google.firebase:") }

        assertTrue("Firebase must be attributed", firebase.size >= 10)
        assertTrue("Firebase must not be claimed by freenet", firebase.all { it.standardOnly })
    }

    @Test
    fun `each artifact appears once`() {
        val names = notices.dependencies.map { it.name }
        assertEquals(names.size, names.distinct().size)
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
