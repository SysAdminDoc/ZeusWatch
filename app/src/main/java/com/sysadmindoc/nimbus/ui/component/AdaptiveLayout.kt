package com.sysadmindoc.nimbus.ui.component

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provides adaptive layout dimensions based on WindowSizeClass.
 * Supports phone portrait, phone landscape, and tablet layouts.
 */
@Stable
data class AdaptiveLayoutInfo(
    val widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    val columns: Int = 1,
    val contentPadding: Dp = 12.dp,
    val cardSpacing: Dp = 12.dp,
    val isCompact: Boolean = true,
    val isMedium: Boolean = false,
    val isExpanded: Boolean = false,
) {
    companion object {
        fun from(widthClass: WindowWidthSizeClass): AdaptiveLayoutInfo = when (widthClass) {
            WindowWidthSizeClass.Compact -> AdaptiveLayoutInfo(
                widthClass = widthClass,
                columns = 1,
                contentPadding = 12.dp,
                cardSpacing = 12.dp,
                isCompact = true,
            )
            WindowWidthSizeClass.Medium -> AdaptiveLayoutInfo(
                widthClass = widthClass,
                columns = 2,
                contentPadding = 24.dp,
                cardSpacing = 16.dp,
                isMedium = true,
                isCompact = false,
            )
            WindowWidthSizeClass.Expanded -> AdaptiveLayoutInfo(
                widthClass = widthClass,
                columns = 2,
                contentPadding = 32.dp,
                cardSpacing = 16.dp,
                isExpanded = true,
                isCompact = false,
            )
            else -> AdaptiveLayoutInfo()
        }
    }
}

/**
 * CompositionLocal providing adaptive layout info throughout the composable tree.
 * Default is compact (phone portrait).
 */
val LocalAdaptiveLayout = compositionLocalOf { AdaptiveLayoutInfo() }
