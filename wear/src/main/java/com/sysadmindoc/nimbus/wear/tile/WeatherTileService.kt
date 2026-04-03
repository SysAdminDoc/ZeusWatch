package com.sysadmindoc.nimbus.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Wear OS Tile showing current weather at a glance.
 * Displays temperature, condition, and high/low.
 * Users see this by swiping from the watch face.
 */
@AndroidEntryPoint
class WeatherTileService : androidx.wear.tiles.TileService() {

    @Inject
    lateinit var repository: WearWeatherRepository

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        CallbackToFutureAdapter.getFuture { completer ->
            val tile = runBlocking {
            val data = repository.getCurrentWeather().getOrNull()

            val layout = Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder()
                        .setText(if (data != null) "${data.temperature}\u00B0" else "--")
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(40f))
                                .setColor(argb(0xFFF0F0F5.toInt()))
                                .build()
                        )
                        .build()
                )
                .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                .addContent(
                    Text.Builder()
                        .setText(data?.condition ?: "Loading...")
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(14f))
                                .setColor(argb(0xFFB0B8CC.toInt()))
                                .build()
                        )
                        .build()
                )
                .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                .addContent(
                    Text.Builder()
                        .setText(
                            if (data != null) "H:${data.high}\u00B0 L:${data.low}\u00B0"
                            else ""
                        )
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(12f))
                                .setColor(argb(0xFF7A839E.toInt()))
                                .build()
                        )
                        .build()
                )
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(
                            LayoutElementBuilders.Layout.Builder()
                                .setRoot(layout)
                                .build()
                        )
                        .build()
                )
                .build()

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(30 * 60 * 1000L) // 30 min refresh
                .build()
            }
            completer.set(tile)
            "weather-tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion("1")
                    .build()
            )
            "weather-tile-resources"
        }
}
