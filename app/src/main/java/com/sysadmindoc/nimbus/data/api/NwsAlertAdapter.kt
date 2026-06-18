package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertCoordinate
import com.sysadmindoc.nimbus.data.model.AlertGeometry
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] wrapping the existing [NwsAlertApi] (National Weather Service).
 * Covers the United States and its territories.
 */
@Singleton
class NwsAlertAdapter @Inject constructor(
    private val nwsApi: NwsAlertApi,
) : AlertSourceAdapter {

    override val sourceId = "nws"
    override val displayName = "National Weather Service"
    override val supportedRegions = setOf("US")

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        return try {
            // Must force Locale.US — default-locale format() produces comma
            // decimals ("39,7392,-104,9903") in de_DE/fr_FR/etc., which NWS
            // then rejects as a malformed point.
            val point = String.format(Locale.US, "%.4f,%.4f", lat, lon)
            val response = nwsApi.getActiveAlerts(point)
            val alerts = response.features.mapNotNull { feature ->
                val props = feature.properties ?: return@mapNotNull null
                val event = props.event ?: return@mapNotNull null
                val geometry = feature.geometry.toAlertGeometry()
                WeatherAlert(
                    id = feature.id ?: props.alertId ?: "",
                    event = event,
                    headline = props.headline ?: event,
                    description = props.description ?: "",
                    instruction = props.instruction,
                    severity = AlertSeverity.from(props.severity),
                    urgency = AlertUrgency.from(props.urgency),
                    certainty = props.certainty ?: "Unknown",
                    senderName = props.senderName ?: "National Weather Service",
                    areaDescription = props.areaDesc ?: "",
                    effective = props.effective ?: props.onset,
                    expires = props.expires ?: props.ends,
                    response = props.response,
                    geometry = geometry,
                    coversRequestedLocation = geometry?.contains(lat, lon),
                )
            }
            Result.success(alerts)
        } catch (e: HttpException) {
            // NWS returns 404/400 for non-US coordinates — treat as empty.
            // Use the HTTP code directly instead of `e.message?.contains("404")`,
            // which is fragile to Retrofit/OkHttp message-format changes.
            if (e.code() == 404 || e.code() == 400) {
                Result.success(emptyList())
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

internal fun NwsAlertGeometry?.toAlertGeometry(): AlertGeometry? {
    val coordinates = this?.coordinates as? JsonArray ?: return null
    val polygons = when (type) {
        "Polygon" -> listOfNotNull(parseAlertPolygon(coordinates))
        "MultiPolygon" -> coordinates.mapNotNull { parseAlertPolygon(it as? JsonArray) }
        else -> emptyList()
    }
    return polygons.takeIf { it.isNotEmpty() }?.let(::AlertGeometry)
}

private fun parseAlertPolygon(rawPolygon: JsonArray?): AlertPolygon? {
    val outerRing = rawPolygon?.firstOrNull() as? JsonArray ?: return null
    val points = outerRing.mapNotNull { coordinate ->
        val pair = coordinate as? JsonArray ?: return@mapNotNull null
        val longitude = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val latitude = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        AlertCoordinate(latitude = latitude, longitude = longitude)
    }
    return AlertPolygon(points).takeIf { points.size >= 3 }
}
