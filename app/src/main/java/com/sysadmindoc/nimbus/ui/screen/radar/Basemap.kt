package com.sysadmindoc.nimbus.ui.screen.radar

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sysadmindoc.nimbus.R
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * A basemap the radar and location picker maps can load. Every style URL the
 * app uses lives in this file, so a provider change is one edit.
 */
internal sealed interface BasemapStyle {
    @get:StringRes
    val attributionRes: Int?

    /** A keyless vector style fetched over the network. */
    data class Remote(
        val url: String,
        @param:StringRes override val attributionRes: Int,
    ) : BasemapStyle

    /** A one-colour style built in memory, so the map still draws with no basemap server. */
    data class Plain(val backgroundColor: String) : BasemapStyle {
        override val attributionRes: Int? get() = null

        val json: String
            get() = """{"version":8,"name":"zeuswatch-plain","sources":{},""" +
                """"layers":[{"id":"background","type":"background",""" +
                """"paint":{"background-color":"$backgroundColor"}}]}"""
    }
}

/**
 * Styles to try in order for the current theme. OpenFreeMap first, VersaTiles
 * when OpenFreeMap is down, and a plain background last so radar tiles still
 * draw. Carto is not in the chain: its basemaps need an API key now.
 */
internal fun basemapFallbackChain(isLightTheme: Boolean): List<BasemapStyle> = if (isLightTheme) {
    listOf(
        BasemapStyle.Remote(OPENFREEMAP_POSITRON, R.string.basemap_attribution_openfreemap),
        BasemapStyle.Remote(VERSATILES_MUTED, R.string.basemap_attribution_versatiles),
        BasemapStyle.Plain(PLAIN_LIGHT_BACKGROUND),
    )
} else {
    listOf(
        BasemapStyle.Remote(OPENFREEMAP_DARK, R.string.basemap_attribution_openfreemap),
        BasemapStyle.Remote(VERSATILES_COLORFUL_DARK, R.string.basemap_attribution_versatiles),
        BasemapStyle.Plain(PLAIN_DARK_BACKGROUND),
    )
}

/**
 * Loads the theme's basemap chain into [loader] once the map exists, loads the
 * other chain when the theme changes, and tells the loader when the screen
 * goes to the background and comes back.
 */
@Composable
internal fun BasemapLoaderEffect(loader: BasemapLoader?, isLightTheme: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(loader, isLightTheme) {
        loader?.load(basemapFallbackChain(isLightTheme))
    }
    DisposableEffect(loader, lifecycleOwner) {
        if (loader == null) return@DisposableEffect onDispose {}
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(loader)
        onDispose {
            lifecycle.removeObserver(loader)
            loader.cancel()
        }
    }
}

/**
 * Loads the first style in a chain that MapLibre accepts, through
 * [BasemapChainRunner]. [onLoaded] runs with the style that loaded and the
 * chain entry it came from. [onUnloaded] runs before the loader replaces a
 * style it already reported, since MapLibre refuses calls on a replaced style.
 *
 * A map that comes back from the background on the plain background, after a
 * dropped connection or a load that ran out in the background, tries the
 * remote styles again while MapLibre says there's a connection.
 */
internal class BasemapLoader(
    private val map: MapLibreMap,
    private val mapView: MapView,
    private val onLoaded: (Style, BasemapStyle) -> Unit,
    private val onUnloaded: () -> Unit,
    private val isConnected: () -> Boolean = { MapLibre.isConnected() == true },
) : DefaultLifecycleObserver {
    private var chain: List<BasemapStyle> = emptyList()
    private var runner: BasemapChainRunner<Style>? = null
    private var failListener: MapView.OnDidFailLoadingMapListener? = null
    private var stopped = false

    /** Starts [chain] from the top, unless it's the chain already loading or loaded. */
    fun load(chain: List<BasemapStyle>) {
        if (chain == this.chain && runner != null) return
        this.chain = chain
        restart()
    }

    /** Stops for good: nothing scheduled can set a style after this. */
    fun cancel() {
        runner?.cancel()
        failListener?.let(mapView::removeOnDidFailLoadingMapListener)
        failListener = null
    }

    override fun onStop(owner: LifecycleOwner) {
        stopped = true
        runner?.pause()
    }

    override fun onStart(owner: LifecycleOwner) {
        // A lifecycle that is already started reports onStart as soon as the
        // loader is added; only a return from the background counts.
        if (!stopped) return
        stopped = false
        val current = runner ?: return
        if (current.loaded is BasemapStyle.Plain && isConnected()) restart() else current.resume()
    }

    private fun restart() {
        cancel()
        onUnloaded()
        lateinit var listener: MapView.OnDidFailLoadingMapListener
        lateinit var next: BasemapChainRunner<Style>
        next = BasemapChainRunner(
            chain = chain,
            load = { basemap, onStyle ->
                val builder = when (basemap) {
                    is BasemapStyle.Remote -> Style.Builder().fromUri(basemap.url)
                    is BasemapStyle.Plain -> Style.Builder().fromJson(basemap.json)
                }
                map.setStyle(builder) { style -> onStyle(style) }
            },
            // Runs the next step outside MapLibre's callbacks rather than re-entering setStyle from one.
            schedule = { delayMs, step -> mapView.postDelayed({ if (!mapView.isDestroyed) step() }, delayMs) },
            onLoaded = { style, basemap ->
                mapView.removeOnDidFailLoadingMapListener(listener)
                onLoaded(style, basemap)
            },
            timeoutMs = { if (isConnected()) BASEMAP_ONLINE_TIMEOUT_MS else BASEMAP_OFFLINE_TIMEOUT_MS },
        )
        listener = MapView.OnDidFailLoadingMapListener { message ->
            next.failed(message)
            if (next.isSettled) mapView.removeOnDidFailLoadingMapListener(listener)
        }
        runner = next
        failListener = listener
        mapView.addOnDidFailLoadingMapListener(listener)
        if (stopped) next.pause()
        next.start()
    }
}

