package com.sysadmindoc.nimbus.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat

/**
 * Renders a short temperature string (e.g. "72°") into a status-bar small
 * icon. The status bar draws small icons in a fixed monochrome slot, so we paint
 * opaque white glyphs on a transparent canvas and let the system present them.
 * Text auto-shrinks so wider values ("-13°") still fit the icon box.
 */
object StatusBarTempIcon {

    private const val ICON_SIZE_PX = 96
    private const val TEXT_WIDTH_FRACTION = 0.94f

    fun build(text: String): IconCompat {
        val size = ICON_SIZE_PX
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size.toFloat()
        }
        // Shrink to fit the icon width with a small margin so 3-glyph values fit.
        val maxWidth = size * TEXT_WIDTH_FRACTION
        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 0f) {
            paint.textSize = paint.textSize * (maxWidth / measured)
        }
        val fm = paint.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, size / 2f, baseline, paint)
        return IconCompat.createWithBitmap(bitmap)
    }
}
