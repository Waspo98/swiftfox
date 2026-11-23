package app.overbay.swiftfox.ui.browser

import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.ui.reader.ReaderModeOverlay
import app.overbay.swiftfox.ui.settings.BookmarksOverlay
import app.overbay.swiftfox.ui.settings.DownloadsOverlay
import app.overbay.swiftfox.ui.settings.FilterListsOverlay
import app.overbay.swiftfox.ui.settings.HistoryOverlay
import app.overbay.swiftfox.ui.settings.SettingsScreen
import app.overbay.swiftfox.ui.settings.UserscriptsOverlay
import app.overbay.swiftfox.ui.sheets.QuickControlsSheet
import app.overbay.swiftfox.ui.sheets.SiteRulesSheet
import app.overbay.swiftfox.ui.tabs.SpacesTabSwitcher
import app.overbay.swiftfox.ui.tabs.UnfoldedSpacesSidebar
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.zapper.ZappedRulesOverlay
import app.overbay.swiftfox.ui.zapper.ZapperHudOverlay

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    thumbnails: TabThumbnailsSnapshot,
    viewModel: BrowserViewModel,
    onShareUrl: (String, Int) -> Unit,
    onCopyText: (String) -> Unit,
    onPrintPage: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onExitFullscreenVideo: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        val canUndo = state.recentlyClosedTab != null || state.snackbarUndoZapper
        val actionLabel = if (canUndo) "Undo" else null
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            when {
                state.snackbarUndoZapper -> viewModel.undoZapperTemporaryZap()
                state.recentlyClosedTab != null -> viewModel.undoCloseTab()
                else -> viewModel.clearSnackbar()
            }
        } else {
            viewModel.clearSnackbar()
        }
    }

    // Predictive & System Back handling
    val shouldHandleBack = state.isFullscreenVideo ||
        state.zapper.isActive ||
        state.readerArticle != null ||
        state.isSiteRulesOpen ||
        state.isOmniboxFocused ||
        state.isQuickControlsOpen ||
        state.activeOverlay != BrowserOverlay.NONE ||
        state.findInPage.visible ||
        (state.activeTab?.canGoBack == true)

    BackHandler(enabled = shouldHandleBack) {
        when {
            state.isFullscreenVideo -> onExitFullscreenVideo()
            state.zapper.isActive -> viewModel.stopElementZapper()
            state.readerArticle != null -> viewModel.closeReaderMode()
            state.isSiteRulesOpen -> viewModel.toggleSiteRulesSheet(false)
            state.isOmniboxFocused -> viewModel.dismissOmnibox()
            state.isQuickControlsOpen -> viewModel.toggleQuickControls(false)
            state.activeOverlay != BrowserOverlay.NONE -> viewModel.setOverlay(BrowserOverlay.NONE)
            state.findInPage.visible -> viewModel.closeFindInPage()
            else -> viewModel.goBack()
        }
    }

    val activeTab = state.activeTab
    val activeSession: BrowserSession? = if (activeTab != null && !activeTab.isStartPage) {
        remember(activeTab.id) { viewModel.ensureSession(activeTab, context) }
    } else {
        null
    }

    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val primaryAccentHex = remember(primaryArgb) {
        String.format("#%06X", 0xFFFFFF and primaryArgb)
    }
    val surfaceArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BrowserShellLayout(
            state = state,
            snackbarHostState = snackbarHostState,
            onFocusOmnibox = { viewModel.focusOmnibox(prefillCurrentUrl = true) },
            onFocusSearchFresh = { viewModel.focusOmnibox(prefillCurrentUrl = false) },
            onGoBack = { viewModel.goBack() },
            onGoForward = { viewModel.goForward() },
            onReloadOrStop = { viewModel.reloadOrStop() },
            onOpenReaderMode = { viewModel.openReaderMode() },
            onOpenQuickControls = { viewModel.toggleQuickControls(true) },
            onOpenTabSwitcher = { viewModel.setOverlay(BrowserOverlay.TAB_SWITCHER) },
            onOpenSettings = { viewModel.setOverlay(BrowserOverlay.SETTINGS) },
            onSwitchAdjacentTab = { dir -> viewModel.switchAdjacentTab(dir) },
            onToggleUnfoldedRail = { viewModel.toggleUnfoldedRailExpanded() },
            onSelectSpace = { viewModel.selectSpace(it) },
            onTogglePrivate = { viewModel.togglePrivateView(it) },
            onSelectTab = { viewModel.selectTab(it) },
            onCloseTab = { viewModel.closeTab(it) },
            onCreateTab = { viewModel.createTab() },
            onOpenUrl = { viewModel.navigateActiveTab(it) },
            onAddEssential = { title, url -> viewModel.addEssential(title, url) },
            onRemoveEssential = { viewModel.removeEssential(it) },
            onFindQueryChange = { viewModel.updateFindInPageQuery(it) },
            onFindNext = { viewModel.findNextMatch(it) },
            onCloseFind = { viewModel.closeFindInPage() },
            onAdjustZapperScope = { viewModel.adjustZapperScope(it) },
            onSetZapperScopeDepth = { viewModel.setZapperScopeDepth(it) },
            onZapOnce = { viewModel.zapSelectedElementOnce() },
            onBlockPermanently = { viewModel.blockSelectedElementPermanently() },
            onUndoTemporaryZap = { viewModel.undoZapperTemporaryZap() },
            onStopZapper = { viewModel.stopElementZapper() },
            webContent = {
                if (activeSession != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SwipeRefreshLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setColorSchemeColors(primaryArgb)
                                setProgressBackgroundColorSchemeColor(surfaceArgb)
                                setOnRefreshListener {
                                    viewModel.reloadOrStop()
                                    isRefreshing = false
                                }
                            }
                        },
                        update = { swipeLayout ->
                            swipeLayout.isEnabled = state.settings.pullToRefreshEnabled &&
                                !state.zapper.isActive &&
                                state.readerArticle == null
                            swipeLayout.setColorSchemeColors(primaryArgb)
                            swipeLayout.setProgressBackgroundColorSchemeColor(surfaceArgb)

                            val targetWebView = activeSession.webView
                            swipeLayout.setOnChildScrollUpCallback { _, _ ->
                                targetWebView.scrollY > 0
                            }
                            val currentChild = if (swipeLayout.childCount > 0) {
                                swipeLayout.getChildAt(swipeLayout.childCount - 1)
                            } else {
                                null
                            }
                            if (currentChild !== targetWebView) {
                                (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                                // Remove any previous WebView child while leaving SwipeRefreshLayout's spinner view intact
                                for (i in swipeLayout.childCount - 1 downTo 0) {
                                    val child = swipeLayout.getChildAt(i)
                                    if (child is android.webkit.WebView) {
                                        swipeLayout.removeViewAt(i)
                                    }
                                }
                                swipeLayout.addView(
                                    targetWebView,
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
        )

        // Fullscreen overlays
        when (state.activeOverlay) {
            BrowserOverlay.TAB_SWITCHER -> {
                SpacesTabSwitcher(
                    state = state,
                    thumbnails = thumbnails,
                    onClose = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onSelectTab = { viewModel.selectTab(it) },
                    onCloseTab = { viewModel.closeTab(it) },
                    onCreateTab = { priv -> viewModel.createTab(isPrivate = priv) },
                    onSelectSpace = { viewModel.selectSpace(it) },
                    onTogglePrivate = { viewModel.togglePrivateView(it) },
                    onCreateSpace = { name, icon, isolated ->
                        viewModel.createSpace(name, icon, isolated)
                    },
                    onOpenEssential = { url ->
                        viewModel.setOverlay(BrowserOverlay.NONE)
                        viewModel.navigateActiveTab(url)
                    }
                )
            }
            BrowserOverlay.SETTINGS -> {
                SettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onOpenDefaultBrowserSettings = onOpenDefaultBrowserSettings
                )
            }
            BrowserOverlay.BOOKMARKS -> {
                BookmarksOverlay(
                    bookmarks = state.bookmarks,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onOpenUrl = { viewModel.navigateActiveTab(it) },
                    onDeleteBookmark = { viewModel.deleteBookmark(it) }
                )
            }
            BrowserOverlay.HISTORY -> {
                HistoryOverlay(
                    history = state.history,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onOpenUrl = { viewModel.navigateActiveTab(it) },
                    onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
                    onClearAll = { viewModel.clearHistory() }
                )
            }
            BrowserOverlay.DOWNLOADS -> {
                DownloadsOverlay(
                    downloads = state.downloads,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) }
                )
            }
            BrowserOverlay.FILTER_LISTS -> {
                FilterListsOverlay(
                    filterLists = state.filterLists,
                    adblockStatus = state.adblockStatus,
                    isUpdating = state.isUpdatingFilterLists,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onUpdateAll = { viewModel.updateAllFilterLists() },
                    onToggleList = { id, enabled -> viewModel.toggleFilterList(id, enabled) },
                    onAddCustomList = { name, url -> viewModel.addCustomFilterList(name, url) },
                    onDeleteList = { id -> viewModel.deleteCustomFilterList(id) }
                )
            }
            BrowserOverlay.USERSCRIPTS -> {
                UserscriptsOverlay(
                    scripts = state.userscripts,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onToggleScript = { id, enabled -> viewModel.toggleUserscript(id, enabled) },
                    onInstallFromUrl = { url -> viewModel.installUserscriptFromUrl(url) },
                    onImportLocalFile = { viewModel.triggerImportUserscriptFile() },
                    onSaveScriptCode = { existingId, code -> viewModel.saveUserscriptCode(existingId, code) },
                    onDeleteScript = { id -> viewModel.deleteUserscript(id) }
                )
            }
            BrowserOverlay.ZAPPED_RULES -> {
                ZappedRulesOverlay(
                    rules = state.zappedRules,
                    onBack = { viewModel.setOverlay(BrowserOverlay.NONE) },
                    onToggleRule = { id, enabled -> viewModel.toggleZappedRule(id, enabled) },
                    onDeleteRule = { id -> viewModel.deleteZappedRule(id) },
                    onClearHost = { host -> viewModel.clearZappedRulesForHost(host) }
                )
            }
            BrowserOverlay.NONE -> Unit
        }

        // Native Reader Mode Overlay
        state.readerArticle?.let { article ->
            ReaderModeOverlay(
                article = article,
                settings = state.settings,
                onClose = { viewModel.closeReaderMode() },
                onOpenLink = { linkUrl ->
                    viewModel.closeReaderMode()
                    viewModel.navigateActiveTab(linkUrl)
                },
                onSurfaceModeChange = { viewModel.setReaderSurfaceMode(it) },
                onFontFamilyChange = { viewModel.setReaderFontFamily(it) },
                onFontSizeChange = { viewModel.setReaderFontSizeSp(it) },
                onLineHeightChange = { viewModel.setReaderLineHeight(it) }
            )
        }

        // Focused Omnibox overlay
        AnimatedVisibility(
            visible = state.isOmniboxFocused,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(),
            exit = if (reduceMotion) ExitTransition.None else fadeOut()
        ) {
            OmniboxOverlay(
                query = state.omniboxQuery,
                searchEngineLabel = state.settings.searchEngine.label,
                suggestions = state.omniboxSuggestions,
                onQueryChange = { viewModel.onOmniboxQueryChanged(it) },
                onSubmit = { viewModel.submitOmnibox(it) },
                onSelectSuggestion = { suggestion: OmniboxSuggestion ->
                    if (suggestion.tabId != null) {
                        viewModel.selectTab(suggestion.tabId)
                    } else if (suggestion.targetUrl.endsWith(" ")) {
                        viewModel.onOmniboxQueryChanged(suggestion.targetUrl)
                    } else {
                        viewModel.navigateActiveTab(suggestion.targetUrl)
                    }
                },
                onDismiss = { viewModel.dismissOmnibox() }
            )
        }

        // Quick Controls Bottom Sheet (Signature Move)
        if (state.isQuickControlsOpen) {
            QuickControlsSheet(
                state = state,
                onDismiss = { viewModel.toggleQuickControls(false) },
                onToggleBookmark = { viewModel.toggleBookmarkForActiveTab() },
                onShareCleanUrl = {
                    viewModel.toggleQuickControls(false)
                    viewModel.getCleanSharePayload()?.let { (clean, count) ->
                        onShareUrl(clean, count)
                    }
                },
                onPrintPage = {
                    viewModel.toggleQuickControls(false)
                    onPrintPage()
                },
                onToggleSiteMode = { viewModel.toggleActiveTabSiteMode() },
                onToggleWebDarkMode = { viewModel.toggleActiveSiteWebDarkMode() },
                onToggleShieldAdblock = { viewModel.toggleActiveSiteAdblock() },
                onEnterPip = {
                    viewModel.toggleQuickControls(false)
                    onEnterPip()
                },
                onLaunchZapper = { viewModel.startElementZapper(primaryAccentHex) },
                onOpenReaderMode = { viewModel.openReaderMode() },
                onOpenSiteRules = { viewModel.toggleSiteRulesSheet(true) },
                onFindInPage = { viewModel.openFindInPage() },
                onPinEssential = {
                    viewModel.pinCurrentTabToEssentials()
                    viewModel.toggleQuickControls(false)
                },
                onCycleToolbarPlacement = { viewModel.cycleCurrentPostureToolbarPlacement() },
                onOpenOverlay = { viewModel.setOverlay(it) }
            )
        }

        // Per-Site Rules & Overrides Sheet
        if (state.isSiteRulesOpen && activeTab != null && !activeTab.isStartPage && activeTab.displayHost.isNotBlank()) {
            val host = activeTab.displayHost.lowercase()
            val rulesForHost = state.zappedRules.filter { it.host.equals(host, ignoreCase = true) }
            SiteRulesSheet(
                host = host,
                override = state.siteOverrides[host],
                zappedRulesForHost = rulesForHost,
                onDismiss = { viewModel.toggleSiteRulesSheet(false) },
                onSetAdblockDisabled = { viewModel.setSiteAdblockDisabled(host, it) },
                onSetWebDarkOverride = { viewModel.setSiteWebDarkOverride(host, it) },
                onSetSiteModeOverride = { viewModel.setSiteModeOverride(host, it) },
                onSetJavascriptEnabled = { viewModel.setSiteJavascriptEnabled(host, it) },
                onSetCookiesEnabled = { viewModel.setSiteCookiesEnabled(host, it) },
                onSetHttpsBypass = { viewModel.setSiteHttpsBypass(host, it) },
                onSetCustomUserAgent = { viewModel.setSiteCustomUserAgent(host, it) },
                onDeleteZappedRule = { viewModel.deleteZappedRule(it) },
                onLaunchZapper = { viewModel.startElementZapper(primaryAccentHex) },
                onResetAll = {
                    viewModel.resetSiteOverride(host)
                    viewModel.toggleSiteRulesSheet(false)
                }
            )
        }

        // Long-Press Context Menu Sheet
        state.contextMenuHit?.let { hit ->
            ContextMenuBottomSheet(
                hit = hit,
                onDismiss = { viewModel.dismissContextMenu() },
                onOpenInNewTab = {
                    viewModel.dismissContextMenu()
                    viewModel.createTab(url = hit.url, selectImmediately = false)
                    viewModel.showSnackbar("Opened in new tab")
                },
                onOpenInPrivateTab = {
                    viewModel.dismissContextMenu()
                    viewModel.createTab(url = hit.url, isPrivate = true, selectImmediately = true)
                },
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
                        viewModel.sessionCallbacks.onDownloadRequested(img, "Swiftfox", null, "image/*")
                    }
                }
            )
        }

        // External App Link Policy Prompt
        state.externalAppPrompt?.let { prompt ->
            ExternalAppDialog(
                prompt = prompt,
                onOpenApp = { viewModel.resolveExternalAppPrompt(openNative = true) },
                onStayInSwiftfox = { viewModel.resolveExternalAppPrompt(openNative = false) }
            )
        }

        // HTTPS-Only Fallback Prompt
        state.httpsErrorPrompt?.let { prompt ->
            HttpsFallbackDialog(
                prompt = prompt,
                onAllowHttp = { viewModel.resolveHttpsErrorPrompt(allowHttpForSite = true) },
                onCancel = { viewModel.resolveHttpsErrorPrompt(allowHttpForSite = false) }
            )
        }

        // Per-Site Permission Prompt
        state.permissionPrompt?.let { prompt ->
            SitePermissionDialog(
                prompt = prompt,
                onAllowOnce = { viewModel.resolvePermissionPrompt(allow = true, rememberForSite = false) },
                onAlwaysAllow = { viewModel.resolvePermissionPrompt(allow = true, rememberForSite = true) },
                onDeny = { viewModel.resolvePermissionPrompt(allow = false, rememberForSite = true) }
            )
        }
    }
}