/**
 * The stepping behind [BasemapLoader], kept free of MapLibre so it can be
 * tested. Every call arrives on the main thread. [load] starts one style and
 * reports it through its callback, and [schedule] runs a step later.
 *
 * A style that fails to download or parse moves on to the next one, and so
 * does a remote style that hasn't answered within [timeoutMs]: while MapLibre
 * thinks the device is offline it holds a request open without ever failing
 * it, and the plain background at the end needs no network. The wait only
 * counts while the map runs, because MapLibre pauses its requests while the
 * map is stopped.
 */
internal class BasemapChainRunner<S>(
    private val chain: List<BasemapStyle>,
    private val load: (BasemapStyle, (S) -> Unit) -> Unit,
    private val schedule: (delayMs: Long, step: () -> Unit) -> Unit,
    private val onLoaded: (S, BasemapStyle) -> Unit,
    /** Read each time a wait starts, so it follows the connection as it changes. */
    private val timeoutMs: () -> Long,
) {
    init {
        require(chain.isNotEmpty()) { "Basemap chain is empty" }
    }

    private var index = 0
    private var stepPending = false
    private var paused = false

    /** Bumped for every timeout armed, so a timeout from before a pause is ignored. */
    private var timeoutToken = 0

    var isSettled = false
        private set

    /** The chain entry that loaded, once one has. */
    var loaded: BasemapStyle? = null
        private set

    fun start() = attempt()

    /** The current style failed to download or parse. */
    fun failed(reason: String) {
        // The timeout and MapLibre's own error can both report the same style
        // before the next one starts; only the first moves the chain on.
        if (isSettled || stepPending) return
        Log.w(BASEMAP_TAG, "Basemap ${chain[index]} failed to load: $reason")
        if (index >= chain.lastIndex) {
            isSettled = true
            return
        }
        index++
        stepPending = true
        schedule(0L) {
            stepPending = false
            if (!isSettled) attempt()
        }
    }

    /** The map stopped: the wait for a remote style stops counting. */
    fun pause() {
        paused = true
        timeoutToken++
    }

    /** The map runs again: the style being fetched gets a full wait from now. */
    fun resume() {
        if (!paused) return
        paused = false
        if (!isSettled && !stepPending) armTimeout()
    }

    /** Nothing reported after this, and no further style is tried. */
    fun cancel() {
        isSettled = true
        timeoutToken++
    }

    private fun attempt() {
        val attemptIndex = index
        val basemap = chain[attemptIndex]
        load(basemap) { style ->
            // A style that answers after the chain moved past it is never shown.
            if (isSettled || attemptIndex != index) return@load
            isSettled = true
            loaded = basemap
            onLoaded(style, basemap)
        }
        armTimeout()
    }

    private fun armTimeout() {
        val attemptIndex = index
        if (paused || isSettled || chain[attemptIndex] !is BasemapStyle.Remote) return
        val token = ++timeoutToken
        val delayMs = timeoutMs()
        schedule(delayMs) {
            if (!isSettled && token == timeoutToken && attemptIndex == index) {
                failed("no answer in $delayMs ms")
            }
        }
    }
}

/** Short enough that a start with no connection reaches the plain background quickly. */
internal const val BASEMAP_OFFLINE_TIMEOUT_MS = 8_000L

/**
 * With a connection, MapLibre's HTTP client fails a refused, unreachable or
 * stalled request itself (10 s connect and read timeouts), so this is a
 * backstop for what it never times out: a hanging DNS lookup, a server that
 * trickles bytes, or the moment Android reports a connection before MapLibre
 * does. Waiting the offline time here would give up on a slow link that's
 * still delivering the style.
 */
internal const val BASEMAP_ONLINE_TIMEOUT_MS = 30_000L

private const val BASEMAP_TAG = "Basemap"
private const val OPENFREEMAP_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val OPENFREEMAP_POSITRON = "https://tiles.openfreemap.org/styles/positron"
private const val VERSATILES_COLORFUL_DARK = "https://tiles.versatiles.org/assets/styles/colorful-dark/style.json"
private const val VERSATILES_MUTED = "https://tiles.versatiles.org/assets/styles/muted/style.json"
private const val PLAIN_DARK_BACKGROUND = "#01070D"
private const val PLAIN_LIGHT_BACKGROUND = "#F3F6FA"
