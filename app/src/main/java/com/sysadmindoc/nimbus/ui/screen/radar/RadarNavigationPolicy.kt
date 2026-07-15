package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.repository.RadarProvider
import java.net.URI
import java.util.Locale

internal enum class RadarNavigationDecision {
    LOAD_IN_WEBVIEW,
    OPEN_EXTERNAL_HTTPS,
    BLOCK,
}

/**
 * Keeps the JavaScript-enabled radar surface on trusted HTTPS origins.
 *
 * Non-allowlisted HTTPS links may leave the WebView only when they are an
 * explicit, user-initiated main-frame navigation. Redirects and subframes can
 * never launch another app.
 */
internal object RadarNavigationPolicy {
    private val windyHosts = setOf(
        "embed.windy.com",
        "openstreetmap.org",
        "cartocdn.com",
    )

    private val nwsHosts = setOf(
        "radar.weather.gov",
        "weather.gov",
        "noaa.gov",
        "ncep.noaa.gov",
        "digitalgov.gov",
    )

    fun evaluate(
        provider: RadarProvider,
        rawUrl: String,
        isForMainFrame: Boolean,
        hasUserGesture: Boolean,
    ): RadarNavigationDecision {
        val uri = parseHttpsUri(rawUrl) ?: return RadarNavigationDecision.BLOCK
        val allowedHosts = when (provider) {
            RadarProvider.WINDY_WEBVIEW -> windyHosts
            RadarProvider.NWS_WEBVIEW,
            RadarProvider.NWS_STANDARD_WEBVIEW -> nwsHosts
            RadarProvider.NATIVE_MAPLIBRE,
            RadarProvider.LIBREWXR_NATIVE -> emptySet()
        }

        if (allowedHosts.any { uri.host.isHostOrSubdomainOf(it) }) {
            return RadarNavigationDecision.LOAD_IN_WEBVIEW
        }

        return if (isForMainFrame && hasUserGesture) {
            RadarNavigationDecision.OPEN_EXTERNAL_HTTPS
        } else {
            RadarNavigationDecision.BLOCK
        }
    }

    fun canLoadInitialUrl(provider: RadarProvider, rawUrl: String): Boolean =
        evaluate(
            provider = provider,
            rawUrl = rawUrl,
            isForMainFrame = true,
            hasUserGesture = false,
        ) == RadarNavigationDecision.LOAD_IN_WEBVIEW

    private fun parseHttpsUri(rawUrl: String): TrustedHttpsUri? {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != HTTPS_PORT) return null
        val host = uri.host
            ?.lowercase(Locale.ROOT)
            ?.removeSuffix(".")
            ?.takeIf(String::isNotBlank)
            ?: return null
        return TrustedHttpsUri(host)
    }

    private fun String.isHostOrSubdomainOf(domain: String): Boolean =
        this == domain || endsWith(".$domain")

    private data class TrustedHttpsUri(val host: String)

    private const val HTTPS_PORT = 443
}
