package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.api.NimbusDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "DbMaintenance"

/**
 * Periodic background worker that checkpoints and truncates the Room WAL file.
 *
 * Room uses WAL mode by default. On long-running devices that accumulate and
 * evict cache entries the WAL file can grow without bound because Room never
 * issues a checkpoint on its own. PRAGMA wal_checkpoint(TRUNCATE) checkpoints
 * all frames into the main database file and then zeroes the WAL back to its
 * minimum size, reclaiming disk space.
 *
 * Scheduled weekly; requires no network and no user-facing settings toggle.
 */
@HiltWorker
class DatabaseMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: NimbusDatabase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }
            Log.d(TAG, "WAL checkpoint completed")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "WAL checkpoint failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "nimbus_db_maintenance"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(7, TimeUnit.DAYS).build(),
            )
        }
    }
}
