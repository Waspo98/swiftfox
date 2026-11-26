package app.overbay.swiftfox.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintDocumentAdapter
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

data class ContextMenuHit(
    val type: Int,
    val url: String,
    val imageUrl: String? = null
)

data class BridgeTokenSet(
    val mediaToken: String,
    val zapperToken: String,
    val darkReaderToken: String,
    val cosmeticToken: String,
    val gmToken: String,
    val readerToken: String,
    val pwaToken: String
) {
    val darkReaderKey: String = "__sfDr_${darkReaderToken.take(12)}"
    val gmKey: String = "__sfGm_${gmToken.take(12)}"
    val zapperKey: String = "__sfZap_${zapperToken.take(12)}"
    val readerKey: String = "__sfRd_${readerToken.take(12)}"
    val cosmeticKey: String = "__sfCosm_${cosmeticToken.take(12)}"

    companion object {
        fun generate(): BridgeTokenSet = BridgeTokenSet(
            mediaToken = BrowserSession.generateHexToken(),
            zapperToken = BrowserSession.generateHexToken(),
            darkReaderToken = BrowserSession.generateHexToken(),
            cosmeticToken = BrowserSession.generateHexToken(),
            gmToken = BrowserSession.generateHexToken(),
            readerToken = BrowserSession.generateHexToken(),
            pwaToken = BrowserSession.generateHexToken()
        )
    }
}

interface SessionHostCallbacks : WebNavigationCallbacks, WebChromeCallbacks {
    fun onDownloadRequested(
        tabId: String,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        profileName: String,
        isPrivate: Boolean
    )
    fun onBlobDownloadReady(
        tabId: String,
        fileName: String,
        mimeType: String,
        base64Data: String,
        sourceUrl: String,
        isPrivate: Boolean
    )
    fun onBlobDownloadFailed(tabId: String, message: String)
    fun onMediaPlaybackStateChanged(tabId: String, isPlaying: Boolean, hasVideo: Boolean, title: String)
    fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int)
    fun onContextMenuLongPress(tabId: String, hit: ContextMenuHit)

    // Milestone 2 JS Bridges
    fun onZapperSelectionChanged(tabId: String, json: String)
    fun onZapperTemporaryZap(tabId: String, json: String)
    fun onZapperUndoTemporaryZap(tabId: String, json: String)
    fun onZapperPermanentRule(tabId: String, json: String)
    fun onDarkReaderStatus(tabId: String, json: String)
    fun onCosmeticDomTokens(tabId: String, pageUrl: String, classesJson: String, idsJson: String)
    fun onUserscriptSetValue(tabId: String, pageUrl: String, scriptId: String, key: String, valueJson: String)
    fun onUserscriptDeleteValue(tabId: String, pageUrl: String, scriptId: String, key: String)
    fun onUserscriptXmlHttpRequest(tabId: String, pageUrl: String, scriptId: String, reqId: String, requestJson: String)
    fun onUserscriptAbortXmlHttpRequest(tabId: String, reqId: String)
    fun onPwaChannelMessage(tabId: String, pageUrl: String, event: String, json: String) {}
}

