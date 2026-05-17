package com.sysadmindoc.nimbus.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.tiles.TileBuilders
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object WeatherTileRequestRunner {

    fun requestTile(
        scope: CoroutineScope,
        loadData: suspend () -> WearWeatherData?,
        buildTile: (WearWeatherData?) -> TileBuilders.Tile,
    ) = CallbackToFutureAdapter.getFuture<TileBuilders.Tile> { completer ->
        scope.launch {
            try {
                completer.set(buildTile(loadData()))
            } catch (cancelled: CancellationException) {
                completer.setException(cancelled)
                throw cancelled
            } catch (e: Exception) {
                runCatching { buildTile(null) }
                    .onSuccess(completer::set)
                    .onFailure(completer::setException)
            }
        }
        "weather-tile"
    }
}
