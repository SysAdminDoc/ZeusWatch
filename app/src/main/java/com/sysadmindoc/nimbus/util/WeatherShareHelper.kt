package com.sysadmindoc.nimbus.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a weather summary card as a Bitmap image and shares it via FileProvider.
 * Produces a dark-themed card matching the app's visual style.
 */
object WeatherShareHelper {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 720
    private const val CORNER_RADIUS = 32f

    // Theme colors (matching NimbusNavy palette)
    private const val BG_TOP = 0xFF0D1526.toInt()
    private const val BG_BOTTOM = 0xFF1B2845.toInt()
    private const val CARD_BG = 0x33FFFFFF
    private const val TEXT_PRIMARY = 0xFFF0F0F5.toInt()
    private const val TEXT_SECONDARY = 0xFFB0B8CC.toInt()
    private const val TEXT_TERTIARY = 0xFF7A839E.toInt()
    private const val BLUE_ACCENT = 0xFF3D6CB9.toInt()

    /**
     * Render weather data onto a Bitmap.
     */
    fun renderWeatherCard(data: WeatherData, s: NimbusSettings = NimbusSettings()): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()),
            CORNER_RADIUS, CORNER_RADIUS, bgPaint,
        )

        // Card overlay
        val cardPaint = Paint().apply {
            color = CARD_BG
            isAntiAlias = true
        }
        val cardRect = RectF(40f, 40f, CARD_WIDTH - 40f, CARD_HEIGHT - 40f)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

        val current = data.current
        val location = data.location

        // Location name
        val locationPaint = textPaint(TEXT_SECONDARY, 36f)
        canvas.drawText(location.name, 80f, 110f, locationPaint)

        // Region/country
        if (location.region.isNotBlank()) {
            val regionPaint = textPaint(TEXT_TERTIARY, 24f)
            canvas.drawText(
                "${location.region}, ${location.country}",
                80f, 146f, regionPaint,
            )
        }

        // Large temperature
        val tempPaint = textPaint(TEXT_PRIMARY, 120f, bold = true)
        val tempText = WeatherFormatter.formatTemperature(current.temperature, s)
        canvas.drawText(tempText, 80f, 300f, tempPaint)

        // Condition text
        val condPaint = textPaint(TEXT_SECONDARY, 36f)
        canvas.drawText(current.weatherCode.description, 80f, 350f, condPaint)

        // Feels like
        val feelsPaint = textPaint(TEXT_TERTIARY, 28f)
        canvas.drawText("Feels like ${WeatherFormatter.formatTemperature(current.feelsLike, s)}", 80f, 394f, feelsPaint)

        // High/Low
        val hlPaint = textPaint(TEXT_SECONDARY, 28f)
        canvas.drawText(
            "H: ${WeatherFormatter.formatTemperature(current.dailyHigh, s)}  L: ${WeatherFormatter.formatTemperature(current.dailyLow, s)}",
            80f, 434f, hlPaint,
        )

        // Details column (right side)
        val detailX = 580f
        var detailY = 130f
        val detailLabelPaint = textPaint(TEXT_TERTIARY, 24f)
        val detailValuePaint = textPaint(TEXT_PRIMARY, 28f)

        val details = listOf(
            "Wind" to WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s),
            "Humidity" to "${current.humidity}%",
            "UV Index" to "${current.uvIndex.toInt()} ${WeatherFormatter.uvDescription(current.uvIndex)}",
            "Pressure" to WeatherFormatter.formatPressure(current.pressure, s),
            "Cloud Cover" to "${current.cloudCover}%",
        )

        details.forEach { (label, value) ->
            canvas.drawText(label, detailX, detailY, detailLabelPaint)
            detailY += 32f
            canvas.drawText(value, detailX, detailY, detailValuePaint)
            detailY += 50f
        }

        // 3-day mini forecast at bottom
        val forecastY = 510f
        val dayWidth = (CARD_WIDTH - 160f) / 3f
        data.daily.take(3).forEachIndexed { i, day ->
            val x = 80f + i * dayWidth
            val dayLabel = WeatherFormatter.formatDayLabel(day.date)
            val hi = WeatherFormatter.formatTemperature(day.temperatureHigh, s)
            val lo = WeatherFormatter.formatTemperature(day.temperatureLow, s)

            canvas.drawText(dayLabel, x, forecastY, textPaint(TEXT_SECONDARY, 24f))
            canvas.drawText(day.weatherCode.description, x, forecastY + 30f, textPaint(TEXT_TERTIARY, 20f))
            canvas.drawText("$hi / $lo", x, forecastY + 62f, textPaint(TEXT_PRIMARY, 26f))
        }

        // Divider line above forecast
        val divPaint = Paint().apply { color = 0x33FFFFFF; strokeWidth = 1f }
        canvas.drawLine(80f, forecastY - 30f, CARD_WIDTH - 80f, forecastY - 30f, divPaint)

        // Footer branding
        val footerPaint = textPaint(TEXT_TERTIARY, 20f)
        canvas.drawText("ZeusWatch", 80f, CARD_HEIGHT - 70f, footerPaint)

        // Accent line at top
        val accentPaint = Paint().apply { color = BLUE_ACCENT; strokeWidth = 4f; isAntiAlias = true }
        canvas.drawRoundRect(RectF(40f, 40f, CARD_WIDTH - 40f, 44f), 2f, 2f, accentPaint)

        return bitmap
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint {
        return Paint().apply {
            this.color = color
            this.textSize = size
            this.isAntiAlias = true
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /**
     * Save bitmap to cache and return a shareable content:// URI.
     */
    fun shareAsImage(context: Context, data: WeatherData, s: NimbusSettings = NimbusSettings()) {
        val bitmap = renderWeatherCard(data, s)

        // Write to cache directory
        val cacheDir = File(context.cacheDir, "shared_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "nimbus_weather.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Weather for ${data.location.name} - ZeusWatch")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share weather image").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
