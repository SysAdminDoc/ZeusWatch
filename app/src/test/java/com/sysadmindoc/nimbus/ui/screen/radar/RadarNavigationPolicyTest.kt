package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.repository.RadarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RadarNavigationPolicyTest {

    @Test
    fun `only provider HTTPS hosts load inside the WebView`() {
        assertLoads(RadarProvider.WINDY_WEBVIEW, "https://embed.windy.com/embed.html")
        assertLoads(RadarProvider.WINDY_WEBVIEW, "https://a.basemaps.cartocdn.com/dark_all/1/2/3.png")
        assertLoads(RadarProvider.WINDY_WEBVIEW, "https://tile.openstreetmap.org/1/2/3.png")
        assertLoads(RadarProvider.NWS_WEBVIEW, "https://radar.weather.gov/")
        assertLoads(RadarProvider.NWS_STANDARD_WEBVIEW, "https://api.weather.gov/alerts")
        assertLoads(RadarProvider.NWS_WEBVIEW, "https://www.noaa.gov/")
    }

    @Test
    fun `host prefix suffix user-info and port attacks do not pass the allowlist`() {
        listOf(
            "https://embed.windy.com.evil.example/",
            "https://evil-embed.windy.com/",
            "https://evilopenstreetmap.org/",
            "https://cartocdn.com.evil.example/",
            "https://weather.gov.evil.example/",
            "https://evilweather.gov/",
            "https://embed.windy.com@evil.example/",
            "https://embed.windy.com:8443/",
        ).forEach { url ->
            assertEquals(
                url,
                RadarNavigationDecision.BLOCK,
                evaluate(RadarProvider.WINDY_WEBVIEW, url),
            )
            assertFalse(RadarNavigationPolicy.canLoadInitialUrl(RadarProvider.WINDY_WEBVIEW, url))
        }
    }

    @Test
    fun `non HTTPS main frame schemes are always blocked`() {
        listOf(
            "http://embed.windy.com/",
            "intent://embed.windy.com/#Intent;scheme=https;end",
            "file:///data/user/0/secrets",
            "content://com.example.provider/item/1",
            "javascript:alert(1)",
            "data:text/html,hello",
            "blob:https://embed.windy.com/id",
            "about:blank",
        ).forEach { url ->
            assertEquals(
                url,
                RadarNavigationDecision.BLOCK,
                evaluate(
                    provider = RadarProvider.WINDY_WEBVIEW,
                    url = url,
                    isMainFrame = true,
                    hasGesture = true,
                ),
            )
        }
    }

    @Test
    fun `only explicit main frame HTTPS links may open externally`() {
        val externalUrl = "https://www.windy.com/privacy"

        assertEquals(
            RadarNavigationDecision.OPEN_EXTERNAL_HTTPS,
            evaluate(
                provider = RadarProvider.WINDY_WEBVIEW,
                url = externalUrl,
                isMainFrame = true,
                hasGesture = true,
            ),
        )
        assertEquals(
            RadarNavigationDecision.BLOCK,
            evaluate(
                provider = RadarProvider.WINDY_WEBVIEW,
                url = externalUrl,
                isMainFrame = true,
                hasGesture = false,
            ),
        )
        assertEquals(
            RadarNavigationDecision.BLOCK,
            evaluate(
                provider = RadarProvider.WINDY_WEBVIEW,
                url = externalUrl,
                isMainFrame = false,
                hasGesture = true,
            ),
        )
    }

    @Test
    fun `native radar providers never receive a WebView origin`() {
        assertEquals(
            RadarNavigationDecision.BLOCK,
            evaluate(RadarProvider.NATIVE_MAPLIBRE, "https://embed.windy.com/embed.html"),
        )
        assertEquals(
            RadarNavigationDecision.BLOCK,
            evaluate(RadarProvider.LIBREWXR_NATIVE, "https://embed.windy.com/embed.html"),
        )
    }

    @Test
    fun `API 37 compatibility keeps the default WebView user agent`() {
        val radarScreen = readAppSource(
            "src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarScreen.kt",
        )

        assertFalse(radarScreen.contains("userAgentString"))
        assertFalse(radarScreen.contains("setUserAgentString"))
        assertFalse(radarScreen.contains("Mozilla/"))
        assertTrue(radarScreen.contains("RadarNavigationPolicy.canLoadInitialUrl"))
    }

    private fun assertLoads(provider: RadarProvider, url: String) {
        assertEquals(RadarNavigationDecision.LOAD_IN_WEBVIEW, evaluate(provider, url))
        assertTrue(RadarNavigationPolicy.canLoadInitialUrl(provider, url))
    }

    private fun evaluate(
        provider: RadarProvider,
        url: String,
        isMainFrame: Boolean = true,
        hasGesture: Boolean = false,
    ): RadarNavigationDecision = RadarNavigationPolicy.evaluate(
        provider = provider,
        rawUrl = url,
        isForMainFrame = isMainFrame,
        hasUserGesture = hasGesture,
    )

    private fun readAppSource(relativePath: String): String =
        appDir().resolve(relativePath).toFile().readText()

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
