package com.sysadmindoc.nimbus.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.WearMainActivity
import com.sysadmindoc.nimbus.wear.data.DataSource
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
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
        val syncedData = syncedStore.getFreshData()
        val fallbackData = if (syncedData == null) {
            val loc = locationProvider.getLocation()
            repository.getCurrentWeather(loc.lat, loc.lon, loc.name).getOrNull()
        } else {
            null
        }
        val data = WeatherComplicationPayloadSelector.select(
            syncedData = syncedData,
            syncedAtMs = syncedStore.lastSyncTimestamp(),
            fallbackData = fallbackData,
        ) ?: return null

        return WeatherComplicationDataFactory.currentWeatherData(
            type = request.complicationType,
            data = data,
            smallImage = weatherSmallImage(),
            copy = currentCopy(data),
            tapAction = openAppTapAction(),
        )
    }

    private fun weatherSmallImage(): SmallImage = SmallImage.Builder(
        Icon.createWithResource(this, R.drawable.ic_complication_weather),
        SmallImageType.ICON,
    ).build()

    /** Tapping any complication opens the full app. */
    private fun openAppTapAction(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WearMainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

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

    private fun currentCopy(data: WearWeatherData): WeatherComplicationCopy {
        // Convert canonical metric values to the user's display units.
        val temp = WearUnitFormatter.displayTemp(data.temperature, data.tempUnit)
        val high = WearUnitFormatter.displayTemp(data.high, data.tempUnit)
        val low = WearUnitFormatter.displayTemp(data.low, data.tempUnit)
        val updated = data.updatedLabel()
        return WeatherComplicationCopy(
            shortText = getString(R.string.wear_complication_temperature_value, temp),
            shortContentDescription = getString(
                R.string.wear_complication_temperature_content_description,
                temp,
                data.condition,
            ),
            longText = getString(
                R.string.wear_complication_long_text_details,
                temp,
                data.condition,
                data.precipChance,
                updated,
            ),
            longContentDescription = getString(
                R.string.wear_complication_long_content_description,
                temp,
                data.condition,
                high,
                low,
                data.precipChance,
                updated,
            ),
            longTitle = getString(R.string.wear_tile_high_low_abbrev, high, low),
            rangedText = getString(R.string.wear_complication_uv_short, data.uvIndex),
            rangedContentDescription = getString(
                R.string.wear_complication_uv_content_description,
                data.uvIndex,
            ),
            smallImageContentDescription = getString(
                R.string.wear_complication_small_image_content_description,
                data.condition,
                temp,
            ),
        )
    }

    private fun WearWeatherData.updatedLabel(): String {
        val sourceTime = when (dataSource) {
            DataSource.PHONE_SYNC,
            DataSource.DIRECT_API -> syncedAtMs
            DataSource.UNKNOWN -> 0L
        }
        val ageMin = WeatherComplicationFreshness.ageMinutes(System.currentTimeMillis(), sourceTime)
        return if (ageMin < 60) {
            getString(R.string.wear_tile_updated_min, ageMin)
        } else {
            getString(R.string.wear_tile_updated_hr, ageMin / 60)
        }
    }
}
