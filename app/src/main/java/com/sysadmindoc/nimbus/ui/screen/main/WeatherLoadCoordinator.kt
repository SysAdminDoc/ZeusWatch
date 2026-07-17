package com.sysadmindoc.nimbus.ui.screen.main

import android.content.Context
import android.util.Log
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.AirQualityRepository
import com.sysadmindoc.nimbus.data.repository.AuroraRepository
import com.sysadmindoc.nimbus.data.repository.CardType
import com.sysadmindoc.nimbus.data.repository.ClimateRepository
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandRepository
import com.sysadmindoc.nimbus.data.repository.FloodRepository
import com.sysadmindoc.nimbus.data.repository.ForecastAccuracyRepository
import com.sysadmindoc.nimbus.data.repository.ForecastEvolutionRepository
import com.sysadmindoc.nimbus.data.repository.MarineRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TimeTravelRepository
import com.sysadmindoc.nimbus.data.repository.OnThisDayRepository
import com.sysadmindoc.nimbus.data.repository.ProviderAgreementAnalyzer
import com.sysadmindoc.nimbus.data.repository.ProviderWeatherSnapshot
import com.sysadmindoc.nimbus.data.repository.PwsRepository
import com.sysadmindoc.nimbus.data.repository.RadarRepository
import com.sysadmindoc.nimbus.data.repository.SourceOverrides
import com.sysadmindoc.nimbus.data.repository.SummaryStyle
import com.sysadmindoc.nimbus.data.repository.TempestPwsConfigurationException
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.WeatherDataType
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import com.sysadmindoc.nimbus.data.repository.toZoneIdOrNull
import com.sysadmindoc.nimbus.di.DefaultDispatcher
import com.sysadmindoc.nimbus.sync.WearSyncManager
import com.sysadmindoc.nimbus.ui.component.TimeTravelStatus
import com.sysadmindoc.nimbus.util.ActivityIndexEvaluator
import com.sysadmindoc.nimbus.util.ClothingSuggestion
import com.sysadmindoc.nimbus.util.ClothingSuggestionEvaluator
import com.sysadmindoc.nimbus.util.DrivingAlert
import com.sysadmindoc.nimbus.util.DrivingConditionEvaluator
import com.sysadmindoc.nimbus.util.GadgetbridgeWeatherBroadcaster
import com.sysadmindoc.nimbus.util.HealthAlert
import com.sysadmindoc.nimbus.util.HealthAlertEvaluator
import com.sysadmindoc.nimbus.util.PetSafetyAlert
import com.sysadmindoc.nimbus.util.PetSafetyEvaluator
import com.sysadmindoc.nimbus.util.SummaryEngine
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.WeatherNotificationHelper
import com.sysadmindoc.nimbus.util.WeatherSummaryEngine
import com.sysadmindoc.nimbus.wallpaper.WeatherWallpaperService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "WeatherLoadCoordinator"

data class WeatherLoadCoreDependencies @Inject constructor(
    @ApplicationContext val appContext: Context,
    val repository: WeatherRepository,
    val weatherSourceManager: WeatherSourceManager,
    val radarRepository: RadarRepository,
    val airQualityRepository: AirQualityRepository,
    val summaryEngine: SummaryEngine,
    val wearSyncManager: WearSyncManager,
    val gadgetbridgeBroadcaster: GadgetbridgeWeatherBroadcaster,
    @DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
)

data class WeatherLoadOptionalRepositories @Inject constructor(
    val onThisDayRepository: OnThisDayRepository,
    val forecastEvolutionRepository: ForecastEvolutionRepository,
    val forecastAccuracyRepository: ForecastAccuracyRepository,
    val confidenceBandRepository: ConfidenceBandRepository,
    val auroraRepository: AuroraRepository,
    val marineRepository: MarineRepository,
    val floodRepository: FloodRepository,
    val climateRepository: ClimateRepository,
    val pwsRepository: PwsRepository,
    val timeTravelRepository: TimeTravelRepository,
)

