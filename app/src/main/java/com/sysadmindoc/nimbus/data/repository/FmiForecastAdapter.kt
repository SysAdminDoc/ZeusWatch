package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.FmiForecastApi
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import kotlinx.coroutines.CancellationException
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "FmiForecastAdapter"
private const val MS_TO_KMH = 3.6

@Singleton
class FmiForecastAdapter @Inject constructor(
    private val api: FmiForecastApi,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationZone: ZoneId? = null,
    ): Result<WeatherData> {
        if (!isFmiHarmonieCoordinate(latitude, longitude)) {
            return Result.failure(
                IllegalArgumentException("FMI Harmonie forecast is only available in the Nordic/Baltic region"),
            )
        }

        return try {
            val latLon = "%.6f,%.6f".format(Locale.US, latitude, longitude)
            val xml = api.getHarmonieForecast(latLon = latLon).use { it.string() }
            Result.success(mapToWeatherData(xml, latitude, longitude, locationName, locationZone))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FMI forecast failed", e)
            Result.failure(e)
        }
    }

    internal fun mapToWeatherData(
        xml: String,
        requestedLat: Double,
        requestedLon: Double,
        locationName: String?,
        requestedZone: ZoneId? = null,
        now: LocalDateTime? = null,
    ): WeatherData {
        val root = parseXml(xml).documentElement
        if (root.localName == "ExceptionReport") {
            error(root.getElementsByTagNameNS("*", "ExceptionText").asElements()
                .firstOrNull()
                ?.textContent
                ?.trim()
                ?: "FMI returned an OGC exception")
        }

        val metadata = root.toLocationMetadata()
        val zone = requestedZone ?: metadata.timeZone?.toZoneIdOrNull() ?: ZoneId.systemDefault()
        val series = root.toSeries(zone)
        val temperatures = series[TEMPERATURE].orEmpty()
        require(temperatures.isNotEmpty()) { "FMI response missing temperature series" }

        val referenceTime = now ?: LocalDateTime.now(zone)
        val currentTime = temperatures.keys.minByOrNull { time ->
            abs(Duration.between(referenceTime, time).toMinutes())
        } ?: temperatures.keys.first()
        val hourly = buildHourly(series, referenceTime)
        val daily = buildDaily(series)
        val todayDaily = daily.firstOrNull { it.date == currentTime.toLocalDate() }
        val currentTemperature = series.valueAt(TEMPERATURE, currentTime) ?: 0.0
        val currentSymbol = series.valueAt(WEATHER_SYMBOL_3, currentTime)
        val currentWmo = currentSymbol?.roundToInt()?.let(::weatherSymbol3ToWmo)
            ?: codeFromCloudCover(series.valueAt(TOTAL_CLOUD_COVER, currentTime))

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: metadata.name ?: "Unknown",
                region = metadata.region.orEmpty(),
                country = metadata.country.orEmpty(),
                latitude = requestedLat,
                longitude = requestedLon,
                timeZone = zone.id,
            ),
            current = CurrentConditions(
                temperature = currentTemperature,
                feelsLike = currentTemperature,
                humidity = series.valueAt(HUMIDITY, currentTime).toPercentInt(),
                weatherCode = WeatherCode.fromCode(currentWmo),
                observationTime = currentTime,
                isDay = currentTime.isDayHour(),
                windSpeed = series.valueAt(WIND_SPEED_MS, currentTime).toKmh(),
                windDirection = series.valueAt(WIND_DIRECTION, currentTime).toDirectionInt(),
                windGusts = series.valueAt(WIND_GUST, currentTime)?.toKmh(),
                pressure = series.valueAt(PRESSURE, currentTime) ?: 0.0,
                uvIndex = 0.0,
                visibility = series.valueAt(VISIBILITY, currentTime),
                dewPoint = series.valueAt(DEW_POINT, currentTime),
                cloudCover = series.valueAt(TOTAL_CLOUD_COVER, currentTime).toPercentInt(),
                precipitation = series.precipAt(currentTime) ?: 0.0,
                dailyHigh = todayDaily?.temperatureHigh ?: currentTemperature,
                dailyLow = todayDaily?.temperatureLow ?: currentTemperature,
                sunrise = null,
                sunset = null,
            ),
            hourly = hourly,
            daily = daily,
        )
    }

    private fun buildHourly(
        series: Map<String, Map<LocalDateTime, Double>>,
        referenceTime: LocalDateTime,
    ): List<HourlyConditions> {
        return series[TEMPERATURE].orEmpty().keys
            .sorted()
            .filter { !it.isBefore(referenceTime.minusHours(1)) }
            .map { time ->
                val symbol = series.valueAt(WEATHER_SYMBOL_3, time)
                val wmo = symbol?.roundToInt()?.let(::weatherSymbol3ToWmo)
                    ?: codeFromCloudCover(series.valueAt(TOTAL_CLOUD_COVER, time))
                HourlyConditions(
                    time = time,
                    temperature = series.valueAt(TEMPERATURE, time) ?: 0.0,
                    feelsLike = null,
                    weatherCode = WeatherCode.fromCode(wmo),
                    isDay = time.isDayHour(),
                    precipitationProbability = 0,
                    precipitation = series.precipAt(time),
                    windSpeed = series.valueAt(WIND_SPEED_MS, time)?.toKmh(),
                    windDirection = series.valueAt(WIND_DIRECTION, time)?.toDirectionInt(),
                    humidity = series.valueAt(HUMIDITY, time)?.toPercentInt(),
                    uvIndex = null,
                    cloudCover = series.valueAt(TOTAL_CLOUD_COVER, time)?.toPercentInt(),
                    visibility = series.valueAt(VISIBILITY, time),
                    windGusts = series.valueAt(WIND_GUST, time)?.toKmh(),
                    surfacePressure = series.valueAt(PRESSURE, time),
                )
            }
    }

    private fun buildDaily(
        series: Map<String, Map<LocalDateTime, Double>>,
    ): List<DailyConditions> {
        return series[TEMPERATURE].orEmpty().keys
            .groupBy(LocalDateTime::toLocalDate)
            .map { (date, times) ->
                val temps = times.mapNotNull { series.valueAt(TEMPERATURE, it) }
                val winds = times.mapNotNull { series.valueAt(WIND_SPEED_MS, it)?.toKmh() }
                val gusts = times.mapNotNull { series.valueAt(WIND_GUST, it)?.toKmh() }
                val windDirs = times.mapNotNull { series.valueAt(WIND_DIRECTION, it) }
                val precip = times.mapNotNull { series.precipAt(it) }
                val dominantWmo = times.mapNotNull { time ->
                    series.valueAt(WEATHER_SYMBOL_3, time)?.roundToInt()?.let(::weatherSymbol3ToWmo)
                }.filter { it > 0 }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                    ?: codeFromCloudCover(times.mapNotNull { series.valueAt(TOTAL_CLOUD_COVER, it) }.averageOrNull())

                DailyConditions(
                    date = date,
                    weatherCode = WeatherCode.fromCode(dominantWmo),
                    temperatureHigh = temps.maxOrNull() ?: 0.0,
                    temperatureLow = temps.minOrNull() ?: 0.0,
                    precipitationProbability = 0,
                    precipitationSum = precip.sum().takeIf { it > 0.0 },
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = null,
                    windSpeedMax = winds.maxOrNull(),
                    windDirectionDominant = windDirs.averageDirectionOrNull(),
                    snowfallSum = null,
                    sunshineDuration = null,
                    windGustsMax = gusts.maxOrNull(),
                )
            }
            .sortedBy(DailyConditions::date)
    }

    private fun parseXml(xml: String) = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = true
            configureSecureXml()
        }
        .newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))

    private fun DocumentBuilderFactory.configureSecureXml() {
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
        runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
    }

    private fun Element.toLocationMetadata(): FmiLocationMetadata {
        val location = getElementsByTagNameNS("*", "Location").asElements().firstOrNull()
        val name = location?.childElements("name")
            ?.firstOrNull { it.getAttribute("codeSpace").contains("/name") }
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: location?.childElements("name")?.firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }
        return FmiLocationMetadata(
            name = name,
            region = location?.childText("region"),
            country = location?.childText("country"),
            timeZone = location?.childText("timezone"),
        )
    }

    private fun Element.toSeries(zone: ZoneId): Map<String, Map<LocalDateTime, Double>> {
        return getElementsByTagNameNS("*", "PointTimeSeriesObservation")
            .asElements()
            .mapNotNull { observation ->
                val parameter = observation.parameterName() ?: return@mapNotNull null
                val values = observation.getElementsByTagNameNS("*", "MeasurementTVP")
                    .asElements()
                    .mapNotNull { tvp ->
                        val time = tvp.childText("time")?.let { parseTimestamp(it, zone) } ?: return@mapNotNull null
                        val value = tvp.childText("value")?.toDoubleOrNull()?.takeIf { it.isFinite() }
                            ?: return@mapNotNull null
                        time to value
                    }
                    .toMap()
                parameter to values
            }
            .toMap()
    }

    private fun Element.parameterName(): String? {
        val property = getElementsByTagNameNS("*", "observedProperty").asElements().firstOrNull()
            ?: return null
        val href = property.getAttributeNS("http://www.w3.org/1999/xlink", "href")
            .ifBlank { property.getAttribute("xlink:href") }
            .ifBlank { property.getAttribute("href") }
        return PARAMETER_REGEX.find(href)?.groupValues?.getOrNull(1)
    }

    private fun parseTimestamp(value: String, zone: ZoneId): LocalDateTime? =
        runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()

    companion object {
        private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
        private val PARAMETER_REGEX = Regex("""[?&]param=([^&]+)""")
    }
}

