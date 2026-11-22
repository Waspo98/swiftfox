package app.overbay.swiftfox

import android.app.PictureInPictureParams
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.overbay.swiftfox.features.foldable.PostureObserver
import app.overbay.swiftfox.ui.browser.BrowserScreen
import app.overbay.swiftfox.ui.browser.BrowserViewModel
import app.overbay.swiftfox.ui.theme.SwiftfoxTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var fullscreenCustomView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@registerForActivityResult

        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback.onReceiveValue(uris)
    }

    private val defaultBrowserRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        bindViewModelBridges()
        observeFoldablePosture()
        viewModel.openExternalIncomingIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

            SwiftfoxTheme(
                themeMode = state.settings.themeMode,
                nightSurfaceStyle = state.settings.nightSurfaceStyle,
                accentPreset = state.settings.accentPreset,
                useDynamicColor = state.settings.useDynamicColor
            ) {
                BrowserScreen(
                    state = state,
                    thumbnails = thumbnails,
                    viewModel = viewModel,
                    onShareUrl = { cleanUrl, strippedCount ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, cleanUrl)
                            state.activeTab?.title?.takeIf { it.isNotBlank() }?.let {
                                putExtra(Intent.EXTRA_SUBJECT, it)
                            }
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share clean link"))
                        if (strippedCount > 0) {
                            viewModel.showSnackbar("Stripped $strippedCount tracking params before sharing")
                        }
                    },
                    onCopyText = { text ->
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Swiftfox Link", text))
                    },
                    onPrintPage = { printCurrentPage() },
                    onEnterPip = { enterSwiftfoxPip() },
                    onOpenDefaultBrowserSettings = { requestDefaultBrowserRole() },
                    onExitFullscreenVideo = { exitFullscreenVideo() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.openExternalIncomingIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = viewModel.uiState.value
        val activePlaying = state.activeTab?.isPlayingMedia == true || state.isFullscreenVideo
        if (activePlaying && state.settings.backgroundMediaEnabled) {
            enterSwiftfoxPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.onPictureInPictureChanged(isInPictureInPictureMode)
    }

    private fun observeFoldablePosture() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PostureObserver.observe(this@MainActivity).collect { windowState ->
                    viewModel.onFoldableWindowStateChanged(windowState)
                }
            }
        }
    }

    private fun bindViewModelBridges() {
        viewModel.onSystemDarkThemeProvider = {
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

        viewModel.onLaunchExternalIntentBridge = { intent ->
            runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
        }

        viewModel.onShowFileChooserBridge = { callback, chooserIntent ->
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = callback
            runCatching {
                fileChooserLauncher.launch(chooserIntent)
                true
            }.getOrElse {
                pendingFileCallback = null
                false
            }
        }

        viewModel.onShowFullscreenViewBridge = { view, callback ->
            exitFullscreenVideo()
            fullscreenCustomView = view
            fullscreenCallback = callback
            val decor = window.decorView as ViewGroup
            decor.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        viewModel.onHideFullscreenViewBridge = {
            exitFullscreenVideo()
        }
    }

    private fun exitFullscreenVideo() {
        val view = fullscreenCustomView
        fullscreenCustomView = null
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun enterSwiftfoxPip() {
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }.onFailure {
            viewModel.showSnackbar("Picture-in-Picture unavailable on this screen")
        }
    }

    private fun printCurrentPage() {
        val pair = viewModel.getActivePrintSession()
        if (pair == null) {
            viewModel.showSnackbar("Open a webpage to print or save as PDF")
            return
        }
        val (session, title) = pair
        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Swiftfox - ${title.ifBlank { "Page" }}"
        pm.print(jobName, session.createPrintAdapter(jobName), PrintAttributes.Builder().build())
    }

    private fun requestDefaultBrowserRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        ) {
            defaultBrowserRoleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            )
        } else {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
    }
}
