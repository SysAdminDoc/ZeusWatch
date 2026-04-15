package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Unified alert repository that dispatches to the correct international
 * alert source based on the user's location (country detection) and preferences.
 *
 * Supports NWS (US), MeteoAlarm/EUMETNET (Europe), JMA (Japan),
 * and Environment Canada (CA). Falls back gracefully when a source is
 * unavailable or the country is not covered.
 */
@Singleton
class AlertRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adapters: Set<@JvmSuppressWildcards AlertSourceAdapter>,
    private val prefs: UserPreferences,
) {
    companion object {
        private const val TAG = "AlertRepository"
    }

    /**
     * Fetch weather alerts for the given coordinates.
     *
     * 1. Determine the user's country from lat/lon (via Geocoder or preference override).
     * 2. Select matching adapters based on [AlertSourcePreference] and country.
     * 3. Query all applicable adapters in parallel and merge results.
     * 4. Sort by severity then urgency.
     */
    suspend fun getAlerts(latitude: Double, longitude: Double): Result<List<WeatherAlert>> =
        getAlerts(latitude, longitude, preferenceOverride = null)

    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
        preferenceOverride: AlertSourcePreference? = null,
    ): Result<List<WeatherAlert>> =
        withContext(Dispatchers.IO) {
            try {
                val settings = prefs.settings.first()
                val pref = preferenceOverride ?: settings.alertSourcePref
                val countryCode = detectCountry(latitude, longitude)

                Log.d(TAG, "Detected country: $countryCode, alertSourcePref: $pref")

                val selectedAdapters = resolveAdapters(pref, countryCode)

                if (selectedAdapters.isEmpty()) {
                    Log.d(TAG, "No alert adapters matched for country=$countryCode pref=$pref")
                    return@withContext Result.success(emptyList())
                }

                // Query all applicable adapters in parallel
                val allAlerts = coroutineScope {
                    selectedAdapters.map { adapter ->
                        async {
                            try {
                                // MeteoAlarm needs country-specific call
                                if (adapter is MeteoAlarmAdapter && countryCode != null) {
                                    adapter.getAlertsForCountry(countryCode)
                                } else {
                                    adapter.getAlerts(latitude, longitude)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Adapter ${adapter.sourceId} failed: ${e.message}")
                                Result.success(emptyList())
                            }
                        }
                    }.awaitAll()
                }

                // Merge results, dedup by ID, sort by severity then urgency
                val merged = allAlerts
                    .mapNotNull { it.getOrNull() }
                    .flatten()
                    .distinctBy { it.id }
                    .sortedWith(
                        compareBy<WeatherAlert> { it.severity.sortOrder }
                            .thenBy { it.urgency.sortOrder }
                    )

                Result.success(merged)
            } catch (e: Exception) {
                Log.e(TAG, "Alert fetch failed", e)
                Result.failure(e)
            }
        }

    /**
     * Resolve which adapters to query based on user preference and detected country.
     */
    private fun resolveAdapters(
        pref: AlertSourcePreference,
        countryCode: String?,
    ): List<AlertSourceAdapter> {
        return when (pref) {
            AlertSourcePreference.AUTO -> {
                if (countryCode == null) {
                    // If we cannot determine the country, only use adapters that
                    // explicitly declare global coverage. Regional adapters can
                    // otherwise show false-positive alerts for the wrong country.
                    adapters.filter { "GLOBAL" in it.supportedRegions }
                } else {
                    adapters.filter { adapter ->
                        "GLOBAL" in adapter.supportedRegions ||
                            countryCode in adapter.supportedRegions
                    }.ifEmpty {
                        // No specific adapter for this country — return empty
                        // (could add Open-Meteo fallback here in future)
                        emptyList()
                    }
                }
            }

            AlertSourcePreference.NWS_ONLY ->
                adapters.filter { it.sourceId == "nws" }

            AlertSourcePreference.METEOALARM_ONLY ->
                adapters.filter { it.sourceId == "meteoalarm" }

            AlertSourcePreference.JMA_ONLY ->
                adapters.filter { it.sourceId == "jma" }

            AlertSourcePreference.ECCC_ONLY ->
                adapters.filter { it.sourceId == "eccc" }

            AlertSourcePreference.ALL_SOURCES ->
                adapters.toList()
        }
    }

    /**
     * Detect ISO 3166-1 alpha-2 country code from coordinates using Android's Geocoder.
     * Returns null if geocoding is unavailable or fails.
     */
    private suspend fun detectCountry(lat: Double, lon: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                detectCountryWithGeocoder(lat, lon) ?: detectCountryFromTimezone()
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder country detection failed: ${e.message}")
                detectCountryFromTimezone()
            }
        }
    }

    private suspend fun detectCountryWithGeocoder(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null

        val geocoder = Geocoder(context, Locale.US)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses.firstOrNull()?.countryCode?.uppercase())
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.countryCode?.uppercase()
        }
    }

    /**
     * Rough heuristic: map the device's default timezone to a country code.
     * Only used as a last-resort fallback when Geocoder fails.
     */
    private fun detectCountryFromTimezone(): String? {
        val tz = java.util.TimeZone.getDefault().id
        return when {
            tz.startsWith("Canada/") -> "CA"
            isCanadianTimezone(tz) -> "CA"
            isUnitedStatesTimezone(tz) -> "US"
            tz.startsWith("Europe/") -> {
                when {
                    tz.contains("London") -> "GB"
                    tz.contains("Berlin") -> "DE"
                    tz.contains("Paris") -> "FR"
                    tz.contains("Rome") -> "IT"
                    tz.contains("Madrid") -> "ES"
                    tz.contains("Amsterdam") -> "NL"
                    tz.contains("Brussels") -> "BE"
                    tz.contains("Vienna") -> "AT"
                    tz.contains("Zurich") -> "CH"
                    tz.contains("Stockholm") -> "SE"
                    tz.contains("Oslo") -> "NO"
                    tz.contains("Copenhagen") -> "DK"
                    tz.contains("Helsinki") -> "FI"
                    tz.contains("Warsaw") -> "PL"
                    tz.contains("Prague") -> "CZ"
                    tz.contains("Budapest") -> "HU"
                    tz.contains("Bucharest") -> "RO"
                    tz.contains("Athens") -> "GR"
                    tz.contains("Dublin") -> "IE"
                    tz.contains("Lisbon") -> "PT"
                    else -> null
                }
            }
            tz.startsWith("Asia/Tokyo") -> "JP"
            else -> null
        }
    }

    private fun isCanadianTimezone(timezoneId: String): Boolean {
        return timezoneId in setOf(
            "America/Toronto",
            "America/Vancouver",
            "America/Winnipeg",
            "America/Halifax",
            "America/Edmonton",
            "America/Montreal",
            "America/St_Johns",
            "America/Regina",
            "America/Iqaluit",
            "America/Whitehorse",
            "America/Yellowknife",
            "America/Rankin_Inlet",
        )
    }

    private fun isUnitedStatesTimezone(timezoneId: String): Boolean {
        if (timezoneId.startsWith("US/")) return true

        return timezoneId == "Pacific/Honolulu" ||
            timezoneId == "America/New_York" ||
            timezoneId == "America/Detroit" ||
            timezoneId.startsWith("America/Indiana/") ||
            timezoneId.startsWith("America/Kentucky/") ||
            timezoneId == "America/Chicago" ||
            timezoneId == "America/Menominee" ||
            timezoneId.startsWith("America/North_Dakota/") ||
            timezoneId == "America/Denver" ||
            timezoneId == "America/Boise" ||
            timezoneId == "America/Phoenix" ||
            timezoneId == "America/Los_Angeles" ||
            timezoneId == "America/Anchorage" ||
            timezoneId == "America/Juneau" ||
            timezoneId == "America/Sitka" ||
            timezoneId == "America/Metlakatla" ||
            timezoneId == "America/Yakutat" ||
            timezoneId == "America/Nome" ||
            timezoneId == "America/Adak"
    }
}
