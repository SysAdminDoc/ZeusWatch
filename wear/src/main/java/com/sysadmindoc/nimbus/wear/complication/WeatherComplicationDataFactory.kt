package com.sysadmindoc.nimbus.wear.complication

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import com.sysadmindoc.nimbus.wear.data.DataSource
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import kotlin.math.max

object WeatherComplicationDataFactory {

    fun previewData(
        type: ComplicationType,
        smallImage: SmallImage,
        copy: WeatherComplicationCopy,
    ): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = text(copy.shortText),
            contentDescription = text(copy.shortContentDescription),
        ).build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = text(copy.longText),
            contentDescription = text(copy.longContentDescription),
        ).setTitle(text(copy.longTitle)).build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = 5f,
            min = 0f,
            max = 12f,
            contentDescription = text(copy.rangedContentDescription),
        ).setText(text(copy.rangedText)).build()
        ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
            smallImage = smallImage,
            contentDescription = text(copy.smallImageContentDescription),
        ).build()
        else -> null
    }

    fun currentWeatherData(
        type: ComplicationType,
        data: WearWeatherData,
        smallImage: SmallImage,
        copy: WeatherComplicationCopy,
        tapAction: PendingIntent? = null,
    ): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = text(copy.shortText),
            contentDescription = text(copy.shortContentDescription),
        ).setTapAction(tapAction).build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = text(copy.longText),
            contentDescription = text(copy.longContentDescription),
        ).setTitle(text(copy.longTitle)).setTapAction(tapAction).build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = data.uvIndex.toFloat().coerceIn(0f, 12f),
            min = 0f,
            max = 12f,
            contentDescription = text(copy.rangedContentDescription),
        ).setText(text(copy.rangedText)).setTapAction(tapAction).build()
        ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
            smallImage = smallImage,
            contentDescription = text(copy.smallImageContentDescription),
        ).setTapAction(tapAction).build()
        else -> null
    }

    private fun text(value: String): PlainComplicationText =
        PlainComplicationText.Builder(value).build()
}

data class WeatherComplicationCopy(
    val shortText: String,
    val shortContentDescription: String,
    val longText: String,
    val longContentDescription: String,
    val longTitle: String,
    val rangedText: String,
    val rangedContentDescription: String,
    val smallImageContentDescription: String,
)

object WeatherComplicationPayloadSelector {
    fun select(
        syncedData: WearWeatherData?,
        syncedAtMs: Long,
        fallbackData: WearWeatherData?,
    ): WearWeatherData? = syncedData
        ?.copy(dataSource = DataSource.PHONE_SYNC, syncedAtMs = syncedAtMs)
        ?: fallbackData
}

object WeatherComplicationFreshness {
    fun ageMinutes(nowMs: Long, updatedAtMs: Long): Long {
        if (updatedAtMs <= 0L) return 0L
        return max(0L, nowMs - updatedAtMs) / 60_000L
    }
}
