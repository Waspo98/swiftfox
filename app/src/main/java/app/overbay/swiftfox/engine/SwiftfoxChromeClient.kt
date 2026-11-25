package app.overbay.swiftfox.engine

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import app.overbay.swiftfox.features.omnibox.OmniboxEngine

data class SitePermissionPrompt(
    val tabId: String = "",
    val host: String,
    val resources: List<String>,
    val isGeolocation: Boolean,
    val webRequest: PermissionRequest? = null,
    val geoOrigin: String? = null,
    val geoCallback: GeolocationPermissions.Callback? = null
)

interface WebChromeCallbacks {
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleChanged(tabId: String, title: String)
    fun onFaviconReceived(tabId: String, url: String?, icon: Bitmap) {}
    fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideCustomView()
    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        chooserIntent: Intent
    ): Boolean
    fun onPermissionRequested(tabId: String, prompt: SitePermissionPrompt)
    fun onPermissionRequestCanceled(tabId: String, webRequest: PermissionRequest?, isGeolocation: Boolean)
    fun onOpenNewWindow(parentTabId: String, resultMsg: Message?): Boolean
    fun onCloseWindow(tabId: String)
}

class SwiftfoxChromeClient(
    private val tabId: String,
    private val callbacks: WebChromeCallbacks
) : WebChromeClient() {

    private var lastReportedProgress: Int = -1

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        if (newProgress == lastReportedProgress && newProgress in 1..99) return
        lastReportedProgress = newProgress
        callbacks.onProgressChanged(tabId, newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) {
            callbacks.onTitleChanged(tabId, title)
        }
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        if (icon != null && icon.width > 0 && icon.height > 0) {
            callbacks.onFaviconReceived(tabId, view?.url, icon)
        }
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null && callback != null) {
            callbacks.onShowCustomView(view, callback)
        }
    }

    override fun onHideCustomView() {
        callbacks.onHideCustomView()
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        val intent = runCatching { fileChooserParams.createIntent() }.getOrElse {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        }
        if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return callbacks.onShowFileChooser(filePathCallback, intent)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val host = OmniboxEngine.normalizeHost(request.origin?.host)
        val resources = request.resources?.toList().orEmpty()
        callbacks.onPermissionRequested(
            tabId,
            SitePermissionPrompt(
                tabId = tabId,
                host = host,
                resources = resources,
                isGeolocation = false,
                webRequest = request
            )
        )
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        callbacks.onPermissionRequestCanceled(tabId, request, isGeolocation = false)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (origin == null || callback == null) return
        val host = OmniboxEngine.extractHost(origin).ifBlank { OmniboxEngine.normalizeHost(origin) }
        callbacks.onPermissionRequested(
            tabId,
            SitePermissionPrompt(
                tabId = tabId,
                host = host,
                resources = listOf("LOCATION"),
                isGeolocation = true,
                geoOrigin = origin,
                geoCallback = callback
            )
        )
    }

    override fun onGeolocationPermissionsHidePrompt() {
        callbacks.onPermissionRequestCanceled(tabId, null, isGeolocation = true)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (!isUserGesture && !isDialog) return false
        return callbacks.onOpenNewWindow(tabId, resultMsg)
    }

    override fun onCloseWindow(window: WebView?) {
        callbacks.onCloseWindow(tabId)
    }
}
