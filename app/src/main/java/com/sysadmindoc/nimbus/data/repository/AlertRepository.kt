package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.api.WmoAlertAdapter
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
 * Outcome of a detailed alert fetch. Distinguishes a trustworthy "no active
 * alerts" from an outage so safety-critical callers don't treat a total
 * provider failure as clear skies.
 *
 * @property alerts the merged, deduped, sorted alerts that were retrieved.
 * @property allAdaptersFailed true when every adapter that was actually queried
 *   failed (transport error / `Result.failure`); false when nothing was
 *   attempted (no covering source) or at least one query succeeded.
 * @property failedSources source ids that failed, for logging/diagnostics.
 */
data class AlertFetchResult(
    val alerts: List<WeatherAlert>,
    val allAdaptersFailed: Boolean,
    val failedSources: List<String>,
)

/**
 * Unified alert repository that dispatches to the correct international
 * alert source based on the user's location (country detection) and preferences.
 *
 * Supports NWS (US), MeteoAlarm/EUMETNET (Europe), JMA (Japan),
 * Environment Canada (CA), and BMKG (ID). Falls back gracefully when a source is
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

        private val COUNTRY_NAME_ALIASES = mapOf(
            "britain" to "GB",
            "england" to "GB",
            "great britain" to "GB",
            "northern ireland" to "GB",
            "scotland" to "GB",
            "uk" to "GB",
            "united kingdom" to "GB",
            "wales" to "GB",
            "us" to "US",
            "usa" to "US",
            "united states" to "US",
            "united states of america" to "US",
            "america" to "US",
            "czech republic" to "CZ",
            "czechia" to "CZ",
            "korea" to "KR",
            "republic of korea" to "KR",
            "south korea" to "KR",
            "turkey" to "TR",
            "turkiye" to "TR",
        )
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
        getAlerts(latitude, longitude, preferenceOverride = null, countryHint = null)

    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
        preferenceOverride: AlertSourcePreference? = null,
        includeMeteredSources: Boolean = true,
        countryHint: String? = null,
    ): Result<List<WeatherAlert>> =
        withContext(Dispatchers.IO) {
            try {
                val outcomes = collectOutcomes(
                    latitude, longitude, preferenceOverride, includeMeteredSources, countryHint,
                )
                Result.success(mergeAlerts(outcomes))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Alert fetch failed", e)
                Result.failure(e)
            }
        }

    /**
     * Like [getAlerts] but preserves the per-adapter outcome so callers (notably
     * the background [com.sysadmindoc.nimbus.util.AlertCheckWorker]) can tell a
     * genuine "no active alerts" from a provider outage. The plain [getAlerts]
     * collapses an all-providers-down fetch into `success(emptyList())`, which
     * would make a real outage during a severe-weather event look identical to
     * clear skies and suppress the worker's retry. This safety-of-life path
     * surfaces [AlertFetchResult.allAdaptersFailed] and the failed source ids.
     */
    suspend fun getAlertsDetailed(
        latitude: Double,
        longitude: Double,
        preferenceOverride: AlertSourcePreference? = null,
        includeMeteredSources: Boolean = true,
        countryHint: String? = null,
    ): AlertFetchResult =
        withContext(Dispatchers.IO) {
            try {
                val outcomes = collectOutcomes(
                    latitude, longitude, preferenceOverride, includeMeteredSources, countryHint,
                )
                val attempted = outcomes.count { it.attempted }
                val failedSources = outcomes.filter { it.failed }.map { it.sourceId }
                AlertFetchResult(
                    alerts = mergeAlerts(outcomes),
                    // Only an outage when something was actually attempted and all
                    // attempts failed. Zero attempts (no covering adapter) is a
                    // legitimate "no coverage", not a failure.
                    allAdaptersFailed = attempted > 0 && failedSources.size == attempted,
                    failedSources = failedSources,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Alert fetch failed", e)
                // A top-level failure (e.g. settings read) is a total outage from
                // the caller's perspective — never report it as all-clear.
                AlertFetchResult(emptyList(), allAdaptersFailed = true, failedSources = emptyList())
            }
        }

    /**
     * Run the configured alert dispatch and return one [AdapterOutcome] per
     * selected adapter. A thrown exception or a returned `Result.failure` marks
     * that source as failed; the country-aware skip branches (MeteoAlarm/WMO
     * with no/out-of-region country) are recorded as not-attempted so they
     * never count toward an outage.
     */
    private suspend fun collectOutcomes(
        latitude: Double,
        longitude: Double,
        preferenceOverride: AlertSourcePreference?,
        includeMeteredSources: Boolean,
        countryHint: String?,
    ): List<AdapterOutcome> {
        val settings = prefs.settings.first()
        val pref = preferenceOverride ?: settings.alertSourcePref
        val countryCode = normalizeCountryHint(countryHint) ?: detectCountry(latitude, longitude)

        Log.d(TAG, "Detected country: $countryCode, alertSourcePref: $pref")

        val selectedAdapters = resolveAdapters(pref, countryCode)
            .let { adapters ->
                if (includeMeteredSources) adapters else adapters.filterNot { it.isMetered }
            }

        if (selectedAdapters.isEmpty()) {
            Log.d(TAG, "No alert adapters matched for country=$countryCode pref=$pref")
            return emptyList()
        }

        return coroutineScope {
            selectedAdapters.map { adapter ->
                async {
                    // MeteoAlarm's `getAlerts(lat, lon)` is a no-op stub — the API
                    // needs an ISO country code. Skip it cleanly (a) when the
                    // country isn't known yet and (b) when the user's country is
                    // outside MeteoAlarm's EUMETNET coverage — otherwise
                    // `ALL_SOURCES` mode would silently swallow MeteoAlarm
                    // whenever the device falls outside Europe.
                    when {
                        adapter is MeteoAlarmAdapter -> {
                            if (countryCode != null && countryCode in adapter.supportedRegions) {
                                queryAdapter(adapter) { adapter.getAlertsForCountry(countryCode) }
                            } else {
                                AdapterOutcome.skipped(adapter.sourceId)
                            }
                        }
                        adapter is WmoAlertAdapter -> {
                            if (countryCode != null) {
                                queryAdapter(adapter) { adapter.getAlertsForCountry(countryCode) }
                            } else {
                                AdapterOutcome.skipped(adapter.sourceId)
                            }
                        }
                        else -> queryAdapter(adapter) { adapter.getAlerts(latitude, longitude) }
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Invoke an adapter, mapping both thrown exceptions and returned
     * `Result.failure` to a failed [AdapterOutcome] so transport errors are
     * visible to the detailed path instead of being collapsed into empty.
     */
    private suspend fun queryAdapter(
        adapter: AlertSourceAdapter,
        block: suspend () -> Result<List<WeatherAlert>>,
    ): AdapterOutcome {
        val result = try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Adapter ${adapter.sourceId} threw: ${e.message}")
            Result.failure(e)
        }
        return result.fold(
            onSuccess = { AdapterOutcome(adapter.sourceId, it, attempted = true, failed = false) },
            onFailure = {
                Log.w(TAG, "Adapter ${adapter.sourceId} failed: ${it.message}")
                AdapterOutcome(adapter.sourceId, emptyList(), attempted = true, failed = true)
            },
        )
    }

    /** Merge, dedup by ID, and sort by severity then urgency. */
    private fun mergeAlerts(outcomes: List<AdapterOutcome>): List<WeatherAlert> =
        outcomes
            .flatMap { it.alerts }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<WeatherAlert> { it.severity.sortOrder }
                    .thenBy { it.urgency.sortOrder }
            )

    private data class AdapterOutcome(
        val sourceId: String,
        val alerts: List<WeatherAlert>,
        val attempted: Boolean,
        val failed: Boolean,
    ) {
        companion object {
            fun skipped(sourceId: String) =
                AdapterOutcome(sourceId, emptyList(), attempted = false, failed = false)
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
                    // AUTO is a true fallback chain, not aggregation: GLOBAL
                    // adapters (WMO, Pirate Weather) carry the same physical
                    // alerts as the regional feeds under different IDs (NWS id
                    // vs WMO capId vs pw_ hash), so querying both would emit
                    // duplicate notifications. Only fall back to GLOBAL when
                    // no regional adapter covers the country.
                    adapters.filter { countryCode in it.supportedRegions }
                        .ifEmpty { adapters.filter { "GLOBAL" in it.supportedRegions } }
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
    private fun normalizeCountryHint(countryHint: String?): String? {
        val trimmed = countryHint?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val alphaOnly = trimmed.filter { it.isLetter() }
        // Only trust two letters as an ISO 3166-1 alpha-2 code when they are
        // ASCII and actually assigned: two-character localized country names
        // ("日本", "中国") would otherwise masquerade as codes, suppress the
        // geocoder fallback, and misroute alerts to the wrong adapter.
        if (alphaOnly.length == 2 && alphaOnly.all { it in 'A'..'Z' || it in 'a'..'z' }) {
            val code = alphaOnly.uppercase(Locale.ROOT)
            if (code in Locale.getISOCountries()) return code
        }
        return countryNameToCode(trimmed)
    }

    private fun countryNameToCode(countryName: String): String? {
        val normalized = normalizeCountryName(countryName)
        COUNTRY_NAME_ALIASES[normalized]?.let { return it }
        return Locale.getISOCountries().firstOrNull { code ->
            val displayCountry = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.US)
            normalizeCountryName(displayCountry) == normalized
        }
    }

    private fun normalizeCountryName(value: String): String =
        value.lowercase(Locale.US)
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

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