private data class FmiLocationMetadata(
    val name: String?,
    val region: String?,
    val country: String?,
    val timeZone: String?,
)

internal fun isFmiHarmonieCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude in 53.0..72.5 && longitude in 3.0..33.5

internal fun weatherSymbol3ToWmo(code: Int): Int = when (code) {
    1 -> 0
    2 -> 2
    3 -> 3
    21 -> 80
    22 -> 81
    23 -> 82
    31 -> 61
    32 -> 63
    33 -> 65
    41 -> 85
    42 -> 85
    43 -> 86
    51 -> 71
    52 -> 73
    53 -> 75
    61, 63 -> 95
    62, 64 -> 96
    71, 81 -> 66
    72, 73, 82, 83 -> 67
    91, 92 -> 45
    else -> 0
}

private fun codeFromCloudCover(cloudCover: Double?): Int = when {
    cloudCover == null -> 0
    cloudCover < 12.5 -> 0
    cloudCover < 37.5 -> 1
    cloudCover < 75.0 -> 2
    else -> 3
}

private fun Map<String, Map<LocalDateTime, Double>>.valueAt(parameter: String, time: LocalDateTime): Double? =
    this[parameter]?.get(time)

private fun Map<String, Map<LocalDateTime, Double>>.precipAt(time: LocalDateTime): Double? =
    valueAt(PRECIPITATION_1H, time)

