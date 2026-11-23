package app.overbay.swiftfox

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.features.foldable.PostureObserver
import app.overbay.swiftfox.ui.browser.BrowserScreen
import app.overbay.swiftfox.ui.browser.BrowserViewModel
import app.overbay.swiftfox.ui.theme.SwiftfoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingRuntimePermissionCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var fullscreenCustomView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private val configChangeTicks = MutableStateFlow(0)
    private var lastAppliedNightMode: Int? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@registerForActivityResult

        val uris = parseFileChooserResult(result.resultCode, result.data)
        callback.onReceiveValue(uris)
    }

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val cb = pendingRuntimePermissionCallback
        pendingRuntimePermissionCallback = null
        cb?.invoke(grantMap)
    }

    private val defaultBrowserRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    private val userscriptFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readBoundedUriText(uri, MAX_USERSCRIPT_IMPORT_BYTES) }
            }
            val code = result.getOrNull()
            if (code != null) {
                val fallbackName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.removeSuffix(".user.js")
                    ?.removeSuffix(".js")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Imported Userscript"
                viewModel.installUserscriptFromCode(code, fallbackName)
            } else {
                val msg = result.exceptionOrNull()?.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Could not read selected userscript file"
                viewModel.showSnackbar(msg)
            }
        }
    }

    private val importBookmarksLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readBoundedUriText(uri, MAX_BOOKMARKS_IMPORT_BYTES) }
            }
            val html = result.getOrNull()
            if (html != null) {
                viewModel.importBookmarksFromHtml(html)
            } else {
                val msg = result.exceptionOrNull()?.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Could not read selected bookmarks HTML file"
                viewModel.showSnackbar(msg)
            }
        }
    }

    private val exportBookmarksLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val html = withContext(Dispatchers.Default) {
                viewModel.buildBookmarksExportHtml()
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = contentResolver.openOutputStream(uri) ?: return@runCatching false
                    out.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(html)
                    }
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                viewModel.showSnackbar("Exported bookmarks to HTML")
            } else {
                viewModel.showSnackbar("Could not export bookmarks file")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        (application as SwiftfoxApp).tabSessionPool.rebindActivityContext(this)
        viewModel.updateSystemDarkTheme(isNightConfiguration(resources.configuration))

        bindViewModelBridges()
        observeFoldablePosture()
        if (savedInstanceState == null) {
            viewModel.openExternalIncomingIntent(intent)
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.settings.themeMode) {
                applyApplicationNightMode(state.settings.themeMode)
            }

            SwiftfoxTheme(
                themeMode = state.settings.themeMode,
                nightSurfaceStyle = state.settings.nightSurfaceStyle,
                accentPreset = state.settings.accentPreset,
                useDynamicColor = state.settings.useDynamicColor
            ) {
                BrowserScreen(
                    state = state,
                    thumbnailsFlow = viewModel.thumbnails,
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
                        val clip = ClipData.newPlainText("Swiftfox Link", text)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            (state.activeTab?.isPrivate == true || state.isPrivateView)
                        ) {
                            clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        cm.setPrimaryClip(clip)
                    },
                    onPrintPage = { printCurrentPage() },
                    onEnterPip = { enterSwiftfoxPip() },
                    onOpenDefaultBrowserSettings = { requestDefaultBrowserRole() },
                    onExitFullscreenVideo = { exitFullscreenVideo() }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.updateSystemDarkTheme(isNightConfiguration(newConfig))
        configChangeTicks.value = configChangeTicks.value + 1
    }

    private fun applyApplicationNightMode(themeMode: ThemeMode) {
        val targetMode = when (themeMode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.DAY -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.NIGHT -> UiModeManager.MODE_NIGHT_YES
        }
        if (lastAppliedNightMode == targetMode) return
        lastAppliedNightMode = targetMode
        runCatching {
            val uiModeManager = getSystemService(UiModeManager::class.java)
            uiModeManager?.setApplicationNightMode(targetMode)
        }
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        pendingRuntimePermissionCallback?.invoke(emptyMap())
        pendingRuntimePermissionCallback = null
        exitFullscreenVideo()
        clearViewModelBridges()
        (application as SwiftfoxApp).tabSessionPool.unbindHostActivity(this, applicationContext)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.openExternalIncomingIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = viewModel.uiState.value
        val activeVideoPlaying = state.isFullscreenVideo || state.activeTab?.isPlayingVideo == true
        if (activeVideoPlaying && state.settings.backgroundMediaEnabled) {
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

    private fun readBoundedUriText(uri: Uri, maxBytes: Int): String {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open selected file")
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IOException("Selected file exceeds ${maxBytes / (1024 * 1024)} MB limit")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun isNightConfiguration(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val MAX_USERSCRIPT_IMPORT_BYTES = 2 * 1024 * 1024
        const val MAX_BOOKMARKS_IMPORT_BYTES = 10 * 1024 * 1024
    }

    private fun parseFileChooserResult(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            val list = buildList {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { add(it) }
                }
            }
            if (list.isNotEmpty()) return list.toTypedArray()
        }
        return WebChromeClient.FileChooserParams.parseResult(resultCode, data)
    }

    private fun observeFoldablePosture() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PostureObserver.observe(this@MainActivity, configChangeTicks).collect { windowState ->
                    viewModel.onFoldableWindowStateChanged(windowState)
                }
            }
        }
    }

    private fun bindViewModelBridges() {
        viewModel.onSystemDarkThemeProvider = {
            isNightConfiguration(resources.configuration)
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

        viewModel.onRequestAndroidPermissionsBridge = { permissions, onResult ->
            val missing = permissions.filter { perm ->
                ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                onResult(permissions.associateWith { true })
            } else {
                pendingRuntimePermissionCallback?.invoke(emptyMap())
                pendingRuntimePermissionCallback = { granted ->
                    val merged = permissions.associateWith { perm ->
                        granted[perm] == true ||
                            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
                    }
                    onResult(merged)
                }
                runCatching {
                    runtimePermissionLauncher.launch(missing.toTypedArray())
                }.onFailure {
                    val cb = pendingRuntimePermissionCallback
                    pendingRuntimePermissionCallback = null
                    cb?.invoke(emptyMap())
                }
            }
        }

        viewModel.onShowFullscreenViewBridge = { view, callback ->
            // Detach any prior fullscreen view without resetting ViewModel's isFullscreenVideo flag
            exitFullscreenVideo(notifyViewModel = false)
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

        viewModel.onImportUserscriptFileBridge = {
            runCatching {
                userscriptFileLauncher.launch(arrayOf("*/*"))
            }.onFailure {
                viewModel.showSnackbar("Could not open file picker")
            }
        }

        viewModel.onImportBookmarksHtmlBridge = {
            runCatching {
                importBookmarksLauncher.launch(arrayOf("text/html", "text/plain", "*/*"))
            }.onFailure {
                viewModel.showSnackbar("Could not open file picker")
            }
        }

        viewModel.onExportBookmarksHtmlBridge = {
            runCatching {
                exportBookmarksLauncher.launch("swiftfox-bookmarks-${LocalDate.now()}.html")
            }.onFailure {
                viewModel.showSnackbar("Could not open save dialog")
            }
        }
    }

    private fun clearViewModelBridges() {
        viewModel.onSystemDarkThemeProvider = null
        viewModel.onLaunchExternalIntentBridge = null
        viewModel.onShowFileChooserBridge = null
        viewModel.onRequestAndroidPermissionsBridge = null
        viewModel.onShowFullscreenViewBridge = null
        viewModel.onHideFullscreenViewBridge = null
        viewModel.onImportUserscriptFileBridge = null
        viewModel.onImportBookmarksHtmlBridge = null
        viewModel.onExportBookmarksHtmlBridge = null
    }

    private fun exitFullscreenVideo(notifyViewModel: Boolean = true) {
        val view = fullscreenCustomView
        fullscreenCustomView = null
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        // Clear callback reference BEFORE invoking to prevent re-entrant StackOverflowError
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
        if (notifyViewModel) {
            viewModel.onFullscreenVideoExited()
        }
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
        val pm = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (pm == null) {
            viewModel.showSnackbar("Printing is unavailable on this device")
            return
        }
        val jobName = "Swiftfox - ${title.ifBlank { "Page" }}"
        runCatching {
            pm.print(jobName, session.createPrintAdapter(jobName), PrintAttributes.Builder().build())
        }.onFailure {
            viewModel.showSnackbar("Could not open print dialog")
        }
    }

    private fun requestDefaultBrowserRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        ) {
            val launched = runCatching {
                defaultBrowserRoleLauncher.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                )
            }.isSuccess
            if (launched) return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.onFailure {
            viewModel.showSnackbar("Could not open default apps settings")
        }
    }
}
