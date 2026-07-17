package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedApiKeyStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `atomic write creates target with exact bytes`() {
        val target = tempFolder.newFolder().resolve("keys.json")
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        writeBytesAtomically(target, bytes)

        assertTrue(target.exists())
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test
    fun `atomic write replaces existing content instead of appending`() {
        val target = tempFolder.newFolder().resolve("keys.json")
        target.writeBytes(ByteArray(1024) { 0x7F })
        val replacement = "replacement".toByteArray(Charsets.UTF_8)

        writeBytesAtomically(target, replacement)

        assertArrayEquals(replacement, target.readBytes())
    }

    @Test
    fun `atomic write leaves no temp sidecar behind`() {
        val dir = tempFolder.newFolder()
        val target = dir.resolve("keys.json")

        writeBytesAtomically(target, byteArrayOf(9, 8, 7))
        writeBytesAtomically(target, byteArrayOf(6, 5, 4))

        assertFalse(dir.resolve("keys.json.tmp").exists())
        assertArrayEquals(byteArrayOf(6, 5, 4), target.readBytes())
    }

    @Test
    fun `legacy proto extraction still parses api keys`() {
        // Guard the migration path against regressions from the AEAD rework:
        // a minimal DataStore Preferences proto with one string entry.
        val key = "owm_api_key"
        val value = "secret-123"
        val proto = preferencesProto(key, value)

        val keys = extractApiKeysFromPreferencesProto(proto)

        assertTrue(keys.owmApiKey == value)
        assertTrue(keys.pirateWeatherApiKey.isEmpty())
        assertTrue(keys.tempestAccessToken.isEmpty())
    }

    /**
     * Builds `PreferencesProto { map<string, Value> preferences = 1; }` with a
     * single entry whose Value carries `string_value = 5`.
     */
    private fun preferencesProto(key: String, value: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        // Value message: field 5 (string_value), wire type 2.
        val valueMessage = byteArrayOf(((5 shl 3) or 2).toByte(), valueBytes.size.toByte()) + valueBytes
        // Map entry: key = field 1 LEN, value = field 2 LEN(sub-message).
        val entry = byteArrayOf(((1 shl 3) or 2).toByte(), keyBytes.size.toByte()) + keyBytes +
            byteArrayOf(((2 shl 3) or 2).toByte(), valueMessage.size.toByte()) + valueMessage
        // Top level: preferences map = field 1 LEN.
        return byteArrayOf(((1 shl 3) or 2).toByte(), entry.size.toByte()) + entry
    }
}
