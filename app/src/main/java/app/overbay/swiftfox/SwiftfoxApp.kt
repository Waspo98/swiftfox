package app.overbay.swiftfox

import android.app.Application
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.prefs.PreferencesRepository
import app.overbay.swiftfox.engine.TabSessionPool
import app.overbay.swiftfox.features.downloads.DownloadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SwiftfoxApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: SwiftfoxDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var downloadCoordinator: DownloadCoordinator
        private set

    val tabSessionPool = TabSessionPool(maxWarmSessions = 8)

    override fun onCreate() {
        super.onCreate()
        database = SwiftfoxDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        downloadCoordinator = DownloadCoordinator(this, database.downloadRecordDao())

        appScope.launch {
            database.seedDefaultsIfEmpty()
        }
    }
}
