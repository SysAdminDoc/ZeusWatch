package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.api.BlitzortungService
import com.sysadmindoc.nimbus.data.model.AlertCoordinate
import com.sysadmindoc.nimbus.data.model.AlertGeometry
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.repository.AlertFetchResult
import com.sysadmindoc.nimbus.data.repository.CommunityReportSource
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.RadarFrameSet
import com.sysadmindoc.nimbus.data.repository.RadarRepository
import com.sysadmindoc.nimbus.data.repository.TimedTileUrl
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import com.sysadmindoc.nimbus.util.ConnectivityObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)

    private lateinit var radarRepository: RadarRepository
    private lateinit var prefs: UserPreferences
    private lateinit var blitzortungService: BlitzortungService
    private lateinit var communityReportRepository: CommunityReportSource
    private lateinit var weatherSourceManager: WeatherSourceManager
    private lateinit var connectivityObserver: ConnectivityObserver

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        radarRepository = mockk()
        prefs = mockk()
        blitzortungService = mockk(relaxed = true)
        communityReportRepository = mockk(relaxed = true)
        weatherSourceManager = mockk(relaxed = true)
        connectivityObserver = mockk()

        every { prefs.settings } returns flowOf(NimbusSettings())
        every { prefs.lastLocation } returns flowOf(null)
        every { blitzortungService.recentStrikes } returns MutableStateFlow(emptyList())
        every { connectivityObserver.isOnline } returns flowOf(true)
        every { communityReportRepository.deviceId } returns "test-device"
        coEvery {
            weatherSourceManager.getAlertsDetailed(any(), any(), any(), includeMeteredSources = false)
        } returns AlertFetchResult(emptyList(), allAdaptersFailed = false, failedSources = emptyList())
        justRun { blitzortungService.connect() }
        justRun { blitzortungService.disconnect() }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `canAnimateRadarPlayback only allows multiple frames`() {
        assertFalse(canAnimateRadarPlayback(null))
        assertFalse(canAnimateRadarPlayback(frameSet(totalFrames = 1)))
        assertTrue(canAnimateRadarPlayback(frameSet(totalFrames = 2)))
    }

    @Test
    fun `shouldLoadRadarFrames skips duplicate refresh while recent data is fresh`() {
        val state = RadarUiState(
            isLoading = false,
            frameSet = frameSet(totalFrames = 3),
            error = null,
        )

        val result = shouldLoadRadarFrames(
            state = state,
            isRequestInFlight = false,
            lastSuccessfulLoadAtMillis = 10_000L,
            nowMillis = 10_500L,
            force = false,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldLoadRadarFrames forces refresh when clock rolls backward`() {
        val state = RadarUiState(
            isLoading = false,
            frameSet = frameSet(totalFrames = 3),
            error = null,
        )

        // Simulate NTP adjustment: "now" is earlier than the last recorded load.
        val result = shouldLoadRadarFrames(
            state = state,
            isRequestInFlight = false,
            lastSuccessfulLoadAtMillis = 1_000_000L,
            nowMillis = 900_000L,
            force = false,
        )

        assertTrue(result)
    }

    @Test
    fun `togglePlayback keeps stopped when fewer than two frames are loaded`() = runTest(scheduler) {
        coEvery { radarRepository.getRadarFrames() } returns Result.success(frameSet(totalFrames = 1))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePlayback()

        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(viewModel.uiState.value.pausedByGesture)
    }

    @Test
    fun `togglePlayback starts playback when multiple frames are available`() = runTest(scheduler) {
        coEvery { radarRepository.getRadarFrames() } returns Result.success(frameSet(totalFrames = 3))
        val viewModel = createViewModel()
        viewModel.loadFrames()
        advanceUntilIdle()

        viewModel.togglePlayback()

        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayback()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `map interaction pauses and resumes playback when animation is available`() = runTest(scheduler) {
        coEvery { radarRepository.getRadarFrames() } returns Result.success(frameSet(totalFrames = 3))
        val viewModel = createViewModel()
        viewModel.loadFrames()
        advanceUntilIdle()

        viewModel.togglePlayback()
        viewModel.onMapInteractionStart()

        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.pausedByGesture)

        viewModel.onMapInteractionEnd()

        assertTrue(viewModel.uiState.value.isPlaying)
        assertFalse(viewModel.uiState.value.pausedByGesture)

        viewModel.togglePlayback()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `loadFrames skips immediate duplicate fetch when frames are already loaded`() = runTest(scheduler) {
        coEvery { radarRepository.getRadarFrames() } returns Result.success(frameSet(totalFrames = 3))
        val viewModel = createViewModel()

        viewModel.loadFrames()
        advanceUntilIdle()

        viewModel.loadFrames()
        advanceUntilIdle()

        coVerify(exactly = 1) { radarRepository.getRadarFrames() }
    }

    @Test
    fun `loadFrames force reload bypasses duplicate fetch guard`() = runTest(scheduler) {
        coEvery { radarRepository.getRadarFrames() } returns Result.success(frameSet(totalFrames = 3))
        val viewModel = createViewModel()

        viewModel.loadFrames()
        advanceUntilIdle()

        viewModel.loadFrames(force = true)
        advanceUntilIdle()

        coVerify(exactly = 2) { radarRepository.getRadarFrames() }
    }

    @Test
    fun `forced reload cancels the in-flight request so a stale response cannot clobber newer state`() =
        runTest(scheduler) {
            var calls = 0
            coEvery { radarRepository.getRadarFrames() } coAnswers {
                calls++
                if (calls == 1) {
                    // Slow first request: still in flight when the forced reload lands.
                    delay(60_000L)
                    Result.success(frameSet(totalFrames = 2))
                } else {
                    Result.success(frameSet(totalFrames = 3))
                }
            }
            val viewModel = createViewModel()

            viewModel.loadFrames()
            scheduler.runCurrent()

            viewModel.loadFrames(force = true)
            advanceUntilIdle()

            // The stale 2-frame response must not overwrite the forced 3-frame result.
            assertEquals(3, viewModel.uiState.value.totalFrames)
            coVerify(exactly = 2) { radarRepository.getRadarFrames() }
        }

    @Test
    fun `creating the view model does not eagerly fetch radar frames`() = runTest(scheduler) {
        coEvery { radarRepository.getRadarFrames() } returns Result.success(frameSet(totalFrames = 3))

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { radarRepository.getRadarFrames() }
    }

    @Test
    fun `loadAlertOverlays keeps active polygon alerts and drops text-only alerts`() = runTest(scheduler) {
        val polygonAlert = weatherAlert(id = "polygon", geometry = alertGeometry())
        val textOnlyAlert = weatherAlert(id = "text-only", geometry = null)
        coEvery {
            weatherSourceManager.getAlertsDetailed(39.0, -104.0, any(), includeMeteredSources = false)
        } returns AlertFetchResult(
            alerts = listOf(polygonAlert, textOnlyAlert),
            allAdaptersFailed = false,
            failedSources = emptyList(),
        )
        val viewModel = createViewModel()

        viewModel.loadAlertOverlays(39.0, -104.0)
        advanceUntilIdle()

        assertEquals(listOf("polygon"), viewModel.uiState.value.alertOverlays.map { it.id })
        assertEquals(null, viewModel.uiState.value.alertOverlayError)
    }

    @Test
    fun `loadAlertOverlays drops expired polygon alerts`() = runTest(scheduler) {
        coEvery {
            weatherSourceManager.getAlertsDetailed(39.0, -104.0, any(), includeMeteredSources = false)
        } returns AlertFetchResult(
            alerts = listOf(
                weatherAlert(
                    id = "expired",
                    geometry = alertGeometry(),
                    expires = "2020-01-01T00:00:00Z",
                )
            ),
            allAdaptersFailed = false,
            failedSources = emptyList(),
        )
        val viewModel = createViewModel()

        viewModel.loadAlertOverlays(39.0, -104.0)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.alertOverlays.isEmpty())
    }

    @Test
    fun `loadAlertOverlays marks total provider outage without treating it as clear`() = runTest(scheduler) {
        coEvery {
            weatherSourceManager.getAlertsDetailed(39.0, -104.0, any(), includeMeteredSources = false)
        } returns AlertFetchResult(
            alerts = emptyList(),
            allAdaptersFailed = true,
            failedSources = listOf("nws"),
        )
        val viewModel = createViewModel()

        viewModel.loadAlertOverlays(39.0, -104.0)
        advanceUntilIdle()

        assertEquals(ALERT_OVERLAY_FAILED, viewModel.uiState.value.alertOverlayError)
        assertEquals(listOf("nws"), viewModel.uiState.value.alertOverlayFailedSources)
    }

    private fun createViewModel(): RadarViewModel {
        return RadarViewModel(
            radarRepository = radarRepository,
            prefs = prefs,
            blitzortungService = blitzortungService,
            communityReportRepository = communityReportRepository,
            weatherSourceManager = weatherSourceManager,
            connectivityObserver = connectivityObserver,
        )
    }

    private fun frameSet(totalFrames: Int): RadarFrameSet {
        val frames = List(totalFrames) { index ->
            TimedTileUrl(
                timestamp = 1_000L + index,
                tileUrl = "https://example.com/$index",
                isPast = index == 0,
            )
        }
        val pastCount = if (totalFrames > 0) 1 else 0
        return RadarFrameSet(
            past = frames.take(pastCount),
            forecast = frames.drop(pastCount),
        )
    }

    private fun weatherAlert(
        id: String,
        geometry: AlertGeometry?,
        expires: String = "2099-01-01T00:00:00Z",
    ): WeatherAlert {
        return WeatherAlert(
            id = id,
            event = "Tornado Warning",
            headline = "Tornado Warning issued",
            description = "Take shelter now.",
            instruction = "Move to an interior room.",
            severity = AlertSeverity.SEVERE,
            urgency = AlertUrgency.IMMEDIATE,
            certainty = "Observed",
            senderName = "National Weather Service",
            areaDescription = "Test County",
            effective = "2026-06-17T00:00:00Z",
            expires = expires,
            response = "Shelter",
            geometry = geometry,
            coversRequestedLocation = true,
        )
    }

    private fun alertGeometry(): AlertGeometry {
        return AlertGeometry(
            polygons = listOf(
                AlertPolygon(
                    points = listOf(
                        AlertCoordinate(latitude = 39.0, longitude = -105.0),
                        AlertCoordinate(latitude = 39.0, longitude = -104.0),
                        AlertCoordinate(latitude = 40.0, longitude = -104.0),
                        AlertCoordinate(latitude = 40.0, longitude = -105.0),
                        AlertCoordinate(latitude = 39.0, longitude = -105.0),
                    ),
                ),
            ),
        )
    }
}
