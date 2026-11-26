package app.overbay.swiftfox.features.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.db.SyncedRemoteTabEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.PreferencesRepository
import app.overbay.swiftfox.features.adblock.FilterListRepository
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.userscripts.UserscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import mozilla.appservices.places.BookmarkRoot
import mozilla.appservices.remotetabs.TabsStore
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.browser.storage.sync.TabEntry
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthFlowError
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import mozilla.components.lib.fetch.okhttp.OkHttpClient as MozOkHttpClient
import mozilla.components.service.fxa.FxaAuthData
import mozilla.components.service.fxa.SyncAuthInfoCache
import mozilla.components.service.fxa.SyncConfig
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.asSyncAuthInfo
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.manager.SCOPE_SYNC
import mozilla.components.service.fxa.manager.SyncEnginesStorage
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.fxa.sync.SyncStatusObserver
import mozilla.components.service.fxa.toAuthType
import mozilla.components.support.AppServicesInitializer
import mozilla.components.support.base.log.Log
import mozilla.components.support.rusthttp.RustHttpConfig
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.browser.storage.sync.Tab as MozTab

private object SwiftfoxEntryPoint : FxAEntryPoint {
    override val entryName: String = "swiftfox-sync-settings"
}

private class NoOpCrashReporter : CrashReporting {
    override fun submitCaughtException(throwable: Throwable) =
        kotlinx.coroutines.Job().apply { complete() }

    override fun recordCrashBreadcrumb(breadcrumb: Breadcrumb) = Unit
}

