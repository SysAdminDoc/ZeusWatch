package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.lifecycle.LifecycleOwner
import com.sysadmindoc.nimbus.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Carto's keyless basemaps now need an API key, so the maps load OpenFreeMap,
 * fall back to VersaTiles, and end on a plain background that needs no server.
 */
class BasemapTest {

    @Test
    fun `the dark theme starts on OpenFreeMap dark`() {
        val first = basemapFallbackChain(isLightTheme = false).first()
        assertEquals(
            BasemapStyle.Remote("https://tiles.openfreemap.org/styles/dark", R.string.basemap_attribution_openfreemap),
            first,
        )
    }

    @Test
    fun `the light theme starts on OpenFreeMap positron`() {
        val first = basemapFallbackChain(isLightTheme = true).first()
        assertEquals(
            BasemapStyle.Remote("https://tiles.openfreemap.org/styles/positron", R.string.basemap_attribution_openfreemap),
            first,
        )
    }

    @Test
    fun `each chain has a second provider and ends on a plain background`() {
        listOf(false, true).forEach { light ->
            val chain = basemapFallbackChain(light)
            val remotes = chain.filterIsInstance<BasemapStyle.Remote>()
            assertTrue("theme light=$light needs two remote styles", remotes.size >= 2)
            assertEquals(
                "theme light=$light: the fallback must come from another host",
                remotes.size,
                remotes.map { java.net.URI(it.url).host }.distinct().size,
            )
            assertTrue("theme light=$light must end on Plain", chain.last() is BasemapStyle.Plain)
            assertEquals(1, chain.count { it is BasemapStyle.Plain })
        }
    }

    @Test
    fun `no chain asks Carto for anything`() {
        listOf(false, true).flatMap { basemapFallbackChain(it) }
            .filterIsInstance<BasemapStyle.Remote>()
            .forEach { assertFalse(it.url, it.url.contains("carto", ignoreCase = true)) }
    }

    @Test
    fun `every remote style credits its own provider and plain credits none`() {
        listOf(false, true).flatMap { basemapFallbackChain(it) }.forEach { style ->
            when (style) {
                is BasemapStyle.Remote -> {
                    val expected = when (java.net.URI(style.url).host) {
                        "tiles.openfreemap.org" -> R.string.basemap_attribution_openfreemap
                        "tiles.versatiles.org" -> R.string.basemap_attribution_versatiles
                        else -> error("no credit known for ${style.url}")
                    }
                    assertEquals(style.url, expected, style.attributionRes)
                }
                is BasemapStyle.Plain -> assertEquals(null, style.attributionRes)
            }
        }
    }

    @Test
    fun `the plain style is a valid MapLibre style with one background layer`() {
        listOf(false, true).forEach { light ->
            val plain = basemapFallbackChain(light).last() as BasemapStyle.Plain
            val root = Json.parseToJsonElement(plain.json).jsonObject
            assertEquals(8, root.getValue("version").jsonPrimitive.int)
            assertTrue(root.getValue("sources").jsonObject.isEmpty())
            val layers = root.getValue("layers").jsonArray
            assertEquals(1, layers.size)
            val layer = layers.single().jsonObject
            assertEquals("background", layer.getValue("type").jsonPrimitive.content)
            val color = layer.getValue("paint").jsonObject.getValue("background-color").jsonPrimitive.content
            assertTrue("bad colour $color", Regex("#[0-9A-Fa-f]{6}").matches(color))
        }
    }

