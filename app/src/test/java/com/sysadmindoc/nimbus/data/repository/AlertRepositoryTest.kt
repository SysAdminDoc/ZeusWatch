package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.NwsAlertAdapter
import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.NwsAlertFeature
import com.sysadmindoc.nimbus.data.api.NwsAlertProperties
import com.sysadmindoc.nimbus.data.api.NwsAlertResponse
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

    private val testDispatcher = UnconfinedTestDispatcher()

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

        // Mock Geocoder to return US for default test coordinates
        mockkConstructor(Geocoder::class)
        val usAddress = mockk<Address>()
        every { usAddress.countryCode } returns "US"
        every { anyConstructed<Geocoder>().getFromLocation(any(), any(), any()) } returns listOf(usAddress)

        val adapters: Set<AlertSourceAdapter> = setOf(nwsAdapter)
        repository = AlertRepository(context, adapters, prefs)
    }

    @After
    fun teardown() {
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
}
