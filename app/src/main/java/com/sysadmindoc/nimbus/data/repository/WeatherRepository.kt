package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.WeatherDao
import com.sysadmindoc.nimbus.data.location.ReverseGeocoder
import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.util.DrivingAlert
import com.sysadmindoc.nimbus.util.DrivingAlertType
import com.sysadmindoc.nimbus.util.DrivingConditionEvaluator
import com.sysadmindoc.nimbus.util.DrivingSeverity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApi: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
    private val reverseGeocoder: ReverseGeocoder,
    private val weatherDao: WeatherDao,
    private val userPreferences: UserPreferences,
    private val sourceManager: dagger.Lazy<WeatherSourceManager>,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Result<WeatherData>>>()

    companion object {
        private const val DEFAULT_CACHE_MAX_AGE_MS = 30 * 60 * 1000L

        /**
         * How stale last-known data may be and still be served by
         * [getWeatherOrCached] when a live fetch fails. Wider than the normal
         * cache TTL so background surfaces (widgets, wear) degrade gracefully
         * during transient outages instead of going blank.
         */
        const val DEFAULT_STALE_FALLBACK_MS = 6 * 60 * 60 * 1000L

        private fun coalescingKey(
            lat: Double,
            lon: Double,
            config: SourceConfig,
            savedLocationId: Long?,
        ): String = String.format(
            Locale.US,
            "%.4f,%.4f|%s|%s|%s",
            lat,
            lon,
            config.forecast.name,
            config.forecastFallback?.name ?: "none",
            savedLocationId?.toString() ?: "coord",
        )
    }

    /**
     * Public entry point — delegates through [WeatherSourceManager] which handles
     * primary/fallback source selection. Coalesces concurrent requests for the same
     * location so widget/worker and UI refreshes share a single network call.
     */
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationTimeZone: String? = null,
        sourceOverrides: SourceOverrides = SourceOverrides(),
        savedLocationId: Long? = null,
    ): Result<WeatherData> {
        val settings = userPreferences.settings.first()
        val sourceConfig = settings.sourceConfig.withOverrides(sourceOverrides)
        val cacheMaxAgeMs = settings.cacheTtlMs.takeIf { it > 0L } ?: DEFAULT_CACHE_MAX_AGE_MS
        val normalizedSavedLocationId = savedLocationId?.takeIf { it > 0L }
        val key = coalescingKey(latitude, longitude, sourceConfig, normalizedSavedLocationId)
        val existing = inFlightRequests[key]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<Result<WeatherData>>()
        val winner = inFlightRequests.putIfAbsent(key, deferred)
        if (winner != null) return winner.await()

        return try {
            val result = sourceManager.get().getWeather(
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                locationTimeZone = locationTimeZone,
                sourceOverrides = sourceOverrides,
            )
            result.getOrNull()?.let { data ->
                cacheWeatherData(
                    data = data,
                    latitude = latitude,
                    longitude = longitude,
                    requestedConfig = sourceConfig,
                    savedLocationId = normalizedSavedLocationId,
                    cacheMaxAgeMs = cacheMaxAgeMs,
                )
            }
            deferred.complete(result)
            result
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            inFlightRequests.remove(key)
        }
    }

    suspend fun getWeather(location: SavedLocationEntity): Result<WeatherData> =
        getWeather(
            latitude = location.latitude,
            longitude = location.longitude,
            locationName = location.name,
            locationTimeZone = location.timeZone,
            sourceOverrides = location.sourceOverrides(),
            savedLocationId = location.id,
        )

    /**
     * Background-worker entry point: like [getWeather] but, when the live fetch
     * fails (transient network/source outage), falls back to the last-known
     * cached data within [allowStaleUpToMs] instead of returning a hard failure.
     *
     * The returned data carries its real observation time via
     * [WeatherData.lastUpdated], so callers render a staleness label rather than
     * presenting stale data as fresh. A successful live fetch always wins and
     * writes through the cache, so the next fallback serves the fresh data. When
     * the live fetch fails and no in-window cache exists, the original
     * [Result.failure] is returned unchanged.
     */
    suspend fun getWeatherOrCached(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationTimeZone: String? = null,
        sourceOverrides: SourceOverrides = SourceOverrides(),
        savedLocationId: Long? = null,
        allowStaleUpToMs: Long = DEFAULT_STALE_FALLBACK_MS,
    ): Result<WeatherData> {
        val live = getWeather(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            locationTimeZone = locationTimeZone,
            sourceOverrides = sourceOverrides,
            savedLocationId = savedLocationId,
        )
        if (live.isSuccess) return live
        val stale = readCachedWeather(latitude, longitude, sourceOverrides, savedLocationId, allowStaleUpToMs)
        return if (stale != null) Result.success(stale) else live
    }

    suspend fun getWeatherOrCached(
        location: SavedLocationEntity,
        allowStaleUpToMs: Long = DEFAULT_STALE_FALLBACK_MS,
    ): Result<WeatherData> =
        getWeatherOrCached(
            latitude = location.latitude,
            longitude = location.longitude,
            locationName = location.name,
            locationTimeZone = location.timeZone,
            sourceOverrides = location.sourceOverrides(),
            savedLocationId = location.id,
            allowStaleUpToMs = allowStaleUpToMs,
        )

    /**
     * Builds a foreground route-weather plan by sampling forecast and alert data
     * along a straight-line route. No background tracking or routing vendor is used.
     */
    suspend fun planDrivingRouteWeather(
        originQuery: String,
        destinationQuery: String,
        departureTime: LocalDateTime,
        fallbackOrigin: LocationInfo? = null,
        averageSpeedKmh: Double = DEFAULT_DRIVING_ROUTE_SPEED_KMH,
    ): Result<DrivingRouteWeatherPlan> = withContext(Dispatchers.IO) {
        try {
            val destination = resolveRouteEndpoint(
                query = destinationQuery,
                fallback = null,
                missingReason = DrivingRoutePlanningFailure.DESTINATION_REQUIRED,
                notFoundReason = DrivingRoutePlanningFailure.DESTINATION_NOT_FOUND,
            )
            val origin = resolveRouteEndpoint(
                query = originQuery,
                fallback = fallbackOrigin,
                missingReason = DrivingRoutePlanningFailure.ORIGIN_REQUIRED,
                notFoundReason = DrivingRoutePlanningFailure.ORIGIN_NOT_FOUND,
            )
            val distanceKm = haversineKm(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
            val durationMinutes = ((distanceKm / averageSpeedKmh.coerceAtLeast(20.0)) * 60.0)
                .roundToLong()
                .coerceAtLeast(1L)
            val waypointFractions = routeWaypointFractions(distanceKm)
            val waypoints = waypointFractions.mapIndexed { index, fraction ->
                val latitude = interpolate(origin.latitude, destination.latitude, fraction)
                val longitude = interpolate(origin.longitude, destination.longitude, fraction)
                val waypointLabel = when (index) {
                    0 -> origin.name
                    waypointFractions.lastIndex -> destination.name
                    else -> "Waypoint ${index + 1}"
                }
                val arrivalTime = departureTime.plusMinutes((durationMinutes * fraction).roundToLong())
                val weather = getWeatherOrCached(
                    latitude = latitude,
                    longitude = longitude,
                    locationName = waypointLabel,
                    locationTimeZone = null,
                    allowStaleUpToMs = DEFAULT_STALE_FALLBACK_MS,
                ).getOrElse { throw DrivingRoutePlanningException(DrivingRoutePlanningFailure.WEATHER_UNAVAILABLE, it) }
                val hourly = weather.hourly.nearestTo(arrivalTime)
                val drivingAlerts = if (hourly != null) {
                    DrivingConditionEvaluator.evaluate(hourly, weather.current)
                } else {
                    DrivingConditionEvaluator.evaluate(weather.current)
                }
                val routeConditions = drivingRouteConditions(weather, hourly, drivingAlerts)
                val weatherAlerts = routeWeatherAlerts(
                    latitude = latitude,
                    longitude = longitude,
                    countryHint = weather.location.country.ifBlank { null },
                )
                DrivingRouteWaypoint(
                    index = index,
                    label = waypointLabel,
                    latitude = latitude,
                    longitude = longitude,
                    arrivalTime = arrivalTime,
                    distanceFromStartKm = distanceKm * fraction,
                    conditions = routeConditions,
                    drivingAlerts = drivingAlerts,
                    weatherAlerts = weatherAlerts,
                    risk = drivingRouteRisk(drivingAlerts, weatherAlerts),
                )
            }
            Result.success(
                DrivingRouteWeatherPlan(
                    origin = origin,
                    destination = destination,
                    departureTime = departureTime,
                    estimatedArrivalTime = departureTime.plusMinutes(durationMinutes),
                    distanceKm = distanceKm,
                    estimatedDurationMinutes = durationMinutes,
                    waypoints = waypoints,
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * Direct Open-Meteo forecast fetch — used by the [OpenMeteoForecastAdapter].
     * Bypasses the source manager to avoid circular delegation.
     */
    suspend fun getWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    /**
     * Direct Open-Meteo BOM ACCESS-G forecast fetch.
     *
     * This is the roadmap's safe Australian path: it uses Open-Meteo's
     * documented `/v1/bom` proxy rather than the undocumented BOM app API.
     */
    suspend fun getBomWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getBomForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    suspend fun getKmaWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getKmaForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    suspend fun getUkmoWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getUkmoForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    suspend fun getDmiWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getDmiForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    suspend fun getMeteoFranceWeatherDirect(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = getOpenMeteoWeather(
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        fetch = { forecastHours ->
            weatherApi.getMeteoFranceForecast(latitude, longitude, forecastHours = forecastHours)
        },
    )

    private suspend fun getOpenMeteoWeather(
        latitude: Double,
        longitude: Double,
        locationName: String?,
        fetch: suspend (forecastHours: Int) -> OpenMeteoResponse,
    ): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val settings = userPreferences.settings.first()
            val forecastHours = settings.hourlyForecastHours
            val cacheMaxAgeMs = settings.cacheTtlMs.takeIf { it > 0L } ?: DEFAULT_CACHE_MAX_AGE_MS
            val response = fetch(forecastHours)
            val location = resolveLocationName(latitude, longitude, locationName)
            val weatherData = mapToWeatherData(response, location)

            // Legacy Open-Meteo cache retained for migration/backward compatibility.
            try {
                val responseJson = json.encodeToString(OpenMeteoResponse.serializer(), response)
                weatherDao.upsert(
                    WeatherCacheEntity(
                        locationKey = WeatherCacheEntity.makeKey(latitude, longitude),
                        responseJson = responseJson,
                        locationName = location.name,
                        locationRegion = location.region,
                        locationCountry = location.country,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
                weatherDao.deleteOlderThan(System.currentTimeMillis() - cacheMaxAgeMs)
            } catch (_: Exception) { /* Cache failure is non-fatal */ }

            Result.success(weatherData)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getCachedWeather(
        latitude: Double,
        longitude: Double,
        sourceOverrides: SourceOverrides = SourceOverrides(),
        savedLocationId: Long? = null,
    ): WeatherData? =
        withContext(Dispatchers.IO) {
            val cacheMaxAgeMs = userPreferences.settings.first().cacheTtlMs
                .takeIf { it > 0L }
                ?: DEFAULT_CACHE_MAX_AGE_MS
            readCachedWeather(latitude, longitude, sourceOverrides, savedLocationId, cacheMaxAgeMs)
        }

    suspend fun getCachedWeather(location: SavedLocationEntity): WeatherData? =
        getCachedWeather(
            latitude = location.latitude,
            longitude = location.longitude,
            sourceOverrides = location.sourceOverrides(),
            savedLocationId = location.id,
        )

    /**
     * Decode the cached forecast for a location if it exists and is no older
     * than [maxAgeMs]. Returns null on a cache miss, an over-age entry, or a
     * decode failure. The decoded [WeatherData.lastUpdated] preserves the
     * original fetch time so "updated X ago" and the staleness colouring reflect
     * the cache's real age — not "now", which made every cached read look fresh.
     */
    private suspend fun readCachedWeather(
        latitude: Double,
        longitude: Double,
        sourceOverrides: SourceOverrides,
        savedLocationId: Long?,
        maxAgeMs: Long,
    ): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val key = WeatherCacheEntity.makeKey(latitude, longitude)
            val providerNames = cacheProviderNames(sourceOverrides)
            val normalizedSavedLocationId = savedLocationId?.takeIf { it > 0L }
            val normalized = weatherDao.getCachedWeatherData(
                locationKey = key,
                savedLocationId = normalizedSavedLocationId,
                sourceProviders = providerNames,
                schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
            )
            if (normalized != null && !normalized.isExpired(maxAgeMs)) {
                val payload = json.decodeFromString(WeatherDataCachePayload.serializer(), normalized.payloadJson)
                return@withContext payload.toWeatherData(
                    lastUpdatedOverride = Instant.ofEpochMilli(normalized.cachedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime(),
                )
            }
            if (WeatherSourceProvider.OPEN_METEO.name !in providerNames) return@withContext null
            val cached = weatherDao.getCached(key) ?: return@withContext null
            if (cached.isExpired(maxAgeMs)) return@withContext null
            val response = json.decodeFromString(OpenMeteoResponse.serializer(), cached.responseJson)
            val location = LocationInfo(
                name = cached.locationName,
                region = cached.locationRegion,
                country = cached.locationCountry,
                latitude = cached.latitude,
                longitude = cached.longitude,
                timeZone = response.timezone?.takeIf { it.toZoneIdOrNull() != null },
            )
            mapToWeatherData(
                response,
                location,
                observedAt = Instant.ofEpochMilli(cached.cachedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime(),
            ).copy(sourceProvider = WeatherSourceProvider.OPEN_METEO.displayName)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun cacheWeatherData(
        data: WeatherData,
        latitude: Double,
        longitude: Double,
        requestedConfig: SourceConfig,
        savedLocationId: Long?,
        cacheMaxAgeMs: Long,
    ) {
        try {
            val provider = data.actualSourceProvider(requestedConfig)
            val cacheData = data.copy(sourceProvider = data.sourceProvider ?: provider.displayName)
            weatherDao.upsertWeatherData(
                WeatherDataCacheEntity(
                    cacheKey = WeatherDataCacheEntity.makeKey(
                        latitude = latitude,
                        longitude = longitude,
                        sourceProvider = provider.name,
                        savedLocationId = savedLocationId,
                    ),
                    locationKey = WeatherCacheEntity.makeKey(latitude, longitude),
                    sourceProvider = provider.name,
                    savedLocationId = savedLocationId,
                    schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
                    payloadJson = json.encodeToString(
                        WeatherDataCachePayload.serializer(),
                        cacheData.toCachePayload(),
                    ),
                ),
            )
            weatherDao.deleteWeatherDataOlderThan(System.currentTimeMillis() - cacheMaxAgeMs)
        } catch (_: Exception) {
            // Cache failure is non-fatal.
        }
    }

    private suspend fun cacheProviderNames(sourceOverrides: SourceOverrides): List<String> {
        val config = userPreferences.settings.first().sourceConfig.withOverrides(sourceOverrides)
        return listOfNotNull(config.forecast, config.forecastFallback)
            .map { it.name }
            .distinct()
            .ifEmpty { listOf(WeatherSourceProvider.OPEN_METEO.name) }
    }

    private fun WeatherData.actualSourceProvider(config: SourceConfig): WeatherSourceProvider =
        WeatherSourceProvider.entries.firstOrNull { it.displayName == sourceProvider }
            ?.takeIf { it.isSelectableFor(WeatherDataType.FORECAST) }
            ?: config.forecast

    suspend fun searchLocations(query: String): Result<List<LocationInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val response = geocodingApi.searchLocation(query)
                val locations = response.results?.map { result ->
                    LocationInfo(
                        name = result.name,
                        region = result.admin1 ?: "",
                        country = result.country ?: "",
                        latitude = result.latitude,
                        longitude = result.longitude,
                        timeZone = result.timezone?.takeIf { it.toZoneIdOrNull() != null },
                    )
                } ?: emptyList()
                Result.success(locations)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }

    private suspend fun resolveLocationName(
        latitude: Double,
        longitude: Double,
        provided: String?,
    ): LocationInfo {
        if (!provided.isNullOrBlank()) {
            return LocationInfo(name = provided, latitude = latitude, longitude = longitude)
        }
        // Use Android's built-in Geocoder for reliable reverse geocoding
        val result = reverseGeocoder.resolve(latitude, longitude)
        return if (result != null && result.name.isNotBlank()) {
            LocationInfo(
                name = result.name,
                region = result.region,
                country = result.country,
                latitude = latitude,
                longitude = longitude,
            )
        } else {
            // Last resort: fall back to Open-Meteo reverse geocoding.
            try {
                val geo = geocodingApi.reverseGeocode(latitude, longitude, count = 1)
                val first = geo.results?.firstOrNull()
                LocationInfo(
                    name = first?.name ?: "Unknown Location",
                    region = first?.admin1 ?: "",
                    country = first?.country ?: "",
                    latitude = latitude,
                    longitude = longitude,
                    timeZone = first?.timezone?.takeIf { it.toZoneIdOrNull() != null },
                )
            } catch (_: Exception) {
                LocationInfo(
                    name = "Unknown Location",
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        }
    }

    private fun mapToWeatherData(
        response: OpenMeteoResponse,
        location: LocationInfo,
        observedAt: LocalDateTime? = null,
    ): WeatherData {
        val mappedLocation = location.withResponseTimeZone(response.timezone)
        val hourly = response.hourly
        val daily = response.daily
        val currentHourlyIndex = nearestHourlyIndex(response)
        val snapshot = resolveCurrentSnapshot(response, currentHourlyIndex)

        // Anchor "today" on the location-local date instead of assuming the
        // daily array starts today. It does for the current API params (no
        // past_days), so this resolves to index 0 in practice — but matching by
        // date keeps the current-conditions high/low/sunrise correct if the
        // params ever change or a cached response is replayed across midnight.
        val today = response.locationLocalNow().toLocalDate()
        val todayIndex = daily?.time
            ?.indexOfFirst { parseDate(it) == today }
            ?.takeIf { it >= 0 }
            ?: 0
        val dailyHigh = daily?.temperatureMax?.getOrNull(todayIndex) ?: snapshot.temperature
        val dailyLow = daily?.temperatureMin?.getOrNull(todayIndex) ?: snapshot.temperature

        return WeatherData(
            location = mappedLocation,
            current = snapshot.toCurrentConditions(
                dailyHigh = dailyHigh,
                dailyLow = dailyLow,
                sunrise = daily?.sunrise?.getOrNull(todayIndex),
                sunset = daily?.sunset?.getOrNull(todayIndex),
            ),
            hourly = mapHourlyData(hourly, snapshot.observationTime),
            daily = mapDailyData(daily),
            lastUpdated = observedAt ?: LocalDateTime.now(),
        )
    }

    private fun resolveCurrentSnapshot(
        response: OpenMeteoResponse,
        hourlyIndex: Int?,
    ): OpenMeteoCurrentSnapshot {
        val current = response.current
        val hourly = response.hourly
        val temperature = current?.temperature ?: hourly?.temperature.valueAt(hourlyIndex) ?: 0.0

        return OpenMeteoCurrentSnapshot(
            temperature = temperature,
            feelsLike = current?.apparentTemperature ?: hourly?.apparentTemperature.valueAt(hourlyIndex) ?: temperature,
            humidity = current?.humidity ?: hourly?.humidity.valueAt(hourlyIndex) ?: 0,
            weatherCode = current?.weatherCode ?: hourly?.weatherCode.valueAt(hourlyIndex),
            observationTime = resolveObservationTime(response, hourlyIndex),
            isDay = (current?.isDay ?: hourly?.isDay.valueAt(hourlyIndex) ?: 1) == 1,
            windSpeed = current?.windSpeed ?: hourly?.windSpeed.valueAt(hourlyIndex) ?: 0.0,
            windDirection = current?.windDirection ?: hourly?.windDirection.valueAt(hourlyIndex) ?: 0,
            windGusts = current?.windGusts ?: hourly?.windGusts.valueAt(hourlyIndex),
            pressure = resolveCurrentPressure(current, hourly, hourlyIndex),
            uvIndex = current?.uvIndex ?: hourly?.uvIndex.valueAt(hourlyIndex) ?: 0.0,
            visibility = current?.visibility ?: hourly?.visibility.valueAt(hourlyIndex),
            dewPoint = current?.dewPoint,
            cloudCover = current?.cloudCover ?: hourly?.cloudCover.valueAt(hourlyIndex) ?: 0,
            precipitation = current?.precipitation ?: hourly?.precipitation.valueAt(hourlyIndex) ?: 0.0,
            snowfall = current?.snowfall ?: hourly?.snowfall.valueAt(hourlyIndex),
            // Open-Meteo snow_depth arrives in meters; the formatter contract
            // (WeatherFormatter.formatSnowDepth) expects centimeters.
            snowDepth = (current?.snowDepth ?: hourly?.snowDepth.valueAt(hourlyIndex))
                ?.let { it * 100.0 },
            cape = current?.cape ?: hourly?.cape.valueAt(hourlyIndex),
        )
    }

    private fun resolveObservationTime(
        response: OpenMeteoResponse,
        hourlyIndex: Int?,
    ): LocalDateTime = response.current?.time?.let(::parseDateTime)
        ?: hourlyIndex?.let { response.hourly?.time?.getOrNull(it)?.let(::parseDateTime) }
        ?: response.locationLocalNow()

    private fun resolveCurrentPressure(
        current: CurrentWeather?,
        hourly: HourlyWeather?,
        hourlyIndex: Int?,
    ): Double = current?.pressureMsl
        ?: current?.surfacePressure
        ?: hourly?.surfacePressure.valueAt(hourlyIndex)
        ?: 0.0

    private data class OpenMeteoCurrentSnapshot(
        val temperature: Double,
        val feelsLike: Double,
        val humidity: Int,
        val weatherCode: Int?,
        val observationTime: LocalDateTime,
        val isDay: Boolean,
        val windSpeed: Double,
        val windDirection: Int,
        val windGusts: Double?,
        val pressure: Double,
        val uvIndex: Double,
        val visibility: Double?,
        val dewPoint: Double?,
        val cloudCover: Int,
        val precipitation: Double,
        val snowfall: Double?,
        val snowDepth: Double?,
        val cape: Double?,
    ) {
        fun toCurrentConditions(
            dailyHigh: Double,
            dailyLow: Double,
            sunrise: String?,
            sunset: String?,
        ): CurrentConditions = CurrentConditions(
            temperature = temperature,
            feelsLike = feelsLike,
            humidity = humidity,
            weatherCode = WeatherCode.fromCode(weatherCode),
            observationTime = observationTime,
            isDay = isDay,
            windSpeed = windSpeed,
            windDirection = windDirection,
            windGusts = windGusts,
            pressure = pressure,
            uvIndex = uvIndex,
            visibility = visibility,
            dewPoint = dewPoint,
            cloudCover = cloudCover,
            precipitation = precipitation,
            snowfall = snowfall,
            snowDepth = snowDepth,
            cape = cape,
            dailyHigh = dailyHigh,
            dailyLow = dailyLow,
            sunrise = sunrise,
            sunset = sunset,
        )
    }

    private fun nearestHourlyIndex(response: OpenMeteoResponse): Int? {
        val hourly = response.hourly ?: return null
        if (hourly.time.isEmpty()) return null
        val localNow = response.locationLocalNow()
        return hourly.time
            .mapIndexedNotNull { index, time -> parseDateTime(time)?.let { index to it } }
            .minByOrNull { (_, time) -> abs(Duration.between(time, localNow).toMinutes()) }
            ?.first
    }

    private fun OpenMeteoResponse.locationLocalNow(): LocalDateTime {
        val zone = timezone
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return zone?.let { LocalDateTime.ofInstant(Instant.now(), it) } ?: LocalDateTime.now()
    }

    private fun <T> List<T?>?.valueAt(index: Int?): T? =
        index?.let { this?.getOrNull(it) }

    private fun mapHourlyData(hourly: HourlyWeather?, now: LocalDateTime): List<HourlyConditions> {
        if (hourly == null) return emptyList()
        return hourly.time.mapIndexedNotNull { i, timeStr ->
            val time = parseDateTime(timeStr) ?: return@mapIndexedNotNull null
            if (time.isBefore(now.minusHours(1))) return@mapIndexedNotNull null
            HourlyConditions(
                time = time,
                temperature = hourly.temperature?.getOrNull(i) ?: 0.0,
                feelsLike = hourly.apparentTemperature?.getOrNull(i),
                weatherCode = WeatherCode.fromCode(hourly.weatherCode?.getOrNull(i)),
                isDay = (hourly.isDay?.getOrNull(i) ?: 1) == 1,
                precipitationProbability = hourly.precipitationProbability?.getOrNull(i) ?: 0,
                precipitation = hourly.precipitation?.getOrNull(i),
                windSpeed = hourly.windSpeed?.getOrNull(i),
                windDirection = hourly.windDirection?.getOrNull(i),
                humidity = hourly.humidity?.getOrNull(i),
                uvIndex = hourly.uvIndex?.getOrNull(i),
                cloudCover = hourly.cloudCover?.getOrNull(i),
                visibility = hourly.visibility?.getOrNull(i),
                snowfall = hourly.snowfall?.getOrNull(i),
                windGusts = hourly.windGusts?.getOrNull(i),
                sunshineDuration = hourly.sunshineDuration?.getOrNull(i),
                surfacePressure = hourly.surfacePressure?.getOrNull(i),
                shortwaveRadiation = hourly.shortwaveRadiation?.getOrNull(i),
                directNormalIrradiance = hourly.directNormalIrradiance?.getOrNull(i),
            )
        }
    }

    private fun mapDailyData(daily: DailyWeather?): List<DailyConditions> {
        if (daily == null) return emptyList()
        return daily.time.mapIndexedNotNull { i, dateStr ->
            val date = parseDate(dateStr) ?: return@mapIndexedNotNull null
            DailyConditions(
                date = date,
                weatherCode = WeatherCode.fromCode(daily.weatherCode?.getOrNull(i)),
                temperatureHigh = daily.temperatureMax?.getOrNull(i) ?: 0.0,
                temperatureLow = daily.temperatureMin?.getOrNull(i) ?: 0.0,
                precipitationProbability = daily.precipitationProbabilityMax?.getOrNull(i) ?: 0,
                precipitationSum = daily.precipitationSum?.getOrNull(i),
                sunrise = daily.sunrise?.getOrNull(i),
                sunset = daily.sunset?.getOrNull(i),
                uvIndexMax = daily.uvIndexMax?.getOrNull(i),
                windSpeedMax = daily.windSpeedMax?.getOrNull(i),
                windDirectionDominant = daily.windDirectionDominant?.getOrNull(i),
                snowfallSum = daily.snowfallSum?.getOrNull(i),
                sunshineDuration = daily.sunshineDuration?.getOrNull(i),
                windGustsMax = daily.windGustsMax?.getOrNull(i),
                precipitationHours = daily.precipitationHours?.getOrNull(i),
            )
        }
    }

    /**
     * Public entry point for minutely precipitation — delegates through the source manager.
     */
    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> {
        return sourceManager.get().getMinutelyPrecipitation(latitude, longitude)
    }

    /**
     * Direct Open-Meteo minutely fetch — used by [OpenMeteoMinutelyAdapter].
     */
    suspend fun getMinutelyPrecipitationDirect(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> =
        getOpenMeteoMinutelyPrecipitation {
            weatherApi.getMinutely15Forecast(latitude, longitude)
        }

    /**
     * Direct Open-Meteo Meteo-France 15-minute precipitation fetch.
     */
    suspend fun getMeteoFranceMinutelyPrecipitationDirect(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> =
        getOpenMeteoMinutelyPrecipitation {
            weatherApi.getMeteoFranceMinutely15Forecast(latitude, longitude)
        }

    private suspend fun getOpenMeteoMinutelyPrecipitation(
        fetch: suspend () -> OpenMeteoResponse,
    ): Result<List<MinutelyPrecipitation>> = withContext(Dispatchers.IO) {
        try {
            val response = fetch()
            val minutely = response.minutely15 ?: return@withContext Result.success(emptyList())
            val data = minutely.time.mapIndexedNotNull { i, timeStr ->
                val time = parseDateTime(timeStr) ?: return@mapIndexedNotNull null
                val precip = minutely.precipitation?.getOrNull(i) ?: 0.0
                MinutelyPrecipitation(time = time, precipitation = precip)
            }
            Result.success(data)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getYesterdayWeather(
        latitude: Double,
        longitude: Double,
    ): Result<DailyConditions?> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApi.getHistoricalForecast(latitude, longitude)
            val daily = response.daily ?: return@withContext Result.success(null)
            val yesterday = daily.time.firstOrNull() ?: return@withContext Result.success(null)
            val date = parseDate(yesterday) ?: return@withContext Result.success(null)
            Result.success(
                DailyConditions(
                    date = date,
                    weatherCode = WeatherCode.fromCode(daily.weatherCode?.getOrNull(0)),
                    temperatureHigh = daily.temperatureMax?.getOrNull(0) ?: 0.0,
                    temperatureLow = daily.temperatureMin?.getOrNull(0) ?: 0.0,
                    precipitationProbability = 0,
                    precipitationSum = null,
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = null,
                    windSpeedMax = null,
                    windDirectionDominant = null,
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun parseDateTime(str: String): LocalDateTime? = try {
        LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) { null }

    private fun parseDate(str: String): LocalDate? = try {
        LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) { null }

    private suspend fun resolveRouteEndpoint(
        query: String,
        fallback: LocationInfo?,
        missingReason: DrivingRoutePlanningFailure,
        notFoundReason: DrivingRoutePlanningFailure,
    ): LocationInfo {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return fallback ?: throw DrivingRoutePlanningException(missingReason)
        }
        var lastFailure: Throwable? = null
        routeSearchCandidates(trimmed).forEach { candidate ->
            val locations = searchLocations(candidate).getOrElse {
                lastFailure = it
                emptyList()
            }
            routeSearchMatch(locations, trimmed)?.let { return it }
        }
        throw DrivingRoutePlanningException(notFoundReason, lastFailure)
    }

    private fun routeSearchCandidates(query: String): List<String> {
        val normalized = query.replace('+', ' ').replace(Regex("\\s+"), " ").trim()
        val withoutParenthetical = normalized.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        val parts = withoutParenthetical.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val candidates = buildList {
            add(normalized)
            add(withoutParenthetical)
            if (parts.size >= 2) {
                val city = parts.first()
                val region = parts[1]
                val expandedRegion = routeRegionExpansion(region)
                add("$city $expandedRegion")
                add("$city, $expandedRegion")
                add(city)
            }
            add(withoutParenthetical.replace(Regex("[,;]+"), " ").replace(Regex("\\s+"), " ").trim())
        }
        return candidates.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.US) }
    }

    private fun routeSearchMatch(
        locations: List<LocationInfo>,
        query: String,
    ): LocationInfo? {
        if (locations.isEmpty()) return null
        val hints = routeRegionHints(query)
        return hints
            .firstNotNullOfOrNull { hint ->
                locations.firstOrNull { location ->
                    location.region.equals(hint, ignoreCase = true) ||
                        location.country.equals(hint, ignoreCase = true)
                }
            }
            ?: locations.first()
    }

    private fun routeRegionHints(query: String): List<String> {
        val parts = query.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size < 2) return emptyList()
        val region = parts[1]
        return listOf(region, routeRegionExpansion(region))
            .distinctBy { it.lowercase(Locale.US) }
    }

    private fun routeRegionExpansion(region: String): String =
        US_STATE_NAMES[region.trim().uppercase(Locale.US)] ?: region.trim()

    private suspend fun routeWeatherAlerts(
        latitude: Double,
        longitude: Double,
        countryHint: String?,
    ): List<WeatherAlert> {
        return try {
            sourceManager.get().getAlertsDetailed(
                latitude = latitude,
                longitude = longitude,
                sourceOverrides = SourceOverrides(),
                includeMeteredSources = false,
                countryHint = countryHint,
            ).alerts
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun drivingRouteConditions(
        weather: WeatherData,
        hourly: HourlyConditions?,
        alerts: List<DrivingAlert>,
    ): DrivingRouteConditions {
        val current = weather.current
        return DrivingRouteConditions(
            temperatureC = hourly?.temperature ?: current.temperature,
            weatherCode = hourly?.weatherCode ?: current.weatherCode,
            precipitationMm = hourly?.precipitation ?: current.precipitation,
            precipitationProbability = hourly?.precipitationProbability ?: 0,
            windSpeedKmh = hourly?.windSpeed ?: current.windSpeed,
            windGustKmh = hourly?.windGusts ?: current.windGusts,
            visibilityMeters = hourly?.visibility ?: current.visibility,
            iceRisk = alerts.any {
                it.type == DrivingAlertType.BLACK_ICE || it.type == DrivingAlertType.SNOW_ICE
            },
        )
    }

    private fun drivingRouteRisk(
        drivingAlerts: List<DrivingAlert>,
        weatherAlerts: List<WeatherAlert>,
    ): DrivingRouteRiskLevel {
        val hasDangerDriving = drivingAlerts.any { it.severity == DrivingSeverity.DANGER }
        val hasCautionDriving = drivingAlerts.any { it.severity == DrivingSeverity.CAUTION }
        val hasAdvisoryDriving = drivingAlerts.any { it.severity == DrivingSeverity.ADVISORY }
        val highestWeatherAlertOrder = weatherAlerts.minOfOrNull { it.severity.sortOrder }
        return when {
            hasDangerDriving || highestWeatherAlertOrder != null && highestWeatherAlertOrder <= 1 ->
                DrivingRouteRiskLevel.HIGH
            hasCautionDriving || highestWeatherAlertOrder == 2 -> DrivingRouteRiskLevel.MODERATE
            hasAdvisoryDriving || highestWeatherAlertOrder != null -> DrivingRouteRiskLevel.LOW
            else -> DrivingRouteRiskLevel.CLEAR
        }
    }

    private fun List<HourlyConditions>.nearestTo(time: LocalDateTime): HourlyConditions? =
        minByOrNull { abs(Duration.between(it.time, time).toMinutes()) }

    private fun routeWaypointFractions(distanceKm: Double): List<Double> {
        val segmentCount = when {
            distanceKm < 80.0 -> 1
            distanceKm < 240.0 -> 2
            distanceKm < 520.0 -> 3
            distanceKm < 900.0 -> 4
            else -> 5
        }
        return (0..segmentCount).map { index -> index.toDouble() / segmentCount.toDouble() }
    }

    private fun interpolate(start: Double, end: Double, fraction: Double): Double =
        start + ((end - start) * fraction)

    private fun haversineKm(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(endLat - startLat)
        val dLon = Math.toRadians(endLon - startLon)
        val lat1 = Math.toRadians(startLat)
        val lat2 = Math.toRadians(endLat)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return earthRadiusKm * c
    }

    private fun LocationInfo.withResponseTimeZone(responseTimeZone: String?): LocationInfo {
        val validResponseTimeZone = responseTimeZone?.takeIf { it.toZoneIdOrNull() != null }
        return if (timeZone == null && validResponseTimeZone != null) {
            copy(timeZone = validResponseTimeZone)
        } else {
            this
        }
    }
}

private const val DEFAULT_DRIVING_ROUTE_SPEED_KMH = 88.0

private val US_STATE_NAMES = mapOf(
    "AL" to "Alabama",
    "AK" to "Alaska",
    "AZ" to "Arizona",
    "AR" to "Arkansas",
    "CA" to "California",
    "CO" to "Colorado",
    "CT" to "Connecticut",
    "DE" to "Delaware",
    "FL" to "Florida",
    "GA" to "Georgia",
    "HI" to "Hawaii",
    "ID" to "Idaho",
    "IL" to "Illinois",
    "IN" to "Indiana",
    "IA" to "Iowa",
    "KS" to "Kansas",
    "KY" to "Kentucky",
    "LA" to "Louisiana",
    "ME" to "Maine",
    "MD" to "Maryland",
    "MA" to "Massachusetts",
    "MI" to "Michigan",
    "MN" to "Minnesota",
    "MS" to "Mississippi",
    "MO" to "Missouri",
    "MT" to "Montana",
    "NE" to "Nebraska",
    "NV" to "Nevada",
    "NH" to "New Hampshire",
    "NJ" to "New Jersey",
    "NM" to "New Mexico",
    "NY" to "New York",
    "NC" to "North Carolina",
    "ND" to "North Dakota",
    "OH" to "Ohio",
    "OK" to "Oklahoma",
    "OR" to "Oregon",
    "PA" to "Pennsylvania",
    "RI" to "Rhode Island",
    "SC" to "South Carolina",
    "SD" to "South Dakota",
    "TN" to "Tennessee",
    "TX" to "Texas",
    "UT" to "Utah",
    "VT" to "Vermont",
    "VA" to "Virginia",
    "WA" to "Washington",
    "WV" to "West Virginia",
    "WI" to "Wisconsin",
    "WY" to "Wyoming",
    "DC" to "District of Columbia",
)
