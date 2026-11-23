package app.overbay.swiftfox.features.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Allows [app.overbay.swiftfox.SwiftfoxApp] to supply the shared [FilterListRepository]
 * singleton to [FilterListUpdateWorker] without creating a second [AdblockEngine] instance.
 */
object FilterListWorkerBridge {
    @Volatile
    var onRunUpdate: (suspend () -> kotlin.Result<Int>)? = null
}

class FilterListUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bridgedRunner = FilterListWorkerBridge.onRunUpdate
        if (bridgedRunner != null) {
            return bridgedRunner.invoke().fold(
                onSuccess = { Result.success() },
                onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() }
            )
        }

        val engine = AdblockEngine(applicationContext)
        return try {
            try {
                val db = SwiftfoxDatabase.getInstance(applicationContext)
                val repo = FilterListRepository(applicationContext, db.filterListDao(), engine)
                repo.updateAllEnabled()
                Result.success()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } finally {
            // Release native Rust engine handle so background worker runs never leak native heap
            engine.close()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "swiftfox_filter_list_update"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<FilterListUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
