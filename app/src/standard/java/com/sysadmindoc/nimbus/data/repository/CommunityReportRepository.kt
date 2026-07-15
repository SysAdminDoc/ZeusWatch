package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sysadmindoc.nimbus.data.model.CommunityReport
import com.sysadmindoc.nimbus.data.model.ReportCondition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for submitting and querying community weather reports via Firebase Firestore.
 *
 * Identity model: writes require an anonymous Firebase Auth account (no personal
 * accounts, no hardware identifiers), but reports are stored WITHOUT an owner id
 * so readers cannot correlate one account's reports into a movement history. The
 * per-account write-rate limit lives entirely in the `report_throttles/{uid}` doc,
 * which is bumped to the commit time in the same atomic batch as the report create.
 *
 * IMPORTANT: This requires a valid google-services.json in the app/ directory AND
 * Anonymous Authentication enabled in the Firebase console
 * (Authentication -> Sign-in method -> Anonymous). The standard flavor signs in
 * anonymously on first use; the freenet flavor never touches Firebase.
 */
@Singleton
class CommunityReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CommunityReportSource {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val collection get() = firestore.collection(COLLECTION_NAME)
    private val throttles get() = firestore.collection(THROTTLE_COLLECTION_NAME)

    /** Anonymous Firebase Auth uid once signed in, empty otherwise. Not a hardware identifier. */
    override val deviceId: String
        get() = auth.currentUser?.uid ?: ""

    /** Timestamp of last successful submission for local (UX) rate limiting. */
    @Volatile
    private var lastSubmitTimestamp: Long = 0L

    /**
     * Submit a community weather report.
     *
     * Rate limited to 1 report per account per 5 minutes. The client-side check
     * below is only a fast-fail UX nicety; the authoritative limit is enforced
     * server-side by the Firestore rules via the throttle ledger doc.
     */
    override suspend fun submitReport(report: CommunityReport): Result<String> {
        val now = System.currentTimeMillis()
        if (now - lastSubmitTimestamp < RATE_LIMIT_MS) {
            val remainingSec = (RATE_LIMIT_MS - (now - lastSubmitTimestamp)) / 1000
            return Result.failure(
                IllegalStateException("Please wait ${remainingSec}s before submitting another report.")
            )
        }

        return try {
            val uid = ensureSignedIn()
            val docRef = collection.document()
            val location = CommunityReportPrivacy.coarsenLocation(
                latitude = report.latitude,
                longitude = report.longitude,
            )
            val reportWithId = report.copy(
                id = docRef.id,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = now,
                expiresAt = CommunityReportPrivacy.expiresAt(now),
                geohash = CommunityReportGeo.geohash(location.latitude, location.longitude),
            )

            // Atomic batch: the report create + the throttle bump must land in the
            // same commit so the server-side rate-limit rule can inspect both.
            val batch = firestore.batch()
            batch.set(docRef, reportToMap(reportWithId))
            batch.set(
                throttles.document(uid),
                mapOf(THROTTLE_FIELD to FieldValue.serverTimestamp()),
            )
            batch.commit().await()

            lastSubmitTimestamp = now
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.w(TAG, "Community report submission unavailable: ${e.safeLogMessage()}")
            Result.failure(e)
        }
    }

    /**
     * Query community reports near a location within the last 2 hours.
     * Uses geohash prefix ranges plus exact-distance filtering so dense reports
     * elsewhere on the same latitude cannot starve nearby longitude matches.
     */
    override suspend fun getReportsNearby(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ): Result<List<CommunityReport>> {
        return try {
            ensureSignedIn()
            val queryTime = System.currentTimeMillis()
            val twoHoursAgo = queryTime - CommunityReportPrivacy.RETENTION_MS
            val reports = coroutineScope {
                CommunityReportGeo.queryBounds(lat, lon, radiusKm).map { bound ->
                    async {
                        collection
                            .whereGreaterThanOrEqualTo("geohash", bound.start)
                            .whereLessThan("geohash", bound.end)
                            .whereGreaterThan("timestamp", twoHoursAgo)
                            .orderBy("geohash", Query.Direction.ASCENDING)
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .limit(MAX_RESULTS_PER_GEOHASH_QUERY)
                            .get()
                            .await()
                            .documents
                            .mapNotNull { doc -> mapToReportOrNull(doc.id, doc.data) }
                    }
                }.awaitAll().flatten()
            }

            Result.success(
                CommunityReportGeo.sortNearby(
                    reports = CommunityReportPrivacy.visibleReports(reports, queryTime),
                    latitude = lat,
                    longitude = lon,
                    radiusKm = radiusKm,
                    maxResults = MAX_RESULTS,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Community report lookup unavailable: ${e.safeLogMessage()}")
            Result.failure(e)
        }
    }

    /**
     * Deletion is not supported: the community-report collection is append-only
     * under the anonymous model (Firestore rules hard-deny delete). Kept to honor
     * the [CommunityReportSource] contract.
     */
    override suspend fun deleteReport(id: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Community reports are append-only and cannot be deleted.")
        )

    // --- Auth ---

    /** Ensure an anonymous session exists and return its uid. */
    private suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user?.uid
            ?: throw IllegalStateException("Anonymous sign-in returned no user.")
    }

    // --- Mapping helpers ---

    private fun reportToMap(report: CommunityReport): Map<String, Any> {
        val location = CommunityReportPrivacy.coarsenLocation(report.latitude, report.longitude)
        return mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "geohash" to CommunityReportGeo.geohash(location.latitude, location.longitude),
            "condition" to report.condition.name,
            "note" to report.note.trim().take(100),
            "timestamp" to report.timestamp,
            "expiresAt" to Timestamp(Date(CommunityReportPrivacy.expiresAt(report.timestamp))),
        )
    }

    private fun mapToReportOrNull(id: String, data: Map<String, Any>?): CommunityReport? {
        return try {
            mapToReport(id, data ?: return null)
        } catch (e: Exception) {
            Log.w(TAG, "Skipping malformed report $id", e)
            null
        }
    }

    private fun mapToReport(id: String, data: Map<String, Any>): CommunityReport {
        val timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
        return CommunityReport(
            id = id,
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            geohash = (data["geohash"] as? String) ?: "",
            condition = try {
                ReportCondition.valueOf(data["condition"] as? String ?: "SUNNY")
            } catch (_: IllegalArgumentException) {
                ReportCondition.SUNNY
            },
            note = (data["note"] as? String) ?: "",
            timestamp = timestamp,
            expiresAt = when (val expiresAt = data["expiresAt"]) {
                is Timestamp -> expiresAt.toDate().time
                is Date -> expiresAt.time
                else -> CommunityReportPrivacy.expiresAt(timestamp)
            },
        )
    }

    companion object {
        private const val TAG = "CommunityReportRepo"
        private const val COLLECTION_NAME = "community_reports"
        private const val THROTTLE_COLLECTION_NAME = "report_throttles"
        private const val THROTTLE_FIELD = "lastReportAt"
        private const val RATE_LIMIT_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_RESULTS = 200
        private const val MAX_RESULTS_PER_GEOHASH_QUERY = 50L
    }
}

private fun Throwable.safeLogMessage(): String =
    message?.lineSequence()?.firstOrNull()?.take(180) ?: javaClass.simpleName
