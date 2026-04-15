package com.sysadmindoc.nimbus.sync

import android.content.Context
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op implementation for freenet (F-Droid) builds where Google Play
 * Services Wearable API is unavailable.
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext @Suppress("UNUSED_PARAMETER") context: Context,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun syncWeather(
        data: WeatherData,
        alerts: List<WeatherAlert> = emptyList(),
        airQuality: AirQualityData? = null,
    ) {
        // No-op — Wearable DataLayer API not available without GMS
    }
}