class BrowserSession(
    val tabId: String,
    val isPrivate: Boolean,
    val profileName: String,
    context: Context,
    hostCallbacks: SessionHostCallbacks,
    private val forceMobileViewport: Boolean = false
) {
    internal data class SiteOverrideSnapshot(
        val effectiveMode: SiteMode,
        val customUa: String?,
        val jsEnabled: Boolean,
        val cookiesEnabled: Boolean
    )

    private data class PendingBlobDownload(
        val blobUrl: String,
        val fileName: String,
        val mimeType: String,
        val expiresAtMs: Long
    )

    @Volatile
    private var currentCallbacks: SessionHostCallbacks = hostCallbacks

    private val callbackProxy: SessionHostCallbacks = object : SessionHostCallbacks {
        override fun getHttpsOnlyEnabled(): Boolean = currentCallbacks.getHttpsOnlyEnabled()
        override fun isHttpsBypassedForHost(tabId: String, host: String): Boolean =
            currentCallbacks.isHttpsBypassedForHost(tabId, host)
        override fun getExternalAppPolicy(tabId: String, host: String): ExternalAppLinkPolicy =
            currentCallbacks.getExternalAppPolicy(tabId, host)
        override fun shouldBlockNetworkRequest(
            tabId: String,
            requestUrl: String,
            pageUrl: String,
            headers: Map<String, String>
        ): Boolean = currentCallbacks.shouldBlockNetworkRequest(tabId, requestUrl, pageUrl, headers)
        override fun shouldBlockNetworkRequest(
            tabId: String,
            isPrivate: Boolean,
            requestUrl: String,
            pageUrl: String,
            cachedPageHost: String,
            headers: Map<String, String>
        ): Boolean = currentCallbacks.shouldBlockNetworkRequest(
            tabId,
            isPrivate,
            requestUrl,
            pageUrl,
            cachedPageHost,
            headers
        )
        override fun shouldOverrideMainFrameNavigation(
            tabId: String,
            url: String,
            isRedirect: Boolean,
            hasGesture: Boolean
        ): Boolean = currentCallbacks.shouldOverrideMainFrameNavigation(tabId, url, isRedirect, hasGesture)
        override fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                committedOverrideSnapshot = currentOverrideSnapshot()
            }
            currentCallbacks.onPageStarted(tabId, url, canGoBack, canGoForward)
        }
        override fun onInjectOnPageStarted(tabId: String, url: String) =
            currentCallbacks.onInjectOnPageStarted(tabId, url)
        override fun onPageFinished(
            tabId: String,
            url: String,
            title: String,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) = currentCallbacks.onPageFinished(tabId, url, title, canGoBack, canGoForward)
        override fun onInjectOnPageFinished(tabId: String, url: String) =
            currentCallbacks.onInjectOnPageFinished(tabId, url)
        override fun onVisitedHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) =
            currentCallbacks.onVisitedHistoryUpdated(tabId, url, canGoBack, canGoForward)
        override fun onExternalAppPrompt(prompt: ExternalAppPrompt) =
            currentCallbacks.onExternalAppPrompt(prompt)
        override fun onLaunchExternalIntent(intent: android.content.Intent): Boolean =
            currentCallbacks.onLaunchExternalIntent(intent)
        override fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt) =
            currentCallbacks.onHttpsOnlyFailure(prompt)
        override fun onMainFrameLoadError(tabId: String, url: String, errorCode: Int, description: String) =
            currentCallbacks.onMainFrameLoadError(tabId, url, errorCode, description)
        override fun onRenderProcessGone(tabId: String, didCrash: Boolean) =
            currentCallbacks.onRenderProcessGone(tabId, didCrash)
        override fun onProgressChanged(tabId: String, progress: Int) =
            currentCallbacks.onProgressChanged(tabId, progress)
        override fun onTitleChanged(tabId: String, title: String) =
            currentCallbacks.onTitleChanged(tabId, title)
        override fun onFaviconReceived(tabId: String, url: String?, icon: Bitmap) =
            currentCallbacks.onFaviconReceived(tabId, url, icon)
        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) =
            currentCallbacks.onShowCustomView(view, callback)
        override fun onHideCustomView() = currentCallbacks.onHideCustomView()
        override fun onShowFileChooser(
            filePathCallback: ValueCallback<Array<android.net.Uri>>,
            chooserIntent: android.content.Intent
        ): Boolean = currentCallbacks.onShowFileChooser(filePathCallback, chooserIntent)
        override fun onPermissionRequested(tabId: String, prompt: SitePermissionPrompt) =
            currentCallbacks.onPermissionRequested(tabId, prompt)
        override fun onPermissionRequestCanceled(
            tabId: String,
            webRequest: PermissionRequest?,
            isGeolocation: Boolean
        ) = currentCallbacks.onPermissionRequestCanceled(tabId, webRequest, isGeolocation)
        override fun onOpenNewWindow(parentTabId: String, resultMsg: android.os.Message?): Boolean =
            currentCallbacks.onOpenNewWindow(parentTabId, resultMsg)
        override fun onCloseWindow(tabId: String) = currentCallbacks.onCloseWindow(tabId)
        override fun onDownloadRequested(
            tabId: String,
            url: String,
            userAgent: String,
            contentDisposition: String?,
            mimeType: String?,
            profileName: String,
            isPrivate: Boolean
        ) = currentCallbacks.onDownloadRequested(
            tabId,
            url,
            userAgent,
            contentDisposition,
            mimeType,
            profileName,
            isPrivate
        )
        override fun onBlobDownloadReady(
            tabId: String,
            fileName: String,
            mimeType: String,
            base64Data: String,
            sourceUrl: String,
            isPrivate: Boolean
        ) = currentCallbacks.onBlobDownloadReady(tabId, fileName, mimeType, base64Data, sourceUrl, isPrivate)
        override fun onBlobDownloadFailed(tabId: String, message: String) =
            currentCallbacks.onBlobDownloadFailed(tabId, message)
        override fun onMediaPlaybackStateChanged(tabId: String, isPlaying: Boolean, hasVideo: Boolean, title: String) =
            currentCallbacks.onMediaPlaybackStateChanged(tabId, isPlaying, hasVideo, title)
        override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) =
            currentCallbacks.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)
        override fun onContextMenuLongPress(tabId: String, hit: ContextMenuHit) =
            currentCallbacks.onContextMenuLongPress(tabId, hit)
        override fun onZapperSelectionChanged(tabId: String, json: String) =
            currentCallbacks.onZapperSelectionChanged(tabId, json)
        override fun onZapperTemporaryZap(tabId: String, json: String) =
            currentCallbacks.onZapperTemporaryZap(tabId, json)
        override fun onZapperUndoTemporaryZap(tabId: String, json: String) =
            currentCallbacks.onZapperUndoTemporaryZap(tabId, json)
        override fun onZapperPermanentRule(tabId: String, json: String) =
            currentCallbacks.onZapperPermanentRule(tabId, json)
        override fun onDarkReaderStatus(tabId: String, json: String) =
            currentCallbacks.onDarkReaderStatus(tabId, json)
        override fun onCosmeticDomTokens(tabId: String, pageUrl: String, classesJson: String, idsJson: String) =
            currentCallbacks.onCosmeticDomTokens(tabId, pageUrl, classesJson, idsJson)
        override fun onUserscriptSetValue(
            tabId: String,
            pageUrl: String,
            scriptId: String,
            key: String,
            valueJson: String
        ) = currentCallbacks.onUserscriptSetValue(tabId, pageUrl, scriptId, key, valueJson)
        override fun onUserscriptDeleteValue(tabId: String, pageUrl: String, scriptId: String, key: String) =
            currentCallbacks.onUserscriptDeleteValue(tabId, pageUrl, scriptId, key)
        override fun onUserscriptXmlHttpRequest(
            tabId: String,
            pageUrl: String,
            scriptId: String,
            reqId: String,
            requestJson: String
        ) = currentCallbacks.onUserscriptXmlHttpRequest(tabId, pageUrl, scriptId, reqId, requestJson)
        override fun onUserscriptAbortXmlHttpRequest(tabId: String, reqId: String) =
            currentCallbacks.onUserscriptAbortXmlHttpRequest(tabId, reqId)
        override fun onPwaChannelMessage(tabId: String, pageUrl: String, event: String, json: String) =
            currentCallbacks.onPwaChannelMessage(tabId, pageUrl, event, json)
    }

    private val mutableContext = MutableContextWrapper(context)
    val webView: SwiftfoxWebView = SwiftfoxWebView(mutableContext)

    @Volatile
    var tokens: BridgeTokenSet = BridgeTokenSet.generate()
        private set

    private val originTokensLock = Any()
    private val originTokens = LinkedHashMap<String, BridgeTokenSet>(8, 0.75f, true)

    @Volatile
    private var lastTokenOrigin: String = ""

    val webViewClient: SwiftfoxWebViewClient = SwiftfoxWebViewClient(
        tabId = tabId,
        callbacks = callbackProxy,
        mediaTokenProvider = { tokens.mediaToken },
        isPrivate = isPrivate
    )
    private val defaultMobileUserAgent: String
    private val documentStartHandlers = mutableListOf<ScriptHandler>()
    private var touchScrollGateHandler: ScriptHandler? = null
    private var mobileViewportHandler: ScriptHandler? = null
    private var prepaintDarkHandler: ScriptHandler? = null
    private var lastPrepaintDarkScript: String? = null
    private var subframeDarkRuntimeHandler: ScriptHandler? = null
    private var isSubframeDarkRuntimeRegistered: Boolean = false
    private val pendingBlobDownloads = ConcurrentHashMap<String, PendingBlobDownload>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isDestroyed: Boolean = false
        private set

    @Volatile
    var webDarkDirty: Boolean = false

    @Volatile
    var isPausedForBackground: Boolean = false
        private set

    private var lastSurfaceBgColor: Int? = null

    var currentSiteMode: SiteMode = SiteMode.MOBILE
        private set

    private var currentCustomUa: String? = null
    private var currentJsEnabled: Boolean = true
    private var currentCookiesEnabled: Boolean = true
    @Volatile
    private var committedOverrideSnapshot: SiteOverrideSnapshot? = null

    private fun currentOverrideSnapshot(): SiteOverrideSnapshot = SiteOverrideSnapshot(
        effectiveMode = if (currentCustomUa != null) SiteMode.MOBILE else currentSiteMode,
        customUa = currentCustomUa,
        jsEnabled = currentJsEnabled,
        cookiesEnabled = currentCookiesEnabled
    )

    val userAgentString: String
        get() = runCatching { webView.settings.userAgentString }.getOrDefault(defaultMobileUserAgent)

    init {
        configureProfile()
        defaultMobileUserAgent = sanitizeMobileUserAgent(webView.settings.userAgentString)
        configureSettings()
        if (forceMobileViewport) {
            mobileViewportHandler = ScriptInjector.registerGlobalMobileViewportScript(webView)
        }
        configureClients()
    }

    fun rotateTokensForOrigin(origin: String): BridgeTokenSet {
        val normalized = origin.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty() || normalized == lastTokenOrigin) {
            return tokens
        }
        val resolved = synchronized(originTokensLock) {
            selectTokensForOrigin(normalized, originTokens, MAX_CACHED_ORIGINS_PER_TAB)
        }
        lastTokenOrigin = normalized
        tokens = resolved
        pendingBlobDownloads.clear()
        return resolved
    }

    fun rebindCallbacks(newCallbacks: SessionHostCallbacks) {
        if (!isDestroyed) {
            currentCallbacks = newCallbacks
        }
    }

    fun rebindContext(newContext: Context) {
        if (!isDestroyed) {
            mutableContext.baseContext = newContext
        }
    }

    private fun cookieManagerForProfile(): CookieManager {
        if (profileName != DEFAULT_PROFILE_NAME &&
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        ) {
            val profileCm = runCatching {
                ProfileStore.getInstance().getProfile(profileName)?.cookieManager
            }.getOrNull()
            if (profileCm != null) return profileCm
        }
        return CookieManager.getInstance()
    }

    private fun configureProfile() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                WebViewCompat.setProfile(webView, profileName)
            }.onFailure { err ->
                Log.w("BrowserSession", "Failed to bind WebView profile '$profileName': ${err.message}")
            }
        } else if (profileName != DEFAULT_PROFILE_NAME) {
            Log.w("BrowserSession", "WebView MULTI_PROFILE unsupported; cannot isolate '$profileName'")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings() {
        webView.importantForAutofill = if (isPrivate) {
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_AUTOFILL_YES
        }
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_ALWAYS

        webView.settings.apply {
            userAgentString = defaultMobileUserAgent
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = !forceMobileViewport
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (isPrivate) {
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }

        // Dark Reader is the single web darkening engine; keep Chromium algorithmic darkening off
        // so Android WebView never double-darkens or overrides Appearance & Theme settings.
        disableAlgorithmicDarkening()

        runCatching {
            val cm = cookieManagerForProfile()
            // Do not blindly call cm.setAcceptCookie(true) here because CookieManager.setAcceptCookie
            // is profile-wide and background tab creation would override an active tab's site block.
            // Allow third-party cookies by default in normal tabs until applySiteOverrides() applies
            // the user's setting so OAuth popups (Sign in with Google / SSO) receive existing session
            // cookies cleanly, while keeping Private tabs strict by default.
            cm.setAcceptThirdPartyCookies(webView, !isPrivate)
        }
    }

    private fun configureClients() {
        webView.webViewClient = webViewClient
        webView.webChromeClient = SwiftfoxChromeClient(tabId, callbackProxy)

        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            if (!isDestroyed) callbackProxy.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (isDestroyed) return@setDownloadListener
            // A main-frame download response never commits onPageStarted; revert pending URL
            // so adblock subresource matching stays anchored to the currently displayed page.
            webViewClient.resetPendingMainFrameUrl()
            if (url.startsWith("blob:", ignoreCase = true)) {
                triggerBlobDownload(url, URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType)
            } else if (url.startsWith("data:", ignoreCase = true)) {
                if (url.length > MAX_BLOB_BASE64_CHARS + 128) return@setDownloadListener
                val headerLimit = minOf(url.length, 256)
                val commaIdx = url.indexOf(',').let { if (it in 5 until headerLimit) it else headerLimit }
                val meta = if (commaIdx > 5) url.substring(5, commaIdx) else ""
                val guessedMime = meta.substringBefore(";").trim().ifBlank { "application/octet-stream" }
                val safeName = URLUtil.guessFileName("data:$meta,", contentDisposition, guessedMime)
                callbackProxy.onBlobDownloadReady(tabId, safeName, guessedMime, url, "data:$guessedMime", isPrivate)
            } else {
                callbackProxy.onDownloadRequested(
                    tabId = tabId,
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    profileName = profileName,
                    isPrivate = isPrivate
                )
            }
        }

        webView.setOnLongClickListener {
            if (isDestroyed) return@setOnLongClickListener false
            val result = webView.hitTestResult
            val extra = result.extra
            when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    if (!extra.isNullOrBlank()) {
                        callbackProxy.onContextMenuLongPress(
                            tabId,
                            ContextMenuHit(type = result.type, url = extra, imageUrl = null)
                        )
                        return@setOnLongClickListener true
                    }
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    if (!extra.isNullOrBlank()) {
                        callbackProxy.onContextMenuLongPress(
                            tabId,
                            ContextMenuHit(type = result.type, url = extra, imageUrl = extra)
                        )
                        return@setOnLongClickListener true
                    }
                }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // Request both the anchor href and image src asynchronously from Chromium
                    val handler = Handler(Looper.getMainLooper()) { msg ->
                        if (isDestroyed) return@Handler true
                        val anchorUrl = msg.data.getString("url")?.takeIf { it.isNotBlank() }
                        val srcUrl = msg.data.getString("src")?.takeIf { it.isNotBlank() } ?: extra
                        val resolvedUrl = anchorUrl ?: srcUrl
                        if (!resolvedUrl.isNullOrBlank()) {
                            callbackProxy.onContextMenuLongPress(
                                tabId,
                                ContextMenuHit(
                                    type = result.type,
                                    url = resolvedUrl,
                                    imageUrl = srcUrl
                                )
                            )
                        }
                        true
                    }
                    webView.requestFocusNodeHref(handler.obtainMessage())
                    return@setOnLongClickListener true
                }
            }
            false
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatching {
                WebViewCompat.addWebMessageListener(
                    webView,
                    NATIVE_CHANNEL_NAME,
                    setOf("*")
                ) { _, message, sourceOrigin, isMainFrame, _ ->
                    if (isDestroyed || !isMainFrame) return@addWebMessageListener
                    val raw = message.data ?: return@addWebMessageListener
                    val originStr = sourceOrigin.toString()
                    if (raw.length > LARGE_BRIDGE_MESSAGE_CHARS) {
                        CompletableFuture.runAsync {
                            if (isDestroyed) return@runAsync
                            val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return@runAsync
                            mainHandler.post {
                                if (!isDestroyed) {
                                    handleNativeChannelMessage(parsed, originStr)
                                }
                            }
                        }
                    } else {
                        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
                        handleNativeChannelMessage(obj, originStr)
                    }
                }
            }
            touchScrollGateHandler = ScriptInjector.registerGlobalTouchScrollGateScript(webView)
        }
    }

    private fun handleNativeChannelMessage(obj: JSONObject, sourceOriginRaw: String) {
        val channel = obj.optString("channel")
        when (channel) {
            "touchGateReady" -> {
                webView.markDomTouchGateReady()
                return
            }
            "touchGate" -> {
                webView.updateDomTouchGate(obj.optBoolean("blocked", false))
                return
            }
        }
        val token = obj.optString("token")
        val curTokens = tokens
        val committedUrl = webViewClient.committedMainFrameUrl
        val committedOrigin = ScriptInjector.extractOrigin(committedUrl)
        val msgOrigin = ScriptInjector.extractOrigin(sourceOriginRaw)

        when (channel) {
            "media" -> {
                if (token.isEmpty() || token != curTokens.mediaToken) return
                val isPlaying = obj.optBoolean("isPlaying", false)
                val hasVideo = obj.optBoolean("hasVideo", false)
                val title = obj.optString("title", "")
                webView.isMediaCurrentlyPlaying = isPlaying
                callbackProxy.onMediaPlaybackStateChanged(tabId, isPlaying, hasVideo, title)
            }

            "blob" -> {
                pruneExpiredBlobDownloads()
                val nonce = obj.optString("nonce")
                val pending = if (nonce.isNotEmpty()) pendingBlobDownloads.remove(nonce) else null
                if (pending == null || System.currentTimeMillis() > pending.expiresAtMs) return
                val base64Data = obj.optString("base64Data", "")
                if (base64Data.isBlank() || base64Data.length > MAX_BLOB_BASE64_CHARS) {
                    callbackProxy.onBlobDownloadFailed(tabId, "Blob download exceeds 25 MB limit")
                    return
                }
                val resolvedMime = obj.optString("mimeType", "").ifBlank { pending.mimeType }
                callbackProxy.onBlobDownloadReady(
                    tabId = tabId,
                    fileName = pending.fileName,
                    mimeType = resolvedMime,
                    base64Data = base64Data,
                    sourceUrl = pending.blobUrl,
                    isPrivate = isPrivate
                )
            }

            "blob_error" -> {
                pruneExpiredBlobDownloads()
                val nonce = obj.optString("nonce")
                val pending = if (nonce.isNotEmpty()) pendingBlobDownloads.remove(nonce) else null
                if (pending != null) {
                    val msg = obj.optString("message", "").ifBlank { "Download failed" }
                    callbackProxy.onBlobDownloadFailed(tabId, msg)
                }
            }

            "zapper" -> {
                if (token.isEmpty() || token != curTokens.zapperToken) return
                val payload = obj.optString("json", "")
                when (obj.optString("event")) {
                    "onSelectionChanged" -> callbackProxy.onZapperSelectionChanged(tabId, payload)
                    "onTemporaryZap" -> callbackProxy.onZapperTemporaryZap(tabId, payload)
                    "onUndoTemporaryZap" -> callbackProxy.onZapperUndoTemporaryZap(tabId, payload)
                    "onPermanentRule" -> callbackProxy.onZapperPermanentRule(tabId, payload)
                }
            }

            "darkReader" -> {
                if (token.isEmpty() || token != curTokens.darkReaderToken) return
                callbackProxy.onDarkReaderStatus(tabId, obj.optString("json", ""))
            }

            "cosmetic" -> {
                if (token.isEmpty() || token != curTokens.cosmeticToken) return
                if (committedOrigin == null || msgOrigin != committedOrigin) return
                callbackProxy.onCosmeticDomTokens(
                    tabId = tabId,
                    pageUrl = committedUrl,
                    classesJson = obj.optString("classesJson", "[]"),
                    idsJson = obj.optString("idsJson", "[]")
                )
            }

            "gm" -> {
                if (token.isEmpty() || token != curTokens.gmToken) return
                if (committedOrigin == null || msgOrigin != committedOrigin) return
                when (obj.optString("action")) {
                    "setValue" -> callbackProxy.onUserscriptSetValue(
                        tabId = tabId,
                        pageUrl = committedUrl,
                        scriptId = obj.optString("scriptId", ""),
                        key = obj.optString("key", ""),
                        valueJson = obj.optString("valueJson", "")
                    )
                    "deleteValue" -> callbackProxy.onUserscriptDeleteValue(
                        tabId = tabId,
                        pageUrl = committedUrl,
                        scriptId = obj.optString("scriptId", ""),
                        key = obj.optString("key", "")
                    )
                    "xhr" -> callbackProxy.onUserscriptXmlHttpRequest(
                        tabId = tabId,
                        pageUrl = committedUrl,
                        scriptId = obj.optString("scriptId", ""),
                        reqId = obj.optString("reqId", ""),
                        requestJson = obj.optString("requestJson", "")
                    )
                    "abortXhr" -> callbackProxy.onUserscriptAbortXmlHttpRequest(
                        tabId = tabId,
                        reqId = obj.optString("reqId", "")
                    )
                }
            }

            "pwa" -> {
                if (token.isEmpty() || token != curTokens.pwaToken) return
                if (committedOrigin == null || msgOrigin != committedOrigin) return
                val event = obj.optString("event", "")
                if (event.isNotEmpty()) {
                    callbackProxy.onPwaChannelMessage(tabId, committedUrl, event, obj.toString())
                }
            }
        }
    }

    private fun pruneExpiredBlobDownloads() {
        val now = System.currentTimeMillis()
        pendingBlobDownloads.entries.removeIf { it.value.expiresAtMs < now }
        if (pendingBlobDownloads.size > 32) {
            pendingBlobDownloads.clear()
        }
    }

    fun triggerBlobDownload(blobUrl: String, fileName: String, mimeType: String?) {
        if (isDestroyed) return
        pruneExpiredBlobDownloads()
        val safeName = fileName.ifBlank { "download_${System.currentTimeMillis()}" }
        val safeMime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val nonce = generateHexToken()
        pendingBlobDownloads[nonce] = PendingBlobDownload(
            blobUrl = blobUrl,
            fileName = safeName,
            mimeType = safeMime,
            expiresAtMs = System.currentTimeMillis() + BLOB_PENDING_TTL_MS
        )

        val quotedUrl = JSONObject.quote(blobUrl)
        val quotedNonce = JSONObject.quote(nonce)
        val quotedMime = JSONObject.quote(safeMime)
        val js = """
            (function() {
              var ch = window.SwiftfoxNativeChannel;
              if (!ch || typeof ch.postMessage !== 'function') return;
              fetch($quotedUrl)
                .then(function(r) {
                  if (!r.ok) throw new Error('HTTP ' + r.status);
                  return r.blob();
                })
                .then(function(blob) {
                  if (blob.size > $MAX_BLOB_BYTES) {
                    ch.postMessage(JSON.stringify({
                      channel: 'blob_error',
                      nonce: $quotedNonce,
                      message: 'Blob download exceeds 25 MB limit'
                    }));
                    return;
                  }
                  var reader = new FileReader();
                  reader.onloadend = function() {
                    ch.postMessage(JSON.stringify({
                      channel: 'blob',
                      nonce: $quotedNonce,
                      mimeType: blob.type || $quotedMime,
                      base64Data: typeof reader.result === 'string' ? reader.result : ''
                    }));
                  };
                  reader.onerror = function() {
                    ch.postMessage(JSON.stringify({
                      channel: 'blob_error',
                      nonce: $quotedNonce,
                      message: 'Failed to read blob data'
                    }));
                  };
                  reader.readAsDataURL(blob);
                })
                .catch(function() {
                  ch.postMessage(JSON.stringify({
                    channel: 'blob_error',
                    nonce: $quotedNonce,
                    message: 'Blob download failed'
                  }));
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applySiteOverrides(
        mode: SiteMode,
        customUserAgent: String?,
        javascriptEnabled: Boolean,
        cookiesEnabled: Boolean,
        blockThirdParty: Boolean,
        reloadIfChanged: Boolean,
        applyProfileCookiePolicy: Boolean = true
    ): Boolean {
        if (isDestroyed) return false
        val effectiveMode = if (forceMobileViewport) SiteMode.MOBILE else mode
        val normalizedCustomUa = if (forceMobileViewport) {
            null
        } else {
            customUserAgent?.trim()?.takeIf { it.isNotEmpty() }
        }
        val prevSnapshot = currentOverrideSnapshot()

        currentSiteMode = effectiveMode
        currentCustomUa = normalizedCustomUa
        currentJsEnabled = javascriptEnabled
        currentCookiesEnabled = cookiesEnabled
        val nextSnapshot = currentOverrideSnapshot()

        webView.settings.apply {
            javaScriptEnabled = javascriptEnabled
            userAgentString = when {
                normalizedCustomUa != null -> normalizedCustomUa
                effectiveMode == SiteMode.DESKTOP -> DESKTOP_USER_AGENT
                else -> defaultMobileUserAgent
            }
            useWideViewPort = true
            loadWithOverviewMode = !forceMobileViewport
        }

        runCatching {
            val cm = cookieManagerForProfile()
            // CookieManager.setAcceptCookie is profile-wide; only the active tab should drive it
            if (applyProfileCookiePolicy) {
                cm.setAcceptCookie(cookiesEnabled)
            }
            cm.setAcceptThirdPartyCookies(webView, cookiesEnabled && !blockThirdParty)
        }

        val currentUrl = webView.url.orEmpty()
        val (shouldReload, nextCommitted) = shouldReloadForOverrideTransition(
            committedSnapshot = committedOverrideSnapshot,
            previousAppliedSnapshot = prevSnapshot,
            nextSnapshot = nextSnapshot,
            reloadIfChanged = reloadIfChanged,
            currentUrl = currentUrl
        )
        committedOverrideSnapshot = nextCommitted
        if (shouldReload) {
            webView.reload()
            return true
        }
        return false
    }

    private fun disableAlgorithmicDarkening() {
        if (isDestroyed) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
            }
        }
    }

    val surfaceBackgroundColor: Int
        get() = lastSurfaceBgColor ?: Color.WHITE

    fun applySurfaceBackground(isDarkSurface: Boolean, isAmoled: Boolean) {
        if (isDestroyed) return
        val bgColor = when {
            !isDarkSurface -> Color.WHITE
            isAmoled -> Color.BLACK
            else -> 0xFF181818.toInt()
        }
        if (lastSurfaceBgColor == bgColor) return
        lastSurfaceBgColor = bgColor
        runCatching { webView.setBackgroundColor(bgColor) }
    }

    fun pauseIfBackground() {
        if (isDestroyed || isPausedForBackground) return
        if (webView.allowBackgroundPlayback && webView.isMediaCurrentlyPlaying) return
        isPausedForBackground = true
        runCatching { webView.onPause() }
    }

    fun resumeIfActive() {
        if (isDestroyed) return
        if (isPausedForBackground) {
            isPausedForBackground = false
            runCatching { webView.onResume() }
        }
    }

    fun applyWebDarkMode(state: ResolvedWebDarkState, darkReaderInjector: DarkReaderInjector) {
        if (isDestroyed) return
        webDarkDirty = false
        val curTokens = tokens
        if (state.enabled) {
            val currentUrl = webView.url.orEmpty()
            val originAtCall = ScriptInjector.extractOrigin(currentUrl)
            if (originAtCall != null) {
                // Attempt single-shot fast apply; only inject full runtime if missing on this window.
                val fastScript = darkReaderInjector.buildFastApplyScript(state, curTokens.darkReaderKey)
                evaluateJavascript(fastScript) { result ->
                    if (isDestroyed) return@evaluateJavascript
                    if (result?.trim() == "\"ok\"") return@evaluateJavascript
                    if (ScriptInjector.extractOrigin(webView.url.orEmpty()) != originAtCall) return@evaluateJavascript
                    val guarded = darkReaderInjector.buildApplyScript(
                        state = state,
                        bridgeToken = curTokens.darkReaderToken,
                        runtimeKey = curTokens.darkReaderKey,
                        expectedOrigin = originAtCall
                    )
                    if (guarded.isNotEmpty()) {
                        evaluateJavascript(guarded)
                    }
                }
            }
        } else {
            evaluateJavascript(darkReaderInjector.buildDisableScript(curTokens.darkReaderKey))
        }
    }

    fun flushCookies() {
        if (isDestroyed || isPrivate) return
        runCatching {
            cookieManagerForProfile().flush()
        }
    }

    fun applyBackgroundPlayback(enabled: Boolean) {
        webView.allowBackgroundPlayback = enabled
    }

    fun clearDocumentStartScripts() {
        documentStartHandlers.forEach { runCatching { it.remove() } }
        documentStartHandlers.clear()
    }

    fun updateGlobalPrepaintDarkScript(script: String) {
        if (isDestroyed) return
        if (lastPrepaintDarkScript == script) return
        lastPrepaintDarkScript = script
        prepaintDarkHandler = ScriptInjector.registerGlobalPrepaintDarkScript(
            webView = webView,
            previousHandler = prepaintDarkHandler,
            script = script
        )
    }

    fun clearGlobalPrepaintDarkScript() {
        lastPrepaintDarkScript = null
        runCatching { prepaintDarkHandler?.remove() }
        prepaintDarkHandler = null
    }

    fun updateSubframeDarkReaderRuntime(enabled: Boolean, darkReaderInjector: DarkReaderInjector) {
        if (isDestroyed) return
        if (enabled == isSubframeDarkRuntimeRegistered) return
        if (enabled) {
            val script = darkReaderInjector.buildSubframeRuntimeScript()
            if (script.isBlank()) return
            subframeDarkRuntimeHandler = ScriptInjector.registerGlobalPrepaintDarkScript(
                webView = webView,
                previousHandler = subframeDarkRuntimeHandler,
                script = script
            )
            isSubframeDarkRuntimeRegistered = subframeDarkRuntimeHandler != null
        } else {
            clearSubframeDarkReaderRuntime()
        }
    }

    fun clearSubframeDarkReaderRuntime() {
        isSubframeDarkRuntimeRegistered = false
        runCatching { subframeDarkRuntimeHandler?.remove() }
        subframeDarkRuntimeHandler = null
    }

    fun updateDocumentStartScripts(
        origin: String,
        cosmeticCss: String,
        zappedCss: String,
        gmShimInitJs: String,
        documentStartUserscriptSnippets: List<String>
    ) {
        if (isDestroyed) return
        val newHandlers = ScriptInjector.registerDocumentStartScripts(
            webView = webView,
            previousHandlers = documentStartHandlers,
            origin = origin,
            cosmeticCss = cosmeticCss,
            zappedCss = zappedCss,
            gmShimInitJs = gmShimInitJs,
            documentStartUserscriptSnippets = documentStartUserscriptSnippets
        )
        documentStartHandlers.clear()
        documentStartHandlers.addAll(newHandlers)
    }

    fun evaluateJavascript(script: String, onResult: ((String?) -> Unit)? = null) {
        if (isDestroyed || script.isBlank()) return
        webView.evaluateJavascript(script) { result ->
            onResult?.invoke(result)
        }
    }

    fun loadUrl(url: String) {
        if (!isDestroyed) webView.loadUrl(url)
    }

    fun reload() {
        if (!isDestroyed) webView.reload()
    }

    fun stopLoading() {
        if (!isDestroyed) {
            webViewClient.resetPendingMainFrameUrl()
            webView.stopLoading()
        }
    }

    fun canGoBack(): Boolean = !isDestroyed && webView.canGoBack()
    fun canGoForward(): Boolean = !isDestroyed && webView.canGoForward()
    fun goBack() {
        if (!isDestroyed) webView.goBack()
    }

    fun goForward() {
        if (!isDestroyed) webView.goForward()
    }

    fun findAllAsync(query: String) {
        if (isDestroyed) return
        if (query.isBlank()) {
            webView.clearMatches()
        } else {
            webView.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        if (!isDestroyed) webView.findNext(forward)
    }

    fun clearFindMatches() {
        if (!isDestroyed) webView.clearMatches()
    }

    fun setMediaPaused(pause: Boolean) {
        if (isDestroyed) return
        if (!pause) {
            // Wake background-paused WebViews so Chromium executes e.play() and media timers run
            resumeIfActive()
        }
        val js = if (pause) {
            "document.querySelectorAll('audio,video').forEach(function(e){ try { e.pause(); } catch(err){} });"
        } else {
            "(function(){ var e = (window.__swiftfoxLastMedia && window.__swiftfoxLastMedia.isConnected) ? window.__swiftfoxLastMedia : document.querySelector('video,audio'); if(e){ var p = e.play(); if(p && p.catch) p.catch(function(){}); } })();"
        }
        webView.evaluateJavascript(js, null)
    }

    fun createPrintAdapter(title: String): PrintDocumentAdapter =
        webView.createPrintDocumentAdapter(title.ifBlank { "Swiftfox Page" })

    fun saveState(bundle: Bundle) {
        if (!isDestroyed) webView.saveState(bundle)
    }

    fun restoreState(bundle: Bundle) {
        if (!isDestroyed) {
            webView.restoreState(bundle)
            val restoredUrl = webView.url.orEmpty()
            if (committedOverrideSnapshot == null &&
                (restoredUrl.startsWith("http://", ignoreCase = true) ||
                    restoredUrl.startsWith("https://", ignoreCase = true))
            ) {
                committedOverrideSnapshot = currentOverrideSnapshot()
            }
        }
    }

    fun captureThumbnail(targetWidth: Int = 360, targetHeight: Int = 480): Bitmap? {
        if (isDestroyed || webView.width <= 0 || webView.height <= 0) return null
        return runCatching {
            val scale = targetWidth.toFloat() / webView.width.toFloat()
            val scaledH = (webView.height * scale).toInt().coerceIn(180, targetHeight)
            val bitmap = Bitmap.createBitmap(targetWidth, scaledH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        currentCallbacks = NoOpSessionHostCallbacks
        synchronized(originTokensLock) {
            originTokens.clear()
        }
        mutableContext.baseContext = mutableContext.baseContext.applicationContext
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.isMediaCurrentlyPlaying = false
        webView.onScrollMetricsChanged = null
        webView.onDomPullToRefreshBlocked = null
        runCatching { touchScrollGateHandler?.remove() }
        touchScrollGateHandler = null
        runCatching { mobileViewportHandler?.remove() }
        mobileViewportHandler = null
        clearDocumentStartScripts()
        clearGlobalPrepaintDarkScript()
        clearSubframeDarkReaderRuntime()
        pendingBlobDownloads.clear()
        // Detach all clients, listeners, and bridges BEFORE teardown
        // so hibernation/disposal never fires stale callbacks into BrowserViewModel.
        webView.setFindListener(null)
        webView.setDownloadListener(null)
        webView.setOnLongClickListener(null)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatching {
                WebViewCompat.removeWebMessageListener(webView, NATIVE_CHANNEL_NAME)
            }
        }
        if (isPrivate) {
            runCatching {
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
            }
        }
        runCatching { webView.stopLoading() }
        runCatching { webView.onPause() }
        webView.removeAllViews()
        webView.destroy()
    }

    companion object {
        const val DEFAULT_PROFILE_NAME = Profile.DEFAULT_PROFILE_NAME
        const val PRIVATE_PROFILE_NAME = "swiftfox_private_session"
        private const val NATIVE_CHANNEL_NAME = "SwiftfoxNativeChannel"
        private const val MAX_BLOB_BYTES = 25 * 1024 * 1024
        private const val MAX_BLOB_BASE64_CHARS = 36_000_000
        private const val LARGE_BRIDGE_MESSAGE_CHARS = 65_536
        private const val BLOB_PENDING_TTL_MS = 60_000L
        private const val MAX_CACHED_ORIGINS_PER_TAB = 16
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private val WEBVIEW_WV_TOKEN_REGEX = Regex(""";\s*wv\b""", RegexOption.IGNORE_CASE)
        private val WEBVIEW_VERSION_TOKEN_REGEX = Regex("""\bVersion/\d+(?:\.\d+)*\s+""", RegexOption.IGNORE_CASE)
        private val MOBILE_UA_TOKEN_REGEX = Regex("""\bMobile\b""", RegexOption.IGNORE_CASE)
        private val secureRandom = SecureRandom()
        private val HEX_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        )

        /**
         * Strips Android WebView's `; wv` and `Version/4.0` markers and preserves a standard
         * `Mobile` Chromium UA token so OAuth providers and responsive websites treat Swiftfox
         * as a mobile Chromium browser instead of an embedded WebView or desktop/tablet agent.
         */
        internal fun sanitizeMobileUserAgent(rawUa: String): String {
            if (rawUa.isBlank()) return rawUa
            val cleaned = rawUa
                .replace(WEBVIEW_WV_TOKEN_REGEX, "")
                .replace(WEBVIEW_VERSION_TOKEN_REGEX, "")
                .replace("  ", " ")
                .trim()
            if (cleaned.contains("Android", ignoreCase = true) && !MOBILE_UA_TOKEN_REGEX.containsMatchIn(cleaned)) {
                return if (cleaned.contains("Safari/")) {
                    cleaned.replaceFirst("Safari/", "Mobile Safari/")
                } else {
                    "$cleaned Mobile"
                }
            }
            return cleaned
        }

        internal fun generateHexToken(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            val chars = CharArray(32)
            for (i in 0 until 16) {
                val b = bytes[i].toInt() and 0xFF
                chars[i * 2] = HEX_CHARS[b ushr 4]
                chars[i * 2 + 1] = HEX_CHARS[b and 0x0F]
            }
            return String(chars)
        }

        internal fun selectTokensForOrigin(
            normalizedOrigin: String,
            cache: LinkedHashMap<String, BridgeTokenSet>,
            maxEntries: Int = MAX_CACHED_ORIGINS_PER_TAB
        ): BridgeTokenSet {
            cache[normalizedOrigin]?.let { return it }
            val created = BridgeTokenSet.generate()
            cache[normalizedOrigin] = created
            while (cache.size > maxEntries) {
                val eldestKey = cache.entries.firstOrNull()?.key ?: break
                cache.remove(eldestKey)
            }
            return created
        }

        internal fun shouldReloadForOverrideTransition(
            committedSnapshot: SiteOverrideSnapshot?,
            previousAppliedSnapshot: SiteOverrideSnapshot,
            nextSnapshot: SiteOverrideSnapshot,
            reloadIfChanged: Boolean,
            currentUrl: String
        ): Pair<Boolean, SiteOverrideSnapshot?> {
            val isHttpLoaded = currentUrl.startsWith("http://", ignoreCase = true) ||
                currentUrl.startsWith("https://", ignoreCase = true)
            if (!isHttpLoaded) {
                return false to committedSnapshot
            }
            val baseline = committedSnapshot ?: previousAppliedSnapshot
            if (baseline == nextSnapshot) {
                return false to nextSnapshot
            }
            return if (reloadIfChanged) {
                true to nextSnapshot
            } else {
                false to baseline
            }
        }
    }
}

internal object NoOpSessionHostCallbacks : SessionHostCallbacks {
    override fun getHttpsOnlyEnabled(): Boolean = false
    override fun isHttpsBypassedForHost(tabId: String, host: String): Boolean = false
    override fun getExternalAppPolicy(tabId: String, host: String): ExternalAppLinkPolicy =
        ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX
    override fun shouldBlockNetworkRequest(
        tabId: String,
        requestUrl: String,
        pageUrl: String,
        headers: Map<String, String>
    ): Boolean = false
    override fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) = Unit
    override fun onInjectOnPageStarted(tabId: String, url: String) = Unit
    override fun onPageFinished(
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) = Unit
    override fun onInjectOnPageFinished(tabId: String, url: String) = Unit
    override fun onVisitedHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) = Unit
    override fun onExternalAppPrompt(prompt: ExternalAppPrompt) = Unit
    override fun onLaunchExternalIntent(intent: android.content.Intent): Boolean = false
    override fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt) = Unit
    override fun onRenderProcessGone(tabId: String, didCrash: Boolean) = Unit
    override fun onProgressChanged(tabId: String, progress: Int) = Unit
    override fun onTitleChanged(tabId: String, title: String) = Unit
    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) = Unit
    override fun onHideCustomView() = Unit
    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<android.net.Uri>>,
        chooserIntent: android.content.Intent
    ): Boolean = false
    override fun onPermissionRequested(tabId: String, prompt: SitePermissionPrompt) = Unit
    override fun onPermissionRequestCanceled(
        tabId: String,
        webRequest: PermissionRequest?,
        isGeolocation: Boolean
    ) = Unit
    override fun onOpenNewWindow(parentTabId: String, resultMsg: android.os.Message?): Boolean = false
    override fun onCloseWindow(tabId: String) = Unit
    override fun onDownloadRequested(
        tabId: String,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        profileName: String,
        isPrivate: Boolean
    ) = Unit
    override fun onBlobDownloadReady(
        tabId: String,
        fileName: String,
        mimeType: String,
        base64Data: String,
        sourceUrl: String,
        isPrivate: Boolean
    ) = Unit
    override fun onBlobDownloadFailed(tabId: String, message: String) = Unit
    override fun onMediaPlaybackStateChanged(tabId: String, isPlaying: Boolean, hasVideo: Boolean, title: String) = Unit
    override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) = Unit
    override fun onContextMenuLongPress(tabId: String, hit: ContextMenuHit) = Unit
    override fun onZapperSelectionChanged(tabId: String, json: String) = Unit
    override fun onZapperTemporaryZap(tabId: String, json: String) = Unit
    override fun onZapperUndoTemporaryZap(tabId: String, json: String) = Unit
    override fun onZapperPermanentRule(tabId: String, json: String) = Unit
    override fun onDarkReaderStatus(tabId: String, json: String) = Unit
    override fun onCosmeticDomTokens(tabId: String, pageUrl: String, classesJson: String, idsJson: String) = Unit
    override fun onUserscriptSetValue(
        tabId: String,
        pageUrl: String,
        scriptId: String,
        key: String,
        valueJson: String
    ) = Unit
    override fun onUserscriptDeleteValue(tabId: String, pageUrl: String, scriptId: String, key: String) = Unit
    override fun onUserscriptXmlHttpRequest(
        tabId: String,
        pageUrl: String,
        scriptId: String,
        reqId: String,
        requestJson: String
    ) = Unit
    override fun onUserscriptAbortXmlHttpRequest(tabId: String, reqId: String) = Unit
}
