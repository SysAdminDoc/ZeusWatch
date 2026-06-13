package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.CardType

@get:StringRes
internal val CardType.labelRes: Int
    get() = when (this) {
        CardType.WEATHER_SUMMARY -> R.string.card_type_weather_summary
        CardType.RADAR_PREVIEW -> R.string.card_type_radar_preview
        CardType.NOWCAST -> R.string.card_type_nowcast
        CardType.HOURLY_FORECAST -> R.string.card_type_hourly_forecast
        CardType.TEMPERATURE_GRAPH -> R.string.card_type_temperature_graph
        CardType.FORECAST_EVOLUTION -> R.string.card_type_forecast_evolution
        CardType.DAILY_FORECAST -> R.string.card_type_daily_forecast
        CardType.UV_INDEX -> R.string.card_type_uv_index
        CardType.WIND_COMPASS -> R.string.card_type_wind_compass
        CardType.AIR_QUALITY -> R.string.card_type_air_quality
        CardType.POLLEN -> R.string.card_type_pollen
        CardType.OUTDOOR_SCORE -> R.string.card_type_outdoor_score
        CardType.SNOWFALL -> R.string.card_type_snowfall
        CardType.SEVERE_WEATHER -> R.string.card_type_severe_weather
        CardType.GOLDEN_HOUR -> R.string.card_type_golden_hour
        CardType.SUNSHINE -> R.string.card_type_sunshine
        CardType.DRIVING_CONDITIONS -> R.string.card_type_driving_conditions
        CardType.HEALTH_ALERTS -> R.string.card_type_health_alerts
        CardType.CLOTHING -> R.string.card_type_clothing
        CardType.PET_SAFETY -> R.string.card_type_pet_safety
        CardType.MOON_PHASE -> R.string.card_type_moon_phase
        CardType.HUMIDITY -> R.string.card_type_humidity
        CardType.PRECIPITATION_CHART -> R.string.card_type_precipitation_chart
        CardType.PRESSURE_TREND -> R.string.card_type_pressure_trend
        CardType.WIND_TREND -> R.string.card_type_wind_trend
        CardType.DETAILS_GRID -> R.string.card_type_details_grid
        CardType.CLOUD_COVER -> R.string.card_type_cloud_cover
        CardType.VISIBILITY -> R.string.card_type_visibility
        CardType.ON_THIS_DAY -> R.string.card_type_on_this_day
        CardType.AURORA_KP -> R.string.card_type_aurora_kp
        CardType.ACTIVITY_INDEX -> R.string.card_type_activity_index
        CardType.SOLAR -> R.string.card_type_solar
    }
