package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EnsembleResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String? = null,
    val hourly: JsonObject? = null,
)
