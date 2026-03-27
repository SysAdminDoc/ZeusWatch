package com.sysadmindoc.nimbus.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Log
import com.sysadmindoc.nimbus.data.model.IconMapping
import com.sysadmindoc.nimbus.data.model.IconPack
import com.sysadmindoc.nimbus.data.model.IconPackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

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
        val mapping = pack.mappings[wmoCode] ?: return null
        val filename = if (isDay) mapping.dayIcon else mapping.nightIcon
        return try {
            val stream = openIconStream(context, pack, filename) ?: return null
            stream.use { BitmapFactory.decodeStream(it) }
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
                val json = assets.open(manifestPath).bufferedReader().use { it.readText() }
                parsePack(json, IconPackSource.Bundled(assetPath = "$BUNDLED_ROOT/$dir"))
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
                val json = stream.bufferedReader().use { it.readText() }
                parsePack(json, IconPackSource.External(packageName = pkg))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping external pack '$pkg': ${e.message}")
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal — manifest parsing
    // -------------------------------------------------------------------------

    private fun parsePack(json: String, source: IconPackSource): IconPack {
        val obj = JSONObject(json)
        val id = obj.getString("id")
        val name = obj.getString("name")
        val author = obj.optString("author", "")
        val format = obj.optString("format", "png")

        val mappingsObj = obj.getJSONObject("mappings")
        val mappings = mutableMapOf<Int, IconMapping>()
        for (key in mappingsObj.keys()) {
            val entry = mappingsObj.getJSONObject(key)
            mappings[key.toInt()] = IconMapping(
                dayIcon = entry.getString("day"),
                nightIcon = entry.getString("night"),
            )
        }

        return IconPack(
            id = id,
            name = name,
            author = author,
            format = format,
            source = source,
            mappings = mappings,
        )
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
