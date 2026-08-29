package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.util.Log
import com.sysadmindoc.nimbus.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OssNotices"
private const val NOTICES_ASSET = "oss_notices.json"
private const val FREENET_FLAVOR = "freenet"

/**
 * Open-source notices, generated from the version catalog by
 * `tools/generate_oss_notices.py` and shipped as an asset.
 *
 * Both flavors package the same file; the `freenet` build filters out the
 * proprietary dependencies it does not actually ship, so its notices list
 * never claims a Play Services or ML Kit dependency the APK does not contain.
 */
@Singleton
class OssNoticesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): OssNotices = withContext(Dispatchers.IO) {
        try {
            val raw = context.assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() }
            json.decodeFromString<OssNotices>(raw).forFlavor(BuildConfig.FLAVOR)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            // An empty screen is better than a crash, but this is a packaging
            // bug: the asset is generated and gate-checked at build time.
            Log.e(TAG, "Failed to read $NOTICES_ASSET", e)
            OssNotices()
        }
    }
}

@Serializable
data class OssNotices(
    val dependencies: List<OssNotice> = emptyList(),
    val providers: List<OssProviderNotice> = emptyList(),
) {
    /** Drops standard-only entries from the freenet build. */
    internal fun forFlavor(flavor: String): OssNotices =
        if (flavor == FREENET_FLAVOR) {
            copy(dependencies = dependencies.filterNot { it.standardOnly })
        } else {
            this
        }

    /** Case-insensitive match on the name and the licence, for the search box. */
    fun filter(query: String): OssNotices {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return this
        return OssNotices(
            dependencies = dependencies.filter { it.matches(trimmed) },
            providers = providers.filter { it.matches(trimmed) },
        )
    }

    val isEmpty: Boolean get() = dependencies.isEmpty() && providers.isEmpty()
}

@Serializable
data class OssNotice(
    val name: String = "",
    val version: String = "",
    val license: String = "",
    val url: String = "",
    val standardOnly: Boolean = false,
) {
    internal fun matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) || license.contains(query, ignoreCase = true)
}

@Serializable
data class OssProviderNotice(
    val name: String = "",
    val license: String = "",
    val url: String = "",
) {
    internal fun matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) || license.contains(query, ignoreCase = true)
}
