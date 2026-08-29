package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Same corruption handler as provider health: without it one corrupted file
// makes every edit{} throw CorruptionException forever, while reads keep
// working through the IOException catch and hide it.
private val Context.deliveryHealthStore: DataStore<Preferences> by preferencesDataStore(
    name = "nimbus_delivery_health",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The background jobs that put weather somewhere other than the app itself.
 *
 * Provider health answers "is the forecast coming in". This answers the
 * question users actually ask when a widget goes stale, which is whether the
 * job that fills it ever ran.
 */
@Serializable
enum class DeliverySurface {
    WIDGETS,
    DAILY_BRIEFING,
    WEAR_SYNC,
    GADGETBRIDGE,
    WEATHER_ALERTS,
    CUSTOM_ALERTS,
    NOWCAST_ALERTS,
    HEALTH_ALERTS,
}

/**
 * Why a delivery attempt failed, in terms that survive being written down.
 *
 * Deliberately coarse. A raw exception message from a provider routinely
 * carries the request URL with the user's coordinates in it, and this store is
 * exportable, so nothing but this enum is ever persisted.
 */
@Serializable
enum class DeliveryFailureReason {
    NO_LOCATION,
    NO_NETWORK,
    FORECAST_UNAVAILABLE,
    PERMISSION_DENIED,
    NO_RECEIVER,
    BATTERY_RESTRICTED,
    UNKNOWN,
}

/**
 * Maps a caught exception onto a reason worth persisting.
 *
 * Nothing from the exception itself survives this: `e.message` from a provider
 * routinely contains the request URL with the user's coordinates in it, and the
 * diagnostics panel is exportable.
 */
fun Throwable.deliveryFailureReason(): DeliveryFailureReason = when (this) {
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is java.io.IOException,
    -> DeliveryFailureReason.NO_NETWORK
    is SecurityException -> DeliveryFailureReason.PERMISSION_DENIED
    is IllegalStateException -> DeliveryFailureReason.FORECAST_UNAVAILABLE
    else -> DeliveryFailureReason.UNKNOWN
}

@Serializable
data class DeliveryHealthEntry(
    val surface: DeliverySurface,
    val lastAttemptEpochMs: Long? = null,
    val lastSuccessEpochMs: Long? = null,
    val lastFailureEpochMs: Long? = null,
    val lastFailureReason: DeliveryFailureReason? = null,
    val nextScheduledEpochMs: Long? = null,
    val consecutiveFailures: Int = 0,
)

@Serializable
data class DeliveryHealthSnapshot(
    val entries: List<DeliveryHealthEntry> = emptyList(),
)

@Singleton
class DeliveryHealthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.deliveryHealthStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SNAPSHOT = stringPreferencesKey("delivery_health_snapshot")
    }

    val snapshot: Flow<DeliveryHealthSnapshot> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decode(prefs[Keys.SNAPSHOT]) }

    suspend fun recordAttempt(surface: DeliverySurface, nowEpochMs: Long) {
        update(surface) { it.copy(lastAttemptEpochMs = nowEpochMs) }
    }

    suspend fun recordSuccess(
        surface: DeliverySurface,
        nowEpochMs: Long,
        nextScheduledEpochMs: Long? = null,
    ) {
        update(surface) {
            it.copy(
                lastAttemptEpochMs = nowEpochMs,
                lastSuccessEpochMs = nowEpochMs,
                nextScheduledEpochMs = nextScheduledEpochMs ?: it.nextScheduledEpochMs,
                // Cleared, not left standing: a surface that recovered should
                // stop reading as broken the moment it works again.
                lastFailureReason = null,
                consecutiveFailures = 0,
            )
        }
    }

    suspend fun recordFailure(
        surface: DeliverySurface,
        reason: DeliveryFailureReason,
        nowEpochMs: Long,
        nextScheduledEpochMs: Long? = null,
    ) {
        update(surface) {
            it.copy(
                lastAttemptEpochMs = nowEpochMs,
                lastFailureEpochMs = nowEpochMs,
                lastFailureReason = reason,
                nextScheduledEpochMs = nextScheduledEpochMs ?: it.nextScheduledEpochMs,
                consecutiveFailures = it.consecutiveFailures + 1,
            )
        }
    }

    /** Forgets a surface the user has turned off, so it stops being reported. */
    suspend fun forget(surface: DeliverySurface) {
        store.edit { prefs ->
            val current = decode(prefs[Keys.SNAPSHOT])
            prefs[Keys.SNAPSHOT] = json.encodeToString(
                DeliveryHealthSnapshot(current.entries.filterNot { it.surface == surface }),
            )
        }
    }

    suspend fun clear() {
        store.edit { it[Keys.SNAPSHOT] = json.encodeToString(DeliveryHealthSnapshot()) }
    }

    suspend fun current(): DeliveryHealthSnapshot = snapshot.first()

    suspend fun diagnosticsText(
        nowEpochMs: Long = System.currentTimeMillis(),
        nextScheduledRuns: Map<DeliverySurface, Long> = emptyMap(),
    ): String = DeliveryHealthDiagnosticsFormatter.format(current(), nowEpochMs, nextScheduledRuns)

    private suspend fun update(
        surface: DeliverySurface,
        transform: (DeliveryHealthEntry) -> DeliveryHealthEntry,
    ) {
        store.edit { prefs ->
            val current = decode(prefs[Keys.SNAPSHOT])
            val existing = current.entries.firstOrNull { it.surface == surface }
                ?: DeliveryHealthEntry(surface = surface)
            val updated = current.entries.filterNot { it.surface == surface } + transform(existing)
            prefs[Keys.SNAPSHOT] = json.encodeToString(
                DeliveryHealthSnapshot(updated.sortedBy { it.surface.ordinal }),
            )
        }
    }

    private fun decode(raw: String?): DeliveryHealthSnapshot {
        if (raw.isNullOrBlank()) return DeliveryHealthSnapshot()
        // A snapshot written by a build that knew a surface this one does not
        // would otherwise throw on every read and leave diagnostics blank.
        return runCatching { json.decodeFromString<DeliveryHealthSnapshot>(raw) }
            .getOrElse { DeliveryHealthSnapshot() }
    }
}

