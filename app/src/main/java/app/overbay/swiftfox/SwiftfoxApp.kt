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
import app.overbay.swiftfox.features.icons.FaviconRepository
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.TopDomainsRepository
import app.overbay.swiftfox.features.reader.ReaderExtractor
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import app.overbay.swiftfox.features.sync.SyncWorkerBridge
import app.overbay.swiftfox.features.userscripts.UserscriptBridge
import app.overbay.swiftfox.features.userscripts.UserscriptRepository
import app.overbay.swiftfox.features.zapper.ZapperController
import app.overbay.swiftfox.ui.reader.clearGlobalReaderImageCache
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

    lateinit var faviconRepository: FaviconRepository
        private set

    lateinit var topDomainsRepository: TopDomainsRepository
        private set

    val tabSessionPool = TabSessionPool(maxWarmSessions = 5)

    override fun onCreate() {
        super.onCreate()
        database = SwiftfoxDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        val lazyClientProvider: () -> OkHttpClient = { httpClient }

        faviconRepository = FaviconRepository(this, lazyClientProvider, appScope)
        topDomainsRepository = TopDomainsRepository(this)
        OmniboxEngine.configureHttpClientProvider(lazyClientProvider)
        downloadCoordinator = DownloadCoordinator(
            context = this,
            downloadRecordDao = database.downloadRecordDao(),
            httpClientProvider = lazyClientProvider,
            downloadScope = appScope
        )

        adblockEngine = AdblockEngine(this)
        filterListRepository = FilterListRepository(
            context = this,
            dao = database.filterListDao(),
            engine = adblockEngine,
            clientProvider = lazyClientProvider
        )
        zapperController = ZapperController(this)
        darkReaderInjector = DarkReaderInjector(this)
        userscriptRepository = UserscriptRepository(
            scriptDao = database.userscriptDao(),
            storageDao = database.userscriptStorageDao(),
            clientProvider = lazyClientProvider
        )
        userscriptBridge = UserscriptBridge(
            context = this,
            scope = appScope,
            scriptDao = database.userscriptDao(),
            storageDao = database.userscriptStorageDao(),
            clientProvider = lazyClientProvider
        )
        readerExtractor = ReaderExtractor(this)
        mozillaSyncCoordinator = MozillaSyncCoordinator(
            context = this,
            db = database,
            prefs = preferencesRepository,
            scope = appScope,
            httpClientProvider = lazyClientProvider
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

        // Load warm engine.dat immediately on IO without waiting behind DataStore/Room setup
        appScope.launch {
            adblockEngine.tryLoadWarmCacheFast()
        }

        // Defer WebView/ProfileStore initialization and download receiver registration until
        // Main Looper is idle after first frame so cold launch stays completely unblocked.
        Looper.getMainLooper().queue.addIdleHandler {
            if (BuildConfig.DEBUG) {
                runCatching { WebView.setWebContentsDebuggingEnabled(true) }
            }
            downloadCoordinator.registerCompletionReceiver(appScope)
            tabSessionPool.clearPrivateProfileData()
            appScope.launch {
                downloadCoordinator.reconcileActiveSystemDownloads()
            }
            false
        }

        // Warm core navigation assets & schedule periodic filter updates off the main thread.
        // Zapper and Readability JS remain lazily loaded on demand to save cold-start I/O & RAM.
        appScope.launch {
            topDomainsRepository.warmUp()
            darkReaderInjector.warmUpAssets()
            userscriptBridge.warmUpAssets()
            FilterListUpdateWorker.schedulePeriodic(this@SwiftfoxApp)
        }

        appScope.launch {
            val initialSettings = preferencesRepository.settingsFlow.first()
            database.seedDefaultsIfEmpty(seedInitialContent = !initialSettings.hasSeededDefaults)
            if (!initialSettings.hasSeededDefaults) {
                preferencesRepository.setHasSeededDefaults(true)
            }
            // Only load JNA/megazord & Places/Tabs SQLite at cold start when a Sync account is connected.
            if (initialSettings.syncHasConnectedAccount || mozillaSyncCoordinator.hasPersistedAccountHint()) {
                mozillaSyncCoordinator.initialize()
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
            val isCritical = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            tabSessionPool.hibernateBackgroundSessions(evictThumbnails = true)
            faviconRepository.trimMemory(evictAll = isCritical)
            clearGlobalReaderImageCache()
        }
    }
}
