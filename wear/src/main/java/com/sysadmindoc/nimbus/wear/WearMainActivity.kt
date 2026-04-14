package com.sysadmindoc.nimbus.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.wear.compose.material3.MaterialTheme
import com.sysadmindoc.nimbus.wear.ui.WearNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ViewModel re-fetches on resume anyway */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationIfNeeded()
        setContent {
            MaterialTheme {
                WearNavHost()
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
