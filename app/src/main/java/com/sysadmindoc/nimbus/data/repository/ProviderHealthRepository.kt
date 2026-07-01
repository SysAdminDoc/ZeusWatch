package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.providerHealthStore: DataStore<Preferences> by preferencesDataStore(
    name = "nimbus_provider_health",
)

@Serializable
data class ProviderHealthSnapshot(
    val entries: List<ProviderHealthEntry> = emptyList(),
)

@Serializable
data class ProviderHealthEntry(
    val type: WeatherDataType,
    val provider: WeatherSourceProvider,
    val lastSuccessEpochMs: Long? = null,
    val lastFailureEpochMs: Long? = null,
    val lastFailureReason: ProviderFailureReason? = null,
    val lastCacheAgeMinutes: Long? = null,
    val activeFallback: Boolean = false,
    val fallbackFromProvider: WeatherSourceProvider? = null,
)

@Serializable
enum class ProviderFailureReason {
    TIMEOUT,
    HTTP_429,
    HTTP_5XX,
    NETWORK,
    UNSUPPORTED,
    UNKNOWN;

    companion object {
        fun classify(exception: Throwable?): ProviderFailureReason = when (exception) {
            is SocketTimeoutException -> TIMEOUT
            is HttpException -> when (exception.code()) {
                429 -> HTTP_429
                in 500..599 -> HTTP_5XX
                else -> UNKNOWN
            }
            is UnknownHostException,
            is ConnectException,
            is IOException -> NETWORK
            is UnsupportedOperationException -> UNSUPPORTED
            else -> UNKNOWN
        }
    }
}

@Singleton
class ProviderHealthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.providerHealthStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SNAPSHOT = stringPreferencesKey("provider_health_snapshot")
    }

    val snapshot: Flow<ProviderHealthSnapshot> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decode(prefs[Keys.SNAPSHOT]) }

    suspend fun recordSuccess(
        type: WeatherDataType,
        provider: WeatherSourceProvider,
        cacheAgeMinutes: Long?,
        activeFallback: Boolean = false,
        fallbackFromProvider: WeatherSourceProvider? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        runCatching {
            mutateSnapshot { current ->
                current.upsert(type, provider, clearActiveFallback = true) { entry ->
                    entry.copy(
                        lastSuccessEpochMs = nowEpochMs,
                        lastCacheAgeMinutes = cacheAgeMinutes?.coerceAtLeast(0L),
                        activeFallback = activeFallback,
                        fallbackFromProvider = fallbackFromProvider.takeIf { activeFallback },
                    )
                }
            }
        }
    }

    suspend fun recordFailure(
        type: WeatherDataType,
        provider: WeatherSourceProvider,
        exception: Throwable?,
        clearActiveFallback: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        recordFailure(
            type = type,
            provider = provider,
            reason = ProviderFailureReason.classify(exception),
            clearActiveFallback = clearActiveFallback,
            nowEpochMs = nowEpochMs,
        )
    }

    suspend fun recordFailure(
        type: WeatherDataType,
        provider: WeatherSourceProvider,
        reason: ProviderFailureReason,
        clearActiveFallback: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        runCatching {
            mutateSnapshot { current ->
                current.upsert(type, provider, clearActiveFallback) { entry ->
                    entry.copy(
                        lastFailureEpochMs = nowEpochMs,
                        lastFailureReason = reason,
                    )
                }
            }
        }
    }

    suspend fun diagnosticsText(nowEpochMs: Long = System.currentTimeMillis()): String =
        ProviderHealthDiagnosticsFormatter.format(snapshot.first(), nowEpochMs)

    private suspend fun mutateSnapshot(
        transform: (ProviderHealthSnapshot) -> ProviderHealthSnapshot,
    ) {
        store.edit { prefs ->
            val current = decode(prefs[Keys.SNAPSHOT])
            prefs[Keys.SNAPSHOT] = json.encodeToString(transform(current).sorted())
        }
    }

    private fun decode(raw: String?): ProviderHealthSnapshot =
        raw
            ?.let { runCatching { json.decodeFromString<ProviderHealthSnapshot>(it) }.getOrNull() }
            ?: ProviderHealthSnapshot()
}

object ProviderHealthDiagnosticsFormatter {
    fun format(
        snapshot: ProviderHealthSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): String = buildString {
        appendLine("ZeusWatch source health diagnostics")
        appendLine("Generated: ${Instant.ofEpochMilli(nowEpochMs)}")
        appendLine("Privacy: redacted provider/type status only; no locations, URLs, API keys, or raw exception text.")
        appendLine()

        val entries = snapshot.sorted().entries
        if (entries.isEmpty()) {
            appendLine("No provider checks have been recorded yet.")
            return@buildString
        }

        entries.forEach { entry ->
            appendLine("- ${entry.type.name} / ${entry.provider.displayName}")
            appendLine("  Last success: ${entry.lastSuccessEpochMs.formatInstantOrNone()}")
            appendLine("  Last failure: ${entry.lastFailureEpochMs.formatInstantOrNone()}")
            appendLine("  Failure class: ${entry.lastFailureReason?.name ?: "none"}")
            appendLine("  Last cache age: ${entry.lastCacheAgeMinutes?.let { "$it min" } ?: "none"}")
            appendLine(
                "  Active fallback: " + if (entry.activeFallback) {
                    "yes, from ${entry.fallbackFromProvider?.displayName ?: "primary"}"
                } else {
                    "no"
                },
            )
        }
    }

    private fun Long?.formatInstantOrNone(): String =
        this?.let { Instant.ofEpochMilli(it).toString() } ?: "none"
}

private fun ProviderHealthSnapshot.upsert(
    type: WeatherDataType,
    provider: WeatherSourceProvider,
    clearActiveFallback: Boolean,
    transform: (ProviderHealthEntry) -> ProviderHealthEntry,
): ProviderHealthSnapshot {
    var found = false
    val cleared = entries.map { entry ->
        if (clearActiveFallback && entry.type == type) {
            entry.copy(activeFallback = false, fallbackFromProvider = null)
        } else {
            entry
        }
    }
    val updated = cleared.map { entry ->
        if (entry.type == type && entry.provider == provider) {
            found = true
            transform(entry)
        } else {
            entry
        }
    }
    val finalEntries = if (found) {
        updated
    } else {
        updated + transform(ProviderHealthEntry(type = type, provider = provider))
    }
    return copy(entries = finalEntries).sorted()
}

private fun ProviderHealthSnapshot.sorted(): ProviderHealthSnapshot =
    copy(entries = entries.sortedWith(compareBy<ProviderHealthEntry> { it.type.ordinal }.thenBy { it.provider.name }))
