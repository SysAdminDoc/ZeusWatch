package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val nwsApi: NwsAlertApi,
) {
    suspend fun getAlerts(latitude: Double, longitude: Double): Result<List<WeatherAlert>> =
        withContext(Dispatchers.IO) {
            try {
                val point = "%.4f,%.4f".format(latitude, longitude)
                val response = nwsApi.getActiveAlerts(point)
                val alerts = response.features.mapNotNull { feature ->
                    val props = feature.properties ?: return@mapNotNull null
                    val event = props.event ?: return@mapNotNull null
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
                    )
                }.sortedWith(
                    compareBy<WeatherAlert> { it.severity.sortOrder }
                        .thenBy { it.urgency.sortOrder }
                )
                Result.success(alerts)
            } catch (e: Exception) {
                // NWS only covers US territories; non-US locations will 404
                // Return empty list instead of error for graceful degradation
                if (e.message?.contains("404") == true ||
                    e.message?.contains("400") == true
                ) {
                    Result.success(emptyList())
                } else {
                    Result.failure(e)
                }
            }
        }
}
