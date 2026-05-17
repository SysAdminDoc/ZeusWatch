package com.sysadmindoc.nimbus.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherComplicationDataFactoryTest {

    private val weather = WearWeatherData(
        temperature = 68,
        condition = "Rain",
        high = 74,
        low = 59,
        locationName = "Seattle",
        uvIndex = 14,
        weatherCode = 61,
    )
    private val copy = WeatherComplicationCopy(
        shortText = "68\u00B0",
        shortContentDescription = "68 degrees, Rain",
        longText = "68\u00B0 Rain",
        longContentDescription = "68 degrees, Rain",
        longTitle = "H:74\u00B0 L:59\u00B0",
        rangedText = "UV 14",
        rangedContentDescription = "UV Index 14",
        smallImageContentDescription = "Rain, 68 degrees",
    )
    private val previewCopy = WeatherComplicationCopy(
        shortText = "72\u00B0",
        shortContentDescription = "Temperature",
        longText = "72\u00B0 Partly Cloudy",
        longContentDescription = "Weather",
        longTitle = "H:76\u00B0 L:64\u00B0",
        rangedText = "UV 5",
        rangedContentDescription = "UV Index 5",
        smallImageContentDescription = "Partly cloudy weather",
    )
    private val smallImage by lazy {
        SmallImage.Builder(
            Icon.createWithResource("com.sysadmindoc.nimbus.wear", R.drawable.ic_complication_weather),
            SmallImageType.ICON,
        ).build()
    }

    @Test
    fun `preview data supports every declared complication type`() {
        assertTrue(preview(ComplicationType.SHORT_TEXT) is ShortTextComplicationData)
        assertTrue(preview(ComplicationType.LONG_TEXT) is LongTextComplicationData)
        assertTrue(preview(ComplicationType.RANGED_VALUE) is RangedValueComplicationData)
        assertTrue(preview(ComplicationType.SMALL_IMAGE) is SmallImageComplicationData)
    }

    @Test
    fun `current weather supports every declared complication type`() {
        assertTrue(current(ComplicationType.SHORT_TEXT) is ShortTextComplicationData)
        assertTrue(current(ComplicationType.LONG_TEXT) is LongTextComplicationData)
        assertTrue(current(ComplicationType.RANGED_VALUE) is RangedValueComplicationData)
        assertTrue(current(ComplicationType.SMALL_IMAGE) is SmallImageComplicationData)
    }

    @Test
    fun `long text includes high low title`() {
        val data = current(ComplicationType.LONG_TEXT) as LongTextComplicationData
        val title = data.title!!.getTextAt(RuntimeEnvironment.getApplication().resources, Instant.EPOCH).toString()

        assertNotNull(data.text)
        assertNotNull(data.title)
        assertTrue(title.contains("H:74"))
        assertTrue(title.contains("L:59"))
    }

    @Test
    fun `ranged value clamps UV index to supported scale`() {
        val data = current(ComplicationType.RANGED_VALUE) as RangedValueComplicationData

        assertEquals(12f, data.value, 0.01f)
        assertEquals(0f, data.min, 0.01f)
        assertEquals(12f, data.max, 0.01f)
    }

    @Test
    fun `small image carries supplied image object`() {
        val data = current(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData

        assertSame(smallImage, data.smallImage)
    }

    private fun preview(type: ComplicationType) =
        WeatherComplicationDataFactory.previewData(type, smallImage, previewCopy)

    private fun current(type: ComplicationType) =
        WeatherComplicationDataFactory.currentWeatherData(type, weather, smallImage, copy)
}
