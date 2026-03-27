package com.sysadmindoc.nimbus.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sysadmindoc.nimbus.data.model.CommunityReport
import com.sysadmindoc.nimbus.data.model.ReportCondition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

/**
 * Repository for submitting and querying community weather reports via Firebase Firestore.
 *
 * IMPORTANT: This requires a valid google-services.json in the app/ directory.
 * Configure a Firebase project at https://console.firebase.google.com and download the config.
 */
@Singleton
class CommunityReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CommunityReportSource {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val collection get() = firestore.collection(COLLECTION_NAME)

    /** Hashed device ID for anonymous attribution (no user accounts needed). */
    val deviceId: String by lazy { hashDeviceId(context) }

    /** Timestamp of last successful submission for local rate limiting. */
    @Volatile
    private var lastSubmitTimestamp: Long = 0L

    /**
     * Submit a community weather report.
     * Rate limited to 1 report per device per 5 minutes (enforced locally).
     */
    suspend fun submitReport(report: CommunityReport): Result<String> {
        // Local rate limit: 1 report per 5 minutes
        val now = System.currentTimeMillis()
        if (now - lastSubmitTimestamp < RATE_LIMIT_MS) {
            val remainingSec = (RATE_LIMIT_MS - (now - lastSubmitTimestamp)) / 1000
            return Result.failure(
                IllegalStateException("Please wait ${remainingSec}s before submitting another report.")
            )
        }

        return try {
            val docRef = collection.document()
            val reportWithId = report.copy(
                id = docRef.id,
                deviceId = deviceId,
                timestamp = now,
            )
            val data = reportToMap(reportWithId)
            docRef.set(data).await()
            lastSubmitTimestamp = now
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit report", e)
            Result.failure(e)
        }
    }

    /**
     * Query community reports near a location within the last 2 hours.
     * Uses a bounding box approximation since Firestore lacks native geo-queries.
     */
    suspend fun getReportsNearby(
        lat: Double,
        lon: Double,
        radiusKm: Double = 50.0,
    ): Result<List<CommunityReport>> {
        return try {
            val twoHoursAgo = System.currentTimeMillis() - TWO_HOURS_MS

            // Bounding box approximation: 1 degree latitude ~ 111 km
            val latDelta = radiusKm / 111.0
            // Longitude degrees vary by latitude
            val lonDelta = radiusKm / (111.0 * cos(Math.toRadians(lat)))

            val snapshot = collection
                .whereGreaterThan("latitude", lat - latDelta)
                .whereLessThan("latitude", lat + latDelta)
                .whereGreaterThan("timestamp", twoHoursAgo)
                .orderBy("latitude", Query.Direction.ASCENDING)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(MAX_RESULTS)
                .get()
                .await()

            val reports = snapshot.documents.mapNotNull { doc ->
                try {
                    mapToReport(doc.id, doc.data ?: return@mapNotNull null)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed report ${doc.id}", e)
                    null
                }
            }.filter { report ->
                // Post-filter longitude since Firestore compound queries are limited
                report.longitude in (lon - lonDelta)..(lon + lonDelta)
            }

            Result.success(reports)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch nearby reports", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a report by ID. Only allows deletion if the report was submitted
     * by this device (matched by deviceId).
     */
    suspend fun deleteReport(id: String): Result<Unit> {
        return try {
            val doc = collection.document(id).get().await()
            val reportDeviceId = doc.getString("deviceId") ?: ""
            if (reportDeviceId != deviceId) {
                return Result.failure(SecurityException("Cannot delete another device's report."))
            }
            collection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete report $id", e)
            Result.failure(e)
        }
    }

    // --- Mapping helpers ---

    private fun reportToMap(report: CommunityReport): Map<String, Any> = mapOf(
        "latitude" to report.latitude,
        "longitude" to report.longitude,
        "condition" to report.condition.name,
        "note" to report.note,
        "timestamp" to report.timestamp,
        "deviceId" to report.deviceId,
    )

    private fun mapToReport(id: String, data: Map<String, Any>): CommunityReport {
        return CommunityReport(
            id = id,
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            condition = try {
                ReportCondition.valueOf(data["condition"] as? String ?: "SUNNY")
            } catch (_: IllegalArgumentException) {
                ReportCondition.SUNNY
            },
            note = (data["note"] as? String) ?: "",
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
            deviceId = (data["deviceId"] as? String) ?: "",
        )
    }

    // --- Device ID hashing ---

    @SuppressLint("HardwareIds")
    private fun hashDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(androidId.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "CommunityReportRepo"
        private const val COLLECTION_NAME = "community_reports"
        private const val RATE_LIMIT_MS = 5 * 60 * 1000L // 5 minutes
        private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
        private const val MAX_RESULTS = 200L
    }
}
