package com.sysadmindoc.nimbus.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sysadmindoc.nimbus.data.model.IconMapping
import com.sysadmindoc.nimbus.data.model.IconPack
import com.sysadmindoc.nimbus.data.model.IconPackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val iconPackJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Discovers and loads custom weather icon packs from bundled assets and external APKs.
 *
 * ## Bundled packs
 * Place a directory under `assets/iconpacks/` containing a `manifest.json` and the icon files.
 * See [assets/iconpacks/README.md] for the manifest format.
 *
 * ## External packs
 * Third-party apps declare an intent filter with action [ACTION_ICON_PACK].
 * Their `assets/nimbus-iconpack/manifest.json` follows the same schema.
 */
@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    companion object {
        private const val TAG = "IconPackManager"
        const val ACTION_ICON_PACK = "com.sysadmindoc.nimbus.ICON_PACK"
        private const val BUNDLED_ROOT = "iconpacks"
        private const val EXTERNAL_ASSET_ROOT = "nimbus-iconpack"
        private const val MANIFEST_FILE = "manifest.json"
    }

    // Cached results; cleared on refresh.
    private var cachedPacks: List<IconPack>? = null

    // Decoded-bitmap cache so a given pack icon is only decoded from disk once.
    // Small bounded set (~weather codes x day/night per pack), cleared on refresh.
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    private fun cacheKey(packId: String, wmoCode: Int, isDay: Boolean): String =
        "$packId|$wmoCode|$isDay"

    /**
     * Synchronous peek into the decoded-bitmap cache — cheap enough to call from
     * the composition thread. Returns null on a miss (decode via [loadIcon] off
     * the main thread).
     */
    fun peekCachedIcon(packId: String, wmoCode: Int, isDay: Boolean): Bitmap? =
        bitmapCache[cacheKey(packId, wmoCode, isDay)]

    // -------------------------------------------------------------------------
    // Discovery
    // -------------------------------------------------------------------------

    /**
     * Returns all discovered icon packs (bundled + external). Results are cached
     * until [refreshPacks] is called.
     */
    fun getAvailablePacks(context: Context = appContext): List<IconPack> {
        cachedPacks?.let { return it }
        val packs = mutableListOf<IconPack>()
        packs.addAll(discoverBundledPacks(context))
        packs.addAll(discoverExternalPacks(context))
        cachedPacks = packs
        return packs
    }

    /** Force re-scan on next [getAvailablePacks] call. */
    fun refreshPacks() {
        cachedPacks = null
        bitmapCache.clear()
    }

    /** Find a pack by its [id], or null. */
    fun findPack(id: String, context: Context = appContext): IconPack? =
        getAvailablePacks(context).firstOrNull { it.id == id }

    // -------------------------------------------------------------------------
    // Icon loading
    // -------------------------------------------------------------------------

    /**
     * Load the icon for a given WMO [wmoCode] and day/night flag from [pack].
     *
     * @return A [Bitmap] on success, or `null` if the mapping or file is missing.
     */
    fun loadIcon(
        context: Context = appContext,
        pack: IconPack,
        wmoCode: Int,
        isDay: Boolean,
    ): Bitmap? {
        bitmapCache[cacheKey(pack.id, wmoCode, isDay)]?.let { return it }
        val mapping = pack.mappings[wmoCode] ?: return null
        val filename = if (isDay) mapping.dayIcon else mapping.nightIcon
        return try {
            val stream = openIconStream(context, pack, filename) ?: return null
            stream.use { decodeIconBitmap(it) }
                ?.also { bitmapCache[cacheKey(pack.id, wmoCode, isDay)] = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon $filename from pack ${pack.id}", e)
            null
        }
    }

    // -------------------------------------------------------------------------
    // Internal — bundled packs
    // -------------------------------------------------------------------------

    private fun discoverBundledPacks(context: Context): List<IconPack> {
        val assets = context.assets
        val dirs = try {
            assets.list(BUNDLED_ROOT) ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        return dirs.mapNotNull { dir ->
            try {
                val manifestPath = "$BUNDLED_ROOT/$dir/$MANIFEST_FILE"
                val json = assets.open(manifestPath).use { readBoundedUtf8(it, IconPackLimits.MAX_MANIFEST_BYTES) }
                parseIconPackManifest(json, IconPackSource.Bundled(assetPath = "$BUNDLED_ROOT/$dir"))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping bundled pack dir '$dir': ${e.message}")
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal — external APK packs
    // -------------------------------------------------------------------------

    private fun discoverExternalPacks(context: Context): List<IconPack> {
        val pm = context.packageManager
        val intent = Intent(ACTION_ICON_PACK)
        @Suppress("DEPRECATION")
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        return resolveInfos.mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            try {
                val externalRes = pm.getResourcesForApplication(pkg)
                // External packs store their manifest at assets/nimbus-iconpack/manifest.json
                val stream = externalRes.assets.open("$EXTERNAL_ASSET_ROOT/$MANIFEST_FILE")
                val json = stream.use { readBoundedUtf8(it, IconPackLimits.MAX_MANIFEST_BYTES) }
                parseIconPackManifest(json, IconPackSource.External(packageName = pkg))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping external pack '$pkg': ${e.message}")
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal — manifest parsing
    // -------------------------------------------------------------------------

    private fun decodeIconBitmap(input: InputStream): Bitmap? {
        val bytes = readBoundedBytes(input, IconPackLimits.MAX_ICON_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth > IconPackLimits.MAX_ICON_DIMENSION_PX ||
            bounds.outHeight > IconPackLimits.MAX_ICON_DIMENSION_PX
        ) {
            return null
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // -------------------------------------------------------------------------
    // Internal — stream helpers
    // -------------------------------------------------------------------------

    private fun openIconStream(
        context: Context,
        pack: IconPack,
        filename: String,
    ): InputStream? = when (val src = pack.source) {
        is IconPackSource.Bundled -> {
            context.assets.open("${src.assetPath}/$filename")
        }
        is IconPackSource.External -> {
            val externalRes = context.packageManager.getResourcesForApplication(src.packageName)
            externalRes.assets.open("$EXTERNAL_ASSET_ROOT/$filename")
        }
    }
}

internal fun parseIconPackManifest(json: String, source: IconPackSource): IconPack {
    require(json.toByteArray(Charsets.UTF_8).size <= IconPackLimits.MAX_MANIFEST_BYTES) {
        "Icon-pack manifest is too large."
    }
    val obj = iconPackJson.parseToJsonElement(json).jsonObject
    val id = obj.requiredTrimmedString("id")
    val name = obj.requiredTrimmedString("name")
    val author = obj.optTrimmedString("author")
    val format = obj.optTrimmedString("format", "png").lowercase(Locale.ROOT)
    require(format in IconPackLimits.SUPPORTED_BITMAP_FORMATS) {
        "Icon-pack format '$format' is not supported."
    }

    val mappingsObj = obj["mappings"]?.jsonObject ?: error("Icon-pack manifest has no mappings.")
    require(mappingsObj.size <= IconPackLimits.MAX_MAPPINGS) {
        "Icon-pack manifest contains too many mappings."
    }
    val mappings = mutableMapOf<Int, IconMapping>()
    for ((key, rawEntry) in mappingsObj) {
        val code = key.toIntOrNull() ?: error("Invalid WMO code '$key'.")
        require(code in 0..99) { "WMO code '$key' is outside the supported range." }
        val entry = rawEntry.jsonObject
        mappings[code] = IconMapping(
            dayIcon = sanitizeIconFilename(entry.requiredTrimmedString("day")),
            nightIcon = sanitizeIconFilename(entry.requiredTrimmedString("night")),
        )
    }
    require(mappings.isNotEmpty()) { "Icon-pack manifest has no mappings." }

    return IconPack(
        id = id,
        name = name,
        author = author,
        format = format,
        source = source,
        mappings = mappings,
    )
}

private object IconPackLimits {
    const val MAX_MANIFEST_BYTES = 128 * 1024
    const val MAX_ICON_BYTES = 1024 * 1024
    const val MAX_ICON_DIMENSION_PX = 512
    const val MAX_TEXT_FIELD_CHARS = 96
    const val MAX_FILENAME_CHARS = 160
    const val MAX_MAPPINGS = 256
    val SUPPORTED_BITMAP_FORMATS = setOf("png", "webp")
}

private fun JsonObject.requiredTrimmedString(name: String): String =
    (this[name]?.jsonPrimitive?.contentOrNull ?: error("Icon-pack '$name' is missing.")).trim().also {
        require(it.isNotEmpty()) { "Icon-pack '$name' must not be blank." }
        require(it.length <= IconPackLimits.MAX_TEXT_FIELD_CHARS) {
            "Icon-pack '$name' is too long."
        }
    }

private fun JsonObject.optTrimmedString(name: String, defaultValue: String = ""): String =
    (this[name]?.jsonPrimitive?.contentOrNull ?: defaultValue)
        .trim()
        .take(IconPackLimits.MAX_TEXT_FIELD_CHARS)

private fun sanitizeIconFilename(raw: String): String {
    val normalized = raw.trim().replace('\\', '/')
    require(normalized.isNotEmpty()) { "Icon filename must not be blank." }
    require(normalized.length <= IconPackLimits.MAX_FILENAME_CHARS) { "Icon filename is too long." }
    require(!normalized.startsWith("/")) { "Icon filename must be relative." }
    val segments = normalized.split('/')
    require(segments.none { it.isBlank() || it == "." || it == ".." }) {
        "Icon filename contains unsafe path segments."
    }
    return normalized
}

private fun readBoundedUtf8(input: InputStream, maxBytes: Int): String =
    String(readBoundedBytes(input, maxBytes), Charsets.UTF_8)

private fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "Byte limit must be positive." }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        totalBytes += read
        require(totalBytes <= maxBytes) { "Input exceeds the ${maxBytes / 1024} KB limit." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
