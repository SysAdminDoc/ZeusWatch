package com.sysadmindoc.nimbus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sysadmindoc.nimbus.ui.component.AdaptiveLayoutInfo
import com.sysadmindoc.nimbus.ui.component.LocalAdaptiveLayout
import com.sysadmindoc.nimbus.ui.navigation.NimbusNavHost
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        pendingDeepLink = resolveDeepLink(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val adaptiveInfo = AdaptiveLayoutInfo.from(windowSizeClass.widthSizeClass)

            NimbusTheme {
                CompositionLocalProvider(LocalAdaptiveLayout provides adaptiveInfo) {
                    NimbusNavHost(
                        startRoute = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveDeepLink(intent)?.let { pendingDeepLink = it }
    }

    private fun resolveDeepLink(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "zeuswatch") return null
        return when (uri.host) {
            "locations" -> "locations"
            "settings" -> "settings"
            "radar" -> "radar/0.0/0.0" // Uses last-known location via ViewModel fallback
            else -> null
        }
    }
}
