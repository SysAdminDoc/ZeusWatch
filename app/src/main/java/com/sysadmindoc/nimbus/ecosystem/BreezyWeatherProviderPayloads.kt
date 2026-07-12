package com.sysadmindoc.nimbus.ecosystem

import android.net.Uri
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.PressureUnit
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import com.sysadmindoc.nimbus.data.repository.toZoneIdOrNull
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class BreezyLocationRow(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentPosition: Boolean,
    val timeZone: String,
    val customName: String?,
    val country: String,
    val countryCode: String?,
    val admin1: String?,
    val admin1Code: String?,
    val admin2: String?,
    val admin2Code: String?,
    val admin3: String?,
    val admin3Code: String?,
    val admin4: String?,
    val admin4Code: String?,
    val city: String,
    val district: String?,
    val weather: ByteArray?,
)

internal fun SavedLocationEntity.toBreezyLocationRow(weather: ByteArray? = null): BreezyLocationRow {
    val displayName = name.ifBlank { "Current location" }
    return BreezyLocationRow(
        id = id.takeIf { it > 0L }?.toString() ?: coordinateLocationId(latitude, longitude),
        latitude = latitude,
        longitude = longitude,
        isCurrentPosition = isCurrentLocation,
        timeZone = timeZone?.takeIf { it.toZoneIdOrNull() != null } ?: ZoneId.systemDefault().id,
        customName = displayName.takeUnless { isCurrentLocation },
        country = country,
        countryCode = null,
        admin1 = region.ifBlank { null },
        admin1Code = null,
        admin2 = null,
        admin2Code = null,
        admin3 = null,
        admin3Code = null,
        admin4 = null,
        admin4Code = null,
        city = displayName,
        district = null,
        weather = weather,
    )
}

internal data class BreezyUnitPreferences(
    val temperatureUnit: String,
    val precipitationUnit: String,
    val speedUnit: String,
    val distanceUnit: String,
    val pressureUnit: String,
) {
    companion object {
        fun from(settings: NimbusSettings, uri: Uri? = null): BreezyUnitPreferences =
            BreezyUnitPreferences(
                temperatureUnit = uri.validQueryUnit("temperatureUnit", setOf("c", "f", "k"))
                    ?: when (settings.tempUnit) {
                        TempUnit.CELSIUS -> "c"
                        TempUnit.FAHRENHEIT -> "f"
                    },
                precipitationUnit = uri.validQueryUnit("precipitationUnit", setOf("mm", "cm", "in", "lpsqm"))
                    ?: when (settings.precipUnit) {
                        PrecipUnit.MM -> "mm"
                        PrecipUnit.INCHES -> "in"
                    },
                speedUnit = uri.validQueryUnit("speedUnit", setOf("mps", "kph", "kn", "mph", "ftps", "bf"))
                    ?: when (settings.windUnit) {
                        WindUnit.MS -> "mps"
                        WindUnit.KMH -> "kph"
                        WindUnit.KNOTS -> "kn"
                        WindUnit.MPH -> "mph"
                    },
                distanceUnit = uri.validQueryUnit("distanceUnit", setOf("m", "km", "mi", "nmi", "ft"))
                    ?: when (settings.visibilityUnit) {
                        VisibilityUnit.KM -> "km"
                        VisibilityUnit.MILES -> "mi"
                    },
                pressureUnit = uri.validQueryUnit(
                    "pressureUnit",
                    setOf("mb", "kpa", "hpa", "atm", "mmhg", "inhg", "kgfpsqcm"),
                ) ?: when (settings.pressureUnit) {
                    PressureUnit.HPA -> "hpa"
                    PressureUnit.MBAR -> "mb"
                    PressureUnit.INHG -> "inhg"
                },
            )
    }
}

