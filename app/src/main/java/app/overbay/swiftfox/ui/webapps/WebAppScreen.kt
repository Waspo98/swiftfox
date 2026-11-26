package app.overbay.swiftfox.ui.webapps

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import app.overbay.swiftfox.ui.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.engine.SwiftfoxWebView
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.webapps.WebAppDisplayMode
import app.overbay.swiftfox.features.webapps.WebAppManifestParser
import app.overbay.swiftfox.features.webapps.WebAppShortcutItem
import app.overbay.swiftfox.ui.browser.PullToRefreshIndicatorOverlay
import app.overbay.swiftfox.ui.browser.PullToRefreshVisualState
import app.overbay.swiftfox.ui.browser.WebViewContainer
import app.overbay.swiftfox.ui.components.ContextMenuBottomSheet
import app.overbay.swiftfox.ui.components.ExternalAppDialog
import app.overbay.swiftfox.ui.components.HttpsFallbackDialog
import app.overbay.swiftfox.ui.components.SitePermissionDialog
import app.overbay.swiftfox.ui.icons.automirrored.OpenInNew
import app.overbay.swiftfox.ui.icons.rounded.AutoFixHigh
import app.overbay.swiftfox.ui.icons.rounded.BrightnessAuto
import app.overbay.swiftfox.ui.icons.rounded.DarkMode
import app.overbay.swiftfox.ui.icons.rounded.LightMode
import app.overbay.swiftfox.ui.icons.rounded.LockOpen
import app.overbay.swiftfox.ui.icons.rounded.Security
import app.overbay.swiftfox.ui.icons.rounded.Shield
import app.overbay.swiftfox.ui.icons.rounded.Tune
import app.overbay.swiftfox.ui.sheets.SiteRulesSheet
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import app.overbay.swiftfox.ui.zapper.ZapperHudOverlay

