package com.sysadmindoc.nimbus.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearWeatherScreen()
            }
        }
    }
}

@Composable
fun WearWeatherScreen(
    viewModel: WearWeatherViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.isLoading) {
            Text("Loading...", fontSize = 14.sp)
        } else if (state.error != null) {
            Text(state.error!!, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            // Temperature (large)
            Text(
                text = "${state.temperature}\u00B0",
                fontSize = 48.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Condition
            Text(
                text = state.condition,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))

            // High / Low
            Text(
                text = "H:${state.high}\u00B0 L:${state.low}\u00B0",
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))

            // Location
            Text(
                text = state.locationName,
                fontSize = 11.sp,
            )
        }
    }
}