internal fun WeatherData.toBreezyWeatherBlob(
    units: BreezyUnitPreferences,
    refreshEpochMillis: Long,
    conditionLabel: (WeatherCode) -> String = { it.description },
): ByteArray {
    val zone = location.timeZone.toZoneIdOrNull() ?: ZoneId.systemDefault()
    val payload = BreezyWeatherPayload(
        refreshTime = refreshEpochMillis,
        current = current.let { current ->
            BreezyCurrentPayload(
                weatherText = current.sourceConditionText.takeUnlessBlank() ?: conditionLabel(current.weatherCode),
                weatherCode = current.weatherCode.code.toString(),
                temperature = BreezyTemperaturePayload(
                    temperature = current.temperature.temperatureUnit(units),
                    sourceFeelsLike = current.feelsLike.temperatureUnit(units),
                    computedApparent = current.feelsLike.temperatureUnit(units),
                ),
                wind = BreezyWindPayload(
                    degree = current.windDirection.toDouble(),
                    speed = current.windSpeed.speedUnit(units),
                    gusts = current.windGusts?.speedUnit(units),
                ),
                uV = current.uvIndex.unit(null),
                relativeHumidity = current.humidity.toDouble().unit("%"),
                dewPoint = current.dewPoint?.temperatureUnit(units),
                pressure = current.pressure.pressureUnit(units),
                cloudCover = current.cloudCover.toDouble().unit("%"),
                visibility = current.visibility?.distanceUnit(units),
            )
        },
        daily = daily.map { it.toBreezyDaily(units, zone, conditionLabel) },
        hourly = hourly.map { it.toBreezyHourly(units, zone, conditionLabel) },
        sources = mapOf(
            "forecast" to BreezySourcePayload(
                type = "Forecast",
                text = sourceProvider?.takeUnlessBlank() ?: "ZeusWatch cache",
            ),
        ),
    )
    return gzipUtf8(breezyJson.encodeToString(payload))
}

private fun HourlyConditions.toBreezyHourly(
    units: BreezyUnitPreferences,
    zone: ZoneId,
    conditionLabel: (WeatherCode) -> String,
): BreezyHourlyPayload = BreezyHourlyPayload(
    date = time.toEpochMillis(zone),
    isDaylight = isDay,
    weatherText = sourceConditionText.takeUnlessBlank() ?: conditionLabel(weatherCode),
    weatherCode = weatherCode.code.toString(),
    temperature = BreezyTemperaturePayload(
        temperature = temperature.temperatureUnit(units),
        sourceFeelsLike = feelsLike?.temperatureUnit(units),
        computedApparent = feelsLike?.temperatureUnit(units),
    ),
    precipitation = precipitation?.let { amount ->
        BreezyPrecipitationPayload(
            total = amount.precipitationUnit(units),
            rain = amount.precipitationUnit(units),
            snow = snowfall?.precipitationUnit(units),
        )
    },
    precipitationProbability = BreezyPrecipitationProbabilityPayload(
        total = precipitationProbability.toDouble().unit("%"),
    ),
    wind = BreezyWindPayload(
        degree = windDirection?.toDouble(),
        speed = windSpeed?.speedUnit(units),
        gusts = windGusts?.speedUnit(units),
    ),
    uV = uvIndex?.unit(null),
    relativeHumidity = humidity?.toDouble()?.unit("%"),
    dewPoint = null,
    pressure = surfacePressure?.pressureUnit(units),
    cloudCover = cloudCover?.toDouble()?.unit("%"),
    visibility = visibility?.distanceUnit(units),
)

