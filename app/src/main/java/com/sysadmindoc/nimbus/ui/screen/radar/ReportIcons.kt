package com.sysadmindoc.nimbus.ui.screen.radar

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.sysadmindoc.nimbus.data.model.ReportCondition
import org.maplibre.android.maps.Style
import java.util.Locale
import kotlin.math.ceil

/**
 * Community report markers are images, not text labels. MapLibre draws text
 * from SDF glyph files, which never carry colour emoji, and OpenFreeMap and
 * VersaTiles don't serve the default font stack an unstyled label asks for,
 * so an emoji label drew nothing on them.
 */
internal fun reportIconId(condition: ReportCondition): String =
    "report-icon-" + condition.name.lowercase(Locale.ROOT)

/** Adds one icon per [ReportCondition] to the style, once per style. */
internal fun Style.ensureReportIcons(density: Float = Resources.getSystem().displayMetrics.density) {
    ReportCondition.entries.forEach { condition ->
        val id = reportIconId(condition)
        if (getImage(id) == null) addImage(id, renderEmoji(condition.emoji, density))
    }
}

/**
 * Draws [emoji] with the system's colour emoji font into a square bitmap
 * sized to the glyph it actually drew, so a wider emoji font (Samsung's)
 * can't clip at the edges. The bitmap keeps the display density, so MapLibre
 * shows it at about [REPORT_ICON_DP] whatever the screen.
 */
private fun renderEmoji(emoji: String, density: Float): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = REPORT_ICON_DP * density }
    val bounds = Rect().also { paint.getTextBounds(emoji, 0, emoji.length, it) }
    val glyph = maxOf(bounds.width(), bounds.height(), ceil(paint.measureText(emoji)).toInt())
    val size = (glyph + 2 * ceil(density).toInt()).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    // Centre the drawn glyph itself, not the font's ascent and descent.
    Canvas(bitmap).drawText(emoji, size / 2f - bounds.exactCenterX(), size / 2f - bounds.exactCenterY(), paint)
    return bitmap
}

/** Fits inside the 24 dp report circle drawn under it. */
private const val REPORT_ICON_DP = 15f
