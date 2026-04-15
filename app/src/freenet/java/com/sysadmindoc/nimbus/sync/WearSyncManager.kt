package com.sysadmindoc.nimbus.sync

import android.content.Context
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
    suspend fun syncWeather(data: WeatherData) {
        // No-op — Wearable DataLayer API not available without GMS
    }
}
