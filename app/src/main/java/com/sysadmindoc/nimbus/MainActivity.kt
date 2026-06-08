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
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.navigation.DeepLinkRequest
import com.sysadmindoc.nimbus.ui.navigation.NimbusNavHost
import com.sysadmindoc.nimbus.ui.navigation.resolveZeusWatchDeepLinkRoute
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: UserPreferences

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
            val adaptiveInfo = AdaptiveLayoutInfo.from(windowSizeClass.widthSizeClass)
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = NimbusSettings())

            NimbusTheme(
                useWeatherAdaptive = settings.themeMode == ThemeMode.WEATHER_ADAPTIVE,
            ) {
                CompositionLocalProvider(LocalAdaptiveLayout provides adaptiveInfo) {
                    NimbusNavHost(
                        deepLink = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null },
                    )
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
        )
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
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
