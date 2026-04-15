package com.sysadmindoc.nimbus.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WeatherComplicationService : SuspendingComplicationDataSourceService() {

    @Inject lateinit var repository: WearWeatherRepository
    @Inject lateinit var locationProvider: WearLocationProvider
    @Inject lateinit var syncedStore: SyncedWeatherStore

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("72\u00B0").build(),
                contentDescription = PlainComplicationText.Builder("Temperature").build(),
            ).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("72\u00B0 Partly Cloudy").build(),
                contentDescription = PlainComplicationText.Builder("Weather").build(),
            ).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 5f,
                min = 0f,
                max = 12f,
                contentDescription = PlainComplicationText.Builder("UV Index").build(),
            ).setText(PlainComplicationText.Builder("UV 5").build()).build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Prefer phone-synced data to avoid network calls from the watch
        val data = syncedStore.getFreshData() ?: run {
            val loc = locationProvider.getLocation()
            repository.getCurrentWeather(loc.lat, loc.lon, loc.name).getOrNull()
        } ?: return null

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${data.temperature}\u00B0").build(),
                contentDescription = PlainComplicationText.Builder(
                    "${data.temperature} degrees, ${data.condition}",
                ).build(),
            ).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${data.temperature}\u00B0 ${data.condition}").build(),
                contentDescription = PlainComplicationText.Builder(
                    "${data.temperature} degrees, ${data.condition}",
                ).build(),
            ).setTitle(
                PlainComplicationText.Builder("H:${data.high}\u00B0 L:${data.low}\u00B0").build(),
            ).build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = data.uvIndex.toFloat().coerceIn(0f, 12f),
                min = 0f,
                max = 12f,
                contentDescription = PlainComplicationText.Builder("UV Index ${data.uvIndex}").build(),
            ).setText(
                PlainComplicationText.Builder("UV ${data.uvIndex}").build(),
            ).build()

            else -> null
        }
    }
}
