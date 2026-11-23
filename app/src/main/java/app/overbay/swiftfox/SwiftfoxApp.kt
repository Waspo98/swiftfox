package app.overbay.swiftfox

import android.app.Application
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.prefs.PreferencesRepository
import app.overbay.swiftfox.engine.TabSessionPool
import app.overbay.swiftfox.features.adblock.AdblockEngine
import app.overbay.swiftfox.features.adblock.FilterListRepository
import app.overbay.swiftfox.features.adblock.FilterListUpdateWorker
import app.overbay.swiftfox.features.adblock.FilterListWorkerBridge
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.downloads.DownloadCoordinator
import app.overbay.swiftfox.features.reader.ReaderExtractor
import app.overbay.swiftfox.features.userscripts.UserscriptBridge
import app.overbay.swiftfox.features.userscripts.UserscriptRepository
import app.overbay.swiftfox.features.zapper.ZapperController
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

    lateinit var adblockEngine: AdblockEngine
        private set

    lateinit var filterListRepository: FilterListRepository
        private set

    lateinit var zapperController: ZapperController
        private set

    lateinit var darkReaderInjector: DarkReaderInjector
        private set

    lateinit var userscriptRepository: UserscriptRepository
        private set

    lateinit var userscriptBridge: UserscriptBridge
        private set

    lateinit var readerExtractor: ReaderExtractor
        private set

    val tabSessionPool = TabSessionPool(maxWarmSessions = 8)

    override fun onCreate() {
        super.onCreate()
        database = SwiftfoxDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        downloadCoordinator = DownloadCoordinator(this, database.downloadRecordDao())

        adblockEngine = AdblockEngine(this)
        filterListRepository = FilterListRepository(
            context = this,
            dao = database.filterListDao(),
            engine = adblockEngine
        )
        zapperController = ZapperController(this)
        darkReaderInjector = DarkReaderInjector(this)
        userscriptRepository = UserscriptRepository(
            scriptDao = database.userscriptDao(),
            storageDao = database.userscriptStorageDao()
        )
        userscriptBridge = UserscriptBridge(
            context = this,
            scope = appScope,
            scriptDao = database.userscriptDao(),
            storageDao = database.userscriptStorageDao()
        )
        readerExtractor = ReaderExtractor(this)

        FilterListWorkerBridge.onRunUpdate = {
            runCatching { filterListRepository.updateAllEnabled() }
        }
        FilterListUpdateWorker.schedulePeriodic(this)

        appScope.launch {
            database.seedDefaultsIfEmpty()
            val enabledLists = database.filterListDao().getEnabled()
            adblockEngine.initialize(enabledLists)
            filterListRepository.syncIfNeverUpdated()
        }
    }
}