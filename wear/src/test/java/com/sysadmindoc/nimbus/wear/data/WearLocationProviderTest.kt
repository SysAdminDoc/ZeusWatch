package com.sysadmindoc.nimbus.wear.data

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.sysadmindoc.nimbus.wear.testing.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The provider used to fall back to 39.8,-98.5 (the geographic center of the
 * contiguous US) whenever permission was denied and nothing had been cached,
 * so the watch rendered Kansas weather as the user's own. It must now report
 * "I don't know" instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearLocationProviderTest {

    @Test
    fun `getLocation returns null when nothing has ever been cached`() = runTest {
        val provider = providerWith(FakeSharedPreferences())

        assertNull(provider.getLocation())
    }

    @Test
    fun `getLocation returns the cached bit-preserving fix`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putLong("lat_bits", java.lang.Double.doubleToRawLongBits(47.6062))
            .putLong("lon_bits", java.lang.Double.doubleToRawLongBits(-122.3321))
            .putString("name", "Seattle")
            .commit()

        val location = providerWith(prefs).getLocation()

        assertEquals(WearLocationProvider.LocationResult(47.6062, -122.3321, "Seattle"), location)
    }

    @Test
    fun `getLocation still honors a legacy float cache`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putFloat("lat", 47.6f)
            .putFloat("lon", -122.3f)
            .commit()

        val location = providerWith(prefs).getLocation()!!

        assertEquals(47.6, location.lat, 0.001)
        assertEquals(-122.3, location.lon, 0.001)
        assertEquals("Saved Location", location.name)
    }

    @Test
    fun `a half-written legacy cache is treated as no location`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.edit().putFloat("lat", 47.6f).commit()

        assertNull(providerWith(prefs).getLocation())
    }

    private fun providerWith(prefs: FakeSharedPreferences): WearLocationProvider {
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        // Permission and string lookups go to the real Robolectric context;
        // only the prefs are faked.
        val real = RuntimeEnvironment.getApplication()
        every { context.checkPermission(any(), any(), any()) } answers {
            real.checkPermission(firstArg(), secondArg(), thirdArg())
        }
        every { context.getString(any()) } answers { real.getString(firstArg()) }
        every { context.applicationInfo } returns real.applicationInfo
        every { context.packageName } returns real.packageName
        return WearLocationProvider(context, mockk<FusedLocationProviderClient>(relaxed = true))
    }
}
