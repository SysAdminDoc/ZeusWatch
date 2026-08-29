package com.sysadmindoc.nimbus.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
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
import androidx.wear.protolayout.TriggerBuilders
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.WearMainActivity
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.data.WearWeatherResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

internal const val WEATHER_TILE_FRESHNESS_MS = 30 * 60 * 1000L

@AndroidEntryPoint
class WeatherTileService : Material3TileService(
    allowDynamicTheme = true,
    defaultColorScheme = ColorScheme(),
    serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    @Inject lateinit var repository: WearWeatherRepository
    @Inject lateinit var locationProvider: WearLocationProvider

    override suspend fun MaterialScope.tileResponse(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile = buildTile(loadTileDataForTile(), this)

    internal suspend fun loadTileData(): WearWeatherResult =
        repository.loadWeather { locationProvider.getLocation() }

    internal suspend fun loadTileDataForTile(): WearWeatherResult =
        try {
            loadTileData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            WearWeatherResult.Failed(e)
        }

    /**
     * A tile with no location must say so. "No data" reads like a transient
     * outage and hides the fact that the watch needs permission or a phone sync.
     */
    internal fun emptyStateLabel(result: WearWeatherResult): String =
        if (result is WearWeatherResult.NoLocation) {
            getString(R.string.wear_tile_no_location)
        } else {
            getString(R.string.wear_tile_no_data)
        }

    private fun freshnessLabel(ageMin: Long): String = when {
        ageMin < 1 -> getString(R.string.wear_tile_just_now)
        ageMin < 60 -> getString(R.string.wear_tile_updated_min, ageMin)
        else -> getString(R.string.wear_tile_updated_hr, ageMin / 60)
    }

    private fun buildTile(
        result: WearWeatherResult,
        materialScope: MaterialScope?,
        launchPackageName: String = packageName,
    ): TileBuilders.Tile {
        val data = (result as? WearWeatherResult.Available)?.data
        val emptyLabel = emptyStateLabel(result)
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
                                            .setPackageName(launchPackageName)
                                            .setClassName(WearMainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )

        if (materialScope != null) {
            layout.addContent(
                materialScope.icon(
                    WeatherTileLottieResources.weatherIcon(),
                    "weather_tile_icon",
                    dp(28f),
                    dp(28f),
                    materialScope.colorScheme.primary,
                ),
            )
            layout.addContent(Spacer.Builder().setHeight(dp(3f)).build())
        }

        layout.addContent(
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
                        .setColor(tileColor(materialScope?.colorScheme?.onSurface, 0xFFF0F0F5.toInt()))
                        .build(),
                )
                .build(),
            )
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(
                Text.Builder()
                    .setText(data?.condition ?: emptyLabel)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(14f))
                            .setColor(tileColor(materialScope?.colorScheme?.onSurfaceVariant, 0xFFB0B8CC.toInt()))
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
                            .setColor(tileColor(materialScope?.colorScheme?.outline, 0xFF7A839E.toInt()))
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
                                    .setColor(tileColor(materialScope?.colorScheme?.outline, 0xFF7A839E.toInt()))
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
                            .setColor(tileColor(materialScope?.colorScheme?.outline, 0xFF7A839E.toInt()))
                            .build(),
                    )
                    .setMaxLines(1)
                    .build(),
            )
            // Freshness reflects data age (updatedAtMs) so cached data the
            // phone pushed during an outage doesn't read "just now"; older
            // payloads without the field fall back to sync age.
            val freshnessAnchorMs = if (data.updatedAtMs > 0L) data.updatedAtMs else data.syncedAtMs
            if (freshnessAnchorMs > 0L) {
                val ageMin = ((System.currentTimeMillis() - freshnessAnchorMs) / 60_000L).coerceAtLeast(0)
                layout.addContent(
                    Text.Builder()
                        .setText(freshnessLabel(ageMin))
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(9f))
                                .setColor(tileColor(materialScope?.colorScheme?.outlineVariant, 0xFF5A6180.toInt()))
                                .build(),
                        )
                        .build(),
                )
            }
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
            .setFreshnessIntervalMillis(WEATHER_TILE_FRESHNESS_MS)
            .build()
    }

    /**
     * Rebuilds a [ColorProp] from the public parts of [materialColor].
     *
     * `LayoutColor.prop` is `@RestrictedApi` to the protolayout library group,
     * so reading it fails `:wear:lintDebug`. `staticArgb` and `dynamicArgb`
     * carry the same information and are public.
     */
    internal fun tileColor(materialColor: LayoutColor?, fallbackArgb: Int): ColorProp {
        if (materialColor == null) return argb(fallbackArgb)
        return ColorProp.Builder(materialColor.staticArgb)
            .apply { materialColor.dynamicArgb?.let(::setDynamicValue) }
            .build()
    }
}

internal object WeatherTileLottieResources {
    fun weatherIcon(): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidLottieResourceByResId(
                ResourceBuilders.AndroidLottieResourceByResId.Builder(R.raw.weather_tile_clear)
                    .setStartTrigger(TriggerBuilders.createOnVisibleTrigger())
                    .build(),
            )
            .build()
}
