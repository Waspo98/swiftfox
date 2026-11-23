package app.overbay.swiftfox.features.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object SyncWorkerBridge {
    @Volatile
    var onRunSync: (suspend () -> Result<String>)? = null
}

class SwiftfoxSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val runner = SyncWorkerBridge.onRunSync ?: return Result.success()
        return runner.invoke().fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 2) Result.retry() else Result.failure() }
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "swiftfox_mozilla_sync"

        fun schedulePeriodic(context: Context, intervalMinutes: Int) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (intervalMinutes <= 0) {
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }

            val repeatMinutes = intervalMinutes.coerceAtLeast(15).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SwiftfoxSyncWorker>(repeatMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
