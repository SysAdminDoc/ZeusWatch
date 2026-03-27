# Custom Icon Packs for ZeusWatch

## Bundled Icon Packs

To create a bundled icon pack, add a subdirectory here containing:

1. A `manifest.json` file
2. All referenced icon files (PNG, WebP, or SVG)

### Manifest Format

```json
{
  "id": "my-icon-pack",
  "name": "My Icon Pack",
  "author": "Your Name",
  "format": "png",
  "mappings": {
    "0":  { "day": "clear-day.png",           "night": "clear-night.png" },
    "1":  { "day": "mainly-clear-day.png",     "night": "mainly-clear-night.png" },
    "2":  { "day": "partly-cloudy-day.png",    "night": "partly-cloudy-night.png" },
    "3":  { "day": "overcast.png",             "night": "overcast.png" },
    "45": { "day": "fog.png",                  "night": "fog.png" },
    "48": { "day": "freezing-fog.png",         "night": "freezing-fog.png" },
    "51": { "day": "drizzle-light.png",        "night": "drizzle-light.png" },
    "53": { "day": "drizzle.png",              "night": "drizzle.png" },
    "55": { "day": "drizzle-heavy.png",        "night": "drizzle-heavy.png" },
    "56": { "day": "freezing-drizzle.png",     "night": "freezing-drizzle.png" },
    "57": { "day": "freezing-drizzle.png",     "night": "freezing-drizzle.png" },
    "61": { "day": "rain-light.png",           "night": "rain-light.png" },
    "63": { "day": "rain.png",                 "night": "rain.png" },
    "65": { "day": "rain-heavy.png",           "night": "rain-heavy.png" },
    "66": { "day": "freezing-rain.png",        "night": "freezing-rain.png" },
    "67": { "day": "freezing-rain.png",        "night": "freezing-rain.png" },
    "71": { "day": "snow-light.png",           "night": "snow-light.png" },
    "73": { "day": "snow.png",                 "night": "snow.png" },
    "75": { "day": "snow-heavy.png",           "night": "snow-heavy.png" },
    "77": { "day": "snow-grains.png",          "night": "snow-grains.png" },
    "80": { "day": "showers-day.png",          "night": "showers-night.png" },
    "81": { "day": "showers-day.png",          "night": "showers-night.png" },
    "82": { "day": "showers-heavy.png",        "night": "showers-heavy.png" },
    "85": { "day": "snow-showers-day.png",     "night": "snow-showers-night.png" },
    "86": { "day": "snow-showers-heavy.png",   "night": "snow-showers-heavy.png" },
    "95": { "day": "thunderstorm.png",         "night": "thunderstorm.png" },
    "96": { "day": "thunderstorm-hail.png",    "night": "thunderstorm-hail.png" },
    "99": { "day": "thunderstorm-hail.png",    "night": "thunderstorm-hail.png" }
  }
}
```

### WMO Weather Codes

The mapping keys are WMO weather interpretation codes:

| Code | Condition |
|------|-----------|
| 0 | Clear sky |
| 1 | Mainly clear |
| 2 | Partly cloudy |
| 3 | Overcast |
| 45 | Fog |
| 48 | Depositing rime fog |
| 51-55 | Drizzle (light/moderate/dense) |
| 56-57 | Freezing drizzle |
| 61-65 | Rain (slight/moderate/heavy) |
| 66-67 | Freezing rain |
| 71-75 | Snow (slight/moderate/heavy) |
| 77 | Snow grains |
| 80-82 | Rain showers |
| 85-86 | Snow showers |
| 95 | Thunderstorm |
| 96, 99 | Thunderstorm with hail |

### Recommended Icon Size

- 128x128 px minimum (PNG/WebP)
- Transparent backgrounds preferred

## External Icon Packs (Third-Party APKs)

Third-party apps can provide icon packs by:

1. Adding an activity with intent filter action `com.sysadmindoc.nimbus.ICON_PACK`
2. Placing a `manifest.json` (same format as above) at `assets/nimbus-iconpack/manifest.json`
3. Placing icon files in `assets/nimbus-iconpack/`

### AndroidManifest.xml Example

```xml
<activity android:name=".IconPackActivity"
          android:exported="true">
    <intent-filter>
        <action android:name="com.sysadmindoc.nimbus.ICON_PACK" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```
