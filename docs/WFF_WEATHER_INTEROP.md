# WFF Weather Interoperability

Research date: 2026-05-17

## Decision

ZeusWatch should not add a "publish to WFF weather" settings toggle right now.
As of the current public Android and AndroidX surfaces, there is no documented
third-party API that lets a normal weather app publish ZeusWatch forecast data
into the Wear OS system weather store consumed by Watch Face Format
`[WEATHER.*]` expressions.

The supported local route remains:

- ZeusWatch phone app syncs weather to the ZeusWatch Wear app through the
  standard flavor's private DataLayer path, `/weather/current`.
- ZeusWatch Wear app exposes weather to user-selected watch faces through Wear
  complications.
- WFF watch faces that use `[WEATHER.*]` continue to depend on the Wear OS
  system weather provider and the device's location availability.

## Evidence

- Android's WFF weather guide says WFF version 2 includes weather data and that
  watch faces access it through expressions such as `[WEATHER.CONDITION_NAME]`,
  not through an app-provided data contract:
  https://developer.android.com/training/wearables/wff/weather
- The same guide requires watch faces to check `[WEATHER.IS_AVAILABLE]` and
  `[WEATHER.IS_ERROR]`, which indicates that availability is controlled by the
  system weather source rather than by an arbitrary third-party app:
  https://developer.android.com/training/wearables/wff/weather#availability
- The WFF weather testing guidance says the Wear OS device must know its
  location and normally relies on a connected handheld device or available
  networks rather than watch GPS:
  https://developer.android.com/training/wearables/wff/weather#testing-with-weather-data
- The WFF SourceType reference lists the renderable weather fields and
  condition enum values, but does not define a publisher or registration API:
  https://developer.android.com/reference/wear-os/wff/common/attributes/source-type
- The AndroidX Wear Watchface 1.2.0 beta notes show an attempted default weather
  complication/data-source path was added in beta01 and removed in beta02 with
  "Revert 'Expose a new data source for weather complications'":
  https://developer.android.com/jetpack/androidx/releases/wear-watchface
- AndroidX Wear Watchface 1.3.0 deprecates Jetpack watch-face APIs in favor of
  WFF, while complication APIs remain supported:
  https://developer.android.com/jetpack/androidx/releases/wear-watchface

Local checks:

- `app/src/standard/java/com/sysadmindoc/nimbus/sync/WearSyncManager.kt`
  writes a private `PutDataMapRequest` to `/weather/current`.
- `wear/src/main/java/com/sysadmindoc/nimbus/wear/sync/WeatherDataListenerService.kt`
  reads only that ZeusWatch DataLayer path.
- `wear/src/main/java/com/sysadmindoc/nimbus/wear/complication/WeatherComplicationService.kt`
  is the public Wear watch-face integration point available to third-party
  faces through standard complication slots.
- Searches across the repo and local Gradle cache found no public
  `WeatherProvider`, `WeatherDataProvider`, or WFF weather publisher API.

## Compatibility Matrix

| Surface | ZeusWatch support | Notes |
| --- | --- | --- |
| WFF v1 / Wear OS 4 | Not applicable | WFF weather is introduced in WFF v2. |
| WFF v2+ `[WEATHER.*]` watch-face expressions | System-dependent only | ZeusWatch cannot publish into this store through a documented third-party API. |
| Wear OS system weather availability | User/device/OEM-dependent | WFF watch faces must check `[WEATHER.IS_AVAILABLE]` and `[WEATHER.IS_ERROR]`. |
| Standard flavor phone-to-watch sync | Supported | Private ZeusWatch DataLayer path, backed by Google Play services. |
| Freenet flavor phone-to-watch sync | Not supported | F-Droid/freenet build intentionally has no Google Play services Wearable API. |
| Watch-face complications | Supported | `SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`, and `SMALL_IMAGE` are locally implemented and tested. |

## Future API Watchlist

If Google later publishes a normal-app weather publisher contract, ZeusWatch is
well positioned to implement it. The current Wear payload already carries most
WFF-like values:

| WFF field family | Current ZeusWatch source |
| --- | --- |
| `WEATHER.TEMPERATURE` | `WearWeatherData.temperature` |
| `WEATHER.CONDITION_NAME` | `WearWeatherData.condition` |
| `WEATHER.CONDITION` | `WearWeatherData.weatherCode`, mapped from WMO codes |
| `WEATHER.IS_DAY` | `WearWeatherData.isDay` |
| `WEATHER.DAY_TEMPERATURE_LOW/HIGH` | `WearWeatherData.low/high` |
| `WEATHER.CHANCE_OF_PRECIPITATION` | `WearWeatherData.precipChance` |
| `WEATHER.UV_INDEX` | `WearWeatherData.uvIndex` |
| `WEATHER.LAST_UPDATED` | `SyncedWeatherStore.lastSyncTimestamp()` |
| `WEATHER.HOURS.{index}.*` | `WearWeatherData.hourly` |
| `WEATHER.DAYS.{index}.*` | `WearWeatherData.daily` |

Before implementing a future publisher, require all of the following:

- A public Android or AndroidX API/reference page for registering a weather
  provider.
- A documented permission model that works for Play-distributed third-party
  apps, not only OEM/system apps.
- Emulator or physical-device validation that a WFF sample watch face reads
  ZeusWatch-published data.
- A user-visible settings toggle only if the API actually shares ZeusWatch data
  beyond ZeusWatch's own Wear app.