/**
 * The exportable form.
 *
 * Everything here is either a timestamp or the name of one of this app's own
 * enum constants, which is the whole reason the failure reason is an enum: a
 * user sending this to a bug report must not be sending their coordinates.
 */
object DeliveryHealthDiagnosticsFormatter {
    fun format(
        snapshot: DeliveryHealthSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
        nextScheduledRuns: Map<DeliverySurface, Long> = emptyMap(),
    ): String = buildString {
        appendLine("ZeusWatch background delivery diagnostics")
        appendLine("Generated: " + java.time.Instant.ofEpochMilli(nowEpochMs))
        appendLine(
            "Privacy: redacted per-surface status only; no locations, URLs, " +
                "API keys, or raw exception text.",
        )
        appendLine()

        if (snapshot.entries.isEmpty()) {
            appendLine("No delivery attempts have been recorded yet.")
            return@buildString
        }

        snapshot.entries.sortedBy { it.surface.ordinal }.forEach { entry ->
            appendLine("- " + entry.surface.name)
            appendLine("  Last attempt: " + entry.lastAttemptEpochMs.orNone())
            appendLine("  Last success: " + entry.lastSuccessEpochMs.orNone())
            appendLine("  Last failure: " + entry.lastFailureEpochMs.orNone())
            appendLine("  Failure reason: " + (entry.lastFailureReason?.name ?: "none"))
            appendLine("  Consecutive failures: " + entry.consecutiveFailures)
            appendLine(
                "  Next scheduled: " +
                    (nextScheduledRuns[entry.surface] ?: entry.nextScheduledEpochMs).orNone(),
            )
        }
    }

    private fun Long?.orNone(): String =
        this?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "never"
}
