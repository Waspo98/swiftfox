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
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
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
    val readerToken: String
) {
    val darkReaderKey: String = "__sfDr_${darkReaderToken.take(12)}"
    val gmKey: String = "__sfGm_${gmToken.take(12)}"
    val zapperKey: String = "__sfZap_${zapperToken.take(12)}"
    val readerKey: String = "__sfRd_${readerToken.take(12)}"

    companion object {
        fun generate(): BridgeTokenSet = BridgeTokenSet(
            mediaToken = BrowserSession.generateHexToken(),
            zapperToken = BrowserSession.generateHexToken(),
            darkReaderToken = BrowserSession.generateHexToken(),
            cosmeticToken = BrowserSession.generateHexToken(),
            gmToken = BrowserSession.generateHexToken(),
            readerToken = BrowserSession.generateHexToken()
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
}

class BrowserSession(
    val tabId: String,
    val isPrivate: Boolean,
    val profileName: String,
    context: Context,
    private val hostCallbacks: SessionHostCallbacks
) {
    private data class PendingBlobDownload(
        val blobUrl: String,
        val fileName: String,
        val mimeType: String,
        val expiresAtMs: Long
    )

    private val mutableContext = MutableContextWrapper(context)
    val webView: SwiftfoxWebView = SwiftfoxWebView(mutableContext)

    @Volatile
    var tokens: BridgeTokenSet = BridgeTokenSet.generate()
        private set

    @Volatile
    private var lastTokenOrigin: String = ""

    val webViewClient: SwiftfoxWebViewClient = SwiftfoxWebViewClient(
        tabId = tabId,
        callbacks = hostCallbacks,
        mediaTokenProvider = { tokens.mediaToken }
    )
    private val defaultMobileUserAgent: String
    private val documentStartHandlers = mutableListOf<ScriptHandler>()
    private var prepaintDarkHandler: ScriptHandler? = null
    private var lastPrepaintDarkScript: String? = null
    private var subframeDarkRuntimeHandler: ScriptHandler? = null
    private var isSubframeDarkRuntimeRegistered: Boolean = false
    private val pendingBlobDownloads = ConcurrentHashMap<String, PendingBlobDownload>()

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

    val userAgentString: String
        get() = runCatching { webView.settings.userAgentString }.getOrDefault(defaultMobileUserAgent)

    init {
        configureProfile()
        defaultMobileUserAgent = sanitizeMobileUserAgent(webView.settings.userAgentString)
        configureSettings()
        configureClients()
    }

    fun rotateTokensForOrigin(origin: String): BridgeTokenSet {
        val normalized = origin.trim().lowercase(Locale.ROOT)
        if (normalized.isNotEmpty() && normalized != lastTokenOrigin) {
            lastTokenOrigin = normalized
            tokens = BridgeTokenSet.generate()
            pendingBlobDownloads.clear()
        }
        return tokens
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
            loadWithOverviewMode = true
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
        webView.webChromeClient = SwiftfoxChromeClient(tabId, hostCallbacks)

        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            if (!isDestroyed) hostCallbacks.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)
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
                val meta = if (url.length >= 5) url.substring(5).substringBefore(",") else ""
                val guessedMime = meta.substringBefore(";").trim().ifBlank { "application/octet-stream" }
                val safeName = URLUtil.guessFileName(url, contentDisposition, guessedMime)
                hostCallbacks.onBlobDownloadReady(tabId, safeName, guessedMime, url, url, isPrivate)
            } else {
                hostCallbacks.onDownloadRequested(
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
                        hostCallbacks.onContextMenuLongPress(
                            tabId,
                            ContextMenuHit(type = result.type, url = extra, imageUrl = null)
                        )
                        return@setOnLongClickListener true
                    }
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    if (!extra.isNullOrBlank()) {
                        hostCallbacks.onContextMenuLongPress(
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
                            hostCallbacks.onContextMenuLongPress(
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
                    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
                    handleNativeChannelMessage(obj, sourceOrigin.toString())
                }
            }
        }
    }

    private fun handleNativeChannelMessage(obj: JSONObject, sourceOriginRaw: String) {
        val channel = obj.optString("channel")
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
                hostCallbacks.onMediaPlaybackStateChanged(tabId, isPlaying, hasVideo, title)
            }

            "blob" -> {
                pruneExpiredBlobDownloads()
                val nonce = obj.optString("nonce")
                val pending = if (nonce.isNotEmpty()) pendingBlobDownloads.remove(nonce) else null
                if (pending == null || System.currentTimeMillis() > pending.expiresAtMs) return
                val base64Data = obj.optString("base64Data", "")
                if (base64Data.isBlank() || base64Data.length > MAX_BLOB_BASE64_CHARS) {
                    hostCallbacks.onBlobDownloadFailed(tabId, "Blob download exceeds 25 MB limit")
                    return
                }
                val resolvedMime = obj.optString("mimeType", "").ifBlank { pending.mimeType }
                hostCallbacks.onBlobDownloadReady(
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
                    hostCallbacks.onBlobDownloadFailed(tabId, msg)
                }
            }

            "zapper" -> {
                if (token.isEmpty() || token != curTokens.zapperToken) return
                val payload = obj.optString("json", "")
                when (obj.optString("event")) {
                    "onSelectionChanged" -> hostCallbacks.onZapperSelectionChanged(tabId, payload)
                    "onTemporaryZap" -> hostCallbacks.onZapperTemporaryZap(tabId, payload)
                    "onUndoTemporaryZap" -> hostCallbacks.onZapperUndoTemporaryZap(tabId, payload)
                    "onPermanentRule" -> hostCallbacks.onZapperPermanentRule(tabId, payload)
                }
            }

            "darkReader" -> {
                if (token.isEmpty() || token != curTokens.darkReaderToken) return
                hostCallbacks.onDarkReaderStatus(tabId, obj.optString("json", ""))
            }

            "cosmetic" -> {
                if (token.isEmpty() || token != curTokens.cosmeticToken) return
                if (committedOrigin == null || msgOrigin != committedOrigin) return
                hostCallbacks.onCosmeticDomTokens(
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
                    "setValue" -> hostCallbacks.onUserscriptSetValue(
                        tabId = tabId,
                        pageUrl = committedUrl,
                        scriptId = obj.optString("scriptId", ""),
                        key = obj.optString("key", ""),
                        valueJson = obj.optString("valueJson", "")
                    )
                    "deleteValue" -> hostCallbacks.onUserscriptDeleteValue(
                        tabId = tabId,
                        pageUrl = committedUrl,
                        scriptId = obj.optString("scriptId", ""),
                        key = obj.optString("key", "")
                    )
                    "xhr" -> hostCallbacks.onUserscriptXmlHttpRequest(
                        tabId = tabId,
                        pageUrl = committedUrl,
                        scriptId = obj.optString("scriptId", ""),
                        reqId = obj.optString("reqId", ""),
                        requestJson = obj.optString("requestJson", "")
                    )
                    "abortXhr" -> hostCallbacks.onUserscriptAbortXmlHttpRequest(
                        tabId = tabId,
                        reqId = obj.optString("reqId", "")
                    )
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
        val normalizedCustomUa = customUserAgent?.trim()?.takeIf { it.isNotEmpty() }
        val modeChanged = currentSiteMode != mode
        val effectiveModeChanged = modeChanged && normalizedCustomUa == null
        val uaChanged = currentCustomUa != normalizedCustomUa
        val jsChanged = currentJsEnabled != javascriptEnabled
        val cookieChanged = currentCookiesEnabled != cookiesEnabled

        currentSiteMode = mode
        currentCustomUa = normalizedCustomUa
        currentJsEnabled = javascriptEnabled
        currentCookiesEnabled = cookiesEnabled

        webView.settings.apply {
            javaScriptEnabled = javascriptEnabled
            userAgentString = when {
                normalizedCustomUa != null -> normalizedCustomUa
                mode == SiteMode.DESKTOP -> DESKTOP_USER_AGENT
                else -> defaultMobileUserAgent
            }
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        runCatching {
            val cm = cookieManagerForProfile()
            // CookieManager.setAcceptCookie is profile-wide; only the active tab should drive it
            if (applyProfileCookiePolicy) {
                cm.setAcceptCookie(cookiesEnabled)
            }
            cm.setAcceptThirdPartyCookies(webView, cookiesEnabled && !blockThirdParty)
        }

        if ((effectiveModeChanged || uaChanged || jsChanged || cookieChanged) && reloadIfChanged) {
            val current = webView.url.orEmpty()
            if (current.startsWith("http://", ignoreCase = true) ||
                current.startsWith("https://", ignoreCase = true)
            ) {
                webView.reload()
                return true
            }
        }
        return false
    }

    fun applyWebDarkening(enabled: Boolean) {
        if (isDestroyed) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled)
            }
        }
    }

    val surfaceBackgroundColor: Int
        get() = lastSurfaceBgColor ?: Color.WHITE

    fun applySurfaceBackground(isChromeDark: Boolean, isAmoled: Boolean) {
        if (isDestroyed) return
        val bgColor = when {
            !isChromeDark -> Color.WHITE
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
        if (state.useDarkReader) {
            // Never run Chromium Algorithmic Darkening alongside Dark Reader
            applyWebDarkening(false)
            val currentUrl = webView.url.orEmpty()
            val originAtCall = ScriptInjector.extractOrigin(currentUrl)
            if (originAtCall != null) {
                // Attempt single-shot fast apply; only inject full ~112KB runtime if missing on this window.
                val fastScript = darkReaderInjector.buildFastApplyScript(state, curTokens.darkReaderKey)
                evaluateJavascript(fastScript) { result ->
                    if (isDestroyed) return@evaluateJavascript
                    if (result?.trim() == "\"ok\"") return@evaluateJavascript
                    if (ScriptInjector.extractOrigin(webView.url.orEmpty()) != originAtCall) return@evaluateJavascript
                    val fullScript = darkReaderInjector.buildApplyScript(
                        state,
                        curTokens.darkReaderToken,
                        curTokens.darkReaderKey
                    )
                    val guarded = ScriptInjector.wrapOriginGuardedScript(originAtCall, fullScript)
                    if (guarded.isNotEmpty()) {
                        evaluateJavascript(guarded)
                    }
                }
            }
        } else if (state.useAlgorithmicLite) {
            evaluateJavascript(darkReaderInjector.buildDisableScript(curTokens.darkReaderKey))
            applyWebDarkening(true)
        } else {
            evaluateJavascript(darkReaderInjector.buildDisableScript(curTokens.darkReaderKey))
            applyWebDarkening(false)
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
        if (!isDestroyed) webView.stopLoading()
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
        if (!isDestroyed) webView.restoreState(bundle)
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
        webView.isMediaCurrentlyPlaying = false
        webView.onScrollMetricsChanged = null
        clearDocumentStartScripts()
        clearGlobalPrepaintDarkScript()
        clearSubframeDarkReaderRuntime()
        pendingBlobDownloads.clear()
        // Detach all clients, listeners, and bridges BEFORE loadUrl("about:blank")
        // so teardown/hibernation never fires stale callbacks into BrowserViewModel.
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
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        webView.destroy()
    }

    companion object {
        const val DEFAULT_PROFILE_NAME = Profile.DEFAULT_PROFILE_NAME
        const val PRIVATE_PROFILE_NAME = "swiftfox_private_session"
        private const val NATIVE_CHANNEL_NAME = "SwiftfoxNativeChannel"
        private const val MAX_BLOB_BYTES = 25 * 1024 * 1024
        private const val MAX_BLOB_BASE64_CHARS = 36_000_000
        private const val BLOB_PENDING_TTL_MS = 60_000L
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private val WEBVIEW_WV_TOKEN_REGEX = Regex(""";\s*wv\b""", RegexOption.IGNORE_CASE)
        private val WEBVIEW_VERSION_TOKEN_REGEX = Regex("""\bVersion/\d+(?:\.\d+)*\s+""", RegexOption.IGNORE_CASE)
        private val secureRandom = SecureRandom()

        /**
         * Strips Android WebView's `; wv` and `Version/4.0` markers so Google Sign-In and other
         * OAuth providers treat Swiftfox as a standard mobile Chromium browser instead of an
         * untrusted embedded WebView.
         */
        internal fun sanitizeMobileUserAgent(rawUa: String): String {
            if (rawUa.isBlank()) return rawUa
            return rawUa
                .replace(WEBVIEW_WV_TOKEN_REGEX, "")
                .replace(WEBVIEW_VERSION_TOKEN_REGEX, "")
                .replace("  ", " ")
                .trim()
        }

        internal fun generateHexToken(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }
    }
}