@Composable
internal fun WebAppScreen(
    state: WebAppUiState,
    viewModel: WebAppViewModel,
    primaryAccentHex: String,
    onSystemBarStyleChanged: (topBarColorArgb: Int, bottomBarColorArgb: Int, isFullscreen: Boolean) -> Unit,
    onShareUrl: (String, Int) -> Unit,
    onCopyText: (String) -> Unit,
    onEnterPip: () -> Unit,
    onExitFullscreenVideo: () -> Unit,
    onCloseTask: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val isChromeDark = state.settings.themeMode.isDarkActive(isSystemInDarkTheme())
    val snackbarHostState = remember { SnackbarHostState() }

    val darkState = remember(state.settings, state.activeSiteOverride, isChromeDark) {
        DarkReaderConfig.resolve(state.settings, state.activeSiteOverride, isChromeDark)
    }
    val jsEnabled = state.activeSiteOverride?.javascriptEnabled != false
    val isDarkReaderActivelyDarkening = darkState.enabled && !state.darkReaderSkippedNative && jsEnabled

    val targetTopBarColor = resolvePwaSurfaceColor(
        state = state,
        isDarkReaderActive = isDarkReaderActivelyDarkening,
        amoled = state.settings.darkReaderAmoled,
        fallbackColor = MaterialTheme.colorScheme.background
    )
    val targetBottomBarColor = resolvePwaBottomSurfaceColor(
        state = state,
        isDarkReaderActive = isDarkReaderActivelyDarkening,
        amoled = state.settings.darkReaderAmoled,
        fallbackColor = MaterialTheme.colorScheme.background
    )
    val resolvedBarColor by animateColorAsState(
        targetValue = targetTopBarColor,
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "pwaTopBarColor"
    )
    val resolvedBottomBarColor by animateColorAsState(
        targetValue = targetBottomBarColor,
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "pwaBottomBarColor"
    )

    val showSlimHeader = state.displayMode == WebAppDisplayMode.MINIMAL_UI ||
        state.webApp?.showSlimHeader == true
    val isFullscreen = state.displayMode == WebAppDisplayMode.FULLSCREEN &&
        state.webApp?.showSlimHeader != true
    val drawBehindStatusBar = (state.webApp?.drawBehindStatusBar == true || state.viewportFitCover) &&
        !showSlimHeader &&
        !isFullscreen

    LaunchedEffect(targetTopBarColor, targetBottomBarColor, isFullscreen, state.isFullscreenVideo) {
        onSystemBarStyleChanged(
            targetTopBarColor.toArgb(),
            targetBottomBarColor.toArgb(),
            isFullscreen || state.isFullscreenVideo
        )
    }

    LaunchedEffect(state.snackbarId, state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        viewModel.clearSnackbar()
    }

    BackHandler {
        when {
            state.isFullscreenVideo -> onExitFullscreenVideo()
            state.zapper.isActive -> viewModel.stopElementZapper()
            state.isSiteRulesOpen -> viewModel.toggleSiteRulesSheet(false)
            state.isControlsSheetOpen -> viewModel.setControlsSheetOpen(false)
            state.hasPopupSession -> viewModel.closePopupSession()
            viewModel.goBack() -> Unit
            else -> onCloseTask()
        }
    }

    val pullToRefreshVisualState = remember(density) {
        PullToRefreshVisualState(triggerDistancePx = with(density) { 72.dp.toPx() })
    }

    val barContentColor = if (WebAppManifestParser.isLightColor(resolvedBarColor.toArgb())) {
        Color(0xFF1C1B1F)
    } else {
        Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(resolvedBarColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullscreen && !drawBehindStatusBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(resolvedBarColor)
                )
            }

            if (showSlimHeader && !state.isFullscreenVideo) {
                PwaSlimHeaderBar(
                    state = state,
                    containerColor = resolvedBarColor,
                    contentColor = barContentColor,
                    onBack = { if (!viewModel.goBack()) onCloseTask() },
                    onReload = { viewModel.reload() },
                    onOpenControls = { viewModel.setControlsSheetOpen(true) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (!isFullscreen) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.displayCutout
                                    .union(WindowInsets.navigationBars)
                                    .only(WindowInsetsSides.Horizontal)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .imePadding()
            ) {
                if (state.webApp != null) {
                    val session = remember(state.webApp.id, state.sessionRevision) {
                        viewModel.ensureMainSession(context)
                    }
                    if (session != null && !session.isDestroyed) {
                        key(state.webApp.id, state.sessionRevision) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebViewContainer(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { container ->
                                    if (session.isDestroyed) {
                                        container.pullToRefreshEnabled = false
                                        container.clearGestureCallbacks()
                                        container.removeAllViews()
                                        return@AndroidView
                                    }
                                    container.pullToRefreshEnabled =
                                        state.settings.pullToRefreshEnabled && !state.zapper.isActive
                                    container.syncSurfaceBackground(session.surfaceBackgroundColor)
                                    val wv = session.webView
                                    if (wv.parent !== container) {
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        container.removeAllViews()
                                        container.addView(
                                            wv,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        )
                                        session.resumeIfActive()
                                        wv.requestLayout()
                                        wv.invalidate()
                                        if (wv.width > 0 && wv.height > 0) {
                                            viewModel.loadInitialUrlIfNeeded()
                                        } else {
                                            wv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                                                override fun onLayoutChange(
                                                    v: View,
                                                    left: Int,
                                                    top: Int,
                                                    right: Int,
                                                    bottom: Int,
                                                    oldLeft: Int,
                                                    oldTop: Int,
                                                    oldRight: Int,
                                                    oldBottom: Int
                                                ) {
                                                    if (right - left > 0 && bottom - top > 0) {
                                                        v.removeOnLayoutChangeListener(this)
                                                        if (!session.isDestroyed) {
                                                            viewModel.loadInitialUrlIfNeeded()
                                                        }
                                                    }
                                                }
                                            })
                                        }
                                    } else if (wv.width > 0 && wv.height > 0) {
                                        viewModel.loadInitialUrlIfNeeded()
                                    }
                                    container.onPullToRefreshProgress = { pullPx, dragging ->
                                        pullToRefreshVisualState.updatePull(pullPx, dragging)
                                    }
                                    container.onPullToRefreshTrigger = {
                                        pullToRefreshVisualState.triggerRefreshPulse()
                                        viewModel.forceReload()
                                    }
                                    wv.onDomPullToRefreshBlocked = {
                                        container.handleDomPullToRefreshBlocked()
                                    }
                                }
                            )
                        }
                    }
                }

                PullToRefreshIndicatorOverlay(
                    visualState = pullToRefreshVisualState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(if (drawBehindStatusBar) Modifier.statusBarsPadding() else Modifier)
                )

                state.loadError?.let { err ->
                    PwaErrorCard(
                        title = state.webApp?.name ?: err.host,
                        description = err.description,
                        onRetry = {
                            viewModel.dismissLoadError()
                            viewModel.forceReload()
                        },
                        onOpenInSwiftfox = { viewModel.openCurrentInSwiftfox() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                if (state.zapper.isActive) {
                    val dockZapperTop = state.zapper.selectedSelector != null &&
                        state.zapper.selectedViewportCenterY > 0.55f
                    val hudInsetsModifier = when {
                        dockZapperTop && (drawBehindStatusBar || isFullscreen) -> Modifier.statusBarsPadding()
                        !dockZapperTop && (isFullscreen || state.viewportFitCover || state.isFullscreenVideo) ->
                            Modifier.navigationBarsPadding()
                        else -> Modifier
                    }
                    ZapperHudOverlay(
                        state = state.zapper,
                        onAdjustScope = { viewModel.adjustZapperScope(it) },
                        onSetScopeDepth = { viewModel.setZapperScopeDepth(it) },
                        onSetPreview = { viewModel.setZapperPreview(it) },
                        onZapOnce = { viewModel.zapSelectedElementOnce() },
                        onBlockPermanently = { viewModel.blockSelectedElementPermanently() },
                        onUndoTemporaryZap = { viewModel.undoZapperTemporaryZap() },
                        onDone = { viewModel.stopElementZapper() },
                        modifier = Modifier
                            .align(if (dockZapperTop) Alignment.TopCenter else Alignment.BottomCenter)
                            .then(hudInsetsModifier)
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (isFullscreen || state.viewportFitCover || state.isFullscreenVideo) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            }
                        )
                        .padding(16.dp)
                )
            }

            if (!isFullscreen && !state.viewportFitCover && !state.isFullscreenVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                        .background(resolvedBottomBarColor)
                )
            }
        }

        // Cold-launch native PWA splash screen
        AnimatedVisibility(
            visible = state.showSplash && state.webApp != null,
            enter = EnterTransition.None,
            exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(220))
        ) {
            PwaSplashOverlay(
                name = state.webApp?.name.orEmpty(),
                iconBitmap = state.appIconBitmap,
                surfaceColor = resolvedBarColor,
                contentColor = barContentColor
            )
        }

        // In-PWA OAuth / sign-in popup overlay
        if (state.hasPopupSession) {
            val popup = viewModel.getPopupSession()
            if (popup != null) {
                PwaPopupOverlay(
                    title = state.popupTitle.ifBlank { "Sign in" },
                    url = state.popupUrl,
                    popupSession = popup,
                    revision = state.popupRevision,
                    onClose = { viewModel.closePopupSession() }
                )
            }
        }

        // PWA Quick Controls Sheet
        if (state.isControlsSheetOpen && state.webApp != null) {
            PwaControlsSheet(
                state = state,
                isDarkReaderActive = isDarkReaderActivelyDarkening,
                onDismiss = { viewModel.setControlsSheetOpen(false) },
                onBack = {
                    viewModel.setControlsSheetOpen(false)
                    viewModel.goBack()
                },
                onForward = {
                    viewModel.setControlsSheetOpen(false)
                    viewModel.goForward()
                },
                onReload = {
                    viewModel.setControlsSheetOpen(false)
                    viewModel.reload()
                },
                onShare = {
                    viewModel.setControlsSheetOpen(false)
                    viewModel.getCleanSharePayload()?.let { (clean, count) ->
                        onCopyText(clean)
                        onShareUrl(clean, count)
                    }
                },
                onOpenInSwiftfox = { viewModel.openCurrentInSwiftfox() },
                onToggleAdblock = { viewModel.toggleActiveSiteAdblock() },
                onCycleWebDark = { viewModel.cycleWebDarkOverride() },
                onLaunchZapper = { viewModel.startElementZapper(primaryAccentHex) },
                onOpenSiteRules = { viewModel.toggleSiteRulesSheet(true) },
                onToggleSlimHeader = { viewModel.toggleShowSlimHeader() },
                onToggleDrawBehindStatusBar = { viewModel.toggleDrawBehindStatusBar() },
                onEnterPip = {
                    viewModel.setControlsSheetOpen(false)
                    onEnterPip()
                },
                onOpenShortcut = { shortcut -> viewModel.loadShortcutUrl(shortcut.url) }
            )
        }

        // Shared Per-Site Rules Sheet
        if (state.isSiteRulesOpen) {
            val host = state.activeHost
            val rulesForHost = remember(state.zappedRules, host) {
                state.zappedRules.filter { it.host.equals(host, ignoreCase = true) }
            }
            SiteRulesSheet(
                host = host,
                override = state.activeSiteOverride,
                zappedRulesForHost = rulesForHost,
                onDismiss = { viewModel.toggleSiteRulesSheet(false) },
                onSetAdblockDisabled = { viewModel.setSiteAdblockDisabled(host, it) },
                onSetWebDarkOverride = { viewModel.setSiteWebDarkOverride(host, it) },
                onSetJavascriptEnabled = { viewModel.setSiteJavascriptEnabled(host, it) },
                onSetCookiesEnabled = { viewModel.setSiteCookiesEnabled(host, it) },
                onSetHttpsBypass = { viewModel.setSiteHttpsBypass(host, it) },
                onDeleteZappedRule = { viewModel.deleteZappedRule(it) },
                onLaunchZapper = { viewModel.startElementZapper(primaryAccentHex) },
                onResetAll = {
                    viewModel.resetSiteOverride(host)
                    viewModel.toggleSiteRulesSheet(false)
                },
                globalAdblockEnabled = state.settings.adblockEnabled,
                isNativeRust = state.adblockStatus.isNativeRust,
                isPrivate = false,
                darkReaderSkippedNative = state.darkReaderSkippedNative,
                allowLayoutAndUaOverrides = false,
                snackbarMessage = state.snackbarMessage,
                snackbarId = state.snackbarId,
                onClearSnackbar = { viewModel.clearSnackbar() }
            )
        }

        state.contextMenuHit?.let { hit ->
            ContextMenuBottomSheet(
                hit = hit,
                onDismiss = { viewModel.dismissContextMenu() },
                onOpenInNewTab = {
                    viewModel.dismissContextMenu()
                    val intent = android.content.Intent(context, app.overbay.swiftfox.MainActivity::class.java).apply {
                        action = android.content.Intent.ACTION_VIEW
                        data = android.net.Uri.parse(hit.url)
                        `package` = context.packageName
                    }
                    context.startActivity(intent)
                },
                openInNewTabLabel = "Open in Swiftfox tab",
                onOpenInPrivateTab = null,
                onCopyCleanLink = {
                    viewModel.dismissContextMenu()
                    val (clean, stripped) = viewModel.getCleanSharePayload(hit.url) ?: (hit.url to 0)
                    onCopyText(clean)
                    viewModel.showSnackbar(
                        if (stripped > 0) "Copied clean link ($stripped trackers removed)" else "Link copied"
                    )
                },
                onShareCleanLink = {
                    viewModel.dismissContextMenu()
                    val (clean, stripped) = viewModel.getCleanSharePayload(hit.url) ?: (hit.url to 0)
                    onShareUrl(clean, stripped)
                },
                onDownloadImage = hit.imageUrl?.let { img ->
                    {
                        viewModel.dismissContextMenu()
                        viewModel.saveContextMenuImage(img)
                    }
                }
            )
        }

        state.externalAppPrompt?.let { prompt ->
            ExternalAppDialog(
                prompt = prompt,
                onOpenApp = { viewModel.resolveExternalAppPrompt(openNative = true) },
                onStayInSwiftfox = { viewModel.resolveExternalAppPrompt(openNative = false) },
                stayLabel = "Stay here"
            )
        }

        state.httpsErrorPrompt?.let { prompt ->
            HttpsFallbackDialog(
                prompt = prompt,
                onAllowHttp = { viewModel.resolveHttpsErrorPrompt(allowHttpForSite = true) },
                onCancel = { viewModel.resolveHttpsErrorPrompt(allowHttpForSite = false) }
            )
        }

        state.permissionPrompt?.let { prompt ->
            SitePermissionDialog(
                prompt = prompt,
                onAllowOnce = { viewModel.resolvePermissionPrompt(allow = true, rememberForSite = false) },
                onAlwaysAllow = { viewModel.resolvePermissionPrompt(allow = true, rememberForSite = true) },
                onDeny = { viewModel.resolvePermissionPrompt(allow = false, rememberForSite = true) },
                onDismiss = { viewModel.resolvePermissionPrompt(allow = false, rememberForSite = false) }
            )
        }
    }
}

