package com.sysadmindoc.nimbus.data.repository

import android.app.Application
import android.location.Address
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.WmoAlertAdapter
import com.sysadmindoc.nimbus.data.api.WmoAlertApi
import com.sysadmindoc.nimbus.data.api.WmoMember
import com.sysadmindoc.nimbus.data.api.WmoMemberRegion
import com.sysadmindoc.nimbus.data.api.WmoWarning
import com.sysadmindoc.nimbus.data.api.WmoWarningsResponse
import com.sysadmindoc.nimbus.data.location.CannedGeocoderShadow
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * The alert check reads the country from the geocoder: the blocking call
 * below API 33 and the listener from 33. A listener that never answers must
 * not hold the check, so the country falls back to the timezone guess after a
 * bounded wait. The point is in Japan and the device clock in New York, so the
 * adapter that gets called shows which source decided the country.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [26, 34, 36],
    application = Application::class,
    shadows = [CannedGeocoderShadow::class],
)
class AlertRepositoryGeocoderApiLevelTest {

    private val usesListener = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private val us = CountingAdapter("us-test", setOf("US"))
    private val japan = CountingAdapter("jp-test", setOf("JP"))
    private val prefs = mockk<UserPreferences> {
        every { settings } returns flowOf(NimbusSettings())
    }
    private val savedZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        CannedGeocoderShadow.reset()
        CannedGeocoderShadow.addresses = listOf(Address(Locale.US).apply { countryCode = "JP" })
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        CannedGeocoderShadow.reset()
    }

    @Test
    fun `a geocoder that answers decides the country at every level`() = runTest(timeout = 10.seconds) {
        // The production bound: an answering listener never comes near it.
        val result = repository(us, japan).getAlertsDetailed(TOKYO_LAT, TOKYO_LON)

        assertEquals(0, us.calls.get())
        assertEquals(1, japan.calls.get())
        assertFalse(result.allAdaptersFailed)
        assertTheLevelsCallRan()
        assertEquals(emptyList<String>(), warnings())
    }

    @Test
    fun `a silent listener falls back to the timezone after a bounded wait`() = runTest(timeout = 10.seconds) {
        CannedGeocoderShadow.silent = true

        val result = repository(us, japan, timeoutMs = SHORT_TIMEOUT_MS).getAlertsDetailed(TOKYO_LAT, TOKYO_LON)

        // Below 33 the blocking call answers as before, so Japan still wins.
        assertEquals(if (usesListener) 1 else 0, us.calls.get())
        assertEquals(if (usesListener) 0 else 1, japan.calls.get())
        assertFalse(result.allAdaptersFailed)
        assertTheLevelsCallRan()
        assertEquals(if (usesListener) listOf(NO_ANSWER) else emptyList(), warnings())
    }

    @Test
    fun `a silent listener leaves the WMO area filter with no names, so it keeps every row`() =
        runTest(timeout = 10.seconds) {
            // A country hint skips the country lookup, so the WMO area filter
            // is the only geocoder call. The blocking call below 33 still
            // names Moscow and drops the Rostov warning.
            CannedGeocoderShadow.silent = true
            CannedGeocoderShadow.addresses = listOf(
                Address(Locale.US).apply {
                    countryCode = "RU"
                    adminArea = "Moscow"
                    locality = "Moscow"
                },
            )
            val repository = repository(wmoAdapter(), timeoutMs = SHORT_TIMEOUT_MS)

            val result = repository.getAlertsDetailed(MOSCOW_LAT, MOSCOW_LON, countryHint = "RU")

            val expected = if (usesListener) listOf("wmo-moscow", "wmo-rostov") else listOf("wmo-moscow")
            assertEquals(expected, result.alerts.map { it.id })
            assertFalse(result.allAdaptersFailed)
            assertTheLevelsCallRan()
            assertEquals(if (usesListener) listOf(NO_ANSWER) else emptyList(), warnings())
        }

    private fun repository(
        vararg adapters: AlertSourceAdapter,
        timeoutMs: Long? = null,
    ) = AlertRepository(ApplicationProvider.getApplicationContext(), adapters.toSet(), prefs).apply {
        if (timeoutMs != null) geocodeTimeoutMs = timeoutMs
    }

    private fun assertTheLevelsCallRan() {
        assertEquals(if (usesListener) 1 else 0, CannedGeocoderShadow.listenerCalls)
        assertEquals(if (usesListener) 0 else 1, CannedGeocoderShadow.blockingCalls)
    }

    private fun warnings(): List<String> =
        ShadowLog.getLogsForTag(TAG).filter { it.type == Log.WARN }.map { it.msg }

    /** Map server down, so the adapter serves the country feed's two Russian warnings. */
    private fun wmoAdapter(): WmoAlertAdapter {
        val api = mockk<WmoAlertApi>()
        coEvery { api.getMembers() } returns listOf(
            WmoMemberRegion(members = listOf(WmoMember(mid = "RUMS", name = "Russian Federation", dept = "Roshydromet"))),
        )
        coEvery { api.getWarnings() } returns WmoWarningsResponse(
            items = listOf(
                wmoWarning(id = "wmo-moscow", event = "Snow", areaDesc = "Moscow, New Moscow"),
                wmoWarning(id = "wmo-rostov", event = "Rain", areaDesc = "Rostov Region"),
            ),
        )
        coEvery { api.getFeatures(any(), any(), any(), any(), any(), any(), any()) } throws IOException("map server down")
        return WmoAlertAdapter(api)
    }

    /** Equal severities keep the merge in list order; no expiry, so never dropped. */
    private fun wmoWarning(id: String, event: String, areaDesc: String) = WmoWarning(
        id = id,
        event = event,
        headline = event,
        sent = null,
        expires = null,
        areaDesc = areaDesc,
        mid = "RUMS",
        s = 3,
        u = 3,
        c = 3,
        capURL = null,
        effective = null,
    )

    private class CountingAdapter(
        override val sourceId: String,
        override val supportedRegions: Set<String>,
    ) : AlertSourceAdapter {
        override val displayName: String = sourceId
        val calls = AtomicInteger()

        override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
            calls.incrementAndGet()
            return Result.success(emptyList())
        }
    }

    private companion object {
        const val TAG = "AlertRepository"
        const val SHORT_TIMEOUT_MS = 200L
        const val NO_ANSWER = "Geocoder gave no answer within $SHORT_TIMEOUT_MS ms"
        const val TOKYO_LAT = 35.68
        const val TOKYO_LON = 139.69
        const val MOSCOW_LAT = 55.75
        const val MOSCOW_LON = 37.62
    }
}
