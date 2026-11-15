package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException

/**
 * The path where the keyset can't be read but the keystore is healthy, so the
 * keyset is reset and the saved keys are gone for good. Robolectric has no
 * Android Keystore, so [FakeKeysets] stands in for AndroidKeysetManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EncryptedApiKeyStoreResetTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val keysets = FakeKeysets(context)

    private fun store() = EncryptedApiKeyStore(context, keysets::aead)

    @Test
    fun `a keyset reset empties the keys and marks the ones that were saved`() = runTest {
        store().apply {
            setOwmApiKey("owm-1")
            setPirateWeatherApiKey("pirate-1")
        }
        keysets.corrupt(MAIN_KEYSET)

        val afterReset = store()

        assertEquals(1, keysets.resets)
        assertEquals(ApiKeys(), afterReset.apiKeys.value)
        assertEquals(setOf(ApiKeyName.OWM, ApiKeyName.PIRATE_WEATHER), afterReset.lostKeys.value)
    }

    @Test
    fun `entering a lost key clears its mark and the rest survive a restart`() = runTest {
        store().apply {
            setOwmApiKey("owm-1")
            setPirateWeatherApiKey("pirate-1")
        }
        keysets.corrupt(MAIN_KEYSET)
        store().setOwmApiKey("owm-2")

        val restarted = store()

        assertEquals("owm-2", restarted.apiKeys.value.owmApiKey)
        assertEquals(setOf(ApiKeyName.PIRATE_WEATHER), restarted.lostKeys.value)
    }

    @Test
    fun `dismissing the notice clears every mark for good`() = runTest {
        store().setTempestAccessToken("tempest-1")
        keysets.corrupt(MAIN_KEYSET)
        store().clearLostKeys()

        assertEquals(emptySet<ApiKeyName>(), store().lostKeys.value)
    }

    @Test
    fun `an install that never recorded its keys asks for all of them`() = runTest {
        store().setOwmApiKey("owm-1")
        // As an older version left it: the key file, with no record of which keys it holds.
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        keysets.corrupt(MAIN_KEYSET)

        assertEquals(ApiKeyName.entries.toSet(), store().lostKeys.value)
    }

    @Test
    fun `a reset with no saved keys marks nothing`() {
        store()
        keysets.corrupt(MAIN_KEYSET)

        val afterReset = store()

        assertEquals(1, keysets.resets)
        assertEquals(emptySet<ApiKeyName>(), afterReset.lostKeys.value)
    }

    @Test
    fun `a keystore outage keeps the keyset and marks nothing`() = runTest {
        store().setOwmApiKey("owm-1")
        keysets.keystoreDown = true

        val duringOutage = store()

        assertEquals(0, keysets.resets)
        assertEquals(emptySet<ApiKeyName>(), duringOutage.lostKeys.value)
        keysets.keystoreDown = false
        assertEquals("owm-1", store().apiKeys.value.owmApiKey)
    }

    /**
     * Keysets kept by name in the same SharedPreferences file the real manager
     * uses: a name with no entry there gets a new key (so the store removing
     * the entry is what resets it), a corrupted one throws on every read, and
     * while the keystore is down every name throws.
     */
    private class FakeKeysets(context: Context) {
        private val prefs = context.getSharedPreferences(KEYSET_PREFS, Context.MODE_PRIVATE)
        private val aeads = mutableMapOf<String, Aead>()
        private val corrupted = mutableSetOf<String>()
        var keystoreDown = false
        var resets = 0
            private set

        init {
            AeadConfig.register()
        }

        fun corrupt(name: String) {
            corrupted += name
        }

        @Suppress("UNUSED_PARAMETER")
        fun aead(context: Context, name: String): Aead {
            if (keystoreDown) throw GeneralSecurityException("keystore unavailable")
            if (!prefs.contains(name)) {
                // Only the store removing a corrupted keyset's entry counts as a reset.
                if (corrupted.remove(name)) resets++
                aeads[name] = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
                    .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
                prefs.edit().putString(name, "keyset").commit()
            }
            if (name in corrupted) throw GeneralSecurityException("keyset unreadable")
            return aeads.getValue(name)
        }
    }

    private companion object {
        const val MAIN_KEYSET = "nimbus_api_key_keyset"
        const val KEYSET_PREFS = "nimbus_api_key_keyset_prefs"
        const val STATUS_PREFS = "nimbus_api_key_status"
    }
}