internal fun resolvePwaSurfaceColor(
    state: WebAppUiState,
    isDarkReaderActive: Boolean,
    amoled: Boolean,
    fallbackColor: Color
): Color {
    if (isDarkReaderActive) {
        return if (amoled) Color.Black else Color(0xFF181818)
    }
    val hex = state.liveThemeColorHex
        ?: state.sampledTopColorHex
        ?: state.webApp?.themeColorHex
        ?: state.webApp?.backgroundColorHex
    val argb = WebAppManifestParser.parseColorInt(hex)
    return if (argb != null) Color(argb) else fallbackColor
}

internal fun resolvePwaBottomSurfaceColor(
    state: WebAppUiState,
    isDarkReaderActive: Boolean,
    amoled: Boolean,
    fallbackColor: Color
): Color {
    if (isDarkReaderActive) {
        return if (amoled) Color.Black else Color(0xFF181818)
    }
    val hex = state.sampledBottomColorHex
        ?: state.webApp?.backgroundColorHex
    val argb = WebAppManifestParser.parseColorInt(hex)
    return if (argb != null) Color(argb) else fallbackColor
}

@Composable
private fun PwaSlimHeaderBar(
    state: WebAppUiState,
    containerColor: Color,
    contentColor: Color,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenControls: () -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Horizontal)
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor
                    )
                }
                Icon(
                    imageVector = if (state.currentUrl.startsWith("https://", true)) {
                        Icons.Rounded.Lock
                    } else {
                        Icons.Rounded.LockOpen
                    },
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTitle.ifBlank { state.webApp?.name.orEmpty() },
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.activeHost.isNotBlank()) {
                        Text(
                            text = state.activeHost,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onReload, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (state.isLoading) "Stop" else "Reload",
                        tint = contentColor
                    )
                }
                IconButton(onClick = onOpenControls, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Web app controls",
                        tint = contentColor
                    )
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { (state.progress / 100f).coerceIn(0.08f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = contentColor.copy(alpha = 0.14f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            } else {
                HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
            }
        }
    }
}

