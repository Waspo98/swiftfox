package app.overbay.swiftfox.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.Locale

data class ExternalAppPrompt(
    val tabId: String = "",
    val url: String,
    val appLabel: String,
    val fallbackUrl: String?,
    val launchIntent: Intent
)

data class HttpsUpgradeErrorPrompt(
    val tabId: String = "",
    val host: String,
    val originalHttpUrl: String,
    val reason: String
)

interface WebNavigationCallbacks {
    fun getHttpsOnlyEnabled(): Boolean
    fun isHttpsBypassedForHost(tabId: String, host: String): Boolean
    fun getExternalAppPolicy(tabId: String, host: String): ExternalAppLinkPolicy
    fun shouldBlockNetworkRequest(
        tabId: String,
        requestUrl: String,
        pageUrl: String,
        headers: Map<String, String>
    ): Boolean
    fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onInjectOnPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onInjectOnPageFinished(tabId: String, url: String)
    fun onVisitedHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onExternalAppPrompt(prompt: ExternalAppPrompt)
    fun onLaunchExternalIntent(intent: Intent): Boolean
    fun onHttpsOnlyFailure(prompt: HttpsUpgradeErrorPrompt)
    fun onMainFrameLoadError(tabId: String, url: String, errorCode: Int, description: String) {}
    fun onRenderProcessGone(tabId: String, didCrash: Boolean)
}

