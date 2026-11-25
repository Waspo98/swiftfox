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
        val appClient = (applicationContext as? app.overbay.swiftfox.SwiftfoxApp)?.let { app -> { app.httpClient } }
        var tempClient: okhttp3.OkHttpClient? = null
        val clientProvider: () -> okhttp3.OkHttpClient = appClient ?: {
            tempClient ?: okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
                .also { tempClient = it }
        }
        return try {
            try {
                val db = SwiftfoxDatabase.getInstance(applicationContext)
                val repo = FilterListRepository(applicationContext, db.filterListDao(), engine, clientProvider)
                repo.updateAllEnabled()
                Result.success()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } finally {
            // Release native Rust engine handle and any standalone OkHttp pool
            engine.close()
            tempClient?.let { created ->
                runCatching { created.dispatcher.executorService.shutdown() }
                runCatching { created.connectionPool.evictAll() }
            }
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
