package app.overbay.swiftfox.features.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.engine.SwiftfoxWebViewClient
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Wires Android WebView Service Worker fetch interception to `adblock-rust` using lock-free
 * state snapshots so PWA and background Service Worker requests stay ad-blocked without blocking
 * Chromium's SW background threads on Room/DataStore I/O.
 */
class ServiceWorkerAdblockHelper(
    private val adblockEngine: AdblockEngine
) {
    private val globalAdblockEnabled = AtomicBoolean(true)
    private val adblockDisabledHosts = AtomicReference<Set<String>>(emptySet())
    private val installed = AtomicBoolean(false)

    fun updatePolicy(globalEnabled: Boolean, disabledHosts: Set<String>) {
        globalAdblockEnabled.set(globalEnabled)
        adblockDisabledHosts.set(disabledHosts)
    }

    fun shouldBlockServiceWorkerRequest(
        requestUrl: String,
        isForMainFrame: Boolean,
        headers: Map<String, String>
    ): Boolean {
        if (isForMainFrame || !globalAdblockEnabled.get()) return false
        val scheme = requestUrl.substringBefore(':', "").lowercase()
        if (scheme != "https" && scheme != "http") return false
        val refererOrOrigin = headers.entries.firstOrNull {
            it.key.equals("referer", ignoreCase = true) ||
                it.key.equals("origin", ignoreCase = true)
        }?.value.orEmpty()
        val pageUrl = refererOrOrigin.ifBlank { requestUrl }
        val pageHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(pageUrl))
        if (pageHost.isNotEmpty() && pageHost in adblockDisabledHosts.get()) {
            return false
        }
        val resourceType = adblockEngine.inferResourceType(requestUrl, headers)
        return adblockEngine.shouldBlock(
            requestUrl = requestUrl,
            pageUrl = pageUrl,
            resourceType = resourceType,
            globalEnabled = true,
            siteDisabled = false
        )
    }

    fun installIfSupported() {
        if (!installed.compareAndSet(false, true)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) {
            return
        }
        runCatching {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        if (request.isForMainFrame || !globalAdblockEnabled.get()) return null
                        val uri = request.url ?: return null
                        val requestUrl = uri.toString()
                        val headers = request.requestHeaders ?: emptyMap()
                        return if (shouldBlockServiceWorkerRequest(requestUrl, isForMainFrame = false, headers = headers)) {
                            SwiftfoxWebViewClient.buildBlockedSubresourceResponse(requestUrl, headers)
                        } else {
                            null
                        }
                    }
                }
            )
        }
    }
}
