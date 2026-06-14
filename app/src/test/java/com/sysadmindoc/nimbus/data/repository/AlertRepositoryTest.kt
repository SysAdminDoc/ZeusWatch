package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmApi
import com.sysadmindoc.nimbus.data.api.MeteoAlarmInfo
import com.sysadmindoc.nimbus.data.api.MeteoAlarmResponse
import com.sysadmindoc.nimbus.data.api.MeteoAlarmWarning
import com.sysadmindoc.nimbus.data.api.NwsAlertAdapter
import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.NwsAlertFeature
import com.sysadmindoc.nimbus.data.api.NwsAlertProperties
import com.sysadmindoc.nimbus.data.api.NwsAlertResponse
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
class AlertRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

    private lateinit var api: NwsAlertApi
    private lateinit var repository: AlertRepository
    private lateinit var context: Context
    private lateinit var prefs: UserPreferences
    private lateinit var nwsAdapter: NwsAlertAdapter

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        api = mockk()
        context = mockk(relaxed = true)
        prefs = mockk()
        nwsAdapter = NwsAlertAdapter(api)

        // Default prefs: AUTO mode
        every { prefs.settings } returns flowOf(NimbusSettings())

        // Mock Geocoder to return US for default test coordinates.
        // Geocoder.isPresent() must be stubbed too: with returnDefaultValues it
        // returns false, the constructor mock never engages, and country
        // detection silently falls back to the machine timezone — making the
        // whole suite depend on where CI/dev machines run.
        mockkStatic(Geocoder::class)
        every { Geocoder.isPresent() } returns true
        mockkConstructor(Geocoder::class)
        val usAddress = mockk<Address>()
        every { usAddress.countryCode } returns "US"
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } returns listOf(usAddress)

        val adapters: Set<AlertSourceAdapter> = setOf(nwsAdapter)
        repository = AlertRepository(context, adapters, prefs)
    }

    @After
    fun teardown() {
        unmockkStatic(Geocoder::class)
        unmockkConstructor(Geocoder::class)
        Dispatchers.resetMain()
    }

    private fun makeFeature(
        id: String = "alert-1",
        event: String = "Tornado Warning",
        severity: String = "Extreme",
        urgency: String = "Immediate",
    ) = NwsAlertFeature(
        id = id,
        properties = NwsAlertProperties(
            event = event,
            headline = "$event for Test County",
            description = "A tornado has been sighted.",
            instruction = "Take shelter immediately.",
            severity = severity,
            urgency = urgency,
            certainty = "Observed",
            senderName = "NWS Denver",
            areaDesc = "Denver Metro Area",
            effective = "2025-01-15T12:00:00",
            expires = "2025-01-15T14:00:00",
            response = "Shelter",
        ),
    )

    @Test
    fun `getAlerts maps alerts on success`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                makeFeature(event = "Tornado Warning", severity = "Extreme"),
                makeFeature(id = "alert-2", event = "Flood Watch", severity = "Moderate"),
            ),
        )

        val result = repository.getAlerts(39.7, -104.9)
        assertTrue(result.isSuccess)
        val alerts = result.getOrThrow()
        assertEquals(2, alerts.size)
        assertEquals("Tornado Warning", alerts[0].event)
        assertEquals(AlertSeverity.EXTREME, alerts[0].severity)
    }

    @Test
    fun `getAlerts sorts by severity`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                makeFeature(id = "1", event = "Heat Advisory", severity = "Minor", urgency = "Expected"),
                makeFeature(id = "2", event = "Tornado Warning", severity = "Extreme", urgency = "Immediate"),
                makeFeature(id = "3", event = "Flood Warning", severity = "Severe", urgency = "Immediate"),
            ),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals("Tornado Warning", alerts[0].event)   // Extreme
        assertEquals("Flood Warning", alerts[1].event)      // Severe
        assertEquals("Heat Advisory", alerts[2].event)       // Minor
    }

    @Test
    fun `getAlerts skips features with null event`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                NwsAlertFeature(id = "bad", properties = NwsAlertProperties(event = null)),
                makeFeature(event = "Winter Storm Watch"),
            ),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(1, alerts.size)
        assertEquals("Winter Storm Watch", alerts[0].event)
    }

    @Test
    fun `getAlerts skips features with null properties`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(
                NwsAlertFeature(id = "bad", properties = null),
                makeFeature(),
            ),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(1, alerts.size)
    }

    @Test
    fun `getAlerts returns empty on 404`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 404")

        val result = repository.getAlerts(39.7, -104.9)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `getAlerts returns empty on 400`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } throws Exception("HTTP 400")

        val result = repository.getAlerts(39.7, -104.9)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `getAlerts returns empty for empty response`() = runTest {
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = emptyList(),
        )

        val alerts = repository.getAlerts(39.7, -104.9).getOrThrow()
        assertEquals(0, alerts.size)
    }

    @Test
    fun `getAlerts empty when no adapter matches`() = runTest {
        // Mock Geocoder to return Japan — but only NWS adapter is registered
        val jpAddress = mockk<Address>()
        every { jpAddress.countryCode } returns "JP"
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } returns listOf(jpAddress)

        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse()

        val result = repository.getAlerts(35.6, 139.7) // Tokyo
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `MeteoAlarm short-circuits cleanly when no country code is detected`() = runTest {
        // Pre-fix bug: with ALL_SOURCES (or any pref selecting MeteoAlarm),
        // AlertRepository fell through to `adapter.getAlerts(lat, lon)` when
        // countryCode was null — but that override is a no-op stub returning
        // emptyList(). The fix routes MeteoAlarm through the country-aware
        // path and short-circuits when the country isn't yet known, so the
        // API is never called.
        every { prefs.settings } returns flowOf(
            NimbusSettings(alertSourcePref = AlertSourcePreference.ALL_SOURCES),
        )
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } throws
            RuntimeException("Geocoder unavailable")

        val meteoAlarmApi = mockk<MeteoAlarmApi>()
        val meteoAlarmAdapter = MeteoAlarmAdapter(meteoAlarmApi)
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse()

        val multiSourceRepo = AlertRepository(
            context = context,
            adapters = setOf(nwsAdapter, meteoAlarmAdapter),
            prefs = prefs,
        )

        val result = multiSourceRepo.getAlerts(0.0, 0.0)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify(exactly = 0) { meteoAlarmApi.getWarnings(any()) }
    }

    @Test
    fun `MeteoAlarm only fires for supported European countries`() = runTest {
        // When the detected country is outside MeteoAlarm's EUMETNET
        // coverage (here: US), the adapter is skipped entirely instead of
        // making a doomed `getWarnings("us")` call that would either 404
        // or return junk.
        every { prefs.settings } returns flowOf(
            NimbusSettings(alertSourcePref = AlertSourcePreference.ALL_SOURCES),
        )

        val meteoAlarmApi = mockk<MeteoAlarmApi>()
        val meteoAlarmAdapter = MeteoAlarmAdapter(meteoAlarmApi)
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse()

        val multiSourceRepo = AlertRepository(
            context = context,
            adapters = setOf(nwsAdapter, meteoAlarmAdapter),
            prefs = prefs,
        )

        // setup() already mocks the US country code path
        val result = multiSourceRepo.getAlerts(40.71, -74.01)
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { meteoAlarmApi.getWarnings(any()) }
    }

    @Test
    fun `MeteoAlarm runs for supported European country`() = runTest {
        // Unit tests run with the default JVM timezone; `detectCountry`
        // would otherwise fall through to the timezone heuristic (no
        // Geocoder mock for the API 33+ listener overload), so pin the
        // default zone to Europe/Berlin which maps to "DE" via the
        // existing fallback chain.
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        try {
            every { prefs.settings } returns flowOf(
                NimbusSettings(alertSourcePref = AlertSourcePreference.ALL_SOURCES),
            )
            // Force the Geocoder path to fail so detectCountryFromTimezone
            // takes over with our pinned Europe/Berlin zone.
            every {
                anyConstructed<Geocoder>().getFromLocation(any(), any(), any())
            } throws RuntimeException("Geocoder unavailable in test")

            val meteoAlarmApi = mockk<MeteoAlarmApi>()
            val meteoAlarmAdapter = MeteoAlarmAdapter(meteoAlarmApi)
            coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse()
            coEvery { meteoAlarmApi.getWarnings("de") } returns MeteoAlarmResponse(
                warnings = listOf(
                    MeteoAlarmWarning(
                        identifier = "de-1",
                        info = listOf(
                            MeteoAlarmInfo(
                                event = "Wind",
                                severity = "Severe",
                                urgency = "Immediate",
                                headline = "Sturmwarnung",
                            ),
                        ),
                    ),
                ),
            )

            val multiSourceRepo = AlertRepository(
                context = context,
                adapters = setOf(nwsAdapter, meteoAlarmAdapter),
                prefs = prefs,
            )

            val result = multiSourceRepo.getAlerts(52.52, 13.40) // Berlin
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow().size)
            assertEquals("Wind", result.getOrThrow().first().event)
            coVerify(exactly = 1) { meteoAlarmApi.getWarnings("de") }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun makeGlobalAlert(id: String = "wmo-1") = WeatherAlert(
        id = id,
        event = "Severe Thunderstorm",
        headline = "Severe Thunderstorm Warning",
        description = "Severe storms expected.",
        instruction = null,
        severity = AlertSeverity.SEVERE,
        urgency = AlertUrgency.IMMEDIATE,
        certainty = "Likely",
        senderName = "WMO SWIC",
        areaDescription = "Test Region",
        effective = null,
        expires = null,
        response = null,
    )

    private fun fakeGlobalAdapter(
        alerts: List<WeatherAlert> = emptyList(),
        isMetered: Boolean = false,
    ): AlertSourceAdapter {
        val adapter = mockk<AlertSourceAdapter>()
        every { adapter.sourceId } returns "global_fake"
        every { adapter.displayName } returns "Global Fake"
        every { adapter.supportedRegions } returns setOf("GLOBAL")
        every { adapter.isMetered } returns isMetered
        coEvery { adapter.getAlerts(any(), any()) } returns Result.success(alerts)
        return adapter
    }

    @Test
    fun `AUTO queries regional adapter only when one matches the country`() = runTest {
        // setup() mocks Geocoder → US and prefs → AUTO. The GLOBAL adapter
        // carries the same physical alerts under different IDs, so AUTO must
        // not aggregate it on top of the regional feed.
        val globalAdapter = fakeGlobalAdapter(listOf(makeGlobalAlert()))
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature()),
        )

        val repo = AlertRepository(context, setOf(nwsAdapter, globalAdapter), prefs)
        val result = repo.getAlerts(39.7, -104.9)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("Tornado Warning", result.getOrThrow().first().event)
        coVerify(exactly = 0) { globalAdapter.getAlerts(any(), any()) }
    }

    @Test
    fun `AUTO falls back to GLOBAL adapters when no regional adapter covers the country`() = runTest {
        val brAddress = mockk<Address>()
        every { brAddress.countryCode } returns "BR"
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } returns listOf(brAddress)

        val globalAdapter = fakeGlobalAdapter(listOf(makeGlobalAlert()))

        val repo = AlertRepository(context, setOf(nwsAdapter, globalAdapter), prefs)
        val result = repo.getAlerts(-23.55, -46.63) // São Paulo

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("Severe Thunderstorm", result.getOrThrow().first().event)
        coVerify(exactly = 1) { globalAdapter.getAlerts(any(), any()) }
        coVerify(exactly = 0) { api.getActiveAlerts(any(), any(), any()) }
    }

    @Test
    fun `ALL_SOURCES queries regional and GLOBAL adapters together`() = runTest {
        every { prefs.settings } returns flowOf(
            NimbusSettings(alertSourcePref = AlertSourcePreference.ALL_SOURCES),
        )
        val globalAdapter = fakeGlobalAdapter(listOf(makeGlobalAlert()))
        coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
            features = listOf(makeFeature()),
        )

        val repo = AlertRepository(context, setOf(nwsAdapter, globalAdapter), prefs)
        val result = repo.getAlerts(39.7, -104.9)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        coVerify(exactly = 1) { globalAdapter.getAlerts(any(), any()) }
        coVerify(exactly = 1) { api.getActiveAlerts(any(), any(), any()) }
    }

    @Test
    fun `background-safe fetch excludes metered global adapters`() = runTest {
        val brAddress = mockk<Address>()
        every { brAddress.countryCode } returns "BR"
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } returns listOf(brAddress)

        val freeGlobalAdapter = fakeGlobalAdapter(listOf(makeGlobalAlert(id = "free-global")))
        val meteredGlobalAdapter = fakeGlobalAdapter(
            alerts = listOf(makeGlobalAlert(id = "metered-global")),
            isMetered = true,
        )

        val repo = AlertRepository(context, setOf(nwsAdapter, freeGlobalAdapter, meteredGlobalAdapter), prefs)
        val result = repo.getAlerts(
            latitude = -23.55,
            longitude = -46.63,
            includeMeteredSources = false,
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("free-global"), result.getOrThrow().map { it.id })
        coVerify(exactly = 1) { freeGlobalAdapter.getAlerts(any(), any()) }
        coVerify(exactly = 0) { meteredGlobalAdapter.getAlerts(any(), any()) }
    }

    @Test
    fun `getAlerts does not treat unsupported America timezones as US fallback`() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
        try {
            every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } throws RuntimeException("Geocoder unavailable")
            coEvery { api.getActiveAlerts(any(), any(), any()) } returns NwsAlertResponse(
                features = listOf(makeFeature())
            )

            val result = repository.getAlerts(19.4, -99.1)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            coVerify(exactly = 0) { api.getActiveAlerts(any(), any(), any()) }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    // ── getAlertsDetailed: outage vs. all-clear (safety-of-life) ──────────

    private fun fakeAdapter(
        id: String,
        result: Result<List<WeatherAlert>>? = null,
        throws: Boolean = false,
    ): AlertSourceAdapter {
        val adapter = mockk<AlertSourceAdapter>()
        every { adapter.sourceId } returns id
        every { adapter.displayName } returns id
        every { adapter.supportedRegions } returns setOf("GLOBAL")
        every { adapter.isMetered } returns false
        if (throws) {
            coEvery { adapter.getAlerts(any(), any()) } throws RuntimeException("boom-$id")
        } else {
            coEvery { adapter.getAlerts(any(), any()) } returns result!!
        }
        return adapter
    }

    private fun allSourcesPrefs() {
        every { prefs.settings } returns flowOf(
            NimbusSettings(alertSourcePref = AlertSourcePreference.ALL_SOURCES),
        )
    }

    @Test
    fun `getAlertsDetailed reports total outage when every adapter fails`() = runTest {
        allSourcesPrefs()
        val thrower = fakeAdapter("a1", throws = true)
        val failer = fakeAdapter("a2", result = Result.failure(RuntimeException("net")))

        val repo = AlertRepository(context, setOf(thrower, failer), prefs)
        val result = repo.getAlertsDetailed(39.7, -104.9)

        assertTrue(result.allAdaptersFailed)
        assertEquals(setOf("a1", "a2"), result.failedSources.toSet())
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `getAlertsDetailed flags partial failure with empty result as not-all-clear`() = runTest {
        allSourcesPrefs()
        val ok = fakeAdapter("ok", result = Result.success(emptyList()))
        val bad = fakeAdapter("bad", throws = true)

        val repo = AlertRepository(context, setOf(ok, bad), prefs)
        val result = repo.getAlertsDetailed(39.7, -104.9)

        assertFalse(result.allAdaptersFailed)
        assertEquals(listOf("bad"), result.failedSources)
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `getAlertsDetailed delivers alerts and records the failed source`() = runTest {
        allSourcesPrefs()
        val ok = fakeAdapter("ok", result = Result.success(listOf(makeGlobalAlert())))
        val bad = fakeAdapter("bad", result = Result.failure(RuntimeException("net")))

        val repo = AlertRepository(context, setOf(ok, bad), prefs)
        val result = repo.getAlertsDetailed(39.7, -104.9)

        assertFalse(result.allAdaptersFailed)
        assertEquals(1, result.alerts.size)
        assertEquals(listOf("bad"), result.failedSources)
    }

    @Test
    fun `getAlertsDetailed is not an outage when no adapter covers the location`() = runTest {
        // AUTO + a regional-only adapter for a country it doesn't cover → nothing
        // attempted. That is "no coverage", not a provider outage.
        val brAddress = mockk<Address>()
        every { brAddress.countryCode } returns "BR"
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } returns listOf(brAddress)

        val result = repository.getAlertsDetailed(-23.55, -46.63) // São Paulo, only NWS registered

        assertFalse(result.allAdaptersFailed)
        assertTrue(result.failedSources.isEmpty())
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `getAlerts still returns success-empty when all adapters fail (no behavior change)`() = runTest {
        allSourcesPrefs()
        val thrower = fakeAdapter("a1", throws = true)
        val failer = fakeAdapter("a2", result = Result.failure(RuntimeException("net")))

        val repo = AlertRepository(context, setOf(thrower, failer), prefs)
        val result = repo.getAlerts(39.7, -104.9)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
