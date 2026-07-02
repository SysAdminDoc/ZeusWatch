package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class WeatherDataCachePayload(
    val location: CachedLocationInfo,
    val current: CachedCurrentConditions,
    val hourly: List<CachedHourlyConditions>,
    val daily: List<CachedDailyConditions>,
    val lastUpdated: String,
    val sourceProvider: String? = null,
    val usedFallback: Boolean = false,
)

@Serializable
data class CachedLocationInfo(
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    val timeZone: String? = null,
)

@Serializable
data class CachedCurrentConditions(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val weatherCode: Int,
    val observationTime: String? = null,
    val isDay: Boolean,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double? = null,
    val pressure: Double,
    val uvIndex: Double,
    val visibility: Double? = null,
    val dewPoint: Double? = null,
    val cloudCover: Int,
    val precipitation: Double,
    val snowfall: Double? = null,
    val snowDepth: Double? = null,
    val cape: Double? = null,
    val dailyHigh: Double,
    val dailyLow: Double,
    val sunrise: String? = null,
    val sunset: String? = null,
)

@Serializable
data class CachedHourlyConditions(
    val time: String,
    val temperature: Double,
    val feelsLike: Double? = null,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipitationProbability: Int,
    val precipitation: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Int? = null,
    val humidity: Int? = null,
    val uvIndex: Double? = null,
    val cloudCover: Int? = null,
    val visibility: Double? = null,
    val snowfall: Double? = null,
    val windGusts: Double? = null,
    val sunshineDuration: Double? = null,
    val surfacePressure: Double? = null,
    val shortwaveRadiation: Double? = null,
    val directNormalIrradiance: Double? = null,
)

@Serializable
data class CachedDailyConditions(
    val date: String,
    val weatherCode: Int,
    val temperatureHigh: Double,
    val temperatureLow: Double,
    val precipitationProbability: Int,
    val precipitationSum: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val uvIndexMax: Double? = null,
    val windSpeedMax: Double? = null,
    val windDirectionDominant: Int? = null,
    val snowfallSum: Double? = null,
    val sunshineDuration: Double? = null,
    val windGustsMax: Double? = null,
    val precipitationHours: Double? = null,
)

fun WeatherData.toCachePayload(): WeatherDataCachePayload =
    WeatherDataCachePayload(
        location = location.toCachePayload(),
        current = current.toCachePayload(),
        hourly = hourly.map { it.toCachePayload() },
        daily = daily.map { it.toCachePayload() },
        lastUpdated = lastUpdated.toString(),
        sourceProvider = sourceProvider,
        usedFallback = usedFallback,
    )

fun WeatherDataCachePayload.toWeatherData(lastUpdatedOverride: LocalDateTime? = null): WeatherData =
    WeatherData(
        location = location.toLocationInfo(),
        current = current.toCurrentConditions(),
        hourly = hourly.mapNotNull { it.toHourlyConditions() },
        daily = daily.mapNotNull { it.toDailyConditions() },
        lastUpdated = lastUpdatedOverride ?: lastUpdated.toLocalDateTimeOrNull() ?: LocalDateTime.now(),
        sourceProvider = sourceProvider,
        usedFallback = usedFallback,
    )

private fun LocationInfo.toCachePayload(): CachedLocationInfo =
    CachedLocationInfo(
        name = name,
        region = region,
        country = country,
        latitude = latitude,
        longitude = longitude,
        timeZone = timeZone,
    )

private fun CachedLocationInfo.toLocationInfo(): LocationInfo =
    LocationInfo(
        name = name,
        region = region,
        country = country,
        latitude = latitude,
        longitude = longitude,
        timeZone = timeZone,
    )

private fun CurrentConditions.toCachePayload(): CachedCurrentConditions =
    CachedCurrentConditions(
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        weatherCode = weatherCode.code,
        observationTime = observationTime?.toString(),
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

private fun CachedCurrentConditions.toCurrentConditions(): CurrentConditions =
    CurrentConditions(
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        weatherCode = WeatherCode.fromCode(weatherCode),
        observationTime = observationTime?.toLocalDateTimeOrNull(),
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

private fun HourlyConditions.toCachePayload(): CachedHourlyConditions =
    CachedHourlyConditions(
        time = time.toString(),
        temperature = temperature,
        feelsLike = feelsLike,
        weatherCode = weatherCode.code,
        isDay = isDay,
        precipitationProbability = precipitationProbability,
        precipitation = precipitation,
        windSpeed = windSpeed,
        windDirection = windDirection,
        humidity = humidity,
        uvIndex = uvIndex,
        cloudCover = cloudCover,
        visibility = visibility,
        snowfall = snowfall,
        windGusts = windGusts,
        sunshineDuration = sunshineDuration,
        surfacePressure = surfacePressure,
        shortwaveRadiation = shortwaveRadiation,
        directNormalIrradiance = directNormalIrradiance,
    )

private fun CachedHourlyConditions.toHourlyConditions(): HourlyConditions? {
    val parsedTime = time.toLocalDateTimeOrNull() ?: return null
    return HourlyConditions(
        time = parsedTime,
        temperature = temperature,
        feelsLike = feelsLike,
        weatherCode = WeatherCode.fromCode(weatherCode),
        isDay = isDay,
        precipitationProbability = precipitationProbability,
        precipitation = precipitation,
        windSpeed = windSpeed,
        windDirection = windDirection,
        humidity = humidity,
        uvIndex = uvIndex,
        cloudCover = cloudCover,
        visibility = visibility,
        snowfall = snowfall,
        windGusts = windGusts,
        sunshineDuration = sunshineDuration,
        surfacePressure = surfacePressure,
        shortwaveRadiation = shortwaveRadiation,
        directNormalIrradiance = directNormalIrradiance,
    )
}

private fun DailyConditions.toCachePayload(): CachedDailyConditions =
    CachedDailyConditions(
        date = date.toString(),
        weatherCode = weatherCode.code,
        temperatureHigh = temperatureHigh,
        temperatureLow = temperatureLow,
        precipitationProbability = precipitationProbability,
        precipitationSum = precipitationSum,
        sunrise = sunrise,
        sunset = sunset,
        uvIndexMax = uvIndexMax,
        windSpeedMax = windSpeedMax,
        windDirectionDominant = windDirectionDominant,
        snowfallSum = snowfallSum,
        sunshineDuration = sunshineDuration,
        windGustsMax = windGustsMax,
        precipitationHours = precipitationHours,
    )

private fun CachedDailyConditions.toDailyConditions(): DailyConditions? {
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return DailyConditions(
        date = parsedDate,
        weatherCode = WeatherCode.fromCode(weatherCode),
        temperatureHigh = temperatureHigh,
        temperatureLow = temperatureLow,
        precipitationProbability = precipitationProbability,
        precipitationSum = precipitationSum,
        sunrise = sunrise,
        sunset = sunset,
        uvIndexMax = uvIndexMax,
        windSpeedMax = windSpeedMax,
        windDirectionDominant = windDirectionDominant,
        snowfallSum = snowfallSum,
        sunshineDuration = sunshineDuration,
        windGustsMax = windGustsMax,
        precipitationHours = precipitationHours,
    )
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    runCatching { LocalDateTime.parse(this) }.getOrNull()
