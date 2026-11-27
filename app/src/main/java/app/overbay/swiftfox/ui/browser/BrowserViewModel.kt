package app.overbay.swiftfox.ui.browser

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.DownloadRecordEntity
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.db.TabEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.WebAppEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.AppIconBackgroundMode
import app.overbay.swiftfox.data.prefs.AppIconColorStyle
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.ReaderFontFamily
import app.overbay.swiftfox.data.prefs.ReaderSurfaceMode
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.TabAutoClearDuration
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.engine.BackgroundMediaService
import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.GlobalPrepaintDarkConfig
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.NetworkErrorDiagnostics
import app.overbay.swiftfox.engine.ScriptInjector
import app.overbay.swiftfox.engine.SessionHostCallbacks
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.foldable.SiteModeResolver
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.icons.FaviconRepository
import app.overbay.swiftfox.features.icons.LauncherIconManager
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSearchScope
import app.overbay.swiftfox.features.sharing.CleanUrlSanitizer
import app.overbay.swiftfox.features.sync.ChromeBookmarkHtmlCodec
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher
import app.overbay.swiftfox.features.webapps.PageInstallCandidate
import app.overbay.swiftfox.features.webapps.WebAppDetector
import app.overbay.swiftfox.features.webapps.WebAppManifestParser
import app.overbay.swiftfox.features.webapps.WebAppShortcutManager
import app.overbay.swiftfox.features.zapper.ZapperUiState
import app.overbay.swiftfox.ui.reader.clearGlobalReaderImageCache
import app.overbay.swiftfox.ui.tabs.AutoClearedTabsSnapshot
import app.overbay.swiftfox.ui.tabs.PendingPinCloseTarget
import app.overbay.swiftfox.ui.tabs.PinnedTabKind
import app.overbay.swiftfox.ui.tabs.SidebarPinnedEntry
import app.overbay.swiftfox.ui.tabs.TabAutoClearPolicy
import app.overbay.swiftfox.ui.tabs.WorkspacePinnedFolderLogic
import app.overbay.swiftfox.ui.tabs.WorkspaceTabResolver
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mozilla.components.service.fxa.sync.SyncReason
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SwiftfoxApp
    private val db = app.database
    private val prefs = app.preferencesRepository
    private val pool = app.tabSessionPool

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            isSystemDark = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val omniboxHolder = OmniboxStateHolder(
        scope = viewModelScope,
        db = db,
        topDomainsRepository = app.topDomainsRepository,
        syncCoordinator = app.mozillaSyncCoordinator,
        browserStateProvider = { _uiState.value }
    )
    val omniboxState: StateFlow<OmniboxUiState> = omniboxHolder.state

    private val progressByTab = ConcurrentHashMap<String, Int>()
    private val _activeTabProgress = MutableStateFlow(0)
    val activeTabProgress: StateFlow<Int> = _activeTabProgress.asStateFlow()

    private val blockedCountByTab = ConcurrentHashMap<String, AtomicInteger>()
    private val _activeTabBlockedCount = MutableStateFlow(0)
    val activeTabBlockedCount: StateFlow<Int> = _activeTabBlockedCount.asStateFlow()

    private val _thumbnails = MutableStateFlow(TabThumbnailsSnapshot())
    val thumbnails: StateFlow<TabThumbnailsSnapshot> = _thumbnails.asStateFlow()

    // Activity bridges registered while MainActivity is active
    var onLaunchExternalIntentBridge: ((Intent) -> Boolean)? = null
    var onShowFileChooserBridge: ((ValueCallback<Array<Uri>>, Intent) -> Boolean)? = null
    var onRequestAndroidPermissionsBridge: ((List<String>, (Map<String, Boolean>) -> Unit) -> Unit)? = null
    var onShowFullscreenViewBridge: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null
    var onHideFullscreenViewBridge: (() -> Unit)? = null
    var onSystemDarkThemeProvider: (() -> Boolean)? = null
    var onImportUserscriptFileBridge: (() -> Unit)? = null
    var onImportBookmarksHtmlBridge: (() -> Unit)? = null
    var onExportBookmarksHtmlBridge: (() -> Unit)? = null

    private var readerExtractJob: Job? = null
    private var subframeDarkWarmupJob: Job? = null
    private val readerRequestCounter = AtomicLong(0L)
    private val incomingIntentLock = Any()
    private var initialTabsLoaded = false
    private val startupTabReconciliationDone = AtomicBoolean(false)
    private var hasReceivedInitialPosture = false
    private var hasAskedNotificationPermission = false
    private val pendingIncomingUrls = mutableListOf<String>()
    private val pendingProgrammaticHttpsUpgrades = ConcurrentHashMap<String, Pair<String, String>>()
    private val lastRecordedHistoryUrlByTab = ConcurrentHashMap<String, String>()
    private val lastRecordedHistoryTitleByTab = ConcurrentHashMap<String, String>()
    private var activeMediaTabId: String? = null
    private val snackbarCounter = AtomicLong(0L)
    private val blockedCountFlushScheduled = AtomicBoolean(false)
    private val filterUpdateInFlight = AtomicInteger(0)
    private val cosmeticExceptionsByTab = ConcurrentHashMap<String, Set<String>>()
    @Volatile
    private var enabledZappedRulesByHost: Map<String, List<ZappedRuleEntity>> = emptyMap()
    private val spaCheckJobByTab = ConcurrentHashMap<String, Job>()
    private val pageStartedInjectJobByTab = ConcurrentHashMap<String, Job>()
    private val pageFinishedInjectJobByTab = ConcurrentHashMap<String, Job>()
    private val loadErrorDiagJobByTab = ConcurrentHashMap<String, Job>()
    private val lastCookieFlushMsByProfile = ConcurrentHashMap<String, Long>()
    private val privateSessionGeneration = AtomicLong(0L)
    private val privateUserscriptStorageLock = Any()
    private val privateUserscriptStorage = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private val privateDeletedUserscriptKeys = ConcurrentHashMap<String, MutableSet<String>>()
    private val tabDbMutex = Mutex()
    private val siteOverrideWrites = SiteOverrideWriteCoordinator<Map<String, SiteOverrideEntity>>()
    private val syncPrefsMutex = Mutex()
    private val pendingCreatedSpaces = ConcurrentHashMap<String, SpaceEntity>()
    private val pendingCreatedEssentials = ConcurrentHashMap<String, EssentialEntity>()
    private val pendingCreatedPinnedFolders = ConcurrentHashMap<String, PinnedFolderEntity>()
    private val viewModelJson = Json { ignoreUnknownKeys = true }
    private val ioExceptionHandler = CoroutineExceptionHandler { _, err ->
        Log.w("BrowserViewModel", "Unhandled background coroutine error: ${err.message}")
    }

    private fun syncActiveTabProgressAndBlocked(activeId: String? = _uiState.value.activeTabId) {
        _activeTabProgress.value = if (activeId != null) (progressByTab[activeId] ?: 0) else 0
        _activeTabBlockedCount.value = if (activeId != null) (blockedCountByTab[activeId]?.get() ?: 0) else 0
    }

    private fun effectiveSiteOverride(host: String, isPrivate: Boolean): SiteOverrideEntity? {
        val state = _uiState.value
        val normalized = OmniboxEngine.normalizeHost(host)
        if (normalized.isBlank()) return null
        return if (isPrivate) {
            state.privateSiteOverrides[normalized] ?: state.siteOverrides[normalized]
        } else {
            state.siteOverrides[normalized]
        }
    }

    private fun mergePrivateUserscriptStorage(
        scriptsWithStorage: List<Pair<UserscriptEntity, Map<String, String>>>
    ): List<Pair<UserscriptEntity, Map<String, String>>> {
        if (scriptsWithStorage.isEmpty()) return scriptsWithStorage
        return synchronized(privateUserscriptStorageLock) {
            if (privateUserscriptStorage.isEmpty() && privateDeletedUserscriptKeys.isEmpty()) {
                return@synchronized scriptsWithStorage
            }
            scriptsWithStorage.map { (script, persisted) ->
                val ephemeral = privateUserscriptStorage[script.id]
                val deleted = privateDeletedUserscriptKeys[script.id]
                if (ephemeral.isNullOrEmpty() && deleted.isNullOrEmpty()) {
                    script to persisted
                } else {
                    val merged = LinkedHashMap<String, String>(persisted)
                    deleted?.forEach { merged.remove(it) }
                    ephemeral?.forEach { (k, v) -> merged[k] = v }
                    script to merged
                }
            }
        }
    }

    private val sessionCallbacks: SessionHostCallbacks = object : SessionHostCallbacks {
        override fun getHttpsOnlyEnabled(): Boolean = _uiState.value.settings.httpsOnlyMode

        override fun isHttpsBypassedForHost(tabId: String, host: String): Boolean {
            val state = _uiState.value
            val isPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate
                ?: state.activeTab?.isPrivate
                ?: state.isPrivateView
            return effectiveSiteOverride(host, isPrivate)?.httpsOnlyBypass == true
        }

        override fun getExternalAppPolicy(tabId: String, host: String): ExternalAppLinkPolicy {
            val state = _uiState.value
            val isPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate
                ?: state.activeTab?.isPrivate
                ?: state.isPrivateView
            val raw = effectiveSiteOverride(host, isPrivate)?.externalAppPolicyOverride
            return raw?.let { runCatching { ExternalAppLinkPolicy.valueOf(it) }.getOrNull() }
                ?: state.settings.externalAppLinkPolicy
        }

        override fun shouldBlockNetworkRequest(
            tabId: String,
            requestUrl: String,
            pageUrl: String,
            headers: Map<String, String>
        ): Boolean {
            val state = _uiState.value
            if (!state.settings.adblockEnabled) return false
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            val pageHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(pageUrl))
            return shouldBlockNetworkRequest(tabId, tabIsPrivate, requestUrl, pageUrl, pageHost, headers)
        }

        override fun shouldBlockNetworkRequest(
            tabId: String,
            isPrivate: Boolean,
            requestUrl: String,
            pageUrl: String,
            cachedPageHost: String,
            headers: Map<String, String>
        ): Boolean {
            val state = _uiState.value
            if (!state.settings.adblockEnabled) return false
            val siteDisabled = if (cachedPageHost.isEmpty()) {
                false
            } else if (isPrivate) {
                (state.privateSiteOverrides[cachedPageHost] ?: state.siteOverrides[cachedPageHost])?.adblockDisabled == true
            } else {
                state.siteOverrides[cachedPageHost]?.adblockDisabled == true
            }
            val blocked = app.sessionEngineCoordinator.shouldBlockNetworkRequest(
                requestUrl = requestUrl,
                pageUrl = pageUrl,
                headers = headers,
                globalEnabled = true,
                siteDisabled = siteDisabled
            )
            if (blocked) {
                val newTotal = blockedCountByTab
                    .computeIfAbsent(tabId) { AtomicInteger(0) }
                    .incrementAndGet()
                if (state.activeTabId == tabId && state.activeOverlay == BrowserOverlay.SETTINGS) {
                    scheduleActiveBlockedCountFlush()
                } else if (state.activeTabId == tabId && newTotal == 1) {
                    _activeTabBlockedCount.value = 1
                }
            }
            return blocked
        }

        override fun onPageStarted(
            tabId: String,
            url: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            if (url == OmniboxEngine.NEW_TAB_URL && pool.getExisting(tabId)?.isDestroyed != false) return
            ScriptInjector.extractOrigin(url)?.let { origin ->
                pool.getExisting(tabId)?.rotateTokensForOrigin(origin)
            }
            val isSameUrl = currentTab.url == url
            if (!isSameUrl) {
                app.userscriptBridge.cancelCallsForTab(tabId)
            }
            if (currentTab.isPrivate) {
                val prevScope = FaviconRepository.privateScopeKey(currentTab.url)
                val nextScope = FaviconRepository.privateScopeKey(url)
                if (prevScope != nextScope) {
                    app.faviconRepository.clearPrivateTabIcon(tabId)
                }
            }
            blockedCountByTab.remove(tabId)
            progressByTab[tabId] = 12
            cosmeticExceptionsByTab.remove(tabId)
            spaCheckJobByTab.remove(tabId)?.cancel()
            pageStartedInjectJobByTab.remove(tabId)?.cancel()
            pageFinishedInjectJobByTab.remove(tabId)?.cancel()
            loadErrorDiagJobByTab.remove(tabId)?.cancel()
            if (!isSameUrl && activeMediaTabId == tabId) {
                handoffOrStopMediaService(tabId)
            }
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url,
                    isLoading = true,
                    progress = 12,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isPlayingMedia = if (isSameUrl) tab.isPlayingMedia else false,
                    isPlayingVideo = if (isSameUrl) tab.isPlayingVideo else false,
                    isReaderAvailable = false,
                    isPwaInstallAvailable = false,
                    darkReaderSkippedNative = false,
                    loadError = null
                )
            }
            _uiState.update { s ->
                if (tabId in s.pwaCandidatesByTab) {
                    s.copy(pwaCandidatesByTab = s.pwaCandidatesByTab - tabId)
                } else {
                    s
                }
            }
            if (_uiState.value.activeTabId == tabId) {
                _activeTabProgress.value = 12
                _activeTabBlockedCount.value = 0
                readerExtractJob?.cancel()
                readerExtractJob = null
                pool.getExisting(tabId)?.clearFindMatches()
                val pendingPerm = _uiState.value.permissionPrompt
                if (pendingPerm != null && pendingPerm.tabId == tabId) {
                    denyPromptSilently(pendingPerm)
                }
                _uiState.update { s ->
                    s.copy(
                        zapper = ZapperUiState(),
                        readerArticle = null,
                        isReaderLoading = false,
                        findInPage = FindInPageState(),
                        externalAppPrompt = if (s.externalAppPrompt?.tabId == tabId) null else s.externalAppPrompt,
                        httpsErrorPrompt = if (s.httpsErrorPrompt?.tabId == tabId) null else s.httpsErrorPrompt,
                        permissionPrompt = if (s.permissionPrompt?.tabId == tabId) null else s.permissionPrompt
                    )
                }
            }
            val isHttpNavigation = url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
            // HTTP(S) navigations apply WebView surface/darkening and origin-guarded Dark Reader
            // immediately next in onInjectOnPageStarted; skip redundant unguarded injection here.
            applyResolvedSettingsToTab(
                tabId = tabId,
                reloadIfModeChanged = false,
                updateWebDark = !isHttpNavigation,
                applyIfWebDarkDirty = !isHttpNavigation
            )
        }

        override fun onInjectOnPageStarted(tabId: String, url: String) {
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
            val session = pool.getExisting(tabId) ?: return
            val state = _uiState.value
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate ?: session.isPrivate
            val normalizedHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url))
            val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
            val siteOverride = effectiveSiteOverride(normalizedHost, tabIsPrivate)
            val siteAdblockDisabled = siteOverride?.adblockDisabled == true
            val darkState = DarkReaderConfig.resolve(
                settings = state.settings,
                siteOverride = siteOverride,
                isChromeDark = isChromeDark()
            )
            val tokens = session.rotateTokensForOrigin(expectedOrigin)
            session.webDarkDirty = false
            val (isDarkSurface, isAmoledSurface) = resolveWebSurfaceDarkColors(darkState)
            session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
            ScriptInjector.injectDarkReaderOnPageStarted(
                webView = session.webView,
                expectedOrigin = expectedOrigin,
                tokens = tokens,
                darkState = darkState,
                darkReaderInjector = app.darkReaderInjector,
                isDestroyed = { session.isDestroyed }
            )
            pageStartedInjectJobByTab.remove(tabId)?.cancel()
            pageStartedInjectJobByTab[tabId] = viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                val persistedZapped = if (normalizedHost.isNotBlank()) {
                    enabledZappedRulesByHost[normalizedHost].orEmpty()
                } else {
                    emptyList()
                }
                val zappedRules = if (tabIsPrivate && normalizedHost.isNotBlank()) {
                    persistedZapped + _uiState.value.privateZappedRules[normalizedHost].orEmpty().filter { it.enabled }
                } else {
                    persistedZapped
                }
                val payload = app.sessionEngineCoordinator.preparePageStartedPayload(
                    url = url,
                    tokens = tokens,
                    settings = state.settings,
                    siteAdblockDisabled = siteAdblockDisabled,
                    zappedRules = zappedRules,
                    mergePrivateStorage = { list ->
                        if (tabIsPrivate) mergePrivateUserscriptStorage(list) else list
                    }
                )
                cosmeticExceptionsByTab[tabId] = payload.cosmetic.exceptions.toSet()
                withContext(Dispatchers.Main) {
                    if (session.isDestroyed) return@withContext
                    // Do not run stale page-started injection after page-finished has already committed
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && liveUrl != url) {
                        return@withContext
                    }
                    session.updateDocumentStartScripts(
                        origin = expectedOrigin,
                        cosmeticCss = payload.cosmetic.css,
                        zappedCss = payload.zappedCss,
                        gmShimInitJs = payload.gmShimInitJs,
                        documentStartUserscriptSnippets = payload.docStartSnippets
                    )
                    ScriptInjector.injectOnPageStarted(
                        webView = session.webView,
                        tokens = tokens,
                        cosmetic = payload.cosmetic,
                        zappedRules = payload.zappedRules,
                        zapperController = app.zapperController,
                        docStartUserscripts = payload.docStartUserscripts,
                        userscriptBridge = app.userscriptBridge,
                        isDestroyed = { session.isDestroyed }
                    )
                }
            }
        }

        override fun onPageFinished(
            tabId: String,
            url: String,
            title: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            if (_uiState.value.tabs.none { it.id == tabId }) return
            val cleanTitle = title.ifBlank { OmniboxEngine.extractHost(url).ifBlank { "New Tab" } }
            progressByTab[tabId] = 100
            if (_uiState.value.activeTabId == tabId) {
                _activeTabProgress.value = 100
            }
            val accessedAt = System.currentTimeMillis()
            val shouldBumpLastAccessed = _uiState.value.activeTabId == tabId || url.isNotBlank()
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url.ifBlank { tab.url },
                    title = cleanTitle,
                    isLoading = false,
                    progress = 100,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    lastAccessedAt = if (shouldBumpLastAccessed && !tab.isUnloaded) accessedAt else tab.lastAccessedAt
                )
            }
            val updatedTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
            if (updatedTab != null && !updatedTab.isPrivate && !updatedTab.isStartPage) {
                persistTab(updatedTab)
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    pool.getExisting(tabId)?.let { session ->
                        val now = System.currentTimeMillis()
                        val lastFlush = lastCookieFlushMsByProfile[session.profileName] ?: 0L
                        if (now - lastFlush >= 2_000L) {
                            lastCookieFlushMsByProfile[session.profileName] = now
                            session.flushCookies()
                        }
                    }
                }
                if (lastRecordedHistoryUrlByTab[tabId] != updatedTab.url) {
                    lastRecordedHistoryUrlByTab[tabId] = updatedTab.url
                    lastRecordedHistoryTitleByTab[tabId] = updatedTab.title
                    recordHistory(updatedTab.url, updatedTab.title)
                } else if (title.isNotBlank()) {
                    maybeUpdateRecordedHistoryTitle(tabId, updatedTab.url, cleanTitle)
                }
            }
        }

        override fun onInjectOnPageFinished(tabId: String, url: String) {
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
            val session = pool.getExisting(tabId) ?: return
            val state = _uiState.value
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate ?: session.isPrivate
            val normalizedHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url))
            val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
            val siteOverride = effectiveSiteOverride(normalizedHost, tabIsPrivate)
            val siteAdblockDisabled = siteOverride?.adblockDisabled == true
            val runGenericScan = state.settings.adblockEnabled &&
                state.settings.adblockCosmeticEnabled &&
                !siteAdblockDisabled
            val darkState = DarkReaderConfig.resolve(
                settings = state.settings,
                siteOverride = siteOverride,
                isChromeDark = isChromeDark()
            )
            val tokens = session.rotateTokensForOrigin(expectedOrigin)
            // Apply/confirm Dark Reader immediately on Main without waiting on Adblock/Room I/O
            ScriptInjector.injectDarkReaderFastOrFull(
                webView = session.webView,
                tokens = tokens,
                darkState = darkState,
                darkReaderInjector = app.darkReaderInjector,
                isDestroyed = { session.isDestroyed }
            )
            // Cancel any still-pending page-started injection so it cannot wipe generic cosmetic CSS after page-finished
            pageStartedInjectJobByTab.remove(tabId)?.cancel()
            pageFinishedInjectJobByTab.remove(tabId)?.cancel()
            pageFinishedInjectJobByTab[tabId] = viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                val persistedZapped = if (normalizedHost.isNotBlank()) {
                    enabledZappedRulesByHost[normalizedHost].orEmpty()
                } else {
                    emptyList()
                }
                val zappedRules = if (tabIsPrivate && normalizedHost.isNotBlank()) {
                    persistedZapped + _uiState.value.privateZappedRules[normalizedHost].orEmpty().filter { it.enabled }
                } else {
                    persistedZapped
                }
                val payload = app.sessionEngineCoordinator.preparePageFinishedPayload(
                    url = url,
                    settings = state.settings,
                    siteAdblockDisabled = siteAdblockDisabled,
                    zappedRules = zappedRules,
                    mergePrivateStorage = { list ->
                        if (tabIsPrivate) mergePrivateUserscriptStorage(list) else list
                    }
                )
                cosmeticExceptionsByTab[tabId] = payload.cosmetic.exceptions.toSet()
                withContext(Dispatchers.Main) {
                    if (session.isDestroyed) return@withContext
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && liveUrl != url) {
                        return@withContext
                    }
                    ScriptInjector.injectOnPageFinished(
                        webView = session.webView,
                        tokens = tokens,
                        cosmetic = payload.cosmetic,
                        runGenericCosmeticScan = runGenericScan,
                        zappedRules = payload.zappedRules,
                        zapperController = app.zapperController,
                        docEndUserscripts = payload.docEndUserscripts,
                        docIdleUserscripts = payload.docIdleUserscripts,
                        userscriptBridge = app.userscriptBridge,
                        isDestroyed = { session.isDestroyed }
                    )
                    session.evaluateJavascript(app.readerExtractor.buildCheckReadableScript()) { raw ->
                        if (session.isDestroyed) return@evaluateJavascript
                        val nowUrl = session.webView.url.orEmpty()
                        if (nowUrl.isNotBlank() && nowUrl != url) {
                            return@evaluateJavascript
                        }
                        val readable = raw?.trim().equals("true", ignoreCase = true)
                        updateTab(tabId) { it.copy(isReaderAvailable = readable) }
                    }
                    if (!tabIsPrivate) {
                        session.evaluateJavascript(
                            WebAppDetector.buildInspectPageScript(tokens.pwaToken, expectedOrigin)
                        )
                    }
                }
            }
        }

        override fun onVisitedHistoryUpdated(
            tabId: String,
            url: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            val existingTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            val urlChanged = existingTab.url != url
            ScriptInjector.extractOrigin(url)?.let { origin ->
                pool.getExisting(tabId)?.rotateTokensForOrigin(origin)
            }
            val accessedAt = System.currentTimeMillis()
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    lastAccessedAt = if (urlChanged && !tab.isUnloaded) accessedAt else tab.lastAccessedAt
                )
            }
            val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            val isHttp = url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
            // Only record SPA route transitions here (when page isn't actively loading);
            // full document navigations record cleanly in onPageFinished once redirects settle and title is known.
            if (!tab.isPrivate && !tab.isStartPage && isHttp && !tab.isLoading && urlChanged &&
                lastRecordedHistoryUrlByTab[tabId] != url
            ) {
                val safeTitle = tab.title
                    .takeUnless { it.isBlank() || it.equals("New Tab", ignoreCase = true) }
                    ?: OmniboxEngine.extractHost(url).ifBlank { url }
                lastRecordedHistoryUrlByTab[tabId] = url
                lastRecordedHistoryTitleByTab[tabId] = safeTitle
                persistTab(tab)
                recordHistory(url, safeTitle)
            }
            // Client-side SPA route transition: refresh Reader availability and route-matched userscripts
            if (urlChanged && isHttp && !tab.isLoading) {
                scheduleSpaNavigationRefresh(tabId, url)
            }
        }

        override fun onProgressChanged(tabId: String, progress: Int) {
            progressByTab[tabId] = progress
            if (_uiState.value.activeTabId == tabId) {
                _activeTabProgress.value = progress
            }
            val nowLoading = progress in 1..99
            val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            if (currentTab.isLoading != nowLoading) {
                updateTab(tabId) { tab ->
                    tab.copy(progress = progress, isLoading = nowLoading)
                }
            }
        }

        override fun onTitleChanged(tabId: String, title: String) {
            updateTab(tabId) { tab -> tab.copy(title = title) }
            val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            if (!tab.isPrivate && !tab.isStartPage && title.isNotBlank() &&
                lastRecordedHistoryUrlByTab[tabId] == tab.url
            ) {
                maybeUpdateRecordedHistoryTitle(tabId, tab.url, title)
            }
        }

        override fun onFaviconReceived(tabId: String, url: String?, icon: android.graphics.Bitmap) {
            val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            val targetUrl = url?.takeIf { it.isNotBlank() } ?: tab.url
            if (targetUrl.isBlank() || targetUrl == OmniboxEngine.NEW_TAB_URL) return
            viewModelScope.launch {
                app.faviconRepository.recordWebViewFavicon(
                    url = targetUrl,
                    bitmap = icon,
                    isPrivate = tab.isPrivate,
                    tabId = tabId,
                    aliasUrl = tab.pinnedUrl
                )
            }
        }

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            _uiState.update { it.copy(isFullscreenVideo = true, isFoldedSidebarOpen = false) }
            onShowFullscreenViewBridge?.invoke(view, callback)
        }

        override fun onHideCustomView() {
            _uiState.update { it.copy(isFullscreenVideo = false) }
            onHideFullscreenViewBridge?.invoke()
        }

        override fun onShowFileChooser(
            filePathCallback: ValueCallback<Array<Uri>>,
            chooserIntent: Intent
        ): Boolean {
            return onShowFileChooserBridge?.invoke(filePathCallback, chooserIntent) ?: false
        }

        override fun onPermissionRequested(tabId: String, prompt: SitePermissionPrompt) {
            val normalizedHost = OmniboxEngine.normalizeHost(prompt.host)
            val normalizedPrompt = prompt.copy(tabId = tabId, host = normalizedHost)
            if (_uiState.value.activeTabId != tabId) {
                denyPromptSilently(normalizedPrompt)
                return
            }
            val isPrivate = _uiState.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            val saved = effectiveSiteOverride(normalizedHost, isPrivate)
            val decision = app.sessionEngineCoordinator.resolvePermissionPolicyDecision(normalizedPrompt, saved)
            if (decision != null) {
                if (decision) {
                    grantPermissionWithRuntimeCheck(normalizedPrompt, rememberForSite = false)
                } else if (normalizedPrompt.isGeolocation) {
                    normalizedPrompt.geoCallback?.invoke(normalizedPrompt.geoOrigin, false, false)
                } else {
                    normalizedPrompt.webRequest?.deny()
                }
                return
            }
            // Deny any superseded pending prompt so WebView never leaks a hanging callback
            _uiState.value.permissionPrompt?.let { previous ->
                denyPromptSilently(previous)
            }
            _uiState.update { it.copy(permissionPrompt = normalizedPrompt) }
        }

        override fun onPermissionRequestCanceled(
            tabId: String,
            webRequest: PermissionRequest?,
            isGeolocation: Boolean
        ) {
            _uiState.update { state ->
                val current = state.permissionPrompt ?: return@update state
                if (current.tabId.isNotEmpty() && current.tabId != tabId) return@update state
                val matches = if (isGeolocation) {
                    current.isGeolocation
                } else {
                    webRequest == null || current.webRequest === webRequest
                }
                if (matches) state.copy(permissionPrompt = null) else state
            }
        }

        override fun onOpenNewWindow(parentTabId: String, resultMsg: Message?): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val snapshot = _uiState.value
            val parent = snapshot.tabs.firstOrNull { it.id == parentTabId }
            val popupIsPrivate = parent?.isPrivate ?: snapshot.isPrivateView
            val newTab = createTab(
                url = OmniboxEngine.NEW_TAB_URL,
                isPrivate = popupIsPrivate,
                spaceId = WorkspaceTabResolver.resolveChildTabSpaceId(
                    parentSpaceId = parent?.spaceId,
                    activeSpaceId = snapshot.activeSpaceId
                ),
                selectImmediately = true,
                isPopupWindow = true,
                parentTabId = parentTabId
            )
            // TabSessionPool binds the active Activity context via MutableContextWrapper
            val session = ensureSession(newTab, app)
            transport.webView = session.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(tabId: String) {
            val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            if (tab.isPopupWindow) {
                closeTab(tabId)
            }
        }

        override fun onExternalAppPrompt(prompt: ExternalAppPrompt) {
            if (prompt.tabId.isNotEmpty() && _uiState.value.activeTabId != prompt.tabId) return
            _uiState.update { it.copy(externalAppPrompt = prompt) }
        }

        override fun onLaunchExternalIntent(intent: Intent): Boolean {
            return onLaunchExternalIntentBridge?.invoke(intent) ?: false
        }

        override fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt) {
            if (prompt.tabId.isNotEmpty() && _uiState.value.activeTabId != prompt.tabId) return
            _uiState.update { it.copy(httpsErrorPrompt = prompt) }
        }

        override fun onMainFrameLoadError(
            tabId: String,
            url: String,
            errorCode: Int,
            description: String
        ) {
            val targetTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            val host = OmniboxEngine.extractHost(url)
            val initialError = MainFrameLoadError(
                url = url,
                host = host,
                description = description.ifBlank { "Connection error ($errorCode)" }
            )
            progressByTab[tabId] = 0
            if (_uiState.value.activeTabId == tabId) {
                _activeTabProgress.value = 0
            }
            updateTab(tabId) { tab ->
                tab.copy(isLoading = false, progress = 0, loadError = initialError)
            }
            loadErrorDiagJobByTab.remove(tabId)?.cancel()
            loadErrorDiagJobByTab[tabId] = viewModelScope.launch(ioExceptionHandler) {
                val result = NetworkErrorDiagnostics.diagnose(
                    url = url,
                    description = initialError.description,
                    baseClient = app.httpClient,
                    runDirectProbe = !targetTab.isPrivate
                )
                updateTab(tabId) { tab ->
                    val current = tab.loadError
                    if (current == null || current.url != url) {
                        tab
                    } else {
                        tab.copy(
                            loadError = current.copy(
                                resolvedIps = result.resolvedIps,
                                hasSinkholeOrLoopbackIp = result.hasSinkholeOrLoopbackIp,
                                directProbeSummary = result.directProbeSummary
                            )
                        )
                    }
                }
            }
        }

        override fun onRenderProcessGone(tabId: String, didCrash: Boolean) {
            viewModelScope.launch(Dispatchers.Main) {
                pool.evictCrashedSession(tabId)
                app.userscriptBridge.cancelCallsForTab(tabId)
                progressByTab[tabId] = 0
                pageStartedInjectJobByTab.remove(tabId)?.cancel()
                pageFinishedInjectJobByTab.remove(tabId)?.cancel()
                spaCheckJobByTab.remove(tabId)?.cancel()
                loadErrorDiagJobByTab.remove(tabId)?.cancel()
                val wasActive = _uiState.value.activeTabId == tabId
                if (wasActive) {
                    _activeTabProgress.value = 0
                    readerExtractJob?.cancel()
                    readerExtractJob = null
                    readerRequestCounter.incrementAndGet()
                    if (_uiState.value.isFullscreenVideo) {
                        onHideFullscreenViewBridge?.invoke()
                    }
                }
                _uiState.value.permissionPrompt?.takeIf { it.tabId == tabId || wasActive }?.let(::denyPromptSilently)
                _uiState.update { state ->
                    state.copy(
                        tabs = state.tabs.map { tab ->
                            if (tab.id == tabId) {
                                tab.copy(
                                    isLoading = false,
                                    progress = 0,
                                    isPlayingMedia = false,
                                    isPlayingVideo = false,
                                    sessionRevision = tab.sessionRevision + 1
                                )
                            } else {
                                tab
                            }
                        },
                        zapper = if (wasActive) ZapperUiState() else state.zapper,
                        findInPage = if (wasActive) FindInPageState() else state.findInPage,
                        readerArticle = if (wasActive) null else state.readerArticle,
                        isReaderLoading = if (wasActive) false else state.isReaderLoading,
                        isFullscreenVideo = if (wasActive) false else state.isFullscreenVideo,
                        permissionPrompt = if (state.permissionPrompt?.tabId == tabId || wasActive) null else state.permissionPrompt,
                        externalAppPrompt = if (state.externalAppPrompt?.tabId == tabId || wasActive) null else state.externalAppPrompt,
                        httpsErrorPrompt = if (state.httpsErrorPrompt?.tabId == tabId || wasActive) null else state.httpsErrorPrompt
                    )
                }
                if (activeMediaTabId == tabId) {
                    handoffOrStopMediaService(tabId)
                }
                if (wasActive) {
                    showSnackbar(
                        if (didCrash) "Recovered tab after renderer reset" else "Reloaded tab to free memory"
                    )
                }
            }
        }

        override fun onDownloadRequested(
            tabId: String,
            url: String,
            userAgent: String,
            contentDisposition: String?,
            mimeType: String?,
            profileName: String,
            isPrivate: Boolean
        ) {
            viewModelScope.launch {
                val result = app.downloadCoordinator.enqueueHttpDownload(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    profileName = profileName,
                    isPrivate = isPrivate,
                    onIsolatedResult = { isolated ->
                        isolated.onSuccess { done ->
                            showSnackbar("Saved ${done.fileName} to Downloads")
                        }.onFailure { err ->
                            showSnackbar("Download failed: ${err.message ?: "unknown error"}")
                        }
                    }
                )
                result.onSuccess { rec ->
                    showSnackbar("Downloading ${rec.fileName}")
                }.onFailure { err ->
                    showSnackbar("Download failed: ${err.message ?: "unknown error"}")
                }
            }
        }

        override fun onBlobDownloadReady(
            tabId: String,
            fileName: String,
            mimeType: String,
            base64Data: String,
            sourceUrl: String,
            isPrivate: Boolean
        ) {
            viewModelScope.launch {
                val result = app.downloadCoordinator.saveBase64Blob(
                    fileName = fileName,
                    mimeType = mimeType,
                    base64Payload = base64Data,
                    sourceUrl = sourceUrl,
                    isPrivate = isPrivate
                )
                result.onSuccess { rec ->
                    showSnackbar("Saved ${rec.fileName} to Downloads")
                }.onFailure { err ->
                    showSnackbar("Could not save file: ${err.message ?: "error"}")
                }
            }
        }

        override fun onBlobDownloadFailed(tabId: String, message: String) {
            showSnackbar(message)
        }

        override fun onMediaPlaybackStateChanged(
            tabId: String,
            isPlaying: Boolean,
            hasVideo: Boolean,
            title: String
        ) {
            viewModelScope.launch(Dispatchers.Main) {
                val now = System.currentTimeMillis()
                updateTab(tabId) {
                    val stoppedPlaying = it.isPlayingMedia && !isPlaying
                    it.copy(
                        isPlayingMedia = isPlaying,
                        isPlayingVideo = isPlaying && hasVideo,
                        lastAccessedAt = if (stoppedPlaying) now else it.lastAccessedAt
                    )
                }
                val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
                if (!isPlaying && tab != null && !tab.isPrivate && !tab.isUnloaded) {
                    persistTab(tab)
                }
                if (isPlaying && _uiState.value.settings.backgroundMediaEnabled) {
                    maybeRequestNotificationPermissionOnce()
                    activeMediaTabId = tabId
                    BackgroundMediaService.claimPlayback(
                        owner = this@BrowserViewModel,
                        context = app,
                        controller = createMediaCommandController(tabId),
                        title = title.ifBlank { tab?.title ?: "Media playback" },
                        host = tab?.displayHost.orEmpty().ifBlank { "Swiftfox" },
                        isPlaying = true,
                        isPrivate = tab?.isPrivate == true
                    )
                } else if (!isPlaying && activeMediaTabId == tabId) {
                    val otherPlaying = _uiState.value.tabs.firstOrNull { it.id != tabId && it.isPlayingMedia }
                    if (otherPlaying != null && _uiState.value.settings.backgroundMediaEnabled) {
                        activeMediaTabId = otherPlaying.id
                        BackgroundMediaService.claimPlayback(
                            owner = this@BrowserViewModel,
                            context = app,
                            controller = createMediaCommandController(otherPlaying.id),
                            title = otherPlaying.title.ifBlank { "Media playback" },
                            host = otherPlaying.displayHost.ifBlank { "Swiftfox" },
                            isPlaying = true,
                            isPrivate = otherPlaying.isPrivate
                        )
                    } else if (_uiState.value.settings.backgroundMediaEnabled) {
                        // Keep notification alive in paused state so user can tap Play to resume
                        BackgroundMediaService.updatePlaybackIfOwner(
                            owner = this@BrowserViewModel,
                            context = app,
                            title = title.ifBlank { tab?.title ?: "Media playback" },
                            host = tab?.displayHost.orEmpty().ifBlank { "Swiftfox" },
                            isPlaying = false,
                            isPrivate = tab?.isPrivate == true
                        )
                    } else {
                        activeMediaTabId = null
                        BackgroundMediaService.releasePlaybackOwner(
                            owner = this@BrowserViewModel,
                            context = app,
                            stopService = true
                        )
                    }
                }
            }
        }

        override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) {
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { state ->
                    state.copy(
                        findInPage = state.findInPage.copy(
                            activeMatchOrdinal = activeMatchOrdinal,
                            totalMatches = numberOfMatches
                        )
                    )
                }
            }
        }

        override fun onContextMenuLongPress(tabId: String, hit: ContextMenuHit) {
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { it.copy(contextMenuHit = hit) }
            }
        }

        override fun onZapperSelectionChanged(tabId: String, json: String) {
            val event = app.zapperController.parseSelectionEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                val stateSnapshot = _uiState.value
                if (!stateSnapshot.zapper.isActive || stateSnapshot.activeTabId != tabId) return@launch
                _uiState.update { state ->
                    state.copy(
                        zapper = state.zapper.copy(
                            isActive = true,
                            selectedSelector = event.selector,
                            selectedSummary = event.summary,
                            scopeDepth = event.scopeDepth,
                            maxScopeDepth = event.maxScopeDepth,
                            matchCount = event.matchCount,
                            selectedViewportCenterY = event.viewportCenterY,
                            isPreviewing = event.isPreviewing
                        )
                    )
                }
            }
        }

        override fun onZapperTemporaryZap(tabId: String, json: String) {
            val event = app.zapperController.parseTemporaryZapEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                val stateSnapshot = _uiState.value
                if (!stateSnapshot.zapper.isActive || stateSnapshot.activeTabId != tabId) return@launch
                _uiState.update { state ->
                    state.copy(
                        zapper = state.zapper.copy(
                            selectedSelector = null,
                            selectedSummary = "",
                            scopeDepth = 0,
                            maxScopeDepth = 0,
                            matchCount = 0,
                            isPreviewing = false,
                            temporaryZapCount = event.temporaryZapCount,
                            lastTemporaryZapSummary = event.summary,
                            lastStatusMessage = "Hid ${event.summary.ifBlank { "element" }}"
                        )
                    )
                }
            }
        }

        override fun onZapperUndoTemporaryZap(tabId: String, json: String) {
            val count = app.zapperController.parseUndoCount(json)
            viewModelScope.launch(Dispatchers.Main) {
                val stateSnapshot = _uiState.value
                if (!stateSnapshot.zapper.isActive || stateSnapshot.activeTabId != tabId) return@launch
                _uiState.update { state ->
                    state.copy(
                        zapper = state.zapper.copy(
                            temporaryZapCount = count,
                            lastTemporaryZapSummary = if (count == 0) null else state.zapper.lastTemporaryZapSummary,
                            lastStatusMessage = "Restored element"
                        )
                    )
                }
            }
        }

        override fun onZapperPermanentRule(tabId: String, json: String) {
            val event = app.zapperController.parsePermanentRuleEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                val stateSnapshot = _uiState.value
                if (!stateSnapshot.zapper.isActive || stateSnapshot.activeTabId != tabId) return@launch
                val tab = stateSnapshot.tabs.firstOrNull { it.id == tabId } ?: return@launch
                val host = OmniboxEngine.normalizeHost(tab.displayHost)
                if (host.isBlank()) return@launch
                val blockedLabel = event.label.ifBlank { event.selector }
                if (tab.isPrivate) {
                    val existing = stateSnapshot.privateZappedRules[host].orEmpty()
                    val rule = ZappedRuleEntity(
                        id = "pzap_${UUID.randomUUID().toString().take(12)}",
                        host = host,
                        selector = event.selector,
                        label = event.label,
                        enabled = true
                    )
                    val updatedList = existing.filterNot { it.selector == event.selector } + rule
                    _uiState.update { state ->
                        state.copy(
                            privateZappedRules = state.privateZappedRules + (host to updatedList),
                            zapper = state.zapper.copy(
                                selectedSelector = null,
                                selectedSummary = "",
                                scopeDepth = 0,
                                maxScopeDepth = 0,
                                matchCount = 0,
                                isPreviewing = false,
                                lastStatusMessage = "Blocked $blockedLabel (Private)"
                            ),
                            snackbarMessage = null
                        )
                    }
                    pool.clearAllDocumentStartScripts()
                    refreshZappedCssForHost(host, isPrivateOnly = true)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val existing = db.zappedRuleDao().getForHost(host)
                        .firstOrNull { it.selector == event.selector }
                    val rule = if (existing != null) {
                        existing.copy(enabled = true, label = event.label.ifBlank { existing.label })
                    } else {
                        ZappedRuleEntity(
                            id = "zap_${UUID.randomUUID().toString().take(12)}",
                            host = host,
                            selector = event.selector,
                            label = event.label,
                            enabled = true
                        )
                    }
                    db.zappedRuleDao().upsert(rule)
                }
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                pool.clearAllDocumentStartScripts()
                refreshZappedCssForHost(host, isPrivateOnly = false)
                _uiState.update { state ->
                    state.copy(
                        zapper = state.zapper.copy(
                            selectedSelector = null,
                            selectedSummary = "",
                            scopeDepth = 0,
                            maxScopeDepth = 0,
                            matchCount = 0,
                            isPreviewing = false,
                            lastStatusMessage = "Blocked $blockedLabel"
                        ),
                        snackbarMessage = null
                    )
                }
            }
        }

        override fun onDarkReaderStatus(tabId: String, json: String) {
            val event = app.darkReaderInjector.parseStatusJson(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                updateTab(tabId) { it.copy(darkReaderSkippedNative = event.skippedNativeDark) }
            }
        }

        override fun onCosmeticDomTokens(
            tabId: String,
            pageUrl: String,
            classesJson: String,
            idsJson: String
        ) {
            if (!pageUrl.startsWith("http://", ignoreCase = true) && !pageUrl.startsWith("https://", ignoreCase = true)) {
                return
            }
            if ((classesJson.isBlank() || classesJson == "[]") && (idsJson.isBlank() || idsJson == "[]")) {
                return
            }
            val state = _uiState.value
            if (!state.settings.adblockEnabled || !state.settings.adblockCosmeticEnabled) return
            val host = OmniboxEngine.extractHost(pageUrl)
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            if (effectiveSiteOverride(host, tabIsPrivate)?.adblockDisabled == true) return
            val expectedOrigin = ScriptInjector.extractOrigin(pageUrl) ?: return

            viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                val exceptions = cosmeticExceptionsByTab[tabId].orEmpty()
                val genericCss = app.adblockEngine.genericCosmeticCssFromJson(
                    url = pageUrl,
                    classesJson = classesJson,
                    idsJson = idsJson,
                    exceptions = exceptions,
                    globalEnabled = state.settings.adblockEnabled,
                    cosmeticEnabled = state.settings.adblockCosmeticEnabled,
                    siteDisabled = false
                )
                if (genericCss.isBlank()) return@launch
                val appendJs = ScriptInjector.buildAppendGenericCosmeticCssScript(genericCss)
                withContext(Dispatchers.Main) {
                    val session = pool.getExisting(tabId) ?: return@withContext
                    if (session.isDestroyed) return@withContext
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && ScriptInjector.extractOrigin(liveUrl) != expectedOrigin) {
                        return@withContext
                    }
                    session.evaluateJavascript(appendJs)
                }
            }
        }

        override fun onUserscriptSetValue(
            tabId: String,
            pageUrl: String,
            scriptId: String,
            key: String,
            valueJson: String
        ) {
            val isPrivate = _uiState.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            if (isPrivate) {
                if (scriptId.isBlank() || key.isEmpty() || key == "__proto__" ||
                    key.length > 256 || valueJson.toByteArray(Charsets.UTF_8).size > 262_144
                ) {
                    return
                }
                val sessionGen = privateSessionGeneration.get()
                viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                    val script = db.userscriptDao().getById(scriptId) ?: return@launch
                    if (!script.enabled) return@launch
                    val grants = UserscriptParser.decodeStringList(script.grantsJson)
                    val allowedGrant = !grants.contains("none") &&
                        (grants.isEmpty() || grants.contains("GM_setValue") || grants.contains("GM.setValue"))
                    if (!allowedGrant) return@launch
                    val matches = UserscriptParser.decodeStringList(script.matchesJson)
                    val excludes = UserscriptParser.decodeStringList(script.excludesJson)
                    if (pageUrl.isBlank() || !UserscriptUrlMatcher.matches(pageUrl, matches, excludes)) return@launch
                    // Verify private session is still open & same generation before writing
                    if (privateSessionGeneration.get() != sessionGen ||
                        _uiState.value.tabs.none { it.isPrivate }
                    ) {
                        return@launch
                    }

                    synchronized(privateUserscriptStorageLock) {
                        if (privateSessionGeneration.get() != sessionGen) return@synchronized
                        val map = privateUserscriptStorage.computeIfAbsent(scriptId) { ConcurrentHashMap() }
                        if (!map.containsKey(key) && map.size >= 100) return@synchronized
                        val totalOther = map.entries
                            .filter { it.key != key }
                            .sumOf { it.key.toByteArray(Charsets.UTF_8).size + it.value.toByteArray(Charsets.UTF_8).size }
                        val delta = key.toByteArray(Charsets.UTF_8).size + valueJson.toByteArray(Charsets.UTF_8).size
                        if (totalOther + delta <= 1024 * 1024) {
                            map[key] = valueJson
                            privateDeletedUserscriptKeys[scriptId]?.remove(key)
                        }
                    }
                }
                return
            }
            app.userscriptBridge.handleSetValue(
                scriptId = scriptId,
                key = key,
                valueJson = valueJson,
                pageUrl = pageUrl
            )
        }

        override fun onUserscriptDeleteValue(
            tabId: String,
            pageUrl: String,
            scriptId: String,
            key: String
        ) {
            val isPrivate = _uiState.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            if (isPrivate) {
                if (scriptId.isBlank() || key.isEmpty() || key.length > 256) return
                val sessionGen = privateSessionGeneration.get()
                viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                    val script = db.userscriptDao().getById(scriptId) ?: return@launch
                    if (!script.enabled) return@launch
                    val grants = UserscriptParser.decodeStringList(script.grantsJson)
                    val allowedGrant = !grants.contains("none") &&
                        (grants.isEmpty() || grants.contains("GM_deleteValue") || grants.contains("GM.deleteValue"))
                    if (!allowedGrant) return@launch
                    val matches = UserscriptParser.decodeStringList(script.matchesJson)
                    val excludes = UserscriptParser.decodeStringList(script.excludesJson)
                    if (pageUrl.isBlank() || !UserscriptUrlMatcher.matches(pageUrl, matches, excludes)) return@launch
                    if (privateSessionGeneration.get() != sessionGen ||
                        _uiState.value.tabs.none { it.isPrivate }
                    ) {
                        return@launch
                    }
                    synchronized(privateUserscriptStorageLock) {
                        if (privateSessionGeneration.get() != sessionGen) return@synchronized
                        privateUserscriptStorage[scriptId]?.remove(key)
                        // Tombstone so keys inherited from normal storage don't resurrect on reload
                        privateDeletedUserscriptKeys.computeIfAbsent(scriptId) { ConcurrentHashMap.newKeySet() }.add(key)
                    }
                }
                return
            }
            app.userscriptBridge.handleDeleteValue(
                scriptId = scriptId,
                key = key,
                pageUrl = pageUrl
            )
        }

        override fun onUserscriptXmlHttpRequest(
            tabId: String,
            pageUrl: String,
            scriptId: String,
            reqId: String,
            requestJson: String
        ) {
            val session = pool.getExisting(tabId) ?: return
            if (session.isDestroyed) return
            val originAtRequest = ScriptInjector.extractOrigin(pageUrl)
            val tokens = session.tokens
            app.userscriptBridge.handleXmlHttpRequest(
                scriptId = scriptId,
                reqId = reqId,
                requestJson = requestJson,
                pageUrl = pageUrl,
                bridgeToken = tokens.gmToken,
                tabId = tabId,
                runtimeKey = tokens.gmKey
            ) { callbackJs ->
                val liveSession = pool.getExisting(tabId) ?: return@handleXmlHttpRequest
                if (liveSession.isDestroyed) return@handleXmlHttpRequest
                val liveUrl = liveSession.webView.url.orEmpty()
                if (originAtRequest != null && liveUrl.isNotBlank() &&
                    ScriptInjector.extractOrigin(liveUrl) != originAtRequest
                ) {
                    return@handleXmlHttpRequest
                }
                liveSession.evaluateJavascript(callbackJs)
            }
        }

        override fun onUserscriptAbortXmlHttpRequest(tabId: String, reqId: String) {
            app.userscriptBridge.handleAbortXmlHttpRequest(reqId = reqId, tabId = tabId)
        }

        override fun onPwaChannelMessage(tabId: String, pageUrl: String, event: String, json: String) {
            val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
            if (tab.isPrivate) return
            when (event) {
                "metadata" -> {
                    val meta = WebAppDetector.parseMetadataMessage(json) ?: return
                    val expectedOrigin = ScriptInjector.extractOrigin(meta.pageUrl) ?: return
                    viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                        val candidate = app.webAppRepository.resolveInstallCandidate(meta)
                        withContext(Dispatchers.Main) {
                            val session = pool.getExisting(tabId)
                            val liveUrl = session?.webView?.url.orEmpty()
                            if (liveUrl.isNotBlank() && ScriptInjector.extractOrigin(liveUrl) != expectedOrigin) {
                                return@withContext
                            }
                            val canInstallPrompt = candidate.isStandaloneCapable &&
                                candidate.existingInstalledAppId == null
                            var tabStillOpen = false
                            _uiState.update { state ->
                                if (state.tabs.none { it.id == tabId }) {
                                    state
                                } else {
                                    tabStillOpen = true
                                    state.copy(
                                        pwaCandidatesByTab = state.pwaCandidatesByTab + (tabId to candidate),
                                        tabs = state.tabs.map { t ->
                                            if (t.id == tabId) t.copy(isPwaInstallAvailable = canInstallPrompt) else t
                                        }
                                    )
                                }
                            }
                            if (tabStillOpen && canInstallPrompt && session != null && !session.isDestroyed) {
                                session.evaluateJavascript(
                                    WebAppDetector.buildDispatchBeforeInstallPromptScript(
                                        bridgeToken = session.tokens.pwaToken,
                                        expectedOrigin = expectedOrigin
                                    )
                                )
                            }
                        }
                    }
                }
                "promptInstall" -> {
                    if (_uiState.value.activeTabId == tabId) {
                        openInstallWebAppSheet()
                    }
                }
            }
        }
    }

    init {
        pool.rebindAllCallbacks(sessionCallbacks)
        pool.onThumbnailsEvicted = { evictedIds ->
            _thumbnails.update { current ->
                TabThumbnailsSnapshot(current.bitmaps - evictedIds)
            }
        }
        pool.onThumbnailCaptured = { tabId ->
            pool.getThumbnail(tabId)?.let { bmp ->
                _thumbnails.update { current ->
                    if (current.bitmaps[tabId] === bmp) current
                    else current.copy(bitmaps = current.bitmaps + (tabId to bmp))
                }
            }
        }
        observePersistence()
    }

    private fun observePersistence() {
        viewModelScope.launch {
            prefs.settingsFlow.collectLatest { newSettings ->
                val wasLoaded = _uiState.value.hasLoadedSettings
                val oldSettings = _uiState.value.settings
                val posture = _uiState.value.windowState.posture
                val activePostureSiteModeChanged =
                    oldSettings.profileFor(posture).defaultSiteMode != newSettings.profileFor(posture).defaultSiteMode
                val webDarkChanged = !wasLoaded ||
                    oldSettings.themeMode != newSettings.themeMode ||
                    oldSettings.webDarkMode != newSettings.webDarkMode ||
                    oldSettings.darkReaderAmoled != newSettings.darkReaderAmoled ||
                    oldSettings.darkReaderBrightness != newSettings.darkReaderBrightness ||
                    oldSettings.darkReaderContrast != newSettings.darkReaderContrast ||
                    oldSettings.darkReaderSepia != newSettings.darkReaderSepia ||
                    oldSettings.darkReaderSkipNativeDark != newSettings.darkReaderSkipNativeDark
                val thirdPartyCookiesChanged = oldSettings.blockThirdPartyCookies != newSettings.blockThirdPartyCookies
                val bgMediaChanged = oldSettings.backgroundMediaEnabled != newSettings.backgroundMediaEnabled
                val docStartPrefChanged = wasLoaded && (
                    oldSettings.adblockEnabled != newSettings.adblockEnabled ||
                        oldSettings.adblockCosmeticEnabled != newSettings.adblockCosmeticEnabled ||
                        oldSettings.userscriptsEnabled != newSettings.userscriptsEnabled
                    )
                val autoClearEnabledOrShortened = wasLoaded &&
                    startupTabReconciliationDone.get() &&
                    newSettings.tabAutoClearDuration != oldSettings.tabAutoClearDuration &&
                    newSettings.tabAutoClearDuration.isMoreAggressiveThan(oldSettings.tabAutoClearDuration)
                val iconChanged = !wasLoaded ||
                    oldSettings.themeMode != newSettings.themeMode ||
                    oldSettings.accentPreset != newSettings.accentPreset ||
                    oldSettings.appIconBackgroundMode != newSettings.appIconBackgroundMode ||
                    oldSettings.appIconColorStyle != newSettings.appIconColorStyle ||
                    oldSettings.appIconMatchAccent != newSettings.appIconMatchAccent

                _uiState.update { it.copy(settings = newSettings, hasLoadedSettings = true) }
                if (iconChanged) {
                    syncLauncherIconIfLoaded()
                }
                if (!newSettings.backgroundMediaEnabled && oldSettings.backgroundMediaEnabled) {
                    activeMediaTabId = null
                    BackgroundMediaService.stop(app)
                }
                if (activePostureSiteModeChanged || webDarkChanged || thirdPartyCookiesChanged || bgMediaChanged) {
                    syncAllWarmSessionsWithSettings(
                        reloadActiveOnSiteModeChange = activePostureSiteModeChanged,
                        updateSiteOverrides = activePostureSiteModeChanged || thirdPartyCookiesChanged,
                        updateWebDark = webDarkChanged,
                        updateBgMedia = bgMediaChanged
                    )
                }
                if (docStartPrefChanged) {
                    pool.clearAllDocumentStartScripts()
                }
                if (autoClearEnabledOrShortened) {
                    runAutoClearStaleTabsIfNeeded()
                }
            }
        }

        viewModelScope.launch {
            app.adblockEngine.status.collectLatest { status ->
                _uiState.update { it.copy(adblockStatus = status) }
            }
        }

        viewModelScope.launch {
            db.filterListDao().observeAll().distinctUntilChanged().collectLatest { lists ->
                _uiState.update { it.copy(filterLists = lists) }
            }
        }

        viewModelScope.launch {
            db.zappedRuleDao().observeAll().distinctUntilChanged().collectLatest { rules ->
                enabledZappedRulesByHost = rules
                    .asSequence()
                    .filter { it.enabled }
                    .groupBy { OmniboxEngine.normalizeHost(it.host) }
                _uiState.update { it.copy(zappedRules = rules) }
            }
        }

        viewModelScope.launch {
            db.userscriptDao().observeSummaries()
                .distinctUntilChanged()
                .map { summaries -> summaries.map { it.toMetadataEntity() } }
                .collectLatest { scripts ->
                    _uiState.update { it.copy(userscripts = scripts) }
                }
        }

        viewModelScope.launch {
            db.spaceDao().observeAll().distinctUntilChanged().collectLatest { dbSpaces ->
                if (dbSpaces.isNotEmpty()) {
                    val dbIds = dbSpaces.mapTo(mutableSetOf()) { it.id }
                    // Clear any optimistic newly created space once Room emits it
                    dbIds.forEach { pendingCreatedSpaces.remove(it) }
                    val mergedSpaces = if (pendingCreatedSpaces.isEmpty()) {
                        dbSpaces
                    } else {
                        dbSpaces + pendingCreatedSpaces.values.filter { it.id !in dbIds }
                    }
                    val migratedToPersist = mutableListOf<BrowserTabUi>()
                    _uiState.update { state ->
                        val validSpaceIds = mergedSpaces.mapTo(mutableSetOf()) { it.id }
                        val resolvedSpaceId = if (state.activeSpaceId in validSpaceIds) {
                            state.activeSpaceId
                        } else {
                            mergedSpaces.first().id
                        }
                        val hasOrphanedTab = state.tabs.any {
                            !it.isPrivate &&
                                !(it.isEssentialTab && it.spaceId == WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID) &&
                                it.spaceId !in validSpaceIds
                        }
                        val migratedTabs = if (hasOrphanedTab) {
                            state.tabs.map { tab ->
                                if (!tab.isPrivate &&
                                    !(tab.isEssentialTab && tab.spaceId == WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID) &&
                                    tab.spaceId !in validSpaceIds
                                ) {
                                    tab.copy(spaceId = resolvedSpaceId).also { migratedToPersist += it }
                                } else {
                                    tab
                                }
                            }
                        } else {
                            state.tabs
                        }
                        state.copy(
                            spaces = mergedSpaces,
                            activeSpaceId = resolvedSpaceId,
                            tabs = migratedTabs
                        )
                    }
                    migratedToPersist.forEach { persistTab(it) }
                }
            }
        }

        viewModelScope.launch {
            db.essentialDao().observeAll().distinctUntilChanged().collectLatest { dbEssentials ->
                val dbIds = dbEssentials.mapTo(mutableSetOf()) { it.id }
                dbIds.forEach { pendingCreatedEssentials.remove(it) }
                val mergedEssentials = if (pendingCreatedEssentials.isEmpty()) {
                    dbEssentials
                } else {
                    dbEssentials + pendingCreatedEssentials.values.filter { it.id !in dbIds }
                }
                _uiState.update { it.copy(essentials = mergedEssentials) }
                if (startupTabReconciliationDone.get()) {
                    val validEssIds = mergedEssentials.mapTo(mutableSetOf()) { it.id }
                    val orphaned = _uiState.value.tabs
                        .filter { it.essentialId != null && it.essentialId !in validEssIds }
                        .map { it.id }
                    if (orphaned.isNotEmpty()) {
                        pruneOrphanedBoundTabs(orphaned)
                    }
                }
            }
        }

        viewModelScope.launch {
            db.pinnedFolderDao().observeAll().distinctUntilChanged().collectLatest { dbFolders ->
                val dbIds = dbFolders.mapTo(mutableSetOf()) { it.id }
                dbIds.forEach { pendingCreatedPinnedFolders.remove(it) }
                val mergedFolders = if (pendingCreatedPinnedFolders.isEmpty()) {
                    dbFolders
                } else {
                    dbFolders + pendingCreatedPinnedFolders.values.filter { it.id !in dbIds }
                }
                val clearedOrphanTabs = mutableListOf<BrowserTabUi>()
                _uiState.update { state ->
                    val folderSpaceById = mergedFolders.associate { it.id to it.spaceId }
                    val updatedTabs = if (startupTabReconciliationDone.get()) {
                        state.tabs.map { tab ->
                            val fId = tab.folderId
                            if (!tab.isPrivate && tab.isPinned && !tab.isBoundWorkspaceTab &&
                                fId != null && folderSpaceById[fId] != tab.spaceId
                            ) {
                                tab.copy(folderId = null).also { clearedOrphanTabs += it }
                            } else {
                                tab
                            }
                        }
                    } else {
                        state.tabs
                    }
                    state.copy(pinnedFolders = mergedFolders, tabs = updatedTabs)
                }
                clearedOrphanTabs.forEach { persistTab(it) }
            }
        }

        viewModelScope.launch {
            db.bookmarkDao().observeAll().distinctUntilChanged().collectLatest { bookmarks ->
                val urlSet = if (bookmarks.isEmpty()) {
                    emptySet()
                } else {
                    bookmarks.mapTo(HashSet(bookmarks.size * 2)) { it.url }
                }
                _uiState.update { it.copy(bookmarks = bookmarks, bookmarkedUrls = urlSet) }
            }
        }

        viewModelScope.launch {
            db.historyDao().observeRecent(200).distinctUntilChanged().collectLatest { history ->
                _uiState.update { it.copy(history = history) }
            }
        }

        viewModelScope.launch {
            db.downloadRecordDao().observeAll().distinctUntilChanged().collectLatest { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
            }
        }

        viewModelScope.launch {
            app.webAppRepository.observeAll().distinctUntilChanged().collectLatest { apps ->
                val byUrl = apps.associateBy { it.startUrl }
                _uiState.update { state ->
                    val updatedCandidates = if (state.pwaCandidatesByTab.isEmpty()) {
                        state.pwaCandidatesByTab
                    } else {
                        state.pwaCandidatesByTab.mapValues { (_, cand) ->
                            val matchedId = (byUrl[cand.startUrl] ?: byUrl[cand.pageUrl])?.id
                            if (cand.existingInstalledAppId != matchedId) {
                                cand.copy(existingInstalledAppId = matchedId)
                            } else {
                                cand
                            }
                        }
                    }
                    state.copy(
                        webApps = apps,
                        pwaCandidatesByTab = updatedCandidates,
                        tabs = state.tabs.map { tab ->
                            val cand = updatedCandidates[tab.id]
                            val avail = cand != null && cand.isStandaloneCapable && cand.existingInstalledAppId == null
                            if (tab.isPwaInstallAvailable != avail) {
                                tab.copy(isPwaInstallAvailable = avail)
                            } else {
                                tab
                            }
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            app.mozillaSyncCoordinator.status.collectLatest { syncSnapshot ->
                _uiState.update { it.copy(syncStatus = syncSnapshot) }
            }
        }

        viewModelScope.launch {
            app.mozillaSyncCoordinator.swiftfoxDataRevision.collect { rev ->
                if (rev > 0L) {
                    pool.clearAllDocumentStartScripts()
                    val active = _uiState.value.activeTab
                    if (active != null && !active.isStartPage && active.displayHost.isNotBlank()) {
                        refreshZappedCssForHost(active.displayHost)
                    }
                    if (startupTabReconciliationDone.get()) {
                        reconcileSyncedPinnedTabsFromDb()
                    }
                }
            }
        }

        viewModelScope.launch {
            db.syncedRemoteTabDao().observeAll().distinctUntilChanged().collectLatest { remoteTabs ->
                _uiState.update { it.copy(syncedRemoteTabs = remoteTabs) }
            }
        }

        viewModelScope.launch {
            db.zenPinnedItemDao().observeAll().distinctUntilChanged().collectLatest { zenPinned ->
                _uiState.update { it.copy(zenPinnedItems = zenPinned) }
                if (startupTabReconciliationDone.get()) {
                    val validZenIds = zenPinned.mapTo(mutableSetOf()) { it.id }
                    val orphaned = _uiState.value.tabs
                        .filter { it.zenPinnedId != null && it.zenPinnedId !in validZenIds }
                        .map { it.id }
                    if (orphaned.isNotEmpty()) {
                        pruneOrphanedBoundTabs(orphaned)
                    }
                }
            }
        }

        viewModelScope.launch {
            db.siteOverrideDao().observeAll().distinctUntilChanged().collectLatest { overrides ->
                if (siteOverrideWrites.hasPendingWrites) return@collectLatest
                val newMap = overrides.associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                val oldMap = _uiState.value.siteOverrides
                if (oldMap != newMap) {
                    _uiState.update { it.copy(siteOverrides = newMap) }
                    syncGlobalPrepaintDarkScripts()
                    refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                    val activeId = _uiState.value.activeTab?.id
                    pool.markAllBackgroundWebDarkDirty(activeId)
                    if (activeId != null) {
                        applyResolvedSettingsToTab(activeId, reloadIfModeChanged = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            val currentSettings = prefs.settingsFlow.first()
            if (!currentSettings.hasClearedLegacyWebDarkOverrides) {
                val cleanupGen = siteOverrideWrites.beginWrite()
                siteOverrideWrites.runSerializedWrite(
                    generation = cleanupGen,
                    writeToDb = {
                        db.siteOverrideDao().clearAllWebDarkModeOverridesAndPrune()
                        prefs.setHasClearedLegacyWebDarkOverrides(true)
                    },
                    readSettledFromDb = {
                        db.siteOverrideDao().getAll()
                            .associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                    },
                    onSettled = { settled ->
                        if (_uiState.value.siteOverrides != settled) {
                            _uiState.update { it.copy(siteOverrides = settled) }
                            syncGlobalPrepaintDarkScripts()
                            refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                        }
                    }
                )
            }
            val initialOverrides = db.siteOverrideDao().getAll()
                .associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
            val shouldSeed = !currentSettings.hasSeededDefaults
            db.seedDefaultsIfEmpty(seedInitialContent = shouldSeed)
            if (shouldSeed) {
                prefs.setHasSeededDefaults(true)
            }
            if (!synchronized(incomingIntentLock) { initialTabsLoaded }) {
                val swiftfoxRevBeforeInitialLoad = app.mozillaSyncCoordinator.swiftfoxDataRevision.value
                val initialEssIds = db.essentialDao().getAll().mapTo(mutableSetOf()) { it.id }
                val initialZenIds = db.zenPinnedItemDao().getAll().mapTo(mutableSetOf()) { it.id }
                val initialFolders = db.pinnedFolderDao().getAll()
                val rawSavedTabs = db.tabDao().getNormalTabs()
                val orphanedSavedIds = rawSavedTabs.filter { entity ->
                    (entity.essentialId != null && entity.essentialId !in initialEssIds) ||
                        (entity.zenPinnedId != null && entity.zenPinnedId !in initialZenIds)
                }.map { it.id }
                if (orphanedSavedIds.isNotEmpty()) {
                    tabDbMutex.withLock {
                        db.tabDao().deleteByIds(orphanedSavedIds)
                    }
                }
                val orphanSet = orphanedSavedIds.toSet()
                val savedTabs = rawSavedTabs.filterNot { it.id in orphanSet }
                val existingInMemory = _uiState.value.tabs
                val hasQueuedIncoming = synchronized(incomingIntentLock) { pendingIncomingUrls.isNotEmpty() }
                if (savedTabs.isEmpty() && existingInMemory.isEmpty() && !hasQueuedIncoming) {
                    _uiState.update { it.copy(pinnedFolders = initialFolders) }
                    createTab(
                        url = OmniboxEngine.NEW_TAB_URL,
                        isPrivate = false,
                        spaceId = SwiftfoxDatabase.DEFAULT_SPACE_ID,
                        selectImmediately = true
                    )
                } else if (savedTabs.isNotEmpty()) {
                    val profile = currentSettings.profileFor(_uiState.value.windowState.posture)
                    val initialFolderSpaceById = initialFolders.associate { it.id to it.spaceId }
                    val restored = savedTabs.map { entity ->
                        val tabOverride = SiteModeResolver.parseOrNull(entity.siteModeOverride)
                        val host = OmniboxEngine.extractHost(entity.url)
                        val siteOverride = SiteModeResolver.parseOrNull(
                            initialOverrides[host]?.siteModeOverride
                        )
                        val validFolderId = entity.folderId?.takeIf {
                            entity.isPinned && initialFolderSpaceById[it] == entity.spaceId
                        }
                        BrowserTabUi(
                            id = entity.id,
                            spaceId = entity.spaceId,
                            title = entity.title,
                            url = entity.url,
                            isPrivate = false,
                            isPinned = entity.isPinned,
                            essentialId = entity.essentialId,
                            zenPinnedId = entity.zenPinnedId,
                            isUnloaded = entity.isUnloaded,
                            pinnedUrl = entity.pinnedUrl,
                            customIcon = entity.customIcon,
                            customTitle = entity.customTitle,
                            folderId = validFolderId,
                            sortOrder = entity.sortOrder,
                            siteModeOverride = tabOverride,
                            resolvedSiteMode = SiteModeResolver.resolve(tabOverride, siteOverride, profile),
                            lastAccessedAt = entity.lastAccessedAt
                        )
                    }
                    val restoredIds = restored.mapTo(mutableSetOf()) { it.id }
                    val merged = restored + existingInMemory.filterNot { it.id in restoredIds }
                    val first = existingInMemory.lastOrNull { !it.isUnloaded }
                        ?: restored.filter { !it.isUnloaded }.maxByOrNull { it.lastAccessedAt }
                    val fallbackSpaceId = restored
                        .filter {
                            !it.isEssentialTab &&
                                !it.isUnloaded &&
                                it.spaceId != WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID
                        }
                        .maxByOrNull { it.lastAccessedAt }
                        ?.spaceId
                        ?: _uiState.value.spaces.firstOrNull()?.id
                        ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
                    if (first != null) {
                        val resolvedSpaceId = if (
                            !first.isEssentialTab &&
                            first.spaceId != WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID
                        ) {
                            first.spaceId
                        } else {
                            fallbackSpaceId
                        }
                        _uiState.update {
                            it.copy(
                                pinnedFolders = initialFolders,
                                tabs = merged,
                                activeTabId = first.id,
                                activeSpaceId = resolvedSpaceId
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                pinnedFolders = initialFolders,
                                tabs = merged,
                                activeSpaceId = fallbackSpaceId
                            )
                        }
                        val hasQueuedIncomingFallback = synchronized(incomingIntentLock) { pendingIncomingUrls.isNotEmpty() }
                        if (!hasQueuedIncomingFallback) {
                            createTab(
                                url = OmniboxEngine.NEW_TAB_URL,
                                isPrivate = false,
                                spaceId = fallbackSpaceId,
                                selectImmediately = true
                            )
                        }
                    }
                }
                startupTabReconciliationDone.set(true)
                if (app.mozillaSyncCoordinator.swiftfoxDataRevision.value > swiftfoxRevBeforeInitialLoad) {
                    reconcileSyncedPinnedTabsFromDb()
                }
                val queued = synchronized(incomingIntentLock) {
                    initialTabsLoaded = true
                    if (pendingIncomingUrls.isEmpty()) emptyList()
                    else pendingIncomingUrls.toList().also { pendingIncomingUrls.clear() }
                }
                queued.forEach { target ->
                    createTab(
                        url = target,
                        isPrivate = false,
                        selectImmediately = true,
                        dismissAllOverlays = true
                    )
                }
                syncActiveTabProgressAndBlocked()
                runAutoClearStaleTabsIfNeeded()
                app.mozillaSyncCoordinator.syncSwiftfoxAndZenOnForegroundIfDue()
            }
        }
    }

    fun updateSystemDarkTheme(isDark: Boolean) {
        if (_uiState.value.isSystemDark == isDark) return
        _uiState.update { it.copy(isSystemDark = isDark) }
        val settings = _uiState.value.settings
        if (settings.themeMode == ThemeMode.SYSTEM) {
            syncAllWarmSessionsWithSettings(
                reloadActiveOnSiteModeChange = false,
                updateSiteOverrides = false,
                updateWebDark = true,
                updateBgMedia = false
            )
            if (settings.appIconBackgroundMode == AppIconBackgroundMode.FOLLOW_THEME) {
                syncLauncherIconIfLoaded()
            }
        }
    }

    fun onFullscreenVideoExited() {
        if (_uiState.value.isFullscreenVideo) {
            _uiState.update { it.copy(isFullscreenVideo = false) }
        }
    }

    fun onFoldableWindowStateChanged(newState: FoldableWindowState) {
        val previous = _uiState.value.windowState
        val isInitialEmission = !hasReceivedInitialPosture
        hasReceivedInitialPosture = true
        if (!isInitialEmission && previous == newState) return

        val postureChanged = previous.posture != newState.posture
        _uiState.update {
            it.copy(
                windowState = newState,
                isFoldedSidebarOpen = if (postureChanged) false else it.isFoldedSidebarOpen
            )
        }

        if (isInitialEmission) {
            // Apply initial posture profile quietly without reloading or flashing a snackbar
            syncAllWarmSessionsWithSettings(reloadActiveOnSiteModeChange = false)
            return
        }

        if (postureChanged) {
            val newProfile = _uiState.value.settings.profileFor(newState.posture)
            val activeTab = _uiState.value.activeTab
            if (activeTab == null) {
                refreshResolvedSiteModesForAllTabs(newProfile)
                return
            }
            val activeSiteOverrideEntity = effectiveSiteOverride(activeTab.displayHost, activeTab.isPrivate)
            val siteOverride = SiteModeResolver.parseOrNull(activeSiteOverrideEntity?.siteModeOverride)
            val resolved = SiteModeResolver.resolve(
                tabOverride = activeTab.siteModeOverride,
                siteOverride = siteOverride,
                postureProfile = newProfile
            )
            val hasCustomUa = !activeSiteOverrideEntity?.customUserAgent.isNullOrBlank()
            val shouldReload = !hasCustomUa && SiteModeResolver.shouldReloadOnTransition(
                previousMode = activeTab.resolvedSiteMode,
                newMode = resolved,
                currentUrl = activeTab.url
            )
            // Keep resolvedSiteMode accurate across all tabs (including hibernated & warm background tabs)
            refreshResolvedSiteModesForAllTabs(newProfile)
            _uiState.value.tabs.forEach { tab ->
                val session = pool.getExisting(tab.id) ?: return@forEach
                val isActive = tab.id == activeTab.id
                applySettingsToSession(
                    tab = tab,
                    session = session,
                    reloadIfModeChanged = shouldReload && isActive,
                    updateWebDark = false,
                    applyIfWebDarkDirty = false,
                    updateBgMedia = false
                )
            }
            if (shouldReload) {
                showSnackbar("Switched to ${resolved.label} view (${newState.posture.label})")
            }
        }
    }

    fun onPictureInPictureChanged(inPip: Boolean) {
        _uiState.update {
            it.copy(
                isInPictureInPicture = inPip,
                isFoldedSidebarOpen = if (inPip) false else it.isFoldedSidebarOpen
            )
        }
    }

    fun getExistingSession(tabId: String): BrowserSession? = pool.getExisting(tabId)

    fun ensureSession(tab: BrowserTabUi, context: Context): BrowserSession {
        val existed = pool.getExisting(tab.id) != null
        val initialHost = OmniboxEngine.normalizeHost(tab.displayHost)
        val initialOverride = if (initialHost.isBlank()) null else effectiveSiteOverride(initialHost, tab.isPrivate)
        val initialDarkState = DarkReaderConfig.resolve(
            settings = _uiState.value.settings,
            siteOverride = initialOverride,
            isChromeDark = isChromeDark()
        )
        val (initialDarkSurface, initialAmoledSurface) = resolveWebSurfaceDarkColors(initialDarkState)
        val initialPrepaintConfig = buildGlobalPrepaintDarkConfig(tab.isPrivate)
        val initialPrepaintScript = ScriptInjector.buildGlobalPrepaintDarkScript(initialPrepaintConfig)
        val initialSubframeDarkEnabled = shouldEnableSubframeDarkRuntime(initialPrepaintConfig)
        val subframeRuntimeReady = app.darkReaderInjector.isSubframeRuntimeWarmed
        if (initialSubframeDarkEnabled && !subframeRuntimeReady) {
            scheduleSubframeDarkRuntimeWarmup()
        }
        val session = pool.getOrCreate(
            tabId = tab.id,
            isPrivate = tab.isPrivate,
            context = context,
            callbacks = sessionCallbacks,
            beforeRestore = { created ->
                created.applySurfaceBackground(initialDarkSurface, initialAmoledSurface)
                if (!initialSubframeDarkEnabled || subframeRuntimeReady) {
                    created.updateSubframeDarkReaderRuntime(initialSubframeDarkEnabled, app.darkReaderInjector)
                }
                created.updateGlobalPrepaintDarkScript(initialPrepaintScript)
                applySettingsToSession(
                    tab = tab,
                    session = created,
                    reloadIfModeChanged = false,
                    updateWebDark = false,
                    applyIfWebDarkDirty = false
                )
            }
        )
        session.applySurfaceBackground(initialDarkSurface, initialAmoledSurface)
        if (!initialSubframeDarkEnabled || subframeRuntimeReady) {
            session.updateSubframeDarkReaderRuntime(initialSubframeDarkEnabled, app.darkReaderInjector)
        }
        session.updateGlobalPrepaintDarkScript(initialPrepaintScript)
        if (_uiState.value.activeTabId == tab.id) {
            pool.setActiveTab(tab.id)
        }
        applySettingsToSession(tab, session, reloadIfModeChanged = false)
        pendingProgrammaticHttpsUpgrades.remove(tab.id)?.let { (upgraded, originalHttp) ->
            session.webViewClient.registerProgrammaticHttpsUpgrade(upgraded, originalHttp)
        }
        if (!existed && !tab.isStartPage && !tab.isPopupWindow && session.webView.url.isNullOrBlank()) {
            val target = resolveHttpsOnlyForProgrammaticLoad(tab.id, tab.url, session)
            session.loadUrl(target)
        }
        return session
    }

    private fun applyTabSelectionTransition(
        state: BrowserUiState,
        tabs: List<BrowserTabUi>,
        selectedTabId: String,
        isPrivate: Boolean,
        spaceId: String,
        updateActiveSpace: Boolean,
        selectImmediately: Boolean,
        closeFoldedSidebar: Boolean,
        closeTabSwitcher: Boolean,
        dismissAllOverlays: Boolean
    ): BrowserUiState {
        val nextActiveSpaceId = if (
            selectImmediately &&
            updateActiveSpace &&
            !isPrivate &&
            spaceId != WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID
        ) {
            spaceId
        } else {
            state.activeSpaceId
        }
        return state.copy(
            tabs = tabs,
            activeTabId = if (selectImmediately) selectedTabId else state.activeTabId,
            isPrivateView = if (selectImmediately) isPrivate else state.isPrivateView,
            activeSpaceId = nextActiveSpaceId,
            isFoldedSidebarOpen = if (selectImmediately && closeFoldedSidebar) false else state.isFoldedSidebarOpen,
            activeOverlay = if (selectImmediately && (dismissAllOverlays || (closeTabSwitcher && state.activeOverlay == BrowserOverlay.TAB_SWITCHER))) {
                BrowserOverlay.NONE
            } else {
                state.activeOverlay
            },
            overlayBackStack = if (selectImmediately && dismissAllOverlays) {
                emptyList()
            } else {
                state.overlayBackStack
            },
            isSiteRulesOpen = if (selectImmediately && dismissAllOverlays) false else state.isSiteRulesOpen,
            isQrPairingOpen = if (selectImmediately && dismissAllOverlays) false else state.isQrPairingOpen,
            fxaAuthUrl = if (selectImmediately && dismissAllOverlays) null else state.fxaAuthUrl,
            isOmniboxFocused = false,
            findInPage = if (selectImmediately) FindInPageState() else state.findInPage,
            readerArticle = if (selectImmediately) null else state.readerArticle,
            isReaderLoading = if (selectImmediately) false else state.isReaderLoading,
            zapper = if (selectImmediately) ZapperUiState() else state.zapper,
            externalAppPrompt = if (selectImmediately) null else state.externalAppPrompt,
            httpsErrorPrompt = if (selectImmediately) null else state.httpsErrorPrompt,
            permissionPrompt = if (selectImmediately) null else state.permissionPrompt,
            pendingPinCloseTarget = if (selectImmediately) null else state.pendingPinCloseTarget
        )
    }

    fun createTab(
        url: String = OmniboxEngine.NEW_TAB_URL,
        isPrivate: Boolean = _uiState.value.isPrivateView,
        spaceId: String = _uiState.value.activeSpaceId,
        selectImmediately: Boolean = true,
        isPopupWindow: Boolean = false,
        parentTabId: String? = null,
        closeFoldedSidebar: Boolean = true,
        closeTabSwitcher: Boolean = true,
        dismissAllOverlays: Boolean = false,
        isPinned: Boolean = false,
        customIcon: String? = null,
        customTitle: String? = null,
        essentialId: String? = null,
        zenPinnedId: String? = null,
        pinnedUrl: String? = null
    ): BrowserTabUi {
        if (selectImmediately) {
            stopActiveZapperIfRunning()
            omniboxHolder.cancelJobsAndClearSuggestions()
            readerExtractJob?.cancel()
            readerExtractJob = null
            readerRequestCounter.incrementAndGet()
            _uiState.value.permissionPrompt?.let(::denyPromptSilently)
            _uiState.value.activeTabId?.let { prevId ->
                touchTabLastAccessed(prevId)
                pool.getExisting(prevId)?.clearFindMatches()
            }
        }
        val currentState = _uiState.value
        val isReusableBlankRequest = !isPopupWindow &&
            selectImmediately &&
            !isPinned &&
            essentialId == null &&
            zenPinnedId == null &&
            (url.isBlank() || url == OmniboxEngine.NEW_TAB_URL)
        if (isReusableBlankRequest) {
            val existingBlank = WorkspaceTabResolver.findReusableBlankTab(
                tabs = currentState.tabs,
                activeTabId = currentState.activeTabId,
                spaceId = spaceId,
                isPrivate = isPrivate
            )
            if (existingBlank != null) {
                val updatedBlank = existingBlank.copy(
                    lastAccessedAt = System.currentTimeMillis(),
                    loadError = null
                )
                _uiState.update { state ->
                    applyTabSelectionTransition(
                        state = state,
                        tabs = state.tabs.map { if (it.id == existingBlank.id) updatedBlank else it },
                        selectedTabId = existingBlank.id,
                        isPrivate = isPrivate,
                        spaceId = spaceId,
                        updateActiveSpace = true,
                        selectImmediately = true,
                        closeFoldedSidebar = closeFoldedSidebar,
                        closeTabSwitcher = closeTabSwitcher,
                        dismissAllOverlays = dismissAllOverlays
                    )
                }
                if (!isPrivate) {
                    persistTab(updatedBlank)
                }
                pool.setActiveTab(existingBlank.id)
                syncActiveTabProgressAndBlocked(existingBlank.id)
                return updatedBlank
            }
        }
        val host = OmniboxEngine.extractHost(url)
        val siteOverride = SiteModeResolver.parseOrNull(
            effectiveSiteOverride(host, isPrivate)?.siteModeOverride
        )
        val resolvedMode = SiteModeResolver.resolve(null, siteOverride, currentState.activeProfile)
        val effectivePinned = !isPrivate && essentialId == null && zenPinnedId == null && isPinned
        val isBoundWorkspace = !isPrivate && (essentialId != null || zenPinnedId != null)
        val effectivePinnedUrl = when {
            isPrivate -> null
            pinnedUrl != null -> pinnedUrl
            effectivePinned -> url
            else -> null
        }

        val tabSortOrder = if (effectivePinned) {
            WorkspacePinnedFolderLogic.nextTopLevelSortOrder(
                currentState.pinnedFolders,
                currentState.tabs,
                spaceId
            )
        } else {
            0
        }
        val tab = BrowserTabUi(
            id = UUID.randomUUID().toString(),
            spaceId = spaceId,
            title = if (url == OmniboxEngine.NEW_TAB_URL) "New Tab" else host.ifBlank { url },
            url = url,
            isPrivate = isPrivate,
            isPinned = effectivePinned,
            essentialId = if (isPrivate) null else essentialId,
            zenPinnedId = if (isPrivate) null else zenPinnedId,
            isUnloaded = false,
            pinnedUrl = effectivePinnedUrl,
            customIcon = if (isPrivate) null else customIcon,
            customTitle = if (isPrivate) null else customTitle,
            folderId = null,
            sortOrder = tabSortOrder,
            isPopupWindow = isPopupWindow,
            parentTabId = parentTabId,
            resolvedSiteMode = resolvedMode,
            lastAccessedAt = System.currentTimeMillis()
        )

        _uiState.update { state ->
            val updatedTabs = if (effectivePinned) {
                val spacePinned = state.tabs.filter {
                    !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == spaceId && it.isPinned
                }
                val insertAfterId = spacePinned.lastOrNull()?.id
                if (insertAfterId != null) {
                    val idx = state.tabs.indexOfFirst { it.id == insertAfterId } + 1
                    state.tabs.toMutableList().apply { add(idx, tab) }
                } else {
                    listOf(tab) + state.tabs
                }
            } else {
                state.tabs + tab
            }
            applyTabSelectionTransition(
                state = state,
                tabs = updatedTabs,
                selectedTabId = tab.id,
                isPrivate = isPrivate,
                spaceId = spaceId,
                updateActiveSpace = (essentialId == null),
                selectImmediately = selectImmediately,
                closeFoldedSidebar = closeFoldedSidebar,
                closeTabSwitcher = closeTabSwitcher,
                dismissAllOverlays = dismissAllOverlays
            )
        }

        if (!isPrivate) {
            if (effectivePinned && !isBoundWorkspace) {
                persistTabsForSpace(spaceId, scheduleSwiftfoxPush = true)
            } else {
                persistTab(tab)
            }
        }
        if (selectImmediately) {
            pool.setActiveTab(tab.id)
            syncActiveTabProgressAndBlocked(tab.id)
        }
        return tab
    }

    fun selectTab(tabId: String) {
        omniboxHolder.cancelJobsAndClearSuggestions()
        val previousActive = _uiState.value.activeTabId
        if (previousActive != null && previousActive != tabId) {
            touchTabLastAccessed(previousActive)
            val prevSession = pool.getExisting(previousActive)
            prevSession?.clearFindMatches()
            if (_uiState.value.zapper.isActive && prevSession != null) {
                prevSession.evaluateJavascript(app.zapperController.buildStopScript(prevSession.tokens.zapperKey))
            }
        }
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val wasUnloaded = target.isUnloaded
        val restoredUrl = if (wasUnloaded) {
            target.pinnedUrl?.takeIf { it.isNotBlank() } ?: target.url
        } else {
            target.url
        }
        if (wasUnloaded) {
            pool.discardHibernatedState(tabId)
            progressByTab[tabId] = if (restoredUrl != OmniboxEngine.NEW_TAB_URL) 10 else 0
            blockedCountByTab.remove(tabId)
        }
        val accessedAt = System.currentTimeMillis()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        if (previousActive != null && previousActive != tabId) {
            _uiState.value.permissionPrompt?.takeIf { it.tabId == previousActive }?.let { denyPromptSilently(it) }
        }
        _uiState.update { state ->
            val nextActiveSpaceId = if (
                !target.isPrivate &&
                !target.isEssentialTab &&
                target.spaceId != WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID
            ) {
                target.spaceId
            } else {
                state.activeSpaceId
            }
            state.copy(
                tabs = state.tabs.map {
                    if (it.id == tabId) {
                        it.copy(
                            url = restoredUrl,
                            isUnloaded = false,
                            isLoading = if (wasUnloaded) restoredUrl != OmniboxEngine.NEW_TAB_URL else it.isLoading,
                            progress = if (wasUnloaded && restoredUrl != OmniboxEngine.NEW_TAB_URL) 10 else it.progress,
                            lastAccessedAt = accessedAt
                        )
                    } else {
                        it
                    }
                },
                activeTabId = tabId,
                isPrivateView = target.isPrivate,
                activeSpaceId = nextActiveSpaceId,
                isFoldedSidebarOpen = false,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                isOmniboxFocused = false,
                findInPage = FindInPageState(),
                zapper = ZapperUiState(),
                readerArticle = null,
                isReaderLoading = false,
                externalAppPrompt = if (previousActive != tabId) null else state.externalAppPrompt,
                httpsErrorPrompt = if (previousActive != tabId) null else state.httpsErrorPrompt,
                permissionPrompt = if (previousActive != tabId) null else state.permissionPrompt,
                pendingPinCloseTarget = null
            )
        }
        if (!target.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == tabId }?.let { persistTab(it) }
        }
        pool.setActiveTab(tabId)
        syncActiveTabProgressAndBlocked(tabId)
        applyResolvedSettingsToTab(tabId, reloadIfModeChanged = true)
    }

    fun switchAdjacentTab(direction: Int) {
        val list = _uiState.value.loadedNavigableTabs
        if (list.size < 2) return
        val currentIdx = list.indexOfFirst { it.id == _uiState.value.activeTabId }
        if (currentIdx < 0) return
        val nextIdx = (currentIdx + direction).coerceIn(0, list.lastIndex)
        if (nextIdx != currentIdx) {
            selectTab(list[nextIdx].id)
        }
    }

    fun closeTab(tabId: String) {
        closeTabInternal(tabId, allowDeletingPinned = false)
    }

    fun deletePinnedTab(tabId: String) {
        closeTabInternal(tabId, allowDeletingPinned = true)
    }

    private fun closeTabInternal(tabId: String, allowDeletingPinned: Boolean) {
        val state = _uiState.value
        val closing = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (!allowDeletingPinned && !closing.isPrivate && (closing.isPinned || closing.isBoundWorkspaceTab)) {
            closePinnedOrBoundTab(tabId)
            return
        }
        val remaining = state.tabs.filterNot { it.id == tabId }
        val wasActiveClosing = state.activeTabId == tabId
        val clearAllPrivateState = closing.isPrivate && remaining.none { it.isPrivate }
        val keepExitingThumbnailBriefly = wasActiveClosing && !closing.isStartPage && !clearAllPrivateState

        if (wasActiveClosing) {
            stopActiveZapperIfRunning()
            readerExtractJob?.cancel()
            readerExtractJob = null
            pool.getExisting(tabId)?.clearFindMatches()
            if (keepExitingThumbnailBriefly) {
                captureThumbnailFor(tabId)
            }
        }
        pool.remove(tabId)
        app.userscriptBridge.cancelCallsForTab(tabId)
        progressByTab.remove(tabId)
        blockedCountByTab.remove(tabId)
        lastRecordedHistoryUrlByTab.remove(tabId)
        lastRecordedHistoryTitleByTab.remove(tabId)
        pendingProgrammaticHttpsUpgrades.remove(tabId)
        cosmeticExceptionsByTab.remove(tabId)
        spaCheckJobByTab.remove(tabId)?.cancel()
        pageStartedInjectJobByTab.remove(tabId)?.cancel()
        pageFinishedInjectJobByTab.remove(tabId)?.cancel()
        loadErrorDiagJobByTab.remove(tabId)?.cancel()
        if (keepExitingThumbnailBriefly) {
            // Keep the final snapshot long enough for the 280ms tab-strip slide-out transition
            viewModelScope.launch {
                delay(350)
                _thumbnails.update { current ->
                    if (tabId in current.bitmaps && _uiState.value.tabs.none { it.id == tabId }) {
                        current.copy(bitmaps = current.bitmaps - tabId)
                    } else {
                        current
                    }
                }
            }
        } else {
            _thumbnails.update { current ->
                if (tabId in current.bitmaps) current.copy(bitmaps = current.bitmaps - tabId) else current
            }
        }

        if (activeMediaTabId == tabId || closing.isPlayingMedia) {
            handoffOrStopMediaService(tabId, candidateTabs = remaining)
        }

        if (!closing.isPrivate) {
            val shouldPushPinnedDelete = closing.isPinned && !closing.isBoundWorkspaceTab
            viewModelScope.launch {
                tabDbMutex.withLock {
                    db.tabDao().deleteById(tabId)
                }
                if (shouldPushPinnedDelete) {
                    app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                }
            }
        } else {
            app.faviconRepository.clearPrivateTabIcon(tabId)
            if (clearAllPrivateState) {
                app.faviconRepository.clearAllPrivateIcons()
                synchronized(privateUserscriptStorageLock) {
                    privateSessionGeneration.incrementAndGet()
                    privateUserscriptStorage.clear()
                    privateDeletedUserscriptKeys.clear()
                }
                pool.clearPrivateProfileData()
            }
        }

        val fallbackSpaceId = if (closing.isEssentialTab || closing.spaceId == WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID) {
            state.activeSpaceId
        } else {
            closing.spaceId
        }
        val nextActiveId = when {
            state.activeTabId != tabId -> state.activeTabId
            else -> WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = remaining,
                activeSpaceId = fallbackSpaceId,
                isPrivateView = closing.isPrivate,
                preferredTabId = if (closing.isPopupWindow) closing.parentTabId else null
            )
        }

        if (state.permissionPrompt?.tabId == tabId || state.activeTabId == tabId) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }

        val prevRecentlyClosedIcon = _uiState.value.recentlyClosedTab?.customIcon
        val prevAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        val showClosedSnackbar = !closing.isPrivate && !closing.isStartPage
        _uiState.update { current ->
            val wasActive = current.activeTabId == tabId
            current.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - tabId,
                privateSiteOverrides = if (clearAllPrivateState) emptyMap() else current.privateSiteOverrides,
                privateZappedRules = if (clearAllPrivateState) emptyMap() else current.privateZappedRules,
                recentlyClosedTab = if (showClosedSnackbar) closing else null,
                recentlyResetSiteOverride = null,
                recentlyAutoClearedTabs = null,
                snackbarMessage = if (showClosedSnackbar) "Closed ${closing.displayTitle}" else null,
                snackbarId = if (showClosedSnackbar) snackbarCounter.incrementAndGet() else current.snackbarId,
                zapper = if (wasActive) ZapperUiState() else current.zapper,
                readerArticle = if (wasActive) null else current.readerArticle,
                isReaderLoading = if (wasActive) false else current.isReaderLoading,
                findInPage = if (wasActive) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId == tabId || wasActive) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId == tabId || wasActive) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId == tabId || wasActive) null else current.permissionPrompt
            )
        }
        // Clean up after _uiState has dropped the closing tab (and any superseded recentlyClosedTab)
        cleanupUnusedCustomImage(prevRecentlyClosedIcon)
        prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)
        if (!closing.isPrivate && !showClosedSnackbar) {
            cleanupUnusedCustomImage(closing.customIcon)
        }

        if (nextActiveId != null && nextActiveId != state.activeTabId) {
            pool.setActiveTab(nextActiveId)
            syncActiveTabProgressAndBlocked(nextActiveId)
            applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
        } else if (nextActiveId == null) {
            if (closing.isPrivate) {
                val validSpaceId = _uiState.value.spaces
                    .firstOrNull { it.id == _uiState.value.activeSpaceId }?.id
                    ?: _uiState.value.spaces.firstOrNull()?.id
                    ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
                // Exit Private view cleanly back to the active normal Space so activeTab is never null
                selectSpace(validSpaceId)
            } else {
                createTab(spaceId = fallbackSpaceId, isPrivate = false, selectImmediately = true)
            }
        } else {
            syncActiveTabProgressAndBlocked(nextActiveId)
        }
    }

    fun undoCloseTab() {
        val closed = _uiState.value.recentlyClosedTab ?: return
        val validSpaceId = _uiState.value.spaces
            .firstOrNull { it.id == closed.spaceId }?.id
            ?: _uiState.value.activeSpaceId
        _uiState.update { it.copy(recentlyClosedTab = null, snackbarMessage = null) }
        createTab(
            url = closed.url,
            isPrivate = false,
            spaceId = validSpaceId,
            selectImmediately = true,
            isPinned = closed.isPinned,
            customIcon = closed.customIcon,
            customTitle = closed.customTitle,
            pinnedUrl = closed.pinnedUrl
        )
    }

    /**
     * Switches the active workspace shown on the New Tab / Start Page (updating Essentials & Pins)
     * without opening a new tab or leaving the Start Page.
     */
    fun switchStartPageSpace(spaceId: String) {
        val current = _uiState.value
        if (current.spaces.none { it.id == spaceId }) return
        if (!current.isPrivateView && current.activeSpaceId == spaceId) return
        omniboxHolder.cancelJobsAndClearSuggestions()
        _uiState.update {
            it.copy(
                isPrivateView = false,
                activeSpaceId = spaceId
            )
        }
    }

    fun selectSpace(spaceId: String) {
        stopActiveZapperIfRunning()
        omniboxHolder.cancelJobsAndClearSuggestions()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        val prevTabId = _uiState.value.activeTabId
        if (prevTabId != null) {
            touchTabLastAccessed(prevTabId)
            pool.getExisting(prevTabId)?.clearFindMatches()
        }
        val currentState = _uiState.value
        val spaceTabs = currentState.tabs.filter {
            !it.isPrivate && !it.isUnloaded && !it.isEssentialTab && it.spaceId == spaceId
        }
        if (spaceTabs.isNotEmpty()) {
            val nextTabId = spaceTabs.maxByOrNull { t -> t.lastAccessedAt }?.id ?: spaceTabs.first().id
            _uiState.update {
                it.copy(
                    isPrivateView = false,
                    activeSpaceId = spaceId,
                    activeTabId = nextTabId,
                    findInPage = FindInPageState(),
                    zapper = ZapperUiState(),
                    readerArticle = null,
                    isReaderLoading = false,
                    externalAppPrompt = null,
                    httpsErrorPrompt = null,
                    permissionPrompt = null
                )
            }
            pool.setActiveTab(nextTabId)
            syncActiveTabProgressAndBlocked(nextTabId)
            applyResolvedSettingsToTab(nextTabId, reloadIfModeChanged = true)
        } else {
            val keepCurrentStartPage = WorkspaceTabResolver.shouldKeepStartPageOnSpaceSwitch(currentState, spaceId)
            _uiState.update {
                it.copy(
                    isPrivateView = false,
                    activeSpaceId = spaceId,
                    findInPage = FindInPageState(),
                    zapper = ZapperUiState(),
                    readerArticle = null,
                    isReaderLoading = false,
                    externalAppPrompt = null,
                    httpsErrorPrompt = null,
                    permissionPrompt = null
                )
            }
            if (!keepCurrentStartPage) {
                createTab(spaceId = spaceId, isPrivate = false, selectImmediately = true, closeFoldedSidebar = false)
            }
        }
    }

    fun togglePrivateView(enabled: Boolean) {
        stopActiveZapperIfRunning()
        omniboxHolder.cancelJobsAndClearSuggestions()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        val prevTabId = _uiState.value.activeTabId
        if (prevTabId != null) {
            touchTabLastAccessed(prevTabId)
            pool.getExisting(prevTabId)?.clearFindMatches()
        }
        if (enabled) {
            val privateTabs = _uiState.value.tabs.filter { it.isPrivate }
            if (privateTabs.isNotEmpty()) {
                val nextTabId = privateTabs.last().id
                _uiState.update {
                    it.copy(
                        isPrivateView = true,
                        activeTabId = nextTabId,
                        findInPage = FindInPageState(),
                        zapper = ZapperUiState(),
                        readerArticle = null,
                        isReaderLoading = false,
                        externalAppPrompt = null,
                        httpsErrorPrompt = null,
                        permissionPrompt = null
                    )
                }
                pool.setActiveTab(nextTabId)
                syncActiveTabProgressAndBlocked(nextTabId)
                applyResolvedSettingsToTab(nextTabId, reloadIfModeChanged = true)
            } else {
                createTab(isPrivate = true, selectImmediately = true, closeFoldedSidebar = false)
            }
        } else {
            val validSpaceId = _uiState.value.spaces
                .firstOrNull { it.id == _uiState.value.activeSpaceId }?.id
                ?: _uiState.value.spaces.firstOrNull()?.id
                ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
            selectSpace(validSpaceId)
        }
    }

    fun createSpace(name: String, iconKey: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val id = "space_${UUID.randomUUID().toString().take(8)}"
        val space = SpaceEntity(
            id = id,
            name = trimmed,
            iconKey = iconKey,
            sortOrder = _uiState.value.spaces.size
        )
        pendingCreatedSpaces[id] = space
        _uiState.update { current ->
            if (current.spaces.any { it.id == id }) current
            else current.copy(spaces = current.spaces + space)
        }
        viewModelScope.launch {
            db.spaceDao().upsert(space)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        selectSpace(id)
    }

    fun deleteSpace(spaceId: String) {
        if (_uiState.value.spaces.size <= 1) return
        val spaceToDelete = _uiState.value.spaces.firstOrNull { it.id == spaceId } ?: return
        val fallback = _uiState.value.spaces.first { it.id != spaceId }
        val wasViewingDeletedSpace = !_uiState.value.isPrivateView && _uiState.value.activeSpaceId == spaceId
        val wasActiveSpacePointer = _uiState.value.activeSpaceId == spaceId
        val affectedTabs = _uiState.value.tabs.filter { !it.isPrivate && it.spaceId == spaceId }
        val affectedIds = affectedTabs.mapTo(mutableSetOf()) { it.id }
        affectedTabs.forEach { tab ->
            pool.remove(tab.id)
            app.userscriptBridge.cancelCallsForTab(tab.id)
            progressByTab.remove(tab.id)
            blockedCountByTab.remove(tab.id)
            lastRecordedHistoryUrlByTab.remove(tab.id)
            lastRecordedHistoryTitleByTab.remove(tab.id)
            pendingProgrammaticHttpsUpgrades.remove(tab.id)
            cosmeticExceptionsByTab.remove(tab.id)
            spaCheckJobByTab.remove(tab.id)?.cancel()
            pageStartedInjectJobByTab.remove(tab.id)?.cancel()
            pageFinishedInjectJobByTab.remove(tab.id)?.cancel()
            loadErrorDiagJobByTab.remove(tab.id)?.cancel()
        }
        val remainingAfter = _uiState.value.tabs.filterNot { !it.isPrivate && it.spaceId == spaceId }
        if (activeMediaTabId in affectedIds) {
            handoffOrStopMediaService(activeMediaTabId ?: "", candidateTabs = remainingAfter)
        }
        if (wasViewingDeletedSpace || _uiState.value.permissionPrompt?.tabId in affectedIds) {
            _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        }
        _thumbnails.update { current ->
            current.copy(bitmaps = current.bitmaps - affectedIds)
        }
        pendingCreatedSpaces.remove(spaceId)
        val affectedFolders = _uiState.value.pinnedFolders.filter { it.spaceId == spaceId }
        affectedFolders.forEach { pendingCreatedPinnedFolders.remove(it.id) }
        val affectedFolderIcons = affectedFolders.mapNotNull { it.customIcon }
        val affectedZenPinnedIcons = _uiState.value.zenPinnedItems
            .filter { it.spaceId == spaceId }
            .mapNotNull { it.customIcon }
        val affectedTabIcons = affectedTabs.mapNotNull { it.customIcon }
        _uiState.update { state ->
            val nextActiveSpaceId = if (wasActiveSpacePointer) fallback.id else state.activeSpaceId
            val nextActiveTabId = if (!wasViewingDeletedSpace && state.activeTabId in affectedIds) {
                WorkspaceTabResolver.resolveFallbackTabId(
                    remainingTabs = remainingAfter,
                    activeSpaceId = nextActiveSpaceId,
                    isPrivateView = state.isPrivateView
                )
            } else {
                state.activeTabId
            }
            state.copy(
                spaces = state.spaces.filterNot { it.id == spaceId },
                activeSpaceId = nextActiveSpaceId,
                activeTabId = nextActiveTabId,
                pinnedFolders = state.pinnedFolders.filterNot { it.spaceId == spaceId },
                tabs = remainingAfter,
                pwaCandidatesByTab = state.pwaCandidatesByTab - affectedIds,
                zenPinnedItems = state.zenPinnedItems.filterNot { it.spaceId == spaceId },
                externalAppPrompt = if (wasViewingDeletedSpace || state.externalAppPrompt?.tabId in affectedIds) null else state.externalAppPrompt,
                httpsErrorPrompt = if (wasViewingDeletedSpace || state.httpsErrorPrompt?.tabId in affectedIds) null else state.httpsErrorPrompt,
                permissionPrompt = if (wasViewingDeletedSpace || state.permissionPrompt?.tabId in affectedIds) null else state.permissionPrompt
            )
        }
        val shouldPushDelete = spaceToDelete.syncGuid.isNullOrBlank() ||
            affectedFolders.isNotEmpty() ||
            affectedTabs.any { it.isPinned && !it.isBoundWorkspaceTab }
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().deleteBySpace(spaceId)
                db.pinnedFolderDao().deleteBySpace(spaceId)
                db.zenPinnedItemDao().deleteBySpace(spaceId)
                db.spaceDao().deleteById(spaceId)
            }
            if (shouldPushDelete) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
        if (wasViewingDeletedSpace) {
            selectSpace(fallback.id)
        }
        showSnackbar("Deleted Space \"${spaceToDelete.name}\"")
        cleanupUnusedCustomImage(spaceToDelete.iconKey)
        affectedFolderIcons.forEach(::cleanupUnusedCustomImage)
        affectedTabIcons.forEach(::cleanupUnusedCustomImage)
        affectedZenPinnedIcons.forEach(::cleanupUnusedCustomImage)
    }

    fun renameSpace(spaceId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val existing = _uiState.value.spaces.firstOrNull { it.id == spaceId } ?: return
        val updated = existing.copy(name = trimmed)
        _uiState.update { state ->
            state.copy(spaces = state.spaces.map { if (it.id == spaceId) updated else it })
        }
        viewModelScope.launch {
            db.spaceDao().upsert(updated)
            if (updated.syncGuid.isNullOrBlank()) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
        showSnackbar("Renamed Space to \"$trimmed\"")
    }

    fun setSpaceIcon(spaceId: String, iconKey: String) {
        val existing = _uiState.value.spaces.firstOrNull { it.id == spaceId } ?: return
        val oldIcon = existing.iconKey
        val nextKey = CustomIconSpec.parse(iconKey).encodeExplicit() ?: "${CustomIconSpec.ICON_PREFIX}layers"
        val updated = existing.copy(iconKey = nextKey)
        _uiState.update { state ->
            state.copy(spaces = state.spaces.map { if (it.id == spaceId) updated else it })
        }
        viewModelScope.launch {
            db.spaceDao().upsert(updated)
            if (updated.syncGuid.isNullOrBlank()) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
        if (oldIcon != nextKey) cleanupUnusedCustomImage(oldIcon)
    }

    fun reorderSpaces(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val current = _uiState.value.spaces
        val byId = current.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val reordered = buildList {
            for (id in orderedIds) {
                val space = byId[id]
                if (space != null && seen.add(id)) add(space)
            }
            for (space in current) {
                if (seen.add(space.id)) add(space)
            }
        }.mapIndexed { idx, space -> space.copy(sortOrder = idx) }

        _uiState.update { it.copy(spaces = reordered) }
        viewModelScope.launch {
            db.spaceDao().upsertAll(reordered)
            if (reordered.any { it.syncGuid.isNullOrBlank() }) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
    }

    fun addEssential(title: String, rawUrl: String, customIcon: String? = null) {
        val resolved = OmniboxEngine.resolveInput(rawUrl, _uiState.value.settings)
        val host = OmniboxEngine.extractHost(resolved)
        val essential = EssentialEntity(
            id = "ess_${UUID.randomUUID().toString().take(8)}",
            title = title.trim().ifBlank { host.ifBlank { "Site" } },
            url = resolved,
            domain = host.ifBlank { resolved },
            sortOrder = _uiState.value.essentials.size,
            customIcon = customIcon
        )
        pendingCreatedEssentials[essential.id] = essential
        _uiState.update { state ->
            if (state.essentials.any { it.id == essential.id }) state else state.copy(essentials = state.essentials + essential)
        }
        viewModelScope.launch {
            db.essentialDao().upsert(essential)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            showSnackbar("Pinned ${essential.title} to Essentials")
        }
    }

    fun updateEssential(id: String, title: String, rawUrl: String) {
        val existing = _uiState.value.essentials.firstOrNull { it.id == id } ?: return
        val resolved = OmniboxEngine.resolveInput(rawUrl, _uiState.value.settings)
        val urlChanged = !resolved.equals(existing.url, ignoreCase = false)
        val host = OmniboxEngine.extractHost(resolved)
        val updated = existing.copy(
            title = title.trim().ifBlank { host.ifBlank { existing.title } },
            url = resolved,
            domain = host.ifBlank { resolved }
        )
        val boundTab = _uiState.value.tabForEssential(id)
        _uiState.update { state ->
            state.copy(
                essentials = state.essentials.map { if (it.id == id) updated else it },
                tabs = if (boundTab != null) {
                    state.tabs.map { tab ->
                        if (tab.id == boundTab.id) {
                            tab.copy(
                                customTitle = updated.title,
                                pinnedUrl = updated.url,
                                url = if (urlChanged) updated.url else tab.url,
                                title = if (urlChanged) updated.title else tab.title
                            )
                        } else {
                            tab
                        }
                    }
                } else {
                    state.tabs
                }
            )
        }
        if (boundTab != null) {
            if (urlChanged) {
                pool.discardHibernatedState(boundTab.id)
                val existingSession = pool.getExisting(boundTab.id)
                if (existingSession != null && !existingSession.isDestroyed) {
                    val effective = resolveHttpsOnlyForProgrammaticLoad(boundTab.id, updated.url, existingSession)
                    existingSession.loadUrl(effective)
                }
            }
            _uiState.value.tabs.firstOrNull { it.id == boundTab.id }?.let { persistTab(it) }
        }
        viewModelScope.launch {
            db.essentialDao().upsert(updated)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        showSnackbar("Updated ${updated.title}")
    }

    fun setEssentialCustomIcon(id: String, customIcon: String?) {
        val existing = _uiState.value.essentials.firstOrNull { it.id == id } ?: return
        val oldIcon = existing.customIcon
        val updated = existing.copy(customIcon = customIcon)
        val boundTab = _uiState.value.tabForEssential(id)
        _uiState.update { state ->
            state.copy(
                essentials = state.essentials.map { if (it.id == id) updated else it },
                tabs = if (boundTab != null) {
                    state.tabs.map { if (it.id == boundTab.id) it.copy(customIcon = customIcon) else it }
                } else {
                    state.tabs
                }
            )
        }
        if (boundTab != null) {
            _uiState.value.tabs.firstOrNull { it.id == boundTab.id }?.let { persistTab(it) }
        }
        viewModelScope.launch {
            db.essentialDao().upsert(updated)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        if (oldIcon != customIcon) cleanupUnusedCustomImage(oldIcon)
    }

    fun reorderEssentials(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val current = _uiState.value.essentials
        val byId = current.associateBy { it.id }
        val targetSet = orderedIds.toSet()
        val orderedSubset = orderedIds.mapNotNull { byId[it] }
        if (orderedSubset.isEmpty()) return

        // Place reordered visible essentials back into the slots they occupied so hidden container essentials keep relative order
        var subsetCursor = 0
        val merged = current.map { ess ->
            if (ess.id in targetSet && subsetCursor < orderedSubset.size) {
                orderedSubset[subsetCursor++]
            } else {
                ess
            }
        }.mapIndexed { idx, ess -> ess.copy(sortOrder = idx) }

        _uiState.update { it.copy(essentials = merged) }
        viewModelScope.launch {
            db.essentialDao().upsertAll(merged)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
    }

    fun removeEssential(id: String) {
        val existing = _uiState.value.essentials.firstOrNull { it.id == id }
        val boundTab = _uiState.value.tabForEssential(id)
        pendingCreatedEssentials.remove(id)
        if (boundTab != null) {
            closePinnedOrBoundTab(boundTab.id, showClosedSnackbar = false)
        }
        _uiState.update { state ->
            state.copy(essentials = state.essentials.filterNot { it.id == id })
        }
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().deleteByEssentialId(id)
            }
            db.essentialDao().deleteById(id)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        cleanupUnusedCustomImage(existing?.customIcon)
    }

    fun activateEssential(essentialId: String) {
        val state = _uiState.value
        val essential = state.essentials.firstOrNull { it.id == essentialId } ?: return
        val existing = state.tabForEssential(essentialId)
        if (existing == null) {
            val targetSpaceId = WorkspaceTabResolver.resolveEssentialSpaceId(
                essential = essential,
                spaces = state.spaces,
                activeSpaceId = state.activeSpaceId,
                settings = state.settings,
                allEssentials = state.essentials
            )
            createTab(
                url = essential.url,
                isPrivate = false,
                spaceId = targetSpaceId,
                selectImmediately = true,
                closeFoldedSidebar = true,
                dismissAllOverlays = true,
                customIcon = essential.customIcon,
                customTitle = essential.title,
                essentialId = essential.id,
                pinnedUrl = essential.url
            )
        } else if (state.activeTabId == existing.id && !state.isPrivateView) {
            requestClosePinnedOrBoundTab(existing.id, essential.title, PinnedTabKind.ESSENTIAL)
        } else {
            selectTab(existing.id)
        }
    }

    fun closeEssentialTab(essentialId: String) {
        val boundTab = _uiState.value.tabForEssential(essentialId) ?: return
        closePinnedOrBoundTab(boundTab.id)
    }

    fun resetEssentialTab(essentialId: String) {
        val essential = _uiState.value.essentials.firstOrNull { it.id == essentialId } ?: return
        val boundTab = _uiState.value.tabForEssential(essentialId) ?: return
        navigateBoundTabToUrl(boundTab.id, essential.url)
    }

    fun activateZenPinned(zenPinnedId: String) {
        val state = _uiState.value
        val item = state.zenPinnedItems.firstOrNull { it.id == zenPinnedId } ?: return
        val existing = state.tabForZenPinned(zenPinnedId)
        if (existing == null) {
            createTab(
                url = item.url,
                isPrivate = false,
                spaceId = item.spaceId,
                selectImmediately = true,
                closeFoldedSidebar = true,
                dismissAllOverlays = true,
                customIcon = item.customIcon,
                customTitle = item.title,
                zenPinnedId = item.id,
                pinnedUrl = item.url
            )
        } else if (state.activeTabId == existing.id && !state.isPrivateView) {
            requestClosePinnedOrBoundTab(existing.id, item.title, PinnedTabKind.ZEN_PINNED)
        } else {
            selectTab(existing.id)
        }
    }

    fun closeZenPinnedTab(zenPinnedId: String) {
        val boundTab = _uiState.value.tabForZenPinned(zenPinnedId) ?: return
        closePinnedOrBoundTab(boundTab.id)
    }

    fun resetZenPinnedTab(zenPinnedId: String) {
        val item = _uiState.value.zenPinnedItems.firstOrNull { it.id == zenPinnedId } ?: return
        val boundTab = _uiState.value.tabForZenPinned(zenPinnedId) ?: return
        navigateBoundTabToUrl(boundTab.id, item.url)
    }

    fun activatePinnedTab(tabId: String) {
        val state = _uiState.value
        val target = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (!target.isPinned || target.isPrivate || target.isBoundWorkspaceTab) {
            selectTab(tabId)
            return
        }
        when {
            target.isUnloaded -> selectTab(tabId)
            state.activeTabId == tabId && !state.isPrivateView -> {
                requestClosePinnedOrBoundTab(target.id, target.displayTitle, PinnedTabKind.PINNED_TAB)
            }
            else -> selectTab(tabId)
        }
    }

    fun unloadPinnedTab(tabId: String) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (!target.isPinned || target.isPrivate || target.isUnloaded) return
        closePinnedOrBoundTab(tabId)
    }

    fun resetPinnedTab(tabId: String) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val homeUrl = target.pinnedUrl?.takeIf { it.isNotBlank() } ?: return
        navigateBoundTabToUrl(tabId, homeUrl)
    }

    private fun requestClosePinnedOrBoundTab(tabId: String, title: String, kind: PinnedTabKind) {
        if (_uiState.value.settings.confirmClosePinnedOnSecondTap) {
            _uiState.update {
                it.copy(pendingPinCloseTarget = PendingPinCloseTarget(tabId = tabId, title = title, kind = kind))
            }
        } else {
            closePinnedOrBoundTab(tabId)
        }
    }

    fun confirmClosePendingPinTab(dontShowAgain: Boolean) {
        val pending = _uiState.value.pendingPinCloseTarget ?: return
        _uiState.update { it.copy(pendingPinCloseTarget = null) }
        if (dontShowAgain) {
            setConfirmClosePinnedOnSecondTap(false)
        }
        closePinnedOrBoundTab(pending.tabId)
    }

    fun dismissPendingPinClose() {
        _uiState.update { it.copy(pendingPinCloseTarget = null) }
    }

    private fun navigateBoundTabToUrl(tabId: String, url: String) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        pool.discardHibernatedState(tabId)
        app.userscriptBridge.cancelCallsForTab(tabId)
        val existingSession = pool.getExisting(tabId)
        existingSession?.clearFindMatches()
        val effectiveUrl = resolveHttpsOnlyForProgrammaticLoad(tabId, url, existingSession)
        if (activeMediaTabId == tabId) {
            handoffOrStopMediaService(tabId)
        }
        val initialProgress = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) 0 else 10
        progressByTab[tabId] = initialProgress
        blockedCountByTab.remove(tabId)
        updateTab(tabId) {
            it.copy(
                url = effectiveUrl,
                isUnloaded = false,
                isLoading = effectiveUrl != OmniboxEngine.NEW_TAB_URL,
                progress = initialProgress,
                isPlayingMedia = false,
                isPlayingVideo = false,
                isReaderAvailable = false,
                darkReaderSkippedNative = false
            )
        }
        _uiState.value.tabs.firstOrNull { it.id == tabId }?.let { persistTab(it) }
        if (existingSession != null && !existingSession.isDestroyed) {
            val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: target
            applySettingsToSession(currentTab, existingSession, reloadIfModeChanged = false)
            existingSession.loadUrl(effectiveUrl)
        }
        selectTab(tabId)
    }

    private fun releaseTabSessionResources(tabId: String, wasActive: Boolean) {
        if (wasActive) {
            stopActiveZapperIfRunning()
            readerExtractJob?.cancel()
            readerExtractJob = null
            pool.getExisting(tabId)?.clearFindMatches()
        }
        pool.remove(tabId)
        app.userscriptBridge.cancelCallsForTab(tabId)
        progressByTab.remove(tabId)
        blockedCountByTab.remove(tabId)
        lastRecordedHistoryUrlByTab.remove(tabId)
        lastRecordedHistoryTitleByTab.remove(tabId)
        pendingProgrammaticHttpsUpgrades.remove(tabId)
        cosmeticExceptionsByTab.remove(tabId)
        spaCheckJobByTab.remove(tabId)?.cancel()
        pageStartedInjectJobByTab.remove(tabId)?.cancel()
        pageFinishedInjectJobByTab.remove(tabId)?.cancel()
        loadErrorDiagJobByTab.remove(tabId)?.cancel()
    }

    private fun closePinnedOrBoundTab(tabId: String, showClosedSnackbar: Boolean = true) {
        val state = _uiState.value
        val target = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (target.isPrivate) return
        val wasActive = state.activeTabId == tabId
        releaseTabSessionResources(tabId, wasActive)

        _thumbnails.update { current ->
            if (tabId in current.bitmaps) current.copy(bitmaps = current.bitmaps - tabId) else current
        }

        val updatedTabs: List<BrowserTabUi>
        val unloadedTabToPersist: BrowserTabUi?
        val shouldDeleteFromDb: Boolean
        if (target.isBoundWorkspaceTab) {
            updatedTabs = state.tabs.filterNot { it.id == tabId }
            unloadedTabToPersist = null
            shouldDeleteFromDb = true
        } else if (target.isPinned) {
            val unloaded = WorkspaceTabResolver.toUnloadedPinnedTab(target)
            updatedTabs = state.tabs.map { if (it.id == tabId) unloaded else it }
            unloadedTabToPersist = unloaded
            shouldDeleteFromDb = false
        } else {
            return
        }

        if (activeMediaTabId == tabId || target.isPlayingMedia) {
            handoffOrStopMediaService(tabId, candidateTabs = updatedTabs.filter { !it.isUnloaded })
        }
        if (state.permissionPrompt?.tabId == tabId || wasActive) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }

        val fallbackSpaceId = state.activeSpaceId
        val nextActiveId = if (wasActive) {
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = updatedTabs,
                activeSpaceId = fallbackSpaceId,
                isPrivateView = state.isPrivateView
            )
        } else {
            state.activeTabId
        }

        val displayName = when {
            target.essentialId != null -> state.essentials.firstOrNull { it.id == target.essentialId }?.title ?: target.displayTitle
            target.zenPinnedId != null -> state.zenPinnedItems.firstOrNull { it.id == target.zenPinnedId }?.title ?: target.displayTitle
            else -> target.displayTitle
        }

        val prevAutoClearedIcons = if (showClosedSnackbar) _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty() else emptyList()
        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - tabId,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId == tabId) null else current.pendingPinCloseTarget,
                recentlyClosedTab = null,
                recentlyAutoClearedTabs = if (showClosedSnackbar) null else current.recentlyAutoClearedTabs,
                snackbarMessage = if (showClosedSnackbar) "Closed $displayName" else current.snackbarMessage,
                snackbarId = if (showClosedSnackbar) snackbarCounter.incrementAndGet() else current.snackbarId,
                zapper = if (wasActive) ZapperUiState() else current.zapper,
                readerArticle = if (wasActive) null else current.readerArticle,
                isReaderLoading = if (wasActive) false else current.isReaderLoading,
                findInPage = if (wasActive) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId == tabId || wasActive) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId == tabId || wasActive) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId == tabId || wasActive) null else current.permissionPrompt
            )
        }

        if (shouldDeleteFromDb) {
            viewModelScope.launch {
                tabDbMutex.withLock {
                    db.tabDao().deleteById(tabId)
                }
            }
        } else if (unloadedTabToPersist != null) {
            persistTab(unloadedTabToPersist)
        }
        prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)

        if (wasActive) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
                syncActiveTabProgressAndBlocked(nextActiveId)
                applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
            } else if (!state.isPrivateView) {
                createTab(
                    spaceId = fallbackSpaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
    }

    private fun pruneOrphanedBoundTabs(orphanedTabIds: List<String>) {
        if (orphanedTabIds.isEmpty()) return
        val orphanSet = orphanedTabIds.toSet()
        val state = _uiState.value
        val toRemove = state.tabs.filter { it.id in orphanSet }
        if (toRemove.isEmpty()) return
        val wasActiveRemoved = state.activeTabId in orphanSet
        toRemove.forEach { tab ->
            releaseTabSessionResources(tab.id, wasActive = tab.id == state.activeTabId)
        }
        val remaining = state.tabs.filterNot { it.id in orphanSet }
        _thumbnails.update { current -> current.copy(bitmaps = current.bitmaps - orphanSet) }
        if (activeMediaTabId in orphanSet) {
            handoffOrStopMediaService(activeMediaTabId ?: "", candidateTabs = remaining.filter { !it.isUnloaded })
        }
        if (wasActiveRemoved || state.permissionPrompt?.tabId in orphanSet) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }
        val nextActiveId = if (wasActiveRemoved) {
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = remaining,
                activeSpaceId = state.activeSpaceId,
                isPrivateView = state.isPrivateView
            )
        } else {
            state.activeTabId
        }
        _uiState.update { current ->
            current.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - orphanSet,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId in orphanSet) null else current.pendingPinCloseTarget,
                zapper = if (wasActiveRemoved) ZapperUiState() else current.zapper,
                readerArticle = if (wasActiveRemoved) null else current.readerArticle,
                isReaderLoading = if (wasActiveRemoved) false else current.isReaderLoading,
                findInPage = if (wasActiveRemoved) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in orphanSet || wasActiveRemoved) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in orphanSet || wasActiveRemoved) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in orphanSet || wasActiveRemoved) null else current.permissionPrompt
            )
        }
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().deleteByIds(orphanedTabIds)
            }
        }
        if (wasActiveRemoved) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
                syncActiveTabProgressAndBlocked(nextActiveId)
                applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
            } else if (!state.isPrivateView) {
                createTab(
                    spaceId = state.activeSpaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
    }

    fun closeAllLoadedEssentialsAndPins() {
        val state = _uiState.value
        val targets = WorkspaceTabResolver.collectLoadedEssentialsAndPinsInScope(state)
        if (targets.isEmpty()) return
        val targetIds = targets.mapTo(mutableSetOf()) { it.id }
        val boundIdsToDelete = targets.filter { it.isBoundWorkspaceTab }.map { it.id }
        val wasActiveClosed = state.activeTabId in targetIds

        targets.forEach { tab ->
            releaseTabSessionResources(tab.id, wasActive = tab.id == state.activeTabId)
        }
        _thumbnails.update { current -> current.copy(bitmaps = current.bitmaps - targetIds) }

        val unloadedLocals = mutableListOf<BrowserTabUi>()
        val updatedTabs = buildList {
            for (tab in state.tabs) {
                if (tab.id !in targetIds) {
                    add(tab)
                } else if (!tab.isBoundWorkspaceTab && tab.isPinned) {
                    val unloaded = WorkspaceTabResolver.toUnloadedPinnedTab(tab)
                    unloadedLocals += unloaded
                    add(unloaded)
                }
            }
        }

        if (activeMediaTabId in targetIds || targets.any { it.isPlayingMedia }) {
            handoffOrStopMediaService(activeMediaTabId ?: "", candidateTabs = updatedTabs.filter { !it.isUnloaded })
        }
        if (wasActiveClosed || state.permissionPrompt?.tabId in targetIds) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }

        val nextActiveId = if (wasActiveClosed) {
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = updatedTabs,
                activeSpaceId = state.activeSpaceId,
                isPrivateView = false
            )
        } else {
            state.activeTabId
        }

        val prevAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        val count = targets.size
        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - targetIds,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId in targetIds) null else current.pendingPinCloseTarget,
                recentlyClosedTab = null,
                recentlyAutoClearedTabs = null,
                snackbarMessage = "Closed $count loaded ${if (count == 1) "tab" else "tabs"}",
                snackbarId = snackbarCounter.incrementAndGet(),
                zapper = if (wasActiveClosed) ZapperUiState() else current.zapper,
                readerArticle = if (wasActiveClosed) null else current.readerArticle,
                isReaderLoading = if (wasActiveClosed) false else current.isReaderLoading,
                findInPage = if (wasActiveClosed) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in targetIds || wasActiveClosed) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in targetIds || wasActiveClosed) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in targetIds || wasActiveClosed) null else current.permissionPrompt
            )
        }

        if (boundIdsToDelete.isNotEmpty()) {
            viewModelScope.launch {
                tabDbMutex.withLock {
                    db.tabDao().deleteByIds(boundIdsToDelete)
                }
            }
        }
        unloadedLocals.forEach { persistTab(it) }
        prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)

        if (wasActiveClosed) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
                syncActiveTabProgressAndBlocked(nextActiveId)
                applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
            } else {
                createTab(
                    spaceId = state.activeSpaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
    }

    fun closeAllOpenTabsInActiveSpace() {
        val state = _uiState.value
        val targets = if (state.isPrivateView) {
            state.tabs.filter { it.isPrivate }
        } else {
            state.visibleRegularTabs
        }
        if (targets.isEmpty()) return
        val targetIds = targets.mapTo(mutableSetOf()) { it.id }
        val wasActiveClosed = state.activeTabId in targetIds
        val affectedIcons = targets.mapNotNull { it.customIcon }

        targets.forEach { tab ->
            releaseTabSessionResources(tab.id, wasActive = tab.id == state.activeTabId)
        }
        _thumbnails.update { current -> current.copy(bitmaps = current.bitmaps - targetIds) }

        val remaining = state.tabs.filterNot { it.id in targetIds }
        if (activeMediaTabId in targetIds || targets.any { it.isPlayingMedia }) {
            handoffOrStopMediaService(activeMediaTabId ?: "", candidateTabs = remaining.filter { !it.isUnloaded })
        }
        if (wasActiveClosed || state.permissionPrompt?.tabId in targetIds) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }

        if (state.isPrivateView) {
            val prevAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
            app.faviconRepository.clearAllPrivateIcons()
            synchronized(privateUserscriptStorageLock) {
                privateSessionGeneration.incrementAndGet()
                privateUserscriptStorage.clear()
                privateDeletedUserscriptKeys.clear()
            }
            pool.clearPrivateProfileData()
            val validSpaceId = state.spaces.firstOrNull { it.id == state.activeSpaceId }?.id
                ?: state.spaces.firstOrNull()?.id
                ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
            _uiState.update { current ->
                current.copy(
                    tabs = remaining,
                    activeTabId = null,
                    pwaCandidatesByTab = current.pwaCandidatesByTab - targetIds,
                    privateSiteOverrides = emptyMap(),
                    privateZappedRules = emptyMap(),
                    pendingPinCloseTarget = null,
                    recentlyClosedTab = null,
                    recentlyAutoClearedTabs = null,
                    snackbarMessage = "Closed all private tabs",
                    snackbarId = snackbarCounter.incrementAndGet(),
                    zapper = ZapperUiState(),
                    readerArticle = null,
                    isReaderLoading = false,
                    findInPage = FindInPageState(),
                    externalAppPrompt = null,
                    httpsErrorPrompt = null,
                    permissionPrompt = null
                )
            }
            prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)
            selectSpace(validSpaceId)
            return
        }

        val nextActiveId = if (wasActiveClosed) {
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = remaining,
                activeSpaceId = state.activeSpaceId,
                isPrivateView = false
            )
        } else {
            state.activeTabId
        }
        val prevAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        val count = targets.size
        _uiState.update { current ->
            current.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - targetIds,
                recentlyClosedTab = null,
                recentlyAutoClearedTabs = null,
                snackbarMessage = "Closed $count open ${if (count == 1) "tab" else "tabs"}",
                snackbarId = snackbarCounter.incrementAndGet(),
                zapper = if (wasActiveClosed) ZapperUiState() else current.zapper,
                readerArticle = if (wasActiveClosed) null else current.readerArticle,
                isReaderLoading = if (wasActiveClosed) false else current.isReaderLoading,
                findInPage = if (wasActiveClosed) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in targetIds || wasActiveClosed) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in targetIds || wasActiveClosed) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in targetIds || wasActiveClosed) null else current.permissionPrompt
            )
        }
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().deleteByIds(targets.map { it.id })
            }
        }
        affectedIcons.forEach(::cleanupUnusedCustomImage)
        prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)

        if (wasActiveClosed) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
                syncActiveTabProgressAndBlocked(nextActiveId)
                applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
            } else {
                createTab(
                    spaceId = state.activeSpaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
    }

    fun closeEverythingInActiveSpace() {
        val state = _uiState.value
        if (state.isPrivateView) {
            closeAllOpenTabsInActiveSpace()
            return
        }
        val boundAndPins = WorkspaceTabResolver.collectLoadedEssentialsAndPinsInScope(state)
        val regularTabs = state.visibleRegularTabs
        if (boundAndPins.isEmpty() && regularTabs.isEmpty()) return

        val boundIdsToDelete = boundAndPins.filter { it.isBoundWorkspaceTab }.map { it.id }
        val localPinIdsToUnload = boundAndPins.filter { !it.isBoundWorkspaceTab && it.isPinned }.mapTo(mutableSetOf()) { it.id }
        val regularIdsToDelete = regularTabs.map { it.id }
        val allAffectedIds = (boundIdsToDelete + localPinIdsToUnload + regularIdsToDelete).toSet()
        val wasActiveClosed = state.activeTabId in allAffectedIds
        val regularIcons = regularTabs.mapNotNull { it.customIcon }

        allAffectedIds.forEach { id ->
            releaseTabSessionResources(id, wasActive = id == state.activeTabId)
        }
        _thumbnails.update { current -> current.copy(bitmaps = current.bitmaps - allAffectedIds) }

        val idsToRemove = (boundIdsToDelete + regularIdsToDelete).toSet()
        val unloadedLocals = mutableListOf<BrowserTabUi>()
        val updatedTabs = buildList {
            for (tab in state.tabs) {
                when {
                    tab.id in idsToRemove -> Unit
                    tab.id in localPinIdsToUnload -> {
                        val unloaded = WorkspaceTabResolver.toUnloadedPinnedTab(tab)
                        unloadedLocals += unloaded
                        add(unloaded)
                    }
                    else -> add(tab)
                }
            }
        }

        if (activeMediaTabId in allAffectedIds || state.tabs.any { it.id in allAffectedIds && it.isPlayingMedia }) {
            handoffOrStopMediaService(activeMediaTabId ?: "", candidateTabs = updatedTabs.filter { !it.isUnloaded })
        }
        if (wasActiveClosed || state.permissionPrompt?.tabId in allAffectedIds) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }

        val prevAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = if (wasActiveClosed) null else current.activeTabId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - allAffectedIds,
                pendingPinCloseTarget = null,
                recentlyClosedTab = null,
                recentlyAutoClearedTabs = null,
                snackbarMessage = "Closed all tabs in ${state.activeSpace?.name ?: "Space"}",
                snackbarId = snackbarCounter.incrementAndGet(),
                zapper = if (wasActiveClosed) ZapperUiState() else current.zapper,
                readerArticle = if (wasActiveClosed) null else current.readerArticle,
                isReaderLoading = if (wasActiveClosed) false else current.isReaderLoading,
                findInPage = if (wasActiveClosed) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in allAffectedIds || wasActiveClosed) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in allAffectedIds || wasActiveClosed) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in allAffectedIds || wasActiveClosed) null else current.permissionPrompt
            )
        }

        val allDeletes = boundIdsToDelete + regularIdsToDelete
        if (allDeletes.isNotEmpty()) {
            viewModelScope.launch {
                tabDbMutex.withLock {
                    db.tabDao().deleteByIds(allDeletes)
                }
            }
        }
        unloadedLocals.forEach { persistTab(it) }
        regularIcons.forEach(::cleanupUnusedCustomImage)
        prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)

        if (wasActiveClosed || _uiState.value.activeTab == null) {
            createTab(
                spaceId = state.activeSpaceId,
                isPrivate = false,
                selectImmediately = true,
                closeFoldedSidebar = false,
                closeTabSwitcher = false
            )
        } else {
            syncActiveTabProgressAndBlocked()
        }
    }

    fun pinCurrentTabToEssentials() {
        val tab = _uiState.value.activeTab ?: return
        if (tab.isPrivate || tab.isStartPage) return
        if (tab.isEssentialTab) {
            showSnackbar("Already in Essentials")
            return
        }
        promoteOrAddTabToEssentials(tab)
    }

    fun addTabToEssentials(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.isPrivate || tab.isStartPage) return
        if (tab.isEssentialTab) {
            showSnackbar("Already in Essentials")
            return
        }
        promoteOrAddTabToEssentials(tab)
    }

    private fun promoteOrAddTabToEssentials(tab: BrowserTabUi) {
        val state = _uiState.value
        val host = OmniboxEngine.extractHost(tab.url)
        val essential = EssentialEntity(
            id = "ess_${UUID.randomUUID().toString().take(8)}",
            title = tab.displayTitle.take(24).trim().ifBlank { host.ifBlank { "Site" } },
            url = tab.url,
            domain = host.ifBlank { tab.url },
            sortOrder = state.essentials.size,
            customIcon = tab.customIcon
        )
        pendingCreatedEssentials[essential.id] = essential

        val shouldConvertInPlace = !tab.isPinned && !tab.isBoundWorkspaceTab
        val resolvedSpaceId = WorkspaceTabResolver.resolveEssentialSpaceId(
            essential = essential,
            spaces = state.spaces,
            activeSpaceId = state.activeSpaceId,
            settings = state.settings,
            allEssentials = state.essentials + essential
        )
        _uiState.update { current ->
            current.copy(
                essentials = current.essentials + essential,
                tabs = if (shouldConvertInPlace) {
                    current.tabs.map {
                        if (it.id == tab.id) {
                            it.copy(
                                essentialId = essential.id,
                                spaceId = resolvedSpaceId,
                                pinnedUrl = tab.url,
                                customTitle = essential.title
                            )
                        } else {
                            it
                        }
                    }
                } else {
                    current.tabs
                }
            )
        }
        if (shouldConvertInPlace) {
            _uiState.value.tabs.firstOrNull { it.id == tab.id }?.let { persistTab(it) }
        }
        viewModelScope.launch {
            db.essentialDao().upsert(essential)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            showSnackbar("Pinned ${essential.title} to Essentials")
        }
    }

    fun setTabPinned(tabId: String, pinned: Boolean) {
        val current = _uiState.value
        val target = current.tabs.firstOrNull { it.id == tabId } ?: return
        if (target.isPrivate || target.isBoundWorkspaceTab || target.isPinned == pinned) return
        val baselineTitle = target.customTitle?.takeIf { it.isNotBlank() }
            ?: target.title.takeIf { it.isNotBlank() && !target.isStartPage && !it.equals("New Tab", ignoreCase = true) }
        val nextSortOrder = if (pinned) {
            WorkspacePinnedFolderLogic.nextTopLevelSortOrder(current.pinnedFolders, current.tabs, target.spaceId)
        } else {
            0
        }
        val updatedTab = target.copy(
            isPinned = pinned,
            isUnloaded = false,
            pinnedUrl = if (pinned) (target.pinnedUrl?.takeIf { it.isNotBlank() } ?: target.url) else null,
            customTitle = if (pinned) baselineTitle else if (target.title.isNotBlank()) null else target.customTitle,
            folderId = null,
            sortOrder = nextSortOrder
        )
        _uiState.update { state ->
            val otherTabs = state.tabs.filterNot { it.id == tabId }
            val reordered = if (pinned) {
                val spacePinned = otherTabs.filter {
                    !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == target.spaceId && it.isPinned
                }
                val insertAfterId = spacePinned.lastOrNull()?.id
                if (insertAfterId != null) {
                    val idx = otherTabs.indexOfFirst { it.id == insertAfterId } + 1
                    otherTabs.toMutableList().apply { add(idx, updatedTab) }
                } else {
                    listOf(updatedTab) + otherTabs
                }
            } else {
                otherTabs + updatedTab
            }
            val normalized = WorkspacePinnedFolderLogic.normalizeSpacePinnedOrders(
                state.pinnedFolders,
                reordered,
                target.spaceId
            )
            state.copy(pinnedFolders = normalized.folders, tabs = normalized.tabs)
        }
        persistSpacePinnedState(target.spaceId, includeRegularTabs = true, scheduleSwiftfoxPush = true)
        showSnackbar(if (pinned) "Pinned ${updatedTab.displayTitle}" else "Unpinned ${updatedTab.displayTitle}")
    }

    fun editTabTitleAndUrl(tabId: String, title: String, rawUrl: String) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val resolvedUrl = OmniboxEngine.resolveInput(rawUrl, _uiState.value.settings)
        val urlChanged = !resolvedUrl.equals(target.url, ignoreCase = false)
        val host = OmniboxEngine.extractHost(resolvedUrl)
        val cleanTitle = title.trim().ifBlank { host.ifBlank { target.title } }
        val updatedTab = target.copy(
            title = if (urlChanged) cleanTitle else target.title,
            customTitle = cleanTitle,
            url = resolvedUrl,
            pinnedUrl = if (target.isPinned) resolvedUrl else target.pinnedUrl
        )
        updateTab(tabId) { updatedTab }
        if (urlChanged && !target.isUnloaded) {
            pool.discardHibernatedState(tabId)
            val existingSession = pool.getExisting(tabId)
            if (existingSession != null && !existingSession.isDestroyed) {
                val effective = resolveHttpsOnlyForProgrammaticLoad(tabId, resolvedUrl, existingSession)
                existingSession.loadUrl(effective)
            }
        }
        if (!target.isPrivate) {
            persistTab(updatedTab, scheduleSwiftfoxPush = target.isPinned && !target.isBoundWorkspaceTab)
        }
        showSnackbar("Updated $cleanTitle")
    }

    fun setTabCustomIcon(tabId: String, customIcon: String?) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (target.isPrivate) return
        val oldIcon = target.customIcon
        val updatedTab = target.copy(customIcon = customIcon)
        updateTab(tabId) { updatedTab }
        persistTab(updatedTab, scheduleSwiftfoxPush = target.isPinned && !target.isBoundWorkspaceTab)
        if (oldIcon != customIcon) cleanupUnusedCustomImage(oldIcon)
    }

    fun reorderTabs(orderedIds: List<String>, pinnedSection: Boolean) {
        if (orderedIds.isEmpty()) return
        val state = _uiState.value
        val isPrivate = state.isPrivateView
        val activeSpace = state.activeSpaceId
        val targetSet = orderedIds.toSet()
        val byId = state.tabs.associateBy { it.id }
        val orderedSubset = orderedIds.mapNotNull { byId[it] }
        if (orderedSubset.isEmpty()) return

        var cursor = 0
        val reorderedTabs = state.tabs.map { tab ->
            val belongsToSection = if (isPrivate) {
                tab.isPrivate
            } else {
                !tab.isPrivate && !tab.isBoundWorkspaceTab && tab.spaceId == activeSpace && tab.isPinned == pinnedSection
            }
            if (belongsToSection && tab.id in targetSet && cursor < orderedSubset.size) {
                orderedSubset[cursor++]
            } else {
                tab
            }
        }
        if (!isPrivate && pinnedSection) {
            val spaceFolders = state.pinnedFolders.filter { it.spaceId == activeSpace }
            val validFolderIds = spaceFolders.mapTo(mutableSetOf()) { it.id }
            val orderedUngroupedIds = orderedSubset.filter { it.folderId == null || it.folderId !in validFolderIds }.map { it.id }
            var nextTabs = reorderedTabs
            // Assign updated per-folder sortOrders while keeping top-level folders interleaved safely
            for (folder in spaceFolders) {
                val childIds = orderedSubset.filter { it.folderId == folder.id }.map { it.id }
                if (childIds.size > 1) {
                    nextTabs = WorkspacePinnedFolderLogic.applyReorderFolderPinnedTabs(
                        tabs = nextTabs,
                        spaceId = activeSpace,
                        folderId = folder.id,
                        orderedTabIds = childIds
                    ).first
                }
            }
            val currentTop = WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(state.pinnedFolders, nextTabs, activeSpace)
            var ungroupedIdx = 0
            val newTopKeys = currentTop.map { entry ->
                when (entry) {
                    is SidebarPinnedEntry.FolderGroup -> entry.entryKey
                    is SidebarPinnedEntry.StandalonePin -> {
                        val id = orderedUngroupedIds.getOrNull(ungroupedIdx++) ?: entry.tab.id
                        SidebarPinnedEntry.tabEntryKey(id)
                    }
                }
            }
            val normalized = WorkspacePinnedFolderLogic.applyReorderTopLevelPinned(
                folders = state.pinnedFolders,
                tabs = nextTabs,
                spaceId = activeSpace,
                orderedEntryKeys = newTopKeys
            )
            _uiState.update { it.copy(pinnedFolders = normalized.folders, tabs = normalized.tabs) }
            persistSpacePinnedState(activeSpace, includeRegularTabs = false, scheduleSwiftfoxPush = true)
        } else {
            _uiState.update { it.copy(tabs = reorderedTabs) }
            if (!isPrivate) {
                persistTabsForSpace(activeSpace, scheduleSwiftfoxPush = false)
            }
        }
    }

    fun createPinnedFolder(
        name: String,
        initialTabId: String? = null,
        spaceId: String = _uiState.value.activeSpaceId
    ) {
        val state = _uiState.value
        if (state.isPrivateView || state.spaces.none { it.id == spaceId }) return
        val created = WorkspacePinnedFolderLogic.applyCreatePinnedFolder(
            folders = state.pinnedFolders,
            tabs = state.tabs,
            spaceId = spaceId,
            folderName = name,
            initialTabId = initialTabId
        ) ?: return
        val (newFolder, normalized) = created
        pendingCreatedPinnedFolders[newFolder.id] = newFolder
        _uiState.update { current ->
            current.copy(
                pinnedFolders = normalized.folders,
                tabs = normalized.tabs
            )
        }
        persistSpacePinnedState(spaceId, includeRegularTabs = false, scheduleSwiftfoxPush = true)
        showSnackbar("Created folder \"${newFolder.name}\"")
    }

    fun togglePinnedFolderExpanded(folderId: String) {
        val existing = _uiState.value.pinnedFolders.firstOrNull { it.id == folderId } ?: return
        val updated = existing.copy(isExpanded = !existing.isExpanded)
        _uiState.update { state ->
            state.copy(pinnedFolders = state.pinnedFolders.map { if (it.id == folderId) updated else it })
        }
        viewModelScope.launch {
            db.pinnedFolderDao().upsert(updated)
        }
    }

    fun renamePinnedFolder(folderId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val existing = _uiState.value.pinnedFolders.firstOrNull { it.id == folderId } ?: return
        val updated = existing.copy(name = trimmed)
        _uiState.update { state ->
            state.copy(pinnedFolders = state.pinnedFolders.map { if (it.id == folderId) updated else it })
        }
        viewModelScope.launch {
            db.pinnedFolderDao().upsert(updated)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        showSnackbar("Renamed folder to \"$trimmed\"")
    }

    fun setPinnedFolderCustomIcon(folderId: String, customIcon: String?) {
        val existing = _uiState.value.pinnedFolders.firstOrNull { it.id == folderId } ?: return
        val oldIcon = existing.customIcon
        val nextIcon = CustomIconSpec.parse(customIcon).encodeExplicit()
        val updated = existing.copy(customIcon = nextIcon)
        _uiState.update { state ->
            state.copy(pinnedFolders = state.pinnedFolders.map { if (it.id == folderId) updated else it })
        }
        viewModelScope.launch {
            db.pinnedFolderDao().upsert(updated)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        if (oldIcon != nextIcon) cleanupUnusedCustomImage(oldIcon)
    }

    fun movePinnedTabToFolder(tabId: String, folderId: String?) {
        val state = _uiState.value
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        val normalized = WorkspacePinnedFolderLogic.applyMovePinnedTabToFolder(
            folders = state.pinnedFolders,
            tabs = state.tabs,
            tabId = tabId,
            targetFolderId = folderId
        ) ?: return
        _uiState.update { current ->
            current.copy(pinnedFolders = normalized.folders, tabs = normalized.tabs)
        }
        persistSpacePinnedState(tab.spaceId, includeRegularTabs = false, scheduleSwiftfoxPush = true)
        val folderName = folderId?.let { id -> normalized.folders.firstOrNull { it.id == id }?.name }
        showSnackbar(
            if (folderName != null) "Moved ${tab.displayTitle} to $folderName"
            else "Removed ${tab.displayTitle} from folder"
        )
    }

    fun reorderTopLevelPinned(orderedKeys: List<String>) {
        if (orderedKeys.isEmpty()) return
        val state = _uiState.value
        if (state.isPrivateView) return
        val spaceId = state.activeSpaceId
        val normalized = WorkspacePinnedFolderLogic.applyReorderTopLevelPinned(
            folders = state.pinnedFolders,
            tabs = state.tabs,
            spaceId = spaceId,
            orderedEntryKeys = orderedKeys
        )
        _uiState.update { it.copy(pinnedFolders = normalized.folders, tabs = normalized.tabs) }
        persistSpacePinnedState(spaceId, includeRegularTabs = false, scheduleSwiftfoxPush = true)
    }

    fun reorderFolderPinnedTabs(folderId: String, orderedTabIds: List<String>) {
        if (orderedTabIds.isEmpty()) return
        val state = _uiState.value
        if (state.isPrivateView) return
        val folder = state.pinnedFolders.firstOrNull { it.id == folderId } ?: return
        val (nextTabs, _) = WorkspacePinnedFolderLogic.applyReorderFolderPinnedTabs(
            tabs = state.tabs,
            spaceId = folder.spaceId,
            folderId = folderId,
            orderedTabIds = orderedTabIds
        )
        _uiState.update { it.copy(tabs = nextTabs) }
        persistSpacePinnedState(folder.spaceId, includeRegularTabs = false, scheduleSwiftfoxPush = true)
    }

    fun deletePinnedFolder(folderId: String, deleteContainedTabs: Boolean) {
        val state = _uiState.value
        val targetFolder = state.pinnedFolders.firstOrNull { it.id == folderId } ?: return
        val spaceId = targetFolder.spaceId
        val result = WorkspacePinnedFolderLogic.applyDeletePinnedFolder(
            folders = state.pinnedFolders,
            tabs = state.tabs,
            folderId = folderId,
            deleteContainedTabs = deleteContainedTabs
        ) ?: return

        pendingCreatedPinnedFolders.remove(folderId)
        val removedIds = result.removedTabs.mapTo(mutableSetOf()) { it.id }
        val wasActiveRemoved = state.activeTabId in removedIds
        if (removedIds.isNotEmpty()) {
            result.removedTabs.forEach { tab ->
                releaseTabSessionResources(tab.id, wasActive = tab.id == state.activeTabId)
            }
            _thumbnails.update { current -> current.copy(bitmaps = current.bitmaps - removedIds) }
            if (activeMediaTabId in removedIds || result.removedTabs.any { it.isPlayingMedia }) {
                handoffOrStopMediaService(
                    activeMediaTabId ?: "",
                    candidateTabs = result.updatedTabs.filter { !it.isUnloaded }
                )
            }
            if (wasActiveRemoved || state.permissionPrompt?.tabId in removedIds) {
                state.permissionPrompt?.let(::denyPromptSilently)
            }
        }

        val nextActiveId = if (wasActiveRemoved) {
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = result.updatedTabs,
                activeSpaceId = spaceId,
                isPrivateView = state.isPrivateView
            )
        } else {
            state.activeTabId
        }

        _uiState.update { current ->
            current.copy(
                pinnedFolders = result.updatedFolders,
                tabs = result.updatedTabs,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - removedIds,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId in removedIds) null else current.pendingPinCloseTarget,
                zapper = if (wasActiveRemoved) ZapperUiState() else current.zapper,
                readerArticle = if (wasActiveRemoved) null else current.readerArticle,
                isReaderLoading = if (wasActiveRemoved) false else current.isReaderLoading,
                findInPage = if (wasActiveRemoved) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in removedIds || wasActiveRemoved) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in removedIds || wasActiveRemoved) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in removedIds || wasActiveRemoved) null else current.permissionPrompt
            )
        }

        val removedList = result.removedTabs.map { it.id }
        val foldersToUpsert = result.updatedFolders.filter { it.spaceId == spaceId }
        val tabsToUpsert = result.updatedTabs
            .filter { !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab && it.spaceId == spaceId }
            .map { tab ->
                toTabEntity(
                    tab,
                    WorkspacePinnedFolderLogic.computeLocalPinnedOrderIndex(result.updatedFolders, result.updatedTabs, tab)
                )
            }
        viewModelScope.launch {
            tabDbMutex.withLock {
                if (removedList.isNotEmpty()) {
                    db.tabDao().deleteByIds(removedList)
                }
                if (tabsToUpsert.isNotEmpty()) {
                    db.tabDao().upsertAll(tabsToUpsert)
                }
                if (foldersToUpsert.isNotEmpty()) {
                    db.pinnedFolderDao().upsertAll(foldersToUpsert)
                }
                db.pinnedFolderDao().deleteById(folderId)
            }
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }

        cleanupUnusedCustomImage(targetFolder.customIcon)
        result.removedTabs.mapNotNull { it.customIcon }.forEach(::cleanupUnusedCustomImage)

        if (wasActiveRemoved) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
                syncActiveTabProgressAndBlocked(nextActiveId)
                applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
            } else if (!state.isPrivateView) {
                createTab(
                    spaceId = spaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
        showSnackbar(
            if (deleteContainedTabs && removedIds.isNotEmpty()) {
                "Deleted \"${targetFolder.name}\" and ${removedIds.size} pinned ${if (removedIds.size == 1) "tab" else "tabs"}"
            } else {
                "Deleted folder \"${targetFolder.name}\""
            }
        )
    }

    fun toggleZenFolderExpanded(spaceId: String, folderName: String) {
        val key = BrowserUiState.zenFolderKey(spaceId, folderName)
        _uiState.update { state ->
            val next = if (key in state.collapsedZenFolders) {
                state.collapsedZenFolders - key
            } else {
                state.collapsedZenFolders + key
            }
            state.copy(collapsedZenFolders = next)
        }
    }

    fun moveTabToSpace(tabId: String, targetSpaceId: String) {
        val state = _uiState.value
        val sourceTab = state.tabs.firstOrNull { it.id == tabId } ?: return
        val result = WorkspacePinnedFolderLogic.applyMoveTabToSpace(state, tabId, targetSpaceId) ?: return
        val destSpaceName = state.spaces.firstOrNull { it.id == targetSpaceId }?.name ?: "Space"
        val sourceSpaceId = sourceTab.spaceId

        _uiState.update { current ->
            current.copy(
                pinnedFolders = result.updatedFolders,
                tabs = result.updatedTabs,
                activeSpaceId = result.nextActiveSpaceId,
                activeTabId = result.nextActiveTabId
            )
        }

        if (sourceTab.isPinned) {
            persistSpacePinnedState(sourceSpaceId, includeRegularTabs = false, scheduleSwiftfoxPush = false)
            persistSpacePinnedState(targetSpaceId, includeRegularTabs = false, scheduleSwiftfoxPush = true)
        } else {
            persistTabsForSpace(sourceSpaceId, scheduleSwiftfoxPush = false)
            persistTabsForSpace(targetSpaceId, scheduleSwiftfoxPush = false)
        }
        showSnackbar("Moved ${result.movedTab.displayTitle} to $destSpaceName")
    }

    fun moveZenPinnedToSpace(zenPinnedId: String, targetSpaceId: String) {
        val state = _uiState.value
        val zenItem = state.zenPinnedItems.firstOrNull { it.id == zenPinnedId } ?: return
        val result = WorkspacePinnedFolderLogic.applyMoveZenPinnedToSpace(state, zenPinnedId, targetSpaceId) ?: return
        val destSpaceName = state.spaces.firstOrNull { it.id == targetSpaceId }?.name ?: "Space"

        // Update _uiState synchronously first so clearing zenPinnedId happens before Room observeAll() fires
        _uiState.update { current ->
            current.copy(
                zenPinnedItems = result.updatedZenItems,
                tabs = result.updatedTabs,
                activeSpaceId = result.nextActiveSpaceId,
                activeTabId = result.nextActiveTabId
            )
        }

        val persistedEntity = toTabEntity(
            result.createdOrUpdatedLocalTab,
            WorkspacePinnedFolderLogic.computeLocalPinnedOrderIndex(
                state.pinnedFolders,
                result.updatedTabs,
                result.createdOrUpdatedLocalTab
            )
        )
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().upsert(persistedEntity)
                db.zenPinnedItemDao().deleteById(zenPinnedId)
            }
            app.mozillaSyncCoordinator.mirrorZenPinnedDeleted(zenItem)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
        showSnackbar("Moved ${zenItem.title} to $destSpaceName")
    }

    fun updateZenPinnedItem(id: String, title: String, rawUrl: String) {
        val existing = _uiState.value.zenPinnedItems.firstOrNull { it.id == id } ?: return
        val resolved = OmniboxEngine.resolveInput(rawUrl, _uiState.value.settings)
        val urlChanged = !resolved.equals(existing.url, ignoreCase = false)
        val host = OmniboxEngine.extractHost(resolved)
        val updated = existing.copy(
            title = title.trim().ifBlank { host.ifBlank { existing.title } },
            url = resolved
        )
        val boundTab = _uiState.value.tabForZenPinned(id)
        _uiState.update { state ->
            state.copy(
                zenPinnedItems = state.zenPinnedItems.map { if (it.id == id) updated else it },
                tabs = if (boundTab != null) {
                    state.tabs.map { tab ->
                        if (tab.id == boundTab.id) {
                            tab.copy(
                                customTitle = updated.title,
                                pinnedUrl = updated.url,
                                url = if (urlChanged) updated.url else tab.url,
                                title = if (urlChanged) updated.title else tab.title
                            )
                        } else {
                            tab
                        }
                    }
                } else {
                    state.tabs
                }
            )
        }
        if (boundTab != null) {
            if (urlChanged) {
                pool.discardHibernatedState(boundTab.id)
                val existingSession = pool.getExisting(boundTab.id)
                if (existingSession != null && !existingSession.isDestroyed) {
                    val effective = resolveHttpsOnlyForProgrammaticLoad(boundTab.id, updated.url, existingSession)
                    existingSession.loadUrl(effective)
                }
            }
            _uiState.value.tabs.firstOrNull { it.id == boundTab.id }?.let { persistTab(it) }
        }
        viewModelScope.launch {
            db.zenPinnedItemDao().upsert(updated)
            app.mozillaSyncCoordinator.mirrorZenPinnedUpdated(updated)
        }
        showSnackbar("Updated ${updated.title}")
    }

    fun setZenPinnedCustomIcon(id: String, customIcon: String?) {
        val existing = _uiState.value.zenPinnedItems.firstOrNull { it.id == id } ?: return
        val oldIcon = existing.customIcon
        val updated = existing.copy(customIcon = customIcon)
        val boundTab = _uiState.value.tabForZenPinned(id)
        _uiState.update { state ->
            state.copy(
                zenPinnedItems = state.zenPinnedItems.map { if (it.id == id) updated else it },
                tabs = if (boundTab != null) {
                    state.tabs.map { if (it.id == boundTab.id) it.copy(customIcon = customIcon) else it }
                } else {
                    state.tabs
                }
            )
        }
        if (boundTab != null) {
            _uiState.value.tabs.firstOrNull { it.id == boundTab.id }?.let { persistTab(it) }
        }
        viewModelScope.launch { db.zenPinnedItemDao().upsert(updated) }
        if (oldIcon != customIcon) cleanupUnusedCustomImage(oldIcon)
    }

    fun addZenPinnedToEssentials(id: String) {
        val item = _uiState.value.zenPinnedItems.firstOrNull { it.id == id } ?: return
        addEssential(item.title.take(24), item.url, item.customIcon)
    }

    fun deleteZenPinnedItem(id: String) {
        val existing = _uiState.value.zenPinnedItems.firstOrNull { it.id == id }
        val boundTab = _uiState.value.tabForZenPinned(id)
        if (boundTab != null) {
            closePinnedOrBoundTab(boundTab.id, showClosedSnackbar = false)
        }
        _uiState.update { state ->
            state.copy(zenPinnedItems = state.zenPinnedItems.filterNot { it.id == id })
        }
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().deleteByZenPinnedId(id)
            }
            db.zenPinnedItemDao().deleteById(id)
            if (existing != null) {
                app.mozillaSyncCoordinator.mirrorZenPinnedDeleted(existing)
            }
        }
        cleanupUnusedCustomImage(existing?.customIcon)
    }

    fun reorderZenPinnedItems(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val current = _uiState.value.zenPinnedItems
        val byId = current.associateBy { it.id }
        val targetSet = orderedIds.toSet()
        val orderedSubset = orderedIds.mapNotNull { byId[it] }
        if (orderedSubset.isEmpty()) return

        var cursor = 0
        val merged = current.map { item ->
            if (item.id in targetSet && cursor < orderedSubset.size) {
                orderedSubset[cursor++]
            } else {
                item
            }
        }.mapIndexed { idx, item -> item.copy(sortOrder = idx) }

        _uiState.update { it.copy(zenPinnedItems = merged) }
        val affectedByWorkspace = merged
            .filter { it.id in targetSet }
            .groupBy { it.zenWorkspaceUuid }
        viewModelScope.launch {
            db.zenPinnedItemDao().upsertAll(merged)
            affectedByWorkspace.forEach { (wsUuid, items) ->
                app.mozillaSyncCoordinator.mirrorZenPinnedReordered(wsUuid, items)
            }
        }
    }

    private fun cleanupUnusedCustomImage(oldSpec: String?) {
        if (oldSpec.isNullOrBlank() || !oldSpec.startsWith(CustomIconSpec.IMAGE_PREFIX)) {
            return
        }
        val state = _uiState.value
        val stillReferenced = state.spaces.any { it.iconKey == oldSpec } ||
            state.essentials.any { it.customIcon == oldSpec } ||
            state.pinnedFolders.any { it.customIcon == oldSpec } ||
            state.tabs.any { it.customIcon == oldSpec } ||
            state.zenPinnedItems.any { it.customIcon == oldSpec } ||
            state.webApps.any { it.customIcon == oldSpec } ||
            state.recentlyClosedTab?.customIcon == oldSpec ||
            state.recentlyAutoClearedTabs?.allCustomIcons?.contains(oldSpec) == true
        if (!stillReferenced) {
            app.faviconRepository.deleteCustomImageIfUnused(oldSpec)
        }
    }

    fun submitOmnibox(rawInput: String) {
        omniboxHolder.submit(
            rawInput = rawInput,
            isOmniboxStillFocused = { _uiState.value.isOmniboxFocused },
            onSelectTab = ::selectTab,
            onNavigateUrl = ::navigateActiveTab
        )
    }

    fun navigateActiveTab(url: String) {
        stopActiveZapperIfRunning()
        omniboxHolder.reset()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        val active = _uiState.value.activeTab ?: createTab(url = url, selectImmediately = true)
        val targetSpaceId = WorkspaceTabResolver.resolveNavigationTabSpaceId(
            activeTab = active,
            activeSpaceId = _uiState.value.activeSpaceId
        )
        loadErrorDiagJobByTab.remove(active.id)?.cancel()
        app.userscriptBridge.cancelCallsForTab(active.id)
        // Discard any hibernated WebView bundle so re-creating the session loads the new URL
        pool.discardHibernatedState(active.id)

        val existingSession = pool.getExisting(active.id)
        existingSession?.clearFindMatches()
        val effectiveUrl = resolveHttpsOnlyForProgrammaticLoad(active.id, url, existingSession)

        if (activeMediaTabId == active.id) {
            handoffOrStopMediaService(active.id)
        }

        val initialProgress = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) 0 else 10
        progressByTab[active.id] = initialProgress
        blockedCountByTab.remove(active.id)
        _activeTabProgress.value = initialProgress
        _activeTabBlockedCount.value = 0

        _uiState.update {
            it.copy(
                isOmniboxFocused = false,
                isFoldedSidebarOpen = false,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                findInPage = FindInPageState(),
                readerArticle = null,
                isReaderLoading = false,
                zapper = ZapperUiState(),
                externalAppPrompt = null,
                httpsErrorPrompt = null,
                permissionPrompt = null
            )
        }
        updateTab(active.id) {
            it.copy(
                spaceId = targetSpaceId,
                url = effectiveUrl,
                title = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) {
                    "New Tab"
                } else {
                    OmniboxEngine.extractHost(effectiveUrl).ifBlank { effectiveUrl }
                },
                isLoading = effectiveUrl != OmniboxEngine.NEW_TAB_URL,
                progress = initialProgress,
                isPlayingMedia = false,
                isPlayingVideo = false,
                isReaderAvailable = false,
                darkReaderSkippedNative = false,
                loadError = null
            )
        }
        if (!active.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == active.id }?.let { persistTab(it) }
        }
        existingSession?.let { session ->
            val currentTab = _uiState.value.tabs.firstOrNull { it.id == active.id } ?: active
            applySettingsToSession(
                currentTab,
                session,
                reloadIfModeChanged = false
            )
            session.loadUrl(effectiveUrl)
        }
    }

    private fun resolveHttpsOnlyForProgrammaticLoad(
        tabId: String,
        rawUrl: String,
        session: BrowserSession?
    ): String {
        if (!rawUrl.startsWith("http://", ignoreCase = true)) return rawUrl
        if (!_uiState.value.settings.httpsOnlyMode) return rawUrl
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return rawUrl
        val rawHost = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val canonicalHost = OmniboxEngine.normalizeHost(rawHost)
        val tabIsPrivate = _uiState.value.tabs.firstOrNull { it.id == tabId }?.isPrivate
            ?: session?.isPrivate
            ?: _uiState.value.isPrivateView
        if (rawHost.isBlank() ||
            OmniboxEngine.isLocalHostOrLan(rawHost) ||
            effectiveSiteOverride(canonicalHost, tabIsPrivate)?.httpsOnlyBypass == true
        ) {
            return rawUrl
        }
        val upgraded = uri.buildUpon().scheme("https").build().toString()
        if (session != null) {
            session.webViewClient.registerProgrammaticHttpsUpgrade(upgraded, rawUrl)
        } else {
            pendingProgrammaticHttpsUpgrades[tabId] = upgraded to rawUrl
        }
        return upgraded
    }

    fun openExternalIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val launchedWebAppId = intent.getStringExtra(WebAppShortcutManager.EXTRA_WEB_APP_ID)
            ?.takeIf { it.isNotBlank() }
        if (launchedWebAppId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                app.webAppRepository.markLaunched(launchedWebAppId)
            }
        }
        val targetUrl = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.dataString ?: return
                if (uri.startsWith("http://", ignoreCase = true) ||
                    uri.startsWith("https://", ignoreCase = true)
                ) {
                    uri
                } else {
                    return
                }
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra("query").orEmpty()
                if (query.isNotBlank()) {
                    OmniboxEngine.resolveInput(query, _uiState.value.settings)
                } else {
                    return
                }
            }
            else -> return
        }

        val enqueueOnly = synchronized(incomingIntentLock) {
            if (!initialTabsLoaded) {
                pendingIncomingUrls += targetUrl
                true
            } else {
                false
            }
        }
        if (!enqueueOnly) {
            createTab(
                url = targetUrl,
                isPrivate = false,
                selectImmediately = true,
                dismissAllOverlays = true
            )
        }
    }

    fun focusOmnibox(
        prefillCurrentUrl: Boolean = true,
        searchScope: OmniboxSearchScope? = null
    ) {
        stopActiveZapperIfRunning()
        val snapshot = _uiState.value
        val current = snapshot.activeTab
        val isPrivateContext = snapshot.isPrivateView || current?.isPrivate == true
        val initialText = if (searchScope == null && prefillCurrentUrl && current != null && !current.isStartPage) {
            current.url
        } else {
            ""
        }
        val effectiveScope = if (isPrivateContext && searchScope == OmniboxSearchScope.History) {
            null
        } else {
            searchScope
        }
        _uiState.update {
            it.copy(
                isOmniboxFocused = true,
                isFoldedSidebarOpen = false,
                isSiteRulesOpen = false
            )
        }
        omniboxHolder.onFocused(initialText = initialText, effectiveScope = effectiveScope)
    }

    fun setOmniboxSearchScope(scope: OmniboxSearchScope?) {
        val snapshot = _uiState.value
        val isPrivateContext = snapshot.isPrivateView || snapshot.activeTab?.isPrivate == true
        val effectiveScope = if (isPrivateContext && scope == OmniboxSearchScope.History) {
            null
        } else {
            scope
        }
        omniboxHolder.setSearchScope(effectiveScope)
    }

    fun openSearchSettings() {
        omniboxHolder.reset()
        stopActiveZapperIfRunning()
        syncActiveTabProgressAndBlocked()
        _uiState.update { current ->
            current.copy(
                activeOverlay = BrowserOverlay.SETTINGS,
                activeSettingsCategory = SettingsCategory.SEARCH_GESTURES,
                overlayBackStack = emptyList(),
                isFoldedSidebarOpen = false,
                isSiteRulesOpen = false,
                isOmniboxFocused = false
            )
        }
    }

    fun dismissOmnibox() {
        omniboxHolder.reset()
        _uiState.update {
            it.copy(isOmniboxFocused = false)
        }
    }

    fun onOmniboxQueryChanged(query: String) {
        omniboxHolder.onQueryChanged(query)
    }

    fun goBack(): Boolean {
        val active = _uiState.value.activeTab ?: return false
        val session = pool.getExisting(active.id) ?: return false
        if (session.canGoBack()) {
            session.goBack()
            return true
        }
        return false
    }

    fun goForward() {
        val active = _uiState.value.activeTab ?: return
        pool.getExisting(active.id)?.goForward()
    }

    fun reloadOrStop() {
        val active = _uiState.value.activeTab ?: return
        val session = pool.getExisting(active.id) ?: return
        if (active.isLoading) {
            session.stopLoading()
            progressByTab[active.id] = 0
            _activeTabProgress.value = 0
            updateTab(active.id) { it.copy(isLoading = false, progress = 0) }
        } else {
            loadErrorDiagJobByTab.remove(active.id)?.cancel()
            updateTab(active.id) { it.copy(loadError = null) }
            session.reload()
        }
    }

    fun reloadActiveTab() {
        val active = _uiState.value.activeTab ?: return
        loadErrorDiagJobByTab.remove(active.id)?.cancel()
        updateTab(active.id) { it.copy(loadError = null) }
        pool.getExisting(active.id)?.reload()
    }

    fun dismissActiveLoadError() {
        val active = _uiState.value.activeTab ?: return
        loadErrorDiagJobByTab.remove(active.id)?.cancel()
        updateTab(active.id) { it.copy(loadError = null) }
    }

    fun saveContextMenuImage(imageUrl: String) {
        val active = _uiState.value.activeTab ?: return
        val session = pool.getExisting(active.id)
        val profileName = session?.profileName ?: BrowserSession.DEFAULT_PROFILE_NAME
        val ua = session?.userAgentString ?: ""
        when {
            imageUrl.startsWith("data:", ignoreCase = true) -> {
                val meta = if (imageUrl.length >= 5) imageUrl.substring(5).substringBefore(",") else ""
                val mime = meta.substringBefore(";").trim().ifBlank { "image/png" }
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "png"
                val fileName = "swiftfox_image_${System.currentTimeMillis()}.$ext"
                sessionCallbacks.onBlobDownloadReady(active.id, fileName, mime, imageUrl, imageUrl, active.isPrivate)
            }
            imageUrl.startsWith("blob:", ignoreCase = true) -> {
                val fileName = "swiftfox_image_${System.currentTimeMillis()}.png"
                session?.triggerBlobDownload(imageUrl, fileName, "image/png")
            }
            else -> {
                val ext = MimeTypeMap.getFileExtensionFromUrl(imageUrl)?.lowercase(Locale.ROOT).orEmpty()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
                val guessed = URLUtil.guessFileName(imageUrl, null, mime)
                sessionCallbacks.onDownloadRequested(
                    tabId = active.id,
                    url = imageUrl,
                    userAgent = ua,
                    contentDisposition = "attachment; filename=\"$guessed\"",
                    mimeType = mime,
                    profileName = profileName,
                    isPrivate = active.isPrivate
                )
            }
        }
    }

    fun setFoldedSidebarOpen(open: Boolean) {
        _uiState.update { it.copy(isFoldedSidebarOpen = open) }
    }

    fun toggleWorkspaceSidebar() {
        val current = _uiState.value
        if (current.windowState.posture == DevicePosture.FOLDED) {
            _uiState.update { it.copy(isFoldedSidebarOpen = !it.isFoldedSidebarOpen) }
        } else {
            if (!current.activeProfile.showSpacesSidebar) {
                setUnfoldedSpacesSidebar(true)
                _uiState.update { it.copy(isUnfoldedRailExpanded = true) }
            } else {
                _uiState.update { it.copy(isUnfoldedRailExpanded = !it.isUnfoldedRailExpanded) }
            }
        }
    }

    fun toggleSiteRulesSheet(open: Boolean) {
        _uiState.update {
            it.copy(
                isSiteRulesOpen = open,
                isFoldedSidebarOpen = if (open) false else it.isFoldedSidebarOpen,
                activeOverlay = if (open) BrowserOverlay.NONE else it.activeOverlay,
                overlayBackStack = if (open) emptyList() else it.overlayBackStack
            )
        }
    }

    fun setOverlay(overlay: BrowserOverlay) {
        omniboxHolder.cancelJobsAndClearSuggestions()
        if (overlay != BrowserOverlay.NONE) {
            stopActiveZapperIfRunning()
        }
        if (overlay == BrowserOverlay.SETTINGS) {
            syncActiveTabProgressAndBlocked()
        }
        if (overlay == BrowserOverlay.TAB_SWITCHER) {
            val snap = _uiState.value
            snap.activeTabId?.let { captureThumbnailFor(it) }
            val currentThumbs = _thumbnails.value.bitmaps
            snap.tabSwitcherTabs.forEach { tab ->
                if (!tab.isUnloaded && !tab.isStartPage && tab.id != snap.activeTabId && tab.id !in currentThumbs) {
                    captureThumbnailFor(tab.id)
                }
            }
        }
        if (overlay == BrowserOverlay.DOWNLOADS) {
            viewModelScope.launch { app.downloadCoordinator.refreshAllStatuses() }
        }
        _uiState.update { current ->
            val nextStack = when {
                overlay == BrowserOverlay.NONE -> emptyList()
                current.activeOverlay == overlay -> current.overlayBackStack
                current.activeOverlay == BrowserOverlay.SETTINGS &&
                    overlay in setOf(
                        BrowserOverlay.FILTER_LISTS,
                        BrowserOverlay.USERSCRIPTS,
                        BrowserOverlay.ZAPPED_RULES,
                        BrowserOverlay.SYNC,
                        BrowserOverlay.SYNCED_TABS,
                        BrowserOverlay.BOOKMARKS,
                        BrowserOverlay.HISTORY,
                        BrowserOverlay.DOWNLOADS,
                        BrowserOverlay.WEB_APPS
                    ) -> current.overlayBackStack + BrowserOverlay.SETTINGS
                current.activeOverlay == BrowserOverlay.SYNC &&
                    overlay == BrowserOverlay.SYNCED_TABS ->
                    current.overlayBackStack + BrowserOverlay.SYNC
                current.activeOverlay == BrowserOverlay.TAB_SWITCHER &&
                    overlay == BrowserOverlay.SYNCED_TABS ->
                    current.overlayBackStack + BrowserOverlay.TAB_SWITCHER
                else -> emptyList()
            }
            val preserveSettingsCategory = when {
                overlay == BrowserOverlay.SETTINGS && current.activeOverlay == BrowserOverlay.SETTINGS -> true
                current.activeOverlay == BrowserOverlay.SETTINGS &&
                    overlay in setOf(
                        BrowserOverlay.FILTER_LISTS,
                        BrowserOverlay.USERSCRIPTS,
                        BrowserOverlay.ZAPPED_RULES,
                        BrowserOverlay.SYNC,
                        BrowserOverlay.SYNCED_TABS,
                        BrowserOverlay.BOOKMARKS,
                        BrowserOverlay.HISTORY,
                        BrowserOverlay.DOWNLOADS,
                        BrowserOverlay.WEB_APPS
                    ) -> true
                else -> false
            }
            current.copy(
                activeOverlay = overlay,
                activeSettingsCategory = if (preserveSettingsCategory) current.activeSettingsCategory else null,
                overlayBackStack = nextStack,
                isFoldedSidebarOpen = if (overlay != BrowserOverlay.NONE) false else current.isFoldedSidebarOpen,
                isSiteRulesOpen = false,
                isOmniboxFocused = false
            )
        }
    }

    fun setSettingsCategory(category: SettingsCategory?) {
        _uiState.update { it.copy(activeSettingsCategory = category) }
    }

    fun navigateBackFromOverlay() {
        _uiState.update { current ->
            if (current.activeOverlay == BrowserOverlay.SETTINGS && current.activeSettingsCategory != null) {
                return@update current.copy(activeSettingsCategory = null)
            }
            val prev = current.overlayBackStack.lastOrNull() ?: BrowserOverlay.NONE
            val remaining = if (current.overlayBackStack.isNotEmpty()) {
                current.overlayBackStack.dropLast(1)
            } else {
                emptyList()
            }
            current.copy(
                activeOverlay = prev,
                activeSettingsCategory = if (prev == BrowserOverlay.SETTINGS) current.activeSettingsCategory else null,
                overlayBackStack = remaining
            )
        }
    }

    fun openDownload(record: DownloadRecordEntity) {
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) {
                app.downloadCoordinator.createOpenIntent(record)
            }
            if (intent == null) {
                showSnackbar("File not found in Downloads")
                return@launch
            }
            val launched = onLaunchExternalIntentBridge?.invoke(intent) ?: false
            if (!launched) {
                showSnackbar("No app found to open ${record.fileName}")
            }
        }
    }

    fun deleteDownloadRecord(record: DownloadRecordEntity) {
        viewModelScope.launch {
            app.downloadCoordinator.deleteRecord(record)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            app.downloadCoordinator.clearAllRecords()
            showSnackbar("Cleared download history")
        }
    }

    fun openInstallWebAppSheet() {
        val state = _uiState.value
        val active = state.activeTab ?: return
        val isHttpUrl = active.url.startsWith("http://", ignoreCase = true) ||
            active.url.startsWith("https://", ignoreCase = true)
        if (active.isPrivate || active.isStartPage || !isHttpUrl) {
            showSnackbar(
                if (active.isPrivate) "Switch out of Private mode to install web apps"
                else "Open a webpage first"
            )
            return
        }
        val existingCandidate = state.pwaCandidatesByTab[active.id]
        if (existingCandidate != null) {
            val matchedApp = state.webApps.firstOrNull {
                it.startUrl == existingCandidate.startUrl || it.startUrl == active.url
            }
            _uiState.update {
                it.copy(
                    installSheetCandidate = existingCandidate.copy(
                        existingInstalledAppId = matchedApp?.id ?: existingCandidate.existingInstalledAppId
                    ),
                    activeOverlay = BrowserOverlay.NONE,
                    overlayBackStack = emptyList(),
                    isSiteRulesOpen = false,
                    isFoldedSidebarOpen = false
                )
            }
            return
        }
        val fallbackName = active.title.trim()
            .takeUnless { it.isBlank() || it.equals("New Tab", ignoreCase = true) }
            ?: active.displayHost.ifBlank { "Web App" }
        val existingApp = state.webApps.firstOrNull { it.startUrl == active.url }
        val fallback = PageInstallCandidate(
            pageUrl = active.url,
            pageTitle = active.title,
            suggestedName = fallbackName,
            suggestedShortName = fallbackName,
            startUrl = active.url,
            scopeUrl = WebAppManifestParser.defaultScopeForStartUrl(active.url),
            existingInstalledAppId = existingApp?.id
        )
        _uiState.update {
            it.copy(
                installSheetCandidate = fallback,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                isSiteRulesOpen = false,
                isFoldedSidebarOpen = false
            )
        }
    }

    fun dismissInstallWebAppSheet(installed: Boolean = false) {
        val activeId = _uiState.value.activeTabId
        _uiState.update { it.copy(installSheetCandidate = null) }
        if (activeId != null) {
            pool.getExisting(activeId)?.evaluateJavascript(
                WebAppDetector.buildResolveBeforeInstallChoiceScript(installed)
            )
        }
    }

    fun confirmInstallWebApp(
        candidate: PageInstallCandidate,
        name: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean = false,
        customIconSpec: String?
    ) {
        val activeId = _uiState.value.activeTabId
        _uiState.update { it.copy(installSheetCandidate = null) }
        if (activeId != null) {
            pool.getExisting(activeId)?.evaluateJavascript(
                WebAppDetector.buildResolveBeforeInstallChoiceScript(accepted = true)
            )
        }
        viewModelScope.launch {
            val result = app.webAppRepository.installOrUpdateFromCandidate(
                candidate = candidate,
                customName = name,
                openAsStandalone = openAsStandalone,
                showSlimHeader = showSlimHeader,
                drawBehindStatusBar = drawBehindStatusBar,
                customIconSpec = customIconSpec,
                pinToHomeScreen = true
            )
            result.onSuccess { (entity, pinned) ->
                showSnackbar(
                    if (pinned) "Adding ${entity.name} to Home screen"
                    else "Saved ${entity.name} in Web Apps"
                )
            }.onFailure { err ->
                showSnackbar("Could not install web app: ${err.message ?: "error"}")
            }
        }
    }

    fun launchWebApp(webApp: WebAppEntity) {
        viewModelScope.launch {
            app.webAppRepository.markLaunched(webApp.id)
        }
        if (webApp.openAsStandalone) {
            if (!WebAppShortcutManager.launchWebApp(app, webApp)) {
                showSnackbar("Could not launch ${webApp.name}")
            }
        } else {
            navigateActiveTab(webApp.startUrl)
        }
    }

    fun repinWebApp(webApp: WebAppEntity) {
        viewModelScope.launch {
            val ok = app.webAppRepository.repinWebApp(webApp)
            showSnackbar(
                if (ok) "Requested Home screen pin for ${webApp.name}"
                else "Home screen pinning is unsupported on this launcher"
            )
        }
    }

    fun updateWebApp(
        existing: WebAppEntity,
        name: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean = existing.drawBehindStatusBar,
        customIconSpec: String?
    ) {
        viewModelScope.launch {
            val updated = app.webAppRepository.updateExistingWebApp(
                existing = existing,
                name = name,
                openAsStandalone = openAsStandalone,
                showSlimHeader = showSlimHeader,
                drawBehindStatusBar = drawBehindStatusBar,
                customIconSpec = customIconSpec
            )
            showSnackbar("Updated ${updated.name}")
        }
    }

    fun removeWebApp(webApp: WebAppEntity) {
        viewModelScope.launch {
            app.webAppRepository.removeWebApp(webApp)
            showSnackbar("Removed ${webApp.name}")
        }
    }

    fun toggleUnfoldedRailExpanded() {
        _uiState.update { it.copy(isUnfoldedRailExpanded = !it.isUnfoldedRailExpanded) }
    }

    fun toggleActiveTabSiteMode() {
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) return
        val nextMode = if (active.resolvedSiteMode == SiteMode.DESKTOP) {
            SiteMode.MOBILE
        } else {
            SiteMode.DESKTOP
        }
        val host = OmniboxEngine.normalizeHost(active.displayHost)
        val siteOverride = SiteModeResolver.parseOrNull(
            effectiveSiteOverride(host, active.isPrivate)?.siteModeOverride
        )
        val defaultWithoutTabOverride = siteOverride ?: _uiState.value.activeProfile.defaultSiteMode
        // Clear tab override only when returning to the mode this tab would naturally use without one
        val nextTabOverride = if (nextMode == defaultWithoutTabOverride) null else nextMode
        updateTab(active.id) {
            it.copy(siteModeOverride = nextTabOverride, resolvedSiteMode = nextMode)
        }
        if (!active.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == active.id }?.let { persistTab(it) }
        }
        pool.getExisting(active.id)?.let { session ->
            val updated = _uiState.value.tabs.firstOrNull { it.id == active.id } ?: active
            applySettingsToSession(updated, session, reloadIfModeChanged = true)
        }
        val label = when {
            nextTabOverride == null && siteOverride == null -> "${nextMode.label} view (auto posture default)"
            nextTabOverride == null -> "${nextMode.label} view (site rule)"
            else -> "${nextMode.label} view for this tab"
        }
        showSnackbar(label)
    }

    private fun mutateSiteOverride(
        host: String,
        onUpdated: (SiteOverrideEntity) -> Unit = {},
        transform: (SiteOverrideEntity) -> SiteOverrideEntity
    ) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        val isPrivate = _uiState.value.activeTab?.isPrivate ?: _uiState.value.isPrivateView
        val current = effectiveSiteOverride(normalizedHost, isPrivate) ?: SiteOverrideEntity(host = normalizedHost)
        val optimistic = transform(current).copy(host = normalizedHost)
        val isDefaultOverride = optimistic.isDefault()
        if (isPrivate) {
            _uiState.update { state ->
                val nextPrivate = if (isDefaultOverride && normalizedHost !in state.siteOverrides) {
                    state.privateSiteOverrides - normalizedHost
                } else {
                    state.privateSiteOverrides + (normalizedHost to optimistic)
                }
                state.copy(privateSiteOverrides = nextPrivate)
            }
            onUpdated(optimistic)
            return
        }
        _uiState.update { state ->
            val nextOverrides = if (isDefaultOverride) {
                state.siteOverrides - normalizedHost
            } else {
                state.siteOverrides + (normalizedHost to optimistic)
            }
            state.copy(siteOverrides = nextOverrides)
        }
        onUpdated(optimistic)
        val writeGen = siteOverrideWrites.beginWrite()
        viewModelScope.launch {
            siteOverrideWrites.runSerializedWrite(
                generation = writeGen,
                writeToDb = {
                    val existingDb = db.siteOverrideDao().getByHost(normalizedHost) ?: SiteOverrideEntity(host = normalizedHost)
                    val saved = transform(existingDb).copy(host = normalizedHost)
                    if (saved.isDefault()) {
                        db.siteOverrideDao().deleteByHost(normalizedHost)
                    } else {
                        db.siteOverrideDao().upsert(saved)
                    }
                },
                readSettledFromDb = {
                    db.siteOverrideDao().getAll()
                        .associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                },
                onSettled = { settled ->
                    if (_uiState.value.siteOverrides != settled) {
                        _uiState.update { it.copy(siteOverrides = settled) }
                        refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                    }
                }
            )
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
        }
    }

    fun toggleActiveSiteAdblock() {
        val active = _uiState.value.activeTab
        val host = OmniboxEngine.normalizeHost(active?.displayHost)
        if (active == null || active.isStartPage || host.isBlank()) {
            val next = !_uiState.value.settings.adblockEnabled
            setAdblockEnabled(next)
            showSnackbar(if (next) "Ad blocking enabled" else "Ad blocking paused")
            return
        }
        if (!_uiState.value.settings.adblockEnabled) {
            showSnackbar("Enable global Ad Blocking in Settings first")
            return
        }
        val currentlyDisabled = effectiveSiteOverride(host, active.isPrivate)?.adblockDisabled == true
        setSiteAdblockDisabled(host, !currentlyDisabled)
    }

    // Per-site override mutations
    fun setSiteAdblockDisabled(host: String, disabled: Boolean) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        mutateSiteOverride(normalizedHost, onUpdated = {
            pool.clearAllDocumentStartScripts()
            reloadActiveTabIfHost(normalizedHost)
            showSnackbar(if (disabled) "Ad blocking paused on $normalizedHost" else "Ad blocking active on $normalizedHost")
        }) { it.copy(adblockDisabled = disabled) }
    }

    fun cycleActiveSiteWebDarkOverride() {
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) return
        val host = OmniboxEngine.extractHost(active.url)
        if (host.isBlank()) return
        val current = effectiveSiteOverride(host, active.isPrivate)?.webDarkModeOverride
        val next = DarkReaderConfig.nextSiteOverride(current)
        setSiteWebDarkOverride(host, next)
        val message = when (next) {
            true -> "Web Dark: Force dark on $host"
            false -> "Web Dark: Force light on $host"
            null -> "Web Dark: Auto on $host"
        }
        showSnackbar(message)
    }

    fun setSiteWebDarkOverride(host: String, override: Boolean?) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        mutateSiteOverride(normalizedHost, onUpdated = { updated ->
            val active = _uiState.value.activeTab
            syncGlobalPrepaintDarkScripts()
            pool.markAllBackgroundWebDarkDirty(active?.id)
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                val darkState = DarkReaderConfig.resolve(_uiState.value.settings, updated, isChromeDark())
                val (isDarkSurface, isAmoledSurface) = resolveWebSurfaceDarkColors(darkState)
                if (!darkState.enabled && active.darkReaderSkippedNative) {
                    updateTab(active.id) { it.copy(darkReaderSkippedNative = false) }
                }
                pool.getExisting(active.id)?.let { session ->
                    session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
                    session.applyWebDarkMode(darkState, app.darkReaderInjector)
                }
            }
        }) { it.copy(webDarkModeOverride = override) }
    }

    fun setSiteModeOverride(host: String, mode: SiteMode?) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        mutateSiteOverride(normalizedHost, onUpdated = {
            val active = _uiState.value.activeTab
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                updateTab(active.id) { it.copy(siteModeOverride = null) }
                if (!active.isPrivate) {
                    _uiState.value.tabs.firstOrNull { it.id == active.id }?.let { persistTab(it) }
                }
                applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
            }
        }) { it.copy(siteModeOverride = mode?.name) }
    }

    fun setSiteJavascriptEnabled(host: String, enabled: Boolean) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        mutateSiteOverride(normalizedHost, onUpdated = {
            val active = _uiState.value.activeTab
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
            }
            showSnackbar(if (enabled) "JavaScript enabled on $normalizedHost" else "JavaScript disabled on $normalizedHost")
        }) { it.copy(javascriptEnabled = enabled) }
    }

    fun setSiteCookiesEnabled(host: String, enabled: Boolean) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        mutateSiteOverride(normalizedHost, onUpdated = {
            val active = _uiState.value.activeTab
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
            }
            showSnackbar(if (enabled) "Cookies enabled on $normalizedHost" else "Cookies blocked on $normalizedHost")
        }) { it.copy(cookiesEnabled = enabled) }
    }

    fun setSiteHttpsBypass(host: String, bypass: Boolean) {
        mutateSiteOverride(host) { it.copy(httpsOnlyBypass = bypass) }
    }

    fun setSiteCustomUserAgent(host: String, userAgent: String?) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        if (normalizedHost.isBlank()) return
        val trimmed = userAgent?.trim()
        if (!trimmed.isNullOrEmpty() && trimmed.any { it !in '\u0020'..'\u007E' }) {
            showSnackbar("User-Agent must use standard printable ASCII characters")
            return
        }
        val normalizedUa = trimmed?.takeIf { it.isNotEmpty() }
        mutateSiteOverride(normalizedHost, onUpdated = {
            val active = _uiState.value.activeTab
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
            }
            showSnackbar(if (normalizedUa != null) "Custom User-Agent applied for $normalizedHost" else "Default User-Agent restored")
        }) { it.copy(customUserAgent = normalizedUa) }
    }

    private fun beginFilterUpdate() {
        val count = filterUpdateInFlight.incrementAndGet()
        if (count == 1) {
            _uiState.update { it.copy(isUpdatingFilterLists = true) }
        }
    }

    private fun endFilterUpdate() {
        val count = filterUpdateInFlight.decrementAndGet().coerceAtLeast(0)
        if (count == 0) {
            _uiState.update { it.copy(isUpdatingFilterLists = false) }
        }
    }

    // Filter lists actions
    fun updateAllFilterLists() {
        viewModelScope.launch {
            beginFilterUpdate()
            try {
                val updated = app.filterListRepository.updateAllEnabled()
                pool.clearAllDocumentStartScripts()
                showSnackbar("Synced $updated filter lists")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showSnackbar("Filter update failed: ${e.message ?: "error"}")
            } finally {
                endFilterUpdate()
            }
        }
    }

    fun toggleFilterList(id: String, enabled: Boolean) {
        viewModelScope.launch {
            beginFilterUpdate()
            try {
                val result = app.filterListRepository.toggleList(id, enabled)
                result.onFailure { err ->
                    showSnackbar("Filter list update failed: ${err.message ?: "error"}")
                }
                if (result.getOrNull() != null || result.isFailure) {
                    app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                }
                pool.clearAllDocumentStartScripts()
            } finally {
                endFilterUpdate()
            }
        }
    }

    fun addCustomFilterList(name: String, url: String) {
        viewModelScope.launch {
            beginFilterUpdate()
            try {
                val result = app.filterListRepository.addCustomList(name, url)
                result.onSuccess { entity ->
                    app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                    pool.clearAllDocumentStartScripts()
                    showSnackbar("Added ${entity.name} (${entity.ruleCount} rules)")
                }.onFailure { err ->
                    showSnackbar("Could not add list: ${err.message ?: "error"}")
                }
            } finally {
                endFilterUpdate()
            }
        }
    }

    fun deleteCustomFilterList(id: String) {
        viewModelScope.launch {
            app.filterListRepository.deleteCustomList(id)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            pool.clearAllDocumentStartScripts()
            showSnackbar("Removed filter list")
        }
    }

    // Element Zapper actions
    fun startElementZapper(accentHex: String = "#E86A17") {
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) {
            showSnackbar("Open a webpage to use Element Zapper")
            return
        }
        val siteOverride = effectiveSiteOverride(active.displayHost, active.isPrivate)
        if (siteOverride?.javascriptEnabled == false) {
            showSnackbar("Enable JavaScript for this site to use Element Zapper")
            return
        }
        val session = pool.getExisting(active.id) ?: return
        _uiState.update {
            it.copy(
                isFoldedSidebarOpen = false,
                isSiteRulesOpen = false,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                zapper = ZapperUiState(isActive = true)
            )
        }
        session.evaluateJavascript(
            app.zapperController.buildStartScript(
                accentHex = accentHex,
                bridgeToken = session.tokens.zapperToken,
                runtimeKey = session.tokens.zapperKey
            )
        )
    }

    fun stopElementZapper() {
        val activeId = _uiState.value.activeTabId
        if (activeId != null) {
            pool.getExisting(activeId)?.let { session ->
                session.evaluateJavascript(app.zapperController.buildStopScript(session.tokens.zapperKey))
            }
        }
        _uiState.update { it.copy(zapper = ZapperUiState()) }
    }

    fun adjustZapperScope(delta: Int) {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.let { session ->
            session.evaluateJavascript(app.zapperController.buildAdjustScopeScript(delta, session.tokens.zapperKey))
        }
    }

    fun setZapperScopeDepth(depth: Int) {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.let { session ->
            session.evaluateJavascript(app.zapperController.buildSetScopeScript(depth, session.tokens.zapperKey))
        }
    }

    fun setZapperPreview(enabled: Boolean) {
        val current = _uiState.value
        if (!current.zapper.isActive || current.zapper.selectedSelector == null) return
        val activeId = current.activeTabId ?: return
        _uiState.update { state ->
            state.copy(zapper = state.zapper.copy(isPreviewing = enabled))
        }
        pool.getExisting(activeId)?.let { session ->
            session.evaluateJavascript(
                app.zapperController.buildSetPreviewScript(enabled, session.tokens.zapperKey)
            )
        }
    }

    fun zapSelectedElementOnce() {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.let { session ->
            session.evaluateJavascript(app.zapperController.buildZapOnceScript(session.tokens.zapperKey))
        }
    }

    fun undoZapperTemporaryZap() {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.let { session ->
            session.evaluateJavascript(app.zapperController.buildUndoLastZapScript(session.tokens.zapperKey))
        }
    }

    fun blockSelectedElementPermanently() {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.let { session ->
            session.evaluateJavascript(app.zapperController.buildConfirmBlockPermanentScript(session.tokens.zapperKey))
        }
    }

    fun toggleZappedRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.zappedRules.firstOrNull { it.id == id } ?: return@launch
            db.zappedRuleDao().upsert(current.copy(enabled = enabled))
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            pool.clearAllDocumentStartScripts()
            refreshZappedCssForHost(current.host)
        }
    }

    fun deleteZappedRule(id: String) {
        val privateEntry = _uiState.value.privateZappedRules.entries
            .firstOrNull { (_, list) -> list.any { it.id == id } }
        if (privateEntry != null) {
            val host = privateEntry.key
            val remaining = privateEntry.value.filterNot { it.id == id }
            _uiState.update { state ->
                val updatedMap = if (remaining.isEmpty()) {
                    state.privateZappedRules - host
                } else {
                    state.privateZappedRules + (host to remaining)
                }
                state.copy(privateZappedRules = updatedMap)
            }
            pool.clearAllDocumentStartScripts()
            viewModelScope.launch { refreshZappedCssForHost(host, isPrivateOnly = true) }
            return
        }
        // Prevent Private Mode Site Rules sheet from silently deleting persistent normal-mode Room rules
        if (_uiState.value.activeOverlay == BrowserOverlay.NONE &&
            (_uiState.value.activeTab?.isPrivate ?: _uiState.value.isPrivateView)
        ) {
            showSnackbar("Manage saved normal-mode zaps from Settings or a normal tab")
            return
        }
        viewModelScope.launch {
            val host = _uiState.value.zappedRules.firstOrNull { it.id == id }?.host
            db.zappedRuleDao().deleteById(id)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            pool.clearAllDocumentStartScripts()
            if (host != null) {
                refreshZappedCssForHost(host)
            }
        }
    }

    fun clearZappedRulesForHost(host: String) {
        viewModelScope.launch {
            db.zappedRuleDao().deleteByHost(host)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            pool.clearAllDocumentStartScripts()
            refreshZappedCssForHost(host)
            showSnackbar("Cleared zapped rules for $host")
        }
    }

    private suspend fun refreshZappedCssForHost(host: String, isPrivateOnly: Boolean = false) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        val enabledRules = withContext(Dispatchers.IO) {
            db.zappedRuleDao().getEnabledForHost(normalizedHost)
        }
        val privateRules = _uiState.value.privateZappedRules[normalizedHost].orEmpty().filter { it.enabled }
        val normalScript = if (!isPrivateOnly) app.zapperController.buildApplyZappedCssScript(enabledRules) else ""
        val privateScript = app.zapperController.buildApplyZappedCssScript(enabledRules + privateRules)
        withContext(Dispatchers.Main) {
            _uiState.value.tabs
                .filter {
                    (!isPrivateOnly || it.isPrivate) &&
                        OmniboxEngine.normalizeHost(it.displayHost) == normalizedHost
                }
                .forEach { tab ->
                    pool.getExisting(tab.id)?.evaluateJavascript(
                        if (tab.isPrivate) privateScript else normalScript
                    )
                }
        }
    }

    // Userscripts actions
    fun toggleUserscript(id: String, enabled: Boolean) {
        viewModelScope.launch {
            app.userscriptRepository.setEnabled(id, enabled)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            pool.clearAllDocumentStartScripts()
        }
    }

    fun installUserscriptFromUrl(url: String) {
        viewModelScope.launch {
            val result = app.userscriptRepository.installFromUrl(url)
            result.onSuccess { script ->
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                pool.clearAllDocumentStartScripts()
                val unsupported = UserscriptParser.parse(script.code, script.name).unsupportedDirectives
                val suffix = if (unsupported.isNotEmpty()) {
                    " (note: ${unsupported.joinToString(", ")} not supported)"
                } else {
                    ""
                }
                showSnackbar("Installed userscript ${script.name}$suffix")
            }.onFailure { err ->
                showSnackbar("Userscript install failed: ${err.message ?: "error"}")
            }
        }
    }

    fun installUserscriptFromCode(code: String, fallbackName: String = "Custom Userscript") {
        viewModelScope.launch {
            val result = app.userscriptRepository.installFromCode(code, fallbackName)
            result.onSuccess { script ->
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                pool.clearAllDocumentStartScripts()
                val unsupported = UserscriptParser.parse(script.code, script.name).unsupportedDirectives
                val suffix = if (unsupported.isNotEmpty()) {
                    " (note: ${unsupported.joinToString(", ")} not supported)"
                } else {
                    ""
                }
                showSnackbar("Installed userscript ${script.name}$suffix")
            }.onFailure { err ->
                showSnackbar("Could not install userscript: ${err.message ?: "error"}")
            }
        }
    }

    fun saveUserscriptCode(existingId: String?, code: String) {
        viewModelScope.launch {
            val result = if (existingId != null) {
                app.userscriptRepository.updateCode(existingId, code)
            } else {
                app.userscriptRepository.installFromCode(code)
            }
            result.onSuccess { script ->
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
                pool.clearAllDocumentStartScripts()
                val unsupported = UserscriptParser.parse(script.code, script.name).unsupportedDirectives
                val suffix = if (unsupported.isNotEmpty()) {
                    " (note: ${unsupported.joinToString(", ")} not supported)"
                } else {
                    ""
                }
                showSnackbar("Saved ${script.name}$suffix")
            }.onFailure { err ->
                showSnackbar("Save failed: ${err.message ?: "error"}")
            }
        }
    }

    suspend fun loadUserscriptCode(id: String): String? = withContext(Dispatchers.IO) {
        app.userscriptRepository.getScriptById(id)?.code
    }

    fun deleteUserscript(id: String) {
        viewModelScope.launch {
            app.userscriptRepository.deleteScript(id)
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            synchronized(privateUserscriptStorageLock) {
                privateUserscriptStorage.remove(id)
                privateDeletedUserscriptKeys.remove(id)
            }
            pool.clearAllDocumentStartScripts()
            showSnackbar("Deleted userscript")
        }
    }

    fun triggerImportUserscriptFile() {
        onImportUserscriptFileBridge?.invoke()
    }

    // Reader Mode actions
    fun openReaderMode() {
        stopActiveZapperIfRunning()
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) {
            showSnackbar("Open an article to enter Reader Mode")
            return
        }
        val siteOverride = effectiveSiteOverride(active.displayHost, active.isPrivate)
        if (siteOverride?.javascriptEnabled == false) {
            showSnackbar("Enable JavaScript in Site Rules to use Reader Mode")
            return
        }
        val session = pool.getExisting(active.id) ?: return
        if (session.isDestroyed) return
        val expectedTabId = active.id
        val expectedUrl = active.url
        val expectedOrigin = ScriptInjector.extractOrigin(expectedUrl)
        val readerKey = session.tokens.readerKey
        val readerKeyLit = viewModelJson.encodeToString(readerKey)
        val requestId = readerRequestCounter.incrementAndGet()
        readerExtractJob?.cancel()
        readerExtractJob = null
        _uiState.update {
            it.copy(
                isFoldedSidebarOpen = false,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                isReaderLoading = true
            )
        }

        fun handleExtracted(rawResult: String?) {
            if (readerRequestCounter.get() != requestId || !_uiState.value.isReaderLoading) {
                return
            }
            readerExtractJob?.cancel()
            readerExtractJob = viewModelScope.launch(Dispatchers.Default) {
                val article = app.readerExtractor.parseExtractionResult(rawResult)
                withContext(Dispatchers.Main) {
                    if (readerRequestCounter.get() != requestId) return@withContext
                    val liveActive = _uiState.value.activeTab
                    if (liveActive == null || liveActive.id != expectedTabId || liveActive.url != expectedUrl) {
                        _uiState.update { it.copy(isReaderLoading = false) }
                        return@withContext
                    }
                    _uiState.update { state ->
                        if (article != null) {
                            state.copy(readerArticle = article, isReaderLoading = false)
                        } else {
                            state.copy(
                                isReaderLoading = false,
                                snackbarMessage = "Reader view unavailable for this page",
                                snackbarId = snackbarCounter.incrementAndGet(),
                                recentlyClosedTab = null,
                                recentlyAutoClearedTabs = null
                            )
                        }
                    }
                }
            }
        }

        // Fast-path when Readability + bridge are already attached under session runtimeKey
        session.evaluateJavascript(
            "Object.prototype.hasOwnProperty.call(window, $readerKeyLit)"
        ) { loaded ->
            if (readerRequestCounter.get() != requestId) return@evaluateJavascript
            val nowOrigin = ScriptInjector.extractOrigin(session.webView.url.orEmpty())
            if (session.isDestroyed || (expectedOrigin != null && nowOrigin != null && nowOrigin != expectedOrigin)) {
                _uiState.update { it.copy(isReaderLoading = false) }
                return@evaluateJavascript
            }
            val script = if (loaded?.trim().equals("true", ignoreCase = true)) {
                app.readerExtractor.buildFastExtractScript(readerKey)
            } else {
                app.readerExtractor.buildExtractScript(readerKey)
            }
            if (session.isDestroyed) {
                _uiState.update { it.copy(isReaderLoading = false) }
                return@evaluateJavascript
            }
            session.evaluateJavascript(script) { rawResult ->
                if (readerRequestCounter.get() != requestId) return@evaluateJavascript
                handleExtracted(rawResult)
            }
        }
    }

    fun cancelReaderLoading() {
        readerRequestCounter.incrementAndGet()
        readerExtractJob?.cancel()
        readerExtractJob = null
        _uiState.update { it.copy(isReaderLoading = false) }
    }

    fun closeReaderMode() {
        readerRequestCounter.incrementAndGet()
        readerExtractJob?.cancel()
        readerExtractJob = null
        _uiState.update { it.copy(readerArticle = null, isReaderLoading = false) }
    }

    fun toggleBookmarkForActiveTab() {
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) return
        val canonicalUrl = OmniboxEngine.canonicalizeUrlSchemeAndHost(active.url)
        viewModelScope.launch {
            val existingAll = (db.bookmarkDao().findAllByUrl(canonicalUrl) +
                if (canonicalUrl != active.url) db.bookmarkDao().findAllByUrl(active.url) else emptyList())
                .distinctBy { it.id }
            if (existingAll.isNotEmpty()) {
                db.bookmarkDao().deleteByIds(existingAll.map { it.id })
                existingAll.forEach { entity ->
                    app.mozillaSyncCoordinator.mirrorBookmarkDeleted(entity)
                }
                showSnackbar("Removed bookmark")
            } else {
                val entity = BookmarkEntity(
                    id = UUID.randomUUID().toString(),
                    title = active.title.ifBlank { active.displayHost },
                    url = canonicalUrl
                )
                db.bookmarkDao().upsert(entity)
                app.mozillaSyncCoordinator.mirrorBookmarkAdded(entity)
                showSnackbar("Bookmarked ${active.title}")
            }
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            val existing = db.bookmarkDao().findById(id)
                ?: _uiState.value.bookmarks.firstOrNull { it.id == id }
            db.bookmarkDao().deleteById(id)
            if (existing != null) {
                app.mozillaSyncCoordinator.mirrorBookmarkDeleted(existing)
            }
        }
    }

    fun clearHistory() {
        lastRecordedHistoryUrlByTab.clear()
        lastRecordedHistoryTitleByTab.clear()
        viewModelScope.launch {
            db.historyDao().clearAll()
            app.mozillaSyncCoordinator.mirrorHistoryCleared()
            showSnackbar("Browsing history cleared")
        }
    }

    fun deleteHistoryEntry(url: String) {
        lastRecordedHistoryUrlByTab.entries.removeIf { it.value == url }
        viewModelScope.launch {
            db.historyDao().deleteByUrl(url)
            app.mozillaSyncCoordinator.mirrorHistoryDeleted(url)
        }
    }

    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookiesAndSiteData: Boolean,
        clearCache: Boolean
    ) {
        if (!clearHistory && !clearCookiesAndSiteData && !clearCache) return
        if (clearHistory) {
            lastRecordedHistoryUrlByTab.clear()
            lastRecordedHistoryTitleByTab.clear()
        }
        if (clearHistory || clearCache) {
            clearGlobalReaderImageCache()
        }
        if (clearCache) {
            _thumbnails.update { TabThumbnailsSnapshot() }
        }
        viewModelScope.launch {
            if (clearHistory) {
                db.historyDao().clearAll()
                app.mozillaSyncCoordinator.mirrorHistoryCleared()
            }
            if (clearHistory || clearCache) {
                app.faviconRepository.clearFaviconCache()
            }
            pool.clearProfilesData(
                clearHistory = clearHistory,
                clearCookiesAndSiteData = clearCookiesAndSiteData,
                clearCache = clearCache
            )
            val clearedParts = buildList {
                if (clearHistory) add("history")
                if (clearCookiesAndSiteData) add("cookies & site data")
                if (clearCache) add("cache")
            }
            val summary = when (clearedParts.size) {
                1 -> clearedParts.first()
                2 -> "${clearedParts[0]} and ${clearedParts[1]}"
                else -> "${clearedParts[0]}, ${clearedParts[1]}, and ${clearedParts[2]}"
            }
            showSnackbar("Cleared $summary")
        }
    }

    fun openFindInPage() {
        _uiState.update {
            it.copy(
                isFoldedSidebarOpen = false,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                findInPage = FindInPageState(visible = true)
            )
        }
    }

    fun updateFindInPageQuery(query: String) {
        _uiState.update { state ->
            state.copy(findInPage = state.findInPage.copy(query = query))
        }
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.findAllAsync(query)
    }

    fun findNextMatch(forward: Boolean) {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.findNext(forward)
    }

    fun closeFindInPage() {
        val activeId = _uiState.value.activeTabId
        if (activeId != null) {
            pool.getExisting(activeId)?.clearFindMatches()
        }
        _uiState.update { it.copy(findInPage = FindInPageState()) }
    }

    fun getCleanSharePayload(rawUrl: String? = null): Pair<String, Int>? {
        val url = rawUrl ?: _uiState.value.activeTab?.url ?: return null
        if (url == OmniboxEngine.NEW_TAB_URL) return null
        val sanitized = CleanUrlSanitizer.sanitize(url)
        return sanitized.url to sanitized.strippedCount
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(contextMenuHit = null) }
    }

    fun openActiveTabInExternalApp(intent: Intent, appLabel: String) {
        setOverlay(BrowserOverlay.NONE)
        val launched = onLaunchExternalIntentBridge?.invoke(intent) ?: false
        if (!launched) {
            showSnackbar("Could not open in $appLabel")
        }
    }

    fun resolveExternalAppPrompt(openNative: Boolean) {
        val prompt = _uiState.value.externalAppPrompt ?: return
        _uiState.update { it.copy(externalAppPrompt = null) }
        val activeId = _uiState.value.activeTabId
        if (openNative) {
            val launched = onLaunchExternalIntentBridge?.invoke(prompt.launchIntent) ?: false
            if (!launched && !prompt.fallbackUrl.isNullOrBlank()) {
                activeId?.let { pool.getExisting(it)?.webViewClient?.allowNextLoadInApp(prompt.fallbackUrl) }
                navigateActiveTab(prompt.fallbackUrl)
            }
        } else if (!prompt.fallbackUrl.isNullOrBlank()) {
            activeId?.let { pool.getExisting(it)?.webViewClient?.allowNextLoadInApp(prompt.fallbackUrl) }
            navigateActiveTab(prompt.fallbackUrl)
        }
    }

    fun resolveHttpsErrorPrompt(allowHttpForSite: Boolean) {
        val prompt = _uiState.value.httpsErrorPrompt ?: return
        _uiState.update { it.copy(httpsErrorPrompt = null) }
        if (allowHttpForSite) {
            mutateSiteOverride(prompt.host) { it.copy(httpsOnlyBypass = true) }
            navigateActiveTab(prompt.originalHttpUrl)
        } else {
            val active = _uiState.value.activeTab
            if (active != null && active.canGoBack) {
                goBack()
            } else if (active != null) {
                navigateActiveTab(OmniboxEngine.NEW_TAB_URL)
            }
        }
    }

    fun resolvePermissionPrompt(allow: Boolean, rememberForSite: Boolean) {
        val prompt = _uiState.value.permissionPrompt ?: return
        _uiState.update { it.copy(permissionPrompt = null) }

        if (allow) {
            grantPermissionWithRuntimeCheck(prompt, rememberForSite)
        } else {
            denyPromptSilently(prompt)
            if (rememberForSite && prompt.host.isNotBlank()) {
                persistSitePermission(prompt, allow = false)
            }
        }
    }

    private fun denyPromptSilently(prompt: SitePermissionPrompt) {
        runCatching {
            if (prompt.isGeolocation) {
                prompt.geoCallback?.invoke(prompt.geoOrigin, false, false)
            } else {
                prompt.webRequest?.deny()
            }
        }
    }

    private fun requiredAndroidPermissionsFor(prompt: SitePermissionPrompt): List<String> {
        if (prompt.isGeolocation) {
            return listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in prompt.resources) {
                add(Manifest.permission.CAMERA)
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in prompt.resources) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun grantPermissionWithRuntimeCheck(prompt: SitePermissionPrompt, rememberForSite: Boolean) {
        val needed = requiredAndroidPermissionsFor(prompt)
        if (needed.isEmpty()) {
            applyWebGrant(prompt, rememberForSite)
            return
        }
        val bridge = onRequestAndroidPermissionsBridge
        if (bridge == null) {
            denyPromptSilently(prompt)
            return
        }
        bridge.invoke(needed) { grantMap ->
            val allGranted = if (prompt.isGeolocation) {
                grantMap[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grantMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            } else {
                needed.all { grantMap[it] == true }
            }
            if (allGranted) {
                applyWebGrant(prompt, rememberForSite)
            } else {
                denyPromptSilently(prompt)
                showSnackbar("Android system permission required for ${prompt.host}")
            }
        }
    }

    private fun applyWebGrant(prompt: SitePermissionPrompt, rememberForSite: Boolean) {
        runCatching {
            if (prompt.isGeolocation) {
                prompt.geoCallback?.invoke(prompt.geoOrigin, true, rememberForSite)
            } else {
                prompt.webRequest?.grant(prompt.resources.toTypedArray())
            }
        }
        if (rememberForSite && prompt.host.isNotBlank()) {
            persistSitePermission(prompt, allow = true)
        }
    }

    private fun persistSitePermission(prompt: SitePermissionPrompt, allow: Boolean) {
        mutateSiteOverride(prompt.host) { current ->
            var entity = current
            if (prompt.isGeolocation) {
                entity = entity.copy(allowLocation = allow)
            } else {
                prompt.resources.forEach { res ->
                    entity = when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> entity.copy(allowCamera = allow)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> entity.copy(allowMic = allow)
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> entity.copy(allowProtectedMedia = allow)
                        else -> entity
                    }
                }
            }
            entity
        }
    }

    private fun maybeRequestNotificationPermissionOnce() {
        if (hasAskedNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        hasAskedNotificationPermission = true
        onRequestAndroidPermissionsBridge?.invoke(listOf(Manifest.permission.POST_NOTIFICATIONS)) { }
    }

    fun resetSiteOverride(host: String, forcePersistent: Boolean = false) {
        val normalizedHost = OmniboxEngine.normalizeHost(host)
        val isPrivate = !forcePersistent && (_uiState.value.activeTab?.isPrivate ?: _uiState.value.isPrivateView)
        val prevOverride = effectiveSiteOverride(normalizedHost, isPrivate)
        val hadAdblockDisabled = prevOverride?.adblockDisabled == true
        if (isPrivate) {
            // If persistent Room overrides exist for this host, store a clean default entry in
            // privateSiteOverrides to shadow them for this private session instead of falling back.
            _uiState.update { state ->
                val nextPrivate = if (normalizedHost in state.siteOverrides) {
                    state.privateSiteOverrides + (normalizedHost to SiteOverrideEntity(host = normalizedHost))
                } else {
                    state.privateSiteOverrides - normalizedHost
                }
                state.copy(privateSiteOverrides = nextPrivate)
            }
            _uiState.value.tabs
                .filter { it.isPrivate && OmniboxEngine.normalizeHost(it.displayHost) == normalizedHost }
                .forEach { matching ->
                    updateTab(matching.id) { it.copy(siteModeOverride = null) }
                }
            pool.clearAllDocumentStartScripts()
            syncGlobalPrepaintDarkScripts()
            val active = _uiState.value.activeTab
            pool.markAllBackgroundWebDarkDirty(active?.id)
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                val reloaded = applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                if (hadAdblockDisabled && !reloaded) {
                    reloadActiveTabIfHost(normalizedHost)
                }
            }
            showSnackbar("Reset site overrides for $normalizedHost")
            return
        }
        _uiState.update { it.copy(siteOverrides = it.siteOverrides - normalizedHost) }
        val writeGen = siteOverrideWrites.beginWrite()
        viewModelScope.launch {
            siteOverrideWrites.runSerializedWrite(
                generation = writeGen,
                writeToDb = {
                    db.siteOverrideDao().deleteByHost(host)
                    if (normalizedHost != host) {
                        db.siteOverrideDao().deleteByHost(normalizedHost)
                    }
                },
                readSettledFromDb = {
                    db.siteOverrideDao().getAll()
                        .associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                },
                onSettled = { settled ->
                    if (_uiState.value.siteOverrides != settled) {
                        _uiState.update { it.copy(siteOverrides = settled) }
                        refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                    }
                }
            )
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            withContext(Dispatchers.Main) {
                // Also clear any tab-level siteModeOverride on matching tabs so posture defaults resume
                _uiState.value.tabs
                    .filter { !it.isPrivate && OmniboxEngine.normalizeHost(it.displayHost) == normalizedHost }
                    .forEach { matching ->
                        updateTab(matching.id) { it.copy(siteModeOverride = null) }
                        _uiState.value.tabs.firstOrNull { it.id == matching.id }?.let { persistTab(it) }
                    }
                pool.clearAllDocumentStartScripts()
                syncGlobalPrepaintDarkScripts()
                val active = _uiState.value.activeTab
                pool.markAllBackgroundWebDarkDirty(active?.id)
                if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                    val reloaded = applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                    if (hadAdblockDisabled && !reloaded) {
                        reloadActiveTabIfHost(normalizedHost)
                    }
                }
                if (prevOverride != null) {
                    val discardedClosedIcon = _uiState.value.recentlyClosedTab?.customIcon
                    val discardedAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
                    _uiState.update {
                        it.copy(
                            snackbarMessage = "Reset overrides for $normalizedHost",
                            snackbarId = snackbarCounter.incrementAndGet(),
                            recentlyClosedTab = null,
                            recentlyResetSiteOverride = prevOverride,
                            recentlyAutoClearedTabs = null
                        )
                    }
                    cleanupUnusedCustomImage(discardedClosedIcon)
                    discardedAutoClearedIcons.forEach(::cleanupUnusedCustomImage)
                } else {
                    showSnackbar("Reset site overrides for $normalizedHost")
                }
            }
        }
    }

    fun undoResetSiteOverride() {
        val restored = _uiState.value.recentlyResetSiteOverride ?: return
        val normalizedHost = OmniboxEngine.normalizeHost(restored.host)
        val normalizedEntity = restored.copy(host = normalizedHost)
        _uiState.update {
            it.copy(
                siteOverrides = it.siteOverrides + (normalizedHost to normalizedEntity),
                recentlyResetSiteOverride = null,
                snackbarMessage = null
            )
        }
        val writeGen = siteOverrideWrites.beginWrite()
        viewModelScope.launch {
            siteOverrideWrites.runSerializedWrite(
                generation = writeGen,
                writeToDb = {
                    db.siteOverrideDao().upsert(normalizedEntity)
                },
                readSettledFromDb = {
                    db.siteOverrideDao().getAll()
                        .associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                },
                onSettled = { settled ->
                    if (_uiState.value.siteOverrides != settled) {
                        _uiState.update { it.copy(siteOverrides = settled) }
                        refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                    }
                }
            )
            app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            withContext(Dispatchers.Main) {
                refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                pool.clearAllDocumentStartScripts()
                syncGlobalPrepaintDarkScripts()
                val active = _uiState.value.activeTab
                pool.markAllBackgroundWebDarkDirty(active?.id)
                if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
                showSnackbar("Restored overrides for $normalizedHost")
            }
        }
    }

    fun getActivePrintSession(): Pair<BrowserSession, String>? {
        val active = _uiState.value.activeTab ?: return null
        if (active.isStartPage) return null
        val session = pool.getExisting(active.id) ?: return null
        return session to active.title
    }

    // Settings mutators
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setNightSurfaceStyle(style: NightSurfaceStyle) = viewModelScope.launch { prefs.setNightSurfaceStyle(style) }
    fun setAccentPreset(preset: AccentPreset) = viewModelScope.launch { prefs.setAccentPreset(preset) }
    fun setUseDynamicColor(enabled: Boolean) = viewModelScope.launch { prefs.setUseDynamicColor(enabled) }
    fun setAppIconBackgroundMode(mode: AppIconBackgroundMode) =
        viewModelScope.launch { prefs.setAppIconBackgroundMode(mode) }
    fun setAppIconColorStyle(style: AppIconColorStyle) =
        viewModelScope.launch { prefs.setAppIconColorStyle(style) }
    fun setAppIconMatchAccent(enabled: Boolean) =
        viewModelScope.launch { prefs.setAppIconMatchAccent(enabled) }
    fun setSyncAppIconInApp(enabled: Boolean) =
        viewModelScope.launch { prefs.setSyncAppIconInApp(enabled) }
    fun setWebDarkMode(pref: WebDarkModePreference) = viewModelScope.launch { prefs.setWebDarkMode(pref) }
    fun setDarkReaderAmoled(enabled: Boolean) = viewModelScope.launch { prefs.setDarkReaderAmoled(enabled) }
    fun setDarkReaderBrightness(value: Int) = viewModelScope.launch { prefs.setDarkReaderBrightness(value) }
    fun setDarkReaderContrast(value: Int) = viewModelScope.launch { prefs.setDarkReaderContrast(value) }
    fun setDarkReaderSepia(value: Int) = viewModelScope.launch { prefs.setDarkReaderSepia(value) }
    fun resetDarkReaderAdjustments() = viewModelScope.launch {
        prefs.setDarkReaderBrightness(100)
        prefs.setDarkReaderContrast(100)
        prefs.setDarkReaderSepia(0)
    }
    fun setDarkReaderSkipNativeDark(enabled: Boolean) = viewModelScope.launch { prefs.setDarkReaderSkipNativeDark(enabled) }

    fun setAdblockEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAdblockEnabled(enabled)
        pool.clearAllDocumentStartScripts()
    }

    fun setAdblockCosmeticEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAdblockCosmeticEnabled(enabled)
        pool.clearAllDocumentStartScripts()
    }

    fun setUserscriptsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setUserscriptsEnabled(enabled)
        pool.clearAllDocumentStartScripts()
    }

    fun setReaderFontFamily(family: ReaderFontFamily) = viewModelScope.launch { prefs.setReaderFontFamily(family) }
    fun setReaderFontSizeSp(sizeSp: Int) = viewModelScope.launch { prefs.setReaderFontSizeSp(sizeSp) }
    fun setReaderLineHeight(lineHeight: Float) = viewModelScope.launch { prefs.setReaderLineHeight(lineHeight) }
    fun setReaderSurfaceMode(mode: ReaderSurfaceMode) = viewModelScope.launch { prefs.setReaderSurfaceMode(mode) }

    fun setToolbarPlacement(posture: DevicePosture, placement: ToolbarPlacement) =
        viewModelScope.launch { prefs.setToolbarPlacement(posture, placement) }

    fun setDefaultSiteMode(posture: DevicePosture, mode: SiteMode) = viewModelScope.launch {
        prefs.setDefaultSiteMode(posture, mode)
    }

    fun setUnfoldedSpacesSidebar(visible: Boolean) = viewModelScope.launch {
        prefs.setUnfoldedSpacesSidebar(visible)
    }

    fun setSearchEngine(engine: SearchEngineId) = viewModelScope.launch { prefs.setSearchEngine(engine) }
    fun setCustomSearchUrlTemplate(template: String) = viewModelScope.launch { prefs.setCustomSearchUrlTemplate(template) }
    fun setSearchSuggestionsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setSearchSuggestionsEnabled(enabled) }
    fun setHttpsOnlyMode(enabled: Boolean) = viewModelScope.launch { prefs.setHttpsOnlyMode(enabled) }
    fun setExternalAppLinkPolicy(policy: ExternalAppLinkPolicy) = viewModelScope.launch { prefs.setExternalAppLinkPolicy(policy) }
    fun setPullToRefreshEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setPullToRefreshEnabled(enabled) }
    fun setSwipeUrlBarSwitchTabs(enabled: Boolean) = viewModelScope.launch { prefs.setSwipeUrlBarSwitchTabs(enabled) }
    fun setHideToolbarsOnScroll(enabled: Boolean) = viewModelScope.launch { prefs.setHideToolbarsOnScroll(enabled) }
    fun setConfirmClosePinnedOnSecondTap(enabled: Boolean) = viewModelScope.launch { prefs.setConfirmClosePinnedOnSecondTap(enabled) }
    fun setTabAutoClearDuration(duration: TabAutoClearDuration) = viewModelScope.launch { prefs.setTabAutoClearDuration(duration) }
    fun setBackgroundMediaEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setBackgroundMediaEnabled(enabled) }
    fun setPictureInPictureEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setPictureInPictureEnabled(enabled) }
    fun setBlockThirdPartyCookies(enabled: Boolean) = viewModelScope.launch { prefs.setBlockThirdPartyCookies(enabled) }

    fun onAppForegrounded() {
        if (!startupTabReconciliationDone.get()) return
        runAutoClearStaleTabsIfNeeded()
        app.mozillaSyncCoordinator.syncSwiftfoxAndZenOnForegroundIfDue()
    }

    fun onAppBackgrounded() {
        val activeId = _uiState.value.activeTabId ?: return
        touchTabLastAccessed(activeId, persistIfNormal = true)
    }

    private fun touchTabLastAccessed(
        tabId: String,
        persistIfNormal: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val existing = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (existing.isUnloaded) return
        val updated = existing.copy(lastAccessedAt = nowMs)
        updateTab(tabId) {
            if (it.isUnloaded) it else it.copy(lastAccessedAt = nowMs)
        }
        if (persistIfNormal && !updated.isPrivate) {
            persistTab(updated)
        }
    }

    fun runAutoClearStaleTabsIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        val plan = TabAutoClearPolicy.evaluate(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            normalThresholdMs = state.settings.tabAutoClearDuration.durationMs,
            nowMs = nowMs
        )
        if (!plan.hasAnyChanges) return

        val expiredPrivateIds = plan.expiredPrivateTabIds.toSet()
        val expiredRegularIds = plan.expiredRegularTabs.mapTo(mutableSetOf()) { it.id }
        val boundIdsToUnload = plan.expiredPinnedOrBoundTabs
            .filter { it.isBoundWorkspaceTab }
            .mapTo(mutableSetOf()) { it.id }
        val localPinIdsToUnload = plan.expiredPinnedOrBoundTabs
            .filter { !it.isBoundWorkspaceTab && it.isPinned }
            .mapTo(mutableSetOf()) { it.id }
        val allRemovedIds = expiredPrivateIds + expiredRegularIds + boundIdsToUnload
        val allAffectedIds = allRemovedIds + localPinIdsToUnload

        allAffectedIds.forEach { id ->
            releaseTabSessionResources(id, wasActive = id == state.activeTabId)
        }
        expiredPrivateIds.forEach { app.faviconRepository.clearPrivateTabIcon(it) }
        _thumbnails.update { current ->
            current.copy(bitmaps = current.bitmaps - allAffectedIds)
        }

        val unloadedLocals = mutableListOf<BrowserTabUi>()
        val updatedTabs = buildList {
            for (tab in state.tabs) {
                when {
                    tab.id in allRemovedIds -> Unit
                    tab.id in localPinIdsToUnload -> {
                        val unloaded = WorkspaceTabResolver.toUnloadedPinnedTab(tab)
                        unloadedLocals += unloaded
                        add(unloaded)
                    }
                    else -> add(tab)
                }
            }
        }

        val hadPrivate = state.tabs.any { it.isPrivate }
        val remainingHasPrivate = updatedTabs.any { it.isPrivate }
        val clearAllPrivateState = hadPrivate && !remainingHasPrivate && expiredPrivateIds.isNotEmpty()
        if (clearAllPrivateState) {
            app.faviconRepository.clearAllPrivateIcons()
            synchronized(privateUserscriptStorageLock) {
                privateSessionGeneration.incrementAndGet()
                privateUserscriptStorage.clear()
                privateDeletedUserscriptKeys.clear()
            }
            pool.clearPrivateProfileData()
        }

        if (activeMediaTabId in allAffectedIds || state.tabs.any { it.id in allAffectedIds && it.isPlayingMedia }) {
            handoffOrStopMediaService(
                activeMediaTabId ?: "",
                candidateTabs = updatedTabs.filter { !it.isUnloaded }
            )
        }
        val activeWasAffected = state.activeTabId != null && state.activeTabId in allAffectedIds
        if (activeWasAffected || state.permissionPrompt?.tabId in allAffectedIds) {
            state.permissionPrompt?.let(::denyPromptSilently)
        }

        val exitedPrivateView = state.isPrivateView && !remainingHasPrivate
        val validSpaceId = state.spaces.firstOrNull { it.id == state.activeSpaceId }?.id
            ?: state.spaces.firstOrNull()?.id
            ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
        val nextIsPrivateView = if (exitedPrivateView) false else state.isPrivateView
        val nextActiveId = when {
            !activeWasAffected && !exitedPrivateView -> state.activeTabId
            else -> WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = updatedTabs,
                activeSpaceId = validSpaceId,
                isPrivateView = nextIsPrivateView
            )
        }

        val autoClearMsg = TabAutoClearPolicy.formatAutoClearMessage(
            regularCount = plan.expiredRegularTabs.size,
            pinnedOrBoundCount = plan.expiredPinnedOrBoundTabs.size
        )
        val newSnapshot = if (autoClearMsg != null) {
            AutoClearedTabsSnapshot(
                regularTabs = plan.expiredRegularTabs,
                pinnedOrBoundTabs = plan.expiredPinnedOrBoundTabs
            )
        } else {
            null
        }
        val prevClosedIcon = if (autoClearMsg != null) state.recentlyClosedTab?.customIcon else null
        val prevAutoClearedIcons = if (autoClearMsg != null) {
            state.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        } else {
            emptyList()
        }

        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = current.pwaCandidatesByTab - allAffectedIds,
                activeSpaceId = if (exitedPrivateView) validSpaceId else current.activeSpaceId,
                isPrivateView = nextIsPrivateView,
                privateSiteOverrides = if (clearAllPrivateState) emptyMap() else current.privateSiteOverrides,
                privateZappedRules = if (clearAllPrivateState) emptyMap() else current.privateZappedRules,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId in allAffectedIds) null else current.pendingPinCloseTarget,
                recentlyClosedTab = if (autoClearMsg != null) null else current.recentlyClosedTab,
                recentlyResetSiteOverride = if (autoClearMsg != null) null else current.recentlyResetSiteOverride,
                recentlyAutoClearedTabs = newSnapshot ?: current.recentlyAutoClearedTabs,
                snackbarMessage = autoClearMsg ?: current.snackbarMessage,
                snackbarId = if (autoClearMsg != null) snackbarCounter.incrementAndGet() else current.snackbarId,
                zapper = if (activeWasAffected || exitedPrivateView) ZapperUiState() else current.zapper,
                readerArticle = if (activeWasAffected || exitedPrivateView) null else current.readerArticle,
                isReaderLoading = if (activeWasAffected || exitedPrivateView) false else current.isReaderLoading,
                findInPage = if (activeWasAffected || exitedPrivateView) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in allAffectedIds || activeWasAffected || exitedPrivateView) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in allAffectedIds || activeWasAffected || exitedPrivateView) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in allAffectedIds || activeWasAffected || exitedPrivateView) null else current.permissionPrompt
            )
        }

        cleanupUnusedCustomImage(prevClosedIcon)
        prevAutoClearedIcons.forEach(::cleanupUnusedCustomImage)

        val dbDeletes = (expiredRegularIds + boundIdsToUnload).toList()
        if (dbDeletes.isNotEmpty() || unloadedLocals.isNotEmpty()) {
            val unloadedEntities = unloadedLocals.map { unloaded ->
                toTabEntity(unloaded, computeNormalTabOrderIndex(updatedTabs, unloaded))
            }
            viewModelScope.launch {
                tabDbMutex.withLock {
                    if (dbDeletes.isNotEmpty()) {
                        db.tabDao().deleteByIds(dbDeletes)
                    }
                    if (unloadedEntities.isNotEmpty()) {
                        val liveIds = _uiState.value.tabs.mapTo(mutableSetOf()) { it.id }
                        val liveUnloaded = unloadedEntities.filter { it.id in liveIds }
                        if (liveUnloaded.isNotEmpty()) {
                            db.tabDao().upsertAll(liveUnloaded)
                        }
                    }
                }
            }
        }

        if (activeWasAffected || exitedPrivateView) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
                syncActiveTabProgressAndBlocked(nextActiveId)
                applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = true)
            } else if (!nextIsPrivateView) {
                createTab(
                    spaceId = validSpaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
    }

    fun restoreAutoClearedTabs() {
        val state = _uiState.value
        val snapshot = state.recentlyAutoClearedTabs ?: return
        val now = System.currentTimeMillis()
        val validSpaceIds = state.spaces.mapTo(mutableSetOf()) { it.id }
        val fallbackSpaceId = state.spaces.firstOrNull { it.id == state.activeSpaceId }?.id
            ?: state.spaces.firstOrNull()?.id
            ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
        val validEssIds = state.essentials.mapTo(mutableSetOf()) { it.id }
        val validZenIds = state.zenPinnedItems.mapTo(mutableSetOf()) { it.id }
        val profile = state.activeProfile
        val existingIds = state.tabs.mapTo(mutableSetOf()) { it.id }
        val existingEssIds = state.tabs.mapNotNullTo(mutableSetOf()) { if (!it.isPrivate) it.essentialId else null }
        val existingZenIds = state.tabs.mapNotNullTo(mutableSetOf()) { if (!it.isPrivate) it.zenPinnedId else null }

        val restoredAdditions = mutableListOf<BrowserTabUi>()
        val wokenLocalPinsById = mutableMapOf<String, BrowserTabUi>()
        val droppedIcons = mutableListOf<String>()

        fun restoreTabSnapshot(
            source: BrowserTabUi,
            spaceId: String = source.spaceId,
            url: String = source.url,
            title: String = source.title,
            siteModeOverride: SiteMode? = source.siteModeOverride,
            nextRevision: Int = source.sessionRevision + 1
        ): BrowserTabUi {
            val host = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url))
            val siteOverride = SiteModeResolver.parseOrNull(effectiveSiteOverride(host, false)?.siteModeOverride)
            return source.copy(
                spaceId = spaceId,
                url = url,
                title = title,
                siteModeOverride = siteModeOverride,
                isUnloaded = false,
                isLoading = false,
                progress = 0,
                isPlayingMedia = false,
                isPlayingVideo = false,
                isReaderAvailable = false,
                darkReaderSkippedNative = false,
                loadError = null,
                resolvedSiteMode = SiteModeResolver.resolve(siteModeOverride, siteOverride, profile),
                lastAccessedAt = now,
                sessionRevision = nextRevision
            )
        }

        for (tab in snapshot.regularTabs) {
            if (tab.id in existingIds) {
                tab.customIcon?.let(droppedIcons::add)
                continue
            }
            val resolvedSpaceId = if (tab.spaceId in validSpaceIds) tab.spaceId else fallbackSpaceId
            restoredAdditions += restoreTabSnapshot(tab, spaceId = resolvedSpaceId)
        }

        for (tab in snapshot.pinnedOrBoundTabs) {
            when {
                tab.essentialId != null -> {
                    if (tab.essentialId in validEssIds && tab.essentialId !in existingEssIds) {
                        existingEssIds += tab.essentialId
                        restoredAdditions += restoreTabSnapshot(tab)
                    } else {
                        tab.customIcon?.let(droppedIcons::add)
                    }
                }
                tab.zenPinnedId != null -> {
                    if (tab.zenPinnedId in validZenIds && tab.zenPinnedId !in existingZenIds && tab.spaceId in validSpaceIds) {
                        existingZenIds += tab.zenPinnedId
                        restoredAdditions += restoreTabSnapshot(tab)
                    } else {
                        tab.customIcon?.let(droppedIcons::add)
                    }
                }
                tab.isPinned -> {
                    val currentUnloaded = state.tabs.firstOrNull {
                        it.id == tab.id && !it.isPrivate && it.isPinned && it.isUnloaded
                    }
                    if (currentUnloaded != null) {
                        wokenLocalPinsById[tab.id] = restoreTabSnapshot(
                            source = currentUnloaded,
                            url = tab.url,
                            title = tab.title,
                            siteModeOverride = currentUnloaded.siteModeOverride,
                            nextRevision = currentUnloaded.sessionRevision + 1
                        )
                    }
                }
            }
        }

        val nextTabs = state.tabs.map { wokenLocalPinsById[it.id] ?: it } + restoredAdditions
        _uiState.update {
            it.copy(
                tabs = nextTabs,
                recentlyAutoClearedTabs = null,
                snackbarMessage = null
            )
        }
        droppedIcons.forEach(::cleanupUnusedCustomImage)

        val toPersist = wokenLocalPinsById.values + restoredAdditions
        if (toPersist.isNotEmpty()) {
            val entities = toPersist.map { restored ->
                toTabEntity(restored, computeNormalTabOrderIndex(nextTabs, restored))
            }
            viewModelScope.launch {
                tabDbMutex.withLock {
                    val liveIds = _uiState.value.tabs.mapTo(mutableSetOf()) { it.id }
                    val liveEntities = entities.filter { it.id in liveIds }
                    if (liveEntities.isNotEmpty()) {
                        db.tabDao().upsertAll(liveEntities)
                    }
                }
            }
        }
    }

    // Mozilla / Zen Sync & Chrome Bookmark Interop actions
    fun startMozillaSignIn() {
        viewModelScope.launch {
            app.mozillaSyncCoordinator.beginOAuthSignIn()
                .onSuccess { authUrl ->
                    _uiState.update { it.copy(fxaAuthUrl = authUrl) }
                }
                .onFailure { err ->
                    showSnackbar("Could not open Mozilla sign-in: ${err.message ?: "error"}")
                }
        }
    }

    fun openQrPairing() {
        _uiState.update { it.copy(isQrPairingOpen = true) }
    }

    fun closeQrPairing() {
        _uiState.update { it.copy(isQrPairingOpen = false) }
    }

    fun submitPairingUrl(pairingUrl: String) {
        _uiState.update { it.copy(isQrPairingOpen = false) }
        viewModelScope.launch {
            app.mozillaSyncCoordinator.beginPairing(pairingUrl)
                .onSuccess { authUrl ->
                    _uiState.update { it.copy(fxaAuthUrl = authUrl) }
                }
                .onFailure { err ->
                    showSnackbar("Pairing failed: ${err.message ?: "invalid link"}")
                }
        }
    }

    fun isFxAOAuthRedirectUrl(url: String): Boolean =
        app.mozillaSyncCoordinator.isOAuthRedirectUrl(url)

    fun handleFxAAuthRedirect(redirectUrl: String) {
        _uiState.update { it.copy(fxaAuthUrl = null) }
        viewModelScope.launch {
            app.mozillaSyncCoordinator.completeAuthRedirect(redirectUrl)
                .onSuccess {
                    showSnackbar("Signed in to Mozilla Sync")
                }
                .onFailure { err ->
                    showSnackbar("Sign-in failed: ${err.message ?: "error"}")
                }
        }
    }

    fun cancelFxAAuth() {
        _uiState.update { it.copy(fxaAuthUrl = null) }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            app.mozillaSyncCoordinator.syncNow(SyncReason.User)
                .onSuccess { summary -> showSnackbar(summary) }
                .onFailure { err -> showSnackbar("Sync error: ${err.message ?: "failed"}") }
        }
    }

    fun signOutMozillaSync() {
        viewModelScope.launch {
            app.mozillaSyncCoordinator.signOut()
            showSnackbar("Signed out (local bookmarks and tabs preserved)")
        }
    }

    fun sendActiveTabToZenSpace() {
        val activeTab = _uiState.value.activeTab ?: return
        if (activeTab.isPrivate || activeTab.isStartPage) return
        val space = _uiState.value.activeSpace
        val spaceGuid = space?.syncGuid
        if (spaceGuid.isNullOrBlank()) {
            showSnackbar("Switch to a synced Zen Space first")
            return
        }
        viewModelScope.launch {
            app.mozillaSyncCoordinator.sendTabToZenSpace(
                spaceSyncGuid = spaceGuid,
                url = activeTab.url,
                title = activeTab.title.ifBlank { activeTab.displayHost }
            ).onSuccess {
                showSnackbar("Sent to Zen Space \"${space.name}\"")
                app.mozillaSyncCoordinator.syncNow(SyncReason.User)
            }.onFailure { err ->
                showSnackbar("Could not send to Zen: ${err.message ?: "error"}")
            }
        }
    }

    fun setSyncBookmarksEnabled(enabled: Boolean) = viewModelScope.launch {
        syncPrefsMutex.withLock {
            prefs.setSyncBookmarksEnabled(enabled)
            val s = prefs.settingsFlow.first()
            app.mozillaSyncCoordinator.updateEnginePreferences(
                bookmarks = s.syncBookmarksEnabled,
                history = s.syncHistoryEnabled,
                tabs = s.syncTabsEnabled
            )
        }
    }

    fun setSyncHistoryEnabled(enabled: Boolean) = viewModelScope.launch {
        syncPrefsMutex.withLock {
            prefs.setSyncHistoryEnabled(enabled)
            val s = prefs.settingsFlow.first()
            app.mozillaSyncCoordinator.updateEnginePreferences(
                bookmarks = s.syncBookmarksEnabled,
                history = s.syncHistoryEnabled,
                tabs = s.syncTabsEnabled
            )
        }
    }

    fun setSyncTabsEnabled(enabled: Boolean) = viewModelScope.launch {
        syncPrefsMutex.withLock {
            prefs.setSyncTabsEnabled(enabled)
            val s = prefs.settingsFlow.first()
            app.mozillaSyncCoordinator.updateEnginePreferences(
                bookmarks = s.syncBookmarksEnabled,
                history = s.syncHistoryEnabled,
                tabs = s.syncTabsEnabled
            )
        }
    }

    fun setSyncZenSpacesEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSyncZenSpacesEnabled(enabled)
        app.mozillaSyncCoordinator.onZenSpacesSyncEnabledChanged(enabled)
    }

    fun setSyncSwiftfoxEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setSyncSwiftfoxEnabled(enabled) }

    fun setSyncPeriodicIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            prefs.setSyncPeriodicIntervalMinutes(minutes)
            app.mozillaSyncCoordinator.updatePeriodicSchedule(minutes)
        }
    }

    fun triggerImportBookmarksHtml() {
        onImportBookmarksHtmlBridge?.invoke()
    }

    fun triggerExportBookmarksHtml() {
        onExportBookmarksHtmlBridge?.invoke()
    }

    fun importBookmarksFromHtml(html: String) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                ChromeBookmarkHtmlCodec.parse(html)
            }
            if (parsed.isEmpty()) {
                showSnackbar("No valid bookmarks found in HTML file")
                return@launch
            }
            val existingUrls = db.bookmarkDao().getAll().mapTo(mutableSetOf()) { it.url }
            val toInsert = mutableListOf<BookmarkEntity>()
            var skipped = 0
            for (item in parsed) {
                if (!existingUrls.add(item.url)) {
                    skipped++
                    continue
                }
                toInsert += BookmarkEntity(
                    id = "imp_${UUID.randomUUID().toString().take(12)}",
                    title = item.title,
                    url = item.url,
                    folder = item.folder,
                    createdAt = item.addedAtMs
                )
            }
            if (toInsert.isNotEmpty()) {
                db.bookmarkDao().upsertAll(toInsert)
                app.mozillaSyncCoordinator.mirrorBookmarksBatchAdded(toInsert)
            }
            val msg = if (skipped > 0) {
                "Imported ${toInsert.size} bookmarks ($skipped duplicates skipped)"
            } else {
                "Imported ${toInsert.size} bookmarks"
            }
            showSnackbar(msg)
        }
    }

    fun buildBookmarksExportHtml(): String =
        ChromeBookmarkHtmlCodec.export(_uiState.value.bookmarks)

    fun showSnackbar(message: String) {
        val discardedClosedIcon = _uiState.value.recentlyClosedTab?.customIcon
        val discardedAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        _uiState.update {
            it.copy(
                snackbarMessage = message,
                snackbarId = snackbarCounter.incrementAndGet(),
                recentlyClosedTab = null,
                recentlyResetSiteOverride = null,
                recentlyAutoClearedTabs = null
            )
        }
        cleanupUnusedCustomImage(discardedClosedIcon)
        discardedAutoClearedIcons.forEach(::cleanupUnusedCustomImage)
    }

    fun clearSnackbar() {
        val discardedClosedIcon = _uiState.value.recentlyClosedTab?.customIcon
        val discardedAutoClearedIcons = _uiState.value.recentlyAutoClearedTabs?.allCustomIcons.orEmpty()
        _uiState.update {
            it.copy(
                snackbarMessage = null,
                recentlyClosedTab = null,
                recentlyResetSiteOverride = null,
                recentlyAutoClearedTabs = null
            )
        }
        cleanupUnusedCustomImage(discardedClosedIcon)
        discardedAutoClearedIcons.forEach(::cleanupUnusedCustomImage)
    }

    private fun isChromeDark(): Boolean =
        _uiState.value.settings.themeMode.isDarkActive(
            onSystemDarkThemeProvider?.invoke() ?: _uiState.value.isSystemDark
        )

    private fun syncLauncherIconIfLoaded() {
        val state = _uiState.value
        if (!state.hasLoadedSettings) return
        val resolved = state.settings.resolveAppIcon(
            isChromeDark = isChromeDark(),
            isBetaBuild = SwiftfoxBrand.isBetaBuild
        )
        viewModelScope.launch {
            LauncherIconManager.syncIcon(app, resolved)
        }
    }

    private fun resolveWebSurfaceDarkColors(darkState: ResolvedWebDarkState): Pair<Boolean, Boolean> =
        app.sessionEngineCoordinator.resolveWebSurfaceDarkColors(darkState)

    private fun buildGlobalPrepaintDarkConfig(isPrivate: Boolean): GlobalPrepaintDarkConfig {
        val state = _uiState.value
        return app.sessionEngineCoordinator.buildGlobalPrepaintDarkConfig(
            settings = state.settings,
            isChromeDark = isChromeDark(),
            siteOverrides = state.siteOverrides,
            privateSiteOverrides = state.privateSiteOverrides,
            isPrivate = isPrivate
        )
    }

    private fun shouldEnableSubframeDarkRuntime(config: GlobalPrepaintDarkConfig): Boolean =
        config.globalEnabled || config.includedHosts.isNotEmpty()

    private fun scheduleSubframeDarkRuntimeWarmup() {
        if (app.darkReaderInjector.isSubframeRuntimeWarmed || subframeDarkWarmupJob?.isActive == true) {
            return
        }
        subframeDarkWarmupJob = viewModelScope.launch(Dispatchers.IO) {
            app.darkReaderInjector.warmUpAssets()
            withContext(Dispatchers.Main) {
                syncGlobalPrepaintDarkScripts()
            }
        }
    }

    private fun syncGlobalPrepaintDarkScripts() {
        val normalConfig = buildGlobalPrepaintDarkConfig(isPrivate = false)
        val privateConfig = buildGlobalPrepaintDarkConfig(isPrivate = true)
        val normalSubframe = shouldEnableSubframeDarkRuntime(normalConfig)
        val privateSubframe = shouldEnableSubframeDarkRuntime(privateConfig)
        val needsWarmup = (normalSubframe || privateSubframe) && !app.darkReaderInjector.isSubframeRuntimeWarmed
        if (needsWarmup) {
            scheduleSubframeDarkRuntimeWarmup()
        }
        pool.updateAllGlobalPrepaintDarkScripts(
            normalScript = ScriptInjector.buildGlobalPrepaintDarkScript(normalConfig),
            privateScript = ScriptInjector.buildGlobalPrepaintDarkScript(privateConfig),
            normalSubframeDarkEnabled = normalSubframe,
            privateSubframeDarkEnabled = privateSubframe,
            darkReaderInjector = if (needsWarmup) null else app.darkReaderInjector
        )
    }

    private fun reloadActiveTabIfHost(host: String) {
        val active = _uiState.value.activeTab ?: return
        if (OmniboxEngine.normalizeHost(active.displayHost) == OmniboxEngine.normalizeHost(host)) {
            pool.getExisting(active.id)?.reload()
        }
    }

    private fun updateTab(tabId: String, transform: (BrowserTabUi) -> BrowserTabUi) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { if (it.id == tabId) transform(it) else it }
            )
        }
    }

    private fun computeNormalTabOrderIndex(tabs: List<BrowserTabUi>, target: BrowserTabUi): Int {
        if (target.isBoundWorkspaceTab) return 0
        if (target.isPinned) {
            return WorkspacePinnedFolderLogic.computeLocalPinnedOrderIndex(
                _uiState.value.pinnedFolders,
                tabs,
                target
            )
        }
        return tabs
            .filter {
                !it.isPrivate &&
                    !it.isBoundWorkspaceTab &&
                    it.spaceId == target.spaceId &&
                    !it.isPinned
            }
            .indexOfFirst { it.id == target.id }
            .coerceAtLeast(0)
    }

    private fun toTabEntity(tab: BrowserTabUi, sortOrder: Int): TabEntity = TabEntity(
        id = tab.id,
        spaceId = tab.spaceId,
        title = tab.title,
        url = tab.url,
        isPrivate = false,
        isPinned = tab.isPinned,
        siteModeOverride = tab.siteModeOverride?.name,
        lastAccessedAt = tab.lastAccessedAt,
        sortOrder = sortOrder,
        customIcon = tab.customIcon,
        customTitle = tab.customTitle,
        essentialId = tab.essentialId,
        zenPinnedId = tab.zenPinnedId,
        isUnloaded = tab.isUnloaded,
        pinnedUrl = tab.pinnedUrl,
        folderId = tab.folderId
    )

    private fun persistTab(tab: BrowserTabUi, scheduleSwiftfoxPush: Boolean = false) {
        if (tab.isPrivate) return
        viewModelScope.launch {
            tabDbMutex.withLock {
                // Read latest live state under lock so superseded async writes never overwrite a reset/closed pin
                val latest = _uiState.value.tabs.firstOrNull { it.id == tab.id } ?: return@withLock
                if (latest.isPrivate) return@withLock
                val orderIdx = computeNormalTabOrderIndex(_uiState.value.tabs, latest)
                db.tabDao().upsert(toTabEntity(latest, orderIdx))
            }
            if (scheduleSwiftfoxPush) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
    }

    private fun persistSpacePinnedState(
        spaceId: String,
        includeRegularTabs: Boolean,
        scheduleSwiftfoxPush: Boolean
    ) {
        val state = _uiState.value
        val folders = state.pinnedFolders.filter { it.spaceId == spaceId }
        val spaceTabs = state.tabs.filter {
            !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == spaceId
        }
        val pinnedEntities = spaceTabs.filter { it.isPinned }.map { tab ->
            toTabEntity(
                tab,
                WorkspacePinnedFolderLogic.computeLocalPinnedOrderIndex(state.pinnedFolders, state.tabs, tab)
            )
        }
        val regularEntities = if (includeRegularTabs) {
            spaceTabs.filter { !it.isPinned }.mapIndexed { idx, tab ->
                toTabEntity(tab.copy(isUnloaded = false, pinnedUrl = null, folderId = null), idx)
            }
        } else {
            emptyList()
        }
        val tabEntities = pinnedEntities + regularEntities
        if (folders.isEmpty() && tabEntities.isEmpty()) return
        viewModelScope.launch {
            tabDbMutex.withLock {
                val liveTabIds = _uiState.value.tabs.mapTo(mutableSetOf()) { it.id }
                val liveFolderIds = _uiState.value.pinnedFolders.mapTo(mutableSetOf()) { it.id }
                val liveTabs = tabEntities.filter { it.id in liveTabIds }
                val liveFolders = folders.filter { it.id in liveFolderIds }
                if (liveFolders.isNotEmpty()) {
                    db.pinnedFolderDao().upsertAll(liveFolders)
                }
                if (liveTabs.isNotEmpty()) {
                    db.tabDao().upsertAll(liveTabs)
                }
            }
            if (scheduleSwiftfoxPush) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
    }

    private fun persistTabsForSpace(spaceId: String, scheduleSwiftfoxPush: Boolean = false) {
        val state = _uiState.value
        val spaceTabs = state.tabs.filter {
            !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == spaceId
        }
        val pinnedEntities = spaceTabs.filter { it.isPinned }.map { tab ->
            toTabEntity(
                tab,
                WorkspacePinnedFolderLogic.computeLocalPinnedOrderIndex(state.pinnedFolders, state.tabs, tab)
            )
        }
        val regularEntities = spaceTabs.filter { !it.isPinned }.mapIndexed { idx, tab ->
            toTabEntity(tab.copy(isUnloaded = false, pinnedUrl = null, folderId = null), idx)
        }
        val all = pinnedEntities + regularEntities
        if (all.isEmpty()) return
        viewModelScope.launch {
            tabDbMutex.withLock {
                val liveIds = _uiState.value.tabs.mapTo(mutableSetOf()) { it.id }
                val liveEntities = all.filter { it.id in liveIds }
                if (liveEntities.isNotEmpty()) {
                    db.tabDao().upsertAll(liveEntities)
                }
            }
            if (scheduleSwiftfoxPush) {
                app.mozillaSyncCoordinator.scheduleSwiftfoxPush()
            }
        }
    }

    private fun handoffOrStopMediaService(
        stoppingTabId: String,
        candidateTabs: List<BrowserTabUi> = _uiState.value.tabs
    ) {
        val otherPlaying = candidateTabs.firstOrNull { it.id != stoppingTabId && it.isPlayingMedia }
        if (otherPlaying != null && _uiState.value.settings.backgroundMediaEnabled) {
            activeMediaTabId = otherPlaying.id
            BackgroundMediaService.claimPlayback(
                owner = this@BrowserViewModel,
                context = app,
                controller = createMediaCommandController(otherPlaying.id),
                title = otherPlaying.title.ifBlank { "Media playback" },
                host = otherPlaying.displayHost.ifBlank { "Swiftfox" },
                isPlaying = true,
                isPrivate = otherPlaying.isPrivate
            )
        } else if (activeMediaTabId == stoppingTabId || candidateTabs.none { it.id != stoppingTabId && it.isPlayingMedia }) {
            activeMediaTabId = null
            BackgroundMediaService.releasePlaybackOwner(
                owner = this@BrowserViewModel,
                context = app,
                stopService = true
            )
        }
    }

    private fun createMediaCommandController(targetTabId: String): BackgroundMediaService.MediaCommandController =
        BackgroundMediaService.MediaCommandController { cmd ->
            viewModelScope.launch(Dispatchers.Main) {
                val session = pool.getExisting(targetTabId)
                when (cmd) {
                    BackgroundMediaService.MediaCommand.PLAY -> session?.setMediaPaused(false)
                    BackgroundMediaService.MediaCommand.PAUSE -> session?.setMediaPaused(true)
                    BackgroundMediaService.MediaCommand.STOP -> {
                        session?.setMediaPaused(true)
                        activeMediaTabId = null
                        updateTab(targetTabId) { it.copy(isPlayingMedia = false, isPlayingVideo = false) }
                    }
                }
            }
        }

    private fun recordHistory(url: String, title: String) {
        if (url == OmniboxEngine.NEW_TAB_URL ||
            (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))
        ) {
            return
        }
        val canonicalUrl = OmniboxEngine.canonicalizeUrlSchemeAndHost(url)
        viewModelScope.launch {
            val existing = db.historyDao().findByUrl(canonicalUrl)
                ?: if (canonicalUrl != url) db.historyDao().findByUrl(url) else null
            val resolvedTitle = title
                .trim()
                .takeUnless { it.isEmpty() || it.equals("New Tab", ignoreCase = true) }
                ?: OmniboxEngine.extractHost(canonicalUrl).ifBlank { canonicalUrl }
            if (existing != null && existing.url != canonicalUrl) {
                db.historyDao().deleteByUrl(existing.url)
            }
            db.historyDao().upsert(
                HistoryEntity(
                    url = canonicalUrl,
                    title = resolvedTitle,
                    visitCount = (existing?.visitCount ?: 0) + 1,
                    lastVisitedAt = System.currentTimeMillis()
                )
            )
            app.mozillaSyncCoordinator.mirrorHistoryVisit(canonicalUrl, resolvedTitle)
        }
    }

    private fun maybeUpdateRecordedHistoryTitle(tabId: String, url: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        if (url == OmniboxEngine.NEW_TAB_URL ||
            (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))
        ) {
            return
        }
        if (lastRecordedHistoryTitleByTab[tabId] == clean) return
        lastRecordedHistoryTitleByTab[tabId] = clean
        val canonicalUrl = OmniboxEngine.canonicalizeUrlSchemeAndHost(url)
        viewModelScope.launch {
            val existing = db.historyDao().findByUrl(canonicalUrl)
                ?: (if (canonicalUrl != url) db.historyDao().findByUrl(url) else null)
                ?: return@launch
            if (existing.title != clean || existing.url != canonicalUrl) {
                if (existing.url != canonicalUrl) {
                    db.historyDao().deleteByUrl(existing.url)
                }
                db.historyDao().upsert(existing.copy(url = canonicalUrl, title = clean))
                app.mozillaSyncCoordinator.mirrorHistoryTitleUpdated(canonicalUrl, clean)
            }
        }
    }

    private fun captureThumbnailFor(tabId: String) {
        if (!pool.captureAndStoreThumbnail(tabId)) {
            val bmp = pool.getThumbnail(tabId) ?: return
            _thumbnails.update { current ->
                if (current.bitmaps[tabId] === bmp) current
                else current.copy(bitmaps = current.bitmaps + (tabId to bmp))
            }
        }
    }

    private fun stopActiveZapperIfRunning() {
        if (!_uiState.value.zapper.isActive) return
        val activeId = _uiState.value.activeTabId
        if (activeId != null) {
            pool.getExisting(activeId)?.let { session ->
                session.evaluateJavascript(app.zapperController.buildStopScript(session.tokens.zapperKey))
            }
        }
        _uiState.update { it.copy(zapper = ZapperUiState()) }
    }

    private fun scheduleActiveBlockedCountFlush() {
        if (!blockedCountFlushScheduled.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                delay(200)
                val state = _uiState.value
                val activeId = state.activeTabId
                if (activeId != null) {
                    _activeTabBlockedCount.value = blockedCountByTab[activeId]?.get() ?: 0
                }
            } finally {
                blockedCountFlushScheduled.set(false)
            }
        }
    }

    private fun scheduleSpaNavigationRefresh(tabId: String, url: String) {
        spaCheckJobByTab.remove(tabId)?.cancel()
        val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
        spaCheckJobByTab[tabId] = viewModelScope.launch {
            delay(350)
            val session = pool.getExisting(tabId) ?: return@launch
            if (session.isDestroyed) return@launch
            val state = _uiState.value
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate ?: session.isPrivate
            val tokens = session.tokens
            val (rawDocEnd, rawDocIdle) = withContext(Dispatchers.IO) {
                if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_END) to
                        app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_IDLE)
                } else {
                    emptyList<Pair<UserscriptEntity, Map<String, String>>>() to
                        emptyList()
                }
            }
            val docEnd = if (tabIsPrivate) mergePrivateUserscriptStorage(rawDocEnd) else rawDocEnd
            val docIdle = if (tabIsPrivate) mergePrivateUserscriptStorage(rawDocIdle) else rawDocIdle
            if (session.isDestroyed) return@launch
            val liveUrl = session.webView.url.orEmpty()
            if (liveUrl.isNotBlank() && liveUrl != url) {
                return@launch
            }
            if (docEnd.isNotEmpty() || docIdle.isNotEmpty()) {
                val shimJs = app.userscriptBridge.buildShimInitScript(tokens.gmToken, tokens.gmKey)
                if (shimJs.isNotBlank()) session.evaluateJavascript(shimJs)
                app.userscriptBridge.buildPerScriptSnippets(
                    scriptsWithStorage = docEnd,
                    runAt = UserscriptRunAt.DOCUMENT_END,
                    bridgeToken = tokens.gmToken,
                    runtimeKey = tokens.gmKey
                ).forEach { session.evaluateJavascript(it) }
                app.userscriptBridge.buildPerScriptSnippets(
                    scriptsWithStorage = docIdle,
                    runAt = UserscriptRunAt.DOCUMENT_IDLE,
                    bridgeToken = tokens.gmToken,
                    runtimeKey = tokens.gmKey
                ).forEach { session.evaluateJavascript(it) }
            }
            session.evaluateJavascript(app.readerExtractor.buildCheckReadableScript()) { raw ->
                if (session.isDestroyed) return@evaluateJavascript
                val nowUrl = session.webView.url.orEmpty()
                if (nowUrl.isNotBlank() && (nowUrl != url || ScriptInjector.extractOrigin(nowUrl) != expectedOrigin)) {
                    return@evaluateJavascript
                }
                val readable = raw?.trim().equals("true", ignoreCase = true)
                updateTab(tabId) { it.copy(isReaderAvailable = readable) }
            }
            if (!tabIsPrivate) {
                session.evaluateJavascript(
                    WebAppDetector.buildInspectPageScript(tokens.pwaToken, expectedOrigin)
                )
            }
        }
    }

    private fun refreshResolvedSiteModesForAllTabs(postureProfile: app.overbay.swiftfox.data.prefs.PostureProfile) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                val siteOverride = SiteModeResolver.parseOrNull(
                    effectiveSiteOverride(tab.displayHost, tab.isPrivate)?.siteModeOverride
                )
                val resolved = SiteModeResolver.resolve(
                    tabOverride = tab.siteModeOverride,
                    siteOverride = siteOverride,
                    postureProfile = postureProfile
                )
                if (tab.resolvedSiteMode != resolved) tab.copy(resolvedSiteMode = resolved) else tab
            }
            state.copy(tabs = updatedTabs)
        }
    }

    private fun applyResolvedSettingsToTab(
        tabId: String,
        reloadIfModeChanged: Boolean,
        updateWebDark: Boolean = true,
        applyIfWebDarkDirty: Boolean = true
    ): Boolean {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return false
        val session = pool.getExisting(tabId) ?: return false
        return applySettingsToSession(
            tab = tab,
            session = session,
            reloadIfModeChanged = reloadIfModeChanged,
            updateWebDark = updateWebDark,
            applyIfWebDarkDirty = applyIfWebDarkDirty
        )
    }

    private fun syncAllWarmSessionsWithSettings(
        reloadActiveOnSiteModeChange: Boolean,
        updateSiteOverrides: Boolean = true,
        updateWebDark: Boolean = true,
        updateBgMedia: Boolean = true
    ) {
        if (updateSiteOverrides) {
            refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
        }
        val state = _uiState.value
        val activeId = state.activeTab?.id
        val chromeDark = isChromeDark()
        if (updateWebDark) {
            syncGlobalPrepaintDarkScripts()
            // Avoid broadcasting Dark Reader JS to every background WebView simultaneously;
            // apply immediately to the active tab and refresh background tabs lazily on switch.
            pool.markAllBackgroundWebDarkDirty(activeId)
        }
        state.tabs.forEach { tab ->
            val session = pool.getExisting(tab.id) ?: return@forEach
            val isActive = tab.id == activeId
            if (updateWebDark) {
                val normalizedHost = OmniboxEngine.normalizeHost(tab.displayHost)
                val siteOverrideEntity = if (normalizedHost.isBlank()) null else effectiveSiteOverride(normalizedHost, tab.isPrivate)
                val darkState = DarkReaderConfig.resolve(state.settings, siteOverrideEntity, chromeDark)
                val (isDarkSurface, isAmoledSurface) = resolveWebSurfaceDarkColors(darkState)
                session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
            }
            applySettingsToSession(
                tab = tab,
                session = session,
                reloadIfModeChanged = reloadActiveOnSiteModeChange && isActive,
                updateSiteOverrides = updateSiteOverrides,
                updateWebDark = updateWebDark && isActive,
                applyIfWebDarkDirty = isActive,
                updateBgMedia = updateBgMedia
            )
        }
    }

    private fun applySettingsToSession(
        tab: BrowserTabUi,
        session: BrowserSession,
        reloadIfModeChanged: Boolean,
        updateSiteOverrides: Boolean = true,
        updateWebDark: Boolean = true,
        applyIfWebDarkDirty: Boolean = true,
        updateBgMedia: Boolean = true
    ): Boolean {
        val state = _uiState.value
        val normalizedHost = OmniboxEngine.normalizeHost(tab.displayHost)
        val siteOverrideEntity = if (normalizedHost.isBlank()) null else effectiveSiteOverride(normalizedHost, tab.isPrivate)
        var didReload = false

        if (updateSiteOverrides) {
            val siteOverride = SiteModeResolver.parseOrNull(siteOverrideEntity?.siteModeOverride)
            val resolvedMode = SiteModeResolver.resolve(
                tabOverride = tab.siteModeOverride,
                siteOverride = siteOverride,
                postureProfile = state.activeProfile
            )
            if (tab.resolvedSiteMode != resolvedMode) {
                updateTab(tab.id) { it.copy(resolvedSiteMode = resolvedMode) }
            }
            didReload = session.applySiteOverrides(
                mode = resolvedMode,
                customUserAgent = siteOverrideEntity?.customUserAgent,
                javascriptEnabled = siteOverrideEntity?.javascriptEnabled ?: true,
                cookiesEnabled = siteOverrideEntity?.cookiesEnabled ?: true,
                blockThirdParty = if (tab.isPopupWindow) {
                    false
                } else {
                    tab.isPrivate || state.settings.blockThirdPartyCookies
                },
                reloadIfChanged = reloadIfModeChanged,
                applyProfileCookiePolicy = (tab.id == state.activeTabId)
            )
        }

        if (updateWebDark || (applyIfWebDarkDirty && session.webDarkDirty)) {
            val chromeDark = isChromeDark()
            val darkState = DarkReaderConfig.resolve(
                settings = state.settings,
                siteOverride = siteOverrideEntity,
                isChromeDark = chromeDark
            )
            val (isDarkSurface, isAmoledSurface) = resolveWebSurfaceDarkColors(darkState)
            session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
            if (!darkState.enabled && tab.darkReaderSkippedNative) {
                updateTab(tab.id) { it.copy(darkReaderSkippedNative = false) }
            }
            session.applyWebDarkMode(darkState, app.darkReaderInjector)
        }

        if (updateBgMedia) {
            session.applyBackgroundPlayback(state.settings.backgroundMediaEnabled)
        }
        return didReload
    }

    private suspend fun reconcileSyncedPinnedTabsFromDb() {
        val (dbSpaces, dbFolders, dbPinnedTabs) = tabDbMutex.withLock {
            withContext(Dispatchers.IO) {
                val spaces = db.spaceDao().getAll()
                val folders = db.pinnedFolderDao().getAll()
                val pinned = db.tabDao().getNormalTabs().filter {
                    !it.isPrivate && it.isPinned && it.essentialId == null && it.zenPinnedId == null
                }
                Triple(spaces, folders, pinned)
            }
        }

        val stateBefore = _uiState.value
        val result = WorkspaceTabResolver.reconcileSyncedPinnedTabs(
            currentTabs = stateBefore.tabs,
            dbPinnedTabs = dbPinnedTabs,
            siteOverrides = stateBefore.siteOverrides,
            postureProfile = stateBefore.activeProfile
        )
        if (result.mergedTabs == stateBefore.tabs &&
            result.removedTabIds.isEmpty() &&
            result.updatedHomeUrlTabs.isEmpty() &&
            dbFolders == stateBefore.pinnedFolders &&
            dbSpaces.isEmpty()
        ) {
            return
        }

        val removedSet = result.removedTabIds.toSet()
        val updatedHomeUrlSet = result.updatedHomeUrlTabs.mapTo(mutableSetOf()) { it.id }
        val sessionResetIds = removedSet + updatedHomeUrlSet
        val oldTabIcons = stateBefore.tabs.mapNotNull { it.customIcon }.distinct()
        val oldFolderIcons = stateBefore.pinnedFolders.mapNotNull { it.customIcon }.distinct()

        result.updatedHomeUrlTabs.forEach { updated ->
            pool.discardHibernatedState(updated.id)
        }
        sessionResetIds.forEach { tabId ->
            releaseTabSessionResources(tabId, wasActive = tabId == stateBefore.activeTabId)
        }
        if (sessionResetIds.isNotEmpty()) {
            _thumbnails.update { current -> current.copy(bitmaps = current.bitmaps - sessionResetIds) }
            if (activeMediaTabId in sessionResetIds ||
                stateBefore.tabs.any { it.id in sessionResetIds && it.isPlayingMedia }
            ) {
                handoffOrStopMediaService(
                    activeMediaTabId ?: "",
                    candidateTabs = result.mergedTabs.filter { !it.isUnloaded }
                )
            }
            if (stateBefore.activeTabId in sessionResetIds || stateBefore.permissionPrompt?.tabId in sessionResetIds) {
                stateBefore.permissionPrompt?.let(::denyPromptSilently)
            }
        }

        val wasActiveInvalidated = stateBefore.activeTabId != null && stateBefore.activeTabId in sessionResetIds
        val activeTabIdBefore = stateBefore.activeTabId
        var resolvedNextActiveId: String? = activeTabIdBefore
        var resolvedActiveSpaceId = stateBefore.activeSpaceId

        _uiState.update { current ->
            val currentSpaceIds = current.spaces.mapTo(mutableSetOf()) { it.id }
            val mergedSpaces = if (dbSpaces.isEmpty()) {
                current.spaces
            } else {
                val dbSpaceIds = dbSpaces.mapTo(mutableSetOf()) { it.id }
                dbSpaces + current.spaces.filter { it.id in pendingCreatedSpaces.keys && it.id !in dbSpaceIds }
            }
            val dbFolderIds = dbFolders.mapTo(mutableSetOf()) { it.id }
            val mergedFolders = dbFolders + pendingCreatedPinnedFolders.values.filter { it.id !in dbFolderIds }
            val folderSpaceById = mergedFolders.associate { it.id to it.spaceId }
            val sanitizedMergedTabs = result.mergedTabs.map { tab ->
                val fId = tab.folderId
                if (!tab.isPrivate && tab.isPinned && !tab.isBoundWorkspaceTab &&
                    fId != null && folderSpaceById[fId] != tab.spaceId
                ) {
                    tab.copy(folderId = null)
                } else {
                    tab
                }
            }
            val validSpaceIds = mergedSpaces.mapTo(mutableSetOf()) { it.id }
            val nextActiveSpaceId = if (current.activeSpaceId in validSpaceIds) {
                current.activeSpaceId
            } else {
                mergedSpaces.firstOrNull()?.id ?: current.activeSpaceId
            }
            resolvedActiveSpaceId = nextActiveSpaceId
            val nextActiveId = if (wasActiveInvalidated) {
                WorkspaceTabResolver.resolveFallbackTabId(
                    remainingTabs = sanitizedMergedTabs,
                    activeSpaceId = nextActiveSpaceId,
                    isPrivateView = current.isPrivateView
                )
            } else {
                current.activeTabId
            }
            resolvedNextActiveId = nextActiveId
            current.copy(
                spaces = if (mergedSpaces != current.spaces && currentSpaceIds != mergedSpaces.mapTo(mutableSetOf()) { it.id }) {
                    mergedSpaces
                } else {
                    current.spaces
                },
                pinnedFolders = mergedFolders,
                activeSpaceId = nextActiveSpaceId,
                tabs = sanitizedMergedTabs,
                activeTabId = nextActiveId,
                pwaCandidatesByTab = if (sessionResetIds.isEmpty()) current.pwaCandidatesByTab else current.pwaCandidatesByTab - sessionResetIds,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId in sessionResetIds) null else current.pendingPinCloseTarget,
                zapper = if (wasActiveInvalidated) ZapperUiState() else current.zapper,
                readerArticle = if (wasActiveInvalidated) null else current.readerArticle,
                isReaderLoading = if (wasActiveInvalidated) false else current.isReaderLoading,
                findInPage = if (wasActiveInvalidated) FindInPageState() else current.findInPage,
                externalAppPrompt = if (current.externalAppPrompt?.tabId in sessionResetIds || wasActiveInvalidated) null else current.externalAppPrompt,
                httpsErrorPrompt = if (current.httpsErrorPrompt?.tabId in sessionResetIds || wasActiveInvalidated) null else current.httpsErrorPrompt,
                permissionPrompt = if (current.permissionPrompt?.tabId in sessionResetIds || wasActiveInvalidated) null else current.permissionPrompt
            )
        }

        oldTabIcons.forEach(::cleanupUnusedCustomImage)
        oldFolderIcons.forEach(::cleanupUnusedCustomImage)

        if (wasActiveInvalidated) {
            val nextActive = resolvedNextActiveId
            if (nextActive != null) {
                pool.setActiveTab(nextActive)
                syncActiveTabProgressAndBlocked(nextActive)
                applyResolvedSettingsToTab(nextActive, reloadIfModeChanged = true)
            } else if (!_uiState.value.isPrivateView) {
                createTab(
                    spaceId = resolvedActiveSpaceId,
                    isPrivate = false,
                    selectImmediately = true,
                    closeFoldedSidebar = false,
                    closeTabSwitcher = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pool.onThumbnailsEvicted = null
        pool.onThumbnailCaptured = null
        pool.detachCallbacksOnCleared(destroyPrivateSessions = false)
        BackgroundMediaService.releasePlaybackOwner(
            owner = this@BrowserViewModel,
            context = app,
            stopService = false
        )
        omniboxHolder.reset()
        readerExtractJob?.cancel()
        subframeDarkWarmupJob?.cancel()
        spaCheckJobByTab.values.forEach { it.cancel() }
        spaCheckJobByTab.clear()
        pageStartedInjectJobByTab.values.forEach { it.cancel() }
        pageStartedInjectJobByTab.clear()
        pageFinishedInjectJobByTab.values.forEach { it.cancel() }
        pageFinishedInjectJobByTab.clear()
        loadErrorDiagJobByTab.values.forEach { it.cancel() }
        loadErrorDiagJobByTab.clear()
    }
}