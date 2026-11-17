package uk.co.meditationtimer

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WellnessSync {
    private val scheduling = java.util.concurrent.Executors.newSingleThreadExecutor()
    private const val DRAIN = "wellness-sync-drain"
    private const val PERIODIC = "wellness-sync-recovery"
    private const val TEST = "wellness-sync-test"

    private fun constraints() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun maintainSafely(context: Context) {
        val application = context.applicationContext
        try {
            scheduling.execute {
                try {
                    maintain(application)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun maintain(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!WellnessSettings(context).configuration().enabled) {
            manager.cancelUniqueWork(DRAIN)
            manager.cancelUniqueWork(PERIODIC)
            return
        }
        val periodic =
            PeriodicWorkRequest.Builder(WellnessSyncWorker::class.java, 15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        enqueue(context, false)
    }

    fun enqueue(context: Context, test: Boolean) {
        val request =
            OneTimeWorkRequest.Builder(WellnessSyncWorker::class.java)
                .setConstraints(constraints())
                .setInputData(Data.Builder().putBoolean("test", test).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(if (test) TEST else DRAIN, ExistingWorkPolicy.KEEP, request)
    }

    fun requestSafely(context: Context) {
        // WorkManager initialization can touch SQLite. Even that optional setup stays off
        // timer completion.
        val application = context.applicationContext
        try {
            if (WellnessSettings(application).configuration().enabled)
                scheduling.execute {
                    try {
                        enqueue(application, false)
                    } catch (_: Exception) {}
                }
        } catch (_: Exception) {
            /* Periodic work recovers a crash between capture and enqueue. */
        }
    }
}
