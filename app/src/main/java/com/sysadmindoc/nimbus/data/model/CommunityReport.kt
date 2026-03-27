package com.sysadmindoc.nimbus.data.model

/**
 * A crowd-sourced weather condition report submitted by a user.
 * Stored in Firebase Firestore collection "community_reports".
 *
 * NOTE: Firebase Firestore requires a google-services.json file in app/.
 * You must configure your own Firebase project and download the config file.
 */
data class CommunityReport(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val condition: ReportCondition = ReportCondition.SUNNY,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "", // Anonymous device ID (no user accounts)
)

enum class ReportCondition(val label: String, val emoji: String) {
    SUNNY("Sunny", "\u2600\uFE0F"),
    PARTLY_CLOUDY("Partly Cloudy", "\u26C5"),
    CLOUDY("Cloudy", "\u2601\uFE0F"),
    RAIN("Rain", "\uD83C\uDF27\uFE0F"),
    HEAVY_RAIN("Heavy Rain", "\u26C8\uFE0F"),
    SNOW("Snow", "\uD83C\uDF28\uFE0F"),
    FOG("Fog", "\uD83C\uDF2B\uFE0F"),
    WIND("Windy", "\uD83D\uDCA8"),
    HAIL("Hail", "\uD83E\uDDCA"),
    TORNADO("Tornado", "\uD83C\uDF2A\uFE0F"),
}