data class WeatherLoadCompletion(
    val lat: Double,
    val lon: Double,
    val requestId: Long,
    val data: WeatherData,
    val sourceOverrides: SourceOverrides,
    val alertCountryHint: String?,
)

data class WeatherLoadCallbacks(
    val scope: CoroutineScope,
    val currentState: () -> MainUiState,
    val updateState: ((MainUiState) -> MainUiState) -> Unit,
    val isLatestRequest: (Long) -> Boolean,
)

class WeatherLoadCoordinator @Inject constructor(
    core: WeatherLoadCoreDependencies,
    optionalRepositories: WeatherLoadOptionalRepositories,
) {
    private val appContext = core.appContext
    private val repository = core.repository
    private val weatherSourceManager = core.weatherSourceManager
    private val radarRepository = core.radarRepository
    private val airQualityRepository = core.airQualityRepository
    private val summaryEngine = core.summaryEngine
    private val wearSyncManager = core.wearSyncManager
    private val gadgetbridgeBroadcaster = core.gadgetbridgeBroadcaster
    private val defaultDispatcher = core.defaultDispatcher
    private val onThisDayRepository = optionalRepositories.onThisDayRepository
    private val timeTravelRepository = optionalRepositories.timeTravelRepository
    private val forecastEvolutionRepository = optionalRepositories.forecastEvolutionRepository
    private val forecastAccuracyRepository = optionalRepositories.forecastAccuracyRepository
    private val confidenceBandRepository = optionalRepositories.confidenceBandRepository
    private val auroraRepository = optionalRepositories.auroraRepository
    private val marineRepository = optionalRepositories.marineRepository
    private val floodRepository = optionalRepositories.floodRepository
    private val climateRepository = optionalRepositories.climateRepository
    private val pwsRepository = optionalRepositories.pwsRepository

    // The on-device AI summary can take seconds; a rapid location/tab switch must
    // cancel the previous generation so jobs don't stack on the default dispatcher.
    private var aiSummaryJob: Job? = null

    suspend fun finishSuccessfulWeatherLoad(
        completion: WeatherLoadCompletion,
        callbacks: WeatherLoadCallbacks,
    ) {
        val requestId = completion.requestId
        val data = completion.data
        val currentState = callbacks.currentState
        val updateState = callbacks.updateState
        val isLatestRequest = callbacks.isLatestRequest
        try {
            publishWallpaperWeather(data)
            fetchSupplementalWeatherData(
                lat = completion.lat,
                lon = completion.lon,
                requestId = requestId,
                sourceOverrides = completion.sourceOverrides,
                alertCountryHint = completion.alertCountryHint,
                data = data,
                currentState = currentState,
                updateState = updateState,
                isLatestRequest = isLatestRequest,
            )
            if (!isLatestRequest(requestId)) return
            syncWeather(data, currentState)
            broadcastWeather(data, currentState)
            fetchYesterdayComparison(completion.lat, completion.lon, requestId, updateState, isLatestRequest)
            if (!isLatestRequest(requestId)) return
            recomputeDerivedData(data, requestId, callbacks.scope, currentState, updateState, isLatestRequest)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "finishSuccessfulWeatherLoad failed", e)
        }
    }

    suspend fun recomputeDerivedData(
        data: WeatherData,
        requestId: Long,
        scope: CoroutineScope,
        currentState: () -> MainUiState,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        val settings = currentState().settings
        val stateSnapshot = currentState()
        val derived = withContext(defaultDispatcher) {
            val templateSummary = WeatherSummaryEngine.generate(
                current = data.current,
                today = data.daily.firstOrNull(),
                hourly = data.hourly,
                yesterdayHigh = stateSnapshot.yesterdayHigh,
                s = settings,
                context = appContext,
            )

            val outdoorBreakdown = WeatherFormatter.outdoorActivityScore(
                tempCelsius = data.current.temperature,
                humidity = data.current.humidity,
                windKmh = data.current.windSpeed,
                uvIndex = data.current.uvIndex,
                precipProbability = data.daily.firstOrNull()?.precipitationProbability ?: 0,
                aqi = stateSnapshot.airQuality?.usAqi,
            )

            val drivingAlerts = if (settings.drivingAlerts) {
                DrivingConditionEvaluator.evaluate(data.current)
            } else {
                persistentListOf()
            }

            val healthAlerts = if (settings.healthAlertsEnabled) {
                HealthAlertEvaluator.evaluate(
                    hourly = data.hourly,
                    pressureThresholdHpa = settings.migrainePressureThreshold,
                    enableMigraine = settings.migraineAlerts,
                )
            } else {
                persistentListOf()
            }

            val activityIndices = if (settings.isCardEnabled(CardType.ACTIVITY_INDEX)) {
                ActivityIndexEvaluator.evaluate(
                    current = data.current,
                    precipProbability = data.daily.firstOrNull()?.precipitationProbability ?: 0,
                    aqi = stateSnapshot.airQuality?.usAqi,
                )
            } else {
                emptyList()
            }

            DerivedWeatherState(
                weatherSummary = templateSummary,
                outdoorScore = outdoorBreakdown,
                drivingAlerts = drivingAlerts.toImmutableList(),
                healthAlerts = healthAlerts.toImmutableList(),
                clothingSuggestions = ClothingSuggestionEvaluator.evaluate(data.current).toImmutableList(),
                petSafetyAlerts = PetSafetyEvaluator.evaluate(data.current).toImmutableList(),
                goldenHourTimes = WeatherFormatter.goldenHourTimes(
                    data.current.sunrise,
                    data.current.sunset,
                    settings,
                ),
                activityIndices = activityIndices.toImmutableList(),
            )
        }

        if (!isLatestRequest(requestId)) return

        updateState {
            it.copy(
                weatherSummary = derived.weatherSummary,
                outdoorScore = derived.outdoorScore,
                drivingAlerts = derived.drivingAlerts,
                healthAlerts = derived.healthAlerts,
                clothingSuggestions = derived.clothingSuggestions,
                petSafetyAlerts = derived.petSafetyAlerts,
                goldenHourTimes = derived.goldenHourTimes,
                activityIndices = derived.activityIndices,
            )
        }

        if (settings.summaryStyle == SummaryStyle.AI_GENERATED) {
            aiSummaryJob?.cancel()
            aiSummaryJob = scope.launch {
                val aiSummary = withContext(defaultDispatcher) {
                    WeatherSummaryEngine.generateWithStyle(
                        current = data.current,
                        today = data.daily.firstOrNull(),
                        hourly = data.hourly,
                        yesterdayHigh = currentState().yesterdayHigh,
                        s = settings,
                        aiEngine = summaryEngine,
                        context = appContext,
                    )
                }
                if (isLatestRequest(requestId)) {
                    updateState { it.copy(weatherSummary = aiSummary) }
                }
            }
        }

        if (settings.persistentWeatherNotif && isLatestRequest(requestId)) {
            WeatherNotificationHelper.showOrUpdate(appContext, data, settings)
        }
    }

    suspend fun fetchForecastEvolution(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        if (!isLatestRequest(requestId)) return
        updateState {
            it.copy(
                isForecastEvolutionLoading = true,
                forecastEvolutionUnavailable = false,
                forecastEvolution = null,
            )
        }
        try {
            forecastEvolutionRepository.getForecastEvolution(lat, lon).fold(
                onSuccess = { evolution ->
                    if (isLatestRequest(requestId)) {
                        updateState {
                            it.copy(
                                forecastEvolution = evolution,
                                isForecastEvolutionLoading = false,
                                forecastEvolutionUnavailable = false,
                            )
                        }
                    }
                },
                onFailure = { e ->
                    if (isLatestRequest(requestId)) {
                        Log.w(TAG, "Forecast evolution unavailable", e)
                        updateState {
                            it.copy(
                                forecastEvolution = null,
                                isForecastEvolutionLoading = false,
                                forecastEvolutionUnavailable = true,
                            )
                        }
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchForecastEvolution failed", e)
            if (isLatestRequest(requestId)) {
                updateState {
                    it.copy(
                        forecastEvolution = null,
                        isForecastEvolutionLoading = false,
                        forecastEvolutionUnavailable = true,
                    )
                }
            }
        }
    }

    suspend fun selectHistoricalDate(
        date: LocalDate,
        weather: WeatherData,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        if (!isLatestRequest(requestId)) return
        updateState { it.copy(timeTravelStatus = TimeTravelStatus.LOADING) }
        withContext(defaultDispatcher) {
            try {
                val data = onThisDayRepository.getOnThisDay(
                    weather.location.latitude,
                    weather.location.longitude,
                    date,
                )
                // Also resolve the actual weather for the exact selected date
                // (time-travel scrub) — the archive observation for a single past
                // day, distinct from the "on this day across years" aggregate.
                // "Today" is anchored to the viewed location's timezone so the
                // archive/forecast routing matches what that location's calendar says.
                val locationZone = weather.location.timeZone.toZoneIdOrNull() ?: ZoneId.systemDefault()
                val exactDay = timeTravelRepository.getDay(
                    latitude = weather.location.latitude,
                    longitude = weather.location.longitude,
                    date = date,
                    forecastDaily = weather.daily,
                    today = LocalDate.now(locationZone),
                )
                if (!isLatestRequest(requestId)) return@withContext
                if (exactDay == null) {
                    // Keep the previous onThisDay/timeTravelDay — wiping them would
                    // drop the card's own "explore dates" entry point.
                    updateState { it.copy(timeTravelStatus = TimeTravelStatus.DATE_UNAVAILABLE) }
                } else {
                    updateState {
                        it.copy(
                            onThisDay = data ?: it.onThisDay,
                            timeTravelDay = exactDay,
                            timeTravelStatus = TimeTravelStatus.IDLE,
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "selectHistoricalDate failed: ${e.message}")
                if (isLatestRequest(requestId)) {
                    updateState { it.copy(timeTravelStatus = TimeTravelStatus.ERROR) }
                }
            }
        }
    }

    private fun publishWallpaperWeather(data: WeatherData) {
        try {
            WeatherWallpaperService.publishWeatherCode(appContext, data.current.weatherCode.code)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Wallpaper weather publish failed", e)
        }
    }

    private suspend fun syncWeather(data: WeatherData, currentState: () -> MainUiState) {
        try {
            val state = currentState()
            wearSyncManager.syncWeather(
                data = data,
                // A failed alert fetch leaves `alerts` empty with the failure
                // flag set — syncing that empty list would make the watch
                // clear live alerts as "fetched, none active". null omits the
                // key so the watch keeps its last known set. (A failed AQI
                // fetch already leaves `airQuality` null, which the sync
                // treats the same way.)
                alerts = if (state.alertsFetchFailed) null else state.alerts,
                airQuality = state.airQuality,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Wear sync failed", e)
        }
    }

    private fun broadcastWeather(data: WeatherData, currentState: () -> MainUiState) {
        if (currentState().settings.gadgetbridgeBroadcastEnabled) {
            try {
                gadgetbridgeBroadcaster.broadcast(data)
            } catch (e: Exception) {
                Log.w(TAG, "Gadgetbridge broadcast failed", e)
            }
        }
    }

    private suspend fun fetchSupplementalWeatherData(
        lat: Double,
        lon: Double,
        requestId: Long,
        sourceOverrides: SourceOverrides,
        alertCountryHint: String?,
        data: WeatherData,
        currentState: () -> MainUiState,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        coroutineScope {
            launch { fetchAlerts(lat, lon, requestId, sourceOverrides, alertCountryHint, updateState, isLatestRequest) }
            launch { fetchAirQuality(lat, lon, requestId, updateState, isLatestRequest) }
            launch {
                fetchOnThisDay(
                    lat = lat,
                    lon = lon,
                    referenceDate = data.current.observationTime?.toLocalDate(),
                    requestId = requestId,
                    currentState = currentState,
                    updateState = updateState,
                    isLatestRequest = isLatestRequest,
                )
            }
            launch { fetchAstronomy(data, lat, lon, requestId, updateState, isLatestRequest) }
            launch { fetchRadarPreview(lat, lon, requestId, currentState, updateState, isLatestRequest) }
            launch { fetchNowcast(lat, lon, requestId, updateState, isLatestRequest) }
            if (currentState().settings.isCardEnabled(CardType.FORECAST_EVOLUTION)) {
                launch { fetchForecastEvolution(lat, lon, requestId, updateState, isLatestRequest) }
            }
            if (currentState().settings.isCardEnabled(CardType.PROVIDER_AGREEMENT)) {
                launch {
                    fetchProviderAgreement(
                        lat = lat,
                        lon = lon,
                        sourceOverrides = sourceOverrides,
                        data = data,
                        settings = currentState().settings,
                        requestId = requestId,
                        updateState = updateState,
                        isLatestRequest = isLatestRequest,
                    )
                }
            }
            if (currentState().settings.isCardEnabled(CardType.PWS_OBSERVATION)) {
                launch {
                    fetchPwsObservation(
                        settings = currentState().settings,
                        requestId = requestId,
                        updateState = updateState,
                        isLatestRequest = isLatestRequest,
                    )
                }
            }
            if (currentState().settings.isCardEnabled(CardType.AURORA_KP)) {
                launch { fetchAuroraKp(requestId, updateState, isLatestRequest) }
            }
            if (currentState().settings.isCardEnabled(CardType.MARINE)) {
                launch { fetchMarine(lat, lon, requestId, updateState, isLatestRequest) }
            }
            if (currentState().settings.isCardEnabled(CardType.FLOOD_RISK)) {
                launch { fetchFlood(lat, lon, requestId, updateState, isLatestRequest) }
            }
            if (currentState().settings.showForecastAccuracy) {
                launch { fetchForecastAccuracy(lat, lon, requestId, updateState, isLatestRequest) }
            }
            if (currentState().settings.showConfidenceBands) {
                launch { fetchConfidenceBands(lat, lon, requestId, updateState, isLatestRequest) }
            }
            if (currentState().settings.isCardEnabled(CardType.CLIMATE_OUTLOOK)) {
                launch { fetchClimateOutlook(lat, lon, requestId, updateState, isLatestRequest) }
            }
        }
    }

    suspend fun fetchProviderAgreement(
        lat: Double,
        lon: Double,
        sourceOverrides: SourceOverrides,
        data: WeatherData,
        settings: NimbusSettings,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        if (!isLatestRequest(requestId)) return
        updateState {
            it.copy(
                providerAgreement = null,
                isProviderAgreementLoading = true,
                providerAgreementUnavailable = false,
            )
        }

        // Guarded like the sibling supplemental fetches: an unexpected throw
        // here would otherwise cancel the whole coroutineScope (alerts, AQI)
        // and skip wear sync / derived recompute for the entire load.
        try {
            val primaryProvider = actualForecastProvider(
                data = data,
                settings = settings,
                sourceOverrides = sourceOverrides,
            )
            val candidates = providerAgreementCandidates(primaryProvider)
            if (candidates.size < 2) {
                markProviderAgreementUnavailable(requestId, updateState, isLatestRequest)
                return
            }

            val snapshots = mutableListOf(ProviderWeatherSnapshot(primaryProvider, data))
            val additionalSnapshots = coroutineScope {
                candidates
                    .filterNot { it == primaryProvider }
                    .map { provider ->
                        async {
                            provider to weatherSourceManager.getWeatherFromProvider(
                                provider = provider,
                                latitude = lat,
                                longitude = lon,
                                locationName = data.location.name,
                                locationTimeZone = data.location.timeZone,
                            )
                        }
                    }
                    .awaitAll()
            }.mapNotNull { (provider, result) ->
                result.fold(
                    onSuccess = { ProviderWeatherSnapshot(provider, it) },
                    onFailure = {
                        Log.d(TAG, "Provider agreement ${provider.displayName} unavailable: ${it.message}")
                        null
                    },
                )
            }
            snapshots += additionalSnapshots

            val agreement = withContext(defaultDispatcher) {
                ProviderAgreementAnalyzer.analyze(
                    forecasts = snapshots,
                    referenceTime = data.current.observationTime,
                )
            }

            if (!isLatestRequest(requestId)) return
            updateState {
                it.copy(
                    providerAgreement = agreement,
                    isProviderAgreementLoading = false,
                    providerAgreementUnavailable = agreement == null,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchProviderAgreement failed", e)
            markProviderAgreementUnavailable(requestId, updateState, isLatestRequest)
        }
    }

    private fun actualForecastProvider(
        data: WeatherData,
        settings: NimbusSettings,
        sourceOverrides: SourceOverrides,
    ): WeatherSourceProvider {
        return WeatherSourceProvider.entries
            .firstOrNull { it.displayName == data.sourceProvider }
            ?.takeIf { it.isSelectableFor(WeatherDataType.FORECAST) }
            ?: settings.sourceConfig.withOverrides(sourceOverrides).forecast
    }

    private fun providerAgreementCandidates(primaryProvider: WeatherSourceProvider): List<WeatherSourceProvider> =
        listOf(
            primaryProvider,
            WeatherSourceProvider.OPEN_METEO,
            WeatherSourceProvider.MET_NORWAY,
        )
            .filter { it.isSelectableFor(WeatherDataType.FORECAST) }
            .distinct()
            .take(3)

    private fun markProviderAgreementUnavailable(
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        if (isLatestRequest(requestId)) {
            updateState {
                it.copy(
                    providerAgreement = null,
                    isProviderAgreementLoading = false,
                    providerAgreementUnavailable = true,
                )
            }
        }
    }

    suspend fun fetchPwsObservation(
        settings: NimbusSettings,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        if (!isLatestRequest(requestId)) return
        updateState {
            it.copy(
                pwsObservation = null,
                isPwsObservationLoading = true,
                pwsObservationUnavailable = false,
                pwsObservationNeedsConfig = false,
            )
        }
        try {
            pwsRepository.fetchLatestObservation(settings).fold(
                onSuccess = { observation ->
                    if (isLatestRequest(requestId)) {
                        updateState {
                            it.copy(
                                pwsObservation = observation,
                                isPwsObservationLoading = false,
                                pwsObservationUnavailable = false,
                                pwsObservationNeedsConfig = false,
                            )
                        }
                    }
                },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    Log.d(TAG, "Tempest PWS observation unavailable: ${failure::class.java.simpleName}")
                    if (isLatestRequest(requestId)) {
                        updateState {
                            it.copy(
                                pwsObservation = null,
                                isPwsObservationLoading = false,
                                pwsObservationUnavailable = failure !is TempestPwsConfigurationException,
                                pwsObservationNeedsConfig = failure is TempestPwsConfigurationException,
                            )
                        }
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchPwsObservation failed", e)
            if (isLatestRequest(requestId)) {
                updateState {
                    it.copy(
                        pwsObservation = null,
                        isPwsObservationLoading = false,
                        pwsObservationUnavailable = true,
                        pwsObservationNeedsConfig = false,
                    )
                }
            }
        }
    }

    private suspend fun fetchAlerts(
        lat: Double,
        lon: Double,
        requestId: Long,
        sourceOverrides: SourceOverrides,
        countryHint: String?,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            weatherSourceManager.getAlerts(
                lat,
                lon,
                sourceOverrides,
                countryHint = countryHint,
            ).fold(
                onSuccess = {
                    if (isLatestRequest(requestId)) {
                        updateState { state ->
                            state.copy(
                                alerts = it.toImmutableList(),
                                alertsUpdatedAt = LocalDateTime.now(),
                                alertsFetchFailed = false,
                            )
                        }
                    }
                },
                onFailure = {
                    if (isLatestRequest(requestId)) {
                        updateState { state ->
                            state.copy(
                                alerts = persistentListOf(),
                                alertsFetchFailed = true,
                            )
                        }
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isLatestRequest(requestId)) {
                updateState {
                    it.copy(
                        alerts = persistentListOf(),
                        alertsFetchFailed = true,
                    )
                }
            }
        }
    }

    private suspend fun fetchAirQuality(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            weatherSourceManager.getAirQuality(lat, lon).fold(
                onSuccess = {
                    if (isLatestRequest(requestId)) {
                        updateState { state ->
                            state.copy(
                                airQuality = it,
                                airQualityUpdatedAt = LocalDateTime.now(),
                                airQualityFetchFailed = false,
                            )
                        }
                    }
                },
                onFailure = {
                    if (isLatestRequest(requestId)) {
                        updateState { state ->
                            state.copy(
                                airQuality = null,
                                airQualityFetchFailed = true,
                            )
                        }
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchAirQuality failed", e)
            if (isLatestRequest(requestId)) {
                updateState {
                    it.copy(
                        airQuality = null,
                        airQualityFetchFailed = true,
                    )
                }
            }
        }
    }

    private fun fetchAstronomy(
        data: WeatherData,
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            val astronomy = airQualityRepository.getAstronomy(
                sunrise = data.current.sunrise,
                sunset = data.current.sunset,
                latitude = lat,
                longitude = lon,
                zoneId = data.location.timeZone.toZoneIdOrNull() ?: ZoneId.systemDefault(),
                referenceTime = data.current.observationTime,
            )
            if (isLatestRequest(requestId)) {
                updateState { it.copy(astronomy = astronomy) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAstronomy failed", e)
        }
    }

    private suspend fun fetchRadarPreview(
        lat: Double,
        lon: Double,
        requestId: Long,
        currentState: () -> MainUiState,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            val radarProvider = currentState().settings.radarProvider
            radarRepository.getRadarFrames(radarProvider).fold(
                onSuccess = { frameSet ->
                    val latestFrame = frameSet.past.lastOrNull()
                    if (latestFrame == null) {
                        if (isLatestRequest(requestId)) {
                            updateState { it.copy(radarPreviewFetchFailed = true) }
                        }
                        return@fold
                    }
                    val preview = radarRepository.buildPreviewTileUrls(
                        lat = lat,
                        lon = lon,
                        tileUrlTemplate = latestFrame.tileUrl,
                        maxTileZoom = frameSet.maxTileZoom,
                    )
                    if (isLatestRequest(requestId)) {
                        updateState {
                            it.copy(
                                radarPreviewTileUrl = preview.radarTileUrl,
                                radarBaseMapUrl = preview.baseMapUrl,
                                radarPreviewUpdatedAt = LocalDateTime.now(),
                                radarPreviewFetchFailed = false,
                            )
                        }
                    }
                },
                onFailure = {
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(radarPreviewFetchFailed = true) }
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchRadarPreview failed", e)
            if (isLatestRequest(requestId)) {
                updateState { it.copy(radarPreviewFetchFailed = true) }
            }
        }
    }

    private suspend fun fetchNowcast(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            repository.getMinutelyPrecipitation(lat, lon).fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(nowcastData = data.toImmutableList()) }
                    }
                },
                onFailure = { Log.w(TAG, "fetchNowcast failed: ${it.message}") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchNowcast failed", e)
        }
    }

    private suspend fun fetchForecastAccuracy(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            forecastAccuracyRepository.getForecastAccuracy(lat, lon).fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(forecastAccuracy = data) }
                    }
                },
                onFailure = { Log.d(TAG, "Forecast accuracy unavailable: ${it.message}") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "fetchForecastAccuracy failed", e)
        }
    }

    private suspend fun fetchConfidenceBands(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            confidenceBandRepository.getConfidenceBands(lat, lon).fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(confidenceBands = data) }
                    }
                },
                onFailure = { Log.d(TAG, "Confidence bands unavailable: ${it.message}") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "fetchConfidenceBands failed", e)
        }
    }

    private suspend fun fetchOnThisDay(
        lat: Double,
        lon: Double,
        referenceDate: LocalDate?,
        requestId: Long,
        currentState: () -> MainUiState,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            val data = onThisDayRepository.getOnThisDay(lat, lon, referenceDate ?: LocalDate.now())
            if (isLatestRequest(requestId)) {
                updateState { it.copy(onThisDay = data) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchOnThisDay failed: ${e.message}")
            if (isLatestRequest(requestId) && currentState().onThisDay == null) {
                updateState { it.copy(onThisDay = null) }
            }
        }
    }

    private suspend fun fetchMarine(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            marineRepository.getMarine(lat, lon).fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(marineData = data) }
                    }
                },
                onFailure = { Log.d(TAG, "fetchMarine: no marine data (expected for inland locations)") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "fetchMarine failed", e)
        }
    }

    private suspend fun fetchFlood(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            floodRepository.getFlood(lat, lon).fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(floodData = data) }
                    }
                },
                onFailure = { Log.d(TAG, "fetchFlood: no flood data") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "fetchFlood failed", e)
        }
    }

    private suspend fun fetchClimateOutlook(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            climateRepository.getClimateOutlook(lat, lon).fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(climateOutlook = data) }
                    }
                },
                onFailure = { Log.d(TAG, "fetchClimateOutlook: no climate data") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "fetchClimateOutlook failed", e)
        }
    }

    private suspend fun fetchAuroraKp(
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            auroraRepository.getKpIndex().fold(
                onSuccess = { data ->
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(auroraKpData = data) }
                    }
                },
                onFailure = { Log.w(TAG, "fetchAuroraKp failed: ${it.message}") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchAuroraKp failed", e)
        }
    }

    private suspend fun fetchYesterdayComparison(
        lat: Double,
        lon: Double,
        requestId: Long,
        updateState: ((MainUiState) -> MainUiState) -> Unit,
        isLatestRequest: (Long) -> Boolean,
    ) {
        try {
            repository.getYesterdayWeather(lat, lon).fold(
                onSuccess = { yesterday ->
                    val high = yesterday?.temperatureHigh
                    if (isLatestRequest(requestId)) {
                        updateState { it.copy(yesterdayHigh = high) }
                    }
                },
                onFailure = { Log.w(TAG, "fetchYesterdayComparison failed: ${it.message}") },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fetchYesterdayComparison failed", e)
        }
    }
}

private data class DerivedWeatherState(
    val weatherSummary: String,
    val outdoorScore: WeatherFormatter.OutdoorScoreBreakdown?,
    val drivingAlerts: ImmutableList<DrivingAlert>,
    val healthAlerts: ImmutableList<HealthAlert>,
    val clothingSuggestions: ImmutableList<ClothingSuggestion>,
    val petSafetyAlerts: ImmutableList<PetSafetyAlert>,
    val goldenHourTimes: Pair<String, String>?,
    val activityIndices: ImmutableList<com.sysadmindoc.nimbus.util.ActivityIndex>,
)

private fun NimbusSettings.isCardEnabled(cardType: CardType): Boolean =
    cardType.name !in disabledCards
