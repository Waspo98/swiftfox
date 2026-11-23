package app.overbay.swiftfox.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.print.PrintDocumentAdapter
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebSettings
import androidx.webkit.Profile
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState

data class ContextMenuHit(
    val type: Int,
    val url: String,
    val imageUrl: String? = null
)

interface SessionHostCallbacks : WebNavigationCallbacks, WebChromeCallbacks {
    fun onDownloadRequested(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?
    )
    fun onBlobDownloadReady(fileName: String, mimeType: String, base64Data: String, sourceUrl: String)
    fun onMediaPlaybackStateChanged(tabId: String, isPlaying: Boolean, title: String)
    fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int)
    fun onContextMenuLongPress(tabId: String, hit: ContextMenuHit)
    fun onScrollTopChanged(tabId: String, atTop: Boolean)

    // Milestone 2 JS Bridges
    fun onZapperSelectionChanged(tabId: String, json: String)
    fun onZapperTemporaryZap(tabId: String, json: String)
    fun onZapperUndoTemporaryZap(tabId: String, json: String)
    fun onZapperPermanentRule(tabId: String, json: String)
    fun onDarkReaderStatus(tabId: String, json: String)
    fun onUserscriptSetValue(scriptId: String, key: String, valueJson: String)
    fun onUserscriptDeleteValue(scriptId: String, key: String)
    fun onUserscriptXmlHttpRequest(tabId: String, scriptId: String, reqId: String, requestJson: String)
}

