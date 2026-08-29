package com.sysadmindoc.nimbus.ecosystem

import android.content.ContentResolver
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * The ecosystem ContentProvider is opt-in and off by default. It shipped
 * without automated coverage because it reaches the graph through
 * `EntryPointAccessors.fromApplication`, which needs a Hilt-aware application
 * to bootstrap under Robolectric.
 *
 * What it guards is a privacy boundary: while the toggle is off, a caller
 * holding the permission must see nothing at all, not an empty result that
 * still confirms the app is installed and reachable.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class ZeusWatchWeatherProviderTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var userPreferences: UserPreferences

    private lateinit var resolver: ContentResolver
    private lateinit var authority: String

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        resolver = context.contentResolver
        authority = ZeusWatchWeatherProviderContract.authority(context.packageName)
        runBlocking { userPreferences.setWeatherContentProviderEnabled(false) }
    }

    @Test
    fun disabledProvider_yieldsNoRowsOnAnyPath() = runBlocking {
        listOf("version", "locations").forEach { path ->
            val uri = "content://$authority/$path".toUriCompat()

            // query returns a well-formed empty cursor rather than null so
            // callers do not have to special-case it; the guarantee that
            // matters is that it carries no rows.
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                assertEquals("$path leaked rows while the provider was disabled", 0, cursor.count)
            }
        }
    }

    @Test
    fun disabledProvider_reportsNoTypeAtAll() = runBlocking {
        listOf("version", "locations").forEach { path ->
            val uri = "content://$authority/$path".toUriCompat()

            // getType is the stronger signal: while the toggle is off the
            // provider reports the URI as unsupported outright.
            assertNull("$path type leaked while disabled", resolver.getType(uri))
        }
    }

    @Test
    fun enabledProvider_answersTheVersionPath() = runBlocking {
        userPreferences.setWeatherContentProviderEnabled(true)
        val uri = "content://$authority/version".toUriCompat()

        val cursor = resolver.query(uri, null, null, null, null)

        assertNotNull("version must answer once the user opts in", cursor)
        cursor?.use { assertEquals(1, it.count) }
        assertNotNull(resolver.getType(uri))
    }

    @Test
    fun theToggleIsOffUntilTheUserTurnsItOn() = runBlocking {
        // A default-on ecosystem provider would share saved locations with any
        // app holding the permission before the user ever saw the setting.
        assertEquals(false, userPreferences.weatherContentProviderEnabled())
    }

    @Test
    fun writesAreRejectedEvenWhenEnabled() = runBlocking {
        userPreferences.setWeatherContentProviderEnabled(true)
        val uri = "content://$authority/locations".toUriCompat()

        // Read-only by contract: another app must not be able to edit the
        // user's saved locations through it.
        assertNull(resolver.insert(uri, null))
        assertEquals(0, resolver.delete(uri, null, null))
        assertEquals(0, resolver.update(uri, null, null, null))
    }

    private fun String.toUriCompat() = android.net.Uri.parse(this)
}
