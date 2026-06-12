package com.sysadmindoc.nimbus.ui.component

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fold posture signals that influence top-level layout decisions.
 */
enum class FoldPosture {
    FLAT,
    BOOK,
    TABLETOP,
}

/**
 * Provides adaptive layout dimensions based on WindowSizeClass and fold posture.
 * Supports phone, split-screen, tablet, book-mode foldables, and tabletop foldables.
 */
@Stable
data class AdaptiveLayoutInfo(
    val widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    val foldPosture: FoldPosture = FoldPosture.FLAT,
    val columns: Int = 1,
    val contentPadding: Dp = 12.dp,
    val cardSpacing: Dp = 12.dp,
    val isCompact: Boolean = true,
    val isMedium: Boolean = false,
    val isExpanded: Boolean = false,
) {
    val isBookMode: Boolean
        get() = foldPosture == FoldPosture.BOOK

    val isTabletop: Boolean
        get() = foldPosture == FoldPosture.TABLETOP

    val supportsTwoPaneWeather: Boolean
        get() = isExpanded || isBookMode

    companion object {
        fun from(
            widthClass: WindowWidthSizeClass,
            foldPosture: FoldPosture = FoldPosture.FLAT,
        ): AdaptiveLayoutInfo = when (widthClass) {
            WindowWidthSizeClass.Compact -> AdaptiveLayoutInfo(
                widthClass = widthClass,
                foldPosture = foldPosture,
                columns = 1,
                contentPadding = 16.dp,
                cardSpacing = 14.dp,
                isCompact = true,
            )
            WindowWidthSizeClass.Medium -> AdaptiveLayoutInfo(
                widthClass = widthClass,
                foldPosture = foldPosture,
                columns = 2,
                contentPadding = 28.dp,
                cardSpacing = 18.dp,
                isMedium = true,
                isCompact = false,
            )
            WindowWidthSizeClass.Expanded -> AdaptiveLayoutInfo(
                widthClass = widthClass,
                foldPosture = foldPosture,
                columns = 2,
                contentPadding = 36.dp,
                cardSpacing = 18.dp,
                isExpanded = true,
                isCompact = false,
            )
            else -> AdaptiveLayoutInfo(foldPosture = foldPosture)
        }
    }
}

/**
 * CompositionLocal providing adaptive layout info throughout the composable tree.
 * Default is compact (phone portrait).
 */
val LocalAdaptiveLayout = compositionLocalOf { AdaptiveLayoutInfo() }
