package com.sysadmindoc.nimbus.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sysadmindoc.nimbus.MainActivity
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.conditionDescription
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WeatherQuickSettingsTile : TileService() {

    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var userPreferences: UserPreferences

    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        updateJob = scope?.launch { refreshTile() }
    }

    override fun onStopListening() {
        updateJob?.cancel()
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapseCompat(intent)
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val location = userPreferences.lastLocation.first()
        if (location == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_label)
            tile.setSubtitleCompat(getString(R.string.qs_tile_no_location))
            tile.icon = Icon.createWithResource(this, R.drawable.ic_w_sunny)
            tile.updateTile()
            return
        }

        val settings = userPreferences.settings.first()
        val weather = weatherRepository.getCachedWeather(location.latitude, location.longitude)

        if (weather == null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = location.name
            tile.setSubtitleCompat(getString(R.string.qs_tile_no_data))
            tile.icon = Icon.createWithResource(this, R.drawable.ic_w_sunny)
            tile.updateTile()
            return
        }

        applyWeatherToTile(tile, weather, settings)
        tile.updateTile()
    }

    private fun applyWeatherToTile(tile: Tile, data: WeatherData, s: NimbusSettings) {
        val temp = WeatherFormatter.formatTemperature(data.current.temperature, s)
        val condition = data.current.conditionDescription(this)

        tile.state = Tile.STATE_ACTIVE
        tile.label = "$temp $condition"
        tile.setSubtitleCompat(data.location.name)
        tile.icon = Icon.createWithResource(this, tileIcon(data.current.weatherCode, data.current.isDay))
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun Tile.setSubtitleCompat(value: CharSequence) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            subtitle = value
        }
    }

    private fun tileIcon(code: WeatherCode, isDay: Boolean): Int = when {
        code.isStormy -> R.drawable.ic_w_thunderstorm
        code.isSnowy -> R.drawable.ic_w_snow
        code.isRainy -> R.drawable.ic_w_rain
        code.isFoggy -> R.drawable.ic_w_fog
        code == WeatherCode.OVERCAST -> R.drawable.ic_w_cloudy
        code == WeatherCode.PARTLY_CLOUDY -> if (isDay) R.drawable.ic_w_partly_cloudy else R.drawable.ic_w_cloudy
        code == WeatherCode.MAINLY_CLEAR -> if (isDay) R.drawable.ic_w_sunny else R.drawable.ic_w_night
        code == WeatherCode.CLEAR_SKY -> if (isDay) R.drawable.ic_w_sunny else R.drawable.ic_w_night
        !isDay -> R.drawable.ic_w_night
        else -> R.drawable.ic_w_sunny
    }
}
