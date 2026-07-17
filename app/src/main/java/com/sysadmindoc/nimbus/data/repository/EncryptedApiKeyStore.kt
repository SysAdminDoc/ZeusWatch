package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val API_KEY_FILE = "encrypted_api_keys.json"
private const val TINK_KEYSET_NAME = "nimbus_api_key_keyset"
private const val TINK_KEYSET_PROBE_NAME = "nimbus_api_key_keyset_probe"
private const val TINK_KEYSET_PREFS = "nimbus_api_key_keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://nimbus_api_key_master"
private const val AEAD_RETRY_BACKOFF_MS = 250L

private const val OLD_ENCRYPTED_FILE = "datastore/nimbus_api_keys.preferences_pb"
private val OLD_ASSOCIATED_DATA = "nimbus_api_keys.preferences_pb".toByteArray()
private val ASSOCIATED_DATA = "nimbus_api_keys_v2".toByteArray()

@Serializable
data class ApiKeys(
    val owmApiKey: String = "",
    val pirateWeatherApiKey: String = "",
    val tempestAccessToken: String = "",
)

class EncryptedApiKeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val aeadLock = Any()

    @Volatile
    private var cachedAead: Aead? = createAeadOrNull(appContext)
    private val file = File(appContext.filesDir, API_KEY_FILE)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _keys = MutableStateFlow(readFromFile(cachedAead))

    val apiKeys: StateFlow<ApiKeys> = _keys.asStateFlow()

    suspend fun setOwmApiKey(key: String) = update { it.copy(owmApiKey = key) }

    suspend fun setPirateWeatherApiKey(key: String) = update { it.copy(pirateWeatherApiKey = key) }

    suspend fun setTempestAccessToken(token: String) = update { it.copy(tempestAccessToken = token) }

    /**
     * Lazily (re-)create the AEAD when the Android Keystore was unavailable at
     * construction (post-OTA races, keystore daemon contention). Returns `null`
     * while the keystore is still unreachable — the keyset is NOT destroyed, so
     * stored keys become readable again once it recovers. On recovery the
     * in-memory keys are refreshed from disk before any caller-observable use.
     */
    private fun aeadOrNull(): Aead? {
        cachedAead?.let { return it }
        synchronized(aeadLock) {
            cachedAead?.let { return it }
            val created = createAeadOrNull(appContext) ?: return null
            cachedAead = created
            _keys.value = readFromFile(created)
            return created
        }
    }

    private suspend fun update(transform: (ApiKeys) -> ApiKeys) = mutex.withLock {
        // Resolve the AEAD first: if it just recovered, _keys is refreshed from
        // disk so the transform can't overwrite stored keys with blanks.
        aeadOrNull()
        val updated = transform(_keys.value)
        writeToFile(updated)
        _keys.value = updated
    }

    private fun readFromFile(aead: Aead?): ApiKeys {
        if (aead == null || !file.exists()) return ApiKeys()
        return try {
            val encrypted = file.readBytes()
            if (encrypted.isEmpty()) return ApiKeys()
            val decrypted = aead.decrypt(encrypted, ASSOCIATED_DATA)
            json.decodeFromString(ApiKeys.serializer(), String(decrypted, Charsets.UTF_8))
        } catch (_: Exception) {
            ApiKeys()
        }
    }

    private fun writeToFile(keys: ApiKeys) {
        val aead = aeadOrNull() ?: throw IllegalStateException(
            "API key encryption unavailable: Android Keystore could not be reached",
        )
        val plaintext = json.encodeToString(ApiKeys.serializer(), keys)
        val encrypted = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        file.parentFile?.mkdirs()
        writeBytesAtomically(file, encrypted)
    }

    suspend fun migrateFromLegacyEncryptedDataStore() = mutex.withLock {
        // Keystore unavailable this process start: keep the old file so
        // migration can retry on a later launch instead of losing it.
        val aead = aeadOrNull() ?: return@withLock
        if (
            _keys.value.owmApiKey.isNotBlank() ||
            _keys.value.pirateWeatherApiKey.isNotBlank() ||
            _keys.value.tempestAccessToken.isNotBlank()
        ) {
            return@withLock
        }
        val oldFile = File(appContext.filesDir, OLD_ENCRYPTED_FILE)
        if (!oldFile.exists()) return@withLock
        try {
            val encrypted = oldFile.readBytes()
            if (encrypted.isEmpty()) {
                oldFile.delete()
                return@withLock
            }
            val decrypted = aead.decrypt(encrypted, OLD_ASSOCIATED_DATA)
            val migrated = extractApiKeysFromPreferencesProto(decrypted)
            if (
                migrated.owmApiKey.isNotBlank() ||
                migrated.pirateWeatherApiKey.isNotBlank() ||
                migrated.tempestAccessToken.isNotBlank()
            ) {
                writeToFile(migrated)
                _keys.value = migrated
            }
        } catch (_: Exception) {
            // Old file corrupt or AEAD mismatch — user re-enters keys
        }
        oldFile.delete()
    }
}