    @Test
    fun `only Basemap kt names a map style`() {
        val styleUrl = Regex("""https?://[^"\s]*(style\.json|/styles/|/gl/|\.json["?])""")
        val styleCall = Regex("""(setStyle|fromUri|fromUrl)\(\s*"""")
        val src = appDir().resolve("src")
        val productionSources = Files.list(src).use { sets ->
            sets.filter { !it.name.startsWith("test") && !it.name.startsWith("androidTest") }
                .flatMap { set -> listOf(set.resolve("java"), set.resolve("kotlin")).stream() }
                .filter { Files.isDirectory(it) }
                .toList()
        }
        assertTrue("no source sets found under $src", productionSources.isNotEmpty())
        val offenders = mutableListOf<String>()
        productionSources.forEach { root ->
            Files.walk(root).use { paths ->
                paths.filter { it.extension in setOf("kt", "java") && it.name != "Basemap.kt" }.forEach { file ->
                    val text = file.readText()
                    if (styleUrl.containsMatchIn(text) || styleCall.containsMatchIn(text) || text.contains("Style.Builder()")) {
                        offenders += file.invariantSeparatorsPathString.substringAfter("/src/")
                    }
                }
            }
        }
        assertEquals("map styles must come from Basemap.kt", emptyList<String>(), offenders)

        // The patterns have to catch the style URLs Basemap.kt really uses, or the scan proves nothing.
        val basemapFile = src.resolve("main/java/com/sysadmindoc/nimbus/ui/screen/radar/Basemap.kt").readText()
        val urlsInBasemap = Regex(""""(https?://[^"]+)"""").findAll(basemapFile).map { it.groupValues[1] }.toList()
        assertTrue("no style URLs found in Basemap.kt", urlsInBasemap.size >= 4)
        urlsInBasemap.forEach { url ->
            assertTrue("the scan misses $url", styleUrl.containsMatchIn("\"$url\""))
        }
        assertTrue(basemapFile.contains("Style.Builder()"))
    }

    @Test
    fun `a timeout and an error for the same style move the chain on once`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.runner.failed("loading style failed")
        map.runNext(0L)
        assertEquals("VersaTiles must not be skipped", listOf(map.chain[0], map.chain[1]), map.loads)

        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.runner.failed("loading style failed")
        map.runNext(0L)
        map.answer(2)
        assertEquals(map.chain, map.loads)
        assertEquals("the map must still get the plain style", listOf("style-2" to map.chain[2]), map.loaded)
    }

    @Test
    fun `the wait for a style stops while the map is in the background`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        map.runner.pause()
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        assertEquals("a timeout that ran out in the background must not count", listOf(map.chain[0]), map.loads)
        assertTrue(map.scheduled.isEmpty())

        map.runner.resume()
        map.answer(0)
        assertEquals(listOf("style-0" to map.chain[0]), map.loaded)
    }

