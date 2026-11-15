package com.sysadmindoc.nimbus.data.api

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] for the WMO Severe Weather Information Centre.
 *
 * [getAlertsForLocation] asks SWIC's GeoServer which alert areas contain the
 * point, plus the member's geometry-less alerts (parked on the country
 * centroid), so a Moscow user stops getting every warning Roshydromet issues
 * from Kaliningrad to Vladivostok (issue #58). Polygon matches come back with
 * `coversRequestedLocation = true`; everything else is country-scoped and the
 * repository narrows it by area name.
 *
 * When the GeoServer is unreachable it falls back to the country-wide
 * `json/wmo_all.json` feed joined against `json/wmo_member.json`.
 */
@Singleton
class WmoAlertAdapter @Inject constructor(
    private val api: WmoAlertApi,
) : AlertSourceAdapter {

    override val sourceId = "wmo"
    override val displayName = "WMO Severe Weather"
    override val supportedRegions = setOf("GLOBAL")

    @Volatile
    private var memberCache: List<WmoMember>? = null

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> =
        getAlertsForLocation(lat, lon, countryCode = null)

    /**
     * Alerts for the point. [countryCode] adds the member's geometry-less
     * alerts and enables the country-feed fallback; without it only polygon
     * matches can come back.
     */
    suspend fun getAlertsForLocation(
        lat: Double,
        lon: Double,
        countryCode: String?,
    ): Result<List<WeatherAlert>> {
        val members = try {
            getMembers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The point query still works without the member list; only the
            // sender names and the geometry-less rows depend on it.
            Log.w(TAG, "WMO member list unavailable: ${e.message}")
            emptyList()
        }
        val membersById = members.filter { it.mid.isNotBlank() }.associateBy { it.mid.orEmpty() }
        val memberIds = countryCode?.let { code -> memberIdsFor(members, code) }.orEmpty()

        return try {
            val rows = api.getFeatures(pointFilter(lat, lon, memberIds)).features.map { it.properties }
            Result.success(mapWfsRows(rows, membersById))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "WMO point query failed, using the country feed: ${e.message}")
            if (memberIds.isEmpty()) {
                Result.failure(e)
            } else {
                countryFeed(memberIds, membersById)
            }
        }
    }

    /** Country-wide alerts from `json/wmo_all.json`, with no point filtering. */
    suspend fun getAlertsForCountry(countryCode: String): Result<List<WeatherAlert>> {
        return try {
            val members = getMembers()
            val membersById = members.filter { it.mid.isNotBlank() }.associateBy { it.mid.orEmpty() }
            val memberIds = memberIdsFor(members, countryCode)
            if (memberIds.isEmpty()) Result.success(emptyList()) else countryFeed(memberIds, membersById)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 400) Result.success(emptyList()) else Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "WMO alert fetch failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun countryFeed(
        memberIds: Set<String>,
        membersById: Map<String, WmoMember>,
    ): Result<List<WeatherAlert>> {
        return try {
            val alerts = api.getWarnings().items
                .asSequence()
                .filter { it.mid in memberIds }
                .mapNotNull { warning -> mapToAlert(warning, membersById[warning.mid]) }
                .distinctBy { it.id }
                .toList()
            Result.success(alerts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 400) {
                Result.success(emptyList())
            } else {
                Log.w(TAG, "WMO alert fetch failed: HTTP ${e.code()}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WMO alert fetch failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun getMembers(): List<WmoMember> {
        memberCache?.let { return it }

        val members = api.getMembers().flatMap { it.members }
        memberCache = members
        return members
    }

    /**
     * Member IDs for [countryCode]. Exact name matches win: "China" must not
     * pull in "Hong Kong, China" and "Macao, China", nor "Niger" Nigeria,
     * "Ireland" the UK Met Office or "Sudan" South Sudan. Substring matching
     * only runs when no member name matches exactly ("United Kingdom" against
     * "United Kingdom of Great Britain and Northern Ireland").
     */
    internal fun memberIdsFor(members: List<WmoMember>, countryCode: String): Set<String> {
        val candidates = countryNameCandidates(countryCode)
            .map { it.normalizeForMatch() }
            .filter { it.isNotBlank() }
            .toSet()
        if (candidates.isEmpty()) return emptySet()

        val named = members.filter { it.mid.isNotBlank() && it.name.normalizeForMatch().isNotBlank() }
        val exact = named.filter { it.name.normalizeForMatch() in candidates }
        val matched = exact.ifEmpty {
            named.filter { member ->
                val memberName = member.name.normalizeForMatch()
                candidates.any { candidate -> memberName.contains(candidate) || candidate.contains(memberName) }
            }
        }
        return matched.mapNotNull { it.mid }.toSet()
    }

    private fun countryNameCandidates(countryCode: String): Set<String> =
        // SWIC names members in English. The device-language name is a bonus;
        // on a Russian phone "Россия" normalizes to nothing.
        memberCountryNames(countryCode) + runCatching {
            Locale.Builder().setRegion(countryCode.uppercase(Locale.ROOT)).build().displayCountry
        }.getOrDefault("")

    private fun mapWfsRows(rows: List<WmoWfsAlert>, membersById: Map<String, WmoMember>): List<WeatherAlert> {
        val fallbackShift = rows.mapNotNull { wfsClockShift(it) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: DEFAULT_WFS_CLOCK_SHIFT
        return rows
            .mapNotNull { row -> mapWfsRow(row, membersById[row.mem], wfsClockShift(row) ?: fallbackShift) }
            .distinctBy { it.id }
    }

    private fun mapWfsRow(row: WmoWfsAlert, member: WmoMember?, shift: Duration): WeatherAlert? {
        val event = row.event.takeUnlessBlank() ?: return null
        val capUrl = row.capurl.takeUnlessBlank() ?: return null
        val headline = row.headline.takeUnlessBlank() ?: event
        val excerpt = row.description.takeUnlessBlank()
            ?.takeUnless { it.trim().equals(NO_DESCRIPTION, ignoreCase = true) }
        val sent = shiftedInstant(row.sent, shift)
        return WeatherAlert(
            id = "$WFS_ID_PREFIX$capUrl",
            event = event,
            headline = headline,
            description = excerpt ?: headline,
            instruction = null,
            severity = severityFromCode(row.s),
            urgency = urgencyFromCode(row.u),
            certainty = certaintyFromCode(row.c),
            senderName = member?.dept.takeUnlessBlank() ?: member?.name.takeUnlessBlank() ?: "WMO SWIC",
            areaDescription = row.areadesc.takeUnlessBlank() ?: "",
            effective = shiftedInstant(row.effective, shift) ?: shiftedInstant(row.onset, shift) ?: sent,
            expires = shiftedInstant(row.expires, shift),
            response = null,
            coversRequestedLocation = if (row.rowType.equals(ROW_TYPE_POLYGON, ignoreCase = true)) true else null,
        )
    }

    private fun mapToAlert(warning: WmoWarning, member: WmoMember?): WeatherAlert? {
        val event = warning.event.takeUnlessBlank() ?: return null
        val id = warning.id.takeUnlessBlank() ?: warning.capURL.takeUnlessBlank() ?: return null
        val headline = warning.headline.takeUnlessBlank() ?: event

        return WeatherAlert(
            id = id,
            event = event,
            headline = headline,
            description = headline,
            instruction = null,
            severity = severityFromCode(warning.s),
            urgency = urgencyFromCode(warning.u),
            certainty = certaintyFromCode(warning.c),
            senderName = member?.dept.takeUnlessBlank() ?: member?.name.takeUnlessBlank() ?: "WMO SWIC",
            areaDescription = warning.areaDesc.takeUnlessBlank() ?: "",
            effective = utcFeedTime(warning.effective.takeUnlessBlank() ?: warning.sent),
            expires = utcFeedTime(warning.expires),
            response = null,
        )
    }

    private fun severityFromCode(code: Int?): AlertSeverity {
        return when (code) {
            4 -> AlertSeverity.EXTREME
            3 -> AlertSeverity.SEVERE
            2 -> AlertSeverity.MODERATE
            1 -> AlertSeverity.MINOR
            else -> AlertSeverity.UNKNOWN
        }
    }

    private fun urgencyFromCode(code: Int?): AlertUrgency {
        return when (code) {
            4 -> AlertUrgency.IMMEDIATE
            3 -> AlertUrgency.EXPECTED
            2 -> AlertUrgency.FUTURE
            1 -> AlertUrgency.PAST
            else -> AlertUrgency.UNKNOWN
        }
    }

    private fun certaintyFromCode(code: Int?): String {
        return when (code) {
            4 -> "Observed"
            3 -> "Likely"
            2 -> "Possible"
            1 -> "Unlikely"
            else -> "Unknown"
        }
    }

    companion object {
        private const val TAG = "WmoAlertAdapter"
        private const val ROW_TYPE_POLYGON = "POLYGON"
        private const val NO_DESCRIPTION = "No description"
        private const val WFS_ID_PREFIX = "wmo:"
        private val COMBINING_MARKS = "\\p{Mn}+".toRegex()
        private val NON_WORDS = "[^a-z0-9]+".toRegex()

        /** `<source>/YYYY/MM/DD/HH/MM/SS-<hash>.xml`: the CAP send time in true UTC. */
        private val CAP_PATH_TIME = "/(\\d{4})/(\\d{2})/(\\d{2})/(\\d{2})/(\\d{2})/(\\d{2})-".toRegex()

        /**
         * The GeoServer's timestamps run 8 hours behind the CAP send time in
         * each row's path (Chinese, Kazakh and Russian rows on 2026-11-14). The
         * shift is read off each row's path; this default covers a response
         * where no row has a dated one. Too small a shift would expire a live
         * warning early, so the default is the observed offset.
         */
        private val DEFAULT_WFS_CLOCK_SHIFT: Duration = Duration.ofHours(8)
        private val MAX_WFS_CLOCK_SHIFT: Duration = Duration.ofHours(14)
        private const val SHIFT_ROUNDING_MINUTES = 15L

        private val COUNTRY_ALIASES = mapOf(
            "AE" to setOf("United Arab Emirates"),
            "BO" to setOf("Bolivia", "Plurinational State of Bolivia"),
            "BN" to setOf("Brunei", "Brunei Darussalam"),
            "CD" to setOf("Democratic Republic of the Congo"),
            "CG" to setOf("Congo", "Republic of the Congo"),
            "CI" to setOf("Cote d'Ivoire", "Côte d'Ivoire", "Ivory Coast"),
            "CV" to setOf("Cabo Verde", "Cape Verde"),
            "CZ" to setOf("Czechia", "Czech Republic"),
            "GB" to setOf("United Kingdom", "Great Britain"),
            "HK" to setOf("Hong Kong, China", "Hong Kong"),
            "IR" to setOf("Iran", "Islamic Republic of Iran"),
            "KR" to setOf("Republic of Korea", "South Korea", "Korea"),
            "LA" to setOf("Lao People's Democratic Republic", "Laos"),
            "MD" to setOf("Moldova", "Republic of Moldova"),
            "MK" to setOf("North Macedonia", "Macedonia"),
            "MM" to setOf("Myanmar", "Burma"),
            "MO" to setOf("Macao, China", "Macao", "Macau"),
            "RU" to setOf("Russian Federation", "Russia"),
            "SY" to setOf("Syrian Arab Republic", "Syria"),
            "SZ" to setOf("Eswatini", "Swaziland"),
            "TL" to setOf("Timor-Leste", "Timor Leste"),
            "TR" to setOf("Türkiye", "Turkiye", "Turkey"),
            "TZ" to setOf("Tanzania", "United Republic of Tanzania"),
            "US" to setOf("United States", "United States of America", "USA"),
            "VE" to setOf("Venezuela", "Bolivarian Republic of Venezuela"),
            "VN" to setOf("Viet Nam", "Vietnam"),
        )

        /**
         * English names SWIC may use for [countryCode]: the `Locale.US` display
         * name plus the member-list spellings ("Russian Federation", "Viet Nam").
         * The repository also matches these against area text, since a
         * nationwide warning often names only the country.
         */
        internal fun memberCountryNames(countryCode: String): Set<String> {
            val code = countryCode.trim().uppercase(Locale.ROOT)
            if (code.isEmpty()) return emptySet()
            val display = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.US) }
                .getOrNull()
                ?.takeUnless { it.isBlank() || it.equals(code, ignoreCase = true) }
            return buildSet {
                display?.let(::add)
                COUNTRY_ALIASES[code]?.let(::addAll)
            }
        }

        /**
         * OGC Filter 1.0: rows whose area contains the point, plus the
         * member's geometry-less rows (`row_type` POINT, parked on the
         * country centroid). `mem` 0 rows are SWIC's own test layer.
         */
        internal fun pointFilter(lat: Double, lon: Double, memberIds: Set<String>): String {
            val point = String.format(Locale.US, "%.5f,%.5f", lon, lat)
            val intersects = "<Intersects><PropertyName>wkb_geometry</PropertyName>" +
                "<gml:Point><gml:coordinates>$point</gml:coordinates></gml:Point></Intersects>"
            val ids = memberIds.sorted()
            val memberClauses = ids.joinToString("") { id ->
                "<PropertyIsEqualTo><PropertyName>mem</PropertyName><Literal>${xmlEscape(id)}</Literal></PropertyIsEqualTo>"
            }
            val match = if (ids.isEmpty()) {
                intersects
            } else {
                val members = if (ids.size == 1) memberClauses else "<Or>$memberClauses</Or>"
                "<Or>$intersects<And><PropertyIsEqualTo><PropertyName>row_type</PropertyName>" +
                    "<Literal>POINT</Literal></PropertyIsEqualTo>$members</And></Or>"
            }
            return "<Filter xmlns=\"http://www.opengis.net/ogc\" xmlns:gml=\"http://www.opengis.net/gml\"><And>" +
                "<PropertyIsNotEqualTo><PropertyName>mem</PropertyName><Literal>0</Literal></PropertyIsNotEqualTo>" +
                "$match</And></Filter>"
        }

        /** True UTC minus the row's labelled `sent`, from its CAP path, rounded to 15 minutes. */
        internal fun wfsClockShift(row: WmoWfsAlert): Duration? {
            val sent = parseInstant(row.sent) ?: return null
            val match = CAP_PATH_TIME.find(row.capurl.orEmpty()) ?: return null
            val (year, month, day, hour, minute, second) = match.destructured
            val pathTime = try {
                LocalDateTime.of(
                    year.toInt(), month.toInt(), day.toInt(),
                    hour.toInt(), minute.toInt(), second.toInt(),
                ).toInstant(ZoneOffset.UTC)
            } catch (ignored: java.time.DateTimeException) {
                // An impossible date in the path (month 13) means no usable stamp.
                return null
            }
            val raw = Duration.between(sent, pathTime)
            if (raw.abs() > MAX_WFS_CLOCK_SHIFT) return null
            val quarters = Math.round(raw.toMinutes().toDouble() / SHIFT_ROUNDING_MINUTES)
            return Duration.ofMinutes(quarters * SHIFT_ROUNDING_MINUTES)
        }

        private fun shiftedInstant(value: String?, shift: Duration): String? =
            parseInstant(value)?.plus(shift)?.toString()

        private fun parseInstant(value: String?): Instant? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return try {
                OffsetDateTime.parse(text).toInstant()
            } catch (ignored: DateTimeParseException) {
                // Not offset-qualified; try the bare Instant form before giving up.
                try {
                    Instant.parse(text)
                } catch (alsoIgnored: DateTimeParseException) {
                    // Unparseable stamps stay null so the alert fails open.
                    null
                }
            }
        }

        /**
         * `wmo_all.json` writes `2026-11-15 02:09:00` with no zone, and the
         * values are UTC. Left bare, the app read them as device-local time,
         * so a user at UTC-5 saw every expiry five hours early.
         */
        internal fun utcFeedTime(value: String?): String? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return value
            return try {
                LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toString()
            } catch (ignored: DateTimeParseException) {
                // Already zoned (or unknown): hand it through for parseAlertInstant.
                text
            }
        }

        private fun xmlEscape(value: String): String =
            value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        private fun String?.isNotBlank(): Boolean = !this.isNullOrBlank()

        private fun String?.takeUnlessBlank(): String? = this?.takeUnless { it.isBlank() }

        private fun String?.normalizeForMatch(): String {
            if (this.isNullOrBlank()) return ""
            return Normalizer.normalize(this, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase(Locale.ROOT)
                .replace(NON_WORDS, " ")
                .trim()
        }
    }
}
