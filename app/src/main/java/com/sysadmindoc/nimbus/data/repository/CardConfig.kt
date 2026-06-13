package com.sysadmindoc.nimbus.data.repository

enum class CardType(val label: String, val defaultEnabled: Boolean = true) {
    WEATHER_SUMMARY("Weather Summary", true),
    RADAR_PREVIEW("Radar Preview", true),
    NOWCAST("Rain Next Hour", true),
    HOURLY_FORECAST("Hourly Forecast", true),
    TEMPERATURE_GRAPH("Temperature Trend", true),
    FORECAST_EVOLUTION("Forecast Evolution", false),
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
    CLOUD_COVER("Cloud Cover", false),
    VISIBILITY("Visibility", false),
    ON_THIS_DAY("On This Day", false),
}

val DEFAULT_CARD_ORDER: List<CardType> = CardType.entries.toList()
val DEFAULT_DISABLED_CARDS: Set<String> = CardType.entries
    .filterNot { it.defaultEnabled }
    .map { it.name }
    .toSet()

private val ACCESSIBILITY_PRIORITY_CARDS = listOf(
    CardType.SEVERE_WEATHER,
    CardType.WEATHER_SUMMARY,
    CardType.HOURLY_FORECAST,
    CardType.DAILY_FORECAST,
    CardType.DETAILS_GRID,
)

fun accessibilityCardOrder(baseOrder: List<CardType>): List<CardType> {
    val prioritized = ACCESSIBILITY_PRIORITY_CARDS.filter { it in baseOrder }
    val rest = baseOrder.filter { it !in ACCESSIBILITY_PRIORITY_CARDS }
    return prioritized + rest
}
