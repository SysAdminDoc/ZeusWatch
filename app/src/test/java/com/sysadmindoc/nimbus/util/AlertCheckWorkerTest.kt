package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.repository.AlertRepository
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlertCheckWorkerTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var alertRepository: AlertRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var prefs: UserPreferences

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        params = mockk(relaxed = true)
        alertRepository = mockk()
        locationRepository = mockk()
        prefs = mockk(relaxed = true)
        mockkObject(AlertNotificationHelper)
        every { AlertNotificationHelper.showAlertNotification(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AlertNotificationHelper)
    }

    @Test
    fun `doWork only marks alerts as seen when they are actually notified`() = runTest {
        val lowSeverityAlert = testAlert(id = "minor-1", severity = AlertSeverity.MINOR)
        every { prefs.settings } returns flowOf(
            NimbusSettings(
                alertNotificationsEnabled = true,
                alertMinSeverity = com.sysadmindoc.nimbus.data.repository.AlertMinSeverity.SEVERE,
                alertCheckAllLocations = false,
            )
        )
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { prefs.getSeenAlertIds() } returns emptySet()
        coEvery { alertRepository.getAlerts(any(), any()) } returns Result.success(listOf(lowSeverityAlert))

        val worker = AlertCheckWorker(context, params, alertRepository, locationRepository, prefs)
        worker.doWork()

        coVerify(exactly = 0) { prefs.addSeenAlertIds(any()) }
        io.mockk.verify(exactly = 0) { AlertNotificationHelper.showAlertNotification(any(), any(), any()) }
    }

    @Test
    fun `doWork deduplicates identical alerts across monitored locations in the same run`() = runTest {
        val severeAlert = testAlert(id = "shared-1", severity = AlertSeverity.SEVERE)
        every { prefs.settings } returns flowOf(
            NimbusSettings(
                alertNotificationsEnabled = true,
                alertMinSeverity = com.sysadmindoc.nimbus.data.repository.AlertMinSeverity.SEVERE,
                alertCheckAllLocations = true,
            )
        )
        coEvery { prefs.getSeenAlertIds() } returns emptySet()
        coEvery { locationRepository.getAll() } returns listOf(
            com.sysadmindoc.nimbus.data.model.SavedLocationEntity(
                id = 1,
                name = "Denver",
                latitude = 39.7,
                longitude = -104.9,
                sortOrder = 0,
            ),
            com.sysadmindoc.nimbus.data.model.SavedLocationEntity(
                id = 2,
                name = "Boulder",
                latitude = 40.0,
                longitude = -105.3,
                sortOrder = 1,
            ),
        )
        coEvery { alertRepository.getAlerts(any(), any()) } returns Result.success(listOf(severeAlert))

        val worker = AlertCheckWorker(context, params, alertRepository, locationRepository, prefs)
        worker.doWork()

        io.mockk.verify(exactly = 1) { AlertNotificationHelper.showAlertNotification(any(), any(), any()) }
        coVerify(exactly = 1) { prefs.addSeenAlertIds(setOf("shared-1")) }
    }

    @Test
    fun `doWork only fetches alerts once per unique coordinate set`() = runTest {
        every { prefs.settings } returns flowOf(
            NimbusSettings(
                alertNotificationsEnabled = true,
                alertMinSeverity = com.sysadmindoc.nimbus.data.repository.AlertMinSeverity.SEVERE,
                alertCheckAllLocations = true,
            )
        )
        coEvery { prefs.getSeenAlertIds() } returns emptySet()
        coEvery { locationRepository.getAll() } returns listOf(
            com.sysadmindoc.nimbus.data.model.SavedLocationEntity(
                id = 1,
                name = "My Location",
                latitude = 39.73921,
                longitude = -104.99031,
                isCurrentLocation = true,
                sortOrder = -1,
            ),
            com.sysadmindoc.nimbus.data.model.SavedLocationEntity(
                id = 2,
                name = "Denver",
                latitude = 39.73924,
                longitude = -104.99034,
                sortOrder = 0,
            ),
            com.sysadmindoc.nimbus.data.model.SavedLocationEntity(
                id = 3,
                name = "Boulder",
                latitude = 40.01499,
                longitude = -105.27050,
                sortOrder = 1,
            ),
        )
        coEvery { alertRepository.getAlerts(any(), any()) } returns Result.success(emptyList())

        val worker = AlertCheckWorker(context, params, alertRepository, locationRepository, prefs)
        worker.doWork()

        coVerify(exactly = 2) { alertRepository.getAlerts(any(), any()) }
        coVerify(exactly = 1) { alertRepository.getAlerts(39.73921, -104.99031) }
        coVerify(exactly = 1) { alertRepository.getAlerts(40.01499, -105.27050) }
    }

    @Test
    fun `distinctAlertCheckLocations keeps first entry for rounded duplicate coordinates`() {
        val distinct = distinctAlertCheckLocations(
            listOf(
                Triple(39.73921, -104.99031, "My Location"),
                Triple(39.73924, -104.99034, "Denver"),
                Triple(40.01499, -105.27050, "Boulder"),
            )
        )

        assertEquals(
            listOf(
                Triple(39.73921, -104.99031, "My Location"),
                Triple(40.01499, -105.27050, "Boulder"),
            ),
            distinct,
        )
    }

    private fun testAlert(
        id: String,
        severity: AlertSeverity,
    ): WeatherAlert {
        return WeatherAlert(
            id = id,
            event = "Test Alert",
            headline = "Heads up",
            description = "Test description",
            instruction = "Take action",
            severity = severity,
            urgency = AlertUrgency.IMMEDIATE,
            certainty = "Observed",
            senderName = "NWS",
            areaDescription = "Test Area",
            effective = "2026-04-11T12:00:00Z",
            expires = "2099-04-11T14:00:00Z",
            response = "Shelter",
        )
    }
}
