# Wear OS Companion

ZeusWatch ships a Wear OS companion app with phone sync, direct Open-Meteo
fallback, a tile, and watch-face complications.

## Complications

The complication data source is declared by
`wear/src/main/java/com/sysadmindoc/nimbus/wear/complication/WeatherComplicationService.kt`.

Supported complication types:

- `SHORT_TEXT` - current temperature.
- `LONG_TEXT` - temperature plus condition, with high/low title.
- `RANGED_VALUE` - UV index on a 0-12 scale.
- `SMALL_IMAGE` - weather icon image with condition/temperature content
  description.

Install/use flow:

1. Install the ZeusWatch Wear APK on the watch.
2. Open the watch-face editor.
3. Choose a complication slot.
4. Select `Weather` from the ZeusWatch provider.
5. Pick one of the supported types above, depending on the slot.

Data source behavior:

- Fresh phone-synced data is preferred to avoid watch-side network calls.
- If the phone has not synced recently, the watch falls back to a direct
  Open-Meteo request using the watch location.
- The complication updates at the manifest-declared 30-minute cadence.

## Tests

The pure complication data factory is covered by
`wear/src/test/java/com/sysadmindoc/nimbus/wear/complication/WeatherComplicationDataFactoryTest.kt`.
Run it with:

```powershell
.\gradlew.bat :wear:testDebugUnitTest --tests com.sysadmindoc.nimbus.wear.complication.WeatherComplicationDataFactoryTest --console=plain
```
