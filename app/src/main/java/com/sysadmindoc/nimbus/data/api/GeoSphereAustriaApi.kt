package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface GeoSphereAustriaDatasetApi {
    @GET("timeseries/forecast/nowcast-v1-15min-1km")
    suspend fun getIncaNowcast(
        @Query("lat_lon") latLon: String,
        @Query("parameters") parameters: String = "rr",
        @Query("forecast_offset") forecastOffset: Int = 0,
    ): GeoSphereNowcastResponse

    companion object {
        const val BASE_URL = "https://dataset.api.hub.geosphere.at/v1/"
    }
}

interface GeoSphereAustriaWarnApi {
    @GET("getWarningsForCoords")
    suspend fun getWarningsForCoords(
        @Query("lon") longitude: Double,
        @Query("lat") latitude: Double,
        @Query("lang") language: String = "en",
    ): GeoSphereWarningResponse

    companion object {
        const val BASE_URL = "https://warnungen.zamg.at/wsapp/api/"
    }
}

@Serializable
data class GeoSphereNowcastResponse(
    val timestamps: List<String> = emptyList(),
    val features: List<GeoSphereNowcastFeature> = emptyList(),
)

@Serializable
data class GeoSphereNowcastFeature(
    val properties: GeoSphereNowcastProperties? = null,
)

@Serializable
data class GeoSphereNowcastProperties(
    val parameters: GeoSphereNowcastParameters = GeoSphereNowcastParameters(),
)

@Serializable
data class GeoSphereNowcastParameters(
    val rr: GeoSphereNowcastSeries? = null,
)

@Serializable
data class GeoSphereNowcastSeries(
    val data: List<Double?> = emptyList(),
)

@Serializable
data class GeoSphereWarningResponse(
    val properties: GeoSphereWarningProperties = GeoSphereWarningProperties(),
)

@Serializable
data class GeoSphereWarningProperties(
    val location: GeoSphereWarningLocation? = null,
    val warnings: List<GeoSphereWarningObject> = emptyList(),
)

@Serializable
data class GeoSphereWarningLocation(
    val properties: GeoSphereWarningLocationProperties? = null,
)

@Serializable
data class GeoSphereWarningLocationProperties(
    val name: String? = null,
)

@Serializable
data class GeoSphereWarningObject(
    val warnid: JsonElement? = null,
    @SerialName("warnstufeid") val warningLevel: Int? = null,
    @SerialName("warntypid") val warningType: Int? = null,
    val begin: String? = null,
    val end: String? = null,
    val text: String? = null,
    @SerialName("auswirkungen") val impacts: String? = null,
    @SerialName("empfehlungen") val recommendations: String? = null,
    @SerialName("meteotext") val meteorologistText: String? = null,
)

@Singleton
class GeoSphereAustriaNowcastAdapter @Inject constructor(
    private val api: GeoSphereAustriaDatasetApi,
) {
    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> {
        if (!isGeoSphereNowcastCoordinate(latitude, longitude)) {
            return Result.success(emptyList())
        }

        return try {
            val latLon = "%.6f,%.6f".format(Locale.US, latitude, longitude)
            val response = api.getIncaNowcast(latLon = latLon)
            val precipitation = response.features
                .firstOrNull()
                ?.properties
                ?.parameters
                ?.rr
                ?.data
                .orEmpty()
            val data = response.timestamps.mapIndexedNotNull { index, timestamp ->
                val time = parseGeoSphereTimestamp(timestamp) ?: return@mapIndexedNotNull null
                MinutelyPrecipitation(
                    time = time,
                    precipitation = precipitation.getOrNull(index) ?: 0.0,
                )
            }
            Result.success(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class GeoSphereAustriaAlertAdapter @Inject constructor(
    private val api: GeoSphereAustriaWarnApi,
) {
    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> {
        if (!isAustriaWarningCoordinate(latitude, longitude)) {
            return Result.success(emptyList())
        }

        return try {
            val response = api.getWarningsForCoords(latitude = latitude, longitude = longitude)
            val area = response.properties.location?.properties?.name ?: "Austria"
            Result.success(
                response.properties.warnings.mapNotNull { warning ->
                    warning.toWeatherAlert(area)
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun GeoSphereWarningObject.toWeatherAlert(area: String): WeatherAlert? {
        val event = warningType.toGeoSphereEvent()
        val headline = text.firstNonBlankLine() ?: "$event warning"
        val description = listOfNotNull(
            text?.trim()?.takeIf { it.isNotBlank() },
            impacts?.trim()?.takeIf { it.isNotBlank() },
            meteorologistText?.trim()?.takeIf { it.isNotBlank() },
        ).distinct().joinToString("\n\n")

        return WeatherAlert(
            id = warnid.toStableWarningId(event, begin),
            event = event,
            headline = headline,
            description = description,
            instruction = recommendations?.trim()?.takeIf { it.isNotBlank() },
            severity = warningLevel.toGeoSphereSeverity(),
            urgency = begin.toGeoSphereUrgency(),
            certainty = "Likely",
            senderName = "GeoSphere Austria",
            areaDescription = area,
            effective = begin,
            expires = end,
            response = null,
            coversRequestedLocation = true,
        )
    }
}

private fun parseGeoSphereTimestamp(value: String): LocalDateTime? =
    runCatching { OffsetDateTime.parse(value).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        .getOrNull()

private fun JsonElement?.toStableWarningId(
    event: String,
    begin: String?,
): String {
    val primitive = this as? JsonPrimitive
    val value = primitive?.longOrNull?.toString() ?: primitive?.content?.takeIf { it.isNotBlank() }
    return value ?: "geosphere_${event.lowercase(Locale.US).replace(' ', '_')}_${begin.orEmpty()}"
}

private fun Int?.toGeoSphereEvent(): String = when (this) {
    1 -> "Wind"
    2 -> "Rain"
    3 -> "Snow"
    4 -> "Black ice"
    5 -> "Thunderstorm"
    6 -> "Heat"
    7 -> "Cold"
    else -> "Weather"
}

private fun Int?.toGeoSphereSeverity(): AlertSeverity = when (this) {
    1 -> AlertSeverity.MINOR
    2 -> AlertSeverity.MODERATE
    3 -> AlertSeverity.SEVERE
    else -> AlertSeverity.UNKNOWN
}

private fun String?.toGeoSphereUrgency(): AlertUrgency {
    val begin = this?.let(::parseGeoSphereTimestamp) ?: return AlertUrgency.UNKNOWN
    val now = LocalDateTime.now()
    return if (!begin.isAfter(now.plusMinutes(30))) {
        AlertUrgency.IMMEDIATE
    } else {
        AlertUrgency.EXPECTED
    }
}

private fun String?.firstNonBlankLine(): String? =
    this
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotBlank() }

private fun isGeoSphereNowcastCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude in 45.49..49.49 && longitude in 8.09..17.75

private fun isAustriaWarningCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude in 46.3..49.1 && longitude in 9.4..17.2