private fun createAeadOrNull(context: Context): Aead? {
    AeadConfig.register()
    return try {
        buildAead(context)
    } catch (_: Exception) {
        // Transient Android Keystore failures (post-OTA races, keystore daemon
        // contention) throw the same way as true keyset corruption. Retry once
        // after a brief backoff before considering any recovery.
        try {
            Thread.sleep(AEAD_RETRY_BACKOFF_MS)
            buildAead(context)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (_: Exception) {
            recoverAeadOrNull(context)
        }
    }
}

/**
 * Distinguish keyset corruption from keystore unavailability before touching
 * the stored keyset: build a throwaway probe keyset against the same master
 * key. If the probe succeeds the keystore is healthy, so the real keyset must
 * be unreadable — its data is unrecoverable regardless, and resetting just
 * that entry is the only way forward. If the probe also fails the keystore
 * itself is down: keep the keyset intact and let the AEAD retry lazily, so a
 * transient failure never destroys the user's stored keys.
 */
private fun recoverAeadOrNull(context: Context): Aead? {
    val keystoreHealthy = runCatching { buildProbeAead(context) }.isSuccess
    val prefs = context.getSharedPreferences(TINK_KEYSET_PREFS, Context.MODE_PRIVATE)
    prefs.edit().remove(TINK_KEYSET_PROBE_NAME).apply()
    if (!keystoreHealthy) return null
    prefs.edit().remove(TINK_KEYSET_NAME).commit()
    return runCatching { buildAead(context) }.getOrNull()
}

private fun buildAead(context: Context): Aead = buildAead(context, TINK_KEYSET_NAME)

private fun buildProbeAead(context: Context): Aead = buildAead(context, TINK_KEYSET_PROBE_NAME)

private fun buildAead(context: Context, keysetName: String): Aead {
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, keysetName, TINK_KEYSET_PREFS)
        .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle
    return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}

/**
 * Crash-safe replacement of [target]: write to a sibling temp file, fsync it,
 * then rename over the target (atomic on POSIX filesystems) — the same pattern
 * DataStore uses. A crash mid-write leaves the previous file intact instead of
 * a truncated ciphertext that decrypts to nothing.
 */
internal fun writeBytesAtomically(target: File, bytes: ByteArray) {
    val tmp = File(target.parentFile, "${target.name}.tmp")
    FileOutputStream(tmp).use { stream ->
        stream.write(bytes)
        stream.fd.sync()
    }
    if (!tmp.renameTo(target)) {
        // Some filesystems refuse rename-over-existing; fall back once.
        target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Failed to atomically replace ${target.name}")
        }
    }
}

/**
 * Best-effort extraction of API key strings from a DataStore Preferences
 * protobuf blob. Handles the common case; returns empty keys on any
 * parse error so callers degrade gracefully.
 *
 * Protobuf wire format for DataStore Preferences:
 * ```
 * PreferencesProto { map<string, Value> preferences = 1; }
 * Value { oneof { ... string string_value = 5; ... } }
 * ```
 * Map entries are field 1 (LEN) containing sub-fields key=1 and value=2.
 */