class BrowserSession(
    val tabId: String,
    val isPrivate: Boolean,
    val profileName: String,
    context: Context,
    private val hostCallbacks: SessionHostCallbacks
) {
    val webView: SwiftfoxWebView = SwiftfoxWebView(context)
    private val defaultMobileUserAgent: String
    private var documentStartHandler: ScriptHandler? = null

    var currentSiteMode: SiteMode = SiteMode.MOBILE
        private set

    private var currentCustomUa: String? = null
    private var currentJsEnabled: Boolean = true
    private var currentCookiesEnabled: Boolean = true

    init {
        configureProfile()
        defaultMobileUserAgent = webView.settings.userAgentString
        configureSettings()
        configureClients()
    }

    private fun configureProfile() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                WebViewCompat.setProfile(webView, profileName)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings() {
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_ALWAYS

        webView.settings.apply {
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
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (isPrivate) {
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    }

    private fun configureClients() {
        webView.webViewClient = SwiftfoxWebViewClient(tabId, hostCallbacks)
        webView.webChromeClient = SwiftfoxChromeClient(tabId, hostCallbacks)

        webView.onScrollTopChanged = { atTop ->
            hostCallbacks.onScrollTopChanged(tabId, atTop)
        }

        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            hostCallbacks.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:", ignoreCase = true)) {
                val safeName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    .replace("'", "")
                val safeMime = (mimeType ?: "application/octet-stream").replace("'", "")
                val safeUrl = url.replace("'", "")
                webView.evaluateJavascript(
                    "window.__swiftfoxFetchBlob && window.__swiftfoxFetchBlob('$safeUrl', '$safeName', '$safeMime');",
                    null
                )
            } else if (url.startsWith("data:", ignoreCase = true)) {
                val guessedMime = url.substringAfter("data:").substringBefore(";", "application/octet-stream")
                val safeName = URLUtil.guessFileName(url, contentDisposition, guessedMime)
                hostCallbacks.onBlobDownloadReady(safeName, guessedMime, url, url)
            } else {
                hostCallbacks.onDownloadRequested(url, userAgent, contentDisposition, mimeType)
            }
        }

        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val extra = result.extra
            if (!extra.isNullOrBlank()) {
                when (result.type) {
                    android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE,
                    android.webkit.WebView.HitTestResult.IMAGE_TYPE,
                    android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        hostCallbacks.onContextMenuLongPress(
                            tabId,
                            ContextMenuHit(
                                type = result.type,
                                url = extra,
                                imageUrl = if (result.type != android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE) extra else null
                            )
                        )
                        return@setOnLongClickListener true
                    }
                }
            }
            false
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onBlobData(fileName: String, mimeType: String, base64Data: String, sourceUrl: String) {
                    hostCallbacks.onBlobDownloadReady(fileName, mimeType, base64Data, sourceUrl)
                }
            },
            "SwiftfoxBlobBridge"
        )

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onMediaStateChanged(isPlaying: Boolean, title: String) {
                    webView.isMediaCurrentlyPlaying = isPlaying
                    hostCallbacks.onMediaPlaybackStateChanged(tabId, isPlaying, title)
                }
            },
            "SwiftfoxMediaBridge"
        )

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onSelectionChanged(json: String) {
                    hostCallbacks.onZapperSelectionChanged(tabId, json)
                }

                @JavascriptInterface
                fun onTemporaryZap(json: String) {
                    hostCallbacks.onZapperTemporaryZap(tabId, json)
                }

                @JavascriptInterface
                fun onUndoTemporaryZap(json: String) {
                    hostCallbacks.onZapperUndoTemporaryZap(tabId, json)
                }

                @JavascriptInterface
                fun onPermanentRule(json: String) {
                    hostCallbacks.onZapperPermanentRule(tabId, json)
                }
            },
            "SwiftfoxZapperBridge"
        )

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onDarkReaderStatus(json: String) {
                    hostCallbacks.onDarkReaderStatus(tabId, json)
                }
            },
            "SwiftfoxDarkReaderBridge"
        )

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onSetValue(scriptId: String, key: String, valueJson: String) {
                    hostCallbacks.onUserscriptSetValue(scriptId, key, valueJson)
                }

                @JavascriptInterface
                fun onDeleteValue(scriptId: String, key: String) {
                    hostCallbacks.onUserscriptDeleteValue(scriptId, key)
                }

                @JavascriptInterface
                fun onXmlHttpRequest(scriptId: String, reqId: String, requestJson: String) {
                    hostCallbacks.onUserscriptXmlHttpRequest(tabId, scriptId, reqId, requestJson)
                }
            },
            "SwiftfoxGmBridge"
        )
    }

    fun applySiteMode(mode: SiteMode, reloadIfChanged: Boolean) {
        applySiteOverrides(
            mode = mode,
            customUserAgent = currentCustomUa,
            javascriptEnabled = currentJsEnabled,
            cookiesEnabled = currentCookiesEnabled,
            blockThirdParty = true,
            reloadIfChanged = reloadIfChanged
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applySiteOverrides(
        mode: SiteMode,
        customUserAgent: String?,
        javascriptEnabled: Boolean,
        cookiesEnabled: Boolean,
        blockThirdParty: Boolean,
        reloadIfChanged: Boolean
    ) {
        val normalizedCustomUa = customUserAgent?.trim()?.takeIf { it.isNotEmpty() }
        val modeChanged = currentSiteMode != mode
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
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(cookiesEnabled)
            cm.setAcceptThirdPartyCookies(webView, cookiesEnabled && !blockThirdParty)
        }

        if ((modeChanged || uaChanged || jsChanged || cookieChanged) && reloadIfChanged) {
            val current = webView.url.orEmpty()
            if (current.startsWith("http://") || current.startsWith("https://")) {
                webView.reload()
            }
        }
    }

    fun applyWebDarkening(enabled: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled)
            }
        }
    }

    fun applyWebDarkMode(state: ResolvedWebDarkState, darkReaderInjector: DarkReaderInjector) {
        if (state.useDarkReader) {
            // Never run Chromium Algorithmic Darkening alongside Dark Reader
            applyWebDarkening(false)
            val currentUrl = webView.url.orEmpty()
            if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
                evaluateJavascript(darkReaderInjector.buildApplyScript(state))
            }
        } else if (state.useAlgorithmicLite) {
            evaluateJavascript(darkReaderInjector.buildDisableScript())
            applyWebDarkening(true)
        } else {
            evaluateJavascript(darkReaderInjector.buildDisableScript())
            applyWebDarkening(false)
        }
    }

    fun applyThirdPartyCookieBlocking(blockThirdParty: Boolean) {
        runCatching {
            CookieManager.getInstance().setAcceptThirdPartyCookies(
                webView,
                currentCookiesEnabled && !blockThirdParty
            )
        }
    }

    fun applyBackgroundPlayback(enabled: Boolean) {
        webView.allowBackgroundPlayback = enabled
    }

    fun updateDocumentStartScript(
        cosmeticCss: String,
        zappedCss: String,
        documentStartUserscriptJs: String
    ) {
        documentStartHandler = ScriptInjector.registerDocumentStartScript(
            webView = webView,
            previousHandler = documentStartHandler,
            cosmeticCss = cosmeticCss,
            zappedCss = zappedCss,
            documentStartUserscriptJs = documentStartUserscriptJs
        )
    }

    fun evaluateJavascript(script: String, onResult: ((String?) -> Unit)? = null) {
        if (script.isBlank()) return
        webView.evaluateJavascript(script) { result ->
            onResult?.invoke(result)
        }
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    fun reload() {
        webView.reload()
    }

    fun stopLoading() {
        webView.stopLoading()
    }

    fun canGoBack(): Boolean = webView.canGoBack()
    fun canGoForward(): Boolean = webView.canGoForward()
    fun goBack() = webView.goBack()
    fun goForward() = webView.goForward()

    fun findAllAsync(query: String) {
        if (query.isBlank()) {
            webView.clearMatches()
        } else {
            webView.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) = webView.findNext(forward)
    fun clearFindMatches() = webView.clearMatches()

    fun setMediaPaused(pause: Boolean) {
        val js = if (pause) {
            "document.querySelectorAll('audio,video').forEach(function(e){e.pause();});"
        } else {
            "var e=document.querySelector('video,audio'); if(e){e.play();}"
        }
        webView.evaluateJavascript(js, null)
    }

    fun createPrintAdapter(title: String): PrintDocumentAdapter =
        webView.createPrintDocumentAdapter(title.ifBlank { "Swiftfox Page" })

    fun saveState(bundle: Bundle) {
        webView.saveState(bundle)
    }

    fun restoreState(bundle: Bundle) {
        webView.restoreState(bundle)
    }

    fun captureThumbnail(targetWidth: Int = 360, targetHeight: Int = 480): Bitmap? {
        if (webView.width <= 0 || webView.height <= 0) return null
        return runCatching {
            val scale = targetWidth.toFloat() / webView.width.toFloat()
            val scaledH = (webView.height * scale).toInt().coerceIn(180, targetHeight)
            val bitmap = Bitmap.createBitmap(targetWidth, scaledH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    fun destroy() {
        runCatching { documentStartHandler?.remove() }
        documentStartHandler = null
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        webView.destroy()
    }

    companion object {
        const val DEFAULT_PROFILE_NAME = Profile.DEFAULT_PROFILE_NAME
        const val PRIVATE_PROFILE_NAME = "swiftfox_private_session"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    }
}