class SwiftfoxWebViewClient(
    private val tabId: String,
    private val callbacks: WebNavigationCallbacks,
    private val mediaTokenProvider: () -> String = { "" }
) : WebViewClient() {

    private val upgradedFromHttpByUrl: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 32
        }
    )
    private val upgradedFromHttpByHost: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 32
        }
    )
    private val oneShotInAppUrls: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 16
        }
    )

    @Volatile
    var committedMainFrameUrl: String = ""
        internal set

    @Volatile
    var lastRequestedMainFrameUrl: String = ""
        internal set

    fun registerProgrammaticHttpsUpgrade(upgradedUrl: String, originalHttpUrl: String) {
        upgradedFromHttpByUrl[upgradedUrl] = originalHttpUrl
        val canonicalHost = OmniboxEngine.extractHost(upgradedUrl)
        if (canonicalHost.isNotBlank()) {
            upgradedFromHttpByHost[canonicalHost] = originalHttpUrl
        }
    }

    fun allowNextLoadInApp(url: String) {
        oneShotInAppUrls[url] = true
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        val url = uri.toString()
        val scheme = uri.scheme?.lowercase().orEmpty()

        if (scheme == "http" || scheme == "https") {
            val rawHost = uri.host?.lowercase().orEmpty()
            val canonicalHost = OmniboxEngine.normalizeHost(rawHost)
            if (scheme == "http" &&
                request.isForMainFrame &&
                callbacks.getHttpsOnlyEnabled() &&
                !OmniboxEngine.isLocalHostOrLan(rawHost) &&
                !callbacks.isHttpsBypassedForHost(tabId, canonicalHost)
            ) {
                val upgraded = uri.buildUpon().scheme("https").build().toString()
                val alreadyUpgraded = upgradedFromHttpByUrl.containsKey(upgraded) ||
                    (canonicalHost.isNotBlank() && upgradedFromHttpByHost.containsKey(canonicalHost))
                if (alreadyUpgraded) {
                    // Server redirected https:// back down to http:// — break infinite loop and prompt user
                    upgradedFromHttpByUrl.remove(upgraded)
                    if (canonicalHost.isNotBlank()) {
                        upgradedFromHttpByHost.remove(canonicalHost)
                    }
                    callbacks.onHttpsOnlyFailure(
                        HttpsUpgradeErrorPrompt(
                            tabId = tabId,
                            host = canonicalHost.ifBlank { rawHost },
                            originalHttpUrl = url,
                            reason = "Site redirected HTTPS back to an unencrypted HTTP address"
                        )
                    )
                    return true
                }
                registerProgrammaticHttpsUpgrade(upgraded, url)
                view.loadUrl(upgraded)
                return true
            }

            if (request.isForMainFrame) {
                if (oneShotInAppUrls.remove(url) != null) {
                    return false
                }
                if (request.hasGesture() && handleHttpExternalAppLink(view, uri, url, canonicalHost)) {
                    return true
                }
            }
            return false
        }

        if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
            return false
        }

        if (!request.isForMainFrame || !request.hasGesture()) {
            return true
        }

        return handleNonHttpUri(view, uri, url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString().orEmpty()
        if (url.isEmpty()) return null

        val scheme = request.url?.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme != "http" && scheme != "https") return null

        if (request.isForMainFrame) {
            lastRequestedMainFrameUrl = url
            return null
        }

        val pageUrl = lastRequestedMainFrameUrl.ifBlank { committedMainFrameUrl }.ifBlank { url }
        val headers = request.requestHeaders ?: emptyMap()
        if (callbacks.shouldBlockNetworkRequest(tabId, url, pageUrl, headers)) {
            return buildBlockedSubresourceResponse(url, headers)
        }

        return null
    }

    private fun buildBlockedSubresourceResponse(
        url: String,
        headers: Map<String, String>
    ): WebResourceResponse {
        val accept = headers.entries.firstOrNull { it.key.equals("accept", ignoreCase = true) }?.value
            ?.lowercase(Locale.ROOT).orEmpty()
        val dest = headers.entries.firstOrNull { it.key.equals("sec-fetch-dest", ignoreCase = true) }?.value
            ?.lowercase(Locale.ROOT).orEmpty()
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        val corsHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-store"
        )

        return when {
            dest == "image" || accept.startsWith("image/") ||
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                path.endsWith(".ico") -> {
                WebResourceResponse(
                    "image/png",
                    null,
                    200,
                    "OK",
                    corsHeaders,
                    ByteArrayInputStream(TRANSPARENT_1X1_PNG)
                )
            }
            dest == "script" || path.endsWith(".js") || path.endsWith(".mjs") -> {
                WebResourceResponse(
                    "application/javascript",
                    "utf-8",
                    200,
                    "OK",
                    corsHeaders,
                    ByteArrayInputStream(EMPTY_JS_BYTES)
                )
            }
            dest == "style" || accept.contains("text/css") || path.endsWith(".css") -> {
                WebResourceResponse(
                    "text/css",
                    "utf-8",
                    200,
                    "OK",
                    corsHeaders,
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            dest == "iframe" || dest == "frame" || dest == "document" || accept.contains("text/html") -> {
                WebResourceResponse(
                    "text/html",
                    "utf-8",
                    200,
                    "OK",
                    corsHeaders,
                    ByteArrayInputStream(TRANSPARENT_EMPTY_HTML_BYTES)
                )
            }
            else -> {
                WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    200,
                    "OK",
                    corsHeaders,
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }
    }

    private fun handleHttpExternalAppLink(
        view: WebView,
        uri: Uri,
        url: String,
        targetHost: String
    ): Boolean {
        val currentHost = OmniboxEngine.extractHost(view.url.orEmpty())
        val policy = callbacks.getExternalAppPolicy(tabId, targetHost.ifBlank { currentHost })
        if (policy == ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX) return false

        // Don't prompt on same-site in-page navigation if the user is already browsing that host
        if (currentHost.isNotBlank() && currentHost == targetHost && policy == ExternalAppLinkPolicy.ASK) {
            return false
        }

        val context = view.context
        val pm = context.packageManager
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val dedicatedApp = findDedicatedNativeApp(context, pm, viewIntent) ?: return false
        val appLabel = runCatching {
            dedicatedApp.loadLabel(pm).toString()
        }.getOrNull().orEmpty().ifBlank { "External app" }

        val nonBrowserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(dedicatedApp.activityInfo.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        }

        return when (policy) {
            ExternalAppLinkPolicy.NATIVE_APP -> {
                callbacks.onLaunchExternalIntent(nonBrowserIntent)
            }
            ExternalAppLinkPolicy.ASK -> {
                callbacks.onExternalAppPrompt(
                    ExternalAppPrompt(
                        tabId = tabId,
                        url = url,
                        appLabel = appLabel,
                        fallbackUrl = url,
                        launchIntent = nonBrowserIntent
                    )
                )
                true
            }
            ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX -> false
        }
    }

    private fun findDedicatedNativeApp(
        context: Context,
        pm: PackageManager,
        intent: Intent
    ): android.content.pm.ResolveInfo? {
        val candidates = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        if (candidates.isEmpty()) return null

        val genericBrowserPackages = cachedGenericBrowserPackages(pm)

        return candidates.firstOrNull { info ->
            val pkg = info.activityInfo?.packageName
            !pkg.isNullOrBlank() &&
                pkg != context.packageName &&
                pkg !in genericBrowserPackages
        }
    }

    private fun sanitizeExternalIntent(context: Context, uri: Uri, url: String): Pair<Intent, String?>? {
        val isIntentScheme = uri.scheme.equals("intent", ignoreCase = true)
        val parsed = if (isIntentScheme) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }

        // Security: restrict to ACTION_VIEW, strip explicit component/selector/extras, and
        // force browsable category so webpages cannot invoke arbitrary Android components or actions.
        if (parsed.action.isNullOrBlank()) {
            parsed.action = Intent.ACTION_VIEW
        } else if (parsed.action != Intent.ACTION_VIEW) {
            return null
        }
        parsed.component = null
        parsed.selector = null
        parsed.addCategory(Intent.CATEGORY_BROWSABLE)
        // Replace (not OR) flags so attacker-controlled intent:// launchFlags cannot survive
        parsed.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER

        // Block targeting Swiftfox itself or local file/content/packageless http(s) URIs
        if (parsed.`package` == context.packageName) return null
        val dataScheme = parsed.data?.scheme?.lowercase(Locale.ROOT)
        if (dataScheme == "file" || dataScheme == "content" || dataScheme == "javascript") {
            return null
        }
        if ((dataScheme == "http" || dataScheme == "https") && parsed.`package`.isNullOrBlank()) {
            return null
        }

        val rawFallback = parsed.getStringExtra("browser_fallback_url")
        val safeFallback = rawFallback?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
        if (isIntentScheme) {
            parsed.replaceExtras(null as Bundle?)
        }
        return parsed to safeFallback
    }

    private fun loadFallbackUrlRespectingHttpsOnly(view: WebView, fallbackUrl: String) {
        val parsedUri = runCatching { Uri.parse(fallbackUrl) }.getOrNull()
        val scheme = parsedUri?.scheme?.lowercase(Locale.ROOT)
        val rawHost = parsedUri?.host?.lowercase(Locale.ROOT).orEmpty()
        val canonicalHost = OmniboxEngine.normalizeHost(rawHost)
        val targetUrl = if (
            scheme == "http" &&
            callbacks.getHttpsOnlyEnabled() &&
            !OmniboxEngine.isLocalHostOrLan(rawHost) &&
            !callbacks.isHttpsBypassedForHost(tabId, canonicalHost)
        ) {
            val upgraded = parsedUri.buildUpon().scheme("https").build().toString()
            registerProgrammaticHttpsUpgrade(upgraded, fallbackUrl)
            upgraded
        } else {
            fallbackUrl
        }
        allowNextLoadInApp(targetUrl)
        view.loadUrl(targetUrl)
    }

    private fun handleNonHttpUri(view: WebView, uri: Uri, url: String): Boolean {
        val context = view.context
        val host = OmniboxEngine.extractHost(view.url.orEmpty())
        val policy = callbacks.getExternalAppPolicy(tabId, host)

        val (parsedIntent, fallbackUrl) = sanitizeExternalIntent(context, uri, url) ?: return true

        if (policy == ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX) {
            if (!fallbackUrl.isNullOrBlank()) {
                loadFallbackUrlRespectingHttpsOnly(view, fallbackUrl)
            }
            return true
        }

        val resolved = runCatching {
            context.packageManager.resolveActivity(parsedIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()

        val appLabel = runCatching {
            resolved?.loadLabel(context.packageManager)?.toString()
        }.getOrNull().orEmpty().ifBlank {
            parsedIntent.`package`?.substringAfterLast('.')
                ?.replaceFirstChar { it.uppercase(Locale.ROOT) }
                ?: uri.scheme?.uppercase(Locale.ROOT)?.takeIf { it != "INTENT" }
                ?: "External app"
        }

        return when (policy) {
            ExternalAppLinkPolicy.NATIVE_APP -> {
                if (!callbacks.onLaunchExternalIntent(parsedIntent) && !fallbackUrl.isNullOrBlank()) {
                    loadFallbackUrlRespectingHttpsOnly(view, fallbackUrl)
                }
                true
            }
            ExternalAppLinkPolicy.ASK -> {
                // Even if package visibility hides resolveActivity on custom schemes,
                // prompt when resolved OR when intent specifies a package/standard custom scheme.
                if (resolved == null && parsedIntent.`package`.isNullOrBlank() && uri.scheme.equals("intent", true)) {
                    if (!fallbackUrl.isNullOrBlank()) {
                        loadFallbackUrlRespectingHttpsOnly(view, fallbackUrl)
                    }
                    return true
                }
                callbacks.onExternalAppPrompt(
                    ExternalAppPrompt(
                        tabId = tabId,
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
        (view as? SwiftfoxWebView)?.isMediaCurrentlyPlaying = false
        if (!url.isNullOrBlank()) {
            oneShotInAppUrls.remove(url)
            committedMainFrameUrl = url
            lastRequestedMainFrameUrl = url
            callbacks.onPageStarted(tabId, url, view.canGoBack(), view.canGoForward())
            callbacks.onInjectOnPageStarted(tabId, url)
        }
    }

    fun resetPendingMainFrameUrl() {
        lastRequestedMainFrameUrl = committedMainFrameUrl
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        val safeUrl = url.orEmpty()
        if (safeUrl.isNotBlank()) {
            committedMainFrameUrl = safeUrl
            lastRequestedMainFrameUrl = safeUrl
        }
        upgradedFromHttpByUrl.remove(safeUrl)
        val finishedHost = OmniboxEngine.extractHost(safeUrl)
        if (finishedHost.isNotBlank()) {
            upgradedFromHttpByHost.remove(finishedHost)
        }
        // Rotate origin tokens inside onInjectOnPageFinished BEFORE capturing mediaTokenProvider()
        // so cross-origin redirects and restored loads embed the active post-rotation media token.
        callbacks.onInjectOnPageFinished(tabId, safeUrl)
        injectRuntimeHelpers(view, mediaTokenProvider())
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
            committedMainFrameUrl = url
            lastRequestedMainFrameUrl = url
            callbacks.onVisitedHistoryUpdated(tabId, url, view.canGoBack(), view.canGoForward())
        }
    }

    private fun consumeUpgradedOriginal(failedUrl: String): Pair<String, String>? {
        val canonicalHost = OmniboxEngine.extractHost(failedUrl)
        val original = upgradedFromHttpByUrl.remove(failedUrl)
            ?: if (canonicalHost.isNotBlank()) upgradedFromHttpByHost.remove(canonicalHost) else null
        if (original == null) return null
        if (canonicalHost.isNotBlank()) {
            upgradedFromHttpByHost.remove(canonicalHost)
        }
        return canonicalHost to original
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (!request.isForMainFrame) return
        lastRequestedMainFrameUrl = committedMainFrameUrl
        val failedUrl = request.url?.toString().orEmpty()
        val description = error.description?.toString().orEmpty()
        val match = consumeUpgradedOriginal(failedUrl)
        if (match != null) {
            val (host, originalHttp) = match
            callbacks.onHttpsOnlyFailure(
                HttpsUpgradeErrorPrompt(
                    tabId = tabId,
                    host = host,
                    originalHttpUrl = originalHttp,
                    reason = description.ifBlank { "HTTPS unavailable" }
                )
            )
            return
        }
        if (description.contains("ERR_ABORTED", ignoreCase = true)) {
            return
        }
        if (failedUrl.startsWith("http://", ignoreCase = true) ||
            failedUrl.startsWith("https://", ignoreCase = true)
        ) {
            callbacks.onMainFrameLoadError(
                tabId = tabId,
                url = failedUrl,
                errorCode = error.errorCode,
                description = description
            )
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        callbacks.onRenderProcessGone(tabId, detail.didCrash())
        return true
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        val failedUrl = error.url.orEmpty()
        if (failedUrl.isNotBlank() && failedUrl == lastRequestedMainFrameUrl) {
            lastRequestedMainFrameUrl = committedMainFrameUrl
        }
        val match = consumeUpgradedOriginal(failedUrl) ?: return
        val (host, originalHttp) = match
        callbacks.onHttpsOnlyFailure(
            HttpsUpgradeErrorPrompt(
                tabId = tabId,
                host = host,
                originalHttpUrl = originalHttp,
                reason = "Certificate validation failed"
            )
        )
    }

    private fun injectRuntimeHelpers(view: WebView, mediaToken: String) {
        val tokenLit = JSONObject.quote(mediaToken)
        val js = """
            (function() {
              var mediaToken = $tokenLit;
              if (!window.__swiftfoxMediaHooked) {
                window.__swiftfoxMediaHooked = true;
                var notify = function(evt) {
                  try {
                    if (evt && evt.target && (evt.target.tagName === 'VIDEO' || evt.target.tagName === 'AUDIO')) {
                      if (evt.type === 'play' || evt.type === 'playing') {
                        window.__swiftfoxLastMedia = evt.target;
                      }
                    }
                    var els = document.querySelectorAll('audio, video');
                    var playing = false;
                    var hasVideo = false;
                    for (var i = 0; i < els.length; i++) {
                      var el = els[i];
                      var isActive = !el.paused && !el.ended && (el.readyState >= 1 || (evt && evt.type === 'playing' && evt.target === el));
                      if (isActive) {
                        playing = true;
                        window.__swiftfoxLastMedia = el;
                        if (el.tagName === 'VIDEO' && (el.videoWidth > 0 || el.clientHeight > 60)) {
                          hasVideo = true;
                        }
                      }
                    }
                    var ch = window.SwiftfoxNativeChannel;
                    if (mediaToken && ch && typeof ch.postMessage === 'function') {
                      ch.postMessage(JSON.stringify({
                        channel: 'media',
                        token: mediaToken,
                        isPlaying: playing,
                        hasVideo: hasVideo,
                        title: document.title || ''
                      }));
                    }
                  } catch (e) {}
                };
                document.addEventListener('play', notify, true);
                document.addEventListener('playing', notify, true);
                document.addEventListener('pause', notify, true);
                document.addEventListener('ended', notify, true);
                document.addEventListener('emptied', notify, true);
                window.__swiftfoxNotifyMedia = notify;
              }
              if (window.__swiftfoxNotifyMedia) {
                window.__swiftfoxNotifyMedia(null);
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    companion object {
        private const val BROWSER_CACHE_TTL_MS = 60_000L

        @Volatile
        private var browserPackagesCache: Set<String>? = null

        @Volatile
        private var browserPackagesCachedAtMs: Long = 0L

        private fun cachedGenericBrowserPackages(pm: PackageManager): Set<String> {
            val now = System.currentTimeMillis()
            val cached = browserPackagesCache
            if (cached != null && (now - browserPackagesCachedAtMs) < BROWSER_CACHE_TTL_MS) {
                return cached
            }
            val fresh = runCatching {
                val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
            browserPackagesCache = fresh
            browserPackagesCachedAtMs = now
            return fresh
        }

        // Minimal 1x1 transparent PNG so blocked ad/tracker images never render broken-image boxes
        private val TRANSPARENT_1X1_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte()
        )
        private val EMPTY_JS_BYTES = "/* blocked */".toByteArray(Charsets.UTF_8)
        private val TRANSPARENT_EMPTY_HTML_BYTES =
            "<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent!important;}</style></head><body></body></html>"
                .toByteArray(Charsets.UTF_8)
    }
}
