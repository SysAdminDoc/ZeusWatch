package com.sysadmindoc.nimbus.ui.component

internal fun rtlCanvasX(x: Float, width: Float, isRtl: Boolean): Float {
    val safeWidth = width.coerceAtLeast(0f)
    val clamped = x.coerceIn(0f, safeWidth)
    return if (isRtl) safeWidth - clamped else clamped
}

internal fun rtlCanvasRectLeft(
    left: Float,
    rectWidth: Float,
    canvasWidth: Float,
    isRtl: Boolean,
): Float {
    if (!isRtl) return left
    return canvasWidth - left - rectWidth
}

internal fun centeredCanvasLabelLeft(
    centerX: Float,
    labelWidth: Float,
    canvasWidth: Float,
): Float {
    val maxLeft = (canvasWidth - labelWidth).coerceAtLeast(0f)
    return (centerX - labelWidth / 2f).coerceIn(0f, maxLeft)
}

internal fun logicalCanvasX(displayX: Float, width: Float, isRtl: Boolean): Float =
    rtlCanvasX(displayX, width, isRtl)
