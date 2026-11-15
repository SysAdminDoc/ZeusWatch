package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.api.WmoAlertAdapter
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.util.isAlertExpired
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
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

        /** Same bound ReverseGeocoder puts on the platform geocoder. */
        private const val GEOCODE_TIMEOUT_MS = 5_000L

        /** Shortest normalized place name the WMO area filter will match on. */
        private const val MIN_AREA_NAME_CHARS = 3

        /** Shortest normalized area-description segment the WMO area filter will judge. */
        private const val MIN_AREA_SEGMENT_CHARS = 4

        private val COMBINING_MARKS = "\\p{Mn}+".toRegex()

        /** Anything but a letter or digit in any script, so Cyrillic names survive. */
        private val AREA_NON_WORDS = "[^\\p{L}\\p{N}]+".toRegex()

        /**
         * Words that only say what kind of area a name is. The geocoder writes
         * "Volgograd Oblast" where Roshydromet writes "Volgograd Region", so
         * both sides drop these before comparing.
         */
        private val ADMIN_WORDS = setOf(
            "autonomous", "city", "county", "department", "district", "governorate", "krai", "kray",
            "municipality", "oblast", "of", "okrug", "prefecture", "province", "raion", "rayon",
            "region", "republic", "state", "territory", "the", "town",
        )

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
     * that source as failed; MeteoAlarm's country-aware skip (no or
     * out-of-region country) is recorded as not-attempted so it never counts
     * toward an outage.
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
        val hint = normalizeCountryHint(countryHint)
        // A hint settles the country without the geocoder. The WMO area filter
        // geocodes on its own, and only when it has rows to judge.
        val geocoded = if (hint == null) geocodedLocation(latitude, longitude) else null
        val countryCode = hint ?: geocoded?.countryCode ?: detectCountryFromTimezone()

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
                            // SWIC answers for the point (issue #58). Without a
                            // country only polygon matches can come back.
                            val outcome = queryAdapter(adapter) {
                                adapter.getAlertsForLocation(latitude, longitude, countryCode)
                            }
                            if (outcome.alerts.all { it.coversRequestedLocation == true }) {
                                outcome
                            } else {
                                val areaNames = (geocoded ?: geocodedLocation(latitude, longitude)).areaNames
                                outcome.copy(alerts = filterAreaRelevant(outcome.alerts, areaNames, countryCode))
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

    /**
     * Merge, drop expired, dedup by ID, and sort by severity then urgency.
     *
     * The expiry filter belongs here, not at a call site: every consumer of an
     * alert list — the Today banner, the radar polygon layer, the widgets, the
     * notification worker — wants the same answer, and only the worker used to
     * ask for it. A country-wide feed such as WMO SWIC keeps items long past
     * their `expires`, which is how the banner ended up rendering pages of
     * warnings stamped "Expired" (issue #55).
     *
     * `isAlertExpired` fails open: a null or unparseable `expires` is treated as
     * still active, so a provider that omits the field never has its alerts
     * silently swallowed.
     */
    private fun mergeAlerts(outcomes: List<AdapterOutcome>): List<WeatherAlert> =
        outcomes
            .flatMap { it.alerts }
            .filterNot { isAlertExpired(it.expires) }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<WeatherAlert> { it.severity.sortOrder }
                    .thenBy { it.urgency.sortOrder }
            )

    /**
     * Drop WMO warnings whose area text names somewhere else (issue #58).
     *
     * Polygon matches already cover the point and pass untouched. What's left
     * is country-scoped: members that publish no geometry (China's CMA sends a
     * Beijing user dense-fog rows for Nenjiang and Wuwei) and the country-feed
     * fallback, which for Russia spans Kaliningrad to Vladivostok. Those keep
     * only area segments that name a place at the point (the reverse-geocoded
     * admin area, district, city or neighborhood) or the country itself.
     *
     * Fails open like [isAlertExpired]: no geocoded names, blank or
     * too-short area text, and area text in a script none of the names use
     * all keep the alert.
     */
    internal fun filterAreaRelevant(
        alerts: List<WeatherAlert>,
        areaNames: List<String>,
        countryCode: String?,
    ): List<WeatherAlert> {
        val placeNeedles = areaNames.map(::placeKey).filter { it.length >= MIN_AREA_NAME_CHARS }
        if (placeNeedles.isEmpty()) return alerts

        val countryNeedles = countryCode?.let { WmoAlertAdapter.memberCountryNames(it) }.orEmpty()
            .map(::placeKey)
            .filter { it.length >= MIN_AREA_NAME_CHARS }
        val needles = (placeNeedles + countryNeedles).distinct()
        val needleScripts = needles.flatMap(::letterScripts).toSet()

        return alerts.filter { alert ->
            if (alert.coversRequestedLocation == true) return@filter true
            val segments = alert.areaDescription.split(',', ';', '(', ')', '/')
                .map(::placeKey)
                .filter { it.length >= MIN_AREA_SEGMENT_CHARS }
            when {
                segments.isEmpty() -> true
                segments.none { segment -> letterScripts(segment).any { it in needleScripts } } -> true
                else -> segments.any { segment -> needles.any { needle -> " $segment ".contains(" $needle ") } }
            }
        }
    }

    /** "São Paulo State" → "sao paulo": diacritics folded, admin words dropped. */
    private fun placeKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .split(AREA_NON_WORDS)
            .filter { it.isNotEmpty() && it !in ADMIN_WORDS }
            .joinToString(" ")

    private fun letterScripts(value: String): Set<Character.UnicodeScript> =
        value.filter { it.isLetter() }.map { Character.UnicodeScript.of(it.code) }.toSet()

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

    /** How long the API 33+ geocoder listener gets before the check moves on. */
    @VisibleForTesting
    internal var geocodeTimeoutMs = GEOCODE_TIMEOUT_MS

    /** A listener answer, so a timed-out wait (null) differs from an answer with no address. */
    private class GeocodeAnswer(val address: Address?)

    /** Reverse-geocoding result: country code plus the point's place names. */
    private data class GeocodedLocation(
        val countryCode: String?,
        val areaNames: List<String>,
    )

    private suspend fun geocodedLocation(lat: Double, lon: Double): GeocodedLocation {
        return withContext(Dispatchers.IO) {
            try {
                geocodeLocationContext(lat, lon)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Geocoder location detection failed: ${e.message}")
                GeocodedLocation(null, emptyList())
            }
        }
    }

    private suspend fun geocodeLocationContext(lat: Double, lon: Double): GeocodedLocation {
        if (!Geocoder.isPresent()) return GeocodedLocation(null, emptyList())

        val geocoder = Geocoder(context, Locale.US)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The listener may never fire when the geocoder backend is
            // unreachable, so the wait is bounded like ReverseGeocoder's. A
            // silent geocoder reads as no address: the country comes from the
            // timezone guess and the WMO area filter gets no names to judge.
            val answer = withTimeoutOrNull(geocodeTimeoutMs) {
                GeocodeAnswer(suspendCancellableCoroutine<Address?> { continuation ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) {
                                continuation.resume(addresses.firstOrNull())
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    })
                })
            }
            if (answer == null) Log.w(TAG, "Geocoder gave no answer within $geocodeTimeoutMs ms")
            answer?.address
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
        }
        return GeocodedLocation(
            countryCode = address?.countryCode?.uppercase(),
            areaNames = address?.let(::addressAreaNames).orEmpty(),
        )
    }

    /**
     * Place names at the point, broadest first, for the WMO area filter. Read
     * one at a time: some geocoders return sparse addresses, and a missing
     * name only costs filter precision, never the country detection sharing
     * this call.
     */
    private fun addressAreaNames(address: Address): List<String> =
        listOfNotNull(
            safeAddressPart { address.adminArea },
            safeAddressPart { address.subAdminArea },
            safeAddressPart { address.locality },
            safeAddressPart { address.subLocality },
        ).distinct()

    private inline fun safeAddressPart(part: () -> String?): String? = try {
        part()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        // A sparse address only costs filter precision.
        Log.d(TAG, "Address part unavailable: ${e.message}")
        null
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
