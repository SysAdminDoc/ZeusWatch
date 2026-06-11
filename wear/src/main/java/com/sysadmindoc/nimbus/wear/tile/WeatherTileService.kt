package com.sysadmindoc.nimbus.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.WearMainActivity
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class WeatherTileService : androidx.wear.tiles.TileService() {

    @Inject lateinit var repository: WearWeatherRepository
    @Inject lateinit var locationProvider: WearLocationProvider
    @Inject lateinit var syncedStore: SyncedWeatherStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        WeatherTileRequestRunner.requestTile(
            scope = serviceScope,
            loadData = ::loadTileData,
            buildTile = ::buildTile,
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion("2")
                    .build(),
            )
            "weather-tile-resources"
        }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    internal suspend fun loadTileData(): WearWeatherData? {
        // Prefer phone-synced data to avoid network calls from the watch.
        val synced = syncedStore.getFreshData()
        return synced ?: run {
            val loc = locationProvider.getLocation()
            repository.getCurrentWeather(loc.lat, loc.lon, loc.name).getOrNull()
        }
    }

    private fun buildTile(data: WearWeatherData?): TileBuilders.Tile {
        val layout = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // Tap anywhere on the tile opens the full app.
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_app")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(WearMainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Text.Builder()
                    .setText(
                        if (data != null) {
                            "${WearUnitFormatter.displayTemp(data.temperature, data.tempUnit)}\u00B0"
                        } else {
                            "--"
                        },
                    )
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(40f))
                            .setColor(argb(0xFFF0F0F5.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(
                Text.Builder()
                    .setText(data?.condition ?: getString(R.string.wear_tile_no_data))
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(14f))
                            .setColor(argb(0xFFB0B8CC.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(
                Text.Builder()
                    .setText(
                        if (data != null) {
                            getString(
                                R.string.wear_tile_high_low_abbrev,
                                WearUnitFormatter.displayTemp(data.high, data.tempUnit),
                                WearUnitFormatter.displayTemp(data.low, data.tempUnit),
                            )
                        } else {
                            ""
                        },
                    )
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(12f))
                            .setColor(argb(0xFF7A839E.toInt()))
                            .build(),
                    )
                    .build(),
            )

        // Detail row: humidity + wind
        if (data != null) {
            layout.addContent(Spacer.Builder().setHeight(dp(6f)).build())
            layout.addContent(
                Row.Builder()
                    .addContent(
                        Text.Builder()
                            .setText(
                                getString(
                                    R.string.wear_tile_humidity_wind,
                                    data.humidity,
                                    WearUnitFormatter.displayWind(data.windSpeed, data.windUnit),
                                ),
                            )
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(0xFF7A839E.toInt()))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            layout.addContent(Spacer.Builder().setHeight(dp(3f)).build())
            layout.addContent(
                Text.Builder()
                    .setText(data.locationName)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(10f))
                            .setColor(argb(0xFF7A839E.toInt()))
                            .build(),
                    )
                    .setMaxLines(1)
                    .build(),
            )
        }

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(layout.build())
                            .build(),
                    )
                    .build(),
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("2")
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(30 * 60 * 1000L)
            .build()
    }
}
