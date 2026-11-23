package app.overbay.swiftfox.ui.browser

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.db.TabEntity
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
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.zapper.ZapperUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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
    var onShowFullscreenViewBridge: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null
    var onHideFullscreenViewBridge: (() -> Unit)? = null
    var onSystemDarkThemeProvider: (() -> Boolean)? = null
    var onImportUserscriptFileBridge: (() -> Unit)? = null

    private var suggestionJob: Job? = null
    private var initialTabsLoaded = false

    val sessionCallbacks: SessionHostCallbacks = object : SessionHostCallbacks {
        override fun getHttpsOnlyEnabled(): Boolean = _uiState.value.settings.httpsOnlyMode

        override fun isHttpsBypassedForHost(host: String): Boolean =
            _uiState.value.siteOverrides[host.lowercase()]?.httpsOnlyBypass == true

        override fun getExternalAppPolicy(host: String): ExternalAppLinkPolicy {
            val raw = _uiState.value.siteOverrides[host.lowercase()]?.externalAppPolicyOverride
            return raw?.let { runCatching { ExternalAppLinkPolicy.valueOf(it) }.getOrNull() }
                ?: _uiState.value.settings.externalAppLinkPolicy
        }

        override fun shouldBlockNetworkRequest(
            tabId: String,
            requestUrl: String,
            pageUrl: String,
            headers: Map<String, String>
        ): Boolean {
            val state = _uiState.value
            val pageHost = OmniboxEngine.extractHost(pageUrl).lowercase()
            val siteDisabled = state.siteOverrides[pageHost]?.adblockDisabled == true
            val resourceType = app.adblockEngine.inferResourceType(requestUrl, headers)
            val blocked = app.adblockEngine.shouldBlock(
                requestUrl = requestUrl,
                pageUrl = pageUrl,
                resourceType = resourceType,
                globalEnabled = state.settings.adblockEnabled,
                siteDisabled = siteDisabled
            )
            if (blocked) {
                _uiState.update { current ->
                    current.copy(
                        tabs = current.tabs.map { tab ->
                            if (tab.id == tabId) tab.copy(blockedCount = tab.blockedCount + 1) else tab
                        }
                    )
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
            val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
            val isSameUrl = currentTab?.url == url
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url,
                    isLoading = true,
                    progress = 12,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    blockedCount = if (isSameUrl) tab.blockedCount else 0,
                    isReaderAvailable = false,
                    darkReaderSkippedNative = false
                )
            }
            if (_uiState.value.activeTabId == tabId) {
                if (_uiState.value.zapper.isActive) {
                    _uiState.update { it.copy(zapper = ZapperUiState()) }
                }
                if (_uiState.value.readerArticle != null) {
                    _uiState.update { it.copy(readerArticle = null) }
                }
            }
            applyResolvedSettingsToTab(tabId, reloadIfModeChanged = false)
        }

        override fun onInjectOnPageStarted(tabId: String, url: String) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) return
            val session = pool.getExisting(tabId) ?: return
            val state = _uiState.value
            val host = OmniboxEngine.extractHost(url).lowercase()
            val siteOverride = state.siteOverrides[host]
            val siteAdblockDisabled = siteOverride?.adblockDisabled == true
            val cosmetic = app.adblockEngine.cosmeticForUrl(
                url = url,
                globalEnabled = state.settings.adblockEnabled,
                cosmeticEnabled = state.settings.adblockCosmeticEnabled,
                siteDisabled = siteAdblockDisabled
            )
            val darkState = DarkReaderConfig.resolve(
                settings = state.settings,
                siteOverride = siteOverride,
                isChromeDark = isChromeDark()
            )
            viewModelScope.launch(Dispatchers.IO) {
                val zappedRules = if (host.isNotBlank()) db.zappedRuleDao().getEnabledForHost(host) else emptyList()
                val docStartUserscripts = if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_START)
                } else {
                    emptyList()
                }
                val docStartJs = if (docStartUserscripts.isNotEmpty()) {
                    app.userscriptBridge.buildInjectionScript(docStartUserscripts, UserscriptRunAt.DOCUMENT_START)
                } else {
                    ""
                }
                val zappedCss = app.zapperController.buildCssForRules(zappedRules)
                withContext(Dispatchers.Main) {
                    session.updateDocumentStartScript(cosmetic.css, zappedCss, docStartJs)
                    ScriptInjector.injectOnPageStarted(
                        webView = session.webView,
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
                recordHistory(updatedTab.url, updatedTab.title)
            }
            viewModelScope.launch {
                delay(250)
                captureThumbnailFor(tabId)
            }
        }

        override fun onInjectOnPageFinished(tabId: String, url: String) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) return
            val session = pool.getExisting(tabId) ?: return
            val state = _uiState.value
            val host = OmniboxEngine.extractHost(url).lowercase()
            val siteOverride = state.siteOverrides[host]
            val siteAdblockDisabled = siteOverride?.adblockDisabled == true
            val cosmetic = app.adblockEngine.cosmeticForUrl(
                url = url,
                globalEnabled = state.settings.adblockEnabled,
                cosmeticEnabled = state.settings.adblockCosmeticEnabled,
                siteDisabled = siteAdblockDisabled
            )
            val darkState = DarkReaderConfig.resolve(
                settings = state.settings,
                siteOverride = siteOverride,
                isChromeDark = isChromeDark()
            )
            viewModelScope.launch(Dispatchers.IO) {
                val zappedRules = if (host.isNotBlank()) db.zappedRuleDao().getEnabledForHost(host) else emptyList()
                val docEndUserscripts = if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_END)
                } else {
                    emptyList()
                }
                val docIdleUserscripts = if (state.settings.userscriptsEnabled) {
                    app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_IDLE)
                } else {
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    ScriptInjector.injectOnPageFinished(
                        webView = session.webView,
                        cosmetic = cosmetic,
                        zappedRules = zappedRules,
                        zapperController = app.zapperController,
                        darkState = darkState,
                        darkReaderInjector = app.darkReaderInjector,
                        docEndUserscripts = docEndUserscripts,
                        docIdleUserscripts = docIdleUserscripts,
                        userscriptBridge = app.userscriptBridge
                    )
                    session.evaluateJavascript(app.readerExtractor.buildCheckReadableScript()) { raw ->
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
            updateTab(tabId) { tab ->
                tab.copy(
                    url = url,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }
        }

        override fun onProgressChanged(tabId: String, progress: Int) {
            updateTab(tabId) { tab ->
                tab.copy(progress = progress, isLoading = progress in 1..99)
            }
        }

        override fun onTitleChanged(tabId: String, title: String) {
            updateTab(tabId) { tab -> tab.copy(title = title) }
        }

        override fun onFaviconReceived(tabId: String, icon: Bitmap?) {
            // Reserved for favicon caching in M3
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
            val saved = _uiState.value.siteOverrides[prompt.host]
            if (prompt.isGeolocation && saved?.allowLocation != null) {
                prompt.geoCallback?.invoke(prompt.geoOrigin, saved.allowLocation, false)
                return
            }
            if (!prompt.isGeolocation && saved != null) {
                val allApproved = prompt.resources.all { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> saved.allowCamera == true
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> saved.allowMic == true
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> saved.allowProtectedMedia == true
                        else -> false
                    }
                }
                val anyDenied = prompt.resources.any { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> saved.allowCamera == false
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> saved.allowMic == false
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> saved.allowProtectedMedia == false
                        else -> false
                    }
                }
                if (allApproved) {
                    prompt.webRequest?.grant(prompt.resources.toTypedArray())
                    return
                }
                if (anyDenied) {
                    prompt.webRequest?.deny()
                    return
                }
            }
            _uiState.update { it.copy(permissionPrompt = prompt) }
        }

        override fun onOpenNewWindow(parentTabId: String, resultMsg: Message?): Boolean {
            val parent = _uiState.value.tabs.firstOrNull { it.id == parentTabId }
            val newTab = createTab(
                url = OmniboxEngine.NEW_TAB_URL,
                isPrivate = parent?.isPrivate ?: _uiState.value.isPrivateView,
                spaceId = parent?.spaceId ?: _uiState.value.activeSpaceId,
                selectImmediately = true
            )
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            if (transport != null) {
                val session = ensureSession(newTab, app)
                transport.webView = session.webView
                resultMsg.sendToTarget()
                return true
            }
            return false
        }

        override fun onExternalAppPrompt(prompt: ExternalAppPrompt) {
            _uiState.update { it.copy(externalAppPrompt = prompt) }
        }

        override fun onLaunchExternalIntent(intent: Intent): Boolean {
            return onLaunchExternalIntentBridge?.invoke(intent) ?: false
        }

        override fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt) {
            _uiState.update { it.copy(httpsErrorPrompt = prompt) }
        }

        override fun onDownloadRequested(
            url: String,
            userAgent: String,
            contentDisposition: String?,
            mimeType: String?
        ) {
            viewModelScope.launch {
                val result = app.downloadCoordinator.enqueueHttpDownload(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType
                )
                result.onSuccess { rec ->
                    showSnackbar("Downloading ${rec.fileName}")
                }.onFailure { err ->
                    showSnackbar("Download failed: ${err.message ?: "unknown error"}")
                }
            }
        }

        override fun onBlobDownloadReady(
            fileName: String,
            mimeType: String,
            base64Data: String,
            sourceUrl: String
        ) {
            viewModelScope.launch {
                val result = app.downloadCoordinator.saveBase64Blob(
                    fileName = fileName,
                    mimeType = mimeType,
                    base64Payload = base64Data,
                    sourceUrl = sourceUrl
                )
                result.onSuccess { rec ->
                    showSnackbar("Saved ${rec.fileName} to Downloads")
                }.onFailure { err ->
                    showSnackbar("Could not save file: ${err.message ?: "error"}")
                }
            }
        }

        override fun onMediaPlaybackStateChanged(tabId: String, isPlaying: Boolean, title: String) {
            viewModelScope.launch(Dispatchers.Main) {
                updateTab(tabId) { it.copy(isPlayingMedia = isPlaying) }
                val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
                if (isPlaying && _uiState.value.settings.backgroundMediaEnabled) {
                    BackgroundMediaService.activeController = BackgroundMediaService.MediaCommandController { cmd ->
                        viewModelScope.launch(Dispatchers.Main) {
                            val session = pool.getExisting(tabId)
                            when (cmd) {
                                BackgroundMediaService.MediaCommand.PLAY -> session?.setMediaPaused(false)
                                BackgroundMediaService.MediaCommand.PAUSE -> session?.setMediaPaused(true)
                                BackgroundMediaService.MediaCommand.STOP -> {
                                    session?.setMediaPaused(true)
                                    updateTab(tabId) { it.copy(isPlayingMedia = false) }
                                }
                            }
                        }
                    }
                    BackgroundMediaService.updateState(
                        context = app,
                        title = title.ifBlank { tab?.title ?: "Media playback" },
                        host = tab?.displayHost.orEmpty().ifBlank { "Swiftfox" },
                        isPlaying = true
                    )
                } else if (!isPlaying && _uiState.value.tabs.none { it.isPlayingMedia }) {
                    BackgroundMediaService.stop(app)
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

        override fun onScrollTopChanged(tabId: String, atTop: Boolean) {
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { it.copy(isWebViewScrolledToTop = atTop) }
            }
        }

        override fun onZapperSelectionChanged(tabId: String, json: String) {
            val event = app.zapperController.parseSelectionEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                if (_uiState.value.activeTabId != tabId) return@launch
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
                if (_uiState.value.activeTabId != tabId) return@launch
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
                        snackbarUndoZapper = true,
                        recentlyClosedTab = null
                    )
                }
            }
        }

        override fun onZapperUndoTemporaryZap(tabId: String, json: String) {
            val count = app.zapperController.parseUndoCount(json)
            viewModelScope.launch(Dispatchers.Main) {
                if (_uiState.value.activeTabId != tabId) return@launch
                _uiState.update { state ->
                    state.copy(
                        zapper = state.zapper.copy(temporaryZapCount = count),
                        snackbarMessage = "Restored element",
                        snackbarUndoZapper = false
                    )
                }
            }
        }

        override fun onZapperPermanentRule(tabId: String, json: String) {
            val event = app.zapperController.parsePermanentRuleEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return@launch
                val host = tab.displayHost.lowercase()
                if (host.isBlank()) return@launch
                val rule = ZappedRuleEntity(
                    id = "zap_${UUID.randomUUID().toString().take(12)}",
                    host = host,
                    selector = event.selector,
                    label = event.label,
                    enabled = true
                )
                withContext(Dispatchers.IO) {
                    db.zappedRuleDao().upsert(rule)
                }
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

        override fun onUserscriptSetValue(scriptId: String, key: String, valueJson: String) {
            app.userscriptBridge.handleSetValue(scriptId, key, valueJson)
        }

        override fun onUserscriptDeleteValue(scriptId: String, key: String) {
            app.userscriptBridge.handleDeleteValue(scriptId, key)
        }

        override fun onUserscriptXmlHttpRequest(
            tabId: String,
            scriptId: String,
            reqId: String,
            requestJson: String
        ) {
            app.userscriptBridge.handleXmlHttpRequest(scriptId, reqId, requestJson) { callbackJs ->
                pool.getExisting(tabId)?.evaluateJavascript(callbackJs)
            }
        }
    }

    init {
        observePersistence()
    }

    private fun observePersistence() {
        viewModelScope.launch {
            prefs.settingsFlow.collectLatest { newSettings ->
                val oldSettings = _uiState.value.settings
                _uiState.update { it.copy(settings = newSettings) }
                if (oldSettings != newSettings) {
                    syncAllWarmSessionsWithSettings(reloadActiveOnSiteModeChange = false)
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
                        val resolvedSpaceId = if (spaces.any { it.id == state.activeSpaceId }) {
                            state.activeSpaceId
                        } else {
                            spaces.first().id
                        }
                        state.copy(spaces = spaces, activeSpaceId = resolvedSpaceId)
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
            db.siteOverrideDao().observeAll().collectLatest { overrides ->
                _uiState.update { it.copy(siteOverrides = overrides.associateBy { o -> o.host }) }
                val activeId = _uiState.value.activeTabId
                if (activeId != null) {
                    applyResolvedSettingsToTab(activeId, reloadIfModeChanged = false)
                }
            }
        }

        viewModelScope.launch {
            db.seedDefaultsIfEmpty()
            if (!initialTabsLoaded) {
                initialTabsLoaded = true
                val savedTabs = db.tabDao().getNormalTabs()
                if (savedTabs.isEmpty()) {
                    createTab(
                        url = OmniboxEngine.NEW_TAB_URL,
                        isPrivate = false,
                        spaceId = SwiftfoxDatabase.DEFAULT_SPACE_ID,
                        selectImmediately = true
                    )
                } else {
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
                            isPinned = entity.isPinned,
                            siteModeOverride = tabOverride,
                            resolvedSiteMode = SiteModeResolver.resolve(tabOverride, siteOverride, profile),
                            lastAccessedAt = entity.lastAccessedAt
                        )
                    }
                    val first = restored.maxByOrNull { it.lastAccessedAt } ?: restored.first()
                    _uiState.update {
                        it.copy(
                            tabs = restored,
                            activeTabId = first.id,
                            activeSpaceId = first.spaceId
                        )
                    }
                }
            }
        }
    }

    fun onFoldableWindowStateChanged(newState: FoldableWindowState) {
        val previous = _uiState.value.windowState
        if (previous == newState) return

        val postureChanged = previous.posture != newState.posture
        _uiState.update { it.copy(windowState = newState) }

        if (postureChanged) {
            val activeTab = _uiState.value.activeTab ?: return
            val newProfile = _uiState.value.settings.profileFor(newState.posture)
            val siteOverride = SiteModeResolver.parseOrNull(
                _uiState.value.siteOverrides[activeTab.displayHost]?.siteModeOverride
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
            pool.getExisting(activeTab.id)?.applySiteMode(resolved, reloadIfChanged = shouldReload)
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
        if (!existed && !tab.isStartPage && session.webView.url.isNullOrBlank()) {
            session.loadUrl(tab.url)
        }
        return session
    }

    fun createTab(
        url: String = OmniboxEngine.NEW_TAB_URL,
        isPrivate: Boolean = _uiState.value.isPrivateView,
        spaceId: String = _uiState.value.activeSpaceId,
        selectImmediately: Boolean = true
    ): BrowserTabUi {
        val currentState = _uiState.value
        val host = OmniboxEngine.extractHost(url)
        val siteOverride = SiteModeResolver.parseOrNull(currentState.siteOverrides[host]?.siteModeOverride)
        val resolvedMode = SiteModeResolver.resolve(null, siteOverride, currentState.activeProfile)

        val tab = BrowserTabUi(
            id = UUID.randomUUID().toString(),
            spaceId = spaceId,
            title = if (url == OmniboxEngine.NEW_TAB_URL) "New Tab" else host.ifBlank { url },
            url = url,
            isPrivate = isPrivate,
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
                readerArticle = if (selectImmediately) null else state.readerArticle,
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
            if (_uiState.value.zapper.isActive) {
                pool.getExisting(previousActive)?.evaluateJavascript(app.zapperController.buildStopScript())
            }
        }
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update { state ->
            state.copy(
                activeTabId = tabId,
                isPrivateView = target.isPrivate,
                activeSpaceId = if (!target.isPrivate) target.spaceId else state.activeSpaceId,
                activeOverlay = BrowserOverlay.NONE,
                isOmniboxFocused = false,
                findInPage = FindInPageState(),
                zapper = ZapperUiState(),
                readerArticle = null
            )
        }
        applyResolvedSettingsToTab(tabId, reloadIfModeChanged = false)
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

        pool.remove(tabId)
        if (!closing.isPrivate) {
            viewModelScope.launch { db.tabDao().deleteById(tabId) }
        } else if (remaining.none { it.isPrivate }) {
            pool.clearPrivateProfileData()
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

        _uiState.update {
            it.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                recentlyClosedTab = if (!closing.isPrivate && !closing.isStartPage) closing else null,
                snackbarUndoZapper = false,
                snackbarMessage = if (!closing.isPrivate && !closing.isStartPage) {
                    "Closed ${closing.title}"
                } else {
                    null
                },
                zapper = if (state.activeTabId == tabId) ZapperUiState() else state.zapper,
                readerArticle = if (state.activeTabId == tabId) null else state.readerArticle
            )
        }

        if (nextActiveId == null && !closing.isPrivate) {
            createTab(spaceId = closing.spaceId, isPrivate = false, selectImmediately = true)
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
        _uiState.value.activeTabId?.let { captureThumbnailFor(it) }
        val spaceTabs = _uiState.value.tabs.filter { !it.isPrivate && it.spaceId == spaceId }
        if (spaceTabs.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isPrivateView = false,
                    activeSpaceId = spaceId,
                    activeTabId = spaceTabs.maxByOrNull { t -> t.lastAccessedAt }?.id ?: spaceTabs.first().id,
                    zapper = ZapperUiState(),
                    readerArticle = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isPrivateView = false,
                    activeSpaceId = spaceId,
                    zapper = ZapperUiState(),
                    readerArticle = null
                )
            }
            createTab(spaceId = spaceId, isPrivate = false, selectImmediately = true)
        }
    }

    fun togglePrivateView(enabled: Boolean) {
        _uiState.value.activeTabId?.let { captureThumbnailFor(it) }
        if (enabled) {
            val privateTabs = _uiState.value.tabs.filter { it.isPrivate }
            if (privateTabs.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isPrivateView = true,
                        activeTabId = privateTabs.last().id,
                        zapper = ZapperUiState(),
                        readerArticle = null
                    )
                }
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
        viewModelScope.launch {
            db.spaceDao().upsert(space)
            selectSpace(id)
        }
    }

    fun deleteSpace(spaceId: String) {
        if (_uiState.value.spaces.size <= 1) return
        val fallback = _uiState.value.spaces.first { it.id != spaceId }
        val affectedTabs = _uiState.value.tabs.filter { !it.isPrivate && it.spaceId == spaceId }
        affectedTabs.forEach { pool.remove(it.id) }
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.filterNot { !it.isPrivate && it.spaceId == spaceId }
            )
        }
        viewModelScope.launch {
            db.tabDao().deleteBySpace(spaceId)
            db.spaceDao().deleteById(spaceId)
        }
        selectSpace(fallback.id)
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
        val active = _uiState.value.activeTab ?: createTab(url = url, selectImmediately = true)
        _uiState.update {
            it.copy(
                isOmniboxFocused = false,
                omniboxQuery = "",
                omniboxSuggestions = emptyList(),
                activeOverlay = BrowserOverlay.NONE,
                readerArticle = null,
                zapper = ZapperUiState()
            )
        }
        updateTab(active.id) {
            it.copy(
                url = url,
                title = OmniboxEngine.extractHost(url).ifBlank { url },
                isLoading = url != OmniboxEngine.NEW_TAB_URL,
                progress = if (url == OmniboxEngine.NEW_TAB_URL) 0 else 10,
                blockedCount = 0,
                isReaderAvailable = false,
                darkReaderSkippedNative = false
            )
        }
        if (!active.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == active.id }?.let { persistTab(it) }
        }
        pool.getExisting(active.id)?.let { session ->
            applySettingsToSession(
                _uiState.value.tabs.first { it.id == active.id },
                session,
                reloadIfModeChanged = false
            )
            session.loadUrl(url)
        }
    }

    fun openExternalIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.dataString ?: return
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    createTab(url = uri, isPrivate = false, selectImmediately = true)
                }
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra("query").orEmpty()
                if (query.isNotBlank()) {
                    val resolved = OmniboxEngine.resolveInput(query, _uiState.value.settings)
                    createTab(url = resolved, isPrivate = false, selectImmediately = true)
                }
            }
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
                _uiState.update { it.copy(omniboxSuggestions = results) }
                return@launch
            }

            val bang = OmniboxEngine.detectBang(trimmed)
            if (bang != null) {
                val target = if (bang.query.isNotBlank()) {
                    OmniboxEngine.resolveInput(trimmed, _uiState.value.settings)
                } else {
                    bang.bang.urlTemplate.replace("%s", "")
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

            _uiState.value.tabs
                .filter { !it.isStartPage && (it.title.contains(trimmed, true) || it.url.contains(trimmed, true)) }
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

            delay(180)
            val live = OmniboxEngine.fetchLiveSuggestions(trimmed, _uiState.value.settings)
            if (live.isNotEmpty()) {
                val merged = results.toMutableList()
                live.forEach { suggestionText ->
                    val url = OmniboxEngine.resolveInput(suggestionText, _uiState.value.settings)
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
        _uiState.update {
            it.copy(
                activeOverlay = overlay,
                isQuickControlsOpen = false,
                isSiteRulesOpen = false,
                isOmniboxFocused = false
            )
        }
    }

    fun toggleUnfoldedRailExpanded() {
        _uiState.update { it.copy(isUnfoldedRailExpanded = !it.isUnfoldedRailExpanded) }
    }

    fun toggleActiveTabSiteMode() {
        val active = _uiState.value.activeTab ?: return
        val nextMode = if (active.resolvedSiteMode == SiteMode.DESKTOP) {
            SiteMode.MOBILE
        } else {
            SiteMode.DESKTOP
        }
        updateTab(active.id) {
            it.copy(siteModeOverride = nextMode, resolvedSiteMode = nextMode)
        }
        if (!active.isPrivate) {
            _uiState.value.tabs.firstOrNull { it.id == active.id }?.let { persistTab(it) }
        }
        val host = active.displayHost
        if (host.isNotBlank()) {
            viewModelScope.launch {
                val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
                db.siteOverrideDao().upsert(existing.copy(siteModeOverride = nextMode.name))
            }
        }
        pool.getExisting(active.id)?.applySiteMode(nextMode, reloadIfChanged = true)
        showSnackbar("${nextMode.label} site mode for ${host.ifBlank { "tab" }}")
    }

    fun toggleActiveSiteWebDarkMode() {
        val active = _uiState.value.activeTab
        val host = active?.displayHost.orEmpty()
        val currentlyEnabled = _uiState.value.activeTabWebDarkEffective
        val nextEnabled = !currentlyEnabled

        if (host.isNotBlank() && active != null) {
            viewModelScope.launch {
                val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
                val updated = existing.copy(webDarkModeOverride = nextEnabled)
                db.siteOverrideDao().upsert(updated)
                val darkState = DarkReaderConfig.resolve(
                    settings = _uiState.value.settings,
                    siteOverride = updated,
                    isChromeDark = isChromeDark()
                )
                withContext(Dispatchers.Main) {
                    pool.getExisting(active.id)?.applyWebDarkMode(darkState, app.darkReaderInjector)
                }
            }
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
        val host = active?.displayHost.orEmpty()
        if (active == null || active.isStartPage || host.isBlank()) {
            val next = !_uiState.value.settings.adblockEnabled
            setAdblockEnabled(next)
            showSnackbar(if (next) "Ad blocking enabled" else "Ad blocking paused")
            return
        }
        val currentlyDisabled = _uiState.value.siteOverrides[host]?.adblockDisabled == true
        setSiteAdblockDisabled(host, !currentlyDisabled)
    }

    // Per-site override mutations
    fun setSiteAdblockDisabled(host: String, disabled: Boolean) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            db.siteOverrideDao().upsert(existing.copy(adblockDisabled = disabled))
            withContext(Dispatchers.Main) {
                reloadActiveTabIfHost(host)
                showSnackbar(if (disabled) "Ad blocking paused on $host" else "Ad blocking active on $host")
            }
        }
    }

    fun setSiteWebDarkOverride(host: String, override: Boolean?) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            val updated = existing.copy(webDarkModeOverride = override)
            db.siteOverrideDao().upsert(updated)
            val active = _uiState.value.activeTab
            if (active != null && active.displayHost.equals(host, ignoreCase = true)) {
                val darkState = DarkReaderConfig.resolve(_uiState.value.settings, updated, isChromeDark())
                withContext(Dispatchers.Main) {
                    pool.getExisting(active.id)?.applyWebDarkMode(darkState, app.darkReaderInjector)
                }
            }
        }
    }

    fun setSiteModeOverride(host: String, mode: SiteMode?) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            db.siteOverrideDao().upsert(existing.copy(siteModeOverride = mode?.name))
            withContext(Dispatchers.Main) {
                val active = _uiState.value.activeTab
                if (active != null && active.displayHost.equals(host, ignoreCase = true)) {
                    updateTab(active.id) { it.copy(siteModeOverride = mode) }
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
            }
        }
    }

    fun setSiteJavascriptEnabled(host: String, enabled: Boolean) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            db.siteOverrideDao().upsert(existing.copy(javascriptEnabled = enabled))
            withContext(Dispatchers.Main) {
                val active = _uiState.value.activeTab
                if (active != null && active.displayHost.equals(host, ignoreCase = true)) {
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
                showSnackbar(if (enabled) "JavaScript enabled on $host" else "JavaScript disabled on $host")
            }
        }
    }

    fun setSiteCookiesEnabled(host: String, enabled: Boolean) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            db.siteOverrideDao().upsert(existing.copy(cookiesEnabled = enabled))
            withContext(Dispatchers.Main) {
                val active = _uiState.value.activeTab
                if (active != null && active.displayHost.equals(host, ignoreCase = true)) {
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
                showSnackbar(if (enabled) "Cookies enabled on $host" else "Cookies blocked on $host")
            }
        }
    }

    fun setSiteHttpsBypass(host: String, bypass: Boolean) {
        if (host.isBlank()) return
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            db.siteOverrideDao().upsert(existing.copy(httpsOnlyBypass = bypass))
        }
    }

    fun setSiteCustomUserAgent(host: String, userAgent: String?) {
        if (host.isBlank()) return
        val normalized = userAgent?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            val existing = db.siteOverrideDao().getByHost(host) ?: SiteOverrideEntity(host = host)
            db.siteOverrideDao().upsert(existing.copy(customUserAgent = normalized))
            withContext(Dispatchers.Main) {
                val active = _uiState.value.activeTab
                if (active != null && active.displayHost.equals(host, ignoreCase = true)) {
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
                showSnackbar(if (normalized != null) "Custom User-Agent applied for $host" else "Default User-Agent restored")
            }
        }
    }

    // Filter lists actions
    fun updateAllFilterLists() {
        if (_uiState.value.isUpdatingFilterLists) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingFilterLists = true) }
            try {
                val updated = app.filterListRepository.updateAllEnabled()
                showSnackbar("Synced $updated filter lists")
            } catch (e: Throwable) {
                showSnackbar("Filter update failed: ${e.message ?: "error"}")
            } finally {
                _uiState.update { it.copy(isUpdatingFilterLists = false) }
            }
        }
    }

    fun toggleFilterList(id: String, enabled: Boolean) {
        viewModelScope.launch {
            app.filterListRepository.toggleList(id, enabled)
        }
    }

    fun addCustomFilterList(name: String, url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingFilterLists = true) }
            try {
                val result = app.filterListRepository.addCustomList(name, url)
                result.onSuccess { entity ->
                    showSnackbar("Added ${entity.name} (${entity.ruleCount} rules)")
                }.onFailure { err ->
                    showSnackbar("Could not add list: ${err.message ?: "error"}")
                }
            } finally {
                _uiState.update { it.copy(isUpdatingFilterLists = false) }
            }
        }
    }

    fun deleteCustomFilterList(id: String) {
        viewModelScope.launch {
            app.filterListRepository.deleteCustomList(id)
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
                zapper = ZapperUiState(isActive = true)
            )
        }
        session.evaluateJavascript(app.zapperController.buildStartScript(accentHex))
    }

    fun stopElementZapper() {
        val activeId = _uiState.value.activeTabId
        if (activeId != null) {
            pool.getExisting(activeId)?.evaluateJavascript(app.zapperController.buildStopScript())
        }
        _uiState.update { it.copy(zapper = ZapperUiState()) }
    }

    fun adjustZapperScope(delta: Int) {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.evaluateJavascript(app.zapperController.buildAdjustScopeScript(delta))
    }

    fun setZapperScopeDepth(depth: Int) {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.evaluateJavascript(app.zapperController.buildSetScopeScript(depth))
    }

    fun zapSelectedElementOnce() {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.evaluateJavascript(app.zapperController.buildZapOnceScript())
    }

    fun undoZapperTemporaryZap() {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.evaluateJavascript(app.zapperController.buildUndoLastZapScript())
    }

    fun blockSelectedElementPermanently() {
        val activeId = _uiState.value.activeTabId ?: return
        pool.getExisting(activeId)?.evaluateJavascript(app.zapperController.buildConfirmBlockPermanentScript())
    }

    fun toggleZappedRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.zappedRules.firstOrNull { it.id == id } ?: return@launch
            db.zappedRuleDao().upsert(current.copy(enabled = enabled))
            refreshZappedCssForHost(current.host)
        }
    }

    fun deleteZappedRule(id: String) {
        viewModelScope.launch {
            val host = _uiState.value.zappedRules.firstOrNull { it.id == id }?.host
            db.zappedRuleDao().deleteById(id)
            if (host != null) {
                refreshZappedCssForHost(host)
            }
        }
    }

    fun clearZappedRulesForHost(host: String) {
        viewModelScope.launch {
            db.zappedRuleDao().deleteByHost(host)
            refreshZappedCssForHost(host)
            showSnackbar("Cleared zapped rules for $host")
        }
    }

    private suspend fun refreshZappedCssForHost(host: String) {
        val enabledRules = withContext(Dispatchers.IO) {
            db.zappedRuleDao().getEnabledForHost(host)
        }
        val script = app.zapperController.buildApplyZappedCssScript(enabledRules)
        withContext(Dispatchers.Main) {
            _uiState.value.tabs
                .filter { it.displayHost.equals(host, ignoreCase = true) }
                .forEach { tab ->
                    pool.getExisting(tab.id)?.evaluateJavascript(script)
                }
        }
    }

    // Userscripts actions
    fun toggleUserscript(id: String, enabled: Boolean) {
        viewModelScope.launch {
            app.userscriptRepository.setEnabled(id, enabled)
        }
    }

    fun installUserscriptFromUrl(url: String) {
        viewModelScope.launch {
            val result = app.userscriptRepository.installFromUrl(url)
            result.onSuccess { script ->
                showSnackbar("Installed userscript ${script.name}")
            }.onFailure { err ->
                showSnackbar("Userscript install failed: ${err.message ?: "error"}")
            }
        }
    }

    fun installUserscriptFromCode(code: String, fallbackName: String = "Custom Userscript") {
        viewModelScope.launch {
            val result = app.userscriptRepository.installFromCode(code, fallbackName)
            result.onSuccess { script ->
                showSnackbar("Installed userscript ${script.name}")
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
                showSnackbar("Saved ${script.name}")
            }.onFailure { err ->
                showSnackbar("Save failed: ${err.message ?: "error"}")
            }
        }
    }

    fun deleteUserscript(id: String) {
        viewModelScope.launch {
            app.userscriptRepository.deleteScript(id)
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
        _uiState.update { it.copy(isQuickControlsOpen = false, isReaderLoading = true) }
        session.evaluateJavascript(app.readerExtractor.buildExtractScript()) { rawResult ->
            val article = app.readerExtractor.parseExtractionResult(rawResult)
            _uiState.update { state ->
                if (article != null) {
                    state.copy(readerArticle = article, isReaderLoading = false)
                } else {
                    state.copy(
                        isReaderLoading = false,
                        snackbarMessage = "Reader view unavailable for this page"
                    )
                }
            }
        }
    }

    fun closeReaderMode() {
        _uiState.update { it.copy(readerArticle = null, isReaderLoading = false) }
    }

    fun toggleBookmarkForActiveTab() {
        val active = _uiState.value.activeTab ?: return
        if (active.isStartPage) return
        viewModelScope.launch {
            val existing = db.bookmarkDao().findByUrl(active.url)
            if (existing != null) {
                db.bookmarkDao().deleteByUrl(active.url)
                showSnackbar("Removed bookmark")
            } else {
                db.bookmarkDao().upsert(
                    BookmarkEntity(
                        id = UUID.randomUUID().toString(),
                        title = active.title.ifBlank { active.displayHost },
                        url = active.url
                    )
                )
                showSnackbar("Bookmarked ${active.title}")
            }
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { db.bookmarkDao().deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.historyDao().clearAll()
            showSnackbar("Browsing history cleared")
        }
    }

    fun deleteHistoryEntry(url: String) {
        viewModelScope.launch { db.historyDao().deleteByUrl(url) }
    }

    fun clearAllBrowsingData() {
        viewModelScope.launch {
            db.historyDao().clearAll()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            pool.getAllWarm().forEach { it.webView.clearCache(true) }
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
        if (openNative) {
            val launched = onLaunchExternalIntentBridge?.invoke(prompt.launchIntent) ?: false
            if (!launched && !prompt.fallbackUrl.isNullOrBlank()) {
                navigateActiveTab(prompt.fallbackUrl)
            }
        } else if (!prompt.fallbackUrl.isNullOrBlank()) {
            navigateActiveTab(prompt.fallbackUrl)
        }
    }

    fun resolveHttpsErrorPrompt(allowHttpForSite: Boolean) {
        val prompt = _uiState.value.httpsErrorPrompt ?: return
        _uiState.update { it.copy(httpsErrorPrompt = null) }
        if (allowHttpForSite) {
            viewModelScope.launch {
                val existing = db.siteOverrideDao().getByHost(prompt.host)
                    ?: SiteOverrideEntity(host = prompt.host)
                db.siteOverrideDao().upsert(existing.copy(httpsOnlyBypass = true))
                navigateActiveTab(prompt.originalHttpUrl)
            }
        }
    }

    fun resolvePermissionPrompt(allow: Boolean, rememberForSite: Boolean) {
        val prompt = _uiState.value.permissionPrompt ?: return
        _uiState.update { it.copy(permissionPrompt = null) }

        if (prompt.isGeolocation) {
            prompt.geoCallback?.invoke(prompt.geoOrigin, allow, rememberForSite)
        } else {
            if (allow) {
                prompt.webRequest?.grant(prompt.resources.toTypedArray())
            } else {
                prompt.webRequest?.deny()
            }
        }

        if (rememberForSite && prompt.host.isNotBlank()) {
            viewModelScope.launch {
                var entity = db.siteOverrideDao().getByHost(prompt.host)
                    ?: SiteOverrideEntity(host = prompt.host)
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
                db.siteOverrideDao().upsert(entity)
            }
        }
    }

    fun resetSiteOverride(host: String) {
        viewModelScope.launch {
            db.siteOverrideDao().deleteByHost(host)
            withContext(Dispatchers.Main) {
                val active = _uiState.value.activeTab
                if (active != null && active.displayHost.equals(host, ignoreCase = true)) {
                    applyResolvedSettingsToTab(active.id, reloadIfModeChanged = true)
                }
                showSnackbar("Reset site overrides for $host")
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

    fun setAdblockEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAdblockEnabled(enabled) }
    fun setAdblockCosmeticEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAdblockCosmeticEnabled(enabled) }
    fun setUserscriptsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setUserscriptsEnabled(enabled) }

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

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message, snackbarUndoZapper = false) }
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
        ThemeMode.SYSTEM -> onSystemDarkThemeProvider?.invoke() ?: true
    }

    private fun reloadActiveTabIfHost(host: String) {
        val active = _uiState.value.activeTab ?: return
        if (active.displayHost.equals(host, ignoreCase = true)) {
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
        viewModelScope.launch {
            db.tabDao().upsert(
                TabEntity(
                    id = tab.id,
                    spaceId = tab.spaceId,
                    title = tab.title,
                    url = tab.url,
                    isPrivate = false,
                    isPinned = tab.isPinned,
                    siteModeOverride = tab.siteModeOverride?.name,
                    lastAccessedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun recordHistory(url: String, title: String) {
        if (url == OmniboxEngine.NEW_TAB_URL || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return
        }
        viewModelScope.launch {
            val existing = db.historyDao().findByUrl(url)
            db.historyDao().upsert(
                HistoryEntity(
                    url = url,
                    title = title.ifBlank { OmniboxEngine.extractHost(url) },
                    visitCount = (existing?.visitCount ?: 0) + 1,
                    lastVisitedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun captureThumbnailFor(tabId: String) {
        pool.captureAndStoreThumbnail(tabId)
        val bmp = pool.getThumbnail(tabId) ?: return
        _thumbnails.update { current ->
            current.copy(bitmaps = current.bitmaps + (tabId to bmp))
        }
    }

    private fun applyResolvedSettingsToTab(tabId: String, reloadIfModeChanged: Boolean) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val session = pool.getExisting(tabId) ?: return
        applySettingsToSession(tab, session, reloadIfModeChanged)
    }

    private fun syncAllWarmSessionsWithSettings(reloadActiveOnSiteModeChange: Boolean) {
        val state = _uiState.value
        state.tabs.forEach { tab ->
            val session = pool.getExisting(tab.id) ?: return@forEach
            applySettingsToSession(
                tab = tab,
                session = session,
                reloadIfModeChanged = reloadActiveOnSiteModeChange && tab.id == state.activeTabId
            )
        }
    }

    private fun applySettingsToSession(
        tab: BrowserTabUi,
        session: BrowserSession,
        reloadIfModeChanged: Boolean
    ) {
        val state = _uiState.value
        val siteOverrideEntity = state.siteOverrides[tab.displayHost.lowercase()]
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
            reloadIfChanged = reloadIfModeChanged
        )

        val darkState = DarkReaderConfig.resolve(
            settings = state.settings,
            siteOverride = siteOverrideEntity,
            isChromeDark = isChromeDark()
        )
        session.applyWebDarkMode(darkState, app.darkReaderInjector)
        session.applyBackgroundPlayback(state.settings.backgroundMediaEnabled)
    }
}