package app.overbay.swiftfox.engine

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.features.omnibox.OmniboxEngine

data class ExternalAppPrompt(
    val url: String,
    val appLabel: String,
    val fallbackUrl: String?,
    val launchIntent: Intent
)

data class HttpsUpgradeErrorPrompt(
    val host: String,
    val originalHttpUrl: String,
    val reason: String
)

interface WebNavigationCallbacks {
    fun getHttpsOnlyEnabled(): Boolean
    fun isHttpsBypassedForHost(host: String): Boolean
    fun getExternalAppPolicy(host: String): ExternalAppLinkPolicy
    fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onPageFinished(tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onVisitedHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onExternalAppPrompt(prompt: ExternalAppPrompt)
    fun onLaunchExternalIntent(intent: Intent): Boolean
    fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt)
}

class SwiftfoxWebViewClient(
    private val tabId: String,
    private val callbacks: WebNavigationCallbacks
) : WebViewClient() {

    private val upgradedFromHttp = mutableMapOf<String, String>()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        val url = uri.toString()
        val scheme = uri.scheme?.lowercase().orEmpty()

        if (scheme == "http" || scheme == "https") {
            val host = uri.host?.lowercase().orEmpty()
            if (scheme == "http" &&
                request.isForMainFrame &&
                callbacks.getHttpsOnlyEnabled() &&
                !OmniboxEngine.isLocalHostOrLan(host) &&
                !callbacks.isHttpsBypassedForHost(host)
            ) {
                val upgraded = "https://" + url.removePrefix("http://")
                upgradedFromHttp[upgraded] = url
                view.loadUrl(upgraded)
                return true
            }
            return false
        }

        if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
            return false
        }

        return handleNonHttpUri(view, uri, url)
    }

    private fun handleNonHttpUri(view: WebView, uri: Uri, url: String): Boolean {
        val context = view.context
        val host = OmniboxEngine.extractHost(view.url.orEmpty())
        val policy = callbacks.getExternalAppPolicy(host)

        val parsedIntent = if (uri.scheme.equals("intent", ignoreCase = true)) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
        } else {
            Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        } ?: return true

        val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")

        if (policy == ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX) {
            if (!fallbackUrl.isNullOrBlank()) {
                view.loadUrl(fallbackUrl)
            }
            return true
        }

        val resolved = runCatching {
            context.packageManager.resolveActivity(parsedIntent, 0)
        }.getOrNull()

        if (resolved == null) {
            if (!fallbackUrl.isNullOrBlank()) {
                view.loadUrl(fallbackUrl)
            }
            return true
        }

        val appLabel = runCatching {
            resolved.loadLabel(context.packageManager).toString()
        }.getOrNull().orEmpty().ifBlank { "External app" }

        return when (policy) {
            ExternalAppLinkPolicy.NATIVE_APP -> {
                if (!callbacks.onLaunchExternalIntent(parsedIntent) && !fallbackUrl.isNullOrBlank()) {
                    view.loadUrl(fallbackUrl)
                }
                true
            }
            ExternalAppLinkPolicy.ASK -> {
                callbacks.onExternalAppPrompt(
                    ExternalAppPrompt(
                        url = url,
                        appLabel = appLabel,
                        fallbackUrl = fallbackUrl,
                        launchIntent = parsedIntent
                    )
                )
                true
            }
            ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX -> true
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank()) {
            callbacks.onPageStarted(tabId, url, view.canGoBack(), view.canGoForward())
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        val safeUrl = url.orEmpty()
        upgradedFromHttp.remove(safeUrl)
        injectRuntimeHelpers(view)
        callbacks.onPageFinished(
            tabId = tabId,
            url = safeUrl,
            title = view.title.orEmpty(),
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward()
        )
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank()) {
            callbacks.onVisitedHistoryUpdated(tabId, url, view.canGoBack(), view.canGoForward())
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (!request.isForMainFrame) return
        val failedUrl = request.url?.toString().orEmpty()
        val originalHttp = upgradedFromHttp.remove(failedUrl)
        if (originalHttp != null) {
            val host = request.url?.host?.lowercase().orEmpty()
            callbacks.onHttpsOnlyFailure(
                HttpsUpgradeErrorPrompt(
                    host = host,
                    originalHttpUrl = originalHttp,
                    reason = error.description?.toString().orEmpty().ifBlank { "HTTPS unavailable" }
                )
            )
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        val failedUrl = error.url.orEmpty()
        val originalHttp = upgradedFromHttp.remove(failedUrl)
        if (originalHttp != null) {
            val host = OmniboxEngine.extractHost(failedUrl)
            callbacks.onHttpsOnlyFailure(
                HttpsUpgradeErrorPrompt(
                    host = host,
                    originalHttpUrl = originalHttp,
                    reason = "Certificate validation failed"
                )
            )
        }
    }

    private fun injectRuntimeHelpers(view: WebView) {
        val js = """
            (function() {
              if (!window.__swiftfoxMediaHooked) {
                window.__swiftfoxMediaHooked = true;
                var notify = function() {
                  try {
                    var els = document.querySelectorAll('audio, video');
                    var playing = false;
                    for (var i = 0; i < els.length; i++) {
                      if (!els[i].paused && !els[i].ended && els[i].readyState > 1) {
                        playing = true;
                        break;
                      }
                    }
                    if (window.SwiftfoxMediaBridge) {
                      window.SwiftfoxMediaBridge.onMediaStateChanged(playing, document.title || '');
                    }
                  } catch (e) {}
                };
                document.addEventListener('play', notify, true);
                document.addEventListener('pause', notify, true);
                document.addEventListener('ended', notify, true);
              }
              window.__swiftfoxFetchBlob = function(blobUrl, fileName, mimeType) {
                fetch(blobUrl).then(function(r) { return r.blob(); }).then(function(blob) {
                  var reader = new FileReader();
                  reader.onloadend = function() {
                    if (window.SwiftfoxBlobBridge) {
                      window.SwiftfoxBlobBridge.onBlobData(
                        fileName || ('download_' + Date.now()),
                        blob.type || mimeType || 'application/octet-stream',
                        reader.result || '',
                        blobUrl
                      );
                    }
                  };
                  reader.readAsDataURL(blob);
                }).catch(function() {});
              };
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}
