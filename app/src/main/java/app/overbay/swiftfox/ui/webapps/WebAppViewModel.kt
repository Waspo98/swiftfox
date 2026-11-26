package app.overbay.swiftfox.ui.webapps

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.swiftfox.MainActivity
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.WebAppEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.engine.BackgroundMediaService
import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.NetworkErrorDiagnostics
import app.overbay.swiftfox.engine.ScriptInjector
import app.overbay.swiftfox.engine.SessionHostCallbacks
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.adblock.AdblockEngineStatus
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.sharing.CleanUrlSanitizer
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.webapps.WebAppDetector
import app.overbay.swiftfox.features.webapps.WebAppDisplayMode
import app.overbay.swiftfox.features.webapps.WebAppManifestParser
import app.overbay.swiftfox.features.webapps.WebAppShortcutItem
import app.overbay.swiftfox.features.zapper.ZapperUiState
import app.overbay.swiftfox.ui.browser.MainFrameLoadError
import app.overbay.swiftfox.ui.browser.SiteOverrideWriteCoordinator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class WebAppUiState(
    val webApp: WebAppEntity? = null,
    val isMissingApp: Boolean = false,
    val settings: BrowserSettings = BrowserSettings(),
    val hasLoadedSettings: Boolean = false,
    val isSystemDark: Boolean = true,
    val currentUrl: String = "",
    val currentTitle: String = "",
    val isLoading: Boolean = true,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val liveThemeColorHex: String? = null,
    val sampledTopColorHex: String? = null,
    val sampledBottomColorHex: String? = null,
    val viewportFitCover: Boolean = false,
    val darkReaderSkippedNative: Boolean = false,
    val showSplash: Boolean = true,
    val sessionRevision: Int = 0,
    val siteOverrides: Map<String, SiteOverrideEntity> = emptyMap(),
    val zappedRules: List<ZappedRuleEntity> = emptyList(),
    val zapper: ZapperUiState = ZapperUiState(),
    val adblockStatus: AdblockEngineStatus = AdblockEngineStatus(),
    val blockedCount: Int = 0,
    val isControlsSheetOpen: Boolean = false,
    val isSiteRulesOpen: Boolean = false,
    val contextMenuHit: ContextMenuHit? = null,
    val externalAppPrompt: ExternalAppPrompt? = null,
    val httpsErrorPrompt: HttpsUpgradeErrorPrompt? = null,
    val permissionPrompt: SitePermissionPrompt? = null,
    val loadError: MainFrameLoadError? = null,
    val isFullscreenVideo: Boolean = false,
    val isInPictureInPicture: Boolean = false,
    val isPlayingMedia: Boolean = false,
    val isPlayingVideo: Boolean = false,
    val hasPopupSession: Boolean = false,
    val popupUrl: String = "",
    val popupTitle: String = "",
    val popupRevision: Int = 0,
    val snackbarMessage: String? = null,
    val snackbarId: Long = 0L,
    val appIconBitmap: Bitmap? = null
) {
    val activeHost: String
        get() = OmniboxEngine.normalizeHost(
            OmniboxEngine.extractHost(currentUrl.ifBlank { webApp?.startUrl.orEmpty() })
        )

    val activeSiteOverride: SiteOverrideEntity?
        get() = activeHost.takeIf { it.isNotBlank() }?.let { siteOverrides[it] }

    val displayMode: WebAppDisplayMode
        get() = WebAppDisplayMode.fromRaw(webApp?.displayMode)

    val manifestShortcuts: List<WebAppShortcutItem>
        get() = WebAppManifestParser.decodeShortcutsJson(webApp?.shortcutsJson)
}

class WebAppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SwiftfoxApp
    private val db = app.database
    private val prefs = app.preferencesRepository
    private val coordinator = app.sessionEngineCoordinator

    private val _uiState = MutableStateFlow(
        WebAppUiState(
            isSystemDark = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        )
    )
    val uiState: StateFlow<WebAppUiState> = _uiState.asStateFlow()

    var onLaunchExternalIntentBridge: ((Intent) -> Boolean)? = null
    var onShowFileChooserBridge: ((ValueCallback<Array<Uri>>, Intent) -> Boolean)? = null
    var onRequestAndroidPermissionsBridge: ((List<String>, (Map<String, Boolean>) -> Unit) -> Unit)? = null
    var onShowFullscreenViewBridge: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null
    var onHideFullscreenViewBridge: (() -> Unit)? = null
    var onSystemDarkThemeProvider: (() -> Boolean)? = null

    private var boundHostContext: Context = application
    private var mainSession: BrowserSession? = null
    private var popupSession: BrowserSession? = null
    private var loadedAppId: String? = null
    private var hasTriggeredInitialLoad = false
    private var hasAskedNotificationPermission = false

    private val blockedCounter = AtomicInteger(0)
    private val snackbarCounter = AtomicLong(0L)
    private val siteOverrideWrites = SiteOverrideWriteCoordinator<Map<String, SiteOverrideEntity>>()
    private val cosmeticExceptionsByTab = ConcurrentHashMap<String, Set<String>>()
    @Volatile
    private var enabledZappedRulesByHost: Map<String, List<ZappedRuleEntity>> = emptyMap()
    private var pageStartedInjectJob: Job? = null
    private var pageFinishedInjectJob: Job? = null
    private var loadErrorDiagJob: Job? = null
    private var splashTimeoutJob: Job? = null
    private var initWebAppJob: Job? = null
    private val ioExceptionHandler = CoroutineExceptionHandler { _, err ->
        Log.w("WebAppViewModel", "Background coroutine error: ${err.message}")
    }

    private val sessionCallbacks = object : SessionHostCallbacks {
        override fun getHttpsOnlyEnabled(): Boolean = _uiState.value.settings.httpsOnlyMode

        override fun isHttpsBypassedForHost(tabId: String, host: String): Boolean {
            val norm = OmniboxEngine.normalizeHost(host)
            return _uiState.value.siteOverrides[norm]?.httpsOnlyBypass == true
        }

        override fun getExternalAppPolicy(tabId: String, host: String): ExternalAppLinkPolicy {
            val norm = OmniboxEngine.normalizeHost(host)
            val raw = _uiState.value.siteOverrides[norm]?.externalAppPolicyOverride
            return raw?.let { runCatching { ExternalAppLinkPolicy.valueOf(it) }.getOrNull() }
                ?: _uiState.value.settings.externalAppLinkPolicy
        }

        override fun shouldOverrideMainFrameNavigation(
            tabId: String,
            url: String,
            isRedirect: Boolean,
            hasGesture: Boolean
        ): Boolean {
            if (tabId == POPUP_TAB_ID) return false
            val webApp = _uiState.value.webApp ?: return false
            if (WebAppManifestParser.isUrlInWebAppScope(url, webApp.startUrl, webApp.scopeUrl)) {
                return false
            }
            if (WebAppManifestParser.isLikelyAuthOrOAuthRedirect(url, isRedirect, hasGesture)) {
                return false
            }
            openUrlInSwiftfox(url)
            return true
        }

        override fun shouldBlockNetworkRequest(
            tabId: String,
            requestUrl: String,
            pageUrl: String,
            headers: Map<String, String>
        ): Boolean {
            val pageHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(pageUrl))
            return shouldBlockNetworkRequest(tabId, false, requestUrl, pageUrl, pageHost, headers)
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
            val siteDisabled = cachedPageHost.isNotEmpty() &&
                state.siteOverrides[cachedPageHost]?.adblockDisabled == true
            val blocked = coordinator.shouldBlockNetworkRequest(
                requestUrl = requestUrl,
                pageUrl = pageUrl,
                headers = headers,
                globalEnabled = state.settings.adblockEnabled,
                siteDisabled = siteDisabled
            )
            if (blocked && tabId == MAIN_TAB_ID) {
                val count = blockedCounter.incrementAndGet()
                if (count == 1 || count % 5 == 0 || state.isControlsSheetOpen) {
                    _uiState.update { it.copy(blockedCount = count) }
                }
            }
            return blocked
        }

        override fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
            sessionForId(tabId)?.let { s ->
                ScriptInjector.extractOrigin(url)?.let { origin -> s.rotateTokensForOrigin(origin) }
            }
            if (tabId == POPUP_TAB_ID) {
                _uiState.update { it.copy(popupUrl = url) }
                return
            }
            app.userscriptBridge.cancelCallsForTab(tabId)
            blockedCounter.set(0)
            cosmeticExceptionsByTab.remove(tabId)
            pageStartedInjectJob?.cancel()
            pageFinishedInjectJob?.cancel()
            loadErrorDiagJob?.cancel()

            val current = _uiState.value
            val isSameUrl = current.currentUrl == url
            val isSameHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(current.currentUrl)) ==
                OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url))
            if (!isSameUrl && current.isPlayingMedia) {
                BackgroundMediaService.releasePlaybackOwner(
                    owner = this@WebAppViewModel,
                    context = app,
                    stopService = true
                )
            }
            val pendingPerm = current.permissionPrompt
            if (pendingPerm != null && pendingPerm.tabId == tabId) {
                denyPromptSilently(pendingPerm)
            }
            _uiState.update {
                it.copy(
                    currentUrl = url,
                    isLoading = true,
                    progress = 12,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    liveThemeColorHex = if (isSameHost) it.liveThemeColorHex else it.webApp?.themeColorHex,
                    sampledTopColorHex = if (isSameHost) it.sampledTopColorHex else null,
                    sampledBottomColorHex = if (isSameHost) it.sampledBottomColorHex else null,
                    viewportFitCover = if (isSameHost) it.viewportFitCover else false,
                    blockedCount = 0,
                    darkReaderSkippedNative = false,
                    loadError = null,
                    zapper = ZapperUiState(),
                    isPlayingMedia = if (isSameUrl) it.isPlayingMedia else false,
                    isPlayingVideo = if (isSameUrl) it.isPlayingVideo else false,
                    externalAppPrompt = if (it.externalAppPrompt?.tabId == tabId) null else it.externalAppPrompt,
                    httpsErrorPrompt = if (it.httpsErrorPrompt?.tabId == tabId) null else it.httpsErrorPrompt,
                    permissionPrompt = if (it.permissionPrompt?.tabId == tabId) null else it.permissionPrompt
                )
            }
            applyResolvedSettingsToSession(tabId, reloadIfModeChanged = false)
        }

        override fun onInjectOnPageStarted(tabId: String, url: String) {
            if (!isHttpUrl(url)) return
            val session = sessionForId(tabId) ?: return
            val state = _uiState.value
            val normalizedHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url))
            val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
            val siteOverride = state.siteOverrides[normalizedHost]
            val siteAdblockDisabled = siteOverride?.adblockDisabled == true
            val darkState = DarkReaderConfig.resolve(state.settings, siteOverride, isChromeDark())
            val tokens = session.rotateTokensForOrigin(expectedOrigin)
            session.webDarkDirty = false
            val (isDarkSurface, isAmoledSurface) = coordinator.resolveWebSurfaceDarkColors(darkState)
            session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
            ScriptInjector.injectDarkReaderOnPageStarted(
                webView = session.webView,
                expectedOrigin = expectedOrigin,
                tokens = tokens,
                darkState = darkState,
                darkReaderInjector = app.darkReaderInjector,
                isDestroyed = { session.isDestroyed }
            )

            val job = viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                val zappedRules = if (normalizedHost.isNotBlank()) {
                    enabledZappedRulesByHost[normalizedHost].orEmpty()
                } else {
                    emptyList()
                }
                val payload = coordinator.preparePageStartedPayload(
                    url = url,
                    tokens = tokens,
                    settings = state.settings,
                    siteAdblockDisabled = siteAdblockDisabled,
                    zappedRules = zappedRules
                )
                cosmeticExceptionsByTab[tabId] = payload.cosmetic.exceptions.toSet()
                withContext(Dispatchers.Main) {
                    if (session.isDestroyed) return@withContext
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && liveUrl != url) return@withContext
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
            if (tabId == MAIN_TAB_ID) {
                pageStartedInjectJob?.cancel()
                pageStartedInjectJob = job
            }
        }

        override fun onPageFinished(
            tabId: String,
            url: String,
            title: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            if (tabId == POPUP_TAB_ID) {
                _uiState.update {
                    it.copy(
                        popupUrl = url.ifBlank { it.popupUrl },
                        popupTitle = title.ifBlank { it.popupTitle }
                    )
                }
                return
            }
            sessionForId(tabId)?.flushCookies()
            _uiState.update {
                it.copy(
                    currentUrl = url.ifBlank { it.currentUrl },
                    currentTitle = title.ifBlank { it.currentTitle },
                    isLoading = false,
                    progress = 100,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    blockedCount = blockedCounter.get(),
                    showSplash = false
                )
            }
        }

        override fun onInjectOnPageFinished(tabId: String, url: String) {
            if (!isHttpUrl(url)) return
            val session = sessionForId(tabId) ?: return
            val state = _uiState.value
            val normalizedHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url))
            val expectedOrigin = ScriptInjector.extractOrigin(url) ?: return
            val siteOverride = state.siteOverrides[normalizedHost]
            val siteAdblockDisabled = siteOverride?.adblockDisabled == true
            val runGenericScan = state.settings.adblockEnabled &&
                state.settings.adblockCosmeticEnabled &&
                !siteAdblockDisabled
            val darkState = DarkReaderConfig.resolve(state.settings, siteOverride, isChromeDark())
            val tokens = session.rotateTokensForOrigin(expectedOrigin)
            ScriptInjector.injectDarkReaderFastOrFull(
                webView = session.webView,
                tokens = tokens,
                darkState = darkState,
                darkReaderInjector = app.darkReaderInjector,
                isDestroyed = { session.isDestroyed }
            )
            if (tabId == MAIN_TAB_ID) {
                session.evaluateJavascript(
                    WebAppDetector.buildThemeColorObserverScript(tokens.pwaToken, expectedOrigin)
                )
            }
            session.evaluateJavascript(ScriptInjector.buildEnsureMobileViewportScript())
            val job = viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
                val zappedRules = if (normalizedHost.isNotBlank()) {
                    enabledZappedRulesByHost[normalizedHost].orEmpty()
                } else {
                    emptyList()
                }
                val payload = coordinator.preparePageFinishedPayload(
                    url = url,
                    settings = state.settings,
                    siteAdblockDisabled = siteAdblockDisabled,
                    zappedRules = zappedRules
                )
                cosmeticExceptionsByTab[tabId] = payload.cosmetic.exceptions.toSet()
                withContext(Dispatchers.Main) {
                    if (session.isDestroyed) return@withContext
                    val liveUrl = session.webView.url.orEmpty()
                    if (liveUrl.isNotBlank() && liveUrl != url) return@withContext
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
                }
            }
            if (tabId == MAIN_TAB_ID) {
                pageStartedInjectJob?.cancel()
                pageFinishedInjectJob?.cancel()
                pageFinishedInjectJob = job
            }
        }

        override fun onVisitedHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
            if (tabId == POPUP_TAB_ID) {
                _uiState.update { it.copy(popupUrl = url) }
                return
            }
            sessionForId(tabId)?.let { s ->
                ScriptInjector.extractOrigin(url)?.let { origin -> s.rotateTokensForOrigin(origin) }
            }
            _uiState.update {
                it.copy(
                    currentUrl = url,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }
            val expectedOrigin = ScriptInjector.extractOrigin(url)
            if (expectedOrigin != null) {
                sessionForId(tabId)?.let { s ->
                    s.evaluateJavascript(
                        WebAppDetector.buildThemeColorObserverScript(s.tokens.pwaToken, expectedOrigin)
                    )
                }
            }
        }

        override fun onProgressChanged(tabId: String, progress: Int) {
            if (tabId != MAIN_TAB_ID) return
            _uiState.update {
                it.copy(
                    progress = progress,
                    isLoading = progress in 1..99,
                    showSplash = if (progress >= 65) false else it.showSplash
                )
            }
        }

        override fun onTitleChanged(tabId: String, title: String) {
            if (tabId == POPUP_TAB_ID) {
                _uiState.update { it.copy(popupTitle = title) }
            } else {
                _uiState.update { it.copy(currentTitle = title) }
            }
        }

        override fun onPwaChannelMessage(tabId: String, pageUrl: String, event: String, json: String) {
            if (tabId != MAIN_TAB_ID) return
            if (event == "themeSurface") {
                val update = WebAppDetector.parseThemeSurfaceMessage(json) ?: return
                _uiState.update {
                    it.copy(
                        liveThemeColorHex = update.metaThemeColorHex,
                        sampledTopColorHex = update.sampledTopColorHex,
                        sampledBottomColorHex = update.sampledBottomColorHex,
                        viewportFitCover = update.viewportFitCover
                    )
                }
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
        ): Boolean = onShowFileChooserBridge?.invoke(filePathCallback, chooserIntent) ?: false

        override fun onPermissionRequested(tabId: String, prompt: SitePermissionPrompt) {
            val normalizedHost = OmniboxEngine.normalizeHost(prompt.host)
            val normalizedPrompt = prompt.copy(tabId = tabId, host = normalizedHost)
            val saved = _uiState.value.siteOverrides[normalizedHost]
            val decision = coordinator.resolvePermissionPolicyDecision(normalizedPrompt, saved)
            if (decision == true) {
                grantPermissionWithRuntimeCheck(normalizedPrompt, rememberForSite = false)
                return
            }
            if (decision == false) {
                denyPromptSilently(normalizedPrompt)
                return
            }
            _uiState.value.permissionPrompt?.let(::denyPromptSilently)
            _uiState.update { it.copy(permissionPrompt = normalizedPrompt) }
        }

        override fun onPermissionRequestCanceled(
            tabId: String,
            webRequest: PermissionRequest?,
            isGeolocation: Boolean
        ) {
            _uiState.update { state ->
                val current = state.permissionPrompt ?: return@update state
                val matches = if (isGeolocation) current.isGeolocation else (webRequest == null || current.webRequest === webRequest)
                if (matches) state.copy(permissionPrompt = null) else state
            }
        }

        override fun onOpenNewWindow(parentTabId: String, resultMsg: Message?): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val popup = recreatePopupSession()
            transport.webView = popup.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(tabId: String) {
            if (tabId == POPUP_TAB_ID) {
                closePopupSession()
            }
        }

        override fun onExternalAppPrompt(prompt: ExternalAppPrompt) {
            _uiState.update { it.copy(externalAppPrompt = prompt) }
        }

        override fun onLaunchExternalIntent(intent: Intent): Boolean =
            onLaunchExternalIntentBridge?.invoke(intent) ?: false

        override fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt) {
            _uiState.update { it.copy(httpsErrorPrompt = prompt) }
        }

        override fun onMainFrameLoadError(tabId: String, url: String, errorCode: Int, description: String) {
            if (tabId != MAIN_TAB_ID) return
            val host = OmniboxEngine.extractHost(url)
            val initialError = MainFrameLoadError(
                url = url,
                host = host,
                description = description.ifBlank { "Connection error ($errorCode)" }
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    progress = 0,
                    showSplash = false,
                    loadError = initialError
                )
            }
            loadErrorDiagJob?.cancel()
            loadErrorDiagJob = viewModelScope.launch(ioExceptionHandler) {
                val result = NetworkErrorDiagnostics.diagnose(
                    url = url,
                    description = initialError.description,
                    baseClient = app.httpClient,
                    runDirectProbe = true
                )
                _uiState.update { state ->
                    val current = state.loadError
                    if (current == null || current.url != url) state
                    else state.copy(
                        loadError = current.copy(
                            resolvedIps = result.resolvedIps,
                            hasSinkholeOrLoopbackIp = result.hasSinkholeOrLoopbackIp,
                            directProbeSummary = result.directProbeSummary
                        )
                    )
                }
            }
        }

        override fun onRenderProcessGone(tabId: String, didCrash: Boolean) {
            viewModelScope.launch(Dispatchers.Main) {
                if (tabId == POPUP_TAB_ID) {
                    closePopupSession()
                    return@launch
                }
                mainSession?.destroy()
                mainSession = null
                hasTriggeredInitialLoad = false
                if (_uiState.value.isFullscreenVideo) {
                    onHideFullscreenViewBridge?.invoke()
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = 0,
                        isPlayingMedia = false,
                        isPlayingVideo = false,
                        zapper = ZapperUiState(),
                        sessionRevision = it.sessionRevision + 1
                    )
                }
                showSnackbar(if (didCrash) "Recovered web app after renderer reset" else "Reloaded web app")
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
                    isPrivate = false,
                    onIsolatedResult = { isolated ->
                        isolated.onSuccess { showSnackbar("Saved ${it.fileName} to Downloads") }
                            .onFailure { showSnackbar("Download failed: ${it.message ?: "error"}") }
                    }
                )
                result.onSuccess { showSnackbar("Downloading ${it.fileName}") }
                    .onFailure { showSnackbar("Download failed: ${it.message ?: "error"}") }
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
                    isPrivate = false
                )
                result.onSuccess { showSnackbar("Saved ${it.fileName} to Downloads") }
                    .onFailure { showSnackbar("Could not save file: ${it.message ?: "error"}") }
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
            if (tabId != MAIN_TAB_ID) return
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isPlayingMedia = isPlaying,
                        isPlayingVideo = isPlaying && hasVideo
                    )
                }
                val state = _uiState.value
                val appId = state.webApp?.id
                val mediaTitle = title.ifBlank { state.currentTitle.ifBlank { state.webApp?.name ?: "Media playback" } }
                val mediaHost = state.webApp?.name ?: state.activeHost.ifBlank { "Web App" }
                if (isPlaying && state.settings.backgroundMediaEnabled) {
                    maybeRequestNotificationPermissionOnce()
                    BackgroundMediaService.claimPlayback(
                        owner = this@WebAppViewModel,
                        context = app,
                        controller = BackgroundMediaService.MediaCommandController { cmd ->
                            val session = mainSession ?: return@MediaCommandController
                            when (cmd) {
                                BackgroundMediaService.MediaCommand.PLAY -> session.setMediaPaused(false)
                                BackgroundMediaService.MediaCommand.PAUSE -> session.setMediaPaused(true)
                                BackgroundMediaService.MediaCommand.STOP -> {
                                    session.setMediaPaused(true)
                                    _uiState.update { it.copy(isPlayingMedia = false, isPlayingVideo = false) }
                                }
                            }
                        },
                        title = mediaTitle,
                        host = mediaHost,
                        isPlaying = true,
                        isPrivate = false,
                        webAppId = appId
                    )
                } else if (!isPlaying) {
                    if (state.settings.backgroundMediaEnabled) {
                        BackgroundMediaService.updatePlaybackIfOwner(
                            owner = this@WebAppViewModel,
                            context = app,
                            title = mediaTitle,
                            host = mediaHost,
                            isPlaying = false,
                            isPrivate = false,
                            webAppId = appId
                        )
                    } else {
                        BackgroundMediaService.releasePlaybackOwner(
                            owner = this@WebAppViewModel,
                            context = app,
                            stopService = true
                        )
                    }
                }
            }
        }

        override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) = Unit

        override fun onContextMenuLongPress(tabId: String, hit: ContextMenuHit) {
            if (tabId == MAIN_TAB_ID) {
                _uiState.update { it.copy(contextMenuHit = hit) }
            }
        }

        override fun onZapperSelectionChanged(tabId: String, json: String) {
            if (tabId != MAIN_TAB_ID) return
            val event = app.zapperController.parseSelectionEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                if (!_uiState.value.zapper.isActive) return@launch
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
            if (tabId != MAIN_TAB_ID) return
            val event = app.zapperController.parseTemporaryZapEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                if (!_uiState.value.zapper.isActive) return@launch
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
            if (tabId != MAIN_TAB_ID) return
            val count = app.zapperController.parseUndoCount(json)
            viewModelScope.launch(Dispatchers.Main) {
                if (!_uiState.value.zapper.isActive) return@launch
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
            if (tabId != MAIN_TAB_ID) return
            val event = app.zapperController.parsePermanentRuleEvent(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                val state = _uiState.value
                if (!state.zapper.isActive) return@launch
                val host = state.activeHost
                if (host.isBlank()) return@launch
                val blockedLabel = event.label.ifBlank { event.selector }
                withContext(Dispatchers.IO) {
                    val existing = db.zappedRuleDao().getForHost(host).firstOrNull { it.selector == event.selector }
                    val rule = existing?.copy(enabled = true, label = event.label.ifBlank { existing.label })
                        ?: ZappedRuleEntity(
                            id = "zap_${UUID.randomUUID().toString().take(12)}",
                            host = host,
                            selector = event.selector,
                            label = event.label,
                            enabled = true
                        )
                    db.zappedRuleDao().upsert(rule)
                }
                _uiState.update {
                    it.copy(
                        zapper = it.zapper.copy(
                            selectedSelector = null,
                            selectedSummary = "",
                            scopeDepth = 0,
                            maxScopeDepth = 0,
                            matchCount = 0,
                            isPreviewing = false,
                            lastStatusMessage = "Blocked $blockedLabel"
                        )
                    )
                }
                mainSession?.clearDocumentStartScripts()
                refreshZappedCssForHost(host)
            }
        }

        override fun onDarkReaderStatus(tabId: String, json: String) {
            if (tabId != MAIN_TAB_ID) return
            val event = app.darkReaderInjector.parseStatusJson(json) ?: return
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update { it.copy(darkReaderSkippedNative = event.skippedNativeDark) }
            }
        }

        override fun onCosmeticDomTokens(tabId: String, pageUrl: String, classesJson: String, idsJson: String) {
            if (!isHttpUrl(pageUrl)) return
            if ((classesJson.isBlank() || classesJson == "[]") && (idsJson.isBlank() || idsJson == "[]")) return
            val state = _uiState.value
            if (!state.settings.adblockEnabled || !state.settings.adblockCosmeticEnabled) return
            val host = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(pageUrl))
            if (state.siteOverrides[host]?.adblockDisabled == true) return
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
                    val session = sessionForId(tabId) ?: return@withContext
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
            app.userscriptBridge.handleSetValue(scriptId = scriptId, key = key, valueJson = valueJson, pageUrl = pageUrl)
        }

        override fun onUserscriptDeleteValue(tabId: String, pageUrl: String, scriptId: String, key: String) {
            app.userscriptBridge.handleDeleteValue(scriptId = scriptId, key = key, pageUrl = pageUrl)
        }

        override fun onUserscriptXmlHttpRequest(
            tabId: String,
            pageUrl: String,
            scriptId: String,
            reqId: String,
            requestJson: String
        ) {
            val session = sessionForId(tabId) ?: return
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
                val live = sessionForId(tabId) ?: return@handleXmlHttpRequest
                if (live.isDestroyed) return@handleXmlHttpRequest
                val liveUrl = live.webView.url.orEmpty()
                if (originAtRequest != null && liveUrl.isNotBlank() &&
                    ScriptInjector.extractOrigin(liveUrl) != originAtRequest
                ) {
                    return@handleXmlHttpRequest
                }
                live.evaluateJavascript(callbackJs)
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
                val old = _uiState.value.settings
                val wasLoaded = _uiState.value.hasLoadedSettings
                val webDarkChanged = !wasLoaded ||
                    old.themeMode != newSettings.themeMode ||
                    old.webDarkMode != newSettings.webDarkMode ||
                    old.darkReaderAmoled != newSettings.darkReaderAmoled ||
                    old.darkReaderBrightness != newSettings.darkReaderBrightness ||
                    old.darkReaderContrast != newSettings.darkReaderContrast ||
                    old.darkReaderSepia != newSettings.darkReaderSepia ||
                    old.darkReaderSkipNativeDark != newSettings.darkReaderSkipNativeDark
                val bgMediaChanged = old.backgroundMediaEnabled != newSettings.backgroundMediaEnabled
                val cookiesChanged = old.blockThirdPartyCookies != newSettings.blockThirdPartyCookies
                _uiState.update { it.copy(settings = newSettings, hasLoadedSettings = true) }
                if (!newSettings.backgroundMediaEnabled && old.backgroundMediaEnabled) {
                    BackgroundMediaService.stop(app)
                }
                if (webDarkChanged) {
                    syncGlobalPrepaintDark()
                }
                if (webDarkChanged || bgMediaChanged || cookiesChanged) {
                    applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = false)
                }
            }
        }

        viewModelScope.launch {
            app.adblockEngine.status.collectLatest { status ->
                _uiState.update { it.copy(adblockStatus = status) }
            }
        }

        viewModelScope.launch {
            db.zappedRuleDao().observeAll().distinctUntilChanged().collectLatest { rules ->
                enabledZappedRulesByHost = rules.asSequence()
                    .filter { it.enabled }
                    .groupBy { OmniboxEngine.normalizeHost(it.host) }
                _uiState.update { it.copy(zappedRules = rules) }
            }
        }

        viewModelScope.launch {
            db.siteOverrideDao().observeAll().distinctUntilChanged().collectLatest { list ->
                val map = list.associateBy { OmniboxEngine.normalizeHost(it.host) }
                _uiState.update { it.copy(siteOverrides = map) }
                syncGlobalPrepaintDark()
                applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = false)
            }
        }

        viewModelScope.launch {
            app.mozillaSyncCoordinator.swiftfoxDataRevision.collectLatest { rev ->
                if (rev > 0L) {
                    mainSession?.clearDocumentStartScripts()
                    popupSession?.clearDocumentStartScripts()
                    val host = _uiState.value.activeHost
                    if (host.isNotBlank()) {
                        refreshZappedCssForHost(host)
                    }
                }
            }
        }
    }

    fun initializeForWebApp(webAppId: String?, openControlsOnStart: Boolean = false) {
        if (webAppId.isNullOrBlank()) {
            _uiState.update { it.copy(isMissingApp = true, showSplash = false) }
            return
        }
        if (loadedAppId == webAppId && _uiState.value.webApp != null) {
            if (openControlsOnStart) {
                _uiState.update { it.copy(isControlsSheetOpen = true) }
            }
            return
        }
        val isSwitchingApp = loadedAppId != null && loadedAppId != webAppId
        if (isSwitchingApp) {
            pageStartedInjectJob?.cancel()
            pageFinishedInjectJob?.cancel()
            loadErrorDiagJob?.cancel()
            BackgroundMediaService.releasePlaybackOwner(
                owner = this@WebAppViewModel,
                context = app,
                stopService = true
            )
            closePopupSession()
            mainSession?.destroy()
            mainSession = null
            hasTriggeredInitialLoad = false
            blockedCounter.set(0)
            cosmeticExceptionsByTab.clear()
        }
        loadedAppId = webAppId
        splashTimeoutJob?.cancel()
        splashTimeoutJob = viewModelScope.launch {
            delay(2_500L)
            _uiState.update { it.copy(showSplash = false) }
        }
        initWebAppJob?.cancel()
        initWebAppJob = viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                app.webAppRepository.getById(webAppId)
            }
            if (loadedAppId != webAppId) return@launch
            if (entity == null) {
                _uiState.update { it.copy(isMissingApp = true, showSplash = false) }
                return@launch
            }
            app.webAppRepository.markLaunched(entity.id)
            val iconBmp = withContext(Dispatchers.IO) {
                app.webAppRepository.iconHelper.resolveDisplayBitmap(
                    appId = entity.id,
                    iconPath = entity.iconPath,
                    iconUrl = entity.iconUrl,
                    pageUrl = entity.startUrl,
                    customIconSpec = entity.customIcon,
                    fallbackLabel = entity.name,
                    backgroundColorHex = entity.backgroundColorHex ?: entity.themeColorHex
                )
            }
            if (loadedAppId != webAppId) return@launch
            _uiState.update {
                it.copy(
                    webApp = entity,
                    isMissingApp = false,
                    currentUrl = entity.startUrl,
                    currentTitle = entity.name,
                    isLoading = true,
                    progress = 0,
                    canGoBack = false,
                    canGoForward = false,
                    liveThemeColorHex = entity.themeColorHex,
                    sampledTopColorHex = null,
                    sampledBottomColorHex = null,
                    viewportFitCover = false,
                    showSplash = true,
                    sessionRevision = if (isSwitchingApp) it.sessionRevision + 1 else it.sessionRevision,
                    blockedCount = if (isSwitchingApp) 0 else it.blockedCount,
                    loadError = null,
                    appIconBitmap = iconBmp,
                    isControlsSheetOpen = openControlsOnStart
                )
            }
        }
    }

    fun rebindHostContext(context: Context) {
        boundHostContext = context
        mainSession?.rebindContext(context)
        popupSession?.rebindContext(context)
    }

    fun unbindHostContext(activityContext: Context) {
        if (boundHostContext !== activityContext) return
        boundHostContext = app
        mainSession?.rebindContext(app)
        popupSession?.rebindContext(app)
    }

    fun ensureMainSession(context: Context): BrowserSession? {
        val webApp = _uiState.value.webApp ?: return null
        val existing = mainSession?.takeUnless { it.isDestroyed }
        if (existing != null) {
            existing.rebindContext(boundHostContext)
            return existing
        }
        val created = BrowserSession(
            tabId = MAIN_TAB_ID,
            isPrivate = false,
            profileName = BrowserSession.DEFAULT_PROFILE_NAME,
            context = context,
            hostCallbacks = sessionCallbacks,
            forceMobileViewport = true
        )
        mainSession = created
        syncGlobalPrepaintDark()
        applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = false)
        return created
    }

    /**
     * Triggers the initial PWA URL load once the WebView is attached and measured so first-paint
     * scripts and Service Worker cache hits observe accurate device viewport dimensions.
     */
    fun loadInitialUrlIfNeeded() {
        if (hasTriggeredInitialLoad) return
        val url = _uiState.value.webApp?.startUrl ?: return
        val session = mainSession?.takeUnless { it.isDestroyed } ?: return
        hasTriggeredInitialLoad = true
        session.loadUrl(url)
    }

    fun getPopupSession(): BrowserSession? = popupSession?.takeUnless { it.isDestroyed }

    private fun recreatePopupSession(): BrowserSession {
        popupSession?.destroy()
        val created = BrowserSession(
            tabId = POPUP_TAB_ID,
            isPrivate = false,
            profileName = BrowserSession.DEFAULT_PROFILE_NAME,
            context = boundHostContext,
            hostCallbacks = sessionCallbacks,
            forceMobileViewport = true
        )
        popupSession = created
        applyResolvedSettingsToSession(POPUP_TAB_ID, reloadIfModeChanged = false)
        _uiState.update {
            it.copy(
                hasPopupSession = true,
                popupUrl = "",
                popupTitle = "Sign in",
                popupRevision = it.popupRevision + 1
            )
        }
        return created
    }

    fun closePopupSession() {
        popupSession?.destroy()
        popupSession = null
        _uiState.update { it.copy(hasPopupSession = false, popupUrl = "", popupTitle = "") }
    }

    fun updateSystemDarkTheme(isDark: Boolean) {
        if (_uiState.value.isSystemDark != isDark) {
            _uiState.update { it.copy(isSystemDark = isDark) }
            syncGlobalPrepaintDark()
            applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = false)
        }
    }

    fun onPictureInPictureChanged(inPip: Boolean) {
        _uiState.update { it.copy(isInPictureInPicture = inPip) }
    }

    fun onFullscreenVideoExited() {
        _uiState.update { it.copy(isFullscreenVideo = false) }
    }

    fun setControlsSheetOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                blockedCount = if (open) blockedCounter.get() else it.blockedCount,
                isControlsSheetOpen = open,
                isSiteRulesOpen = if (open) false else it.isSiteRulesOpen
            )
        }
    }

    fun toggleSiteRulesSheet(open: Boolean) {
        _uiState.update {
            it.copy(
                isSiteRulesOpen = open,
                isControlsSheetOpen = if (open) false else it.isControlsSheetOpen
            )
        }
    }

    fun reload() {
        val s = mainSession ?: return
        if (_uiState.value.isLoading) s.stopLoading() else s.reload()
    }

    fun forceReload() {
        mainSession?.reload()
    }

    fun loadShortcutUrl(url: String) {
        _uiState.update { it.copy(isControlsSheetOpen = false) }
        mainSession?.loadUrl(url)
    }

    fun goBack(): Boolean {
        val s = mainSession ?: return false
        return if (s.canGoBack()) {
            s.goBack()
            true
        } else {
            false
        }
    }

    fun goForward() {
        mainSession?.goForward()
    }

    fun dismissLoadError() {
        _uiState.update { it.copy(loadError = null) }
    }

    fun openCurrentInSwiftfox() {
        val url = _uiState.value.currentUrl.ifBlank { _uiState.value.webApp?.startUrl.orEmpty() }
        if (url.isNotBlank()) {
            _uiState.update { it.copy(isControlsSheetOpen = false) }
            openUrlInSwiftfox(url)
        }
    }

    private fun openUrlInSwiftfox(url: String) {
        val intent = Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            `package` = app.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        onLaunchExternalIntentBridge?.invoke(intent) ?: runCatching { app.startActivity(intent) }
    }

    fun toggleShowSlimHeader() {
        val webApp = _uiState.value.webApp ?: return
        val updated = webApp.copy(showSlimHeader = !webApp.showSlimHeader)
        _uiState.update { it.copy(webApp = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            db.webAppDao().upsert(updated)
        }
    }

    fun toggleDrawBehindStatusBar() {
        val webApp = _uiState.value.webApp ?: return
        val updated = webApp.copy(drawBehindStatusBar = !webApp.drawBehindStatusBar)
        _uiState.update { it.copy(webApp = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            db.webAppDao().upsert(updated)
        }
    }

    fun toggleActiveSiteAdblock() {
        val host = _uiState.value.activeHost
        if (host.isBlank()) return
        if (!_uiState.value.settings.adblockEnabled) {
            showSnackbar("Enable global Ad Blocking in Swiftfox Settings first")
            return
        }
        val disabled = _uiState.value.activeSiteOverride?.adblockDisabled == true
        setSiteAdblockDisabled(host, !disabled)
    }

    fun setSiteAdblockDisabled(host: String, disabled: Boolean) {
        val norm = OmniboxEngine.normalizeHost(host)
        if (norm.isBlank()) return
        mutateSiteOverride(norm, onUpdated = {
            mainSession?.clearDocumentStartScripts()
            mainSession?.reload()
            showSnackbar(if (disabled) "Ad blocking paused on $norm" else "Ad blocking active on $norm")
        }) { it.copy(adblockDisabled = disabled) }
    }

    fun cycleWebDarkOverride() {
        val host = _uiState.value.activeHost
        if (host.isBlank()) return
        val current = _uiState.value.activeSiteOverride?.webDarkModeOverride
        val next = DarkReaderConfig.nextSiteOverride(current)
        setSiteWebDarkOverride(host, next)
        showSnackbar(
            when (next) {
                true -> "Web Dark: Force dark on $host"
                false -> "Web Dark: Force light on $host"
                null -> "Web Dark: Auto on $host"
            }
        )
    }

    fun setSiteWebDarkOverride(host: String, override: Boolean?) {
        val norm = OmniboxEngine.normalizeHost(host)
        if (norm.isBlank()) return
        mutateSiteOverride(norm, onUpdated = { updated ->
            syncGlobalPrepaintDark()
            val darkState = DarkReaderConfig.resolve(_uiState.value.settings, updated, isChromeDark())
            val (isDarkSurface, isAmoledSurface) = coordinator.resolveWebSurfaceDarkColors(darkState)
            if (!darkState.enabled) {
                _uiState.update { it.copy(darkReaderSkippedNative = false) }
            }
            mainSession?.let { s ->
                s.applySurfaceBackground(isDarkSurface, isAmoledSurface)
                s.applyWebDarkMode(darkState, app.darkReaderInjector)
            }
        }) { it.copy(webDarkModeOverride = override) }
    }

    fun setSiteJavascriptEnabled(host: String, enabled: Boolean) {
        val norm = OmniboxEngine.normalizeHost(host)
        if (norm.isBlank()) return
        mutateSiteOverride(norm, onUpdated = {
            applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = true)
            showSnackbar(if (enabled) "JavaScript enabled on $norm" else "JavaScript disabled on $norm")
        }) { it.copy(javascriptEnabled = enabled) }
    }

    fun setSiteCookiesEnabled(host: String, enabled: Boolean) {
        val norm = OmniboxEngine.normalizeHost(host)
        if (norm.isBlank()) return
        mutateSiteOverride(norm, onUpdated = {
            applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = true)
            showSnackbar(if (enabled) "Cookies enabled on $norm" else "Cookies blocked on $norm")
        }) { it.copy(cookiesEnabled = enabled) }
    }

    fun setSiteHttpsBypass(host: String, bypass: Boolean) {
        mutateSiteOverride(host) { it.copy(httpsOnlyBypass = bypass) }
    }

    fun resetSiteOverride(host: String) {
        val norm = OmniboxEngine.normalizeHost(host)
        if (norm.isBlank()) return
        mutateSiteOverride(norm, onUpdated = {
            _uiState.update { it.copy(darkReaderSkippedNative = false) }
            syncGlobalPrepaintDark()
            applyResolvedSettingsToSession(MAIN_TAB_ID, reloadIfModeChanged = true)
            showSnackbar("Reset site rules for $norm")
        }) { SiteOverrideEntity(host = norm) }
    }

    fun startElementZapper(accentHex: String) {
        val state = _uiState.value
        if (state.activeSiteOverride?.javascriptEnabled == false) {
            showSnackbar("Enable JavaScript for this site to use Element Zapper")
            return
        }
        val session = mainSession ?: return
        _uiState.update {
            it.copy(
                isControlsSheetOpen = false,
                isSiteRulesOpen = false,
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
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildStopScript(s.tokens.zapperKey))
        }
        _uiState.update { it.copy(zapper = ZapperUiState()) }
    }

    fun adjustZapperScope(delta: Int) {
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildAdjustScopeScript(delta, s.tokens.zapperKey))
        }
    }

    fun setZapperScopeDepth(depth: Int) {
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildSetScopeScript(depth, s.tokens.zapperKey))
        }
    }

    fun setZapperPreview(enabled: Boolean) {
        if (!_uiState.value.zapper.isActive || _uiState.value.zapper.selectedSelector == null) return
        _uiState.update { it.copy(zapper = it.zapper.copy(isPreviewing = enabled)) }
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildSetPreviewScript(enabled, s.tokens.zapperKey))
        }
    }

    fun zapSelectedElementOnce() {
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildZapOnceScript(s.tokens.zapperKey))
        }
    }

    fun undoZapperTemporaryZap() {
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildUndoLastZapScript(s.tokens.zapperKey))
        }
    }

    fun blockSelectedElementPermanently() {
        mainSession?.let { s ->
            s.evaluateJavascript(app.zapperController.buildConfirmBlockPermanentScript(s.tokens.zapperKey))
        }
    }

    fun deleteZappedRule(id: String) {
        val host = _uiState.value.zappedRules.firstOrNull { it.id == id }?.host
        viewModelScope.launch {
            db.zappedRuleDao().deleteById(id)
            mainSession?.clearDocumentStartScripts()
            if (!host.isNullOrBlank()) refreshZappedCssForHost(host)
        }
    }

    fun getCleanSharePayload(rawUrl: String? = null): Pair<String, Int>? {
        val target = rawUrl ?: _uiState.value.currentUrl.ifBlank { _uiState.value.webApp?.startUrl.orEmpty() }
        if (target.isBlank()) return null
        val sanitized = CleanUrlSanitizer.sanitize(target)
        return sanitized.url to sanitized.strippedCount
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(contextMenuHit = null) }
    }

    fun saveContextMenuImage(imageUrl: String) {
        viewModelScope.launch {
            val result = app.downloadCoordinator.enqueueHttpDownload(
                url = imageUrl,
                userAgent = mainSession?.userAgentString.orEmpty(),
                contentDisposition = null,
                mimeType = "image/*",
                profileName = BrowserSession.DEFAULT_PROFILE_NAME,
                isPrivate = false
            )
            result.onSuccess { showSnackbar("Saving ${it.fileName}") }
                .onFailure { showSnackbar("Could not save image") }
        }
    }

    fun resolveExternalAppPrompt(openNative: Boolean) {
        val prompt = _uiState.value.externalAppPrompt ?: return
        _uiState.update { it.copy(externalAppPrompt = null) }
        if (openNative) {
            onLaunchExternalIntentBridge?.invoke(prompt.launchIntent)
        } else {
            val fallback = prompt.fallbackUrl ?: return
            val webApp = _uiState.value.webApp
            if (webApp != null && !WebAppManifestParser.isUrlInWebAppScope(fallback, webApp.startUrl, webApp.scopeUrl)) {
                openUrlInSwiftfox(fallback)
            } else {
                sessionForId(prompt.tabId)?.apply {
                    webViewClient.allowNextLoadInApp(fallback)
                    loadUrl(fallback)
                }
            }
        }
    }

    fun resolveHttpsErrorPrompt(allowHttpForSite: Boolean) {
        val prompt = _uiState.value.httpsErrorPrompt ?: return
        _uiState.update { it.copy(httpsErrorPrompt = null) }
        if (allowHttpForSite) {
            setSiteHttpsBypass(prompt.host, true)
            sessionForId(prompt.tabId)?.loadUrl(prompt.originalHttpUrl)
        } else if (mainSession?.canGoBack() == true) {
            mainSession?.goBack()
        }
    }

    fun resolvePermissionPrompt(allow: Boolean, rememberForSite: Boolean) {
        val prompt = _uiState.value.permissionPrompt ?: return
        _uiState.update { it.copy(permissionPrompt = null) }
        if (allow) {
            grantPermissionWithRuntimeCheck(prompt, rememberForSite)
        } else {
            if (prompt.isGeolocation) {
                prompt.geoCallback?.invoke(prompt.geoOrigin, false, rememberForSite)
            } else {
                prompt.webRequest?.deny()
            }
            if (rememberForSite && prompt.host.isNotBlank()) {
                persistPermissionOverride(prompt, allow = false)
            }
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update {
            it.copy(
                snackbarMessage = message,
                snackbarId = snackbarCounter.incrementAndGet()
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun grantPermissionWithRuntimeCheck(prompt: SitePermissionPrompt, rememberForSite: Boolean) {
        val needed = buildList {
            if (prompt.isGeolocation) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in prompt.resources) add(Manifest.permission.CAMERA)
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in prompt.resources) add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (needed.isEmpty()) {
            grantWebPrompt(prompt, rememberForSite)
            return
        }
        val bridge = onRequestAndroidPermissionsBridge
        if (bridge == null) {
            denyPromptSilently(prompt)
            return
        }
        bridge(needed) { grants ->
            val anyOk = if (prompt.isGeolocation) {
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else {
                needed.all { grants[it] == true }
            }
            if (anyOk) {
                grantWebPrompt(prompt, rememberForSite)
            } else {
                denyPromptSilently(prompt)
                showSnackbar("Android permission required for ${prompt.host}")
            }
        }
    }

    private fun grantWebPrompt(prompt: SitePermissionPrompt, rememberForSite: Boolean) {
        if (prompt.isGeolocation) {
            prompt.geoCallback?.invoke(prompt.geoOrigin, true, rememberForSite)
        } else {
            runCatching { prompt.webRequest?.grant(prompt.resources.toTypedArray()) }
        }
        if (rememberForSite && prompt.host.isNotBlank()) {
            persistPermissionOverride(prompt, allow = true)
        }
    }

    private fun denyPromptSilently(prompt: SitePermissionPrompt) {
        if (prompt.isGeolocation) {
            prompt.geoCallback?.invoke(prompt.geoOrigin, false, false)
        } else {
            runCatching { prompt.webRequest?.deny() }
        }
    }

    private fun persistPermissionOverride(prompt: SitePermissionPrompt, allow: Boolean) {
        mutateSiteOverride(prompt.host) { current ->
            if (prompt.isGeolocation) {
                current.copy(allowLocation = allow)
            } else {
                var next = current
                prompt.resources.forEach { res ->
                    next = when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> next.copy(allowCamera = allow)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> next.copy(allowMic = allow)
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> next.copy(allowProtectedMedia = allow)
                        else -> next
                    }
                }
                next
            }
        }
    }

    private fun maybeRequestNotificationPermissionOnce() {
        if (hasAskedNotificationPermission || android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        hasAskedNotificationPermission = true
        onRequestAndroidPermissionsBridge?.invoke(listOf(Manifest.permission.POST_NOTIFICATIONS)) { _ -> }
    }

    private fun mutateSiteOverride(
        host: String,
        onUpdated: (SiteOverrideEntity) -> Unit = {},
        transform: (SiteOverrideEntity) -> SiteOverrideEntity
    ) {
        val norm = OmniboxEngine.normalizeHost(host)
        if (norm.isBlank()) return
        val current = _uiState.value.siteOverrides[norm] ?: SiteOverrideEntity(host = norm)
        val optimistic = transform(current).copy(host = norm)
        _uiState.update { state ->
            val next = if (optimistic.isDefault()) {
                state.siteOverrides - norm
            } else {
                state.siteOverrides + (norm to optimistic)
            }
            state.copy(siteOverrides = next)
        }
        onUpdated(optimistic)
        val writeGen = siteOverrideWrites.beginWrite()
        viewModelScope.launch {
            siteOverrideWrites.runSerializedWrite(
                generation = writeGen,
                writeToDb = {
                    val existing = db.siteOverrideDao().getByHost(norm) ?: SiteOverrideEntity(host = norm)
                    val saved = transform(existing).copy(host = norm)
                    if (saved.isDefault()) db.siteOverrideDao().deleteByHost(norm) else db.siteOverrideDao().upsert(saved)
                },
                readSettledFromDb = {
                    db.siteOverrideDao().getAll().associateBy { OmniboxEngine.normalizeHost(it.host) }
                },
                onSettled = { settled ->
                    _uiState.update { it.copy(siteOverrides = settled) }
                }
            )
        }
    }

    private fun refreshZappedCssForHost(host: String) {
        val norm = OmniboxEngine.normalizeHost(host)
        val session = mainSession ?: return
        if (session.isDestroyed || _uiState.value.activeHost != norm) return
        viewModelScope.launch(Dispatchers.IO + ioExceptionHandler) {
            val rules = db.zappedRuleDao().getEnabledForHost(norm)
            val js = app.zapperController.buildApplyZappedCssScript(rules)
            withContext(Dispatchers.Main) {
                if (!session.isDestroyed && _uiState.value.activeHost == norm) {
                    session.evaluateJavascript(js)
                }
            }
        }
    }

    private fun syncGlobalPrepaintDark() {
        val state = _uiState.value
        val cfg = coordinator.buildGlobalPrepaintDarkConfig(
            settings = state.settings,
            isChromeDark = isChromeDark(),
            siteOverrides = state.siteOverrides
        )
        val script = ScriptInjector.buildGlobalPrepaintDarkScript(cfg)
        val enableSubframe = cfg.globalEnabled || cfg.includedHosts.isNotEmpty()
        mainSession?.apply {
            updateSubframeDarkReaderRuntime(enableSubframe, app.darkReaderInjector)
            updateGlobalPrepaintDarkScript(script)
        }
    }

    private fun applyResolvedSettingsToSession(tabId: String, reloadIfModeChanged: Boolean) {
        val session = sessionForId(tabId) ?: return
        val state = _uiState.value
        val url = if (tabId == POPUP_TAB_ID) state.popupUrl else state.currentUrl
        val host = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(url)).ifBlank { state.activeHost }
        val siteOverride = if (host.isNotBlank()) state.siteOverrides[host] else null
        session.applySiteOverrides(
            mode = SiteMode.MOBILE,
            customUserAgent = null,
            javascriptEnabled = siteOverride?.javascriptEnabled ?: true,
            cookiesEnabled = siteOverride?.cookiesEnabled ?: true,
            blockThirdParty = if (tabId == POPUP_TAB_ID) false else state.settings.blockThirdPartyCookies,
            reloadIfChanged = reloadIfModeChanged,
            applyProfileCookiePolicy = (tabId == MAIN_TAB_ID)
        )
        val darkState = DarkReaderConfig.resolve(state.settings, siteOverride, isChromeDark())
        val (isDarkSurface, isAmoledSurface) = coordinator.resolveWebSurfaceDarkColors(darkState)
        session.applySurfaceBackground(isDarkSurface, isAmoledSurface)
        session.applyWebDarkMode(darkState, app.darkReaderInjector)
        session.applyBackgroundPlayback(state.settings.backgroundMediaEnabled)
    }

    private fun isChromeDark(): Boolean =
        _uiState.value.settings.themeMode.isDarkActive(
            onSystemDarkThemeProvider?.invoke() ?: _uiState.value.isSystemDark
        )

    private fun sessionForId(tabId: String): BrowserSession? = when (tabId) {
        MAIN_TAB_ID -> mainSession?.takeUnless { it.isDestroyed }
        POPUP_TAB_ID -> popupSession?.takeUnless { it.isDestroyed }
        else -> null
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    override fun onCleared() {
        super.onCleared()
        BackgroundMediaService.releasePlaybackOwner(
            owner = this@WebAppViewModel,
            context = app,
            stopService = true
        )
        initWebAppJob?.cancel()
        pageStartedInjectJob?.cancel()
        pageFinishedInjectJob?.cancel()
        loadErrorDiagJob?.cancel()
        splashTimeoutJob?.cancel()
        popupSession?.destroy()
        popupSession = null
        mainSession?.destroy()
        mainSession = null
    }

    companion object {
        private const val MAIN_TAB_ID = "pwa_main"
        private const val POPUP_TAB_ID = "pwa_popup"
    }
}
