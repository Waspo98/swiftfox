package app.overbay.swiftfox

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Looper
import android.webkit.WebView
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.prefs.PreferencesRepository
import app.overbay.swiftfox.engine.TabSessionPool
import app.overbay.swiftfox.features.adblock.AdblockEngine
import app.overbay.swiftfox.features.adblock.FilterListRepository
import app.overbay.swiftfox.features.adblock.FilterListUpdateWorker
import app.overbay.swiftfox.features.adblock.FilterListWorkerBridge
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.downloads.DownloadCoordinator
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.reader.ReaderExtractor
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import app.overbay.swiftfox.features.sync.SyncWorkerBridge
import app.overbay.swiftfox.features.userscripts.UserscriptBridge
import app.overbay.swiftfox.features.userscripts.UserscriptRepository
import app.overbay.swiftfox.features.zapper.ZapperController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mozilla.components.service.fxa.sync.SyncReason
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SwiftfoxApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

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

    lateinit var mozillaSyncCoordinator: MozillaSyncCoordinator
        private set

    val tabSessionPool = TabSessionPool(maxWarmSessions = 5)

    override fun onCreate() {
        super.onCreate()
        database = SwiftfoxDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        OmniboxEngine.configureHttpClient(httpClient)
        downloadCoordinator = DownloadCoordinator(this, database.downloadRecordDao(), httpClient)
        downloadCoordinator.registerCompletionReceiver(appScope)

        adblockEngine = AdblockEngine(this)
        filterListRepository = FilterListRepository(
            context = this,
            dao = database.filterListDao(),
            engine = adblockEngine,
            client = httpClient
        )
        zapperController = ZapperController(this)
        darkReaderInjector = DarkReaderInjector(this)
        userscriptRepository = UserscriptRepository(
            scriptDao = database.userscriptDao(),
            storageDao = database.userscriptStorageDao(),
            client = httpClient
        )
        userscriptBridge = UserscriptBridge(
            context = this,
            scope = appScope,
            scriptDao = database.userscriptDao(),
            storageDao = database.userscriptStorageDao(),
            client = httpClient
        )
        readerExtractor = ReaderExtractor(this)
        mozillaSyncCoordinator = MozillaSyncCoordinator(
            context = this,
            db = database,
            prefs = preferencesRepository,
            scope = appScope,
            httpClient = httpClient
        )
        SyncWorkerBridge.onRunSync = {
            mozillaSyncCoordinator.syncNow(SyncReason.Startup)
        }

        FilterListWorkerBridge.onRunUpdate = {
            try {
                kotlin.Result.success(filterListRepository.updateAllEnabled())
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                kotlin.Result.failure(t)
            }
        }

        // Defer WebView/ProfileStore initialization until Main Looper is idle after first frame
        // so Chromium never blocks cold launch.
        Looper.getMainLooper().queue.addIdleHandler {
            if (BuildConfig.DEBUG) {
                runCatching { WebView.setWebContentsDebuggingEnabled(true) }
            }
            tabSessionPool.clearPrivateProfileData()
            false
        }

        // Run Mozilla Sync/JNA init, JS asset warmup, and WorkManager scheduling off the main thread.
        appScope.launch {
            darkReaderInjector.warmUpAssets()
            zapperController.warmUpAssets()
            userscriptBridge.warmUpAssets()
            readerExtractor.warmUpAssets()
            FilterListUpdateWorker.schedulePeriodic(this@SwiftfoxApp)
            mozillaSyncCoordinator.initialize()
        }

        appScope.launch {
            val initialSettings = preferencesRepository.settingsFlow.first()
            database.seedDefaultsIfEmpty(seedInitialContent = !initialSettings.hasSeededDefaults)
            if (!initialSettings.hasSeededDefaults) {
                preferencesRepository.setHasSeededDefaults(true)
            }
            val allLists = database.filterListDao().getAll()
            val enabledLists = allLists.filter { it.enabled }
            adblockEngine.initialize(enabledLists, hasAnyConfiguredLists = allLists.isNotEmpty())
            filterListRepository.syncIfNeverUpdated()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // TRIM_MEMORY_UI_HIDDEN (20) fires on every normal app switch — only hibernate warm
        // background tabs under genuine system memory pressure.
        val isGenuineMemoryPressure =
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (isGenuineMemoryPressure) {
            tabSessionPool.hibernateBackgroundSessions(evictThumbnails = true)
        }
    }
}