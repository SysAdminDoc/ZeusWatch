package com.sysadmindoc.nimbus.wear

import android.Manifest
import android.os.Bundle
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
        // Reload immediately on grant — the initial load already ran (and
        // fell back to the cached/default location) before the user answered
        // the prompt, so without this the new permission does nothing until
        // a manual refresh.
        if (granted) viewModel.loadWeather()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationIfNeeded()
        setContent {
            MaterialTheme {
                WearNavHost(viewModel = viewModel)
            }
        }
    }

    private fun requestLocationIfNeeded() {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (granted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
