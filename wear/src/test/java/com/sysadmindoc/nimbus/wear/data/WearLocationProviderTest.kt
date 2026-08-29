package com.sysadmindoc.nimbus.wear.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.sysadmindoc.nimbus.wear.testing.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun `an active fix is requested when there is no last known location`() = runTest {
        val fix = Location("test").apply { latitude = 47.6062; longitude = -122.3321 }
        val client = mockk<FusedLocationProviderClient>()
        every { client.lastLocation } returns nullLocationTask()
        every { client.getCurrentLocation(any<Int>(), any()) } returns locationTask(fix)

        // lastLocation is null on a watch where nothing has requested a fix
        // recently. Without the active request, granting permission would
        // leave the user stuck on the no-location card with no way out.
        val location = providerWith(FakeSharedPreferences(), client, granted = true).getLocation()

        assertEquals(47.6062, location!!.lat, 0.00001)
        assertEquals(-122.3321, location.lon, 0.00001)
        verify(exactly = 1) { client.getCurrentLocation(any<Int>(), any()) }
    }

    @Test
    fun `no active fix is requested when a last known location exists`() = runTest {
        val fix = Location("test").apply { latitude = 10.0; longitude = 20.0 }
        val client = mockk<FusedLocationProviderClient>()
        every { client.lastLocation } returns locationTask(fix)

        val location = providerWith(FakeSharedPreferences(), client, granted = true).getLocation()

        assertEquals(10.0, location!!.lat, 0.00001)
        verify(exactly = 0) { client.getCurrentLocation(any<Int>(), any()) }
    }

    private fun locationTask(location: Location?): Task<Location> {
        val task = mockk<Task<Location>>(relaxed = true)
        every { task.addOnSuccessListener(any<OnSuccessListener<Location>>()) } answers {
            firstArg<OnSuccessListener<Location>>().onSuccess(location)
            task
        }
        every { task.addOnFailureListener(any<OnFailureListener>()) } returns task
        return task
    }

    private fun nullLocationTask(): Task<Location> = locationTask(null)

    private fun providerWith(prefs: FakeSharedPreferences): WearLocationProvider =
        providerWith(prefs, mockk(relaxed = true), granted = false)

    private fun providerWith(
        prefs: FakeSharedPreferences,
        client: FusedLocationProviderClient,
        granted: Boolean,
    ): WearLocationProvider {
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        // String lookups go to the real Robolectric context; the prefs and the
        // permission answer are faked so both branches are reachable.
        val real = RuntimeEnvironment.getApplication()
        every { context.checkPermission(any(), any(), any()) } returns
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        every { context.getString(any()) } answers { real.getString(firstArg()) }
        every { context.applicationInfo } returns real.applicationInfo
        every { context.packageName } returns real.packageName
        return WearLocationProvider(context, client)
    }
}
