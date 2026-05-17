package com.sysadmindoc.nimbus.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
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
        return WeatherComplicationDataFactory.previewData(type, weatherSmallImage(), previewCopy())
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Prefer phone-synced data to avoid network calls from the watch
        val data = syncedStore.getFreshData() ?: run {
            val loc = locationProvider.getLocation()
            repository.getCurrentWeather(loc.lat, loc.lon, loc.name).getOrNull()
        } ?: return null

        return WeatherComplicationDataFactory.currentWeatherData(
            type = request.complicationType,
            data = data,
            smallImage = weatherSmallImage(),
            copy = currentCopy(data),
        )
    }

    private fun weatherSmallImage(): SmallImage = SmallImage.Builder(
        Icon.createWithResource(this, R.drawable.ic_complication_weather),
        SmallImageType.ICON,
    ).build()

    private fun previewCopy(): WeatherComplicationCopy = WeatherComplicationCopy(
        shortText = getString(R.string.wear_complication_preview_temperature),
        shortContentDescription = getString(R.string.wear_complication_temperature_label),
        longText = getString(R.string.wear_complication_preview_long_text),
        longContentDescription = getString(R.string.complication_label),
        longTitle = getString(R.string.wear_tile_high_low_abbrev, 76, 64),
        rangedText = getString(R.string.wear_complication_uv_short, 5),
        rangedContentDescription = getString(R.string.wear_complication_uv_content_description, 5),
        smallImageContentDescription = getString(R.string.wear_complication_preview_small_image_cd),
    )

    private fun currentCopy(data: WearWeatherData): WeatherComplicationCopy =
        WeatherComplicationCopy(
            shortText = getString(R.string.wear_complication_temperature_value, data.temperature),
            shortContentDescription = getString(
                R.string.wear_complication_temperature_content_description,
                data.temperature,
                data.condition,
            ),
            longText = getString(R.string.wear_complication_long_text, data.temperature, data.condition),
            longContentDescription = getString(
                R.string.wear_complication_temperature_content_description,
                data.temperature,
                data.condition,
            ),
            longTitle = getString(R.string.wear_tile_high_low_abbrev, data.high, data.low),
            rangedText = getString(R.string.wear_complication_uv_short, data.uvIndex),
            rangedContentDescription = getString(
                R.string.wear_complication_uv_content_description,
                data.uvIndex,
            ),
            smallImageContentDescription = getString(
                R.string.wear_complication_small_image_content_description,
                data.condition,
                data.temperature,
            ),
        )
}
