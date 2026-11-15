package com.sysadmindoc.nimbus.data.location

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.util.Locale

/**
 * The reverse geocoder takes the blocking call below API 33 and the listener
 * from 33. Both have to read an address the same way, so each case runs at
 * the minSdk, at 34 and at the target SDK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [26, 34, 36],
    application = Application::class,
    shadows = [CannedGeocoderShadow::class],
)
class ReverseGeocoderApiLevelTest {

    private val geocoder = ReverseGeocoder(ApplicationProvider.getApplicationContext())
    private val usesListener = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @Before
    fun setUp() = CannedGeocoderShadow.reset()

    @Test
    fun `a named place resolves through the call this API level uses`() = runTest {
        CannedGeocoderShadow.addresses = listOf(
            address(locality = "Boulder", adminArea = "Colorado", countryName = "United States"),
        )

        assertEquals(
            ReverseGeoResult(name = "Boulder", region = "Colorado", country = "United States"),
            geocoder.resolve(40.0, -105.3),
        )
        assertTheLevelsCallRan()
    }

    @Test
    fun `a blank town name falls through to the county`() = runTest {
        CannedGeocoderShadow.addresses = listOf(
            address(locality = "", subAdminArea = "Boulder County", adminArea = "Colorado"),
        )

        assertEquals("Boulder County", geocoder.resolve(40.0, -105.3)?.name)
        assertTheLevelsCallRan()
    }

    @Test
    fun `with no usable town or county the state names the place`() = runTest {
        CannedGeocoderShadow.addresses = listOf(
            address(subAdminArea = " ", adminArea = "Colorado", countryName = "United States"),
        )

        assertEquals(
            ReverseGeoResult(name = "Colorado", region = "Colorado", country = "United States"),
            geocoder.resolve(40.0, -105.3),
        )
        assertTheLevelsCallRan()
    }

    @Test
    fun `an address with no usable name gives no result`() = runTest {
        CannedGeocoderShadow.addresses = listOf(address(locality = " ", countryName = "United States"))

        assertNull(geocoder.resolve(40.0, -105.3))
        assertTheLevelsCallRan()
        assertNothingLogged()
    }

    @Test
    fun `no address gives no result`() = runTest {
        assertNull(geocoder.resolve(40.0, -105.3))
        assertTheLevelsCallRan()
        assertNothingLogged()
    }

    @Test
    fun `a backend error gives no result`() = runTest {
        CannedGeocoderShadow.error = "backend unavailable"

        assertNull(geocoder.resolve(40.0, -105.3))
        assertTheLevelsCallRan()
        val expected = if (usesListener) "reverse geocode error: backend unavailable" else "reverse geocode backend unavailable"
        assertEquals(listOf(expected), ShadowLog.getLogsForTag(TAG).map { it.msg })
    }

    /** A null from resolve proves nothing unless the call for this level ran, and only that one. */
    private fun assertTheLevelsCallRan() {
        assertEquals(if (usesListener) 1 else 0, CannedGeocoderShadow.listenerCalls)
        assertEquals(if (usesListener) 0 else 1, CannedGeocoderShadow.blockingCalls)
    }

    /** resolve also returns null when it swallows an exception, and that path always logs. */
    private fun assertNothingLogged() {
        assertEquals(emptyList<String>(), ShadowLog.getLogsForTag(TAG).map { it.msg })
    }

    private fun address(
        locality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null,
        countryName: String? = null,
    ) = Address(Locale.US).apply {
        this.locality = locality
        this.subAdminArea = subAdminArea
        this.adminArea = adminArea
        this.countryName = countryName
    }

    private companion object {
        const val TAG = "ReverseGeocoder"
    }
}

/**
 * Robolectric's own geocoder shadow keeps its canned addresses per Geocoder,
 * and ReverseGeocoder builds a new one for every call, so this shadow serves
 * one shared answer to the blocking call and the listener alike.
 */
@Suppress("UnusedParameter") // The parameters mirror the framework signatures.
@Implements(Geocoder::class)
class CannedGeocoderShadow {

    @Implementation
    fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int): List<Address> {
        blockingCalls++
        error?.let { throw IOException(it) }
        return addresses
    }

    @Implementation(minSdk = Build.VERSION_CODES.TIRAMISU)
    fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int,
        listener: Geocoder.GeocodeListener,
    ) {
        listenerCalls++
        val message = error
        if (message != null) listener.onError(message) else listener.onGeocode(addresses.toMutableList())
    }

    companion object {
        var addresses: List<Address> = emptyList()
        var error: String? = null
        var blockingCalls = 0
        var listenerCalls = 0

        fun reset() {
            addresses = emptyList()
            error = null
            blockingCalls = 0
            listenerCalls = 0
        }
    }
}
