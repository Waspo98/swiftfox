package app.overbay.swiftfox.ui.sync

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator

/**
 * Isolated OAuth WebView flow for Mozilla Accounts sign-in and QR pairing.
 * Uses a dedicated WebView profile so FxA sign-in cookies stay isolated from normal browsing,
 * and intercepts Fenix's redirect URI to deliver code/state back to [MozillaSyncCoordinator].
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FxAAuthWebViewOverlay(
    authUrl: String,
    isRedirectUrl: (String) -> Boolean,
    onRedirectCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var redirectHandled by remember { mutableStateOf(false) }
    val currentIsRedirectUrl by rememberUpdatedState(isRedirectUrl)
    val currentOnRedirectCaptured by rememberUpdatedState(onRedirectCaptured)

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val cookieManager = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching {
                    WebViewCompat.setProfile(this, MozillaSyncCoordinator.FXA_AUTH_PROFILE_NAME)
                    ProfileStore.getInstance()
                        .getOrCreateProfile(MozillaSyncCoordinator.FXA_AUTH_PROFILE_NAME)
                        .cookieManager
                }.getOrElse { CookieManager.getInstance() }
            } else {
                CookieManager.getInstance()
            }
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = false
            settings.allowFileAccess = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    val target = uri.toString()
                    if (request.isForMainFrame && currentIsRedirectUrl(target)) {
                        if (!redirectHandled) {
                            redirectHandled = true
                            currentOnRedirectCaptured(target)
                        }
                        return true
                    }
                    // Block non-HTTPS navigations (http://, file://, content://, javascript:) inside auth WebView
                    return !uri.scheme.equals("https", ignoreCase = true)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val target = url.orEmpty()
                    if (currentIsRedirectUrl(target) && !redirectHandled) {
                        redirectHandled = true
                        view?.stopLoading()
                        currentOnRedirectCaptured(target)
                    }
                }
            }
        }
    }

    LaunchedEffect(authUrl) {
        redirectHandled = false
        webView.loadUrl(authUrl)
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.clearHistory()
                webView.clearCache(true)
                webView.destroy()
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching {
                    val store = ProfileStore.getInstance()
                    val profile = store.getProfile(MozillaSyncCoordinator.FXA_AUTH_PROFILE_NAME)
                    profile?.webStorage?.deleteAllData()
                    profile?.cookieManager?.removeAllCookies {
                        runCatching {
                            profile.cookieManager.flush()
                            store.deleteProfile(MozillaSyncCoordinator.FXA_AUTH_PROFILE_NAME)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mozilla Account Sign-In",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close sign-in")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