private fun DailyConditions.toBreezyDaily(
    units: BreezyUnitPreferences,
    zone: ZoneId,
    conditionLabel: (WeatherCode) -> String,
): BreezyDailyPayload = BreezyDailyPayload(
    date = date.atStartOfDay().toEpochMillis(zone),
    day = BreezyHalfDayPayload(
        weatherText = sourceConditionText.takeUnlessBlank() ?: conditionLabel(weatherCode),
        weatherCode = weatherCode.code.toString(),
        temperature = BreezyTemperaturePayload(
            temperature = temperatureHigh.temperatureUnit(units),
        ),
        precipitation = precipitationSum?.let { amount ->
            BreezyPrecipitationPayload(
                total = amount.precipitationUnit(units),
                rain = amount.precipitationUnit(units),
                snow = snowfallSum?.precipitationUnit(units),
            )
        },
        precipitationProbability = BreezyPrecipitationProbabilityPayload(
            total = precipitationProbability.toDouble().unit("%"),
        ),
        precipitationDuration = precipitationHours?.let { hours ->
            BreezyPrecipitationDurationPayload(total = hours.unit("h"))
        },
        wind = BreezyWindPayload(
            degree = windDirectionDominant?.toDouble(),
            speed = windSpeedMax?.speedUnit(units),
            gusts = windGustsMax?.speedUnit(units),
        ),
    ),
    night = BreezyHalfDayPayload(
        weatherText = sourceConditionText.takeUnlessBlank() ?: conditionLabel(weatherCode),
        weatherCode = weatherCode.code.toString(),
        temperature = BreezyTemperaturePayload(
            temperature = temperatureLow.temperatureUnit(units),
        ),
    ),
    uV = uvIndexMax?.unit(null),
    sunshineDuration = sunshineDuration?.div(3600.0)?.unit("h"),
)

private fun Uri?.validQueryUnit(key: String, allowed: Set<String>): String? =
    this?.getQueryParameter(key)
        ?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf { it in allowed }

private fun Double.temperatureUnit(units: BreezyUnitPreferences): BreezyUnitPayload =
    when (units.temperatureUnit) {
        "f" -> (this * 9.0 / 5.0 + 32.0).unit("f")
        "k" -> (this + 273.15).unit("k")
        else -> unit("c")
    }

private fun Double.precipitationUnit(units: BreezyUnitPreferences): BreezyUnitPayload =
    when (units.precipitationUnit) {
        "cm" -> (this / 10.0).unit("cm")
        "in" -> (this / 25.4).unit("in")
        "lpsqm" -> unit("lpsqm")
        else -> unit("mm")
    }

private fun Double.speedUnit(units: BreezyUnitPreferences): BreezyUnitPayload =
    when (units.speedUnit) {
        "mps" -> (this / 3.6).unit("mps")
        "kn" -> (this / 1.852).unit("kn")
        "mph" -> (this / 1.609344).unit("mph")
        "ftps" -> (this / 1.09728).unit("ftps")
        "bf" -> toBeaufort().toDouble().unit("bf")
        else -> unit("kph")
    }

private fun Double.distanceUnit(units: BreezyUnitPreferences): BreezyUnitPayload =
    when (units.distanceUnit) {
        "km" -> (this / 1000.0).unit("km")
        "mi" -> (this / 1609.344).unit("mi")
        "nmi" -> (this / 1852.0).unit("nmi")
        "ft" -> (this * 3.280839895).unit("ft")
        else -> unit("m")
    }

private fun Double.pressureUnit(units: BreezyUnitPreferences): BreezyUnitPayload =
    when (units.pressureUnit) {
        "kpa" -> (this / 10.0).unit("kpa")
        "atm" -> (this / 1013.25).unit("atm")
        "mmhg" -> (this * 0.750061683).unit("mmhg")
        "inhg" -> (this * 0.029529983).unit("inhg")
        "kgfpsqcm" -> (this / 980.665).unit("kgfpsqcm")
        "mb" -> unit("mb")
        else -> unit("hpa")
    }

private fun Double.toBeaufort(): Int = when {
    this < 1.0 -> 0
    this < 6.0 -> 1
    this < 12.0 -> 2
    this < 20.0 -> 3
    this < 29.0 -> 4
    this < 39.0 -> 5
    this < 50.0 -> 6
    this < 62.0 -> 7
    this < 75.0 -> 8
    this < 89.0 -> 9
    this < 103.0 -> 10
    this < 118.0 -> 11
    else -> 12
}

private fun Double.unit(unit: String?): BreezyUnitPayload = BreezyUnitPayload(value = this, unit = unit)

private fun LocalDateTime.toEpochMillis(zone: ZoneId): Long =
    atZone(zone).toInstant().toEpochMilli()

private fun String?.takeUnlessBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun coordinateLocationId(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "coord:%.4f,%.4f", latitude, longitude)

