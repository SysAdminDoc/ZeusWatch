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
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import java.io.File
import java.io.FileOutputStream

/**
 * Unified sharing helper for weather data.
 * Supports text and image formats.
 */
object ShareWeatherHelper {

    // ── Text Sharing ──────────────────────────────────────────────────

    fun buildShareText(
        data: WeatherData,
        airQuality: AirQualityData? = null,
        s: NimbusSettings = NimbusSettings(),
    ): String = buildString {
        val location = data.location
        val current = data.current
        val referenceDate = current.observationTime?.toLocalDate() ?: data.daily.firstOrNull()?.date

        appendLine("--- ZeusWatch ---")
        appendLine("${location.name}${if (location.region.isNotBlank()) ", ${location.region}" else ""}")
        appendLine()

        appendLine("Now: ${WeatherFormatter.formatTemperatureUnit(current.temperature, s)} ${current.weatherCode.description}")
        appendLine("Feels like ${WeatherFormatter.formatTemperature(current.feelsLike, s)}")
        appendLine("High ${WeatherFormatter.formatTemperature(current.dailyHigh, s)} / Low ${WeatherFormatter.formatTemperature(current.dailyLow, s)}")
        appendLine()

        appendLine("Wind: ${WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s)}")
        appendLine("Humidity: ${current.humidity}%")
        appendLine("UV Index: ${current.uvIndex.toInt()} (${WeatherFormatter.uvDescription(current.uvIndex)})")
        appendLine("Pressure: ${WeatherFormatter.formatPressure(current.pressure, s)}")
        current.visibility?.let { appendLine("Visibility: ${WeatherFormatter.formatVisibility(it, s)}") }
        appendLine()

        airQuality?.let { aq ->
            appendLine("Air Quality: ${aq.usAqi} ${aq.aqiLevel.label}")
        }

        if (data.daily.isNotEmpty()) {
            appendLine("--- Forecast ---")
            data.daily.take(3).forEach { day ->
                val label = WeatherFormatter.formatRelativeDayLabel(day.date, referenceDate)
                val desc = day.weatherCode.description
                val hi = WeatherFormatter.formatTemperature(day.temperatureHigh, s)
                val lo = WeatherFormatter.formatTemperature(day.temperatureLow, s)
                val precip = day.precipitationProbability
                append("$label: $desc $hi/$lo")
                if (precip > 0) append(" ($precip% precip)")
                appendLine()
            }
        }

        appendLine()
        append("Shared via ZeusWatch")
    }.trimEnd()

