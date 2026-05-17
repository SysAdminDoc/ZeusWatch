package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.AlertMinSeverity
import com.sysadmindoc.nimbus.data.repository.AlertSourcePreference
import com.sysadmindoc.nimbus.data.repository.IconStyle
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.PressureUnit
import com.sysadmindoc.nimbus.data.repository.RadarProvider
import com.sysadmindoc.nimbus.data.repository.SummaryStyle
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.ThemeMode
import com.sysadmindoc.nimbus.data.repository.TimeFormat
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import com.sysadmindoc.nimbus.data.repository.WindUnit

@get:StringRes
internal val TempUnit.labelRes: Int
    get() = when (this) {
        TempUnit.FAHRENHEIT -> R.string.settings_unit_fahrenheit
        TempUnit.CELSIUS -> R.string.settings_unit_celsius
    }

@get:StringRes
internal val WindUnit.labelRes: Int
    get() = when (this) {
        WindUnit.MPH -> R.string.settings_unit_mph
        WindUnit.KMH -> R.string.settings_unit_kmh
        WindUnit.MS -> R.string.settings_unit_ms
        WindUnit.KNOTS -> R.string.settings_unit_knots
    }

@get:StringRes
internal val PressureUnit.labelRes: Int
    get() = when (this) {
        PressureUnit.INHG -> R.string.settings_unit_inhg
        PressureUnit.HPA -> R.string.settings_unit_hpa
        PressureUnit.MBAR -> R.string.settings_unit_mbar
    }

@get:StringRes
internal val PrecipUnit.labelRes: Int
    get() = when (this) {
        PrecipUnit.INCHES -> R.string.settings_unit_inches
        PrecipUnit.MM -> R.string.settings_unit_mm
    }

@get:StringRes
internal val VisibilityUnit.labelRes: Int
    get() = when (this) {
        VisibilityUnit.MILES -> R.string.settings_unit_miles
        VisibilityUnit.KM -> R.string.settings_unit_km
    }

@get:StringRes
internal val TimeFormat.labelRes: Int
    get() = when (this) {
        TimeFormat.TWELVE_HOUR -> R.string.settings_time_12_hour
        TimeFormat.TWENTY_FOUR_HOUR -> R.string.settings_time_24_hour
    }

@get:StringRes
internal val AlertMinSeverity.labelRes: Int
    get() = when (this) {
        AlertMinSeverity.EXTREME -> R.string.settings_alert_severity_extreme
        AlertMinSeverity.SEVERE -> R.string.settings_alert_severity_severe
        AlertMinSeverity.MODERATE -> R.string.settings_alert_severity_moderate
        AlertMinSeverity.ALL -> R.string.settings_alert_severity_all
    }

@get:StringRes
internal val AlertSourcePreference.labelRes: Int
    get() = when (this) {
        AlertSourcePreference.AUTO -> R.string.settings_alert_source_auto
        AlertSourcePreference.NWS_ONLY -> R.string.settings_alert_source_nws
        AlertSourcePreference.METEOALARM_ONLY -> R.string.settings_alert_source_meteoalarm
        AlertSourcePreference.JMA_ONLY -> R.string.settings_alert_source_jma
        AlertSourcePreference.ECCC_ONLY -> R.string.settings_alert_source_eccc
        AlertSourcePreference.ALL_SOURCES -> R.string.settings_alert_source_all
    }

@get:StringRes
internal val RadarProvider.labelRes: Int
    get() = when (this) {
        RadarProvider.WINDY_WEBVIEW -> R.string.settings_radar_provider_windy
        RadarProvider.NATIVE_MAPLIBRE -> R.string.settings_radar_provider_rainviewer
        RadarProvider.NWS_WEBVIEW -> R.string.settings_radar_provider_nws
        RadarProvider.NWS_STANDARD_WEBVIEW -> R.string.settings_radar_provider_nws_lite
    }

@get:StringRes
internal val RadarProvider.summaryRes: Int
    get() = when (this) {
        RadarProvider.WINDY_WEBVIEW -> R.string.settings_radar_provider_windy_summary
        RadarProvider.NATIVE_MAPLIBRE -> R.string.settings_radar_provider_rainviewer_summary
        RadarProvider.NWS_WEBVIEW -> R.string.settings_radar_provider_nws_summary
        RadarProvider.NWS_STANDARD_WEBVIEW -> R.string.settings_radar_provider_nws_lite_summary
    }

@get:StringRes
internal val IconStyle.labelRes: Int
    get() = when (this) {
        IconStyle.MATERIAL -> R.string.settings_icon_style_material
        IconStyle.METEOCONS -> R.string.settings_icon_style_meteocons
        IconStyle.CUSTOM -> R.string.settings_icon_style_custom
    }

@get:StringRes
internal val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.STATIC_DARK -> R.string.settings_theme_static_dark
        ThemeMode.WEATHER_ADAPTIVE -> R.string.settings_theme_weather_adaptive
    }

@get:StringRes
internal val SummaryStyle.labelRes: Int
    get() = when (this) {
        SummaryStyle.TEMPLATE -> R.string.settings_summary_standard
        SummaryStyle.AI_GENERATED -> R.string.settings_summary_ai_generated
    }

@get:StringRes
internal val WeatherSourceProvider.displayNameRes: Int
    get() = when (this) {
        WeatherSourceProvider.OPEN_METEO -> R.string.weather_source_open_meteo
        WeatherSourceProvider.OPEN_METEO_BOM -> R.string.weather_source_open_meteo_bom
        WeatherSourceProvider.NWS -> R.string.weather_source_nws
        WeatherSourceProvider.OPEN_WEATHER_MAP -> R.string.weather_source_open_weather_map
        WeatherSourceProvider.PIRATE_WEATHER -> R.string.weather_source_pirate_weather
        WeatherSourceProvider.BRIGHT_SKY -> R.string.weather_source_bright_sky
        WeatherSourceProvider.MET_NORWAY -> R.string.weather_source_met_norway
        WeatherSourceProvider.ENVIRONMENT_CANADA -> R.string.weather_source_environment_canada
        WeatherSourceProvider.METEOALARM -> R.string.weather_source_meteoalarm
        WeatherSourceProvider.JMA -> R.string.weather_source_jma
    }