    @Test
    fun `a style still loading when the map comes back gets a full wait again`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = true))
        map.runner.start()
        map.runner.pause()
        map.runner.resume()
        // The timeout armed before the pause is stale; only the new one counts.
        assertEquals(2, map.scheduled.count { it.first == BASEMAP_OFFLINE_TIMEOUT_MS })
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        assertTrue("the stale timeout must do nothing", map.scheduled.none { it.first == 0L })
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.runNext(0L)
        assertEquals(listOf(map.chain[0], map.chain[1]), map.loads)
    }

    @Test
    fun `a cancelled chain reports nothing and tries nothing more`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        map.runner.cancel()
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.answer(0)
        assertEquals(listOf(map.chain[0]), map.loads)
        assertTrue(map.loaded.isEmpty())
    }

    @Test
    fun `a map back from the background on the plain background tries the basemaps again`() {
        val maplibre = FakeMapLibre(isConnected = true)
        val loader = maplibre.loader()
        loader.load(basemapFallbackChain(isLightTheme = false))
        maplibre.timeOut()
        maplibre.timeOut()
        maplibre.answerLatest()
        assertEquals("loaded:plain", maplibre.events.last())

        loader.onStop(owner)
        loader.onStart(owner)
        assertEquals(
            listOf("unloaded", "request:https://tiles.openfreemap.org/styles/dark"),
            maplibre.events.takeLast(2),
        )
    }

    @Test
    fun `an offline map back from the background keeps the plain background`() {
        val maplibre = FakeMapLibre(isConnected = false)
        val loader = maplibre.loader()
        loader.load(basemapFallbackChain(isLightTheme = false))
        maplibre.timeOut()
        maplibre.timeOut()
        maplibre.answerLatest()
        val before = maplibre.events.toList()

        loader.onStop(owner)
        loader.onStart(owner)
        assertEquals(before, maplibre.events)
    }

    @Test
    fun `a map that loaded a remote basemap keeps it when it comes back`() {
        val maplibre = FakeMapLibre(isConnected = true)
        val loader = maplibre.loader()
        loader.load(basemapFallbackChain(isLightTheme = true))
        maplibre.answerLatest()
        // An already started lifecycle reports onStart when the loader is added.
        loader.onStart(owner)
        loader.onStop(owner)
        loader.onStart(owner)
        assertEquals(
            listOf("unloaded", "request:https://tiles.openfreemap.org/styles/positron", "loaded:https://tiles.openfreemap.org/styles/positron"),
            maplibre.events,
        )
    }

    @Test
    fun `a theme change swaps the chain and the same theme leaves it alone`() {
        val maplibre = FakeMapLibre(isConnected = true)
        val loader = maplibre.loader()
        loader.load(basemapFallbackChain(isLightTheme = false))
        maplibre.answerLatest()
        loader.load(basemapFallbackChain(isLightTheme = false))
        assertEquals(3, maplibre.events.size)

        loader.load(basemapFallbackChain(isLightTheme = true))
        assertEquals(
            listOf("unloaded", "request:https://tiles.openfreemap.org/styles/positron"),
            maplibre.events.takeLast(2),
        )
        maplibre.answerLatest()
        assertEquals("loaded:https://tiles.openfreemap.org/styles/positron", maplibre.events.last())
    }

    @Test
    fun `the style after a failed one stops the listening once it loads`() {
        val maplibre = FakeMapLibre(isConnected = true)
        val loader = maplibre.loader()
        loader.load(basemapFallbackChain(isLightTheme = false))
        maplibre.failListener!!.onDidFailLoadingMap("HTTP 503")
        maplibre.runPosted(0L)
        assertEquals("request:https://tiles.versatiles.org/assets/styles/colorful-dark/style.json", maplibre.events.last())
        maplibre.answerLatest()
        verify { maplibre.mapView.removeOnDidFailLoadingMapListener(maplibre.failListener!!) }
    }

    @Test
    fun `a chain where every style fails stops listening after the last one`() {
        val maplibre = FakeMapLibre(isConnected = true)
        val loader = maplibre.loader()
        loader.load(basemapFallbackChain(isLightTheme = true))
        val listener = maplibre.failListener!!
        listener.onDidFailLoadingMap("HTTP 503")
        maplibre.runPosted(0L)
        listener.onDidFailLoadingMap("HTTP 503")
        maplibre.runPosted(0L)
        verify(exactly = 0) { maplibre.mapView.removeOnDidFailLoadingMapListener(any()) }

        listener.onDidFailLoadingMap("bad style")
        verify(exactly = 1) { maplibre.mapView.removeOnDidFailLoadingMapListener(listener) }
        assertTrue("nothing loaded", maplibre.events.none { it.startsWith("loaded:") })
    }

    @Test
    fun `a connected map gives a slow style longer than an offline one`() {
        val online = FakeMapLibre(isConnected = true)
        online.loader().load(basemapFallbackChain(isLightTheme = false))
        assertEquals(listOf(BASEMAP_ONLINE_TIMEOUT_MS), online.postedDelays())

        val offline = FakeMapLibre(isConnected = false)
        offline.loader().load(basemapFallbackChain(isLightTheme = false))
        assertEquals(listOf(BASEMAP_OFFLINE_TIMEOUT_MS), offline.postedDelays())
        assertTrue(BASEMAP_ONLINE_TIMEOUT_MS > BASEMAP_OFFLINE_TIMEOUT_MS)
    }

    @Test
    fun `each wait follows the connection at the time it starts`() {
        val maplibre = FakeMapLibre(isConnected = true)
        maplibre.loader().load(basemapFallbackChain(isLightTheme = false))
        maplibre.isConnected = false
        // The first wait was armed while connected, so it's the long one.
        maplibre.runPosted(BASEMAP_ONLINE_TIMEOUT_MS)
        maplibre.runPosted(0L)
        assertEquals(
            "the second style starts after the connection dropped",
            listOf(BASEMAP_OFFLINE_TIMEOUT_MS),
            maplibre.postedDelays(),
        )
    }

    @Test
    fun `the first style that loads is reported once and nothing else is fetched`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        map.answer(0)

        assertEquals(listOf("style-0" to map.chain[0]), map.loaded)
        assertEquals(listOf(map.chain[0]), map.loads)
        assertTrue(map.runner.isSettled)
    }

    @Test
    fun `a style that fails moves on to the next one`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        map.runner.failed("HTTP 404")
        map.runNext(0L)
        map.answer(1)

        assertEquals(listOf(map.chain[0], map.chain[1]), map.loads)
        assertEquals(listOf("style-1" to map.chain[1]), map.loaded)
    }

    @Test
    fun `remote styles that never answer time out until the plain background draws`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.runNext(0L)
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.runNext(0L)
        map.answer(2)

        assertEquals(map.chain, map.loads)
        assertEquals(listOf("style-2" to map.chain[2]), map.loaded)
        assertTrue("the plain style must not get a timeout", map.scheduled.isEmpty())
    }

    @Test
    fun `the timeout of a style that already failed does nothing`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = true))
        map.runner.start()
        map.runner.failed("HTTP 500")
        map.runNext(0L)
        // The first style's timeout is still queued; the second style is loading.
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)

        assertEquals(listOf(map.chain[0], map.chain[1]), map.loads)
        assertFalse(map.runner.isSettled)
    }

    @Test
    fun `a style that answers after the chain moved past it is ignored`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = true))
        map.runner.start()
        map.runNext(BASEMAP_OFFLINE_TIMEOUT_MS)
        map.runNext(0L)
        map.answer(0)
        assertTrue(map.loaded.isEmpty())

        map.answer(1)
        assertEquals(listOf("style-1" to map.chain[1]), map.loaded)
    }

    @Test
    fun `a failure on the last style settles without loading anything more`() {
        val map = FakeMap(basemapFallbackChain(isLightTheme = false))
        map.runner.start()
        repeat(map.chain.size - 1) {
            map.runner.failed("down")
            map.runNext(0L)
        }
        map.runner.failed("bad JSON")

        assertEquals(map.chain, map.loads)
        assertTrue(map.loaded.isEmpty())
        assertTrue(map.runner.isSettled)
        assertTrue(map.scheduled.none { it.first == 0L })
    }

    private class FakeMap(val chain: List<BasemapStyle>) {
        val loads = mutableListOf<BasemapStyle>()
        private val callbacks = mutableListOf<(String) -> Unit>()
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val loaded = mutableListOf<Pair<String, BasemapStyle>>()
        val runner = BasemapChainRunner<String>(
            chain = chain,
            load = { basemap, onStyle ->
                loads += basemap
                callbacks += onStyle
            },
            schedule = { delayMs, step -> scheduled += delayMs to step },
            onLoaded = { style, basemap -> loaded += style to basemap },
            timeoutMs = { BASEMAP_OFFLINE_TIMEOUT_MS },
        )

        /** MapLibre finishes loading the [attempt]th style it was asked for. */
        fun answer(attempt: Int) = callbacks[attempt]("style-$attempt")

        fun runNext(delayMs: Long) {
            val index = scheduled.indexOfFirst { it.first == delayMs }
            assertTrue("nothing scheduled after $delayMs ms", index >= 0)
            scheduled.removeAt(index).second()
        }
    }

    private val owner = mockk<LifecycleOwner>()

    /** The MapLibre side of [BasemapLoader]: style requests, posted steps and the fail listener. */
    private class FakeMapLibre(var isConnected: Boolean) {
        val map = mockk<MapLibreMap>()
        val mapView = mockk<MapView>(relaxed = true)
        val events = mutableListOf<String>()
        private val pending = mutableListOf<Pair<String, Style.OnStyleLoaded>>()
        private val posted = mutableListOf<Pair<Long, Runnable>>()
        var failListener: MapView.OnDidFailLoadingMapListener? = null

        init {
            every { map.setStyle(any<Style.Builder>(), any<Style.OnStyleLoaded>()) } answers {
                val name = firstArg<Style.Builder>().uri ?: "plain"
                events += "request:$name"
                pending += name to secondArg()
            }
            every { mapView.postDelayed(any(), any()) } answers {
                posted += secondArg<Long>() to firstArg<Runnable>()
                true
            }
            every { mapView.isDestroyed } returns false
            every { mapView.addOnDidFailLoadingMapListener(any()) } answers { failListener = firstArg() }
        }

        fun loader() = BasemapLoader(
            map = map,
            mapView = mapView,
            onLoaded = { _, basemap ->
                events += "loaded:" + ((basemap as? BasemapStyle.Remote)?.url ?: "plain")
            },
            onUnloaded = { events += "unloaded" },
            isConnected = { isConnected },
        )

        /** MapLibre finishes the most recent style request. */
        fun answerLatest() = pending.last().second.onStyleLoaded(mockk())

        /** The pending remote style runs out of time and the next one starts. */
        fun timeOut() {
            runPosted(if (isConnected) BASEMAP_ONLINE_TIMEOUT_MS else BASEMAP_OFFLINE_TIMEOUT_MS)
            runPosted(0L)
        }

        fun postedDelays(): List<Long> = posted.map { it.first }

        fun runPosted(delayMs: Long) {
            val index = posted.indexOfFirst { it.first == delayMs }
            assertTrue("nothing posted after $delayMs ms", index >= 0)
            posted.removeAt(index).second.run()
        }
    }

    private fun appDir(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return listOf(
            cwd,
            cwd.resolve("app"),
            cwd.parent?.resolve("app"),
        ).filterNotNull().first { candidate ->
            Files.exists(candidate.resolve("src/main/AndroidManifest.xml"))
        }
    }
}
