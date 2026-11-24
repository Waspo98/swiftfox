package app.overbay.swiftfox.ui.browser

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
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
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.db.TabEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.ReaderFontFamily
import app.overbay.swiftfox.data.prefs.ReaderSurfaceMode
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.data.prefs.WebDarkEngine
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.engine.BackgroundMediaService
import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.GlobalPrepaintDarkConfig
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.NetworkErrorDiagnostics
import app.overbay.swiftfox.engine.PrepaintDarkMode
import app.overbay.swiftfox.engine.ScriptInjector
import app.overbay.swiftfox.engine.SessionHostCallbacks
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.foldable.SiteModeResolver
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.icons.FaviconRepository
import app.overbay.swiftfox.features.omnibox.AutocompleteCandidate
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.omnibox.SuggestionKind
import app.overbay.swiftfox.features.sharing.CleanUrlSanitizer
import app.overbay.swiftfox.features.sync.ChromeBookmarkHtmlCodec
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher
import app.overbay.swiftfox.features.zapper.ZapperUiState
import app.overbay.swiftfox.ui.tabs.PendingPinCloseTarget
import app.overbay.swiftfox.ui.tabs.PinnedTabKind
import app.overbay.swiftfox.ui.tabs.WorkspaceTabResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

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

    private var suggestionJob: Job? = null
    private var readerExtractJob: Job? = null
    private val readerRequestCounter = AtomicLong(0L)
    private var initialTabsLoaded = false
    private val startupTabReconciliationDone = AtomicBoolean(false)
    private var hasReceivedInitialPosture = false
    private var hasAskedNotificationPermission = false
    private val pendingIncomingUrls = mutableListOf<String>()
    private val pendingProgrammaticHttpsUpgrades = mutableMapOf<String, Pair<String, String>>()
    private val lastRecordedHistoryUrlByTab = mutableMapOf<String, String>()
    private val lastRecordedHistoryTitleByTab = mutableMapOf<String, String>()
    private var activeMediaTabId: String? = null
    private val snackbarCounter = AtomicLong(0L)
    private val pendingBlockedDeltaByTab = ConcurrentHashMap<String, AtomicInteger>()
    private val blockedCountFlushScheduled = AtomicBoolean(false)
    private val filterUpdateInFlight = AtomicInteger(0)
    private val cosmeticExceptionsByTab = ConcurrentHashMap<String, List<String>>()
    private val spaCheckJobByTab = mutableMapOf<String, Job>()
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
    private val viewModelJson = Json { ignoreUnknownKeys = true }
    private val ioExceptionHandler = CoroutineExceptionHandler { _, err ->
        Log.w("BrowserViewModel", "Unhandled background coroutine error: ${err.message}")
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
            val pageHost = OmniboxEngine.extractHost(pageUrl).lowercase(Locale.ROOT)
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            val siteDisabled = effectiveSiteOverride(pageHost, tabIsPrivate)?.adblockDisabled == true
            val resourceType = app.adblockEngine.inferResourceType(requestUrl, headers)
            val blocked = app.adblockEngine.shouldBlock(
                requestUrl = requestUrl,
                pageUrl = pageUrl,
                resourceType = resourceType,
                globalEnabled = state.settings.adblockEnabled,
                siteDisabled = siteDisabled
            )
            if (blocked) {
                pendingBlockedDeltaByTab
                    .computeIfAbsent(tabId) { AtomicInteger(0) }
                    .incrementAndGet()
                scheduleBlockedCountFlush()
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
            if (currentTab.isPrivate) {
                val prevHost = FaviconRepository.normalizedHost(currentTab.url)
                val nextHost = FaviconRepository.normalizedHost(url)
                if (prevHost != nextHost) {
                    app.faviconRepository.clearPrivateTabIcon(tabId)
                }
            }
            pendingBlockedDeltaByTab.remove(tabId)
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
                    blockedCount = 0,
                    isReaderAvailable = false,
                    darkReaderSkippedNative = false,
                    loadError = null
                )
            }
            if (_uiState.value.activeTabId == tabId) {
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
            val host = OmniboxEngine.extractHost(url).lowercase(Locale.ROOT)
            val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
            val siteOverride = effectiveSiteOverride(host, tabIsPrivate)
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
            session.applyWebDarkening(darkState.useAlgorithmicLite)
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
                val cosmetic = app.adblockEngine.cosmeticForUrl(
                    url = url,
                    globalEnabled = state.settings.adblockEnabled,
                    cosmeticEnabled = state.settings.adblockCosmeticEnabled,
                    siteDisabled = siteAdblockDisabled
                )
                cosmeticExceptionsByTab[tabId] = cosmetic.exceptions
                val persistedZapped = if (host.isNotBlank()) db.zappedRuleDao().getEnabledForHost(host) else emptyList()
                val zappedRules = if (tabIsPrivate && host.isNotBlank()) {
                    persistedZapped + _uiState.value.privateZappedRules[host].orEmpty().filter { it.enabled }
                } else {
                    persistedZapped
                }
                val rawDocStartUserscripts = if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_START)
                } else {
                    emptyList()
                }
                val docStartUserscripts = if (tabIsPrivate) {
                    mergePrivateUserscriptStorage(rawDocStartUserscripts)
                } else {
                    rawDocStartUserscripts
                }
                val gmShimInitJs = if (docStartUserscripts.isNotEmpty()) {
                    app.userscriptBridge.buildShimInitScript(tokens.gmToken, tokens.gmKey)
                } else {
                    ""
                }
                val docStartSnippets = app.userscriptBridge.buildPerScriptSnippets(
                    scriptsWithStorage = docStartUserscripts,
                    runAt = UserscriptRunAt.DOCUMENT_START,
                    bridgeToken = tokens.gmToken,
                    runtimeKey = tokens.gmKey,
                    includeUrlGuard = true
                )
                val zappedCss = app.zapperController.buildCssForRules(zappedRules)
                withContext(Dispatchers.Main) {
                    if (session.isDestroyed) return@withContext
                    // Do not run stale page-started injection after page-finished has already committed
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && liveUrl != url) {
                        return@withContext
                    }
                    session.updateDocumentStartScripts(
                        origin = expectedOrigin,
                        cosmeticCss = cosmetic.css,
                        zappedCss = zappedCss,
                        gmShimInitJs = gmShimInitJs,
                        documentStartUserscriptSnippets = docStartSnippets
                    )
                    ScriptInjector.injectOnPageStarted(
                        webView = session.webView,
                        tokens = tokens,
                        cosmetic = cosmetic,
                        zappedRules = zappedRules,
                        zapperController = app.zapperController,
                        docStartUserscripts = docStartUserscripts,
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
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url.ifBlank { tab.url },
                    title = cleanTitle,
                    isLoading = false,
                    progress = 100,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
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
            val host = OmniboxEngine.extractHost(url).lowercase(Locale.ROOT)
            val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
            val siteOverride = effectiveSiteOverride(host, tabIsPrivate)
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
                val cosmetic = app.adblockEngine.cosmeticForUrl(
                    url = url,
                    globalEnabled = state.settings.adblockEnabled,
                    cosmeticEnabled = state.settings.adblockCosmeticEnabled,
                    siteDisabled = siteAdblockDisabled
                )
                cosmeticExceptionsByTab[tabId] = cosmetic.exceptions
                val persistedZapped = if (host.isNotBlank()) db.zappedRuleDao().getEnabledForHost(host) else emptyList()
                val zappedRules = if (tabIsPrivate && host.isNotBlank()) {
                    persistedZapped + _uiState.value.privateZappedRules[host].orEmpty().filter { it.enabled }
                } else {
                    persistedZapped
                }
                val rawDocEnd = if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_END)
                } else {
                    emptyList()
                }
                val rawDocIdle = if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_IDLE)
                } else {
                    emptyList()
                }
                val docEndUserscripts = if (tabIsPrivate) mergePrivateUserscriptStorage(rawDocEnd) else rawDocEnd
                val docIdleUserscripts = if (tabIsPrivate) mergePrivateUserscriptStorage(rawDocIdle) else rawDocIdle
                withContext(Dispatchers.Main) {
                    if (session.isDestroyed) return@withContext
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && liveUrl != url) {
                        return@withContext
                    }
                    ScriptInjector.injectOnPageFinished(
                        webView = session.webView,
                        tokens = tokens,
                        cosmetic = cosmetic,
                        runGenericCosmeticScan = runGenericScan,
                        zappedRules = zappedRules,
                        zapperController = app.zapperController,
                        docEndUserscripts = docEndUserscripts,
                        docIdleUserscripts = docIdleUserscripts,
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
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
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
            updateTab(tabId) { tab ->
                tab.copy(progress = progress, isLoading = progress in 1..99)
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
                    tabId = tabId
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
            if (normalizedPrompt.isGeolocation && saved?.allowLocation != null) {
                if (saved.allowLocation) {
                    grantPermissionWithRuntimeCheck(normalizedPrompt, rememberForSite = false)
                } else {
                    normalizedPrompt.geoCallback?.invoke(normalizedPrompt.geoOrigin, false, false)
                }
                return
            }
            if (!normalizedPrompt.isGeolocation && saved != null) {
                val allApproved = normalizedPrompt.resources.all { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> saved.allowCamera == true
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> saved.allowMic == true
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> saved.allowProtectedMedia == true
                        else -> false
                    }
                }
                val anyDenied = normalizedPrompt.resources.any { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> saved.allowCamera == false
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> saved.allowMic == false
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> saved.allowProtectedMedia == false
                        else -> false
                    }
                }
                if (allApproved) {
                    grantPermissionWithRuntimeCheck(normalizedPrompt, rememberForSite = false)
                    return
                }
                if (anyDenied) {
                    normalizedPrompt.webRequest?.deny()
                    return
                }
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
            val parentProfileName = if (popupIsPrivate) {
                null
            } else {
                pool.getExisting(parentTabId)?.profileName
                    ?: parent?.let {
                        WorkspaceTabResolver.resolveSessionContainerProfile(it, snapshot.spaces)
                    }
                    ?: BrowserSession.DEFAULT_PROFILE_NAME
            }
            val newTab = createTab(
                url = OmniboxEngine.NEW_TAB_URL,
                isPrivate = popupIsPrivate,
                spaceId = WorkspaceTabResolver.resolveChildTabSpaceId(
                    parentSpaceId = parent?.spaceId,
                    activeSpaceId = snapshot.activeSpaceId
                ),
                selectImmediately = true,
                isPopupWindow = true,
                parentTabId = parentTabId,
                profileOverride = parentProfileName
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
                pageStartedInjectJobByTab.remove(tabId)?.cancel()
                pageFinishedInjectJobByTab.remove(tabId)?.cancel()
                spaCheckJobByTab.remove(tabId)?.cancel()
                loadErrorDiagJobByTab.remove(tabId)?.cancel()
                val wasActive = _uiState.value.activeTabId == tabId
                if (wasActive) {
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
                    isPrivate = isPrivate
                )
                result.onSuccess { rec ->
                    val label = if (isPrivate) {
                        "Saved ${rec.fileName} to Downloads"
                    } else {
                        "Downloading ${rec.fileName}"
                    }
                    showSnackbar(label)
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
                updateTab(tabId) { it.copy(isPlayingMedia = isPlaying, isPlayingVideo = isPlaying && hasVideo) }
                val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
                if (isPlaying && _uiState.value.settings.backgroundMediaEnabled) {
                    maybeRequestNotificationPermissionOnce()
                    activeMediaTabId = tabId
                    attachMediaController(tabId)
                    BackgroundMediaService.updateState(
                        context = app,
                        title = title.ifBlank { tab?.title ?: "Media playback" },
                        host = tab?.displayHost.orEmpty().ifBlank { "Swiftfox" },
                        isPlaying = true,
                        isPrivate = tab?.isPrivate == true
                    )
                } else if (!isPlaying && activeMediaTabId == tabId) {
                    val otherPlaying = _uiState.value.tabs.firstOrNull { it.id != tabId && it.isPlayingMedia }
                    if (otherPlaying != null && _uiState.value.settings.backgroundMediaEnabled) {
                        activeMediaTabId = otherPlaying.id
                        attachMediaController(otherPlaying.id)
                        BackgroundMediaService.updateState(
                            context = app,
                            title = otherPlaying.title.ifBlank { "Media playback" },
                            host = otherPlaying.displayHost.ifBlank { "Swiftfox" },
                            isPlaying = true,
                            isPrivate = otherPlaying.isPrivate
                        )
                    } else if (_uiState.value.settings.backgroundMediaEnabled) {
                        // Keep notification alive in paused state so user can tap Play to resume
                        BackgroundMediaService.updateState(
                            context = app,
                            title = title.ifBlank { tab?.title ?: "Media playback" },
                            host = tab?.displayHost.orEmpty().ifBlank { "Swiftfox" },
                            isPlaying = false,
                            isPrivate = tab?.isPrivate == true
                        )
                    } else {
                        activeMediaTabId = null
                        BackgroundMediaService.stop(app)
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
            val state = _uiState.value
            if (!state.settings.adblockEnabled || !state.settings.adblockCosmeticEnabled) return
            val host = OmniboxEngine.extractHost(pageUrl)
            val tabIsPrivate = state.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
            if (effectiveSiteOverride(host, tabIsPrivate)?.adblockDisabled == true) return
            val expectedOrigin = ScriptInjector.extractOrigin(pageUrl) ?: return

            viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                val classes = runCatching { viewModelJson.decodeFromString<List<String>>(classesJson) }
                    .getOrDefault(emptyList())
                val ids = runCatching { viewModelJson.decodeFromString<List<String>>(idsJson) }
                    .getOrDefault(emptyList())
                if (classes.isEmpty() && ids.isEmpty()) return@launch
                val exceptions = cosmeticExceptionsByTab[tabId].orEmpty()
                val genericCss = app.adblockEngine.genericCosmeticCss(
                    url = pageUrl,
                    classes = classes,
                    ids = ids,
                    exceptions = exceptions.toSet(),
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
    }

    init {
        pool.onThumbnailsEvicted = { evictedIds ->
            _thumbnails.update { current ->
                TabThumbnailsSnapshot(current.bitmaps - evictedIds)
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
                    oldSettings.nightSurfaceStyle != newSettings.nightSurfaceStyle ||
                    oldSettings.webDarkMode != newSettings.webDarkMode ||
                    oldSettings.webDarkEngine != newSettings.webDarkEngine ||
                    oldSettings.darkReaderAmoled != newSettings.darkReaderAmoled ||
                    oldSettings.darkReaderBrightness != newSettings.darkReaderBrightness ||
                    oldSettings.darkReaderContrast != newSettings.darkReaderContrast ||
                    oldSettings.darkReaderSepia != newSettings.darkReaderSepia ||
                    oldSettings.darkReaderSkipNativeDark != newSettings.darkReaderSkipNativeDark
                val thirdPartyCookiesChanged = oldSettings.blockThirdPartyCookies != newSettings.blockThirdPartyCookies
                val bgMediaChanged = oldSettings.backgroundMediaEnabled != newSettings.backgroundMediaEnabled

                _uiState.update { it.copy(settings = newSettings, hasLoadedSettings = true) }
                if (!newSettings.backgroundMediaEnabled && oldSettings.backgroundMediaEnabled) {
                    activeMediaTabId = null
                    BackgroundMediaService.stop(app)
                }
                if (webDarkChanged) {
                    syncGlobalPrepaintDarkScripts()
                }
                if (activePostureSiteModeChanged || webDarkChanged || thirdPartyCookiesChanged || bgMediaChanged) {
                    syncAllWarmSessionsWithSettings(
                        reloadActiveOnSiteModeChange = activePostureSiteModeChanged,
                        updateSiteOverrides = activePostureSiteModeChanged || thirdPartyCookiesChanged,
                        updateWebDark = webDarkChanged,
                        updateBgMedia = bgMediaChanged
                    )
                }
            }
        }

        viewModelScope.launch {
            app.adblockEngine.status.collectLatest { status ->
                _uiState.update { it.copy(adblockStatus = status) }
            }
        }

        viewModelScope.launch {
            db.filterListDao().observeAll().collectLatest { lists ->
                _uiState.update { it.copy(filterLists = lists) }
            }
        }

        viewModelScope.launch {
            db.zappedRuleDao().observeAll().collectLatest { rules ->
                _uiState.update { it.copy(zappedRules = rules) }
            }
        }

        viewModelScope.launch {
            db.userscriptDao().observeAll().collectLatest { scripts ->
                _uiState.update { it.copy(userscripts = scripts) }
            }
        }

        viewModelScope.launch {
            db.spaceDao().observeAll().collectLatest { dbSpaces ->
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
            db.essentialDao().observeAll().collectLatest { dbEssentials ->
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
            db.bookmarkDao().observeAll().collectLatest { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }

        viewModelScope.launch {
            db.historyDao().observeRecent(200).collectLatest { history ->
                _uiState.update { it.copy(history = history) }
            }
        }

        viewModelScope.launch {
            db.downloadRecordDao().observeAll().collectLatest { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
            }
        }

        viewModelScope.launch {
            app.mozillaSyncCoordinator.status.collectLatest { syncSnapshot ->
                _uiState.update { it.copy(syncStatus = syncSnapshot) }
            }
        }

        viewModelScope.launch {
            db.syncedRemoteTabDao().observeAll().collectLatest { remoteTabs ->
                _uiState.update { it.copy(syncedRemoteTabs = remoteTabs) }
            }
        }

        viewModelScope.launch {
            db.zenPinnedItemDao().observeAll().collectLatest { zenPinned ->
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
            db.siteOverrideDao().observeAll().collectLatest { overrides ->
                if (siteOverrideWrites.hasPendingWrites) return@collectLatest
                val newMap = overrides.associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                val oldMap = _uiState.value.siteOverrides
                if (oldMap != newMap) {
                    _uiState.update { it.copy(siteOverrides = newMap) }
                    syncGlobalPrepaintDarkScripts()
                    refreshResolvedSiteModesForAllTabs(_uiState.value.activeProfile)
                    val activeId = _uiState.value.activeTabId
                    if (activeId != null) {
                        applyResolvedSettingsToTab(activeId, reloadIfModeChanged = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            val currentSettings = prefs.settingsFlow.first()
            val initialOverrides = db.siteOverrideDao().getAll()
                .associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
            val shouldSeed = !currentSettings.hasSeededDefaults
            db.seedDefaultsIfEmpty(seedInitialContent = shouldSeed)
            if (shouldSeed) {
                prefs.setHasSeededDefaults(true)
            }
            if (!initialTabsLoaded) {
                initialTabsLoaded = true
                val initialEssIds = db.essentialDao().getAll().mapTo(mutableSetOf()) { it.id }
                val initialZenIds = db.zenPinnedItemDao().getAll().mapTo(mutableSetOf()) { it.id }
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
                if (savedTabs.isEmpty() && existingInMemory.isEmpty() && pendingIncomingUrls.isEmpty()) {
                    createTab(
                        url = OmniboxEngine.NEW_TAB_URL,
                        isPrivate = false,
                        spaceId = SwiftfoxDatabase.DEFAULT_SPACE_ID,
                        selectImmediately = true
                    )
                } else if (savedTabs.isNotEmpty()) {
                    val profile = currentSettings.profileFor(_uiState.value.windowState.posture)
                    val restored = savedTabs.map { entity ->
                        val tabOverride = SiteModeResolver.parseOrNull(entity.siteModeOverride)
                        val host = OmniboxEngine.extractHost(entity.url)
                        val siteOverride = SiteModeResolver.parseOrNull(
                            initialOverrides[host]?.siteModeOverride
                        )
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
                                tabs = merged,
                                activeTabId = first.id,
                                activeSpaceId = resolvedSpaceId
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                tabs = merged,
                                activeSpaceId = fallbackSpaceId
                            )
                        }
                        if (pendingIncomingUrls.isEmpty()) {
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
                if (pendingIncomingUrls.isNotEmpty()) {
                    val queued = pendingIncomingUrls.toList()
                    pendingIncomingUrls.clear()
                    queued.forEach { target ->
                        createTab(
                            url = target,
                            isPrivate = false,
                            selectImmediately = true,
                            dismissAllOverlays = true
                        )
                    }
                }
            }
        }
    }

    fun updateSystemDarkTheme(isDark: Boolean) {
        if (_uiState.value.isSystemDark == isDark) return
        _uiState.update { it.copy(isSystemDark = isDark) }
        if (_uiState.value.settings.themeMode == ThemeMode.SYSTEM) {
            syncAllWarmSessionsWithSettings(reloadActiveOnSiteModeChange = false)
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
            val activeTab = _uiState.value.activeTab ?: return
            val newProfile = _uiState.value.settings.profileFor(newState.posture)
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
            // Keep resolvedSiteMode accurate across all tabs (including hibernated ones)
            refreshResolvedSiteModesForAllTabs(newProfile)
            pool.getExisting(activeTab.id)?.let { session ->
                val updated = _uiState.value.tabs.firstOrNull { it.id == activeTab.id } ?: activeTab
                applySettingsToSession(updated, session, reloadIfModeChanged = shouldReload)
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
        val spaceContainer = WorkspaceTabResolver.resolveSessionContainerProfile(
            tab = tab,
            spaces = _uiState.value.spaces
        )
        val existed = pool.getExisting(tab.id) != null
        val initialHost = OmniboxEngine.normalizeHost(tab.displayHost)
        val initialOverride = if (initialHost.isBlank()) null else effectiveSiteOverride(initialHost, tab.isPrivate)
        val initialDarkState = DarkReaderConfig.resolve(
            settings = _uiState.value.settings,
            siteOverride = initialOverride,
            isChromeDark = isChromeDark()
        )
        val (initialDarkSurface, initialAmoledSurface) = resolveWebSurfaceDarkColors(initialDarkState)
        val initialPrepaintScript = buildGlobalPrepaintDarkScript(tab.isPrivate)
        val session = pool.getOrCreate(
            tabId = tab.id,
            isPrivate = tab.isPrivate,
            spaceContainerProfile = spaceContainer,
            context = context,
            callbacks = sessionCallbacks,
            beforeRestore = { created ->
                created.applySurfaceBackground(initialDarkSurface, initialAmoledSurface)
                created.applyWebDarkening(initialDarkState.useAlgorithmicLite)
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
            omniboxSuggestions = if (selectImmediately) emptyList() else state.omniboxSuggestions,
            omniboxInlineAutocomplete = if (selectImmediately) null else state.omniboxInlineAutocomplete,
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
        profileOverride: String? = null,
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
            suggestionJob?.cancel()
            readerExtractJob?.cancel()
            readerExtractJob = null
            readerRequestCounter.incrementAndGet()
            _uiState.value.permissionPrompt?.let(::denyPromptSilently)
            _uiState.value.activeTabId?.let { prevId ->
                captureThumbnailFor(prevId)
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
            isPopupWindow = isPopupWindow,
            parentTabId = parentTabId,
            profileOverride = if (isPrivate) null else profileOverride,
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
                persistTabsForSpace(spaceId)
            } else {
                persistTab(tab)
            }
        }
        if (selectImmediately) {
            pool.setActiveTab(tab.id)
        }
        return tab
    }

    fun selectTab(tabId: String) {
        suggestionJob?.cancel()
        val previousActive = _uiState.value.activeTabId
        if (previousActive != null && previousActive != tabId) {
            captureThumbnailFor(previousActive)
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
                omniboxSuggestions = emptyList(),
                omniboxInlineAutocomplete = null,
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
        val state = _uiState.value
        val closing = state.tabs.firstOrNull { it.id == tabId } ?: return
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
        lastRecordedHistoryUrlByTab.remove(tabId)
        lastRecordedHistoryTitleByTab.remove(tabId)
        pendingProgrammaticHttpsUpgrades.remove(tabId)
        pendingBlockedDeltaByTab.remove(tabId)
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
            viewModelScope.launch {
                tabDbMutex.withLock {
                    db.tabDao().deleteById(tabId)
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
        val showClosedSnackbar = !closing.isPrivate && !closing.isStartPage
        _uiState.update { current ->
            val wasActive = current.activeTabId == tabId
            current.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                privateSiteOverrides = if (clearAllPrivateState) emptyMap() else current.privateSiteOverrides,
                privateZappedRules = if (clearAllPrivateState) emptyMap() else current.privateZappedRules,
                recentlyClosedTab = if (showClosedSnackbar) closing else null,
                recentlyResetSiteOverride = null,
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
        if (!closing.isPrivate && !showClosedSnackbar) {
            cleanupUnusedCustomImage(closing.customIcon)
        }

        if (nextActiveId != null && nextActiveId != state.activeTabId) {
            pool.setActiveTab(nextActiveId)
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

    fun selectSpace(spaceId: String) {
        stopActiveZapperIfRunning()
        suggestionJob?.cancel()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        val prevTabId = _uiState.value.activeTabId
        if (prevTabId != null) {
            captureThumbnailFor(prevTabId)
            pool.getExisting(prevTabId)?.clearFindMatches()
        }
        val spaceTabs = _uiState.value.tabs.filter {
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
            applyResolvedSettingsToTab(nextTabId, reloadIfModeChanged = true)
        } else {
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
            createTab(spaceId = spaceId, isPrivate = false, selectImmediately = true, closeFoldedSidebar = false)
        }
    }

    fun togglePrivateView(enabled: Boolean) {
        stopActiveZapperIfRunning()
        suggestionJob?.cancel()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        val prevTabId = _uiState.value.activeTabId
        if (prevTabId != null) {
            captureThumbnailFor(prevTabId)
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

    fun createSpace(name: String, iconKey: String, isolatedContainer: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val id = "space_${UUID.randomUUID().toString().take(8)}"
        val space = SpaceEntity(
            id = id,
            name = trimmed,
            iconKey = iconKey,
            containerProfileName = if (isolatedContainer) id else null,
            sortOrder = _uiState.value.spaces.size
        )
        pendingCreatedSpaces[id] = space
        _uiState.update { current ->
            if (current.spaces.any { it.id == id }) current
            else current.copy(spaces = current.spaces + space)
        }
        viewModelScope.launch {
            db.spaceDao().upsert(space)
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
            lastRecordedHistoryUrlByTab.remove(tab.id)
            lastRecordedHistoryTitleByTab.remove(tab.id)
            pendingProgrammaticHttpsUpgrades.remove(tab.id)
            pendingBlockedDeltaByTab.remove(tab.id)
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
        spaceToDelete.containerProfileName?.let { profileName ->
            pool.deleteContainerProfile(profileName)
        }
        val affectedZenPinnedIcons = _uiState.value.zenPinnedItems
            .filter { it.spaceId == spaceId }
            .mapNotNull { it.customIcon }
        val affectedTabIcons = affectedTabs.mapNotNull { it.customIcon }
        _uiState.update { state ->
            state.copy(
                spaces = state.spaces.filterNot { it.id == spaceId },
                activeSpaceId = if (wasActiveSpacePointer) fallback.id else state.activeSpaceId,
                tabs = remainingAfter,
                zenPinnedItems = state.zenPinnedItems.filterNot { it.spaceId == spaceId },
                externalAppPrompt = if (wasViewingDeletedSpace || state.externalAppPrompt?.tabId in affectedIds) null else state.externalAppPrompt,
                httpsErrorPrompt = if (wasViewingDeletedSpace || state.httpsErrorPrompt?.tabId in affectedIds) null else state.httpsErrorPrompt,
                permissionPrompt = if (wasViewingDeletedSpace || state.permissionPrompt?.tabId in affectedIds) null else state.permissionPrompt
            )
        }
        viewModelScope.launch {
            tabDbMutex.withLock {
                db.tabDao().deleteBySpace(spaceId)
                db.zenPinnedItemDao().deleteBySpace(spaceId)
                db.spaceDao().deleteById(spaceId)
            }
        }
        if (wasViewingDeletedSpace) {
            selectSpace(fallback.id)
        }
        showSnackbar("Deleted Space \"${spaceToDelete.name}\"")
        cleanupUnusedCustomImage(spaceToDelete.iconKey)
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
        viewModelScope.launch { db.spaceDao().upsert(updated) }
        showSnackbar("Renamed workspace to \"$trimmed\"")
    }

    fun setSpaceIcon(spaceId: String, iconKey: String) {
        val existing = _uiState.value.spaces.firstOrNull { it.id == spaceId } ?: return
        val oldIcon = existing.iconKey
        val nextKey = CustomIconSpec.parse(iconKey).encodeExplicit() ?: "${CustomIconSpec.ICON_PREFIX}layers"
        val updated = existing.copy(iconKey = nextKey)
        _uiState.update { state ->
            state.copy(spaces = state.spaces.map { if (it.id == spaceId) updated else it })
        }
        viewModelScope.launch { db.spaceDao().upsert(updated) }
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
        viewModelScope.launch { db.spaceDao().upsertAll(reordered) }
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
        viewModelScope.launch { db.essentialDao().upsert(updated) }
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
        viewModelScope.launch { db.essentialDao().upsert(updated) }
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
        viewModelScope.launch { db.essentialDao().upsertAll(merged) }
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
        val existingSession = pool.getExisting(tabId)
        existingSession?.clearFindMatches()
        val effectiveUrl = resolveHttpsOnlyForProgrammaticLoad(tabId, url, existingSession)
        if (activeMediaTabId == tabId) {
            handoffOrStopMediaService(tabId)
        }
        updateTab(tabId) {
            it.copy(
                url = effectiveUrl,
                isUnloaded = false,
                isLoading = effectiveUrl != OmniboxEngine.NEW_TAB_URL,
                progress = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) 0 else 10,
                isPlayingMedia = false,
                isPlayingVideo = false,
                blockedCount = 0,
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
        lastRecordedHistoryUrlByTab.remove(tabId)
        lastRecordedHistoryTitleByTab.remove(tabId)
        pendingProgrammaticHttpsUpgrades.remove(tabId)
        pendingBlockedDeltaByTab.remove(tabId)
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

        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = nextActiveId,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId == tabId) null else current.pendingPinCloseTarget,
                recentlyClosedTab = null,
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

        if (wasActive) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
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

        val count = targets.size
        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = nextActiveId,
                pendingPinCloseTarget = if (current.pendingPinCloseTarget?.tabId in targetIds) null else current.pendingPinCloseTarget,
                recentlyClosedTab = null,
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

        if (wasActiveClosed) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
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
                    privateSiteOverrides = emptyMap(),
                    privateZappedRules = emptyMap(),
                    pendingPinCloseTarget = null,
                    recentlyClosedTab = null,
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
        val count = targets.size
        _uiState.update { current ->
            current.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                recentlyClosedTab = null,
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

        if (wasActiveClosed) {
            if (nextActiveId != null) {
                pool.setActiveTab(nextActiveId)
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

        _uiState.update { current ->
            current.copy(
                tabs = updatedTabs,
                activeTabId = if (wasActiveClosed) null else current.activeTabId,
                pendingPinCloseTarget = null,
                recentlyClosedTab = null,
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

        if (wasActiveClosed || _uiState.value.activeTab == null) {
            createTab(
                spaceId = state.activeSpaceId,
                isPrivate = false,
                selectImmediately = true,
                closeFoldedSidebar = false,
                closeTabSwitcher = false
            )
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
            showSnackbar("Pinned ${essential.title} to Essentials")
        }
    }

    fun setTabPinned(tabId: String, pinned: Boolean) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (target.isPrivate || target.isBoundWorkspaceTab || target.isPinned == pinned) return
        val updatedTab = target.copy(
            isPinned = pinned,
            isUnloaded = false,
            pinnedUrl = if (pinned) (target.pinnedUrl ?: target.url) else null
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
            state.copy(tabs = reordered)
        }
        persistTabsForSpace(target.spaceId)
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
            persistTab(updatedTab)
        }
        showSnackbar("Updated $cleanTitle")
    }

    fun setTabCustomIcon(tabId: String, customIcon: String?) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (target.isPrivate) return
        val oldIcon = target.customIcon
        val updatedTab = target.copy(customIcon = customIcon)
        updateTab(tabId) { updatedTab }
        persistTab(updatedTab)
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
        val updatedTabs = state.tabs.map { tab ->
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
        _uiState.update { it.copy(tabs = updatedTabs) }
        if (!isPrivate) {
            persistTabsForSpace(activeSpace)
        }
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
        viewModelScope.launch { db.zenPinnedItemDao().upsert(updated) }
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
        viewModelScope.launch { db.zenPinnedItemDao().upsertAll(merged) }
    }

    private fun cleanupUnusedCustomImage(oldSpec: String?) {
        if (oldSpec.isNullOrBlank() || !oldSpec.startsWith(CustomIconSpec.IMAGE_PREFIX)) {
            return
        }
        val state = _uiState.value
        val stillReferenced = state.spaces.any { it.iconKey == oldSpec } ||
            state.essentials.any { it.customIcon == oldSpec } ||
            state.tabs.any { it.customIcon == oldSpec } ||
            state.zenPinnedItems.any { it.customIcon == oldSpec } ||
            state.recentlyClosedTab?.customIcon == oldSpec
        if (!stillReferenced) {
            app.faviconRepository.deleteCustomImageIfUnused(oldSpec)
        }
    }

    fun submitOmnibox(rawInput: String) {
        val targetUrl = OmniboxEngine.resolveInput(rawInput, _uiState.value.settings)
        navigateActiveTab(targetUrl)
    }

    fun navigateActiveTab(url: String) {
        stopActiveZapperIfRunning()
        suggestionJob?.cancel()
        readerExtractJob?.cancel()
        readerExtractJob = null
        readerRequestCounter.incrementAndGet()
        _uiState.value.permissionPrompt?.let(::denyPromptSilently)
        val active = _uiState.value.activeTab ?: createTab(url = url, selectImmediately = true)
        loadErrorDiagJobByTab.remove(active.id)?.cancel()
        // Discard any hibernated WebView bundle so re-creating the session loads the new URL
        pool.discardHibernatedState(active.id)

        val existingSession = pool.getExisting(active.id)
        existingSession?.clearFindMatches()
        val effectiveUrl = resolveHttpsOnlyForProgrammaticLoad(active.id, url, existingSession)

        if (activeMediaTabId == active.id) {
            handoffOrStopMediaService(active.id)
        }

        _uiState.update {
            it.copy(
                isOmniboxFocused = false,
                omniboxQuery = "",
                omniboxSuggestions = emptyList(),
                omniboxInlineAutocomplete = null,
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
                url = effectiveUrl,
                title = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) {
                    "New Tab"
                } else {
                    OmniboxEngine.extractHost(effectiveUrl).ifBlank { effectiveUrl }
                },
                isLoading = effectiveUrl != OmniboxEngine.NEW_TAB_URL,
                progress = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) 0 else 10,
                isPlayingMedia = false,
                isPlayingVideo = false,
                blockedCount = 0,
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

        if (!initialTabsLoaded) {
            pendingIncomingUrls += targetUrl
        } else {
            createTab(
                url = targetUrl,
                isPrivate = false,
                selectImmediately = true,
                dismissAllOverlays = true
            )
        }
    }

    fun focusOmnibox(prefillCurrentUrl: Boolean = true) {
        stopActiveZapperIfRunning()
        val current = _uiState.value.activeTab
        val initialText = if (prefillCurrentUrl && current != null && !current.isStartPage) {
            current.url
        } else {
            ""
        }
        _uiState.update {
            it.copy(
                isOmniboxFocused = true,
                omniboxQuery = initialText,
                isFoldedSidebarOpen = false,
                isSiteRulesOpen = false
            )
        }
        onOmniboxQueryChanged(initialText)
    }

    fun dismissOmnibox() {
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                isOmniboxFocused = false,
                omniboxQuery = "",
                omniboxSuggestions = emptyList(),
                omniboxInlineAutocomplete = null
            )
        }
    }

    private fun buildInMemoryAutocompleteCandidates(
        state: BrowserUiState,
        maxBookmarksAndHistory: Int = Int.MAX_VALUE
    ): List<AutocompleteCandidate> {
        val inPrivate = state.isPrivateView
        return buildList {
            state.tabs.forEach { tab ->
                if (!tab.isStartPage && !tab.isUnloaded && tab.isPrivate == inPrivate) {
                    add(
                        AutocompleteCandidate(
                            url = tab.url,
                            priority = 40,
                            visitCount = 1,
                            lastVisitedAt = tab.lastAccessedAt
                        )
                    )
                }
            }
            if (!inPrivate) {
                state.essentials.forEach { ess ->
                    add(AutocompleteCandidate(url = ess.url, priority = 30, visitCount = 2))
                }
                state.zenPinnedItems.forEach { pin ->
                    add(AutocompleteCandidate(url = pin.url, priority = 30, visitCount = 2))
                }
            }
            val bookmarkSlice = if (state.bookmarks.size > maxBookmarksAndHistory) {
                state.bookmarks.take(maxBookmarksAndHistory)
            } else {
                state.bookmarks
            }
            bookmarkSlice.forEach { bm ->
                add(
                    AutocompleteCandidate(
                        url = bm.url,
                        priority = 20,
                        visitCount = 2,
                        lastVisitedAt = bm.updatedAt.coerceAtLeast(bm.createdAt)
                    )
                )
            }
            if (!inPrivate) {
                val historySlice = if (state.history.size > maxBookmarksAndHistory) {
                    state.history.take(maxBookmarksAndHistory)
                } else {
                    state.history
                }
                historySlice.forEach { h ->
                    add(
                        AutocompleteCandidate(
                            url = h.url,
                            priority = 10,
                            visitCount = h.visitCount,
                            lastVisitedAt = h.lastVisitedAt
                        )
                    )
                }
            }
        }
    }

    fun onOmniboxQueryChanged(query: String) {
        val snapshot = _uiState.value
        // Keep the synchronous Main-thread pass strictly bounded so huge synced bookmark
        // collections never jank typing; the coroutine below refines across the full corpus.
        val instantAutocomplete = OmniboxEngine.findInlineAutocomplete(
            rawQuery = query,
            candidates = buildInMemoryAutocompleteCandidates(snapshot, maxBookmarksAndHistory = 80)
        )
        _uiState.update {
            it.copy(
                omniboxQuery = query,
                omniboxInlineAutocomplete = instantAutocomplete
            )
        }
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            val trimmed = query.trim()
            val results = mutableListOf<OmniboxSuggestion>()

            if (trimmed.isEmpty()) {
                _uiState.value.bookmarks.take(3).forEach { bm ->
                    results += OmniboxSuggestion(
                        id = "bm_${bm.id}",
                        title = bm.title,
                        subtitle = OmniboxEngine.extractHost(bm.url),
                        targetUrl = bm.url,
                        kind = SuggestionKind.BOOKMARK
                    )
                }
                if (!_uiState.value.isPrivateView) {
                    _uiState.value.history.take(4).forEach { h ->
                        if (results.none { it.targetUrl == h.url }) {
                            results += OmniboxSuggestion(
                                id = "hist_${h.url}",
                                title = h.title,
                                subtitle = OmniboxEngine.extractHost(h.url),
                                targetUrl = h.url,
                                kind = SuggestionKind.HISTORY
                            )
                        }
                    }
                }
                _uiState.update { it.copy(omniboxSuggestions = results, omniboxInlineAutocomplete = null) }
                return@launch
            }

            val bang = OmniboxEngine.detectBang(trimmed)
            if (bang != null) {
                val target = if (bang.query.isNotBlank()) {
                    OmniboxEngine.resolveInput(trimmed, _uiState.value.settings)
                } else {
                    OmniboxEngine.bangHomepageUrl(bang.bang)
                }
                results += OmniboxSuggestion(
                    id = "bang_${bang.triggerUsed}",
                    title = if (bang.query.isNotBlank()) {
                        "Search ${bang.bang.name} for \"${bang.query}\""
                    } else {
                        "${bang.bang.name} (${bang.triggerUsed})"
                    },
                    subtitle = "Bang shortcut",
                    targetUrl = target,
                    kind = SuggestionKind.BANG
                )
            } else if (trimmed.startsWith("!") || trimmed.startsWith("@")) {
                OmniboxEngine.bangs
                    .filter { b -> b.triggers.any { it.startsWith(trimmed.lowercase()) } }
                    .take(4)
                    .forEach { b ->
                        results += OmniboxSuggestion(
                            id = "bang_hint_${b.name}",
                            title = "${b.triggers.first()}  ${b.name}",
                            subtitle = "Type space + query",
                            targetUrl = b.triggers.first() + " ",
                            kind = SuggestionKind.BANG
                        )
                    }
            }

            val inPrivate = _uiState.value.isPrivateView
            _uiState.value.tabs
                .filter {
                    !it.isStartPage &&
                        !it.isUnloaded &&
                        it.isPrivate == inPrivate &&
                        (it.title.contains(trimmed, true) || it.url.contains(trimmed, true))
                }
                .take(2)
                .forEach { tab ->
                    results += OmniboxSuggestion(
                        id = "tab_${tab.id}",
                        title = tab.title,
                        subtitle = "Switch to open tab · ${tab.displayHost}",
                        targetUrl = tab.url,
                        kind = SuggestionKind.OPEN_TAB,
                        tabId = tab.id
                    )
                }

            val dbBookmarks = db.bookmarkDao().search(trimmed, limit = 12)
            dbBookmarks.take(3).forEach { bm ->
                if (results.none { it.targetUrl == bm.url }) {
                    results += OmniboxSuggestion(
                        id = "bm_${bm.id}",
                        title = bm.title,
                        subtitle = OmniboxEngine.extractHost(bm.url),
                        targetUrl = bm.url,
                        kind = SuggestionKind.BOOKMARK
                    )
                }
            }
            val dbHistory = if (!inPrivate) db.historyDao().search(trimmed, limit = 16) else emptyList()
            dbHistory.take(4).forEach { h ->
                if (results.none { it.targetUrl == h.url }) {
                    results += OmniboxSuggestion(
                        id = "hist_${h.url}",
                        title = h.title,
                        subtitle = OmniboxEngine.extractHost(h.url),
                        targetUrl = h.url,
                        kind = SuggestionKind.HISTORY
                    )
                }
            }

            val stateSnapshot = _uiState.value
            val refinedAutocomplete = withContext(Dispatchers.Default) {
                val allAutocompleteCandidates = buildList {
                    addAll(buildInMemoryAutocompleteCandidates(stateSnapshot))
                    dbBookmarks.forEach { bm ->
                        add(
                            AutocompleteCandidate(
                                url = bm.url,
                                priority = 20,
                                visitCount = 2,
                                lastVisitedAt = bm.updatedAt.coerceAtLeast(bm.createdAt)
                            )
                        )
                    }
                    dbHistory.forEach { h ->
                        add(
                            AutocompleteCandidate(
                                url = h.url,
                                priority = 10,
                                visitCount = h.visitCount,
                                lastVisitedAt = h.lastVisitedAt
                            )
                        )
                    }
                }
                OmniboxEngine.findInlineAutocomplete(
                    rawQuery = query,
                    candidates = allAutocompleteCandidates
                )
            }

            val resolvedDirect = OmniboxEngine.resolveInput(trimmed, _uiState.value.settings)
            if (OmniboxEngine.looksLikeUrl(trimmed)) {
                results.add(
                    0,
                    OmniboxSuggestion(
                        id = "direct_$trimmed",
                        title = trimmed,
                        subtitle = "Go to site",
                        targetUrl = resolvedDirect,
                        kind = SuggestionKind.DIRECT_URL
                    )
                )
            } else if (bang == null) {
                if (refinedAutocomplete != null &&
                    results.none { it.targetUrl.equals(refinedAutocomplete.targetUrl, ignoreCase = true) }
                ) {
                    results.add(
                        0,
                        OmniboxSuggestion(
                            id = "autocomplete_${refinedAutocomplete.targetUrl}",
                            title = refinedAutocomplete.displayText,
                            subtitle = "Go to suggested site",
                            targetUrl = refinedAutocomplete.targetUrl,
                            kind = SuggestionKind.DIRECT_URL
                        )
                    )
                }
                results += OmniboxSuggestion(
                    id = "search_self_$trimmed",
                    title = trimmed,
                    subtitle = "Search with ${_uiState.value.settings.searchEngine.label}",
                    targetUrl = resolvedDirect,
                    kind = SuggestionKind.SEARCH
                )
            }

            _uiState.update {
                it.copy(
                    omniboxSuggestions = results,
                    omniboxInlineAutocomplete = refinedAutocomplete
                )
            }

            // Never leak Private Mode keystrokes to remote search suggestion endpoints
            if (inPrivate) return@launch

            delay(180)
            val live = OmniboxEngine.fetchLiveSuggestions(trimmed, _uiState.value.settings)
            if (live.isNotEmpty()) {
                val merged = results.toMutableList()
                live.forEach { suggestionText ->
                    val url = OmniboxEngine.buildSearchUrl(suggestionText, _uiState.value.settings)
                    if (merged.none { it.title.equals(suggestionText, ignoreCase = true) }) {
                        merged += OmniboxSuggestion(
                            id = "live_$suggestionText",
                            title = suggestionText,
                            subtitle = _uiState.value.settings.searchEngine.label,
                            targetUrl = url,
                            kind = SuggestionKind.SEARCH
                        )
                    }
                }
                _uiState.update { it.copy(omniboxSuggestions = merged) }
            }
        }
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
        suggestionJob?.cancel()
        if (overlay != BrowserOverlay.NONE) {
            stopActiveZapperIfRunning()
        }
        if (overlay == BrowserOverlay.TAB_SWITCHER) {
            _uiState.value.activeTabId?.let { captureThumbnailFor(it) }
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
                        BrowserOverlay.DOWNLOADS
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
                        BrowserOverlay.DOWNLOADS
                    ) -> true
                else -> false
            }
            current.copy(
                activeOverlay = overlay,
                activeSettingsCategory = if (preserveSettingsCategory) current.activeSettingsCategory else null,
                overlayBackStack = nextStack,
                isFoldedSidebarOpen = if (overlay != BrowserOverlay.NONE) false else current.isFoldedSidebarOpen,
                isSiteRulesOpen = false,
                isOmniboxFocused = false,
                omniboxSuggestions = emptyList(),
                omniboxInlineAutocomplete = null
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
        val state = _uiState.value
        val isPrivate = state.activeTab?.isPrivate ?: state.isPrivateView
        val current = effectiveSiteOverride(normalizedHost, isPrivate) ?: SiteOverrideEntity(host = normalizedHost)
        val optimistic = transform(current)
        if (isPrivate) {
            _uiState.update { it.copy(privateSiteOverrides = it.privateSiteOverrides + (normalizedHost to optimistic)) }
            onUpdated(optimistic)
            return
        }
        _uiState.update { it.copy(siteOverrides = it.siteOverrides + (normalizedHost to optimistic)) }
        onUpdated(optimistic)
        val writeGen = siteOverrideWrites.beginWrite()
        viewModelScope.launch {
            siteOverrideWrites.runSerializedWrite(
                generation = writeGen,
                writeToDb = {
                    val existingDb = db.siteOverrideDao().getByHost(normalizedHost) ?: SiteOverrideEntity(host = normalizedHost)
                    val saved = transform(existingDb)
                    db.siteOverrideDao().upsert(saved)
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
        }
    }

    fun toggleActiveSiteWebDarkMode() {
        val active = _uiState.value.activeTab
        val host = OmniboxEngine.normalizeHost(active?.displayHost)
        val currentlyEnabled = _uiState.value.activeTabWebDarkEffective
        val nextEnabled = !currentlyEnabled

        if (host.isNotBlank() && active != null) {
            mutateSiteOverride(
                host = host,
                onUpdated = { updated ->
                    syncGlobalPrepaintDarkScripts()
                    pool.markAllBackgroundWebDarkDirty(active.id)
                    val darkState = DarkReaderConfig.resolve(
                        settings = _uiState.value.settings,
                        siteOverride = updated,
                        isChromeDark = isChromeDark()
                    )
                    val (isDarkSurface, isAmoledSurface) = resolveWebSurfaceDarkColors(darkState)
                    pool.getExisting(active.id)?.let { session ->
                        session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
                        session.applyWebDarkMode(darkState, app.darkReaderInjector)
                    }
                }
            ) { it.copy(webDarkModeOverride = nextEnabled) }
            showSnackbar(if (nextEnabled) "Dark web view on for $host" else "Dark web view off for $host")
        } else {
            viewModelScope.launch {
                val nextPref = if (nextEnabled) WebDarkModePreference.ALWAYS_ON else WebDarkModePreference.OFF
                prefs.setWebDarkMode(nextPref)
            }
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
                app.filterListRepository.toggleList(id, enabled)
                    .onFailure { err ->
                        showSnackbar("Filter list update failed: ${err.message ?: "error"}")
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
            pool.clearAllDocumentStartScripts()
            if (host != null) {
                refreshZappedCssForHost(host)
            }
        }
    }

    fun clearZappedRulesForHost(host: String) {
        viewModelScope.launch {
            db.zappedRuleDao().deleteByHost(host)
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
            pool.clearAllDocumentStartScripts()
        }
    }

    fun installUserscriptFromUrl(url: String) {
        viewModelScope.launch {
            val result = app.userscriptRepository.installFromUrl(url)
            result.onSuccess { script ->
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

    fun deleteUserscript(id: String) {
        viewModelScope.launch {
            app.userscriptRepository.deleteScript(id)
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
                                recentlyClosedTab = null
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
        viewModelScope.launch {
            val existingAll = db.bookmarkDao().findAllByUrl(active.url)
            if (existingAll.isNotEmpty()) {
                db.bookmarkDao().deleteByUrl(active.url)
                existingAll.forEach { entity ->
                    app.mozillaSyncCoordinator.mirrorBookmarkDeleted(entity)
                }
                showSnackbar("Removed bookmark")
            } else {
                val entity = BookmarkEntity(
                    id = UUID.randomUUID().toString(),
                    title = active.title.ifBlank { active.displayHost },
                    url = active.url
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
            val containerProfiles = _uiState.value.spaces.mapNotNull { it.containerProfileName }
            pool.clearProfilesData(
                containerProfiles = containerProfiles,
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
                    _uiState.update {
                        it.copy(
                            snackbarMessage = "Reset overrides for $normalizedHost",
                            snackbarId = snackbarCounter.incrementAndGet(),
                            recentlyClosedTab = null,
                            recentlyResetSiteOverride = prevOverride
                        )
                    }
                    cleanupUnusedCustomImage(discardedClosedIcon)
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
    fun setWebDarkMode(pref: WebDarkModePreference) = viewModelScope.launch { prefs.setWebDarkMode(pref) }
    fun setWebDarkEngine(engine: WebDarkEngine) = viewModelScope.launch { prefs.setWebDarkEngine(engine) }
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
    fun setBackgroundMediaEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setBackgroundMediaEnabled(enabled) }
    fun setBlockThirdPartyCookies(enabled: Boolean) = viewModelScope.launch { prefs.setBlockThirdPartyCookies(enabled) }

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

    fun setSyncZenSpacesEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setSyncZenSpacesEnabled(enabled) }

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
        _uiState.update {
            it.copy(
                snackbarMessage = message,
                snackbarId = snackbarCounter.incrementAndGet(),
                recentlyClosedTab = null,
                recentlyResetSiteOverride = null
            )
        }
        cleanupUnusedCustomImage(discardedClosedIcon)
    }

    fun clearSnackbar() {
        val discardedClosedIcon = _uiState.value.recentlyClosedTab?.customIcon
        _uiState.update {
            it.copy(
                snackbarMessage = null,
                recentlyClosedTab = null,
                recentlyResetSiteOverride = null
            )
        }
        cleanupUnusedCustomImage(discardedClosedIcon)
    }

    private fun isChromeDark(): Boolean = when (_uiState.value.settings.themeMode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.SYSTEM -> onSystemDarkThemeProvider?.invoke() ?: _uiState.value.isSystemDark
    }

    private fun isAmoledSurface(): Boolean =
        isChromeDark() && _uiState.value.settings.nightSurfaceStyle == NightSurfaceStyle.AMOLED

    private fun resolveWebSurfaceDarkColors(darkState: ResolvedWebDarkState): Pair<Boolean, Boolean> {
        val chromeDark = isChromeDark()
        val isDarkSurface = when {
            darkState.isSiteOverride -> darkState.enabled
            else -> darkState.enabled || chromeDark
        }
        val isAmoled = when {
            !isDarkSurface -> false
            darkState.useDarkReader -> darkState.amoled
            else -> isAmoledSurface()
        }
        return isDarkSurface to isAmoled
    }

    private fun buildGlobalPrepaintDarkScript(isPrivate: Boolean): String {
        val state = _uiState.value
        val settings = state.settings
        val globalDark = DarkReaderConfig.resolve(
            settings = settings,
            siteOverride = null,
            isChromeDark = isChromeDark()
        )
        val effectiveOverrides = if (isPrivate) {
            state.siteOverrides + state.privateSiteOverrides
        } else {
            state.siteOverrides
        }
        val excluded = mutableSetOf<String>()
        val included = mutableSetOf<String>()
        effectiveOverrides.forEach { (hostKey, entity) ->
            val normalized = OmniboxEngine.normalizeHost(hostKey.ifBlank { entity.host })
            if (normalized.isNotBlank()) {
                when (entity.webDarkModeOverride) {
                    false -> excluded += normalized
                    true -> included += normalized
                    null -> Unit
                }
            }
        }
        val mode = when (settings.webDarkEngine) {
            WebDarkEngine.DARK_READER -> PrepaintDarkMode.DARK_READER
            WebDarkEngine.ALGORITHMIC_LITE -> PrepaintDarkMode.ALGORITHMIC_LITE
        }
        val amoled = when (settings.webDarkEngine) {
            WebDarkEngine.DARK_READER -> settings.darkReaderAmoled
            WebDarkEngine.ALGORITHMIC_LITE -> isAmoledSurface()
        }
        return ScriptInjector.buildGlobalPrepaintDarkScript(
            GlobalPrepaintDarkConfig(
                mode = mode,
                amoled = amoled,
                globalEnabled = globalDark.enabled,
                excludedHosts = excluded,
                includedHosts = included
            )
        )
    }

    private fun syncGlobalPrepaintDarkScripts() {
        val normalScript = buildGlobalPrepaintDarkScript(isPrivate = false)
        val privateScript = buildGlobalPrepaintDarkScript(isPrivate = true)
        pool.updateAllGlobalPrepaintDarkScripts(
            normalScript = normalScript,
            privateScript = privateScript
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

    private fun persistTab(tab: BrowserTabUi) {
        if (tab.isPrivate) return
        val orderIdx = _uiState.value.tabs
            .filter {
                !it.isPrivate &&
                    !it.isBoundWorkspaceTab &&
                    it.spaceId == tab.spaceId &&
                    it.isPinned == tab.isPinned
            }
            .indexOfFirst { it.id == tab.id }
            .coerceAtLeast(0)
        viewModelScope.launch {
            tabDbMutex.withLock {
                // Skip upserting if the tab was already closed before this coroutine acquired the lock
                if (_uiState.value.tabs.none { it.id == tab.id }) return@withLock
                db.tabDao().upsert(
                    TabEntity(
                        id = tab.id,
                        spaceId = tab.spaceId,
                        title = tab.title,
                        url = tab.url,
                        isPrivate = false,
                        isPinned = tab.isPinned,
                        siteModeOverride = tab.siteModeOverride?.name,
                        lastAccessedAt = tab.lastAccessedAt,
                        sortOrder = orderIdx,
                        customIcon = tab.customIcon,
                        customTitle = tab.customTitle,
                        essentialId = tab.essentialId,
                        zenPinnedId = tab.zenPinnedId,
                        isUnloaded = tab.isUnloaded,
                        pinnedUrl = tab.pinnedUrl
                    )
                )
            }
        }
    }

    private fun persistTabsForSpace(spaceId: String) {
        val spaceTabs = _uiState.value.tabs.filter {
            !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == spaceId
        }
        val pinnedEntities = spaceTabs.filter { it.isPinned }.mapIndexed { idx, tab ->
            TabEntity(
                id = tab.id,
                spaceId = tab.spaceId,
                title = tab.title,
                url = tab.url,
                isPrivate = false,
                isPinned = true,
                siteModeOverride = tab.siteModeOverride?.name,
                lastAccessedAt = tab.lastAccessedAt,
                sortOrder = idx,
                customIcon = tab.customIcon,
                customTitle = tab.customTitle,
                essentialId = null,
                zenPinnedId = null,
                isUnloaded = tab.isUnloaded,
                pinnedUrl = tab.pinnedUrl
            )
        }
        val regularEntities = spaceTabs.filter { !it.isPinned }.mapIndexed { idx, tab ->
            TabEntity(
                id = tab.id,
                spaceId = tab.spaceId,
                title = tab.title,
                url = tab.url,
                isPrivate = false,
                isPinned = false,
                siteModeOverride = tab.siteModeOverride?.name,
                lastAccessedAt = tab.lastAccessedAt,
                sortOrder = idx,
                customIcon = tab.customIcon,
                customTitle = tab.customTitle,
                essentialId = null,
                zenPinnedId = null,
                isUnloaded = false,
                pinnedUrl = null
            )
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
        }
    }

    private fun handoffOrStopMediaService(
        stoppingTabId: String,
        candidateTabs: List<BrowserTabUi> = _uiState.value.tabs
    ) {
        val otherPlaying = candidateTabs.firstOrNull { it.id != stoppingTabId && it.isPlayingMedia }
        if (otherPlaying != null && _uiState.value.settings.backgroundMediaEnabled) {
            activeMediaTabId = otherPlaying.id
            attachMediaController(otherPlaying.id)
            BackgroundMediaService.updateState(
                context = app,
                title = otherPlaying.title.ifBlank { "Media playback" },
                host = otherPlaying.displayHost.ifBlank { "Swiftfox" },
                isPlaying = true,
                isPrivate = otherPlaying.isPrivate
            )
        } else if (activeMediaTabId == stoppingTabId || candidateTabs.none { it.id != stoppingTabId && it.isPlayingMedia }) {
            activeMediaTabId = null
            BackgroundMediaService.stop(app)
        }
    }

    private fun attachMediaController(targetTabId: String) {
        BackgroundMediaService.activeController = BackgroundMediaService.MediaCommandController { cmd ->
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
    }

    private fun recordHistory(url: String, title: String) {
        if (url == OmniboxEngine.NEW_TAB_URL || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return
        }
        viewModelScope.launch {
            val existing = db.historyDao().findByUrl(url)
            val resolvedTitle = title
                .trim()
                .takeUnless { it.isEmpty() || it.equals("New Tab", ignoreCase = true) }
                ?: OmniboxEngine.extractHost(url).ifBlank { url }
            db.historyDao().upsert(
                HistoryEntity(
                    url = url,
                    title = resolvedTitle,
                    visitCount = (existing?.visitCount ?: 0) + 1,
                    lastVisitedAt = System.currentTimeMillis()
                )
            )
            app.mozillaSyncCoordinator.mirrorHistoryVisit(url, resolvedTitle)
        }
    }

    private fun maybeUpdateRecordedHistoryTitle(tabId: String, url: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        if (url == OmniboxEngine.NEW_TAB_URL || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return
        }
        if (lastRecordedHistoryTitleByTab[tabId] == clean) return
        lastRecordedHistoryTitleByTab[tabId] = clean
        viewModelScope.launch {
            val existing = db.historyDao().findByUrl(url) ?: return@launch
            if (existing.title != clean) {
                db.historyDao().upsert(existing.copy(title = clean))
                app.mozillaSyncCoordinator.mirrorHistoryTitleUpdated(url, clean)
            }
        }
    }

    private fun captureThumbnailFor(tabId: String) {
        pool.captureAndStoreThumbnail(tabId)
        val bmp = pool.getThumbnail(tabId) ?: return
        _thumbnails.update { current ->
            current.copy(bitmaps = current.bitmaps + (tabId to bmp))
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

    private fun scheduleBlockedCountFlush() {
        if (!blockedCountFlushScheduled.compareAndSet(false, true)) return
        viewModelScope.launch {
            var releasedLock = false
            try {
                while (true) {
                    delay(150)
                    val snapshot = HashMap<String, Int>()
                    val keys = pendingBlockedDeltaByTab.keys.toList()
                    for (id in keys) {
                        val delta = pendingBlockedDeltaByTab[id]?.getAndSet(0) ?: 0
                        if (delta > 0) snapshot[id] = delta
                    }
                    if (snapshot.isNotEmpty()) {
                        _uiState.update { current ->
                            current.copy(
                                tabs = current.tabs.map { tab ->
                                    val delta = snapshot[tab.id]
                                    if (delta != null) tab.copy(blockedCount = tab.blockedCount + delta) else tab
                                }
                            )
                        }
                    }
                    blockedCountFlushScheduled.set(false)
                    releasedLock = true
                    val hasMore = pendingBlockedDeltaByTab.values.any { it.get() > 0 }
                    if (!hasMore || !blockedCountFlushScheduled.compareAndSet(false, true)) break
                    releasedLock = false
                }
            } finally {
                if (!releasedLock) {
                    blockedCountFlushScheduled.set(false)
                }
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
        val activeId = state.activeTabId
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
            session.applyWebDarkMode(darkState, app.darkReaderInjector)
        }

        if (updateBgMedia) {
            session.applyBackgroundPlayback(state.settings.backgroundMediaEnabled)
        }
        return didReload
    }

    override fun onCleared() {
        super.onCleared()
        pool.onThumbnailsEvicted = null
        BackgroundMediaService.activeController = null
        suggestionJob?.cancel()
        readerExtractJob?.cancel()
        spaCheckJobByTab.values.forEach { it.cancel() }
        spaCheckJobByTab.clear()
        loadErrorDiagJobByTab.values.forEach { it.cancel() }
        loadErrorDiagJobByTab.clear()
    }
}