private fun gzipUtf8(value: String): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { gzip ->
        gzip.write(value.toByteArray(Charsets.UTF_8))
    }
    return output.toByteArray()
}

private val breezyJson = Json {
    encodeDefaults = false
}

@Serializable
private data class BreezyWeatherPayload(
    val refreshTime: Long? = null,
    val current: BreezyCurrentPayload? = null,
    val daily: List<BreezyDailyPayload>? = null,
    val hourly: List<BreezyHourlyPayload>? = null,
    val sources: Map<String, BreezySourcePayload>? = null,
)

@Serializable
private data class BreezyCurrentPayload(
    val weatherText: String? = null,
    val weatherCode: String? = null,
    val temperature: BreezyTemperaturePayload? = null,
    val wind: BreezyWindPayload? = null,
    val uV: BreezyUnitPayload? = null,
    val relativeHumidity: BreezyUnitPayload? = null,
    val dewPoint: BreezyUnitPayload? = null,
    val pressure: BreezyUnitPayload? = null,
    val cloudCover: BreezyUnitPayload? = null,
    val visibility: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyHourlyPayload(
    val date: Long,
    val isDaylight: Boolean = true,
    val weatherText: String? = null,
    val weatherCode: String? = null,
    val temperature: BreezyTemperaturePayload? = null,
    val precipitation: BreezyPrecipitationPayload? = null,
    val precipitationProbability: BreezyPrecipitationProbabilityPayload? = null,
    val wind: BreezyWindPayload? = null,
    val uV: BreezyUnitPayload? = null,
    val relativeHumidity: BreezyUnitPayload? = null,
    val dewPoint: BreezyUnitPayload? = null,
    val pressure: BreezyUnitPayload? = null,
    val cloudCover: BreezyUnitPayload? = null,
    val visibility: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyDailyPayload(
    val date: Long,
    val day: BreezyHalfDayPayload? = null,
    val night: BreezyHalfDayPayload? = null,
    val uV: BreezyUnitPayload? = null,
    val sunshineDuration: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyHalfDayPayload(
    val weatherText: String? = null,
    val weatherSummary: String? = null,
    val weatherCode: String? = null,
    val temperature: BreezyTemperaturePayload? = null,
    val precipitation: BreezyPrecipitationPayload? = null,
    val precipitationProbability: BreezyPrecipitationProbabilityPayload? = null,
    val precipitationDuration: BreezyPrecipitationDurationPayload? = null,
    val wind: BreezyWindPayload? = null,
)

@Serializable
private data class BreezyTemperaturePayload(
    val temperature: BreezyUnitPayload? = null,
    val sourceFeelsLike: BreezyUnitPayload? = null,
    val computedApparent: BreezyUnitPayload? = null,
    val computedWindChill: BreezyUnitPayload? = null,
    val computedHumidex: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyWindPayload(
    val degree: Double? = null,
    val speed: BreezyUnitPayload? = null,
    val gusts: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyPrecipitationPayload(
    val total: BreezyUnitPayload? = null,
    val thunderstorm: BreezyUnitPayload? = null,
    val rain: BreezyUnitPayload? = null,
    val snow: BreezyUnitPayload? = null,
    val ice: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyPrecipitationProbabilityPayload(
    val total: BreezyUnitPayload? = null,
    val thunderstorm: BreezyUnitPayload? = null,
    val rain: BreezyUnitPayload? = null,
    val snow: BreezyUnitPayload? = null,
    val ice: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyPrecipitationDurationPayload(
    val total: BreezyUnitPayload? = null,
    val thunderstorm: BreezyUnitPayload? = null,
    val rain: BreezyUnitPayload? = null,
    val snow: BreezyUnitPayload? = null,
    val ice: BreezyUnitPayload? = null,
)

@Serializable
private data class BreezyUnitPayload(
    val value: Double? = null,
    val unit: String? = null,
    val description: String? = null,
    val color: String? = null,
)

@Serializable
private data class BreezySourcePayload(
    val type: String? = null,
    val text: String? = null,
    val links: Map<String, String>? = null,
)