@Composable
private fun PwaSplashOverlay(
    name: String,
    iconBitmap: Bitmap?,
    surfaceColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            val img = remember(iconBitmap) { iconBitmap?.asImageBitmap() }
            if (img != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(84.dp)
                ) {
                    Image(
                        bitmap = img,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PwaErrorCard(
    title: String,
    description: String,
    onRetry: () -> Unit,
    onOpenInSwiftfox: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Can't reach $title", style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onOpenInSwiftfox,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Open in Swiftfox")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun PwaPopupOverlay(
    title: String,
    url: String,
    popupSession: app.overbay.swiftfox.engine.BrowserSession,
    revision: Int,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close popup")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (url.isNotBlank()) {
                            Text(
                                text = OmniboxEngine.extractHost(url).ifBlank { url },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            key(revision) {
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    factory = { ctx ->
                        WebViewContainer(ctx).apply {
                            pullToRefreshEnabled = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { container ->
                        val wv = popupSession.webView
                        if (wv.parent !== container) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            container.removeAllViews()
                            container.addView(
                                wv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PwaControlsSheet(
    state: WebAppUiState,
    isDarkReaderActive: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onOpenInSwiftfox: () -> Unit,
    onToggleAdblock: () -> Unit,
    onCycleWebDark: () -> Unit,
    onLaunchZapper: () -> Unit,
    onOpenSiteRules: () -> Unit,
    onToggleSlimHeader: () -> Unit,
    onToggleDrawBehindStatusBar: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenShortcut: (WebAppShortcutItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val webApp = state.webApp ?: return
    val siteOverride = state.activeSiteOverride
    val isShieldActive = state.settings.adblockEnabled && siteOverride?.adblockDisabled != true
    val webDarkOverride = siteOverride?.webDarkModeOverride

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val img = remember(state.appIconBitmap) { state.appIconBitmap?.asImageBitmap() }
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = webApp.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.currentUrl.ifBlank { webApp.startUrl },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Navigation + Share + Open in Swiftfox row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PwaSheetCircleAction("Back", Icons.AutoMirrored.Rounded.ArrowBack, state.canGoBack, onBack, Modifier.weight(1f))
                PwaSheetCircleAction("Forward", Icons.AutoMirrored.Rounded.ArrowForward, state.canGoForward, onForward, Modifier.weight(1f))
                PwaSheetCircleAction(
                    if (state.isLoading) "Stop" else "Reload",
                    if (state.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                    true,
                    onReload,
                    Modifier.weight(1f)
                )
                PwaSheetCircleAction("Share", Icons.Rounded.Share, true, onShare, Modifier.weight(1f))
                PwaSheetCircleAction("Swiftfox", Icons.AutoMirrored.Rounded.OpenInNew, true, onOpenInSwiftfox, Modifier.weight(1f))
            }

            if (state.settings.pictureInPictureEnabled && (state.isPlayingMedia || state.isPlayingVideo)) {
                Surface(
                    onClick = onEnterPip,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Picture-in-Picture", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Power Tools 2x2 Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PwaSheetControlTile(
                    title = "Shield",
                    subtitle = if (isShieldActive) "${state.blockedCount} blocked" else "Paused",
                    icon = if (isShieldActive) Icons.Rounded.Shield else Icons.Rounded.Security,
                    active = isShieldActive,
                    onClick = onToggleAdblock,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                PwaSheetControlTile(
                    title = "Web Dark",
                    subtitle = when (webDarkOverride) {
                        true -> "Force dark"
                        false -> "Force light"
                        null -> if (state.darkReaderSkippedNative) "Auto · Native" else if (isDarkReaderActive) "Auto · Dark" else "Auto · Light"
                    },
                    icon = when (webDarkOverride) {
                        true -> Icons.Rounded.DarkMode
                        false -> Icons.Rounded.LightMode
                        null -> Icons.Rounded.BrightnessAuto
                    },
                    active = webDarkOverride != null,
                    onClick = onCycleWebDark,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PwaSheetControlTile(
                    title = "Zapper",
                    subtitle = "Hide overlays",
                    icon = Icons.Rounded.AutoFixHigh,
                    active = false,
                    onClick = onLaunchZapper,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                PwaSheetControlTile(
                    title = "Site rules",
                    subtitle = "Per-site settings",
                    icon = Icons.Rounded.Tune,
                    active = false,
                    onClick = onOpenSiteRules,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            if (state.displayMode != WebAppDisplayMode.MINIMAL_UI) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleSlimHeader)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show slim header bar", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Always show Back, host & controls menu at top",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = webApp.showSlimHeader,
                            onCheckedChange = { onToggleSlimHeader() }
                        )
                    }
                }

                if (!webApp.showSlimHeader) {
                    val autoCover = state.viewportFitCover && !webApp.drawBehindStatusBar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !autoCover, onClick = onToggleDrawBehindStatusBar)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Draw behind status bar", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (autoCover) {
                                        "Active automatically via site viewport-fit=cover"
                                    } else {
                                        "Extend page content behind transparent status bar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = webApp.drawBehindStatusBar || state.viewportFitCover,
                                enabled = !autoCover,
                                onCheckedChange = { onToggleDrawBehindStatusBar() }
                            )
                        }
                    }
                }
            }

            if (state.manifestShortcuts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Text(
                    text = "App shortcuts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.manifestShortcuts.forEach { item ->
                        Surface(
                            onClick = { onOpenShortcut(item) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (item.description.isNotBlank()) {
                                        Text(
                                            item.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PwaSheetCircleAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PwaSheetControlTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (active) BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
        modifier = modifier.heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