private fun String.toZoneIdOrNull(): ZoneId? = runCatching { ZoneId.of(this) }.getOrNull()

private fun LocalDateTime.isDayHour(): Boolean = hour in 6..19

private fun Double?.toKmh(): Double = (this ?: 0.0) * MS_TO_KMH

private fun Double?.toPercentInt(): Int = this?.coerceIn(0.0, 100.0)?.roundToInt() ?: 0

private fun Double?.toDirectionInt(): Int = this?.let { ((it.roundToInt() % 360) + 360) % 360 } ?: 0

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

private fun List<Double>.averageDirectionOrNull(): Int? {
    if (isEmpty()) return null
    val sinSum = sumOf { kotlin.math.sin(Math.toRadians(it)) }
    val cosSum = sumOf { kotlin.math.cos(Math.toRadians(it)) }
    return ((Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)) + 360) % 360).roundToInt()
}

private fun Element.childText(localName: String): String? =
    childElements(localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }

private fun Element.childElements(localName: String): List<Element> =
    (getElementsByTagNameNS("*", localName).asElements() + getElementsByTagName(localName).asElements())
        .distinct()
        .filter { it.parentNode == this }

private fun NodeList.asElements(): List<Element> =
    (0 until length).mapNotNull { index -> item(index) as? Element }

private const val TEMPERATURE = "Temperature"
private const val HUMIDITY = "Humidity"
private const val PRESSURE = "Pressure"
private const val WIND_SPEED_MS = "WindSpeedMS"
private const val WIND_DIRECTION = "WindDirection"
private const val WIND_GUST = "WindGust"
private const val TOTAL_CLOUD_COVER = "TotalCloudCover"
private const val PRECIPITATION_1H = "Precipitation1h"
private const val VISIBILITY = "Visibility"
private const val DEW_POINT = "DewPoint"
private const val WEATHER_SYMBOL_3 = "WeatherSymbol3"
