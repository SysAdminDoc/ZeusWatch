package com.sysadmindoc.nimbus.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NimbusDarkScheme = darkColorScheme(
    primary = NimbusBlueAccent,
    onPrimary = NimbusTextPrimary,
    primaryContainer = NimbusNavyLight,
    onPrimaryContainer = NimbusTextPrimary,
    secondary = NimbusRainBlue,
    onSecondary = NimbusNavyDark,
    secondaryContainer = NimbusSurfaceElevated,
    onSecondaryContainer = NimbusTextPrimary,
    tertiary = NimbusSunYellow,
    onTertiary = NimbusNavyDark,
    background = NimbusNavyDark,
    onBackground = NimbusTextPrimary,
    surface = NimbusSurface,
    onSurface = NimbusTextPrimary,
    surfaceVariant = NimbusSurfaceVariant,
    onSurfaceVariant = NimbusTextSecondary,
    outline = NimbusCardBorder,
    outlineVariant = Color(0xFF1F2B4A),
    error = NimbusError,
    onError = NimbusTextPrimary,
)

@Composable
fun NimbusTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = NimbusDarkScheme,
        typography = NimbusTypography,
        content = content,
    )
}
