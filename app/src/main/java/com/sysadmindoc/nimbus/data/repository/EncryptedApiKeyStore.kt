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

private const val API_KEY_FILE = "encrypted_api_keys.json"
private const val TINK_KEYSET_NAME = "nimbus_api_key_keyset"
private const val TINK_KEYSET_PREFS = "nimbus_api_key_keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://nimbus_api_key_master"

private const val OLD_ENCRYPTED_FILE = "datastore/nimbus_api_keys.preferences_pb"
private val OLD_ASSOCIATED_DATA = "nimbus_api_keys.preferences_pb".toByteArray()
private val ASSOCIATED_DATA = "nimbus_api_keys_v2".toByteArray()

@Serializable
data class ApiKeys(
    val owmApiKey: String = "",
    val pirateWeatherApiKey: String = "",
)

class EncryptedApiKeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val aead: Aead = createAead(appContext)
    private val file = File(appContext.filesDir, API_KEY_FILE)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _keys = MutableStateFlow(readFromFile())

    val apiKeys: StateFlow<ApiKeys> = _keys.asStateFlow()

    suspend fun setOwmApiKey(key: String) = update { it.copy(owmApiKey = key) }

    suspend fun setPirateWeatherApiKey(key: String) = update { it.copy(pirateWeatherApiKey = key) }

    private suspend fun update(transform: (ApiKeys) -> ApiKeys) = mutex.withLock {
        val updated = transform(_keys.value)
        writeToFile(updated)
        _keys.value = updated
    }

    private fun readFromFile(): ApiKeys {
        if (!file.exists()) return ApiKeys()
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
        val plaintext = json.encodeToString(ApiKeys.serializer(), keys)
        val encrypted = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        file.parentFile?.mkdirs()
        file.writeBytes(encrypted)
    }

    suspend fun migrateFromLegacyEncryptedDataStore() = mutex.withLock {
        if (_keys.value.owmApiKey.isNotBlank() || _keys.value.pirateWeatherApiKey.isNotBlank()) return@withLock
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
            if (migrated.owmApiKey.isNotBlank() || migrated.pirateWeatherApiKey.isNotBlank()) {
                writeToFile(migrated)
                _keys.value = migrated
            }
        } catch (_: Exception) {
            // Old file corrupt or AEAD mismatch — user re-enters keys
        }
        oldFile.delete()
    }
}

private fun createAead(context: Context): Aead {
    AeadConfig.register()
    return try {
        buildAead(context)
    } catch (_: Exception) {
        context.getSharedPreferences(TINK_KEYSET_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        buildAead(context)
    }
}

private fun buildAead(context: Context): Aead {
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, TINK_KEYSET_NAME, TINK_KEYSET_PREFS)
        .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle
    return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
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
