package com.notificationlogger.util

import android.content.Context
import android.util.Log
import androidx.work.*
import com.notificationlogger.data.repository.NotificationRepository
import java.util.concurrent.TimeUnit

class RetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "RetentionWorker"
        private const val WORK_TAG = "retention_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = PrefsHelper(applicationContext)
            val retentionDays = prefs.getRetentionDays()
            val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
            val repo = NotificationRepository(applicationContext)
            val deleted = repo.countOlderThan(cutoff)
            repo.deleteOlderThan(cutoff)
            Log.i(TAG, "Retention cleanup: deleted $deleted records older than $retentionDays days")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Retention cleanup failed", e)
            Result.retry()
        }
    }
}
