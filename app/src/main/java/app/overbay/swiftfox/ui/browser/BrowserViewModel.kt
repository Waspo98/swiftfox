package app.overbay.swiftfox.ui.browser

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
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
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.ScriptInjector
import app.overbay.swiftfox.engine.SessionHostCallbacks
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.foldable.SiteModeResolver
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.omnibox.SuggestionKind
import app.overbay.swiftfox.features.sharing.CleanUrlSanitizer
import app.overbay.swiftfox.features.sync.ChromeBookmarkHtmlCodec
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher
import app.overbay.swiftfox.features.zapper.ZapperUiState
import kotlinx.coroutines.CancellationException
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
    private var initialTabsLoaded = false
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
    private val privateUserscriptStorage = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private val viewModelJson = Json { ignoreUnknownKeys = true }

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
        if (privateUserscriptStorage.isEmpty() || scriptsWithStorage.isEmpty()) return scriptsWithStorage
        return scriptsWithStorage.map { (script, persisted) ->
            val ephemeral = privateUserscriptStorage[script.id]
            if (ephemeral.isNullOrEmpty()) {
                script to persisted
            } else {
                script to (persisted + ephemeral)
            }
        }
    }

    val sessionCallbacks: SessionHostCallbacks = object : SessionHostCallbacks {
        override fun getHttpsOnlyEnabled(): Boolean = _uiState.value.settings.httpsOnlyMode

        override fun isHttpsBypassedForHost(host: String): Boolean {
            val state = _uiState.value
            val isPrivate = state.activeTab?.isPrivate ?: state.isPrivateView
            return effectiveSiteOverride(host, isPrivate)?.httpsOnlyBypass == true
        }

        override fun getExternalAppPolicy(host: String): ExternalAppLinkPolicy {
            val state = _uiState.value
            val isPrivate = state.activeTab?.isPrivate ?: state.isPrivateView
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
            pendingBlockedDeltaByTab.remove(tabId)
            cosmeticExceptionsByTab.remove(tabId)
            spaCheckJobByTab.remove(tabId)?.cancel()
            if (!isSameUrl && activeMediaTabId == tabId) {
                activeMediaTabId = null
                BackgroundMediaService.stop(app)
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
                    darkReaderSkippedNative = false
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
            applyResolvedSettingsToTab(tabId, reloadIfModeChanged = false)
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
            val tokens = session.tokens
            viewModelScope.launch(Dispatchers.IO) {
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
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && ScriptInjector.extractOrigin(liveUrl) != expectedOrigin) {
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
                        darkState = darkState,
                        darkReaderInjector = app.darkReaderInjector,
                        docStartUserscripts = docStartUserscripts,
                        userscriptBridge = app.userscriptBridge
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
            viewModelScope.launch(Dispatchers.IO) {
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
                    if (liveUrl.isNotBlank() && ScriptInjector.extractOrigin(liveUrl) != expectedOrigin) {
                        return@withContext
                    }
                    ScriptInjector.injectOnPageFinished(
                        webView = session.webView,
                        tokens = tokens,
                        cosmetic = cosmetic,
                        runGenericCosmeticScan = runGenericScan,
                        zappedRules = zappedRules,
                        zapperController = app.zapperController,
                        darkState = darkState,
                        darkReaderInjector = app.darkReaderInjector,
                        docEndUserscripts = docEndUserscripts,
                        docIdleUserscripts = docIdleUserscripts,
                        userscriptBridge = app.userscriptBridge
                    )
                    session.evaluateJavascript(app.readerExtractor.buildCheckReadableScript()) { raw ->
                        if (session.isDestroyed) return@evaluateJavascript
                        val nowUrl = session.webView.url.orEmpty()
                        if (nowUrl.isNotBlank() && ScriptInjector.extractOrigin(nowUrl) != expectedOrigin) {
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
            if (!tab.isPrivate && !tab.isStartPage && isHttp && lastRecordedHistoryUrlByTab[tabId] != url) {
                val initialTitle = if (urlChanged && tab.isLoading) {
                    OmniboxEngine.extractHost(url).ifBlank { url }
                } else {
                    tab.title.ifBlank { OmniboxEngine.extractHost(url).ifBlank { url } }
                }
                lastRecordedHistoryUrlByTab[tabId] = url
                lastRecordedHistoryTitleByTab[tabId] = initialTitle
                persistTab(tab)
                recordHistory(url, initialTitle)
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

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            _uiState.update { it.copy(isFullscreenVideo = true) }
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
            val parent = _uiState.value.tabs.firstOrNull { it.id == parentTabId }
            val newTab = createTab(
                url = OmniboxEngine.NEW_TAB_URL,
                isPrivate = parent?.isPrivate ?: _uiState.value.isPrivateView,
                spaceId = parent?.spaceId ?: _uiState.value.activeSpaceId,
                selectImmediately = true,
                isPopupWindow = true
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
                            matchCount = event.matchCount
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
                            temporaryZapCount = event.temporaryZapCount,
                            lastTemporaryZapSummary = event.summary
                        ),
                        snackbarMessage = "Zapped ${event.summary.ifBlank { "element" }}",
                        snackbarId = snackbarCounter.incrementAndGet(),
                        snackbarUndoZapper = true,
                        recentlyClosedTab = null
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
                        zapper = state.zapper.copy(temporaryZapCount = count),
                        snackbarMessage = "Restored element",
                        snackbarId = snackbarCounter.incrementAndGet(),
                        snackbarUndoZapper = false
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
                                matchCount = 0
                            ),
                            snackbarMessage = "Blocked for this Private session on $host",
                            snackbarId = snackbarCounter.incrementAndGet(),
                            snackbarUndoZapper = false
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
                            matchCount = 0
                        ),
                        snackbarMessage = "Blocked permanently on $host",
                        snackbarId = snackbarCounter.incrementAndGet(),
                        snackbarUndoZapper = false
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

            viewModelScope.launch(Dispatchers.IO) {
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
                val appendJs = buildAppendGenericCosmeticCssScript(genericCss)
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
                viewModelScope.launch(Dispatchers.IO) {
                    val script = db.userscriptDao().getById(scriptId) ?: return@launch
                    if (!script.enabled) return@launch
                    val grants = UserscriptParser.decodeStringList(script.grantsJson)
                    val allowedGrant = !grants.contains("none") &&
                        (grants.isEmpty() || grants.contains("GM_setValue") || grants.contains("GM.setValue"))
                    if (!allowedGrant) return@launch
                    val matches = UserscriptParser.decodeStringList(script.matchesJson)
                    val excludes = UserscriptParser.decodeStringList(script.excludesJson)
                    if (pageUrl.isBlank() || !UserscriptUrlMatcher.matches(pageUrl, matches, excludes)) return@launch

                    val map = privateUserscriptStorage.computeIfAbsent(scriptId) { ConcurrentHashMap() }
                    synchronized(map) {
                        if (!map.containsKey(key) && map.size >= 100) return@synchronized
                        val totalOther = map.entries
                            .filter { it.key != key }
                            .sumOf { it.key.toByteArray(Charsets.UTF_8).size + it.value.toByteArray(Charsets.UTF_8).size }
                        val delta = key.toByteArray(Charsets.UTF_8).size + valueJson.toByteArray(Charsets.UTF_8).size
                        if (totalOther + delta <= 1024 * 1024) {
                            map[key] = valueJson
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
                viewModelScope.launch(Dispatchers.IO) {
                    val script = db.userscriptDao().getById(scriptId) ?: return@launch
                    if (!script.enabled) return@launch
                    val grants = UserscriptParser.decodeStringList(script.grantsJson)
                    val allowedGrant = !grants.contains("none") &&
                        (grants.isEmpty() || grants.contains("GM_deleteValue") || grants.contains("GM.deleteValue"))
                    if (!allowedGrant) return@launch
                    val matches = UserscriptParser.decodeStringList(script.matchesJson)
                    val excludes = UserscriptParser.decodeStringList(script.excludesJson)
                    if (pageUrl.isBlank() || !UserscriptUrlMatcher.matches(pageUrl, matches, excludes)) return@launch
                    privateUserscriptStorage[scriptId]?.remove(key)
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
        observePersistence()
    }

    private fun observePersistence() {
        viewModelScope.launch {
            prefs.settingsFlow.collectLatest { newSettings ->
                val oldSettings = _uiState.value.settings
                val posture = _uiState.value.windowState.posture
                val activePostureSiteModeChanged =
                    oldSettings.profileFor(posture).defaultSiteMode != newSettings.profileFor(posture).defaultSiteMode
                val webDarkChanged = oldSettings.themeMode != newSettings.themeMode ||
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

                _uiState.update { it.copy(settings = newSettings) }
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
            db.spaceDao().observeAll().collectLatest { spaces ->
                if (spaces.isNotEmpty()) {
                    _uiState.update { state ->
                        val validSpaceIds = spaces.mapTo(mutableSetOf()) { it.id }
                        val resolvedSpaceId = if (state.activeSpaceId in validSpaceIds) {
                            state.activeSpaceId
                        } else {
                            spaces.first().id
                        }
                        val hasOrphanedTab = state.tabs.any { !it.isPrivate && it.spaceId !in validSpaceIds }
                        val migratedTabs = if (hasOrphanedTab) {
                            state.tabs.map { tab ->
                                if (!tab.isPrivate && tab.spaceId !in validSpaceIds) {
                                    tab.copy(spaceId = resolvedSpaceId)
                                } else {
                                    tab
                                }
                            }
                        } else {
                            state.tabs
                        }
                        state.copy(
                            spaces = spaces,
                            activeSpaceId = resolvedSpaceId,
                            tabs = migratedTabs
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            db.essentialDao().observeAll().collectLatest { essentials ->
                _uiState.update { it.copy(essentials = essentials) }
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
            }
        }

        viewModelScope.launch {
            db.siteOverrideDao().observeAll().collectLatest { overrides ->
                val newMap = overrides.associateBy { o -> OmniboxEngine.normalizeHost(o.host) }
                val oldMap = _uiState.value.siteOverrides
                if (oldMap != newMap) {
                    _uiState.update { it.copy(siteOverrides = newMap) }
                    val activeId = _uiState.value.activeTabId
                    if (activeId != null) {
                        applyResolvedSettingsToTab(activeId, reloadIfModeChanged = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            val currentSettings = prefs.settingsFlow.first()
            val shouldSeed = !currentSettings.hasSeededDefaults
            db.seedDefaultsIfEmpty(seedInitialContent = shouldSeed)
            if (shouldSeed) {
                prefs.setHasSeededDefaults(true)
            }
            if (!initialTabsLoaded) {
                initialTabsLoaded = true
                val savedTabs = db.tabDao().getNormalTabs()
                val existingInMemory = _uiState.value.tabs
                if (savedTabs.isEmpty() && existingInMemory.isEmpty() && pendingIncomingUrls.isEmpty()) {
                    createTab(
                        url = OmniboxEngine.NEW_TAB_URL,
                        isPrivate = false,
                        spaceId = SwiftfoxDatabase.DEFAULT_SPACE_ID,
                        selectImmediately = true
                    )
                } else if (savedTabs.isNotEmpty()) {
                    val profile = _uiState.value.activeProfile
                    val restored = savedTabs.map { entity ->
                        val tabOverride = SiteModeResolver.parseOrNull(entity.siteModeOverride)
                        val host = OmniboxEngine.extractHost(entity.url)
                        val siteOverride = SiteModeResolver.parseOrNull(
                            _uiState.value.siteOverrides[host]?.siteModeOverride
                        )
                        BrowserTabUi(
                            id = entity.id,
                            spaceId = entity.spaceId,
                            title = entity.title,
                            url = entity.url,
                            isPrivate = false,
                            siteModeOverride = tabOverride,
                            resolvedSiteMode = SiteModeResolver.resolve(tabOverride, siteOverride, profile),
                            lastAccessedAt = entity.lastAccessedAt
                        )
                    }
                    val restoredIds = restored.mapTo(mutableSetOf()) { it.id }
                    val merged = restored + existingInMemory.filterNot { it.id in restoredIds }
                    val first = existingInMemory.lastOrNull()
                        ?: restored.maxByOrNull { it.lastAccessedAt }
                        ?: merged.first()
                    _uiState.update {
                        it.copy(
                            tabs = merged,
                            activeTabId = first.id,
                            activeSpaceId = first.spaceId
                        )
                    }
                }
                if (pendingIncomingUrls.isNotEmpty()) {
                    val queued = pendingIncomingUrls.toList()
                    pendingIncomingUrls.clear()
                    queued.forEach { target ->
                        createTab(url = target, isPrivate = false, selectImmediately = true)
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
        _uiState.update { it.copy(windowState = newState) }

        if (isInitialEmission) {
            // Apply initial posture profile quietly without reloading or flashing a snackbar
            syncAllWarmSessionsWithSettings(reloadActiveOnSiteModeChange = false)
            return
        }

        if (postureChanged) {
            val activeTab = _uiState.value.activeTab ?: return
            val newProfile = _uiState.value.settings.profileFor(newState.posture)
            val siteOverride = SiteModeResolver.parseOrNull(
                effectiveSiteOverride(activeTab.displayHost, activeTab.isPrivate)?.siteModeOverride
            )
            val resolved = SiteModeResolver.resolve(
                tabOverride = activeTab.siteModeOverride,
                siteOverride = siteOverride,
                postureProfile = newProfile
            )
            val shouldReload = SiteModeResolver.shouldReloadOnTransition(
                previousMode = activeTab.resolvedSiteMode,
                newMode = resolved,
                currentUrl = activeTab.url
            )
            updateTab(activeTab.id) { it.copy(resolvedSiteMode = resolved) }
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
        _uiState.update { it.copy(isInPictureInPicture = inPip) }
    }

    fun ensureSession(tab: BrowserTabUi, context: Context): BrowserSession {
        val spaceContainer = _uiState.value.spaces
            .firstOrNull { it.id == tab.spaceId }
            ?.containerProfileName
        val existed = pool.getExisting(tab.id) != null
        val session = pool.getOrCreate(
            tabId = tab.id,
            isPrivate = tab.isPrivate,
            spaceContainerProfile = spaceContainer,
            context = context,
            callbacks = sessionCallbacks
        )
        applySettingsToSession(tab, session, reloadIfModeChanged = false)
        pendingProgrammaticHttpsUpgrades.remove(tab.id)?.let { (upgraded, originalHttp) ->
            session.webViewClient.registerProgrammaticHttpsUpgrade(upgraded, originalHttp)
        }
        if (!existed && !tab.isStartPage && session.webView.url.isNullOrBlank()) {
            val target = resolveHttpsOnlyForProgrammaticLoad(tab.id, tab.url, session)
            session.loadUrl(target)
        }
        return session
    }

    fun createTab(
        url: String = OmniboxEngine.NEW_TAB_URL,
        isPrivate: Boolean = _uiState.value.isPrivateView,
        spaceId: String = _uiState.value.activeSpaceId,
        selectImmediately: Boolean = true,
        isPopupWindow: Boolean = false
    ): BrowserTabUi {
        if (selectImmediately) {
            stopActiveZapperIfRunning()
            readerExtractJob?.cancel()
            readerExtractJob = null
            _uiState.value.activeTabId?.let { prevId ->
                pool.getExisting(prevId)?.clearFindMatches()
            }
        }
        val currentState = _uiState.value
        val host = OmniboxEngine.extractHost(url)
        val siteOverride = SiteModeResolver.parseOrNull(
            effectiveSiteOverride(host, isPrivate)?.siteModeOverride
        )
        val resolvedMode = SiteModeResolver.resolve(null, siteOverride, currentState.activeProfile)

        val tab = BrowserTabUi(
            id = UUID.randomUUID().toString(),
            spaceId = spaceId,
            title = if (url == OmniboxEngine.NEW_TAB_URL) "New Tab" else host.ifBlank { url },
            url = url,
            isPrivate = isPrivate,
            isPopupWindow = isPopupWindow,
            resolvedSiteMode = resolvedMode,
            lastAccessedAt = System.currentTimeMillis()
        )

        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + tab,
                activeTabId = if (selectImmediately) tab.id else state.activeTabId,
                isPrivateView = if (selectImmediately) isPrivate else state.isPrivateView,
                activeSpaceId = if (selectImmediately && !isPrivate) spaceId else state.activeSpaceId,
                activeOverlay = if (selectImmediately && state.activeOverlay == BrowserOverlay.TAB_SWITCHER) {
                    BrowserOverlay.NONE
                } else {
                    state.activeOverlay
                },
                isOmniboxFocused = false,
                findInPage = if (selectImmediately) FindInPageState() else state.findInPage,
                readerArticle = if (selectImmediately) null else state.readerArticle,
                isReaderLoading = if (selectImmediately) false else state.isReaderLoading,
                zapper = if (selectImmediately) ZapperUiState() else state.zapper
            )
        }

        if (!isPrivate) {
            persistTab(tab)
        }
        return tab
    }

    fun selectTab(tabId: String) {
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
        val accessedAt = System.currentTimeMillis()
        readerExtractJob?.cancel()
        readerExtractJob = null
        if (previousActive != null && previousActive != tabId) {
            _uiState.value.permissionPrompt?.takeIf { it.tabId == previousActive }?.let { denyPromptSilently(it) }
        }
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { if (it.id == tabId) it.copy(lastAccessedAt = accessedAt) else it },
                activeTabId = tabId,
                isPrivateView = target.isPrivate,
                activeSpaceId = if (!target.isPrivate) target.spaceId else state.activeSpaceId,
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                isOmniboxFocused = false,
                findInPage = FindInPageState(),
                zapper = ZapperUiState(),
                readerArticle = null,
                isReaderLoading = false,
                externalAppPrompt = if (previousActive != tabId) null else state.externalAppPrompt,
                httpsErrorPrompt = if (previousActive != tabId) null else state.httpsErrorPrompt,
                permissionPrompt = if (previousActive != tabId) null else state.permissionPrompt
            )
        }
        if (!target.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == tabId }?.let { persistTab(it) }
        }
        applyResolvedSettingsToTab(tabId, reloadIfModeChanged = true)
    }

    fun switchAdjacentTab(direction: Int) {
        val list = _uiState.value.visibleTabs
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

        if (state.activeTabId == tabId) {
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
        _thumbnails.update { current ->
            if (tabId in current.bitmaps) current.copy(bitmaps = current.bitmaps - tabId) else current
        }

        if (activeMediaTabId == tabId || closing.isPlayingMedia) {
            val otherPlaying = remaining.firstOrNull { it.isPlayingMedia }
            if (otherPlaying != null && state.settings.backgroundMediaEnabled) {
                activeMediaTabId = otherPlaying.id
                attachMediaController(otherPlaying.id)
                BackgroundMediaService.updateState(
                    context = app,
                    title = otherPlaying.title.ifBlank { "Media playback" },
                    host = otherPlaying.displayHost.ifBlank { "Swiftfox" },
                    isPlaying = true,
                    isPrivate = otherPlaying.isPrivate
                )
            } else if (activeMediaTabId == tabId || remaining.none { it.isPlayingMedia }) {
                activeMediaTabId = null
                BackgroundMediaService.stop(app)
            }
        }

        val clearAllPrivateState = closing.isPrivate && remaining.none { it.isPrivate }
        if (!closing.isPrivate) {
            viewModelScope.launch { db.tabDao().deleteById(tabId) }
        } else if (clearAllPrivateState) {
            pool.clearPrivateProfileData()
            privateUserscriptStorage.clear()
        }

        val visibleRemaining = if (closing.isPrivate) {
            remaining.filter { it.isPrivate }
        } else {
            remaining.filter { !it.isPrivate && it.spaceId == closing.spaceId }
        }

        val nextActiveId = when {
            state.activeTabId != tabId -> state.activeTabId
            visibleRemaining.isNotEmpty() -> visibleRemaining.last().id
            else -> null
        }

        if (state.permissionPrompt?.tabId == tabId) {
            state.permissionPrompt.let(::denyPromptSilently)
        }

        val showClosedSnackbar = !closing.isPrivate && !closing.isStartPage
        _uiState.update { current ->
            val wasActive = current.activeTabId == tabId
            current.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                privateSiteOverrides = if (clearAllPrivateState) emptyMap() else current.privateSiteOverrides,
                privateZappedRules = if (clearAllPrivateState) emptyMap() else current.privateZappedRules,
                recentlyClosedTab = if (showClosedSnackbar) closing else null,
                snackbarUndoZapper = false,
                snackbarMessage = if (showClosedSnackbar) "Closed ${closing.title}" else null,
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

        if (nextActiveId != null && nextActiveId != state.activeTabId) {
            applyResolvedSettingsToTab(nextActiveId, reloadIfModeChanged = false)
        } else if (nextActiveId == null) {
            if (closing.isPrivate) {
                // Exit Private view cleanly back to the active normal Space so activeTab is never null
                selectSpace(state.activeSpaceId)
            } else {
                createTab(spaceId = closing.spaceId, isPrivate = false, selectImmediately = true)
            }
        }
    }

    fun undoCloseTab() {
        val closed = _uiState.value.recentlyClosedTab ?: return
        _uiState.update { it.copy(recentlyClosedTab = null, snackbarMessage = null) }
        createTab(
            url = closed.url,
            isPrivate = false,
            spaceId = closed.spaceId,
            selectImmediately = true
        )
    }

    fun selectSpace(spaceId: String) {
        stopActiveZapperIfRunning()
        readerExtractJob?.cancel()
        readerExtractJob = null
        val prevTabId = _uiState.value.activeTabId
        if (prevTabId != null) {
            captureThumbnailFor(prevTabId)
            pool.getExisting(prevTabId)?.clearFindMatches()
        }
        val spaceTabs = _uiState.value.tabs.filter { !it.isPrivate && it.spaceId == spaceId }
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
                    isReaderLoading = false
                )
            }
            applyResolvedSettingsToTab(nextTabId, reloadIfModeChanged = true)
        } else {
            _uiState.update {
                it.copy(
                    isPrivateView = false,
                    activeSpaceId = spaceId,
                    findInPage = FindInPageState(),
                    zapper = ZapperUiState(),
                    readerArticle = null,
                    isReaderLoading = false
                )
            }
            createTab(spaceId = spaceId, isPrivate = false, selectImmediately = true)
        }
    }

    fun togglePrivateView(enabled: Boolean) {
        stopActiveZapperIfRunning()
        readerExtractJob?.cancel()
        readerExtractJob = null
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
                        isReaderLoading = false
                    )
                }
                applyResolvedSettingsToTab(nextTabId, reloadIfModeChanged = true)
            } else {
                createTab(isPrivate = true, selectImmediately = true)
            }
        } else {
            selectSpace(_uiState.value.activeSpaceId)
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
        }
        val remainingAfter = _uiState.value.tabs.filterNot { !it.isPrivate && it.spaceId == spaceId }
        if (activeMediaTabId in affectedIds) {
            val otherPlaying = remainingAfter.firstOrNull { it.isPlayingMedia }
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
            } else {
                activeMediaTabId = null
                BackgroundMediaService.stop(app)
            }
        }
        _thumbnails.update { current ->
            current.copy(bitmaps = current.bitmaps - affectedIds)
        }
        spaceToDelete.containerProfileName?.let { profileName ->
            pool.deleteContainerProfile(profileName)
        }
        _uiState.update { state ->
            state.copy(
                spaces = state.spaces.filterNot { it.id == spaceId },
                tabs = remainingAfter
            )
        }
        viewModelScope.launch {
            db.tabDao().deleteBySpace(spaceId)
            db.zenPinnedItemDao().deleteBySpace(spaceId)
            db.spaceDao().deleteById(spaceId)
        }
        if (wasViewingDeletedSpace) {
            selectSpace(fallback.id)
        }
        showSnackbar("Deleted Space \"${spaceToDelete.name}\"")
    }

    fun addEssential(title: String, rawUrl: String) {
        val resolved = OmniboxEngine.resolveInput(rawUrl, _uiState.value.settings)
        val host = OmniboxEngine.extractHost(resolved)
        val essential = EssentialEntity(
            id = "ess_${UUID.randomUUID().toString().take(8)}",
            title = title.trim().ifBlank { host.ifBlank { "Site" } },
            url = resolved,
            domain = host.ifBlank { resolved },
            sortOrder = _uiState.value.essentials.size
        )
        viewModelScope.launch {
            db.essentialDao().upsert(essential)
            showSnackbar("Pinned ${essential.title} to Essentials")
        }
    }

    fun removeEssential(id: String) {
        viewModelScope.launch { db.essentialDao().deleteById(id) }
    }

    fun pinCurrentTabToEssentials() {
        val tab = _uiState.value.activeTab ?: return
        if (tab.isStartPage) return
        addEssential(tab.title.take(18), tab.url)
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
        val active = _uiState.value.activeTab ?: createTab(url = url, selectImmediately = true)
        // Discard any hibernated WebView bundle so re-creating the session loads the new URL
        pool.discardHibernatedState(active.id)

        val existingSession = pool.getExisting(active.id)
        existingSession?.clearFindMatches()
        val effectiveUrl = resolveHttpsOnlyForProgrammaticLoad(active.id, url, existingSession)

        if (activeMediaTabId == active.id) {
            activeMediaTabId = null
            BackgroundMediaService.stop(app)
        }

        _uiState.update {
            it.copy(
                isOmniboxFocused = false,
                omniboxQuery = "",
                omniboxSuggestions = emptyList(),
                activeOverlay = BrowserOverlay.NONE,
                overlayBackStack = emptyList(),
                findInPage = FindInPageState(),
                readerArticle = null,
                isReaderLoading = false,
                zapper = ZapperUiState()
            )
        }
        updateTab(active.id) {
            it.copy(
                url = effectiveUrl,
                title = OmniboxEngine.extractHost(effectiveUrl).ifBlank { effectiveUrl },
                isLoading = effectiveUrl != OmniboxEngine.NEW_TAB_URL,
                progress = if (effectiveUrl == OmniboxEngine.NEW_TAB_URL) 0 else 10,
                isPlayingMedia = false,
                isPlayingVideo = false,
                blockedCount = 0,
                isReaderAvailable = false,
                darkReaderSkippedNative = false
            )
        }
        if (!active.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == active.id }?.let { persistTab(it) }
        }
        existingSession?.let { session ->
            applySettingsToSession(
                _uiState.value.tabs.first { it.id == active.id },
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
        val rawHost = uri.host?.lowercase().orEmpty()
        val canonicalHost = OmniboxEngine.normalizeHost(rawHost)
        if (rawHost.isBlank() ||
            OmniboxEngine.isLocalHostOrLan(rawHost) ||
            _uiState.value.siteOverrides[canonicalHost]?.httpsOnlyBypass == true
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
            createTab(url = targetUrl, isPrivate = false, selectImmediately = true)
        }
    }

    fun focusOmnibox(prefillCurrentUrl: Boolean = true) {
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
                isQuickControlsOpen = false,
                isSiteRulesOpen = false
            )
        }
        onOmniboxQueryChanged(initialText)
    }

    fun dismissOmnibox() {
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(isOmniboxFocused = false, omniboxQuery = "", omniboxSuggestions = emptyList())
        }
    }

    fun onOmniboxQueryChanged(query: String) {
        _uiState.update { it.copy(omniboxQuery = query) }
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
                _uiState.update { it.copy(omniboxSuggestions = results) }
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

            db.bookmarkDao().search(trimmed, limit = 3).forEach { bm ->
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
            if (!inPrivate) {
                db.historyDao().search(trimmed, limit = 4).forEach { h ->
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
                results += OmniboxSuggestion(
                    id = "search_self_$trimmed",
                    title = trimmed,
                    subtitle = "Search with ${_uiState.value.settings.searchEngine.label}",
                    targetUrl = resolvedDirect,
                    kind = SuggestionKind.SEARCH
                )
            }

            _uiState.update { it.copy(omniboxSuggestions = results) }

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
            session.reload()
        }
    }

    fun reloadActiveTab() {
        val active = _uiState.value.activeTab ?: return
        pool.getExisting(active.id)?.reload()
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

    fun toggleQuickControls(open: Boolean) {
        _uiState.update { it.copy(isQuickControlsOpen = open) }
    }

    fun toggleSiteRulesSheet(open: Boolean) {
        _uiState.update {
            it.copy(
                isSiteRulesOpen = open,
                isQuickControlsOpen = if (open) false else it.isQuickControlsOpen
            )
        }
    }

    fun setOverlay(overlay: BrowserOverlay) {
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
                        BrowserOverlay.BOOKMARKS
                    ) -> current.overlayBackStack + BrowserOverlay.SETTINGS
                current.activeOverlay == BrowserOverlay.SYNC &&
                    overlay == BrowserOverlay.SYNCED_TABS ->
                    current.overlayBackStack + BrowserOverlay.SYNC
                current.activeOverlay == BrowserOverlay.TAB_SWITCHER &&
                    overlay == BrowserOverlay.SYNCED_TABS ->
                    current.overlayBackStack + BrowserOverlay.TAB_SWITCHER
                else -> emptyList()
            }
            current.copy(
                activeOverlay = overlay,
                overlayBackStack = nextStack,
                isQuickControlsOpen = false,
                isSiteRulesOpen = false,
                isOmniboxFocused = false
            )
        }
    }

    fun navigateBackFromOverlay() {
        _uiState.update { current ->
            val prev = current.overlayBackStack.lastOrNull() ?: BrowserOverlay.NONE
            val remaining = if (current.overlayBackStack.isNotEmpty()) {
                current.overlayBackStack.dropLast(1)
            } else {
                emptyList()
            }
            current.copy(
                activeOverlay = prev,
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
        viewModelScope.launch {
            val existingDb = db.siteOverrideDao().getByHost(normalizedHost) ?: SiteOverrideEntity(host = normalizedHost)
            val saved = transform(existingDb)
            db.siteOverrideDao().upsert(saved)
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
                    val darkState = DarkReaderConfig.resolve(
                        settings = _uiState.value.settings,
                        siteOverride = updated,
                        isChromeDark = isChromeDark()
                    )
                    pool.getExisting(active.id)?.applyWebDarkMode(darkState, app.darkReaderInjector)
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
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                val darkState = DarkReaderConfig.resolve(_uiState.value.settings, updated, isChromeDark())
                pool.getExisting(active.id)?.applyWebDarkMode(darkState, app.darkReaderInjector)
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
        val normalizedUa = userAgent?.trim()?.takeIf { it.isNotEmpty() }
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
        val session = pool.getExisting(active.id) ?: return
        _uiState.update {
            it.copy(
                isQuickControlsOpen = false,
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
            privateUserscriptStorage.remove(id)
            pool.clearAllDocumentStartScripts()
            showSnackbar("Deleted userscript")
        }
    }

    fun triggerImportUserscriptFile() {
        onImportUserscriptFileBridge?.invoke()
    }

    // Reader Mode actions
    fun openReaderMode() {
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) {
            showSnackbar("Open an article to enter Reader Mode")
            return
        }
        val session = pool.getExisting(active.id) ?: return
        val expectedTabId = active.id
        val expectedUrl = active.url
        val expectedOrigin = ScriptInjector.extractOrigin(expectedUrl)
        val readerKey = session.tokens.readerKey
        val readerKeyLit = viewModelJson.encodeToString(readerKey)
        readerExtractJob?.cancel()
        readerExtractJob = null
        _uiState.update { it.copy(isQuickControlsOpen = false, isReaderLoading = true) }

        fun handleExtracted(rawResult: String?) {
            readerExtractJob?.cancel()
            readerExtractJob = viewModelScope.launch(Dispatchers.Default) {
                val article = app.readerExtractor.parseExtractionResult(rawResult)
                withContext(Dispatchers.Main) {
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
                                snackbarUndoZapper = false,
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
            session.evaluateJavascript(script) { rawResult ->
                handleExtracted(rawResult)
            }
        }
    }

    fun cancelReaderLoading() {
        readerExtractJob?.cancel()
        readerExtractJob = null
        _uiState.update { it.copy(isReaderLoading = false) }
    }

    fun closeReaderMode() {
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
        viewModelScope.launch {
            db.historyDao().clearAll()
            app.mozillaSyncCoordinator.mirrorHistoryCleared()
            showSnackbar("Browsing history cleared")
        }
    }

    fun deleteHistoryEntry(url: String) {
        viewModelScope.launch {
            db.historyDao().deleteByUrl(url)
            app.mozillaSyncCoordinator.mirrorHistoryDeleted(url)
        }
    }

    fun clearAllBrowsingData() {
        viewModelScope.launch {
            db.historyDao().clearAll()
            app.mozillaSyncCoordinator.mirrorHistoryCleared()
            val containerProfiles = _uiState.value.spaces.mapNotNull { it.containerProfileName }
            pool.clearAllProfilesData(containerProfiles)
            showSnackbar("Cleared history, cookies, and cache")
        }
    }

    fun openFindInPage() {
        _uiState.update {
            it.copy(
                isQuickControlsOpen = false,
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
        if (isPrivate) {
            _uiState.update { it.copy(privateSiteOverrides = it.privateSiteOverrides - normalizedHost) }
            _uiState.value.tabs
                .filter { it.isPrivate && OmniboxEngine.normalizeHost(it.displayHost) == normalizedHost }
                .forEach { matching ->
                    updateTab(matching.id) { it.copy(siteModeOverride = null) }
                }
            val active = _uiState.value.activeTab
            if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
            }
            showSnackbar("Reset site overrides for $normalizedHost")
            return
        }
        _uiState.update { it.copy(siteOverrides = it.siteOverrides - normalizedHost) }
        viewModelScope.launch {
            db.siteOverrideDao().deleteByHost(host)
            if (normalizedHost != host) {
                db.siteOverrideDao().deleteByHost(normalizedHost)
            }
            withContext(Dispatchers.Main) {
                // Also clear any tab-level siteModeOverride on matching tabs so posture defaults resume
                _uiState.value.tabs
                    .filter { !it.isPrivate && OmniboxEngine.normalizeHost(it.displayHost) == normalizedHost }
                    .forEach { matching ->
                        updateTab(matching.id) { it.copy(siteModeOverride = null) }
                        _uiState.value.tabs.firstOrNull { it.id == matching.id }?.let { persistTab(it) }
                    }
                val active = _uiState.value.activeTab
                if (active != null && OmniboxEngine.normalizeHost(active.displayHost) == normalizedHost) {
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
                showSnackbar("Reset site overrides for $normalizedHost")
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

    fun cycleCurrentPostureToolbarPlacement() {
        val posture = _uiState.value.windowState.posture
        val current = _uiState.value.activeProfile.toolbarPlacement
        val choices = if (posture == DevicePosture.UNFOLDED) {
            listOf(ToolbarPlacement.TOP, ToolbarPlacement.BOTTOM, ToolbarPlacement.SPLIT, ToolbarPlacement.SIDE_RAIL)
        } else {
            listOf(ToolbarPlacement.SPLIT, ToolbarPlacement.BOTTOM, ToolbarPlacement.TOP)
        }
        val next = choices[(choices.indexOf(current) + 1) % choices.size]
        setToolbarPlacement(posture, next)
        showSnackbar("${posture.label} toolbar: ${next.label}")
    }

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

    fun sendActiveTabToZenSpace(asEssential: Boolean = false) {
        val activeTab = _uiState.value.activeTab ?: return
        if (activeTab.isPrivate || activeTab.isStartPage) return
        val space = _uiState.value.activeSpace
        val spaceGuid = space?.syncGuid
        if (spaceGuid.isNullOrBlank()) {
            showSnackbar("Switch to a synced Zen Space first")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isQuickControlsOpen = false) }
            app.mozillaSyncCoordinator.sendTabToZenSpace(
                spaceSyncGuid = spaceGuid,
                url = activeTab.url,
                title = activeTab.title.ifBlank { activeTab.displayHost },
                asEssential = asEssential
            ).onSuccess {
                val message = if (asEssential) {
                    "Sent to Zen Essentials"
                } else {
                    "Sent to Zen Space \"${space.name}\""
                }
                showSnackbar(message)
                app.mozillaSyncCoordinator.syncNow(SyncReason.User)
            }.onFailure { err ->
                showSnackbar("Could not send to Zen: ${err.message ?: "error"}")
            }
        }
    }

    fun setSyncBookmarksEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSyncBookmarksEnabled(enabled)
        val s = _uiState.value.settings
        app.mozillaSyncCoordinator.updateEnginePreferences(
            bookmarks = enabled,
            history = s.syncHistoryEnabled,
            tabs = s.syncTabsEnabled
        )
    }

    fun setSyncHistoryEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSyncHistoryEnabled(enabled)
        val s = _uiState.value.settings
        app.mozillaSyncCoordinator.updateEnginePreferences(
            bookmarks = s.syncBookmarksEnabled,
            history = enabled,
            tabs = s.syncTabsEnabled
        )
    }

    fun setSyncTabsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSyncTabsEnabled(enabled)
        val s = _uiState.value.settings
        app.mozillaSyncCoordinator.updateEnginePreferences(
            bookmarks = s.syncBookmarksEnabled,
            history = s.syncHistoryEnabled,
            tabs = enabled
        )
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
        _uiState.update {
            it.copy(
                snackbarMessage = message,
                snackbarId = snackbarCounter.incrementAndGet(),
                snackbarUndoZapper = false,
                recentlyClosedTab = null
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update {
            it.copy(
                snackbarMessage = null,
                snackbarUndoZapper = false,
                recentlyClosedTab = null
            )
        }
    }

    private fun isChromeDark(): Boolean = when (_uiState.value.settings.themeMode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.SYSTEM -> onSystemDarkThemeProvider?.invoke() ?: _uiState.value.isSystemDark
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
            .filter { !it.isPrivate && it.spaceId == tab.spaceId }
            .indexOfFirst { it.id == tab.id }
            .coerceAtLeast(0)
        viewModelScope.launch {
            db.tabDao().upsert(
                TabEntity(
                    id = tab.id,
                    spaceId = tab.spaceId,
                    title = tab.title,
                    url = tab.url,
                    isPrivate = false,
                    isPinned = false,
                    siteModeOverride = tab.siteModeOverride?.name,
                    lastAccessedAt = tab.lastAccessedAt,
                    sortOrder = orderIdx
                )
            )
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
            val resolvedTitle = title.ifBlank { OmniboxEngine.extractHost(url) }
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
            try {
                delay(150)
                while (true) {
                    val snapshot = HashMap<String, Int>()
                    val keys = pendingBlockedDeltaByTab.keys.toList()
                    for (id in keys) {
                        val delta = pendingBlockedDeltaByTab[id]?.getAndSet(0) ?: 0
                        if (delta > 0) snapshot[id] = delta
                    }
                    if (snapshot.isEmpty()) break
                    _uiState.update { current ->
                        current.copy(
                            tabs = current.tabs.map { tab ->
                                val delta = snapshot[tab.id]
                                if (delta != null) tab.copy(blockedCount = tab.blockedCount + delta) else tab
                            }
                        )
                    }
                    blockedCountFlushScheduled.set(false)
                    val hasMore = pendingBlockedDeltaByTab.values.any { it.get() > 0 }
                    if (!hasMore || !blockedCountFlushScheduled.compareAndSet(false, true)) break
                    delay(150)
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
            if (liveUrl.isNotBlank() && ScriptInjector.extractOrigin(liveUrl) != expectedOrigin) {
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
                val readable = raw?.trim().equals("true", ignoreCase = true)
                updateTab(tabId) { it.copy(isReaderAvailable = readable) }
            }
        }
    }

    private fun buildAppendGenericCosmeticCssScript(css: String): String {
        val cssLit = viewModelJson.encodeToString(css)
        return """
            (function() {
                var css = $cssLit;
                if (!css) return;
                var id = 'swiftfox-cosmetic-generic';
                var el = document.getElementById(id);
                if (!el) {
                    var target = document.head || document.documentElement;
                    if (!target) return;
                    el = document.createElement('style');
                    el.id = id;
                    el.type = 'text/css';
                    target.appendChild(el);
                }
                if (el.textContent && el.textContent.indexOf(css) !== -1) return;
                el.textContent = el.textContent ? (el.textContent + '\n' + css) : css;
            })();
        """.trimIndent()
    }

    private fun applyResolvedSettingsToTab(tabId: String, reloadIfModeChanged: Boolean) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val session = pool.getExisting(tabId) ?: return
        applySettingsToSession(tab, session, reloadIfModeChanged)
    }

    private fun syncAllWarmSessionsWithSettings(
        reloadActiveOnSiteModeChange: Boolean,
        updateSiteOverrides: Boolean = true,
        updateWebDark: Boolean = true,
        updateBgMedia: Boolean = true
    ) {
        val state = _uiState.value
        state.tabs.forEach { tab ->
            val session = pool.getExisting(tab.id) ?: return@forEach
            applySettingsToSession(
                tab = tab,
                session = session,
                reloadIfModeChanged = reloadActiveOnSiteModeChange && tab.id == state.activeTabId,
                updateSiteOverrides = updateSiteOverrides,
                updateWebDark = updateWebDark,
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
        updateBgMedia: Boolean = true
    ) {
        val state = _uiState.value
        val normalizedHost = OmniboxEngine.normalizeHost(tab.displayHost)
        val siteOverrideEntity = if (normalizedHost.isBlank()) null else effectiveSiteOverride(normalizedHost, tab.isPrivate)

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
            session.applySiteOverrides(
                mode = resolvedMode,
                customUserAgent = siteOverrideEntity?.customUserAgent,
                javascriptEnabled = siteOverrideEntity?.javascriptEnabled ?: true,
                cookiesEnabled = siteOverrideEntity?.cookiesEnabled ?: true,
                blockThirdParty = state.settings.blockThirdPartyCookies,
                reloadIfChanged = reloadIfModeChanged,
                applyProfileCookiePolicy = (tab.id == state.activeTabId)
            )
        }

        if (updateWebDark) {
            val darkState = DarkReaderConfig.resolve(
                settings = state.settings,
                siteOverride = siteOverrideEntity,
                isChromeDark = isChromeDark()
            )
            session.applyWebDarkMode(darkState, app.darkReaderInjector)
        }

        if (updateBgMedia) {
            session.applyBackgroundPlayback(state.settings.backgroundMediaEnabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        BackgroundMediaService.activeController = null
        suggestionJob?.cancel()
        readerExtractJob?.cancel()
        spaCheckJobByTab.values.forEach { it.cancel() }
        spaCheckJobByTab.clear()
    }
}