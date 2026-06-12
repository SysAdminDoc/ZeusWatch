package com.sysadmindoc.nimbus.data.api

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] for the WMO Severe Weather Information Centre.
 *
 * The live SWIC feed is country/member scoped, not coordinate scoped:
 * `json/wmo_all.json` has alert items with WMO member IDs and no geometry.
 * [getAlertsForCountry] joins those IDs against `json/wmo_member.json` and is
 * used by [com.sysadmindoc.nimbus.data.repository.AlertRepository] after
 * country detection.
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

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        // SWIC no longer publishes per-alert geometry in the JSON feed. A raw
        // coordinate query would either miss everything or show global noise.
        return Result.success(emptyList())
    }

    suspend fun getAlertsForCountry(countryCode: String): Result<List<WeatherAlert>> {
        return try {
            val members = getMembers()
            val membersById = members
                .filter { it.mid.isNotBlank() }
                .associateBy { it.mid.orEmpty() }
            val memberIds = members
                .filter { it.matchesCountry(countryCode) }
                .mapNotNull { it.mid }
                .toSet()

            if (memberIds.isEmpty()) {
                return Result.success(emptyList())
            }

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
            effective = warning.effective.takeUnlessBlank() ?: warning.sent,
            expires = warning.expires,
            response = null,
        )
    }

    private fun WmoMember.matchesCountry(countryCode: String): Boolean {
        val memberName = name.normalizeForMatch()
        if (memberName.isBlank()) return false

        val candidates = countryNameCandidates(countryCode)
            .map { it.normalizeForMatch() }
            .filter { it.isNotBlank() }

        return candidates.any { candidate ->
            memberName == candidate ||
                memberName.contains(candidate) ||
                candidate.contains(memberName)
        }
    }

    private fun countryNameCandidates(countryCode: String): Set<String> {
        val normalizedCode = countryCode.uppercase(Locale.ROOT)
        val locale = Locale.Builder().setRegion(normalizedCode).build()
        return buildSet {
            add(locale.displayCountry)
            COUNTRY_ALIASES[normalizedCode]?.let(::addAll)
        }
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
        private val COMBINING_MARKS = "\\p{Mn}+".toRegex()
        private val NON_WORDS = "[^a-z0-9]+".toRegex()

        private val COUNTRY_ALIASES = mapOf(
            "AE" to setOf("United Arab Emirates"),
            "BO" to setOf("Bolivia", "Plurinational State of Bolivia"),
            "BN" to setOf("Brunei", "Brunei Darussalam"),
            "CD" to setOf("Democratic Republic of the Congo"),
            "CG" to setOf("Congo", "Republic of the Congo"),
            "CI" to setOf("Cote d'Ivoire", "Côte d'Ivoire", "Ivory Coast"),
            "CV" to setOf("Cabo Verde", "Cape Verde"),
            "CZ" to setOf("Czechia", "Czech Republic"),
            "GB" to setOf("United Kingdom", "Great Britain", "Northern Ireland"),
            "IR" to setOf("Iran", "Islamic Republic of Iran"),
            "KR" to setOf("Republic of Korea", "South Korea", "Korea"),
            "LA" to setOf("Lao People's Democratic Republic", "Laos"),
            "MD" to setOf("Moldova", "Republic of Moldova"),
            "MK" to setOf("North Macedonia", "Macedonia"),
            "MM" to setOf("Myanmar", "Burma"),
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
