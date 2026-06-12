package com.sysadmindoc.nimbus

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.ThemeMode
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.ui.component.AdaptiveLayoutInfo
import com.sysadmindoc.nimbus.ui.component.FoldPosture
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.component.LocalIconPackManager
import com.sysadmindoc.nimbus.ui.navigation.DeepLinkRequest
import com.sysadmindoc.nimbus.ui.navigation.NimbusNavHost
import com.sysadmindoc.nimbus.ui.navigation.resolveZeusWatchDeepLinkRoute
import com.sysadmindoc.nimbus.ui.theme.LocalWeatherThemeState
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import com.sysadmindoc.nimbus.ui.theme.WeatherThemeBus
import com.sysadmindoc.nimbus.util.IconPackManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: UserPreferences

    @Inject
    lateinit var iconPackManager: IconPackManager

    private var pendingDeepLink by mutableStateOf<DeepLinkRequest?>(null)
    private var deepLinkCounter = 0L

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyImmersiveMode()

        // Only handle the launch intent on first creation, not on every config
        // change — otherwise rotating the device re-navigates the deep link.
        if (savedInstanceState == null) deliverDeepLink(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val windowLayoutInfo by WindowInfoTracker
                .getOrCreate(this)
                .windowLayoutInfo(this)
                .collectAsStateWithLifecycle(initialValue = WindowLayoutInfo(emptyList()))
            val adaptiveInfo = AdaptiveLayoutInfo.from(
                widthClass = windowSizeClass.widthSizeClass,
                foldPosture = windowLayoutInfo.foldPosture(),
            )
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = NimbusSettings())
            // Hoisted above NimbusTheme: the weather-adaptive scheme reads
            // LocalWeatherThemeState inside the theme, so the provider must be
            // an ancestor of it. MainScreen pushes updates via WeatherThemeBus.
            val weatherThemeState by WeatherThemeBus.state.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalWeatherThemeState provides weatherThemeState) {
                NimbusTheme(
                    useWeatherAdaptive = settings.themeMode == ThemeMode.WEATHER_ADAPTIVE,
                ) {
                    CompositionLocalProvider(
                        LocalAdaptiveLayout provides adaptiveInfo,
                        LocalIconPackManager provides iconPackManager,
                    ) {
                        NimbusNavHost(
                            deepLink = pendingDeepLink,
                            onDeepLinkConsumed = { pendingDeepLink = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverDeepLink(intent)
    }

    private fun deliverDeepLink(intent: Intent?) {
        val route = resolveDeepLink(intent) ?: return
        pendingDeepLink = DeepLinkRequest(route, ++deepLinkCounter)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    private fun resolveDeepLink(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "zeuswatch") return null
        return resolveZeusWatchDeepLinkRoute(
            host = uri.host,
            target = uri.getQueryParameter("target"),
            card = uri.getQueryParameter("card"),
            locationId = uri.getQueryParameter("locationId"),
        )
    }

    private fun WindowLayoutInfo.foldPosture(): FoldPosture {
        val foldingFeature = displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull { it.state == FoldingFeature.State.HALF_OPENED || it.isSeparating }
            ?: return FoldPosture.FLAT

        return when (foldingFeature.orientation) {
            FoldingFeature.Orientation.HORIZONTAL -> {
                if (foldingFeature.state == FoldingFeature.State.HALF_OPENED) {
                    FoldPosture.TABLETOP
                } else {
                    FoldPosture.FLAT
                }
            }
            FoldingFeature.Orientation.VERTICAL -> FoldPosture.BOOK
            else -> FoldPosture.FLAT
        }
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