internal fun extractApiKeysFromPreferencesProto(bytes: ByteArray): ApiKeys {
    val entries = mutableMapOf<String, String>()
    var pos = 0
    while (pos < bytes.size) {
        val tagResult = readVarint(bytes, pos) ?: break
        pos = tagResult.nextPos
        val fieldNum = (tagResult.value ushr 3).toInt()
        val wireType = (tagResult.value and 0x7).toInt()
        when (wireType) {
            2 -> {
                val lenResult = readVarint(bytes, pos) ?: break
                pos = lenResult.nextPos
                val len = lenResult.value.toInt()
                if (pos + len > bytes.size) break
                if (fieldNum == 1) {
                    parseMapEntry(bytes, pos, pos + len)?.let { (k, v) -> entries[k] = v }
                }
                pos += len
            }
            0 -> {
                val vr = readVarint(bytes, pos) ?: break
                pos = vr.nextPos
            }
            1 -> pos += 8
            5 -> pos += 4
            else -> break
        }
    }
    return ApiKeys(
        owmApiKey = entries["owm_api_key"] ?: "",
        pirateWeatherApiKey = entries["pirate_weather_api_key"] ?: "",
        tempestAccessToken = entries["tempest_access_token"] ?: "",
    )
}

private data class VarintResult(val value: Long, val nextPos: Int)

private fun readVarint(bytes: ByteArray, start: Int): VarintResult? {
    var result = 0L
    var shift = 0
    var pos = start
    while (pos < bytes.size) {
        val b = bytes[pos].toInt() and 0xFF
        result = result or ((b.toLong() and 0x7F) shl shift)
        pos++
        if (b and 0x80 == 0) return VarintResult(result, pos)
        shift += 7
        if (shift >= 64) return null
    }
    return null
}

private fun readString(bytes: ByteArray, start: Int, end: Int): String? {
    if (start > end || end > bytes.size) return null
    return String(bytes, start, end - start, Charsets.UTF_8)
}

private fun parseMapEntry(bytes: ByteArray, start: Int, end: Int): Pair<String, String>? {
    var key: String? = null
    var stringValue: String? = null
    var pos = start
    while (pos < end) {
        val tagResult = readVarint(bytes, pos) ?: break
        pos = tagResult.nextPos
        val fieldNum = (tagResult.value ushr 3).toInt()
        val wireType = (tagResult.value and 0x7).toInt()
        when (wireType) {
            2 -> {
                val lenResult = readVarint(bytes, pos) ?: break
                pos = lenResult.nextPos
                val len = lenResult.value.toInt()
                if (pos + len > end) break
                when (fieldNum) {
                    1 -> key = readString(bytes, pos, pos + len)
                    2 -> stringValue = parseValueMessage(bytes, pos, pos + len)
                }
                pos += len
            }
            0 -> {
                val vr = readVarint(bytes, pos) ?: break
                pos = vr.nextPos
            }
            1 -> pos += 8
            5 -> pos += 4
            else -> break
        }
    }
    return if (key != null && stringValue != null) key to stringValue else null
}

private fun parseValueMessage(bytes: ByteArray, start: Int, end: Int): String? {
    var pos = start
    while (pos < end) {
        val tagResult = readVarint(bytes, pos) ?: break
        pos = tagResult.nextPos
        val fieldNum = (tagResult.value ushr 3).toInt()
        val wireType = (tagResult.value and 0x7).toInt()
        when (wireType) {
            2 -> {
                val lenResult = readVarint(bytes, pos) ?: break
                pos = lenResult.nextPos
                val len = lenResult.value.toInt()
                if (pos + len > end) break
                if (fieldNum == 5) return readString(bytes, pos, pos + len)
                pos += len
            }
            0 -> {
                val vr = readVarint(bytes, pos) ?: break
                pos = vr.nextPos
            }
            1 -> pos += 8
            5 -> pos += 4
            else -> break
        }
    }
    return null
}