    fun share(context: Context, data: WeatherData, airQuality: AirQualityData? = null, s: NimbusSettings = NimbusSettings()) {
        val text = buildShareText(data, airQuality, s)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Weather for ${data.location.name}")
        }
        context.startActivity(
            Intent.createChooser(intent, "Share weather").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ── Image Sharing ─────────────────────────────────────────────────

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 720
    private const val CORNER_RADIUS = 32f

    private const val BG_TOP = 0xFF0D1526.toInt()
    private const val BG_BOTTOM = 0xFF1B2845.toInt()
    private const val CARD_BG = 0x33FFFFFF
    private const val TEXT_PRIMARY = 0xFFF0F0F5.toInt()
    private const val TEXT_SECONDARY = 0xFFB0B8CC.toInt()
    private const val TEXT_TERTIARY = 0xFF7A839E.toInt()
    private const val BLUE_ACCENT = 0xFF3D6CB9.toInt()

    fun renderWeatherCard(data: WeatherData, s: NimbusSettings = NimbusSettings()): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

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

        val cardPaint = Paint().apply { color = CARD_BG; isAntiAlias = true }
        val cardRect = RectF(40f, 40f, CARD_WIDTH - 40f, CARD_HEIGHT - 40f)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

        val current = data.current
        val location = data.location

        canvas.drawText(location.name, 80f, 110f, textPaint(TEXT_SECONDARY, 36f))

        if (location.region.isNotBlank()) {
            canvas.drawText("${location.region}, ${location.country}", 80f, 146f, textPaint(TEXT_TERTIARY, 24f))
        }

        canvas.drawText(WeatherFormatter.formatTemperature(current.temperature, s), 80f, 300f, textPaint(TEXT_PRIMARY, 120f, bold = true))
        canvas.drawText(current.weatherCode.description, 80f, 350f, textPaint(TEXT_SECONDARY, 36f))
        canvas.drawText("Feels like ${WeatherFormatter.formatTemperature(current.feelsLike, s)}", 80f, 394f, textPaint(TEXT_TERTIARY, 28f))
        canvas.drawText(
            "H: ${WeatherFormatter.formatTemperature(current.dailyHigh, s)}  L: ${WeatherFormatter.formatTemperature(current.dailyLow, s)}",
            80f, 434f, textPaint(TEXT_SECONDARY, 28f),
        )

        // Details column (right side)
        val detailX = 580f
        var detailY = 130f
        val details = listOf(
            "Wind" to WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, s),
            "Humidity" to "${current.humidity}%",
            "UV Index" to "${current.uvIndex.toInt()} ${WeatherFormatter.uvDescription(current.uvIndex)}",
            "Pressure" to WeatherFormatter.formatPressure(current.pressure, s),
            "Cloud Cover" to "${current.cloudCover}%",
        )
        details.forEach { (label, value) ->
            canvas.drawText(label, detailX, detailY, textPaint(TEXT_TERTIARY, 24f))
            detailY += 32f
            canvas.drawText(value, detailX, detailY, textPaint(TEXT_PRIMARY, 28f))
            detailY += 50f
        }

        // 3-day mini forecast
        val forecastY = 510f
        val dayWidth = (CARD_WIDTH - 160f) / 3f
        val referenceDate = current.observationTime?.toLocalDate() ?: data.daily.firstOrNull()?.date
        data.daily.take(3).forEachIndexed { i, day ->
            val x = 80f + i * dayWidth
            canvas.drawText(
                WeatherFormatter.formatRelativeDayLabel(day.date, referenceDate),
                x,
                forecastY,
                textPaint(TEXT_SECONDARY, 24f),
            )
            canvas.drawText(day.weatherCode.description, x, forecastY + 30f, textPaint(TEXT_TERTIARY, 20f))
            canvas.drawText(
                "${WeatherFormatter.formatTemperature(day.temperatureHigh, s)} / ${WeatherFormatter.formatTemperature(day.temperatureLow, s)}",
                x, forecastY + 62f, textPaint(TEXT_PRIMARY, 26f),
            )
        }

        val divPaint = Paint().apply { color = 0x33FFFFFF; strokeWidth = 1f }
        canvas.drawLine(80f, forecastY - 30f, CARD_WIDTH - 80f, forecastY - 30f, divPaint)
        canvas.drawText("ZeusWatch", 80f, CARD_HEIGHT - 70f, textPaint(TEXT_TERTIARY, 20f))

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

    fun shareAsImage(context: Context, data: WeatherData, s: NimbusSettings = NimbusSettings()) {
        val bitmap = renderWeatherCard(data, s)
        try {
            val cacheDir = File(context.cacheDir, "shared_images")
            if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory) {
                android.util.Log.w("ShareWeatherHelper", "Could not create shared_images cache dir")
                return
            }
            val file = File(cacheDir, "nimbus_weather.png")
            val compressed = FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!compressed || !file.exists() || file.length() == 0L) {
                android.util.Log.w("ShareWeatherHelper", "PNG compression failed or produced empty file")
                return
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

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
        } catch (e: Exception) {
            // Don't crash the share action on disk-full / FileProvider misconfig / etc.
            android.util.Log.w("ShareWeatherHelper", "Failed to share as image", e)
        } finally {
            bitmap.recycle() // Free the ~3 MB bitmap immediately
        }
    }
}
