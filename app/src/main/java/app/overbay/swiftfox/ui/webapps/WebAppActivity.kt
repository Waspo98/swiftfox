package app.overbay.swiftfox.ui.webapps

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.swiftfox.R
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.features.webapps.WebAppManifestParser
import app.overbay.swiftfox.features.webapps.WebAppShortcutManager
import app.overbay.swiftfox.ui.components.LocalFaviconRepository
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.SwiftfoxBrandAppearance
import app.overbay.swiftfox.ui.theme.SwiftfoxTheme

/**
 * Standalone edge-to-edge host Activity for installed Swiftfox PWAs (`documentLaunchMode="intoExisting"`).
 * Shares Swiftfox's default WebView profile and full engine pipeline (adblock, Dark Reader,
 * Zapper, Userscripts, background media/PiP, per-site rules) while preserving independent
 * Android Recents multitasking and config-change survival across foldable postures.
 */
class WebAppActivity : ComponentActivity() {

    private val viewModel: WebAppViewModel by viewModels()

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingRuntimePermissionCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var fullscreenCustomView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastAppliedNightMode: Int? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@registerForActivityResult
        callback.onReceiveValue(parseFileChooserResult(result.resultCode, result.data))
    }

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cb = pendingRuntimePermissionCallback
        pendingRuntimePermissionCallback = null
        cb?.invoke(grants)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false

        viewModel.rebindHostContext(this)
        viewModel.updateSystemDarkTheme(isNightConfiguration(resources.configuration))
        bindBridges()

        val webAppId = extractWebAppId(intent)
        val openControls = intent?.getBooleanExtra(WebAppShortcutManager.EXTRA_OPEN_CONTROLS, false) == true
        viewModel.initializeForWebApp(webAppId, openControlsOnStart = openControls)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.isMissingApp) {
                if (state.isMissingApp) {
                    finishAndRemoveTask()
                }
            }

            LaunchedEffect(state.hasLoadedSettings, state.settings.themeMode) {
                if (state.hasLoadedSettings) {
                    applyApplicationNightMode(state.settings.themeMode)
                }
            }

            val isChromeDark = state.settings.themeMode.isDarkActive(isSystemInDarkTheme())
            val brandAppearance = SwiftfoxBrandAppearance(
                syncInApp = state.settings.syncAppIconInApp,
                isLightChrome = !isChromeDark,
                resolvedIcon = state.settings.resolveAppIcon(
                    isChromeDark = isChromeDark,
                    isBetaBuild = SwiftfoxBrand.isBetaBuild
                )
            )

            SwiftfoxTheme(
                themeMode = state.settings.themeMode,
                nightSurfaceStyle = state.settings.nightSurfaceStyle,
                accentPreset = state.settings.accentPreset,
                useDynamicColor = state.settings.useDynamicColor,
                brandAppearance = brandAppearance
            ) {
                val primaryHex = String.format("#%06X", 0xFFFFFF and androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb())
                val app = state.webApp
                LaunchedEffect(app?.id, app?.name, state.appIconBitmap, state.liveThemeColorHex) {
                    if (app != null) {
                        updateTaskDescription(
                            label = app.name,
                            icon = state.appIconBitmap,
                            primaryColor = WebAppManifestParser.parseColorInt(
                                state.liveThemeColorHex
                                    ?: app.themeColorHex
                                    ?: app.backgroundColorHex
                            )
                        )
                    }
                }
                LaunchedEffect(app?.id, app?.name) {
                    if (app != null) {
                        updateControlsNotificationIfActive()
                    }
                }

                CompositionLocalProvider(
                    LocalFaviconRepository provides (application as SwiftfoxApp).faviconRepository
                ) {
                    WebAppScreen(
                        state = state,
                        viewModel = viewModel,
                        primaryAccentHex = primaryHex,
                        onSystemBarStyleChanged = { topBarColorArgb, bottomBarColorArgb, isFullscreen ->
                            applySystemBarAppearance(topBarColorArgb, bottomBarColorArgb, isFullscreen)
                        },
                        onShareUrl = { cleanUrl, strippedCount ->
                            val latest = viewModel.uiState.value
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, cleanUrl)
                                latest.currentTitle.takeIf { it.isNotBlank() }?.let {
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
                            cm.setPrimaryClip(ClipData.newPlainText("Web App Link", text))
                        },
                        onEnterPip = { enterPwaPip() },
                        onExitFullscreenVideo = { exitFullscreenVideo() },
                        onCloseTask = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateControlsNotificationIfActive()
    }

    override fun onStop() {
        super.onStop()
        cancelControlsNotification()
        runCatching { android.webkit.CookieManager.getInstance().flush() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val webAppId = extractWebAppId(intent)
        val openControls = intent.getBooleanExtra(WebAppShortcutManager.EXTRA_OPEN_CONTROLS, false)
        viewModel.initializeForWebApp(webAppId, openControlsOnStart = openControls)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        runCatching { theme.rebase() }
        super.onConfigurationChanged(newConfig)
        viewModel.updateSystemDarkTheme(isNightConfiguration(newConfig))
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = viewModel.uiState.value
        val activeVideoPlaying = state.isFullscreenVideo || state.isPlayingVideo
        if (activeVideoPlaying && state.settings.backgroundMediaEnabled) {
            enterPwaPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.onPictureInPictureChanged(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        cancelControlsNotification()
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        pendingRuntimePermissionCallback?.invoke(emptyMap())
        pendingRuntimePermissionCallback = null
        exitFullscreenVideo()
        clearBridges()
        viewModel.unbindHostContext(this)
        super.onDestroy()
    }

    private fun extractWebAppId(intent: Intent?): String? {
        val extra = intent?.getStringExtra(WebAppShortcutManager.EXTRA_WEB_APP_ID)?.takeIf { it.isNotBlank() }
        if (extra != null) return extra
        val data = intent?.data ?: return null
        if (data.scheme == "swiftfox-pwa" && data.host == "app") {
            return data.lastPathSegment?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun applySystemBarAppearance(
        topBarColorArgb: Int,
        bottomBarColorArgb: Int,
        isFullscreen: Boolean
    ) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = WebAppManifestParser.isLightColor(topBarColorArgb)
            controller.isAppearanceLightNavigationBars = WebAppManifestParser.isLightColor(bottomBarColorArgb)
        }
    }

    @Suppress("DEPRECATION")
    private fun updateTaskDescription(label: String, icon: Bitmap?, primaryColor: Int?) {
        runCatching {
            val validIcon = icon?.takeUnless { it.isRecycled }
            val taskDesc = if (primaryColor != null) {
                ActivityManager.TaskDescription(label, validIcon, primaryColor)
            } else {
                ActivityManager.TaskDescription(label, validIcon)
            }
            setTaskDescription(taskDesc)
        }
    }

    private fun updateControlsNotificationIfActive() {
        val webApp = viewModel.uiState.value.webApp ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureControlsChannel(nm)
        val openControlsIntent = Intent(this, WebAppActivity::class.java).apply {
            action = WebAppShortcutManager.ACTION_LAUNCH_WEB_APP
            data = Uri.parse("swiftfox-pwa://app/${webApp.id}")
            putExtra(WebAppShortcutManager.EXTRA_WEB_APP_ID, webApp.id)
            putExtra(WebAppShortcutManager.EXTRA_OPEN_CONTROLS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            webApp.id.hashCode(),
            openControlsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CONTROLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_swiftfox)
            .setContentTitle("${webApp.name} · Swiftfox Web App")
            .setContentText("Tap for Site Rules, Dark Reader, Zapper, or Open in Swiftfox")
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        runCatching {
            nm.notify(controlsNotificationId(webApp.id), notification)
        }
    }

    private fun cancelControlsNotification() {
        val webAppId = viewModel.uiState.value.webApp?.id ?: extractWebAppId(intent) ?: return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(controlsNotificationId(webAppId))
    }

    private fun ensureControlsChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CONTROLS_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CONTROLS_CHANNEL_ID,
            getString(R.string.pwa_controls_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.pwa_controls_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun controlsNotificationId(appId: String): Int =
        CONTROLS_NOTIFICATION_BASE_ID + (appId.hashCode() and 0x0FFF)

    private fun bindBridges() {
        viewModel.onSystemDarkThemeProvider = { isNightConfiguration(resources.configuration) }
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
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                onResult(permissions.associateWith { true })
            } else {
                pendingRuntimePermissionCallback?.invoke(emptyMap())
                pendingRuntimePermissionCallback = { grants ->
                    val merged = permissions.associateWith { p ->
                        grants[p] == true ||
                            ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
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
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        viewModel.onHideFullscreenViewBridge = { exitFullscreenVideo() }
    }

    private fun clearBridges() {
        viewModel.onSystemDarkThemeProvider = null
        viewModel.onLaunchExternalIntentBridge = null
        viewModel.onShowFileChooserBridge = null
        viewModel.onRequestAndroidPermissionsBridge = null
        viewModel.onShowFullscreenViewBridge = null
        viewModel.onHideFullscreenViewBridge = null
    }

    private fun exitFullscreenVideo(notifyViewModel: Boolean = true) {
        val view = fullscreenCustomView
        fullscreenCustomView = null
        if (view != null) (view.parent as? ViewGroup)?.removeView(view)
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
        if (notifyViewModel) viewModel.onFullscreenVideoExited()
        val state = viewModel.uiState.value
        val isFullscreen = state.displayMode == app.overbay.swiftfox.features.webapps.WebAppDisplayMode.FULLSCREEN &&
            state.webApp?.showSlimHeader != true
        if (!isFullscreen) {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun enterPwaPip() {
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }.onFailure {
            viewModel.showSnackbar("Picture-in-Picture unavailable")
        }
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

    private fun applyApplicationNightMode(themeMode: ThemeMode) {
        val targetMode = when (themeMode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.DAY -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.NIGHT -> UiModeManager.MODE_NIGHT_YES
        }
        if (lastAppliedNightMode == targetMode) return
        lastAppliedNightMode = targetMode
        runCatching {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(targetMode)
        }
    }

    private fun isNightConfiguration(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        private const val CONTROLS_CHANNEL_ID = "swiftfox_pwa_controls"
        private const val CONTROLS_NOTIFICATION_BASE_ID = 5200
    }
}
