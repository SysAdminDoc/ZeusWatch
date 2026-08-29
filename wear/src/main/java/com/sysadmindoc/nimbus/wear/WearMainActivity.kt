package com.sysadmindoc.nimbus.wear

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.wear.compose.material3.MaterialTheme
import com.sysadmindoc.nimbus.wear.ui.WearNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {

    private val viewModel: WearWeatherViewModel by viewModels()

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // Reload immediately on grant — the initial load already ran (and
            // resolved to the cached fix or the no-location state) before the
            // user answered the prompt, so without this the new permission
            // does nothing until a manual refresh.
            viewModel.loadWeather()
        } else if (promptedFromCard) {
            // Once the permission is permanently denied the launcher returns
            // instantly with no dialog, so the card's only button would do
            // nothing at all. Send the user somewhere they can actually fix it.
            openAppSettings()
        }
        promptedFromCard = false
    }

    /** Distinguishes the launch-time prompt from a tap on the no-location card. */
    private var promptedFromCard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationIfNeeded()
        setContent {
            MaterialTheme {
                WearNavHost(
                    viewModel = viewModel,
                    onRequestLocation = ::requestLocation,
                )
            }
        }
    }

    private fun requestLocationIfNeeded() {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (granted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Launch-time prompt: a denial here just leaves the card in place.
            // Only a tap on the card escalates to app settings.
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    /**
     * Re-prompt from the no-location card. When the permission is already
     * permanently denied the launcher returns immediately without a dialog,
     * so the result callback opens app settings instead of leaving the
     * button silently inert.
     */
    private fun requestLocation() {
        promptedFromCard = true
        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        // A watch without a settings activity for this intent must not crash.
        runCatching { startActivity(intent) }
    }
}