class MozillaSyncCoordinator(
    private val context: Context,
    private val db: SwiftfoxDatabase,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
    private val httpClientProvider: () -> OkHttpClient,
    private val filterListRepository: FilterListRepository? = null,
    private val userscriptRepository: UserscriptRepository? = null
) {
    constructor(
        context: Context,
        db: SwiftfoxDatabase,
        prefs: PreferencesRepository,
        scope: CoroutineScope,
        httpClient: OkHttpClient
    ) : this(context, db, prefs, scope, { httpClient }, null, null)

    private val _status = MutableStateFlow(SyncStatusSnapshot())
    val status: StateFlow<SyncStatusSnapshot> = _status.asStateFlow()

    private val _swiftfoxDataRevision = MutableStateFlow(0L)
    val swiftfoxDataRevision: StateFlow<Long> = _swiftfoxDataRevision.asStateFlow()

    private val syncMutex = Mutex()
    private val signOutMutex = Mutex()
    private val bookmarkMirrorMutex = Mutex()
    private val historyMirrorMutex = Mutex()
    private val zenMutationMutex = Mutex()
    private val pendingDeletedBookmarkGuids: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val sync15Transport = Sync15Transport(httpClientProvider)
    private val zenClient = Sync15SpacesClient(sync15Transport)
    private val zenPendingOpsStore = ZenSpacesPendingOpsStore(context, sync15Transport.json)
    private val swiftfoxClient = Sync15SwiftfoxClient(
        context = context,
        db = db,
        prefs = prefs,
        filterListRepository = filterListRepository,
        userscriptRepository = userscriptRepository,
        transport = sync15Transport
    )
    private val crashReporter = NoOpCrashReporter()
    private val initComplete = CompletableDeferred<Unit>()
    private val authGeneration = AtomicLong(0L)
    private val syncStartedFlag = AtomicBoolean(false)
    private val isSigningOut = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val swiftfoxPushLock = Any()
    private var swiftfoxPushJob: Job? = null
    private var isSwiftfoxPushInFlight = false
    private var swiftfoxPushRequestedAgain = false

    @Volatile
    private var lastForegroundSyncAtMs = 0L

    @Volatile
    private var lastKnownZenSpacesSyncEnabled = true

    @Volatile
    private var isOrchestratedSync = false

    @Volatile
    private var pendingAuthCompletion: CompletableDeferred<Result<Unit>>? = null

    @Volatile
    private var activeSyncDeferred: CompletableDeferred<Result<Unit>>? = null

    @Volatile
    private var bookmarksStorage: PlacesBookmarksStorage? = null

    @Volatile
    private var historyStorage: PlacesHistoryStorage? = null

    @Volatile
    private var remoteTabsStorage: RemoteTabsStorage? = null

    @Volatile
    private var accountManager: FxaAccountManager? = null

    private val accountObserver = object : AccountObserver {
        override fun onReady(authenticatedAccount: OAuthAccount?) {
            val mgr = accountManager
            if (authenticatedAccount != null) {
                val profile = mgr?.accountProfile()
                if (mgr?.accountNeedsReauth() == true) {
                    _status.update {
                        it.copy(
                            accountState = SyncAccountState.NeedsReauth(
                                email = profile?.email,
                                message = "Mozilla Account session expired. Please sign in again."
                            ),
                            isSyncing = false
                        )
                    }
                    SwiftfoxSyncWorker.cancel(context)
                } else {
                    updateConnectedFromProfile(profile)
                    val gen = authGeneration.get()
                    scope.launch {
                        prefs.setSyncHasConnectedAccount(true)
                        val settings = prefs.settingsFlow.first()
                        if (authGeneration.get() == gen && accountManager?.connectedAccount() != null) {
                            SwiftfoxSyncWorker.schedulePeriodic(context, settings.syncPeriodicIntervalMinutes)
                        }
                    }
                }
            } else {
                scope.launch {
                    prefs.setSyncHasConnectedAccount(false)
                }
                _status.update {
                    it.copy(accountState = SyncAccountState.SignedOut, isSyncing = false)
                }
            }
        }

        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            pendingAuthCompletion?.complete(Result.success(Unit))
            val gen = authGeneration.get()
            val profile = accountManager?.accountProfile()
            updateConnectedFromProfile(profile)
            scope.launch {
                prefs.setSyncHasConnectedAccount(true)
                val freshProfile = runCatching { account.getProfile(ignoreCache = false) }.getOrNull()
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@launch
                if (freshProfile != null) {
                    updateConnectedFromProfile(freshProfile)
                }
                val settings = prefs.settingsFlow.first()
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@launch
                SwiftfoxSyncWorker.schedulePeriodic(context, settings.syncPeriodicIntervalMinutes)
                if (authType != AuthType.Existing) {
                    // Join or run a full orchestrated sync so Room + Zen Spaces populate right away
                    syncNow(SyncReason.User)
                }
            }
        }

        override fun onProfileUpdated(profile: Profile) {
            if (accountManager?.accountNeedsReauth() == true) return
            updateConnectedFromProfile(profile)
        }

        override fun onAuthenticationProblems() {
            pendingAuthCompletion?.complete(
                Result.failure(IOException("Mozilla Account requires re-authentication"))
            )
            val prevEmail = (_status.value.accountState as? SyncAccountState.Connected)?.email
                ?: (_status.value.accountState as? SyncAccountState.NeedsReauth)?.email
                ?: accountManager?.accountProfile()?.email
            _status.update {
                it.copy(
                    accountState = SyncAccountState.NeedsReauth(
                        email = prevEmail,
                        message = "Mozilla Account session expired. Please sign in again."
                    ),
                    isSyncing = false
                )
            }
        }

        override fun onFlowError(error: AuthFlowError) {
            pendingAuthCompletion?.complete(
                Result.failure(IOException("Authentication failed (${error.name})"))
            )
            _status.update {
                it.copy(
                    accountState = if (it.accountState is SyncAccountState.Authenticating) {
                        SyncAccountState.SignedOut
                    } else {
                        it.accountState
                    },
                    lastSyncError = "Authentication failed (${error.name})",
                    isSyncing = false
                )
            }
        }

        override fun onLoggedOut() {
            pendingAuthCompletion?.complete(
                Result.failure(IOException("Mozilla Account signed out"))
            )
            zenClient.invalidateAuthCache()
            SwiftfoxSyncWorker.cancel(context)
            // During explicit signOut(), wait until local Places/Room/profile teardown finishes
            // before exposing SignedOut UI so rapid re-sign-in cannot race with cleanup.
            if (!isSigningOut.get()) {
                scope.launch { prefs.setSyncHasConnectedAccount(false) }
                _status.update {
                    SyncStatusSnapshot(
                        accountState = SyncAccountState.SignedOut,
                        isEngineAvailable = it.isEngineAvailable
                    )
                }
            }
        }
    }

    private val syncStatusObserver = object : SyncStatusObserver {
        override fun onStarted() {
            syncStartedFlag.set(true)
            _status.update { it.copy(isSyncing = true, lastSyncError = null) }
        }

        override fun onIdle() {
            if (isOrchestratedSync) {
                // WorkManagerSyncDispatcher fires onIdle() for SUCCEEDED, FAILED, AND CANCELLED
                // and for prior finished jobs — let wmMonitorJob be the sole completion authority.
                return
            }
            _status.update { it.copy(isSyncing = false) }
            // Reconcile local Room tables when FxaAccountManager finishes an automatic startup sync
            scope.launch(Dispatchers.IO) {
                reconcileFromLocalStoresIfIdle()
            }
        }

        override fun onError(error: Exception?) {
            if (isOrchestratedSync) {
                activeSyncDeferred?.complete(
                    Result.failure(error ?: IOException("Mozilla Sync encountered an error"))
                )
            } else {
                _status.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncError = error?.message ?: "Sync encountered an error"
                    )
                }
            }
        }
    }

    /**
     * Checks whether Mozilla FxA state preferences exist on disk without loading JNA/megazord,
     * ensuring existing signed-in users upgrading from older versions still initialize eagerly.
     */
    fun hasPersistedAccountHint(): Boolean =
        runCatching {
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            File(sharedPrefsDir, "mozilla_fxa_state_machine_prefs.xml").isFile
        }.getOrDefault(false)

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                AppServicesInitializer.init(
                    AppServicesInitializer.Config(
                        crashReporting = null,
                        logLevel = Log.Priority.WARN
                    )
                )
                RustHttpConfig.setClient(lazy { MozOkHttpClient(httpClientProvider(), context) })

                val bStore = PlacesBookmarksStorage(context)
                val hStore = PlacesHistoryStorage(context, crashReporter)
                val tStore = RemoteTabsStorage(context, crashReporter)

                bookmarksStorage = bStore
                historyStorage = hStore
                remoteTabsStorage = tStore

                GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to lazy { bStore })
                GlobalSyncableStoreProvider.configureStore(SyncEngine.History to lazy { hStore })
                GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to lazy { tStore })

                val deviceName =
                    "${Build.MANUFACTURER.replaceFirstChar { it.uppercase(Locale.ROOT) }} ${Build.MODEL} (Swiftfox)"

                // FxaAccountManager.<init> registers a LiveData.observeForever() observer inside
                // WorkManagerSyncManager, which MUST execute on the Main thread.
                val mgr = withContext(Dispatchers.Main) {
                    FxaAccountManager(
                        context = context,
                        serverConfig = FxaConfig(
                            server = FxaServer.Release,
                            clientId = FENIX_CLIENT_ID,
                            redirectUri = FENIX_REDIRECT_URI
                        ),
                        deviceConfig = DeviceConfig(
                            name = deviceName,
                            type = DeviceType.MOBILE,
                            capabilities = emptySet(),
                            secureStateAtRest = true
                        ),
                        syncConfig = SyncConfig(
                            supportedEngines = setOf(
                                SyncEngine.Bookmarks,
                                SyncEngine.History,
                                SyncEngine.Tabs
                            ),
                            periodicSyncConfig = null
                        )
                    ).also { created ->
                        created.register(accountObserver)
                        created.syncStatusObserverRegistry.register(syncStatusObserver)
                    }
                }
                accountManager = mgr

                val settings = prefs.settingsFlow.first()
                lastKnownZenSpacesSyncEnabled = settings.syncZenSpacesEnabled
                if (!settings.syncZenSpacesEnabled) {
                    zenPendingOpsStore.clear()
                }
                _status.update { it.copy(lastSyncAtMs = settings.syncLastTimestampMs) }
                updateEnginePreferences(
                    bookmarks = settings.syncBookmarksEnabled,
                    history = settings.syncHistoryEnabled,
                    tabs = settings.syncTabsEnabled
                )
                runCatching { bStore.warmUp() }
                runCatching { hStore.warmUp() }
                runCatching { tStore.warmUp() }
                try {
                    mgr.start()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (err: Throwable) {
                    _status.update {
                        it.copy(lastSyncError = "FxA init error: ${err.message}")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                _status.update {
                    it.copy(
                        isEngineAvailable = false,
                        lastSyncError = "Mozilla sync runtime unavailable: ${err.message}"
                    )
                }
            } finally {
                initComplete.complete(Unit)
            }
            scope.launch(Dispatchers.IO) {
                var lastSeenRevision = prefs.localSettingsMutationRevision.value
                prefs.localSettingsMutationRevision.collect { revision ->
                    if (revision > lastSeenRevision) {
                        lastSeenRevision = revision
                        scheduleSwiftfoxPush()
                    }
                }
            }
            scope.launch(Dispatchers.IO) {
                prefs.settingsFlow
                    .map { it.syncZenSpacesEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        onZenSpacesSyncEnabledChanged(enabled)
                    }
            }
        }
    }

    private suspend fun awaitInitialized() {
        initialize()
        if (!initComplete.isCompleted) {
            withTimeoutOrNull(15_000L) { initComplete.await() }
        }
    }

    suspend fun beginOAuthSignIn(): Result<String> = withContext(Dispatchers.IO) {
        awaitInitialized()
        // Wait for any in-flight signOut() teardown to finish before starting a new auth session
        signOutMutex.withLock {}
        try {
            val mgr = accountManager ?: error("Mozilla Sync runtime unavailable")
            _status.update { it.copy(lastSyncError = null) }
            val url = mgr.beginAuthentication(
                pairingUrl = null,
                entrypoint = SwiftfoxEntryPoint
            ) ?: error("Could not start Mozilla OAuth sign-in")
            Result.success(url)
        } catch (ce: CancellationException) {
            throw ce
        } catch (err: Throwable) {
            Result.failure(err)
        }
    }

    suspend fun beginPairing(rawPairingUrl: String): Result<String> = withContext(Dispatchers.IO) {
        awaitInitialized()
        signOutMutex.withLock {}
        try {
            val trimmed = rawPairingUrl.trim()
            require(isValidPairingUrl(trimmed)) { "Not a valid Mozilla pairing QR link" }

            val mgr = accountManager ?: error("Mozilla Sync runtime unavailable")
            _status.update { it.copy(lastSyncError = null) }
            val url = mgr.beginAuthentication(
                pairingUrl = trimmed,
                entrypoint = SwiftfoxEntryPoint
            ) ?: error("Could not start Firefox/Zen QR pairing")
            Result.success(url)
        } catch (ce: CancellationException) {
            throw ce
        } catch (err: Throwable) {
            Result.failure(err)
        }
    }

    suspend fun completeAuthRedirect(redirectUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        awaitInitialized()
        val previousState = _status.value.accountState
        _status.update { it.copy(accountState = SyncAccountState.Authenticating, lastSyncError = null) }
        val deferred = CompletableDeferred<Result<Unit>>()
        pendingAuthCompletion = deferred
        var completedSuccessfully = false
        try {
            val result = try {
                val uri = Uri.parse(redirectUrl)
                val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
                    ?: error("Missing OAuth authorization code")
                val state = uri.getQueryParameter("state")?.takeIf { it.isNotBlank() }
                    ?: error("Missing OAuth state")
                val authType = uri.getQueryParameter("action").toAuthType()

                val mgr = accountManager ?: error("Mozilla Sync runtime unavailable")
                val ok = mgr.finishAuthentication(
                    FxaAuthData(
                        authType = authType,
                        code = code,
                        state = state
                    )
                )
                check(ok) { "Mozilla account authorization rejected" }
                val authResult = withTimeoutOrNull(20_000L) { deferred.await() }
                    ?: throw IOException("Mozilla authentication timed out")
                authResult.getOrThrow()
                completedSuccessfully = true
                Result.success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                Result.failure(err)
            }
            result.onFailure { err ->
                _status.update {
                    it.copy(
                        accountState = when (previousState) {
                            is SyncAccountState.Connected,
                            is SyncAccountState.NeedsReauth -> previousState
                            else -> SyncAccountState.SignedOut
                        },
                        lastSyncError = err.message ?: "Mozilla sign-in failed"
                    )
                }
            }
            result
        } finally {
            if (pendingAuthCompletion === deferred) {
                pendingAuthCompletion = null
            }
            if (!completedSuccessfully && _status.value.accountState == SyncAccountState.Authenticating) {
                _status.update {
                    it.copy(
                        accountState = when (previousState) {
                            is SyncAccountState.Connected,
                            is SyncAccountState.NeedsReauth -> previousState
                            else -> SyncAccountState.SignedOut
                        }
                    )
                }
            }
        }
    }

    fun isOAuthRedirectUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("accounts.firefox.com", ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.path == "/oauth/success/$FENIX_CLIENT_ID"
    }

    suspend fun syncNow(reason: SyncReason = SyncReason.User): Result<String> = syncMutex.withLock {
        awaitInitialized()
        withContext(Dispatchers.IO) {
            isOrchestratedSync = true
            try {
                try {
                    val mgr = accountManager ?: throw SyncAuthRequiredException("Mozilla Sync engine unavailable")
                    if (mgr.accountNeedsReauth()) {
                        throw SyncAuthRequiredException("Mozilla Account session expired. Please sign in again.")
                    }
                    val account = mgr.connectedAccount()
                        ?: throw SyncAuthRequiredException("Sign in to your Mozilla Account first")

                    _status.update { it.copy(isSyncing = true, lastSyncError = null) }
                    val settings = prefs.settingsFlow.first()

                    // Wait for any pre-existing automatic startup "Immediate" WorkManager sync
                    // BEFORE pushing new local rows into Places so ExistingWorkPolicy.KEEP never
                    // drops our post-push sync pass.
                    awaitActiveMozillaImmediateWorkIfAny()

                    // 1. Pre-sync local push into Places / RemoteTabs
                    if (settings.syncBookmarksEnabled) {
                        bookmarkMirrorMutex.withLock {
                            pushUnsyncedBookmarksToPlaces()
                        }
                    }
                    if (settings.syncHistoryEnabled && !settings.syncLocalHistorySeeded) {
                        historyMirrorMutex.withLock {
                            pushRecentLocalHistoryToPlaces()
                            prefs.setSyncLocalHistorySeeded(true)
                        }
                    }
                    if (settings.syncTabsEnabled) {
                        pushLocalOpenTabsToPlaces()
                    }

                    // 2. Run Mozilla Sync15 engines and await the actual WorkManager job result
                    updateEnginePreferences(
                        bookmarks = settings.syncBookmarksEnabled,
                        history = settings.syncHistoryEnabled,
                        tabs = settings.syncTabsEnabled
                    )

                    val subset = buildList {
                        if (settings.syncBookmarksEnabled) add(SyncEngine.Bookmarks)
                        if (settings.syncHistoryEnabled) add(SyncEngine.History)
                        if (settings.syncTabsEnabled) add(SyncEngine.Tabs)
                    }
                    if (subset.isNotEmpty()) {
                        runAndAwaitMozillaSync(
                            mgr = mgr,
                            reason = reason,
                            debounce = reason == SyncReason.Startup,
                            subset = subset
                        )
                    }

                    // 3. Reconcile Places + Remote Tabs -> Room
                    val summaryParts = mutableListOf<String>()
                    if (settings.syncBookmarksEnabled) {
                        val count = bookmarkMirrorMutex.withLock {
                            pullPlacesBookmarksIntoRoom()
                        }
                        summaryParts += "$count bookmarks"
                    }
                    if (settings.syncHistoryEnabled) {
                        val count = historyMirrorMutex.withLock {
                            pullPlacesHistoryIntoRoom()
                        }
                        summaryParts += "$count history"
                    }
                    if (settings.syncTabsEnabled) {
                        val count = pullRemoteTabsIntoRoom(account)
                        summaryParts += "$count remote tabs"
                    }

                    // 4. Sync Zen Browser Spaces & Essentials (isolated so transient Zen issues don't fail Firefox Sync)
                    val zenNote: String? = if (settings.syncZenSpacesEnabled) {
                        val zenOutcome = syncZenSpacesSection(account)
                        if (zenOutcome.syncedSpaceCount != null) {
                            summaryParts += "${zenOutcome.syncedSpaceCount} Zen Spaces"
                        }
                        zenOutcome.note
                    } else {
                        "Zen Spaces sync disabled"
                    }

                    // 5. Sync Swiftfox-only client data (settings, site/zapper rules, filter lists, userscripts, local Spaces, Essentials & pinned tabs)
                    val swiftfoxNote: String? = if (settings.syncSwiftfoxEnabled) {
                        val sfOutcome = syncSwiftfoxSection(
                            account = account,
                            rethrowOnError = (subset.isEmpty() && !settings.syncZenSpacesEnabled)
                        )
                        if (sfOutcome.succeeded) {
                            summaryParts += "Swiftfox data"
                        }
                        sfOutcome.note
                    } else {
                        "Swiftfox client sync disabled"
                    }

                    val now = System.currentTimeMillis()
                    lastForegroundSyncAtMs = now
                    prefs.setSyncLastTimestampMs(now)
                    val summary = if (summaryParts.isEmpty()) {
                        "Sync complete"
                    } else {
                        "Synced ${summaryParts.joinToString(" · ")}"
                    }
                    _status.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncAtMs = now,
                            lastSyncSummary = summary,
                            lastSyncError = null,
                            zenStatusNote = zenNote,
                            swiftfoxStatusNote = swiftfoxNote
                        )
                    }
                    Result.success(summary)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (err: Throwable) {
                    _status.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncError = err.message ?: "Sync failed"
                        )
                    }
                    Result.failure(err)
                }
            } finally {
                isOrchestratedSync = false
                if (_status.value.isSyncing) {
                    _status.update { it.copy(isSyncing = false) }
                }
            }
        }
    }

    private suspend fun awaitActiveMozillaImmediateWorkIfAny() {
        val wm = WorkManager.getInstance(context.applicationContext)
        val initialInfos = wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).first()
        if (initialInfos.any { it.state == WorkInfo.State.RUNNING }) {
            withTimeoutOrNull(15_000L) {
                wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).first { infos ->
                    infos.none { it.state == WorkInfo.State.RUNNING }
                }
            }
        }
    }

    /**
     * Triggers `FxaAccountManager.syncNow` and awaits either `SyncStatusObserver.onIdle`/`onError`
     * or the exact WorkManager `"Immediate"` job transition so backed-off `Result.retry()` and
     * skipped enqueues fail fast instead of hanging 60s.
     */
    private suspend fun runAndAwaitMozillaSync(
        mgr: FxaAccountManager,
        reason: SyncReason,
        debounce: Boolean,
        subset: List<SyncEngine>
    ) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val initialInfos = wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).first()
        val hasRunning = initialInfos.any { it.state == WorkInfo.State.RUNNING }
        val hasBackedOffQueued = !hasRunning && initialInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }
        if (hasBackedOffQueued) {
            wm.cancelUniqueWork(MOZILLA_IMMEDIATE_WORK_NAME)
            withTimeoutOrNull(2_000L) {
                wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).first { infos ->
                    infos.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                }
            }
        }

        val beforeInfos = wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).first()
        val previouslyFinishedIds = beforeInfos.filter { it.state.isFinished }.mapTo(mutableSetOf()) { it.id }
        val existingActiveId = beforeInfos.firstOrNull { !it.state.isFinished }?.id

        syncStartedFlag.set(false)
        val syncDone = CompletableDeferred<Result<Unit>>()
        activeSyncDeferred = syncDone
        try {
            mgr.syncNow(
                reason = reason,
                debounce = debounce,
                customEngineSubset = subset
            )
            coroutineScope {
                val wmMonitorJob = launch {
                    var observedRunning = false
                    wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).collect { infos ->
                        val target = infos.firstOrNull { it.id == existingActiveId }
                            ?: infos.firstOrNull { it.id !in previouslyFinishedIds }
                            ?: return@collect
                        when (target.state) {
                            WorkInfo.State.RUNNING -> observedRunning = true
                            WorkInfo.State.SUCCEEDED -> syncDone.complete(Result.success(Unit))
                            WorkInfo.State.FAILED -> syncDone.complete(
                                Result.failure(IOException("Mozilla Sync failed — check network or sign in again"))
                            )
                            WorkInfo.State.CANCELLED -> syncDone.complete(
                                Result.failure(IOException("Mozilla Sync was cancelled"))
                            )
                            WorkInfo.State.ENQUEUED -> {
                                if (observedRunning) {
                                    syncDone.complete(
                                        Result.failure(IOException("Mozilla Sync deferred for retry — check network and try again"))
                                    )
                                }
                            }
                            WorkInfo.State.BLOCKED -> Unit
                        }
                    }
                }
                val noStartGuard = launch {
                    delay(4_500L)
                    val infos = wm.getWorkInfosForUniqueWorkFlow(MOZILLA_IMMEDIATE_WORK_NAME).first()
                    val target = infos.firstOrNull { it.id == existingActiveId }
                        ?: infos.firstOrNull { it.id !in previouslyFinishedIds }
                    if (target == null && !syncStartedFlag.get()) {
                        if (debounce) {
                            // Debounced background sync was intentionally skipped by Mozilla Sync
                            syncDone.complete(Result.success(Unit))
                        } else {
                            syncDone.complete(
                                Result.failure(IOException("Mozilla Sync did not start — verify sign-in and network"))
                            )
                        }
                    }
                }
                val awaited = withTimeoutOrNull(SYNC_WAIT_TIMEOUT_MS) { syncDone.await() }
                wmMonitorJob.cancel()
                noStartGuard.cancel()
                if (awaited == null) {
                    throw IOException("Mozilla Sync timed out waiting for background worker")
                }
                awaited.getOrThrow()
            }
        } finally {
            if (activeSyncDeferred === syncDone) {
                activeSyncDeferred = null
            }
        }
    }

    private suspend fun reconcileFromLocalStoresIfIdle() {
        if (!syncMutex.tryLock()) return
        try {
            val account = accountManager?.connectedAccount() ?: return
            val settings = prefs.settingsFlow.first()
            if (settings.syncBookmarksEnabled) {
                bookmarkMirrorMutex.withLock {
                    pullPlacesBookmarksIntoRoom()
                }
            }
            if (settings.syncHistoryEnabled) {
                historyMirrorMutex.withLock {
                    pullPlacesHistoryIntoRoom()
                }
            }
            if (settings.syncTabsEnabled) pullRemoteTabsIntoRoom(account)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Best-effort background refresh after automatic FxA startup sync
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun sendTabToZenSpace(
        spaceSyncGuid: String,
        url: String,
        title: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        awaitInitialized()
        try {
            val mgr = accountManager ?: error("Mozilla Sync unavailable")
            val account = mgr.connectedAccount() ?: error("Sign in to your Mozilla Account first")
            val tokenInfo = account.getAccessToken(SCOPE_SYNC) ?: error("Missing Sync access token")
            val tokenUrl = account.getTokenServerEndpointURL() ?: error("Missing Token Server URL")
            val syncAuth = tokenInfo.asSyncAuthInfo(tokenUrl)

            zenClient.sendTabToZenSpace(
                kid = syncAuth.kid,
                fxaAccessToken = syncAuth.fxaAccessToken,
                syncKeyBase64 = syncAuth.syncKey,
                tokenServerUrl = syncAuth.tokenServerUrl,
                workspaceUuid = spaceSyncGuid,
                url = url,
                title = title
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (err: Throwable) {
            Result.failure(err)
        }
    }

    suspend fun signOut() = withContext(NonCancellable + Dispatchers.IO) {
        signOutMutex.withLock {
            syncMutex.withLock {
                bookmarkMirrorMutex.withLock {
                    historyMirrorMutex.withLock {
                        isSigningOut.set(true)
                        try {
                            authGeneration.incrementAndGet()
                            synchronized(swiftfoxPushLock) {
                                swiftfoxPushRequestedAgain = false
                                swiftfoxPushJob?.cancel()
                                swiftfoxPushJob = null
                            }
                            lastForegroundSyncAtMs = 0L
                            pendingDeletedBookmarkGuids.clear()
                            runCatching { accountManager?.logout() }
                            SwiftfoxSyncWorker.cancel(context)

                            // Wipe local Mozilla Places SQLite database using public PlacesStorage cleanup()
                            // before deleting places.sqlite* and reconstructing fresh storage instances.
                            val oldBookmarks = bookmarksStorage
                            val oldHistory = historyStorage
                            bookmarksStorage = null
                            historyStorage = null
                            runCatching { oldBookmarks?.cancelWrites() }
                            runCatching { oldBookmarks?.cancelReads() }
                            runCatching { oldHistory?.cancelWrites() }
                            runCatching { oldHistory?.cancelReads() }
                            runCatching { oldBookmarks?.cleanup() }
                            runCatching { oldHistory?.cleanup() }
                            listOf("places.sqlite", "places.sqlite-wal", "places.sqlite-shm").forEach { name ->
                                runCatching { File(context.filesDir, name).delete() }
                            }
                            runCatching {
                                val freshBookmarks = PlacesBookmarksStorage(context)
                                val freshHistory = PlacesHistoryStorage(context, crashReporter)
                                bookmarksStorage = freshBookmarks
                                historyStorage = freshHistory
                                GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to lazy { freshBookmarks })
                                GlobalSyncableStoreProvider.configureStore(SyncEngine.History to lazy { freshHistory })
                                freshBookmarks.warmUp()
                                freshHistory.warmUp()
                            }

                            // Wipe staged local tabs + cached remote device tabs in tabs.sqlite
                            val oldRemoteTabs = remoteTabsStorage
                            remoteTabsStorage = null
                            runCatching { oldRemoteTabs?.store(emptyList()) }
                            runCatching { oldRemoteTabs?.cleanup() }
                            listOf("tabs.sqlite", "tabs.sqlite-wal", "tabs.sqlite-shm").forEach { name ->
                                runCatching { File(context.filesDir, name).delete() }
                            }
                            runCatching {
                                val freshTabsStore = RemoteTabsStorage(context, crashReporter)
                                remoteTabsStorage = freshTabsStore
                                GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to lazy { freshTabsStore })
                                freshTabsStore.warmUp()
                            }

                            runCatching { SyncAuthInfoCache(context).clear() }

                            // Preserve user's local Room bookmarks/spaces/essentials while detaching sync GUIDs
                            // so signing into another account won't delete local items as "removed remotely".
                            db.withTransaction {
                                db.bookmarkDao().clearSyncMetadata()
                                db.spaceDao().clearSyncMetadata()
                                db.essentialDao().clearSyncMetadata()
                                db.syncedRemoteTabDao().clearAll()
                                db.zenPinnedItemDao().clearAll()
                                db.tabDao().deleteAllZenPinnedTabs()
                            }

                            prefs.setSyncLastTimestampMs(0L)
                            prefs.setSyncLocalHistorySeeded(false)
                            prefs.setSyncZenSeparateEssentials(null)
                            prefs.setSyncHasConnectedAccount(false)
                            swiftfoxClient.clearBaseline()
                            zenPendingOpsStore.clear()
                            zenClient.invalidateAuthCache()
                            clearIsolatedFxAAuthProfile()

                            _status.update {
                                SyncStatusSnapshot(
                                    accountState = SyncAccountState.SignedOut,
                                    isEngineAvailable = it.isEngineAvailable
                                )
                            }
                        } finally {
                            isSigningOut.set(false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun clearIsolatedFxAAuthProfile() {
        withContext(Dispatchers.Main) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return@withContext
            try {
                val store = ProfileStore.getInstance()
                val profile = store.getProfile(FXA_AUTH_PROFILE_NAME) ?: return@withContext
                profile.webStorage.deleteAllData()
                suspendCancellableCoroutine { cont ->
                    profile.cookieManager.removeAllCookies {
                        runCatching {
                            profile.cookieManager.flush()
                            store.deleteProfile(FXA_AUTH_PROFILE_NAME)
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Best-effort cleanup of isolated auth profile
            }
        }
    }

    /**
     * Schedules a debounced background push (via two-way `swiftfox` collection sync) whenever
     * local synced settings, Essentials, local pinned tabs, or local Spaces change.
     */
    fun scheduleSwiftfoxPush(debounceMs: Long = SWIFTFOX_PUSH_DEBOUNCE_MS) {
        if (isSigningOut.get()) return
        val mgr = accountManager ?: return
        if (mgr.connectedAccount() == null || mgr.accountNeedsReauth()) return
        val gen = authGeneration.get()
        synchronized(swiftfoxPushLock) {
            if (isSwiftfoxPushInFlight) {
                swiftfoxPushRequestedAgain = true
                return
            }
            swiftfoxPushJob?.cancel()
            launchSwiftfoxPushLocked(debounceMs = debounceMs, gen = gen)
        }
    }

    /**
     * Battery-mindful foreground pull for lightweight Zen Spaces & Swiftfox data:
     * runs at most once per [FOREGROUND_SYNC_COOLDOWN_MS] when app resumes to foreground
     * and auto-sync interval is enabled (> 0), without waking Firefox Places/WorkManager engines.
     */
    fun syncSwiftfoxAndZenOnForegroundIfDue() {
        if (isSigningOut.get()) return
        scope.launch(Dispatchers.IO) {
            val preSettings = prefs.settingsFlow.first()
            if (!preSettings.syncHasConnectedAccount && !hasPersistedAccountHint()) return@launch
            if (preSettings.syncPeriodicIntervalMinutes <= 0) return@launch
            if (!preSettings.syncZenSpacesEnabled && !preSettings.syncSwiftfoxEnabled) return@launch

            val nowBeforeLock = System.currentTimeMillis()
            val lastBeforeLock = maxOf(lastForegroundSyncAtMs, preSettings.syncLastTimestampMs)
            if (nowBeforeLock - lastBeforeLock < FOREGROUND_SYNC_COOLDOWN_MS) return@launch

            awaitInitialized()
            val mgr = accountManager ?: return@launch
            if (isSigningOut.get() || mgr.accountNeedsReauth()) return@launch
            if (mgr.connectedAccount() == null) return@launch

            syncMutex.withLock {
                if (isSigningOut.get() || mgr.accountNeedsReauth()) return@withLock
                val account = mgr.connectedAccount() ?: return@withLock
                val settings = prefs.settingsFlow.first()
                if (settings.syncPeriodicIntervalMinutes <= 0) return@withLock
                if (!settings.syncZenSpacesEnabled && !settings.syncSwiftfoxEnabled) return@withLock

                val nowMs = System.currentTimeMillis()
                val latestCheckMs = maxOf(lastForegroundSyncAtMs, settings.syncLastTimestampMs)
                if (nowMs - latestCheckMs < FOREGROUND_SYNC_COOLDOWN_MS) return@withLock

                var zenNote: String? = null
                var swiftfoxNote: String? = null
                var checkedNetwork = false
                if (settings.syncZenSpacesEnabled) {
                    val outcome = syncZenSpacesSection(account)
                    zenNote = outcome.note
                    if (outcome.networkCompleted) checkedNetwork = true
                }
                if (settings.syncSwiftfoxEnabled) {
                    val outcome = syncSwiftfoxSection(account, rethrowOnError = false)
                    swiftfoxNote = outcome.note
                    if (outcome.networkCompleted) checkedNetwork = true
                }
                if (checkedNetwork) {
                    lastForegroundSyncAtMs = System.currentTimeMillis()
                    _status.update { current ->
                        current.copy(
                            zenStatusNote = zenNote ?: current.zenStatusNote,
                            swiftfoxStatusNote = swiftfoxNote ?: current.swiftfoxStatusNote
                        )
                    }
                }
            }
        }
    }

    private fun launchSwiftfoxPushLocked(debounceMs: Long, gen: Long) {
        swiftfoxPushJob = scope.launch(Dispatchers.IO) {
            if (debounceMs > 0L) delay(debounceMs)
            val proceed = synchronized(swiftfoxPushLock) {
                if (authGeneration.get() != gen || isSigningOut.get()) {
                    false
                } else {
                    isSwiftfoxPushInFlight = true
                    swiftfoxPushRequestedAgain = false
                    true
                }
            }
            if (!proceed) return@launch
            try {
                pushSwiftfoxNowIfEligible(expectedAuthGeneration = gen)
            } finally {
                synchronized(swiftfoxPushLock) {
                    isSwiftfoxPushInFlight = false
                    if (swiftfoxPushRequestedAgain &&
                        !isSigningOut.get() &&
                        accountManager?.connectedAccount() != null &&
                        accountManager?.accountNeedsReauth() != true
                    ) {
                        swiftfoxPushRequestedAgain = false
                        launchSwiftfoxPushLocked(
                            debounceMs = SWIFTFOX_PUSH_DEBOUNCE_MS,
                            gen = authGeneration.get()
                        )
                    }
                }
            }
        }
    }

    private suspend fun pushSwiftfoxNowIfEligible(expectedAuthGeneration: Long) {
        syncMutex.withLock {
            if (authGeneration.get() != expectedAuthGeneration || isSigningOut.get()) return@withLock
            val mgr = accountManager ?: return@withLock
            if (mgr.accountNeedsReauth()) return@withLock
            val account = mgr.connectedAccount() ?: return@withLock
            val settings = prefs.settingsFlow.first()
            if (!settings.syncSwiftfoxEnabled) return@withLock

            val outcome = syncSwiftfoxSection(account, rethrowOnError = false)
            if (outcome.note != null) {
                _status.update { it.copy(swiftfoxStatusNote = outcome.note) }
            }
        }
    }

    private data class ZenSectionOutcome(
        val syncedSpaceCount: Int?,
        val note: String,
        val networkCompleted: Boolean
    )

    private data class SwiftfoxSectionOutcome(
        val note: String?,
        val succeeded: Boolean,
        val networkCompleted: Boolean
    )

    private suspend fun syncZenSpacesSection(account: OAuthAccount): ZenSectionOutcome {
        return try {
            val tokenInfo = account.getAccessToken(SCOPE_SYNC)
            val tokenUrl = account.getTokenServerEndpointURL()
            if (tokenInfo != null && tokenUrl != null && tokenInfo.key != null) {
                val syncAuth = tokenInfo.asSyncAuthInfo(tokenUrl)
                var pushedZenOpsCount = 0
                var deferredByNewLocalEdit = false
                val zenResult = zenMutationMutex.withLock {
                    val pendingOps = zenPendingOpsStore.snapshotPending()
                    if (pendingOps.isNotEmpty()) {
                        pushedZenOpsCount = zenClient.applyPendingMutations(
                            kid = syncAuth.kid,
                            fxaAccessToken = syncAuth.fxaAccessToken,
                            syncKeyBase64 = syncAuth.syncKey,
                            tokenServerUrl = syncAuth.tokenServerUrl,
                            ops = pendingOps
                        ).getOrThrow()
                        zenPendingOpsStore.removeApplied(pendingOps)
                    }
                    val fetched = zenClient.fetchZenSnapshot(
                        kid = syncAuth.kid,
                        fxaAccessToken = syncAuth.fxaAccessToken,
                        syncKeyBase64 = syncAuth.syncKey,
                        tokenServerUrl = syncAuth.tokenServerUrl
                    )
                    if (fetched is ZenFetchResult.Success && zenPendingOpsStore.hasPending()) {
                        deferredByNewLocalEdit = true
                    }
                    fetched
                }
                when (zenResult) {
                    is ZenFetchResult.Success -> {
                        val syncedSpaceCount = if (deferredByNewLocalEdit) {
                            prefs.setSyncZenSeparateEssentials(zenResult.snapshot.separateEssentials)
                            db.spaceDao().getAll().count { !it.syncGuid.isNullOrBlank() }
                        } else {
                            val count = reconcileZenSnapshotIntoRoom(zenResult.snapshot)
                            prefs.setSyncZenSeparateEssentials(zenResult.snapshot.separateEssentials)
                            count
                        }
                        val baseZenNote = if (zenResult.snapshot.normalTabsEnabled) {
                            "Synced Spaces, Essentials, pinned & normal Zen tabs"
                        } else {
                            "Synced Spaces, Essentials & pinned Zen tabs"
                        }
                        val note = if (pushedZenOpsCount > 0) {
                            "$baseZenNote (pushed local edits)"
                        } else {
                            baseZenNote
                        }
                        ZenSectionOutcome(syncedSpaceCount = syncedSpaceCount, note = note, networkCompleted = true)
                    }
                    ZenFetchResult.CollectionAbsent -> {
                        ZenSectionOutcome(
                            syncedSpaceCount = null,
                            note = "Standard Firefox Sync active (no Zen Spaces found on account)",
                            networkCompleted = true
                        )
                    }
                }
            } else {
                ZenSectionOutcome(
                    syncedSpaceCount = null,
                    note = "Could not obtain Sync key for Zen Spaces",
                    networkCompleted = false
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (zenErr: Throwable) {
            ZenSectionOutcome(
                syncedSpaceCount = null,
                note = "Zen Spaces sync issue: ${zenErr.message ?: "error"}",
                networkCompleted = false
            )
        }
    }

    private suspend fun syncSwiftfoxSection(
        account: OAuthAccount,
        rethrowOnError: Boolean
    ): SwiftfoxSectionOutcome {
        return try {
            val tokenInfo = account.getAccessToken(SCOPE_SYNC)
            val tokenUrl = account.getTokenServerEndpointURL()
            if (tokenInfo != null && tokenUrl != null && tokenInfo.key != null) {
                val syncAuth = tokenInfo.asSyncAuthInfo(tokenUrl)
                when (
                    val sfResult = swiftfoxClient.sync(
                        kid = syncAuth.kid,
                        fxaAccessToken = syncAuth.fxaAccessToken,
                        syncKeyBase64 = syncAuth.syncKey,
                        tokenServerUrl = syncAuth.tokenServerUrl
                    )
                ) {
                    is SwiftfoxSyncResult.Success -> {
                        if (sfResult.localDataChanged) {
                            _swiftfoxDataRevision.update { it + 1L }
                        }
                        SwiftfoxSectionOutcome(
                            note = formatSwiftfoxResultNote(sfResult),
                            succeeded = true,
                            networkCompleted = true
                        )
                    }
                    is SwiftfoxSyncResult.KeysNotInitialized -> {
                        SwiftfoxSectionOutcome(
                            note = sfResult.message,
                            succeeded = false,
                            networkCompleted = true
                        )
                    }
                }
            } else {
                SwiftfoxSectionOutcome(
                    note = "Could not obtain Sync key for Swiftfox data",
                    succeeded = false,
                    networkCompleted = false
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (sfErr: Throwable) {
            if (rethrowOnError) throw sfErr
            SwiftfoxSectionOutcome(
                note = "Swiftfox sync issue: ${sfErr.message ?: "error"}",
                succeeded = false,
                networkCompleted = false
            )
        }
    }

    private fun formatSwiftfoxResultNote(sfResult: SwiftfoxSyncResult.Success): String {
        val baseNote = when {
            sfResult.pulledCount == 0 && sfResult.pushedCount == 0 ->
                "Swiftfox client data up to date"
            else -> buildList {
                if (sfResult.pulledCount > 0) add("pulled ${sfResult.pulledCount}")
                if (sfResult.pushedCount > 0) add("pushed ${sfResult.pushedCount}")
            }.joinToString(" · ", prefix = "Swiftfox sync: ")
        }
        return if (sfResult.skippedOversizedCount > 0) {
            val itemLabel = if (sfResult.skippedOversizedCount == 1) "item" else "items"
            "$baseNote (${sfResult.skippedOversizedCount} oversized $itemLabel skipped)"
        } else {
            baseNote
        }
    }

    fun mirrorZenPinnedUpdated(item: ZenPinnedItemEntity) {
        val tabId = ZenSpacesCodec.extractTabId(item.id, item.zenWorkspaceUuid) ?: return
        enqueueZenOpAndFlushAsync(
            ZenSpacesCodec.ZenPendingOp.UpdatePin(
                workspaceUuid = item.zenWorkspaceUuid,
                tabId = tabId,
                title = item.title,
                url = item.url,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun mirrorZenPinnedDeleted(item: ZenPinnedItemEntity) {
        val tabId = ZenSpacesCodec.extractTabId(item.id, item.zenWorkspaceUuid) ?: return
        enqueueZenOpAndFlushAsync(
            ZenSpacesCodec.ZenPendingOp.DeletePin(
                workspaceUuid = item.zenWorkspaceUuid,
                tabId = tabId,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun mirrorZenPinnedReordered(workspaceUuid: String, orderedItems: List<ZenPinnedItemEntity>) {
        if (workspaceUuid.isBlank()) return
        val orderedTabIds = orderedItems.mapNotNull { item ->
            if (item.zenWorkspaceUuid != workspaceUuid) null
            else ZenSpacesCodec.extractTabId(item.id, workspaceUuid)
        }
        if (orderedTabIds.size < 2) return
        enqueueZenOpAndFlushAsync(
            ZenSpacesCodec.ZenPendingOp.ReorderPins(
                workspaceUuid = workspaceUuid,
                orderedTabIds = orderedTabIds,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun onZenSpacesSyncEnabledChanged(enabled: Boolean) {
        lastKnownZenSpacesSyncEnabled = enabled
        if (!enabled) {
            scope.launch(Dispatchers.IO) {
                zenPendingOpsStore.clear()
            }
        }
    }

    private fun enqueueZenOpAndFlushAsync(op: ZenSpacesCodec.ZenPendingOp) {
        if (accountManager?.connectedAccount() == null) return
        if (!lastKnownZenSpacesSyncEnabled) return
        val gen = authGeneration.get()
        zenPendingOpsStore.enqueueBuffered(op)
        scope.launch(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()
            lastKnownZenSpacesSyncEnabled = settings.syncZenSpacesEnabled
            if (!settings.syncZenSpacesEnabled) {
                zenPendingOpsStore.clear()
                return@launch
            }
            zenPendingOpsStore.persistPendingToDisk()
            zenMutationMutex.withLock {
                if (authGeneration.get() != gen || !lastKnownZenSpacesSyncEnabled) return@withLock
                val account = accountManager?.connectedAccount() ?: return@withLock
                val pendingOps = zenPendingOpsStore.snapshotPending()
                if (pendingOps.isEmpty()) return@withLock
                runCatching {
                    val tokenInfo = account.getAccessToken(SCOPE_SYNC) ?: return@runCatching
                    val tokenUrl = account.getTokenServerEndpointURL() ?: return@runCatching
                    if (tokenInfo.key == null) return@runCatching
                    val syncAuth = tokenInfo.asSyncAuthInfo(tokenUrl)
                    zenClient.applyPendingMutations(
                        kid = syncAuth.kid,
                        fxaAccessToken = syncAuth.fxaAccessToken,
                        syncKeyBase64 = syncAuth.syncKey,
                        tokenServerUrl = syncAuth.tokenServerUrl,
                        ops = pendingOps
                    ).getOrThrow()
                    zenPendingOpsStore.removeApplied(pendingOps)
                }
            }
        }
    }

    // Live write-through helpers (no-op cleanly if signed out or corresponding engine disabled)
    fun mirrorBookmarkAdded(entity: BookmarkEntity) {
        mirrorBookmarksBatchAdded(listOf(entity))
    }

    fun mirrorBookmarksBatchAdded(entities: List<BookmarkEntity>) {
        if (entities.isEmpty()) return
        val store = bookmarksStorage ?: return
        if (accountManager?.connectedAccount() == null) return
        val gen = authGeneration.get()
        scope.launch(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()
            if (!settings.syncBookmarksEnabled) return@launch
            bookmarkMirrorMutex.withLock {
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@withLock
                val folderCache = mutableMapOf<String, String>()
                val pendingUpdates = ArrayList<Pair<BookmarkEntity, String>>(minOf(entities.size, 64))

                suspend fun flushChunk() {
                    if (pendingUpdates.isEmpty()) return
                    val toVerify = pendingUpdates.toList()
                    pendingUpdates.clear()
                    val cancelledGuids = mutableListOf<String>()
                    db.withTransaction {
                        val validUpserts = ArrayList<BookmarkEntity>(toVerify.size)
                        for ((updatedEntity, createdGuid) in toVerify) {
                            if (db.bookmarkDao().findById(updatedEntity.id) == null) {
                                cancelledGuids += createdGuid
                            } else {
                                validUpserts += updatedEntity
                            }
                        }
                        if (validUpserts.isNotEmpty()) {
                            db.bookmarkDao().upsertAll(validUpserts)
                        }
                    }
                    cancelledGuids.forEach { guid -> runCatching { store.deleteNode(guid) } }
                }

                for (entity in entities) {
                    if (authGeneration.get() != gen) break
                    val stillPresent = db.bookmarkDao().findById(entity.id) ?: continue
                    if (stillPresent.syncGuid != null) continue
                    val parentGuid = folderCache.getOrPut(stillPresent.folder) {
                        ensurePlacesFolderPath(store, stillPresent.folder)
                    }
                    val createdGuid = store.addItem(
                        parentGuid = parentGuid,
                        url = stillPresent.url,
                        title = stillPresent.title,
                        position = null
                    ).getOrNull() ?: continue
                    if (authGeneration.get() != gen) break
                    pendingUpdates += stillPresent.copy(
                        syncGuid = createdGuid,
                        parentGuid = parentGuid,
                        updatedAt = System.currentTimeMillis()
                    ) to createdGuid
                    if (pendingUpdates.size >= 64) {
                        flushChunk()
                    }
                }
                if (authGeneration.get() == gen) {
                    flushChunk()
                }
            }
        }
    }

    fun mirrorBookmarkDeleted(entity: BookmarkEntity) {
        val guid = entity.syncGuid ?: return
        val store = bookmarksStorage ?: return
        if (accountManager?.connectedAccount() == null) return
        val gen = authGeneration.get()
        pendingDeletedBookmarkGuids.add(guid)
        scope.launch(Dispatchers.IO) {
            try {
                val settings = prefs.settingsFlow.first()
                if (!settings.syncBookmarksEnabled) return@launch
                bookmarkMirrorMutex.withLock {
                    if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@withLock
                    runCatching { store.deleteNode(guid) }
                }
            } finally {
                pendingDeletedBookmarkGuids.remove(guid)
            }
        }
    }

    fun mirrorHistoryVisit(url: String, title: String) {
        if (accountManager?.connectedAccount() == null) return
        val gen = authGeneration.get()
        scope.launch(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()
            if (!settings.syncHistoryEnabled) return@launch
            historyMirrorMutex.withLock {
                val store = historyStorage ?: return@withLock
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@withLock
                if (!store.canAddUri(url)) return@withLock
                runCatching {
                    store.recordVisit(url, PageVisit(VisitType.LINK))
                    if (title.isNotBlank()) {
                        store.recordObservation(url, PageObservation(title = title))
                    }
                }
            }
        }
    }

    fun mirrorHistoryTitleUpdated(url: String, title: String) {
        if (title.isBlank() || accountManager?.connectedAccount() == null) return
        val gen = authGeneration.get()
        scope.launch(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()
            if (!settings.syncHistoryEnabled) return@launch
            historyMirrorMutex.withLock {
                val store = historyStorage ?: return@withLock
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@withLock
                if (!store.canAddUri(url)) return@withLock
                runCatching {
                    store.recordObservation(url, PageObservation(title = title))
                }
            }
        }
    }

    fun mirrorHistoryDeleted(url: String) {
        if (accountManager?.connectedAccount() == null) return
        val gen = authGeneration.get()
        scope.launch(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()
            if (!settings.syncHistoryEnabled) return@launch
            historyMirrorMutex.withLock {
                val store = historyStorage ?: return@withLock
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@withLock
                runCatching { store.deleteVisitsFor(url) }
            }
        }
    }

    fun mirrorHistoryCleared() {
        if (accountManager?.connectedAccount() == null) return
        val gen = authGeneration.get()
        scope.launch(Dispatchers.IO) {
            val settings = prefs.settingsFlow.first()
            if (!settings.syncHistoryEnabled) return@launch
            historyMirrorMutex.withLock {
                val store = historyStorage ?: return@withLock
                if (authGeneration.get() != gen || accountManager?.connectedAccount() == null) return@withLock
                runCatching { store.deleteEverything() }
            }
        }
    }

    fun updateEnginePreferences(
        bookmarks: Boolean,
        history: Boolean,
        tabs: Boolean
    ) {
        runCatching {
            val enginesStorage = SyncEnginesStorage(context)
            enginesStorage.setStatus(SyncEngine.Bookmarks, bookmarks)
            enginesStorage.setStatus(SyncEngine.History, history)
            enginesStorage.setStatus(SyncEngine.Tabs, tabs)
        }
    }

    fun updatePeriodicSchedule(intervalMinutes: Int) {
        if (accountManager?.connectedAccount() != null) {
            SwiftfoxSyncWorker.schedulePeriodic(context, intervalMinutes)
        } else {
            SwiftfoxSyncWorker.cancel(context)
        }
    }

    private suspend fun pushUnsyncedBookmarksToPlaces() {
        val store = bookmarksStorage ?: return
        val allBookmarks = db.bookmarkDao().getAll()
        val claimedGuids = allBookmarks.mapNotNullTo(mutableSetOf()) { it.syncGuid }
        val localUnsynced = allBookmarks.filter { it.syncGuid == null }
        if (localUnsynced.isEmpty()) return
        val folderCache = mutableMapOf<String, String>()
        data class StagedPush(val entity: BookmarkEntity, val guid: String, val createdNew: Boolean)
        val staged = ArrayList<StagedPush>(localUnsynced.size)

        for (bm in localUnsynced) {
            val currentRow = db.bookmarkDao().findById(bm.id) ?: continue
            if (currentRow.syncGuid != null) continue
            val existingRemote = store.getBookmarksWithUrl(currentRow.url).getOrNull()
                .orEmpty()
                .firstOrNull { it.guid !in claimedGuids }
            val resolvedParentGuid: String
            val guid: String?
            val createdNew: Boolean
            if (existingRemote != null) {
                guid = existingRemote.guid
                resolvedParentGuid = existingRemote.parentGuid ?: BookmarkRoot.Mobile.id
                createdNew = false
            } else {
                resolvedParentGuid = folderCache.getOrPut(currentRow.folder) {
                    ensurePlacesFolderPath(store, currentRow.folder)
                }
                guid = store.addItem(resolvedParentGuid, currentRow.url, currentRow.title, null).getOrNull()
                createdNew = true
            }
            if (guid != null) {
                claimedGuids += guid
                staged += StagedPush(
                    entity = currentRow.copy(syncGuid = guid, parentGuid = resolvedParentGuid),
                    guid = guid,
                    createdNew = createdNew
                )
            }
        }
        if (staged.isEmpty()) return
        val rollbackGuids = mutableListOf<String>()
        db.withTransaction {
            val validUpserts = ArrayList<BookmarkEntity>(staged.size)
            for (item in staged) {
                if (db.bookmarkDao().findById(item.entity.id) == null) {
                    if (item.createdNew) rollbackGuids += item.guid
                } else {
                    validUpserts += item.entity
                }
            }
            if (validUpserts.isNotEmpty()) {
                db.bookmarkDao().upsertAll(validUpserts)
            }
        }
        rollbackGuids.forEach { guid -> runCatching { store.deleteNode(guid) } }
    }

    private suspend fun pushRecentLocalHistoryToPlaces() {
        val store = historyStorage ?: return
        val localRecent = db.historyDao().getRecent(limit = 200)
        for (entry in localRecent) {
            if (!entry.url.startsWith("http://") && !entry.url.startsWith("https://")) continue
            if (!store.canAddUri(entry.url)) continue
            runCatching {
                store.recordVisit(entry.url, PageVisit(VisitType.LINK))
                if (entry.title.isNotBlank()) {
                    store.recordObservation(entry.url, PageObservation(title = entry.title))
                }
            }
        }
    }

    private suspend fun ensurePlacesFolderPath(
        store: PlacesBookmarksStorage,
        folderPath: String
    ): String {
        val segments = folderPath.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty() || (segments.first().equals("Bookmarks", ignoreCase = true) && segments.size == 1)) {
            return BookmarkRoot.Mobile.id
        }
        val firstLower = segments.first().lowercase(Locale.ROOT)
        var currentGuid = when (firstLower) {
            "bookmarks toolbar", "bookmarks bar", "favorites bar", "toolbar" -> BookmarkRoot.Toolbar.id
            "bookmarks menu", "menu" -> BookmarkRoot.Menu.id
            "other bookmarks", "unfiled bookmarks", "unfiled" -> BookmarkRoot.Unfiled.id
            "mobile bookmarks", "mobile" -> BookmarkRoot.Mobile.id
            else -> BookmarkRoot.Mobile.id
        }
        val remaining = if (
            firstLower in setOf(
                "bookmarks toolbar", "bookmarks bar", "favorites bar", "toolbar",
                "bookmarks menu", "menu",
                "other bookmarks", "unfiled bookmarks", "unfiled",
                "mobile bookmarks", "mobile",
                "bookmarks"
            )
        ) {
            segments.drop(1)
        } else {
            segments
        }

        for (name in remaining) {
            val tree = store.getTree(currentGuid, recursive = false).getOrNull()
            val match = tree?.children?.firstOrNull {
                it.type == BookmarkNodeType.FOLDER && it.title.equals(name, ignoreCase = true)
            }
            currentGuid = match?.guid ?: (store.addFolder(currentGuid, name, null).getOrNull() ?: currentGuid)
        }
        return currentGuid
    }

    private suspend fun pushLocalOpenTabsToPlaces() {
        val store = remoteTabsStorage ?: return
        val tabs = db.tabDao().getNormalTabs()
            .filter { !it.isUnloaded && (it.url.startsWith("http://") || it.url.startsWith("https://")) }
            .map { entity ->
                MozTab(
                    history = listOf(
                        TabEntry(
                            title = entity.title.ifBlank { OmniboxEngine.extractHost(entity.url) },
                            url = entity.url,
                            iconUrl = null
                        )
                    ),
                    active = 0,
                    lastUsed = entity.lastAccessedAt,
                    inactive = false
                )
            }
        store.store(tabs)
    }

    private suspend fun pullPlacesBookmarksIntoRoom(): Int {
        val store = bookmarksStorage ?: return 0
        val rootTree = store.getTree(BookmarkRoot.Root.id, recursive = true).getOrNull() ?: return 0

        data class RemoteBm(
            val guid: String,
            val parentGuid: String?,
            val title: String,
            val url: String,
            val folder: String,
            val dateAdded: Long,
            val lastModified: Long
        )

        val remoteList = mutableListOf<RemoteBm>()

        fun walk(node: BookmarkNode, folderPath: String) {
            when (node.type) {
                BookmarkNodeType.ITEM -> {
                    val u = node.url.orEmpty()
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        remoteList += RemoteBm(
                            guid = node.guid,
                            parentGuid = node.parentGuid,
                            title = node.title?.takeIf { it.isNotBlank() } ?: OmniboxEngine.extractHost(u),
                            url = u,
                            folder = folderPath.ifBlank { "Bookmarks" },
                            dateAdded = node.dateAdded,
                            lastModified = node.lastModified
                        )
                    }
                }
                BookmarkNodeType.FOLDER -> {
                    val nextPath = when (node.guid) {
                        BookmarkRoot.Root.id -> ""
                        BookmarkRoot.Toolbar.id -> "Bookmarks Toolbar"
                        BookmarkRoot.Menu.id -> "Bookmarks Menu"
                        BookmarkRoot.Mobile.id -> "Mobile Bookmarks"
                        BookmarkRoot.Unfiled.id -> "Other Bookmarks"
                        else -> {
                            val name = node.title?.trim().orEmpty()
                            when {
                                folderPath.isBlank() -> name
                                name.isBlank() -> folderPath
                                else -> "$folderPath / $name"
                            }
                        }
                    }
                    node.children.orEmpty().forEach { walk(it, nextPath) }
                }
                BookmarkNodeType.SEPARATOR -> Unit
            }
        }
        walk(rootTree, "")

        return db.withTransaction {
            val existingAll = db.bookmarkDao().getAll()
            val bySyncGuid = existingAll.filter { it.syncGuid != null }.associateBy { it.syncGuid!! }
            val unsyncedByUrl = existingAll
                .filter { it.syncGuid == null }
                .groupBy { it.url }
                .mapValues { it.value.toMutableList() }

            val upserts = mutableListOf<BookmarkEntity>()
            val seenGuids = mutableSetOf<String>()
            val claimedLocalIds = mutableSetOf<String>()
            val now = System.currentTimeMillis()
            val pendingDeletes = pendingDeletedBookmarkGuids.toSet()

            for (r in remoteList) {
                if (r.guid in pendingDeletes) {
                    continue
                }
                seenGuids += r.guid
                val normalizedUrl = OmniboxEngine.canonicalizeUrlSchemeAndHost(r.url)
                val matchedExisting = bySyncGuid[r.guid]?.takeIf { claimedLocalIds.add(it.id) }
                    ?: unsyncedByUrl[normalizedUrl]?.firstOrNull { claimedLocalIds.add(it.id) }
                    ?: unsyncedByUrl[r.url]?.firstOrNull { claimedLocalIds.add(it.id) }

                upserts += if (matchedExisting != null) {
                    matchedExisting.copy(
                        title = r.title,
                        url = normalizedUrl,
                        folder = r.folder,
                        syncGuid = r.guid,
                        parentGuid = r.parentGuid,
                        updatedAt = if (r.lastModified > 0L) r.lastModified else now
                    )
                } else {
                    BookmarkEntity(
                        id = "fx_${r.guid}",
                        title = r.title,
                        url = normalizedUrl,
                        folder = r.folder,
                        createdAt = if (r.dateAdded > 0L) r.dateAdded else now,
                        syncGuid = r.guid,
                        parentGuid = r.parentGuid,
                        updatedAt = if (r.lastModified > 0L) r.lastModified else now
                    )
                }
            }

            if (upserts.isNotEmpty()) {
                db.bookmarkDao().upsertAll(upserts)
            }
            // Delete only bookmarks previously synced that were removed remotely
            val idsToDelete = existingAll.mapNotNull { existing ->
                val sg = existing.syncGuid
                if (sg != null && sg !in seenGuids) existing.id else null
            }
            for (chunk in idsToDelete.chunked(400)) {
                db.bookmarkDao().deleteByIds(chunk)
            }
            seenGuids.size
        }
    }

    private suspend fun pullPlacesHistoryIntoRoom(): Int {
        val store = historyStorage ?: return 0
        val visits = store.getVisitsPaginated(offset = 0L, count = 400L, excludeTypes = emptyList())
        val httpVisits = visits.filter {
            it.url.startsWith("http://", ignoreCase = true) || it.url.startsWith("https://", ignoreCase = true)
        }
        if (httpVisits.isEmpty()) return 0

        val visitCountsInWindow = HashMap<String, Int>(httpVisits.size)
        for (v in httpVisits) {
            val normalizedUrl = OmniboxEngine.canonicalizeUrlSchemeAndHost(v.url)
            visitCountsInWindow[normalizedUrl] = (visitCountsInWindow[normalizedUrl] ?: 0) + 1
        }

        val distinctUrls = visitCountsInWindow.keys.toList()
        val existing = buildMap {
            for (chunk in distinctUrls.chunked(200)) {
                db.historyDao().findByUrls(chunk).forEach { put(it.url, it) }
            }
        }

        val deduped = linkedMapOf<String, HistoryEntity>()
        for (v in httpVisits) {
            val normalizedUrl = OmniboxEngine.canonicalizeUrlSchemeAndHost(v.url)
            val prev = deduped[normalizedUrl] ?: existing[normalizedUrl]
            val resolvedTitle = v.title?.takeIf { it.isNotBlank() }
                ?: prev?.title
                ?: OmniboxEngine.extractHost(normalizedUrl)
            val visitTime = maxOf(v.visitTime, prev?.lastVisitedAt ?: 0L)
            val remoteWindowCount = visitCountsInWindow[normalizedUrl] ?: 1
            deduped[normalizedUrl] = HistoryEntity(
                url = normalizedUrl,
                title = resolvedTitle,
                visitCount = maxOf(remoteWindowCount, prev?.visitCount ?: 0).coerceAtLeast(1),
                lastVisitedAt = visitTime
            )
        }
        if (deduped.isNotEmpty()) {
            db.historyDao().upsertAll(deduped.values.toList())
        }
        return deduped.size
    }

    private suspend fun pullRemoteTabsIntoRoom(account: OAuthAccount): Int {
        val store = remoteTabsStorage ?: return 0
        runCatching { account.deviceConstellation().refreshDevices() }
        val constellation = account.deviceConstellation().state()
        val devicesById = constellation?.otherDevices.orEmpty().associateBy { it.id }
        val currentDeviceId = constellation?.currentDevice?.id

        val allRemote = store.getAll()
        val needsTabsStoreFallback = allRemote.any { (client, _) ->
            client.id != currentDeviceId && devicesById[client.id]?.displayName.isNullOrBlank()
        }
        val clientMetaById = if (needsTabsStoreFallback) {
            runCatching {
                val tabsDbPath = File(context.filesDir, "tabs.sqlite").canonicalPath
                TabsStore(tabsDbPath).use { tabsStore ->
                    tabsStore.getAll().associate { crt ->
                        crt.clientId to (crt.clientName to crt.deviceType.name)
                    }
                }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val entities = mutableListOf<SyncedRemoteTabEntity>()
        for ((client, tabs) in allRemote) {
            if (client.id == currentDeviceId) continue
            val device = devicesById[client.id]
            val richMeta = clientMetaById[client.id]
            val deviceName = device?.displayName?.takeIf { it.isNotBlank() }
                ?: richMeta?.first?.takeIf { it.isNotBlank() }
                ?: "Firefox Device"
            val deviceType = device?.deviceType?.name?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
                ?: richMeta?.second?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
                ?: "DESKTOP"
            tabs.forEachIndexed { idx, tab ->
                val activeEntry = runCatching { tab.active() }.getOrNull() ?: return@forEachIndexed
                if (!activeEntry.url.startsWith("http://") && !activeEntry.url.startsWith("https://")) {
                    return@forEachIndexed
                }
                entities += SyncedRemoteTabEntity(
                    id = "${client.id}_${idx}_${activeEntry.url.hashCode()}",
                    deviceId = client.id,
                    deviceName = deviceName,
                    deviceType = deviceType,
                    title = activeEntry.title.ifBlank { OmniboxEngine.extractHost(activeEntry.url) },
                    url = activeEntry.url,
                    iconUrl = activeEntry.iconUrl,
                    lastUsedAt = tab.lastUsed,
                    position = idx
                )
            }
        }
        db.syncedRemoteTabDao().replaceAll(entities)
        return entities.size
    }

    private suspend fun reconcileZenSnapshotIntoRoom(
        snapshot: ZenSpacesCodec.ZenSyncSnapshot
    ): Int {
        return db.withTransaction {
            val existingSpaces = db.spaceDao().getAll()
            val existingBySyncGuid = existingSpaces.filter { it.syncGuid != null }.associateBy { it.syncGuid!! }
            val existingById = existingSpaces.associateBy { it.id }

            val zenSpaceIdMap = mutableMapOf<String, String>()
            val syncedSpaceEntities = mutableListOf<SpaceEntity>()
            val seenSpaceSyncGuids = mutableSetOf<String>()

            snapshot.spaces.forEachIndexed { idx, zenSpace ->
                seenSpaceSyncGuids += zenSpace.uuid
                val prev = existingBySyncGuid[zenSpace.uuid] ?: existingById["zen_${zenSpace.uuid}"]
                val localId = prev?.id ?: "zen_${zenSpace.uuid}"
                zenSpaceIdMap[zenSpace.uuid] = localId
                val prevIconKey = prev?.iconKey
                val resolvedIconKey = if (CustomIconSpec.isExplicitCustomIcon(prevIconKey)) {
                    prevIconKey!!
                } else {
                    ZenSpacesCodec.mapZenIconToSwiftfoxKey(zenSpace.icon, zenSpace.name)
                }
                syncedSpaceEntities += SpaceEntity(
                    id = localId,
                    name = zenSpace.name,
                    iconKey = resolvedIconKey,
                    sortOrder = idx,
                    createdAt = prev?.createdAt ?: System.currentTimeMillis(),
                    syncGuid = zenSpace.uuid,
                    themeColorHex = zenSpace.themeColorHex,
                    zenContainerGuid = zenSpace.containerGuid
                )
            }

            val syncedSpaceIds = syncedSpaceEntities.mapTo(mutableSetOf()) { it.id }
            val localOnlySpaces = existingSpaces.filter { it.syncGuid == null && it.id !in syncedSpaceIds }

            if (syncedSpaceEntities.isNotEmpty()) {
                db.spaceDao().upsertAll(syncedSpaceEntities)
                // Keep local-only spaces sorted after Zen synced spaces
                val shiftedLocal = localOnlySpaces.mapIndexed { idx, sp ->
                    sp.copy(sortOrder = syncedSpaceEntities.size + idx)
                }
                if (shiftedLocal.isNotEmpty()) db.spaceDao().upsertAll(shiftedLocal)
            }

            val ensuredDefaultSpace = if (syncedSpaceEntities.isEmpty() && localOnlySpaces.isEmpty()) {
                SpaceEntity(
                    id = SwiftfoxDatabase.DEFAULT_SPACE_ID,
                    name = "Personal",
                    iconKey = "home",
                    sortOrder = 0
                ).also { db.spaceDao().upsert(it) }
            } else {
                null
            }

            val fallbackSpaceId = syncedSpaceEntities.firstOrNull()?.id
                ?: localOnlySpaces.firstOrNull()?.id
                ?: ensuredDefaultSpace?.id
                ?: SwiftfoxDatabase.DEFAULT_SPACE_ID

            // Capture existing Zen Pinned items before deleting removed Spaces so bound tabs are also cleaned up
            val existingPinnedById = db.zenPinnedItemDao().getAll().associateBy { it.id }

            for (oldSynced in existingSpaces.filter { it.syncGuid != null && it.syncGuid !in seenSpaceSyncGuids }) {
                // Migrate open regular/pinned tabs out of removed synced Spaces so tabs are never orphaned
                db.tabDao().reassignSpace(oldSynced.id, fallbackSpaceId)
                db.zenPinnedItemDao().deleteBySpace(oldSynced.id)
                db.spaceDao().deleteById(oldSynced.id)
            }

            // Reconcile Zen Essentials
            val existingEssentials = db.essentialDao().getAll()
            val essBySyncGuid = existingEssentials.filter { it.syncGuid != null }.associateBy { it.syncGuid!! }
            val essById = existingEssentials.associateBy { it.id }
            val syncedEssentials = mutableListOf<EssentialEntity>()
            val seenEssSyncGuids = mutableSetOf<String>()

            var essIndex = 0
            for ((bucket, entries) in snapshot.essentialsByContainer) {
                for (entry in entries) {
                    seenEssSyncGuids += entry.tabId
                    val prev = essBySyncGuid[entry.tabId] ?: essById["zen_ess_${entry.tabId}"]
                    val host = OmniboxEngine.extractHost(entry.url).ifBlank { entry.url }
                    syncedEssentials += EssentialEntity(
                        id = prev?.id ?: "zen_ess_${entry.tabId}",
                        spaceId = null,
                        title = entry.title,
                        url = entry.url,
                        domain = host,
                        sortOrder = essIndex++,
                        syncGuid = entry.tabId,
                        zenContainerGuid = bucket,
                        customIcon = prev?.customIcon
                    )
                }
            }
            val syncedEssIds = syncedEssentials.mapTo(mutableSetOf()) { it.id }
            val localOnlyEss = existingEssentials.filter { it.syncGuid == null && it.id !in syncedEssIds }
            if (syncedEssentials.isNotEmpty()) {
                db.essentialDao().upsertAll(syncedEssentials)
                val shiftedLocalEss = localOnlyEss.mapIndexed { idx, ess ->
                    ess.copy(sortOrder = syncedEssentials.size + idx)
                }
                if (shiftedLocalEss.isNotEmpty()) db.essentialDao().upsertAll(shiftedLocalEss)
            }
            for (oldEss in existingEssentials.filter { it.syncGuid != null && it.syncGuid !in seenEssSyncGuids }) {
                db.tabDao().deleteByEssentialId(oldEss.id)
                db.essentialDao().deleteById(oldEss.id)
            }

            // Replace Zen Pinned items per space while preserving local custom icons
            val pinnedEntities = mutableListOf<ZenPinnedItemEntity>()
            for ((workspaceUuid, entries) in snapshot.pinnedBySpace) {
                val spaceId = zenSpaceIdMap[workspaceUuid] ?: continue
                entries.forEachIndexed { idx, entry ->
                    val pinnedId = "${workspaceUuid}_${entry.tabId}"
                    val prevPinned = existingPinnedById[pinnedId]
                    pinnedEntities += ZenPinnedItemEntity(
                        id = pinnedId,
                        spaceId = spaceId,
                        zenWorkspaceUuid = workspaceUuid,
                        title = entry.title,
                        url = entry.url,
                        folderName = entry.folderName,
                        isPinned = entry.isPinned,
                        sortOrder = idx,
                        customIcon = prevPinned?.customIcon
                    )
                }
            }
            val newPinnedIds = pinnedEntities.mapTo(mutableSetOf()) { it.id }
            existingPinnedById.keys
                .filterNot { it in newPinnedIds }
                .forEach { removedPinnedId ->
                    db.tabDao().deleteByZenPinnedId(removedPinnedId)
                }
            db.zenPinnedItemDao().replaceAll(pinnedEntities)

            syncedSpaceEntities.size
        }
    }

    data class PlacesOmniboxMatch(
        val url: String,
        val title: String,
        val score: Int,
        val fromAutocomplete: Boolean
    )

    /**
     * Queries the full on-device Mozilla `PlacesHistoryStorage` directly so synced desktop
     * Zen/Firefox history beyond the 400-visit Room mirror still powers omnibox autocomplete
     * and suggestions. Safe to call during sign-out or when signed out (returns empty).
     */
    suspend fun querySyncedHistoryForOmnibox(
        query: String,
        limit: Int = 10
    ): List<PlacesOmniboxMatch> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || limit <= 0) return@withContext emptyList()
        if (trimmed.startsWith("!") || trimmed.startsWith("@")) return@withContext emptyList()
        if (isSigningOut.get()) return@withContext emptyList()
        val mgr = accountManager ?: return@withContext emptyList()
        if (mgr.connectedAccount() == null) return@withContext emptyList()
        val settings = try {
            prefs.settingsFlow.first()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return@withContext emptyList()
        }
        if (!settings.syncHistoryEnabled) return@withContext emptyList()
        if (isSigningOut.get()) return@withContext emptyList()
        val store = historyStorage ?: return@withContext emptyList()

        val indexByUrl = HashMap<String, Int>(limit + 2)
        val matches = ArrayList<PlacesOmniboxMatch>(limit + 2)

        fun isHttpUrl(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

        fun urlKey(url: String): String = url.trimEnd('/').lowercase(Locale.ROOT)

        try {
            val autoHit = store.getAutocompleteSuggestion(trimmed)
            val autoUrl = autoHit?.url?.trim().orEmpty()
            if (autoUrl.isNotEmpty() && isHttpUrl(autoUrl)) {
                val key = urlKey(autoUrl)
                val fallbackTitle = OmniboxEngine.extractHost(autoUrl)
                    .ifBlank { autoHit?.text?.trim()?.trimEnd('/').orEmpty() }
                    .ifBlank { autoUrl }
                indexByUrl[key] = matches.size
                matches += PlacesOmniboxMatch(
                    url = autoUrl,
                    title = fallbackTitle,
                    score = 1000,
                    fromAutocomplete = true
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Places store may be resetting during sign-out or unavailable; degrade gracefully.
        }

        if (isSigningOut.get()) return@withContext emptyList()

        try {
            val searchResults = store.getSuggestions(trimmed, limit)
            for (hit in searchResults) {
                val url = hit.url.trim()
                if (url.isEmpty() || !isHttpUrl(url)) continue
                val key = urlKey(url)
                val resolvedTitle = hit.title?.takeIf { it.isNotBlank() }
                val existingIdx = indexByUrl[key]
                if (existingIdx != null) {
                    val existing = matches[existingIdx]
                    if (resolvedTitle != null) {
                        matches[existingIdx] = existing.copy(
                            title = resolvedTitle,
                            score = maxOf(existing.score, hit.score)
                        )
                    }
                } else {
                    indexByUrl[key] = matches.size
                    matches += PlacesOmniboxMatch(
                        url = url,
                        title = resolvedTitle ?: OmniboxEngine.extractHost(url).ifBlank { url },
                        score = hit.score,
                        fromAutocomplete = false
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Ignore transient Places reader errors.
        }

        matches
    }

    private fun updateConnectedFromProfile(profile: Profile?) {
        _status.update {
            it.copy(
                accountState = SyncAccountState.Connected(
                    email = profile?.email,
                    displayName = profile?.displayName
                )
            )
        }
    }

    companion object {
        const val FENIX_CLIENT_ID = "a2270f727f45f648"
        const val FENIX_REDIRECT_URI = "https://accounts.firefox.com/oauth/success/a2270f727f45f648"
        const val FXA_AUTH_PROFILE_NAME = "swiftfox_fxa_auth"
        private const val MOZILLA_IMMEDIATE_WORK_NAME = "Immediate"
        private const val SYNC_WAIT_TIMEOUT_MS = 60_000L
        private const val SWIFTFOX_PUSH_DEBOUNCE_MS = 1_500L
        private const val FOREGROUND_SYNC_COOLDOWN_MS = 120_000L

        fun isValidPairingUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return false
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
            val path = uri.path.orEmpty()
            val pathMatches = (path == "/pair" || path.startsWith("/pair/")) && !path.contains("..")
            return uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("accounts.firefox.com", ignoreCase = true) &&
                uri.port == -1 &&
                uri.userInfo == null &&
                pathMatches
        }
    }
}