@Composable
fun BrowserShellLayout(
    state: BrowserUiState,
    snackbarHostState: SnackbarHostState,
    onFocusOmnibox: () -> Unit,
    onFocusSearchFresh: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenQuickControls: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchAdjacentTab: (Int) -> Unit,
    onToggleUnfoldedRail: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddEssential: (String, String) -> Unit,
    onRemoveEssential: (String) -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onCloseFind: () -> Unit,
    onAdjustZapperScope: (Int) -> Unit,
    onSetZapperScopeDepth: (Int) -> Unit,
    onZapOnce: () -> Unit,
    onBlockPermanently: () -> Unit,
    onUndoTemporaryZap: () -> Unit,
    onStopZapper: () -> Unit,
    webContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    val posture = state.windowState.posture
    val profile = state.activeProfile
    val placement = if (posture == DevicePosture.FOLDED && profile.toolbarPlacement.unfoldedOnly) {
        ToolbarPlacement.SPLIT
    } else {
        profile.toolbarPlacement
    }
    val showSpacesSidebar = posture == DevicePosture.UNFOLDED && profile.showSpacesSidebar && !state.isInPictureInPicture
    val hideChrome = state.isInPictureInPicture || state.isFullscreenVideo
    val activeTab = state.activeTab

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Optional collapsible Zen-style vertical Spaces sidebar when unfolded
            if (showSpacesSidebar && !hideChrome) {
                UnfoldedSpacesSidebar(
                    state = state,
                    expanded = state.isUnfoldedRailExpanded,
                    onToggleExpanded = onToggleUnfoldedRail,
                    onSelectSpace = onSelectSpace,
                    onTogglePrivate = onTogglePrivate,
                    onSelectTab = onSelectTab,
                    onCloseTab = onCloseTab,
                    onCreateTab = onCreateTab,
                    onOpenEssential = onOpenUrl
                )
            }

            // Main content column (top toolbar + WebView/StartPage + bottom toolbar)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (!hideChrome) {
                    when (placement) {
                        ToolbarPlacement.TOP -> {
                            FullOmniboxToolbar(
                                state = state,
                                isTop = true,
                                showInlineNav = true,
                                showTrailingActions = true,
                                onFocusOmnibox = onFocusOmnibox,
                                onGoBack = onGoBack,
                                onGoForward = onGoForward,
                                onReloadOrStop = onReloadOrStop,
                                onOpenReaderMode = onOpenReaderMode,
                                onOpenQuickControls = onOpenQuickControls,
                                onOpenTabSwitcher = onOpenTabSwitcher,
                                onSwitchAdjacentTab = onSwitchAdjacentTab
                            )
                        }
                        ToolbarPlacement.SPLIT -> {
                            FullOmniboxToolbar(
                                state = state,
                                isTop = true,
                                showInlineNav = false,
                                showTrailingActions = false,
                                onFocusOmnibox = onFocusOmnibox,
                                onGoBack = onGoBack,
                                onGoForward = onGoForward,
                                onReloadOrStop = onReloadOrStop,
                                onOpenReaderMode = onOpenReaderMode,
                                onOpenQuickControls = onOpenQuickControls,
                                onOpenTabSwitcher = onOpenTabSwitcher,
                                onSwitchAdjacentTab = onSwitchAdjacentTab
                            )
                        }
                        ToolbarPlacement.BOTTOM, ToolbarPlacement.SIDE_RAIL -> {
                            // Clean status bar protection strip when top bar is absent
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }

                // Find-in-page bar
                AnimatedVisibility(
                    visible = state.findInPage.visible && !hideChrome,
                    enter = if (reduceMotion) EnterTransition.None else (slideInVertically { -it } + fadeIn()),
                    exit = if (reduceMotion) ExitTransition.None else (slideOutVertically { -it } + fadeOut())
                ) {
                    FindInPageBar(
                        state = state.findInPage,
                        onQueryChange = onFindQueryChange,
                        onFindNext = onFindNext,
                        onClose = onCloseFind
                    )
                }

                // Primary Viewport: Live WebView or calm Swiftfox Start Page
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (activeTab == null || activeTab.isStartPage) {
                        StartPageContent(
                            state = state,
                            onFocusOmnibox = onFocusSearchFresh,
                            onOpenUrl = onOpenUrl,
                            onSelectSpace = onSelectSpace,
                            onSelectTab = onSelectTab,
                            onAddEssential = onAddEssential,
                            onRemoveEssential = onRemoveEssential
                        )
                    } else {
                        webContent()
                    }

                    // Compact top domain badge when Side Rail placement is active on unfolded display
                    if (!hideChrome && placement == ToolbarPlacement.SIDE_RAIL && activeTab != null && !activeTab.isStartPage) {
                        Surface(
                            onClick = onFocusOmnibox,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .heightIn(min = 42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeTab.isSecureHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = activeTab.displayHost,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Snackbar host floating cleanly above bottom toolbar or gesture bar
                    val snackbarBottomModifier = if (placement == ToolbarPlacement.TOP || placement == ToolbarPlacement.SIDE_RAIL) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    }

                    if (state.isReaderLoading) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                Text("Preparing Reader Mode", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    if (state.zapper.isActive && !hideChrome) {
                        ZapperHudOverlay(
                            state = state.zapper,
                            onAdjustScope = onAdjustZapperScope,
                            onSetScopeDepth = onSetZapperScopeDepth,
                            onZapOnce = onZapOnce,
                            onBlockPermanently = onBlockPermanently,
                            onUndoTemporaryZap = onUndoTemporaryZap,
                            onDone = onStopZapper,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(snackbarBottomModifier)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                // Bottom toolbar variants (TOP and SIDE_RAIL render WebView truly edge-to-edge under transparent gesture bar)
                if (!hideChrome) {
                    when (placement) {
                        ToolbarPlacement.SPLIT -> {
                            SplitBottomActionBar(
                                state = state,
                                onGoBack = onGoBack,
                                onGoForward = onGoForward,
                                onOpenQuickControls = onOpenQuickControls,
                                onOpenTabSwitcher = onOpenTabSwitcher,
                                onOpenSettings = onOpenSettings
                            )
                        }
                        ToolbarPlacement.BOTTOM -> {
                            FullOmniboxToolbar(
                                state = state,
                                isTop = false,
                                showInlineNav = false,
                                showTrailingActions = true,
                                onFocusOmnibox = onFocusOmnibox,
                                onGoBack = onGoBack,
                                onGoForward = onGoForward,
                                onReloadOrStop = onReloadOrStop,
                                onOpenReaderMode = onOpenReaderMode,
                                onOpenQuickControls = onOpenQuickControls,
                                onOpenTabSwitcher = onOpenTabSwitcher,
                                onSwitchAdjacentTab = onSwitchAdjacentTab
                            )
                        }
                        ToolbarPlacement.TOP, ToolbarPlacement.SIDE_RAIL -> Unit
                    }
                }
            }

            // Unfolded right-edge ergonomic Side Rail
            if (!hideChrome && placement == ToolbarPlacement.SIDE_RAIL) {
                ErgonomicSideRail(
                    state = state,
                    onFocusOmnibox = onFocusOmnibox,
                    onGoBack = onGoBack,
                    onGoForward = onGoForward,
                    onReloadOrStop = onReloadOrStop,
                    onOpenQuickControls = onOpenQuickControls,
                    onOpenTabSwitcher = onOpenTabSwitcher,
                    onCreateTab = onCreateTab
                )
            }
        }
    }
}

@Composable
private fun FullOmniboxToolbar(
    state: BrowserUiState,
    isTop: Boolean,
    showInlineNav: Boolean,
    showTrailingActions: Boolean,
    onFocusOmnibox: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenQuickControls: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onSwitchAdjacentTab: (Int) -> Unit
) {
    val activeTab = state.activeTab
    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = activeTab?.isPrivate == true || state.isPrivateView
    val tabCount = state.visibleTabs.size.coerceAtLeast(1)

    var accumX by remember { mutableFloatStateOf(0f) }
    var accumY by remember { mutableFloatStateOf(0f) }

    val swipeModifier = if (state.settings.swipeUrlBarSwitchTabs) {
        Modifier.pointerInput(isTop, state.visibleTabs.size) {
            detectDragGestures(
                onDragStart = {
                    accumX = 0f
                    accumY = 0f
                },
                onDragEnd = {
                    if (kotlin.math.abs(accumX) > 96f && kotlin.math.abs(accumX) > kotlin.math.abs(accumY)) {
                        if (accumX < 0) onSwitchAdjacentTab(1) else onSwitchAdjacentTab(-1)
                    } else if ((isTop && accumY > 88f) || (!isTop && accumY < -88f)) {
                        onOpenTabSwitcher()
                    }
                    accumX = 0f
                    accumY = 0f
                },
                onDrag = { _, dragAmount ->
                    accumX += dragAmount.x
                    accumY += dragAmount.y
                }
            )
        }
    } else {
        Modifier
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
        ) {
            if (!isTop && activeTab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress / 100f).coerceIn(0.08f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showInlineNav) {
                    IconButton(
                        onClick = onGoBack,
                        enabled = activeTab?.canGoBack == true,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    IconButton(
                        onClick = onGoForward,
                        enabled = activeTab?.canGoForward == true,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward"
                        )
                    }
                }

                // Rounded Omnibox Capsule Pill (26dp radius + swipe tab switch)
                Surface(
                    onClick = onFocusOmnibox,
                    shape = RoundedCornerShape(26.dp),
                    color = if (isPrivate) {
                        foxColors.privateContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isPrivate) {
                            foxColors.privateAccent.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(swipeModifier)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Shield / security indicator lit with Fox Amber
                        Icon(
                            imageVector = when {
                                isPrivate -> Icons.Rounded.Shield
                                activeTab == null || activeTab.isStartPage -> Icons.Rounded.Search
                                activeTab.isSecureHttps -> Icons.Rounded.Security
                                else -> Icons.Rounded.LockOpen
                            },
                            contentDescription = "Site security",
                            tint = if (isPrivate) {
                                foxColors.privateAccent
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(18.dp)
                        )

                        Text(
                            text = when {
                                activeTab == null || activeTab.isStartPage ->
                                    "Enter URL or !bang"
                                else -> activeTab.displayHost
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (activeTab != null && !activeTab.isStartPage) {
                                    FontWeight.Medium
                                } else {
                                    FontWeight.Normal
                                }
                            ),
                            color = if (activeTab != null && !activeTab.isStartPage) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (activeTab != null && !activeTab.isStartPage && activeTab.resolvedSiteMode == SiteMode.DESKTOP) {
                            Icon(
                                imageVector = Icons.Rounded.DesktopWindows,
                                contentDescription = "Desktop view active",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (activeTab != null && !activeTab.isStartPage && activeTab.isReaderAvailable) {
                            IconButton(
                                onClick = onOpenReaderMode,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = "Reader mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (activeTab != null && !activeTab.isStartPage) {
                            IconButton(
                                onClick = onReloadOrStop,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeTab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                    contentDescription = if (activeTab.isLoading) "Stop loading" else "Reload page",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (showTrailingActions) {
                    // Accent-lit Tab Count pill
                    TabCountBadgeButton(
                        count = tabCount,
                        isPrivate = isPrivate,
                        onClick = onOpenTabSwitcher
                    )

                    // Quick Controls trigger (Icons.Rounded.Tune exclusively for Quick Controls)
                    IconButton(
                        onClick = onOpenQuickControls,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Quick Controls",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isTop && activeTab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress / 100f).coerceIn(0.08f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            } else if (isTop) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun SplitBottomActionBar(
    state: BrowserUiState,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onOpenQuickControls: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val activeTab = state.activeTab
    val isPrivate = activeTab?.isPrivate == true || state.isPrivateView
    val tabCount = state.visibleTabs.size.coerceAtLeast(1)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onGoBack,
                    enabled = activeTab?.canGoBack == true,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }

                IconButton(
                    onClick = onGoForward,
                    enabled = activeTab?.canGoForward == true,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                }

                // Center signature Quick Controls / Shield pill in Fox Amber
                Surface(
                    onClick = onOpenQuickControls,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Quick Controls & Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Controls",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                TabCountBadgeButton(
                    count = tabCount,
                    isPrivate = isPrivate,
                    onClick = onOpenTabSwitcher
                )

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }
    }
}

@Composable
private fun ErgonomicSideRail(
    state: BrowserUiState,
    onFocusOmnibox: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenQuickControls: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onCreateTab: () -> Unit
) {
    val activeTab = state.activeTab
    val isPrivate = activeTab?.isPrivate == true || state.isPrivateView
    val tabCount = state.visibleTabs.size.coerceAtLeast(1)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onFocusOmnibox,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search or enter URL",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = onCreateTab,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New tab")
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onGoBack,
                    enabled = activeTab?.canGoBack == true,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = onGoForward,
                    enabled = activeTab?.canGoForward == true,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                }
                IconButton(
                    onClick = onReloadOrStop,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (activeTab?.isLoading == true) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = "Reload"
                    )
                }
                Surface(
                    onClick = onOpenQuickControls,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Quick Controls",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TabCountBadgeButton(
                    count = tabCount,
                    isPrivate = isPrivate,
                    onClick = onOpenTabSwitcher
                )
            }
        }
    }
}

@Composable
private fun TabCountBadgeButton(
    count: Int,
    isPrivate: Boolean,
    onClick: () -> Unit
) {
    val foxColors = LocalSwiftfoxColors.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isPrivate) {
                foxColors.privateContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            border = BorderStroke(
                width = 1.5.dp,
                color = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isPrivate) foxColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FindInPageBar(
    state: FindInPageState,
    onQueryChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFindNext(true) }),
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text(
                            text = "Find in page",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            )

            if (state.query.isNotEmpty()) {
                val displayIdx = if (state.totalMatches == 0) 0 else state.activeMatchOrdinal + 1
                Text(
                    text = "$displayIdx / ${state.totalMatches}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { onFindNext(false) },
                enabled = state.totalMatches > 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(
                onClick = { onFindNext(true) },
                enabled = state.totalMatches > 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close find bar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextMenuBottomSheet(
    hit: ContextMenuHit,
    onDismiss: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInPrivateTab: () -> Unit,
    onCopyCleanLink: () -> Unit,
    onShareCleanLink: () -> Unit,
    onDownloadImage: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = hit.url,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ContextActionRow("Open in new tab", Icons.AutoMirrored.Rounded.OpenInNew, onOpenInNewTab)
            ContextActionRow("Open in private tab", Icons.Rounded.Shield, onOpenInPrivateTab)
            ContextActionRow("Copy clean link", Icons.Rounded.ContentCopy, onCopyCleanLink)
            ContextActionRow("Share clean link", Icons.Rounded.Share, onShareCleanLink)
            if (onDownloadImage != null) {
                ContextActionRow("Save image", Icons.Rounded.Download, onDownloadImage)
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ExternalAppDialog(
    prompt: ExternalAppPrompt,
    onOpenApp: () -> Unit,
    onStayInSwiftfox: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStayInSwiftfox,
        title = { Text("Open in ${prompt.appLabel}?") },
        text = {
            Text(
                text = "This link wants to leave Swiftfox and open in ${prompt.appLabel}.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenApp) {
                Text("Open app")
            }
        },
        dismissButton = {
            TextButton(onClick = onStayInSwiftfox) {
                Text("Stay in Swiftfox")
            }
        }
    )
}

@Composable
private fun HttpsFallbackDialog(
    prompt: HttpsUpgradeErrorPrompt,
    onAllowHttp: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("HTTPS unavailable for ${prompt.host}") },
        text = {
            Text(
                text = "${prompt.reason}. Swiftfox upgraded this request to HTTPS. Continue over unencrypted HTTP for this site?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onAllowHttp) {
                Text("Load HTTP")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Go back")
            }
        }
    )
}

@Composable
private fun SitePermissionDialog(
    prompt: SitePermissionPrompt,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val label = if (prompt.isGeolocation) {
        "your location"
    } else {
        prompt.resources.joinToString(", ") { res ->
            when {
                res.contains("VIDEO", true) -> "camera"
                res.contains("AUDIO", true) -> "microphone"
                res.contains("PROTECTED", true) -> "protected media (DRM)"
                else -> "device capability"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("${prompt.host} requests access") },
        text = {
            Text("Allow ${prompt.host} to use $label?")
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAllowOnce) {
                    Text("Once")
                }
                TextButton(onClick = onAlwaysAllow) {
                    Text("Always")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Deny")
            }
        }
    )
}
