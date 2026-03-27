package com.sysadmindoc.nimbus.data.repository

enum class CardType(val label: String, val defaultEnabled: Boolean = true) {
    WEATHER_SUMMARY("Weather Summary", true),
    RADAR_PREVIEW("Radar Preview", true),
    NOWCAST("Rain Next Hour", true),
    HOURLY_FORECAST("Hourly Forecast", true),
    TEMPERATURE_GRAPH("Temperature Trend", true),
    DAILY_FORECAST("Daily Forecast", true),
    UV_INDEX("UV Index", true),
    WIND_COMPASS("Wind", true),
    AIR_QUALITY("Air Quality", true),
    POLLEN("Pollen", true),
    OUTDOOR_SCORE("Outdoor Activity Score", false),
    SNOWFALL("Snowfall", false),
    SEVERE_WEATHER("Severe Weather Potential", false),
    GOLDEN_HOUR("Golden Hour", false),
    SUNSHINE("Sunshine Duration", false),
    DRIVING_CONDITIONS("Driving Conditions", false),
    HEALTH_ALERTS("Health Alerts", false),
    CLOTHING("What to Wear", false),
    PET_SAFETY("Pet Safety", false),
    MOON_PHASE("Moon Phase", true),
    HUMIDITY("Humidity & Comfort", true),
    PRECIPITATION_CHART("Precipitation Forecast", true),
    PRESSURE_TREND("Pressure Trend", false),
    WIND_TREND("Wind Forecast", false),
    DETAILS_GRID("Today's Details", true),
}

val DEFAULT_CARD_ORDER: List<CardType> = CardType.entries.toList()
