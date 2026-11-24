package app.overbay.swiftfox.ui.browser

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import app.overbay.swiftfox.engine.SwiftfoxWebView
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.reader.ReaderArticle
import app.overbay.swiftfox.ui.reader.ReaderModeOverlay
import app.overbay.swiftfox.ui.settings.BookmarksOverlay
import app.overbay.swiftfox.ui.settings.DownloadsOverlay
import app.overbay.swiftfox.ui.settings.FilterListsOverlay
import app.overbay.swiftfox.ui.settings.HistoryOverlay
import app.overbay.swiftfox.ui.settings.SettingsScreen
import app.overbay.swiftfox.ui.settings.UserscriptsOverlay
import app.overbay.swiftfox.ui.sheets.SiteRulesSheet
import app.overbay.swiftfox.ui.sync.FxAAuthWebViewOverlay
import app.overbay.swiftfox.ui.sync.QrPairingOverlay
import app.overbay.swiftfox.ui.sync.SyncOverlay
import app.overbay.swiftfox.ui.sync.SyncedTabsOverlay
import app.overbay.swiftfox.ui.tabs.ArcSpacesSidebar
import app.overbay.swiftfox.ui.tabs.ClosePinnedTabConfirmationDialog
import app.overbay.swiftfox.ui.tabs.SpacesTabSwitcher
import app.overbay.swiftfox.ui.tabs.WorkspaceItemCallbacks
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import app.overbay.swiftfox.ui.zapper.ZappedRulesOverlay
import app.overbay.swiftfox.ui.zapper.ZapperHudOverlay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    thumbnailsFlow: StateFlow<TabThumbnailsSnapshot>,
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
    val activeTab = state.activeTab
    val isSiteRulesVisible = state.isSiteRulesOpen &&
        activeTab != null &&
        !activeTab.isStartPage &&
        activeTab.displayHost.isNotBlank()
    val isModalSheetShowing = isSiteRulesVisible

    LaunchedEffect(state.snackbarId, state.snackbarMessage, isModalSheetShowing) {
        val msg = state.snackbarMessage
        if (msg == null || isModalSheetShowing) {
            snackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        val canUndo = state.recentlyClosedTab != null || state.recentlyResetSiteOverride != null
        val actionLabel = if (canUndo) "Undo" else null
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            when {
                state.recentlyClosedTab != null -> viewModel.undoCloseTab()
                state.recentlyResetSiteOverride != null -> viewModel.undoResetSiteOverride()
                else -> viewModel.clearSnackbar()
            }
        } else {
            viewModel.clearSnackbar()
        }
    }

    // Predictive & System Back handling
    val shouldHandleBack = state.isFullscreenVideo ||
        state.fxaAuthUrl != null ||
        state.isQrPairingOpen ||
        state.zapper.isActive ||
        state.isReaderLoading ||
        state.readerArticle != null ||
        state.isSiteRulesOpen ||
        state.isOmniboxFocused ||
        state.activeOverlay != BrowserOverlay.NONE ||
        state.isFoldedSidebarOpen ||
        state.findInPage.visible ||
        (state.activeTab?.canGoBack == true)

    BackHandler(enabled = shouldHandleBack) {
        // Match visual Z-order: top-most visible overlay/sheet closes before drawer/WebView underneath
        when {
            state.isFullscreenVideo -> onExitFullscreenVideo()
            state.fxaAuthUrl != null -> viewModel.cancelFxAAuth()
            state.isQrPairingOpen -> viewModel.closeQrPairing()
            state.isSiteRulesOpen -> viewModel.toggleSiteRulesSheet(false)
            state.isOmniboxFocused -> viewModel.dismissOmnibox()
            state.isReaderLoading -> viewModel.cancelReaderLoading()
            state.readerArticle != null -> viewModel.closeReaderMode()
            state.activeOverlay != BrowserOverlay.NONE -> viewModel.navigateBackFromOverlay()
            state.isFoldedSidebarOpen -> viewModel.setFoldedSidebarOpen(false)
            state.zapper.isActive -> viewModel.stopElementZapper()
            state.findInPage.visible -> viewModel.closeFindInPage()
            else -> viewModel.goBack()
        }
    }

    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val primaryAccentHex = remember(primaryArgb) {
        String.format(Locale.ROOT, "#%06X", 0xFFFFFF and primaryArgb)
    }
    val surfaceArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

    val reduceMotion = rememberReduceMotion()

    val posture = state.windowState.posture
    val activeProfile = state.activeProfile
    val effectivePlacement = if (posture == DevicePosture.FOLDED && activeProfile.toolbarPlacement.unfoldedOnly) {
        ToolbarPlacement.SPLIT
    } else {
        activeProfile.toolbarPlacement
    }
    val hideChrome = state.isInPictureInPicture || state.isFullscreenVideo
    val hasBottomToolbar = !hideChrome &&
        (effectivePlacement == ToolbarPlacement.SPLIT || effectivePlacement == ToolbarPlacement.BOTTOM)
    val isAnyFullscreenOverlayOpen = state.activeOverlay != BrowserOverlay.NONE ||
        state.isQrPairingOpen ||
        state.fxaAuthUrl != null ||
        state.readerArticle != null ||
        state.isOmniboxFocused

    val density = LocalDensity.current
    val collapseScope = rememberCoroutineScope()
    val collapseBridge = remember(density) {
        with(density) {
            ToolbarCollapseBridge(
                controller = ToolbarScrollCollapseController(
                    topRevealThresholdPx = 8.dp.roundToPx()
                ),
                sideRailTravelPx = 56.dp.toPx(),
                minExtraOverflowPx = 48.dp.roundToPx(),
                minScrollableFloorPx = 160.dp.roundToPx(),
                refreshSpinnerDropPx = 64.dp.roundToPx()
            )
        }
    }
    val toolbarsCollapsed by remember(collapseBridge) {
        derivedStateOf { collapseBridge.progressState.floatValue >= 0.5f }
    }

    val forceExpandToolbars = hideChrome ||
        !state.settings.hideToolbarsOnScroll ||
        activeTab == null ||
        activeTab.isStartPage ||
        isAnyFullscreenOverlayOpen ||
        state.isSiteRulesOpen ||
        state.isFoldedSidebarOpen ||
        state.findInPage.visible ||
        state.zapper.isActive ||
        state.isReaderLoading ||
        state.externalAppPrompt != null ||
        state.httpsErrorPrompt != null ||
        state.permissionPrompt != null

    val currentHideToolbarsOnScroll by rememberUpdatedState(state.settings.hideToolbarsOnScroll)
    val currentForceExpandToolbars by rememberUpdatedState(forceExpandToolbars)
    val currentActiveTabId by rememberUpdatedState(activeTab?.id)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)

    fun animateCollapseTo(target: Float, instant: Boolean = false) {
        collapseBridge.snapJob?.cancel()
        collapseBridge.cancelFlingWork()
        val clampedTarget = target.coerceIn(0f, 1f)
        if (instant || currentReduceMotion) {
            collapseBridge.applyProgress(clampedTarget, expandedViewport = clampedTarget > 0.001f)
            return
        }
        val start = collapseBridge.renderedProgress
        if (kotlin.math.abs(start - clampedTarget) < 0.002f) {
            collapseBridge.applyProgress(clampedTarget, expandedViewport = clampedTarget > 0.001f)
            return
        }
        collapseBridge.snapJob = collapseScope.launch {
            val anim = Animatable(start)
            val remainingFraction = kotlin.math.abs(clampedTarget - start)
            val durationMs = (170f * remainingFraction).roundToInt().coerceIn(70, 170)
            anim.animateTo(
                targetValue = clampedTarget,
                animationSpec = tween(durationMillis = durationMs, easing = EmphasizedDecelerateEasing)
            ) {
                collapseBridge.applyProgress(value, expandedViewport = true)
            }
            collapseBridge.applyProgress(clampedTarget, expandedViewport = clampedTarget > 0.001f)
        }
    }

    LaunchedEffect(forceExpandToolbars, activeTab?.id, effectivePlacement, posture) {
        if (forceExpandToolbars) {
            animateCollapseTo(0f, instant = true)
        } else if (collapseBridge.renderedProgress > 0f || collapseBridge.controller.collapseProgress > 0f) {
            animateCollapseTo(0f, instant = false)
        }
    }
    LaunchedEffect(activeTab?.id, activeTab?.isLoading) {
        if (activeTab?.isLoading == true &&
            (collapseBridge.renderedProgress > 0f || collapseBridge.controller.collapseProgress > 0f)
        ) {
            animateCollapseTo(0f, instant = false)
        }
    }

    val workspaceItemCallbacks = remember(viewModel) {
        WorkspaceItemCallbacks(
            onRenameSpace = { id, name -> viewModel.renameSpace(id, name) },
            onSetSpaceIcon = { id, iconKey -> viewModel.setSpaceIcon(id, iconKey) },
            onReorderSpaces = { ids -> viewModel.reorderSpaces(ids) },
            onDeleteSpace = { id -> viewModel.deleteSpace(id) },
            onAddEssential = { title, url -> viewModel.addEssential(title, url) },
            onUpdateEssential = { id, title, url -> viewModel.updateEssential(id, title, url) },
            onSetEssentialCustomIcon = { id, spec -> viewModel.setEssentialCustomIcon(id, spec) },
            onReorderEssentials = { ids -> viewModel.reorderEssentials(ids) },
            onRemoveEssential = { id -> viewModel.removeEssential(id) },
            onActivateEssential = { id -> viewModel.activateEssential(id) },
            onCloseEssentialTab = { id -> viewModel.closeEssentialTab(id) },
            onResetEssentialTab = { id -> viewModel.resetEssentialTab(id) },
            onSetTabPinned = { tabId, pinned -> viewModel.setTabPinned(tabId, pinned) },
            onActivatePinnedTab = { tabId -> viewModel.activatePinnedTab(tabId) },
            onClosePinnedTab = { tabId -> viewModel.unloadPinnedTab(tabId) },
            onResetPinnedTab = { tabId -> viewModel.resetPinnedTab(tabId) },
            onEditTabTitleAndUrl = { tabId, title, url -> viewModel.editTabTitleAndUrl(tabId, title, url) },
            onSetTabCustomIcon = { tabId, spec -> viewModel.setTabCustomIcon(tabId, spec) },
            onAddTabToEssentials = { tabId -> viewModel.addTabToEssentials(tabId) },
            onReorderTabs = { ids, pinnedSection -> viewModel.reorderTabs(ids, pinnedSection) },
            onUpdateZenPinned = { id, title, url -> viewModel.updateZenPinnedItem(id, title, url) },
            onSetZenPinnedCustomIcon = { id, spec -> viewModel.setZenPinnedCustomIcon(id, spec) },
            onAddZenPinnedToEssentials = { id -> viewModel.addZenPinnedToEssentials(id) },
            onDeleteZenPinned = { id -> viewModel.deleteZenPinnedItem(id) },
            onReorderZenPinned = { ids -> viewModel.reorderZenPinnedItems(ids) },
            onActivateZenPinned = { id -> viewModel.activateZenPinned(id) },
            onCloseZenPinnedTab = { id -> viewModel.closeZenPinnedTab(id) },
            onResetZenPinnedTab = { id -> viewModel.resetZenPinnedTab(id) },
            onCloseAllLoadedPinnedAndEssentials = { viewModel.closeAllLoadedEssentialsAndPins() },
            onCloseAllOpenTabsInSpace = { viewModel.closeAllOpenTabsInActiveSpace() },
            onCloseEverythingInSpace = { viewModel.closeEverythingInActiveSpace() }
        )
    }

    var omniboxSourceRect by remember { mutableStateOf<Rect?>(null) }
    val omniboxProgress = remember { Animatable(if (state.isOmniboxFocused) 1f else 0f) }
    val isOmniboxAnimating by remember { derivedStateOf { omniboxProgress.value > 0.001f } }
    LaunchedEffect(state.windowState) {
        omniboxSourceRect = null
    }
    LaunchedEffect(state.isOmniboxFocused, reduceMotion) {
        val target = if (state.isOmniboxFocused) 1f else 0f
        if (reduceMotion) {
            omniboxProgress.snapTo(target)
        } else {
            omniboxProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = if (state.isOmniboxFocused) 270 else 210,
                    easing = if (state.isOmniboxFocused) EmphasizedDecelerateEasing else EmphasizedAccelerateEasing
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BrowserShellLayout(
            state = state,
            toolbarsCollapsed = toolbarsCollapsed,
            toolbarCollapseProgressProvider = { collapseBridge.progressState.floatValue },
            onChromeHeightsMeasured = { topPx, bottomPx ->
                collapseBridge.onChromeHeightsMeasured(topPx, bottomPx)
            },
            onFocusOmnibox = {
                omniboxSourceRect = null
                viewModel.focusOmnibox(prefillCurrentUrl = true)
            },
            onFocusOmniboxFromRect = { rect, prefillCurrentUrl ->
                omniboxSourceRect = rect
                viewModel.focusOmnibox(prefillCurrentUrl = prefillCurrentUrl)
            },
            onFocusSearchFresh = {
                omniboxSourceRect = null
                viewModel.focusOmnibox(prefillCurrentUrl = false)
            },
            itemCallbacks = workspaceItemCallbacks,
            onGoBack = { viewModel.goBack() },
            onGoForward = { viewModel.goForward() },
            onReloadOrStop = { viewModel.reloadOrStop() },
            onOpenReaderMode = { viewModel.openReaderMode() },
            onOpenTabSwitcher = { viewModel.setOverlay(BrowserOverlay.TAB_SWITCHER) },
            onOpenSettings = { viewModel.setOverlay(BrowserOverlay.SETTINGS) },
            onSwitchAdjacentTab = { dir -> viewModel.switchAdjacentTab(dir) },
            onToggleWorkspaceSidebar = { viewModel.toggleWorkspaceSidebar() },
            onToggleUnfoldedRail = { viewModel.toggleUnfoldedRailExpanded() },
            onCloseFoldedSidebar = { viewModel.setFoldedSidebarOpen(false) },
            onShareActiveTab = {
                viewModel.setFoldedSidebarOpen(false)
                viewModel.getCleanSharePayload()?.let { (clean, count) ->
                    onCopyText(clean)
                    onShareUrl(clean, count)
                }
            },
            onSelectSpace = { viewModel.selectSpace(it) },
            onTogglePrivate = { viewModel.togglePrivateView(it) },
            onCreateSpace = { name, icon, isolated -> viewModel.createSpace(name, icon, isolated) },
            onDeleteSpace = { spaceId -> viewModel.deleteSpace(spaceId) },
            onSelectTab = { viewModel.selectTab(it) },
            onCloseTab = { viewModel.closeTab(it) },
            onCreateTab = { viewModel.createTab() },
            onCreatePrivateTab = { viewModel.createTab(isPrivate = true) },
            onOpenUrl = { viewModel.navigateActiveTab(it) },
            onAddEssential = { title, url -> viewModel.addEssential(title, url) },
            onRemoveEssential = { viewModel.removeEssential(it) },
            onFindQueryChange = { viewModel.updateFindInPageQuery(it) },
            onFindNext = { viewModel.findNextMatch(it) },
            onCloseFind = { viewModel.closeFindInPage() },
            onAdjustZapperScope = { viewModel.adjustZapperScope(it) },
            onSetZapperScopeDepth = { viewModel.setZapperScopeDepth(it) },
            onSetZapperPreview = { viewModel.setZapperPreview(it) },
            onZapOnce = { viewModel.zapSelectedElementOnce() },
            onBlockPermanently = { viewModel.blockSelectedElementPermanently() },
            onUndoTemporaryZap = { viewModel.undoZapperTemporaryZap() },
            onStopZapper = { viewModel.stopElementZapper() },
            tabWebContent = { tab, isExiting ->
                val session: BrowserSession? = remember(tab.id, tab.sessionRevision, isExiting) {
                    if (isExiting) {
                        viewModel.getExistingSession(tab.id)?.takeUnless { it.isDestroyed }
                    } else {
                        viewModel.ensureSession(tab, context)
                    }
                }?.takeUnless { it.isDestroyed }
                if (session != null) {
                    key(tab.id, tab.sessionRevision) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val webContainer = WebViewContainer(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    lastPrimaryArgb = primaryArgb
                                    lastSurfaceArgb = surfaceArgb
                                }
                                SwipeRefreshLayout(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setColorSchemeColors(primaryArgb)
                                    setProgressBackgroundColorSchemeColor(surfaceArgb)
                                    // Host the WebView inside a stable container so SwipeRefreshLayout's
                                    // internal mCircleView.bringToFront() never alters WebView attachment
                                    // or leaves mTarget stale across tab switches.
                                    addView(webContainer)
                                    setOnChildScrollUpCallback { _, _ ->
                                        val childWebView = webContainer.getChildAt(0)
                                        webContainer.currentCollapseProgress > 0.001f ||
                                            (childWebView != null &&
                                                (childWebView.scrollY > 0 || childWebView.canScrollVertically(-1)))
                                    }
                                    setProgressViewOffset(
                                        false,
                                        collapseBridge.topBarHeightPx,
                                        collapseBridge.topBarHeightPx + collapseBridge.refreshSpinnerDropPx
                                    )
                                    setOnRefreshListener {
                                        viewModel.reloadActiveTab()
                                        isRefreshing = false
                                    }
                                }
                            },
                            update = { swipeLayout ->
                                if (session.isDestroyed) {
                                    val container = findWebViewContainer(swipeLayout)
                                    collapseBridge.detachContainer(container)
                                    container?.clearGestureCallbacks()
                                    (container?.getChildAt(0) as? SwiftfoxWebView)?.apply {
                                        onScrollMetricsChanged = null
                                        translationY = 0f
                                    }
                                    container?.removeAllViews()
                                    return@AndroidView
                                }
                                val shouldEnablePullToRefresh = !isExiting &&
                                    state.settings.pullToRefreshEnabled &&
                                    !state.zapper.isActive &&
                                    state.readerArticle == null
                                if (swipeLayout.isEnabled != shouldEnablePullToRefresh) {
                                    swipeLayout.isEnabled = shouldEnablePullToRefresh
                                }

                                val webContainer = findWebViewContainer(swipeLayout) ?: return@AndroidView
                                if (webContainer.lastPrimaryArgb != primaryArgb) {
                                    swipeLayout.setColorSchemeColors(primaryArgb)
                                    webContainer.lastPrimaryArgb = primaryArgb
                                }
                                if (webContainer.lastSurfaceArgb != surfaceArgb) {
                                    swipeLayout.setProgressBackgroundColorSchemeColor(surfaceArgb)
                                    webContainer.lastSurfaceArgb = surfaceArgb
                                }

                                val targetWebView = session.webView
                                if (targetWebView.parent !== webContainer) {
                                    (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                                    webContainer.removeAllViews()
                                    webContainer.addView(
                                        targetWebView,
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    // Resume & invalidate after re-attaching a warm WebView so Chromium's
                                    // compositor immediately repaints instead of staying blank until scroll.
                                    if (!isExiting) {
                                        session.resumeIfActive()
                                    }
                                    targetWebView.requestLayout()
                                    targetWebView.invalidate()
                                }
                                val isActiveTab = !isExiting && tab.id == currentActiveTabId
                                if (isActiveTab) {
                                    collapseBridge.attachActiveContainer(webContainer, swipeLayout)
                                    val progress = collapseBridge.renderedProgress
                                    webContainer.updateChromeMetrics(
                                        topBarHeightPx = collapseBridge.topBarHeightPx,
                                        bottomBarHeightPx = collapseBridge.bottomBarHeightPx,
                                        collapseProgress = progress,
                                        expandedViewport = progress > 0.001f || collapseBridge.isUserDragging
                                    )
                                    swipeLayout.setProgressViewOffset(
                                        false,
                                        collapseBridge.topBarHeightPx,
                                        collapseBridge.topBarHeightPx + collapseBridge.refreshSpinnerDropPx
                                    )
                                    webContainer.onGestureStart = {
                                        collapseBridge.isUserDragging = true
                                        collapseBridge.snapJob?.cancel()
                                        collapseBridge.cancelFlingWork()
                                    }
                                    webContainer.onGestureDragDelta = { rawDeltaY, wv ->
                                        val consumed = collapseBridge.controller.onDragDelta(
                                            rawDeltaY = rawDeltaY,
                                            travelDistancePx = collapseBridge.effectiveTravelDistancePx(),
                                            scrollRange = wv.verticalScrollRangePx,
                                            scrollExtent = wv.verticalScrollExtentPx,
                                            minScrollableOverflowPx = collapseBridge.minScrollableOverflowPx(),
                                            enabled = currentHideToolbarsOnScroll,
                                            forceExpanded = currentForceExpandToolbars
                                        )
                                        val next = collapseBridge.controller.collapseProgress
                                        collapseBridge.applyProgress(
                                            progress = next,
                                            expandedViewport = webContainer.isViewportExpanded || next > 0.001f
                                        )
                                        consumed
                                    }
                                    webContainer.onGestureEnd = { velocityY, wv, handoffFling ->
                                        collapseBridge.isUserDragging = false
                                        val target = collapseBridge.controller.resolveSnapTarget(
                                            velocityY = velocityY,
                                            minFlingVelocityPxPerSec = webContainer.minFlingVelocityPx,
                                            scrollY = wv.scrollY,
                                            scrollRange = wv.verticalScrollRangePx,
                                            scrollExtent = wv.verticalScrollExtentPx,
                                            minScrollableOverflowPx = collapseBridge.minScrollableOverflowPx(),
                                            enabled = currentHideToolbarsOnScroll,
                                            forceExpanded = currentForceExpandToolbars,
                                            webViewTranslatesWithTopBar = collapseBridge.hasTopBar
                                        )
                                        animateCollapseTo(target)
                                        if (handoffFling) {
                                            val flingVy = (-velocityY).roundToInt()
                                            val canScrollInFlingDirection = if (flingVy > 0) {
                                                wv.canScrollVertically(1)
                                            } else {
                                                wv.scrollY > 0 && wv.canScrollVertically(-1)
                                            }
                                            if (canScrollInFlingDirection) {
                                                wv.flingScroll(0, flingVy)
                                            }
                                        }
                                    }
                                    targetWebView.onScrollMetricsChanged = { scrollY, deltaY, scrollRange, scrollExtent ->
                                        collapseBridge.onFlingScrollMetrics(
                                            host = webContainer,
                                            scrollY = scrollY,
                                            deltaY = deltaY,
                                            scrollRange = scrollRange,
                                            scrollExtent = scrollExtent,
                                            enabled = currentHideToolbarsOnScroll,
                                            forceExpanded = currentForceExpandToolbars,
                                            isEnabledProvider = { currentHideToolbarsOnScroll },
                                            isForceExpandedProvider = { currentForceExpandToolbars },
                                            coroutineScope = collapseScope,
                                            onAnimateCollapseTo = { target -> animateCollapseTo(target) }
                                        )
                                    }
                                } else {
                                    collapseBridge.detachContainer(webContainer)
                                    webContainer.clearGestureCallbacks()
                                    targetWebView.onScrollMetricsChanged = null
                                    // Keep exiting tab aligned at its current top bar offset during slide-out
                                    webContainer.updateChromeMetrics(
                                        topBarHeightPx = collapseBridge.topBarHeightPx,
                                        bottomBarHeightPx = collapseBridge.bottomBarHeightPx,
                                        collapseProgress = 0f,
                                        expandedViewport = false
                                    )
                                }
                            },
                            onRelease = { swipeLayout ->
                                val webContainer = findWebViewContainer(swipeLayout)
                                collapseBridge.detachContainer(webContainer)
                                webContainer?.clearGestureCallbacks()
                                (webContainer?.getChildAt(0) as? SwiftfoxWebView)?.apply {
                                    onScrollMetricsChanged = null
                                    translationY = 0f
                                }
                                webContainer?.removeAllViews()
                            }
                        )
                    }
                } else {
                    ExitingTabThumbnailFallback(
                        tabId = tab.id,
                        thumbnailsFlow = thumbnailsFlow,
                        modifier = Modifier.insetByChromeHeights(collapseBridge.chromeHeights)
                    )
                }
            }
        )

        // Fullscreen overlays: clean full-height straight-up / straight-down vertical slide on
        // open & dismiss (no SizeTransform shrink or early fade-out), plus directional horizontal
        // push/pop slides when navigating between sub-overlays (e.g. Settings -> Bookmarks / Sync).
        var lastVisibleOverlay by remember { mutableStateOf(BrowserOverlay.SETTINGS) }
        var lastOverlayDepth by remember { mutableStateOf(0) }
        val isOverlayShowing = state.activeOverlay != BrowserOverlay.NONE
        val displayOverlay = if (isOverlayShowing) state.activeOverlay else lastVisibleOverlay
        val currentDepth = state.overlayBackStack.size
        val overlayPushDirection = when {
            !isOverlayShowing -> 0
            currentDepth > lastOverlayDepth -> 1
            currentDepth < lastOverlayDepth -> -1
            else -> 0
        }
        SideEffect {
            if (isOverlayShowing) {
                lastVisibleOverlay = state.activeOverlay
                lastOverlayDepth = currentDepth
            } else {
                lastOverlayDepth = 0
            }
        }

        AnimatedVisibility(
            visible = isOverlayShowing,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(tween(280, easing = EmphasizedDecelerateEasing)) { it }
            },
            exit = if (reduceMotion) {
                ExitTransition.None
            } else {
                slideOutVertically(tween(240, easing = EmphasizedAccelerateEasing)) { it }
            },
            modifier = Modifier.fillMaxSize(),
            label = "browser_overlay_sheet_slide"
        ) {
            AnimatedContent(
                targetState = displayOverlay,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    when {
                        reduceMotion ->
                            (EnterTransition.None togetherWith ExitTransition.None) using null
                        overlayPushDirection > 0 ->
                            (slideInHorizontally(tween(260, easing = EmphasizedDecelerateEasing)) { (it * 0.28f).toInt() } +
                                fadeIn(tween(180)) togetherWith
                                slideOutHorizontally(tween(220, easing = EmphasizedAccelerateEasing)) { -(it * 0.14f).toInt() } +
                                fadeOut(tween(140))) using null
                        overlayPushDirection < 0 ->
                            (slideInHorizontally(tween(240, easing = EmphasizedDecelerateEasing)) { -(it * 0.14f).toInt() } +
                                fadeIn(tween(180)) togetherWith
                                slideOutHorizontally(tween(220, easing = EmphasizedAccelerateEasing)) { (it * 0.28f).toInt() } +
                                fadeOut(tween(140))) using null
                        else ->
                            (fadeIn(tween(150)) togetherWith fadeOut(tween(110))) using null
                    }
                },
                label = "browser_overlay_subpage_transition"
            ) { overlay ->
                when (overlay) {
                BrowserOverlay.TAB_SWITCHER -> {
                    val thumbnails by thumbnailsFlow.collectAsStateWithLifecycle()
                    SpacesTabSwitcher(
                        state = state,
                        thumbnails = thumbnails,
                        onClose = { viewModel.navigateBackFromOverlay() },
                        onSelectTab = { viewModel.selectTab(it) },
                        onCloseTab = { viewModel.closeTab(it) },
                        onCreateTab = { priv -> viewModel.createTab(isPrivate = priv) },
                        onSelectSpace = { viewModel.selectSpace(it) },
                        onTogglePrivate = { viewModel.togglePrivateView(it) },
                        onCreateSpace = { name, icon, isolated ->
                            viewModel.createSpace(name, icon, isolated)
                        },
                        onDeleteSpace = { spaceId ->
                            viewModel.deleteSpace(spaceId)
                        },
                        onOpenSyncedTabs = { viewModel.setOverlay(BrowserOverlay.SYNCED_TABS) },
                        itemCallbacks = workspaceItemCallbacks
                    )
                }
                BrowserOverlay.SETTINGS -> {
                    SettingsScreen(
                        state = state,
                        viewModel = viewModel,
                        primaryAccentHex = primaryAccentHex,
                        onShareCleanUrl = {
                            viewModel.getCleanSharePayload()?.let { (clean, count) ->
                                onShareUrl(clean, count)
                            }
                        },
                        onPrintPage = onPrintPage,
                        onEnterPip = onEnterPip,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onOpenDefaultBrowserSettings = onOpenDefaultBrowserSettings
                    )
                }
                BrowserOverlay.BOOKMARKS -> {
                    BookmarksOverlay(
                        bookmarks = state.bookmarks,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onOpenUrl = { viewModel.navigateActiveTab(it) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onImportBookmarksHtml = { viewModel.triggerImportBookmarksHtml() },
                        onExportBookmarksHtml = { viewModel.triggerExportBookmarksHtml() }
                    )
                }
                BrowserOverlay.HISTORY -> {
                    HistoryOverlay(
                        history = state.history,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onOpenUrl = { viewModel.navigateActiveTab(it) },
                        onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
                        onClearAll = { viewModel.clearHistory() }
                    )
                }
                BrowserOverlay.DOWNLOADS -> {
                    DownloadsOverlay(
                        downloads = state.downloads,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onOpenDownload = { viewModel.openDownload(it) },
                        onDeleteDownload = { viewModel.deleteDownloadRecord(it) },
                        onClearAll = { viewModel.clearAllDownloads() }
                    )
                }
                BrowserOverlay.FILTER_LISTS -> {
                    FilterListsOverlay(
                        filterLists = state.filterLists,
                        adblockStatus = state.adblockStatus,
                        isUpdating = state.isUpdatingFilterLists,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onUpdateAll = { viewModel.updateAllFilterLists() },
                        onToggleList = { id, enabled -> viewModel.toggleFilterList(id, enabled) },
                        onAddCustomList = { name, url -> viewModel.addCustomFilterList(name, url) },
                        onDeleteList = { id -> viewModel.deleteCustomFilterList(id) }
                    )
                }
                BrowserOverlay.USERSCRIPTS -> {
                    UserscriptsOverlay(
                        scripts = state.userscripts,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onToggleScript = { id, enabled -> viewModel.toggleUserscript(id, enabled) },
                        onInstallFromUrl = { url -> viewModel.installUserscriptFromUrl(url) },
                        onImportLocalFile = { viewModel.triggerImportUserscriptFile() },
                        onSaveScriptCode = { existingId, code -> viewModel.saveUserscriptCode(existingId, code) },
                        onDeleteScript = { id -> viewModel.deleteUserscript(id) },
                        userscriptsEnabled = state.settings.userscriptsEnabled,
                        onEnableUserscripts = { viewModel.setUserscriptsEnabled(true) }
                    )
                }
                BrowserOverlay.ZAPPED_RULES -> {
                    ZappedRulesOverlay(
                        rules = state.zappedRules,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onToggleRule = { id, enabled -> viewModel.toggleZappedRule(id, enabled) },
                        onDeleteRule = { id -> viewModel.deleteZappedRule(id) },
                        onClearHost = { host -> viewModel.clearZappedRulesForHost(host) }
                    )
                }
                BrowserOverlay.SYNC -> {
                    SyncOverlay(
                        state = state,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onSignIn = { viewModel.startMozillaSignIn() },
                        onPairQr = { viewModel.openQrPairing() },
                        onSyncNow = { viewModel.triggerManualSync() },
                        onSignOut = { viewModel.signOutMozillaSync() },
                        onOpenSyncedTabs = { viewModel.setOverlay(BrowserOverlay.SYNCED_TABS) },
                        onToggleSyncBookmarks = { viewModel.setSyncBookmarksEnabled(it) },
                        onToggleSyncHistory = { viewModel.setSyncHistoryEnabled(it) },
                        onToggleSyncTabs = { viewModel.setSyncTabsEnabled(it) },
                        onToggleSyncZenSpaces = { viewModel.setSyncZenSpacesEnabled(it) },
                        onChangeSyncInterval = { viewModel.setSyncPeriodicIntervalMinutes(it) },
                        onImportBookmarksHtml = { viewModel.triggerImportBookmarksHtml() },
                        onExportBookmarksHtml = { viewModel.triggerExportBookmarksHtml() }
                    )
                }
                BrowserOverlay.SYNCED_TABS -> {
                    SyncedTabsOverlay(
                        state = state,
                        onBack = { viewModel.navigateBackFromOverlay() },
                        onOpenUrl = { url -> viewModel.navigateActiveTab(url) },
                        onSyncNow = { viewModel.triggerManualSync() },
                        onOpenSyncSettings = { viewModel.setOverlay(BrowserOverlay.SYNC) }
                    )
                }
                BrowserOverlay.NONE -> Unit
                }
            }
        }

        // QR Code Pairing Overlay (CameraX + ZXing) — straight-up / straight-down slide
        AnimatedVisibility(
            visible = state.isQrPairingOpen,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(tween(280, easing = EmphasizedDecelerateEasing)) { it },
            exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(240, easing = EmphasizedAccelerateEasing)) { it },
            modifier = Modifier.fillMaxSize(),
            label = "qr_pairing_slide"
        ) {
            QrPairingOverlay(
                onPairingUrlDetected = { url -> viewModel.submitPairingUrl(url) },
                onClose = { viewModel.closeQrPairing() }
            )
        }

        // Mozilla Account OAuth WebView Flow — straight-up / straight-down slide
        // Clears cached OAuth URL immediately once exit animation completes so tokens/state aren't retained.
        val fxaTransitionState = remember { MutableTransitionState(state.fxaAuthUrl != null) }
        var cachedFxaAuthUrl by remember { mutableStateOf(state.fxaAuthUrl) }
        LaunchedEffect(state.fxaAuthUrl) {
            if (state.fxaAuthUrl != null) {
                cachedFxaAuthUrl = state.fxaAuthUrl
                fxaTransitionState.targetState = true
            } else {
                fxaTransitionState.targetState = false
            }
        }
        LaunchedEffect(fxaTransitionState.isIdle, fxaTransitionState.currentState, state.fxaAuthUrl) {
            if (fxaTransitionState.isIdle && !fxaTransitionState.currentState && state.fxaAuthUrl == null) {
                cachedFxaAuthUrl = null
            }
        }
        AnimatedVisibility(
            visibleState = fxaTransitionState,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(tween(280, easing = EmphasizedDecelerateEasing)) { it },
            exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(240, easing = EmphasizedAccelerateEasing)) { it },
            modifier = Modifier.fillMaxSize(),
            label = "fxa_auth_slide"
        ) {
            (state.fxaAuthUrl ?: cachedFxaAuthUrl)?.let { authUrl ->
                FxAAuthWebViewOverlay(
                    authUrl = authUrl,
                    isRedirectUrl = { url -> viewModel.isFxAOAuthRedirectUrl(url) },
                    onRedirectCaptured = { redirect -> viewModel.handleFxAAuthRedirect(redirect) },
                    onCancel = { viewModel.cancelFxAAuth() }
                )
            }
        }

        // Native Reader Mode Overlay — straight-up / straight-down slide
        // Clears cached article immediately after exit animation so private-tab content isn't retained.
        val readerTransitionState = remember { MutableTransitionState(state.readerArticle != null) }
        var cachedReaderArticle by remember { mutableStateOf(state.readerArticle) }
        LaunchedEffect(state.readerArticle) {
            if (state.readerArticle != null) {
                cachedReaderArticle = state.readerArticle
                readerTransitionState.targetState = true
            } else {
                readerTransitionState.targetState = false
            }
        }
        LaunchedEffect(readerTransitionState.isIdle, readerTransitionState.currentState, state.readerArticle) {
            if (readerTransitionState.isIdle && !readerTransitionState.currentState && state.readerArticle == null) {
                cachedReaderArticle = null
            }
        }
        AnimatedVisibility(
            visibleState = readerTransitionState,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(tween(280, easing = EmphasizedDecelerateEasing)) { it },
            exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(240, easing = EmphasizedAccelerateEasing)) { it },
            modifier = Modifier.fillMaxSize(),
            label = "reader_mode_slide"
        ) {
            (state.readerArticle ?: cachedReaderArticle)?.let { article ->
                val tabIsPrivate = activeTab?.isPrivate == true
                ReaderModeOverlay(
                    article = article,
                    settings = state.settings,
                    onClose = { viewModel.closeReaderMode() },
                    onOpenLink = { linkUrl ->
                        if (linkUrl.startsWith("http://", ignoreCase = true) ||
                            linkUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            viewModel.closeReaderMode()
                            viewModel.navigateActiveTab(linkUrl)
                        }
                    },
                    onSurfaceModeChange = { viewModel.setReaderSurfaceMode(it) },
                    onFontFamilyChange = { viewModel.setReaderFontFamily(it) },
                    onFontSizeChange = { viewModel.setReaderFontSizeSp(it) },
                    onLineHeightChange = { viewModel.setReaderLineHeight(it) },
                    pageUrl = article.url.ifBlank { activeTab?.url.orEmpty() },
                    isPrivate = tabIsPrivate,
                    siteAdblockDisabled = state.activeSiteOverride?.adblockDisabled == true,
                    isHostHttpsBypassed = { rawHost ->
                        val norm = OmniboxEngine.normalizeHost(rawHost)
                        val ov = if (tabIsPrivate) {
                            state.privateSiteOverrides[norm] ?: state.siteOverrides[norm]
                        } else {
                            state.siteOverrides[norm]
                        }
                        ov?.httpsOnlyBypass == true
                    }
                )
            }
        }

        // Focused Omnibox overlay with zero-recomposition 120Hz bounds-anchored morph expand
        if (state.isOmniboxFocused || isOmniboxAnimating) {
            OmniboxOverlay(
                isVisible = state.isOmniboxFocused,
                expandProgressProvider = { omniboxProgress.value },
                sourceRect = omniboxSourceRect,
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

        // Per-Site Rules & Overrides Sheet
        if (isSiteRulesVisible) {
            val siteRulesTab = activeTab
            val host = OmniboxEngine.normalizeHost(siteRulesTab.displayHost)
            val persistentRulesForHost = state.zappedRules.filter { it.host.equals(host, ignoreCase = true) }
            val rulesForHost = if (siteRulesTab.isPrivate) {
                (persistentRulesForHost + state.privateZappedRules[host].orEmpty())
                    .distinctBy { it.id }
            } else {
                persistentRulesForHost
            }
            val persistentRuleIds = if (siteRulesTab.isPrivate) {
                persistentRulesForHost.mapTo(mutableSetOf()) { it.id }
            } else {
                emptySet()
            }
            SiteRulesSheet(
                host = host,
                override = state.activeSiteOverride,
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
                },
                globalAdblockEnabled = state.settings.adblockEnabled,
                isNativeRust = state.adblockStatus.isNativeRust,
                isPrivate = siteRulesTab.isPrivate,
                persistentRuleIds = persistentRuleIds,
                snackbarMessage = state.snackbarMessage,
                snackbarId = state.snackbarId,
                onClearSnackbar = { viewModel.clearSnackbar() }
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
                        viewModel.saveContextMenuImage(img)
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
                onDeny = { viewModel.resolvePermissionPrompt(allow = false, rememberForSite = true) },
                onDismiss = { viewModel.resolvePermissionPrompt(allow = false, rememberForSite = false) }
            )
        }

        // Second-tap confirmation before closing an already-active Essential or Pinned tab
        state.pendingPinCloseTarget?.let { target ->
            ClosePinnedTabConfirmationDialog(
                target = target,
                onDismiss = { viewModel.dismissPendingPinClose() },
                onConfirm = { dontShowAgain -> viewModel.confirmClosePendingPinTab(dontShowAgain) }
            )
        }

        // Root SnackbarHost rendered above all fullscreen overlays so Sync, Bookmarks, and Settings feedback is always visible
        val snackbarBottomPadding = when {
            state.activeOverlay == BrowserOverlay.TAB_SWITCHER -> 88.dp
            isAnyFullscreenOverlayOpen -> 16.dp
            state.zapper.isActive && hasBottomToolbar -> 156.dp
            state.zapper.isActive -> 100.dp
            hasBottomToolbar && !toolbarsCollapsed -> 72.dp
            else -> 12.dp
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = snackbarBottomPadding)
        )
    }
}

private class TabStripNavTracker {
    var lastTabId: String? = null
    var lastVisibleIds: List<String> = emptyList()
    var lastSpaceKey: String = ""
    var lastFullscreenOverlayOpen: Boolean = false
    var lastTabsRef: List<BrowserTabUi>? = null
    val cachedTabsById = LinkedHashMap<String, BrowserTabUi>(32)
}

@Composable
fun BrowserShellLayout(
    state: BrowserUiState,
    onFocusOmnibox: () -> Unit,
    onFocusSearchFresh: () -> Unit,
    onFocusOmniboxFromRect: (Rect?, Boolean) -> Unit = { _, prefill ->
        if (prefill) onFocusOmnibox() else onFocusSearchFresh()
    },
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchAdjacentTab: (Int) -> Unit,
    onToggleWorkspaceSidebar: () -> Unit,
    onToggleUnfoldedRail: () -> Unit,
    onCloseFoldedSidebar: () -> Unit,
    onShareActiveTab: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onCreateSpace: (String, String, Boolean) -> Unit,
    onDeleteSpace: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit = {},
    onOpenUrl: (String) -> Unit,
    onAddEssential: (String, String) -> Unit,
    onRemoveEssential: (String) -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onCloseFind: () -> Unit,
    onAdjustZapperScope: (Int) -> Unit,
    onSetZapperScopeDepth: (Int) -> Unit,
    onSetZapperPreview: (Boolean) -> Unit,
    onZapOnce: () -> Unit,
    onBlockPermanently: () -> Unit,
    onUndoTemporaryZap: () -> Unit,
    onStopZapper: () -> Unit,
    tabWebContent: @Composable (tab: BrowserTabUi, isExiting: Boolean) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks = WorkspaceItemCallbacks(
        onDeleteSpace = onDeleteSpace,
        onAddEssential = onAddEssential,
        onRemoveEssential = onRemoveEssential
    ),
    toolbarsCollapsed: Boolean = false,
    toolbarCollapseProgressProvider: () -> Float = { 0f },
    onChromeHeightsMeasured: (topBarPx: Int, bottomBarPx: Int) -> Unit = { _, _ -> }
) {
    val reduceMotion = rememberReduceMotion()
    val posture = state.windowState.posture
    val profile = state.activeProfile
    val placement = if (posture == DevicePosture.FOLDED && profile.toolbarPlacement.unfoldedOnly) {
        ToolbarPlacement.SPLIT
    } else {
        profile.toolbarPlacement
    }
    val showUnfoldedSidebar = posture == DevicePosture.UNFOLDED && profile.showSpacesSidebar && !state.isInPictureInPicture
    val hideChrome = state.isInPictureInPicture || state.isFullscreenVideo
    val activeTab = state.activeTab

    val hasBottomToolbar = !hideChrome && (placement == ToolbarPlacement.SPLIT || placement == ToolbarPlacement.BOTTOM)
    val isFullscreenOverlayOpen = state.activeOverlay != BrowserOverlay.NONE ||
        state.isQrPairingOpen ||
        state.fxaAuthUrl != null ||
        state.readerArticle != null ||
        state.isOmniboxFocused

    val navTracker = remember { TabStripNavTracker() }

    val currentTabId = activeTab?.id
    val currentVisibleIds = remember(state.tabs, state.essentials, state.zenPinnedItems, state.activeSpaceId, state.isPrivateView) {
        state.loadedNavigableTabs.map { it.id }
    }
    val currentSpaceKey = if (state.isPrivateView) "private" else state.activeSpaceId
    val slideDirection = resolveTabSlideDirection(
        previousTabId = navTracker.lastTabId,
        nextTabId = currentTabId,
        previousVisibleIds = navTracker.lastVisibleIds,
        nextVisibleIds = currentVisibleIds,
        previousSpaceKey = navTracker.lastSpaceKey,
        nextSpaceKey = currentSpaceKey,
        wasFullscreenOverlayOpen = navTracker.lastFullscreenOverlayOpen,
        reduceMotion = reduceMotion
    )

    SideEffect {
        val prevId = navTracker.lastTabId
        val tabsChanged = state.tabs !== navTracker.lastTabsRef
        if (tabsChanged) {
            navTracker.lastTabsRef = state.tabs
            val hasAnyPrivateTabs = state.tabs.any { it.isPrivate }
            state.tabs.forEach { navTracker.cachedTabsById[it.id] = it }
            if (navTracker.cachedTabsById.size > state.tabs.size) {
                val openIds = state.tabs.mapTo(HashSet(state.tabs.size * 2)) { it.id }
                val iterator = navTracker.cachedTabsById.entries.iterator()
                while (iterator.hasNext()) {
                    val (id, cachedTab) = iterator.next()
                    if (id !in openIds) {
                        // Purge closed private tabs immediately once all private tabs are gone,
                        // and keep at most the single currently-exiting tab during its 280ms transition.
                        val keepBriefly = id == prevId && (!cachedTab.isPrivate || hasAnyPrivateTabs)
                        if (!keepBriefly) {
                            iterator.remove()
                        }
                    }
                }
            }
        }
        navTracker.lastTabId = currentTabId
        navTracker.lastVisibleIds = currentVisibleIds
        navTracker.lastSpaceKey = currentSpaceKey
        navTracker.lastFullscreenOverlayOpen = isFullscreenOverlayOpen
    }

    val currentOpenTabs by rememberUpdatedState(state.tabs)
    LaunchedEffect(currentTabId, state.tabs.size) {
        if (navTracker.cachedTabsById.size <= currentOpenTabs.size) return@LaunchedEffect
        delay(350)
        val openIds = currentOpenTabs.mapTo(HashSet(currentOpenTabs.size * 2)) { it.id }
        navTracker.cachedTabsById.keys.retainAll(openIds)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        val hasTopToolbar = !hideChrome &&
            (placement == ToolbarPlacement.TOP || placement == ToolbarPlacement.SPLIT)
        val chromeHeights = remember { ChromeBarHeights() }

        Row(modifier = Modifier.fillMaxSize()) {
            // Optional collapsible Arc/Zen vertical Spaces sidebar when unfolded
            if (showUnfoldedSidebar && !hideChrome) {
                ArcSpacesSidebar(
                    state = state,
                    expanded = state.isUnfoldedRailExpanded,
                    isOverlayDrawer = false,
                    bottomBarPresent = false,
                    onToggleExpanded = onToggleUnfoldedRail,
                    onCloseDrawer = onCloseFoldedSidebar,
                    onSelectSpace = onSelectSpace,
                    onTogglePrivate = onTogglePrivate,
                    onSelectTab = onSelectTab,
                    onCloseTab = onCloseTab,
                    onCreateTab = onCreateTab,
                    onCreatePrivateTab = onCreatePrivateTab,
                    onCreateSpace = onCreateSpace,
                    onDeleteSpace = onDeleteSpace,
                    itemCallbacks = itemCallbacks
                )
            }

            // Main browser column with 1:1 scroll-linked overlay toolbars & edge-to-edge transparent nav bar
            BrowserChromeViewportLayout(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
                collapseProgressProvider = toolbarCollapseProgressProvider,
                onChromeHeightsMeasured = { topPx, bottomPx ->
                    chromeHeights.update(topPx, bottomPx)
                    onChromeHeightsMeasured(topPx, bottomPx)
                },
                statusBarStrip = {
                    if (!hideChrome) {
                        val stripColor = if (hasTopToolbar) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                                .background(stripColor)
                        )
                    }
                },
                findBar = {
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
                },
                topBar = {
                    if (!hideChrome) {
                        when (placement) {
                            ToolbarPlacement.TOP -> {
                                FullOmniboxToolbar(
                                    state = state,
                                    isTop = true,
                                    showInlineNav = posture == DevicePosture.UNFOLDED,
                                    showTrailingActions = true,
                                    showSidebarToggle = posture == DevicePosture.FOLDED,
                                    isSidebarOpen = state.isFoldedSidebarOpen,
                                    onToggleSidebar = onToggleWorkspaceSidebar,
                                    onFocusOmnibox = { rect -> onFocusOmniboxFromRect(rect, true) },
                                    onGoBack = onGoBack,
                                    onGoForward = onGoForward,
                                    onReloadOrStop = onReloadOrStop,
                                    onOpenReaderMode = onOpenReaderMode,
                                    onOpenSettings = onOpenSettings,
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
                                    onFocusOmnibox = { rect -> onFocusOmniboxFromRect(rect, true) },
                                    onGoBack = onGoBack,
                                    onGoForward = onGoForward,
                                    onReloadOrStop = onReloadOrStop,
                                    onOpenReaderMode = onOpenReaderMode,
                                    onOpenSettings = onOpenSettings,
                                    onOpenTabSwitcher = onOpenTabSwitcher,
                                    onSwitchAdjacentTab = onSwitchAdjacentTab
                                )
                            }
                            ToolbarPlacement.BOTTOM, ToolbarPlacement.SIDE_RAIL -> Unit
                        }
                    }
                },
                bottomBar = {
                    if (!hideChrome) {
                        when (placement) {
                            ToolbarPlacement.SPLIT -> {
                                SplitBottomActionBar(
                                    state = state,
                                    isSidebarOpen = if (posture == DevicePosture.FOLDED) {
                                        state.isFoldedSidebarOpen
                                    } else {
                                        profile.showSpacesSidebar && state.isUnfoldedRailExpanded
                                    },
                                    onToggleSidebar = onToggleWorkspaceSidebar,
                                    onShareActiveTab = onShareActiveTab,
                                    onCreateTab = onCreateTab,
                                    onCreatePrivateTab = onCreatePrivateTab,
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
                                    showSidebarToggle = posture == DevicePosture.FOLDED,
                                    isSidebarOpen = state.isFoldedSidebarOpen,
                                    onToggleSidebar = onToggleWorkspaceSidebar,
                                    onFocusOmnibox = { rect -> onFocusOmniboxFromRect(rect, true) },
                                    onGoBack = onGoBack,
                                    onGoForward = onGoForward,
                                    onReloadOrStop = onReloadOrStop,
                                    onOpenReaderMode = onOpenReaderMode,
                                    onOpenSettings = onOpenSettings,
                                    onOpenTabSwitcher = onOpenTabSwitcher,
                                    onSwitchAdjacentTab = onSwitchAdjacentTab
                                )
                            }
                            ToolbarPlacement.TOP, ToolbarPlacement.SIDE_RAIL -> Unit
                        }
                    }
                },
                content = {
                    AnimatedContent(
                        targetState = currentTabId,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            when {
                                reduceMotion || navTracker.lastFullscreenOverlayOpen ->
                                    (EnterTransition.None togetherWith ExitTransition.None) using SizeTransform(clip = false)
                                slideDirection > 0 ->
                                    (slideInHorizontally(tween(280, easing = EmphasizedDecelerateEasing)) { it } togetherWith
                                        slideOutHorizontally(tween(280, easing = EmphasizedDecelerateEasing)) { -it }) using
                                        SizeTransform(clip = false)
                                slideDirection < 0 ->
                                    (slideInHorizontally(tween(280, easing = EmphasizedDecelerateEasing)) { -it } togetherWith
                                        slideOutHorizontally(tween(280, easing = EmphasizedDecelerateEasing)) { it }) using
                                        SizeTransform(clip = false)
                                else ->
                                    (fadeIn(tween(140)) togetherWith fadeOut(tween(120))) using SizeTransform(clip = false)
                            }
                        },
                        label = "tab_strip_slide"
                    ) { renderedTabId ->
                        val renderedTab = if (renderedTabId == currentTabId) {
                            activeTab
                        } else {
                            state.tabs.firstOrNull { it.id == renderedTabId }
                                ?: renderedTabId?.let { navTracker.cachedTabsById[it] }
                        }
                        if (renderedTab == null || renderedTab.isStartPage) {
                            StartPageContent(
                                state = state,
                                onFocusOmnibox = onFocusSearchFresh,
                                onFocusOmniboxFromRect = { rect -> onFocusOmniboxFromRect(rect, false) },
                                onOpenUrl = onOpenUrl,
                                onSelectSpace = onSelectSpace,
                                onSelectTab = onSelectTab,
                                onAddEssential = onAddEssential,
                                onRemoveEssential = onRemoveEssential,
                                itemCallbacks = itemCallbacks,
                                addBottomNavInset = !hasBottomToolbar && !hideChrome,
                                modifier = Modifier.insetByChromeHeights(chromeHeights)
                            )
                        } else {
                            tabWebContent(renderedTab, renderedTabId != currentTabId)
                        }
                    }
                },
                hudOverlay = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Loading progress indicator in Side Rail mode (no top/bottom bar)
                        if (!hideChrome && placement == ToolbarPlacement.SIDE_RAIL) {
                            ToolbarProgressSlot(
                                isLoading = activeTab?.isLoading == true,
                                progress = activeTab?.progress ?: 0,
                                dividerAlignment = Alignment.TopCenter,
                                showIdleDivider = false,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }

                        // Compact top domain badge when Side Rail placement is active on unfolded display
                        if (!hideChrome && placement == ToolbarPlacement.SIDE_RAIL && activeTab != null && !activeTab.isStartPage) {
                            var sideRailBadgeRect by remember { mutableStateOf<Rect?>(null) }
                            Surface(
                                onClick = { onFocusOmniboxFromRect(sideRailBadgeRect, true) },
                                enabled = !toolbarsCollapsed,
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .heightIn(min = 48.dp)
                                    .onGloballyPositioned { coords ->
                                        if (coords.isAttached) sideRailBadgeRect = coords.boundsInRoot()
                                    }
                                    .graphicsLayer {
                                        val progress = toolbarCollapseProgressProvider().coerceIn(0f, 1f)
                                        translationY = -(size.height + 24f) * progress
                                        alpha = (1f - progress).coerceIn(0f, 1f)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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

                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.isReaderLoading,
                            enter = if (reduceMotion) {
                                EnterTransition.None
                            } else {
                                fadeIn(tween(150)) + scaleIn(tween(180, easing = EmphasizedDecelerateEasing), initialScale = 0.92f)
                            },
                            exit = if (reduceMotion) {
                                ExitTransition.None
                            } else {
                                fadeOut(tween(120)) + scaleOut(tween(140, easing = EmphasizedAccelerateEasing), targetScale = 0.94f)
                            },
                            modifier = Modifier.align(Alignment.Center),
                            label = "reader_loading_pill"
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 4.dp,
                                modifier = Modifier.padding(20.dp)
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

                        val dockZapperTop = state.zapper.selectedSelector != null &&
                            state.zapper.selectedViewportCenterY > 0.55f
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.zapper.isActive && !hideChrome,
                            enter = if (reduceMotion) {
                                EnterTransition.None
                            } else {
                                fadeIn(tween(180)) + slideInVertically(tween(220, easing = EmphasizedDecelerateEasing)) {
                                    if (dockZapperTop) -it else it
                                }
                            },
                            exit = if (reduceMotion) {
                                ExitTransition.None
                            } else {
                                fadeOut(tween(150)) + slideOutVertically(tween(180, easing = EmphasizedAccelerateEasing)) {
                                    if (dockZapperTop) -it else it
                                }
                            },
                            modifier = Modifier
                                .align(if (dockZapperTop) Alignment.TopCenter else Alignment.BottomCenter)
                                .then(if (!dockZapperTop && !hasBottomToolbar) Modifier.navigationBarsPadding() else Modifier),
                            label = "zapper_hud_visibility"
                        ) {
                            ZapperHudOverlay(
                                state = state.zapper,
                                onAdjustScope = onAdjustZapperScope,
                                onSetScopeDepth = onSetZapperScopeDepth,
                                onSetPreview = onSetZapperPreview,
                                onZapOnce = onZapOnce,
                                onBlockPermanently = onBlockPermanently,
                                onUndoTemporaryZap = onUndoTemporaryZap,
                                onDone = onStopZapper
                            )
                        }
                    }
                },
                foldedDrawer = {
                    if (posture == DevicePosture.FOLDED && !hideChrome) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            FoldedSidebarDrawerOverlay(
                                state = state,
                                reduceMotion = reduceMotion,
                                hasBottomToolbar = hasBottomToolbar,
                                onCloseFoldedSidebar = onCloseFoldedSidebar,
                                onSelectSpace = onSelectSpace,
                                onTogglePrivate = onTogglePrivate,
                                onSelectTab = onSelectTab,
                                onCloseTab = onCloseTab,
                                onCreateTab = onCreateTab,
                                onCreatePrivateTab = onCreatePrivateTab,
                                onOpenUrl = onOpenUrl,
                                onCreateSpace = onCreateSpace,
                                onDeleteSpace = onDeleteSpace,
                                itemCallbacks = itemCallbacks
                            )
                        }
                    }
                }
            )

            // Unfolded right-edge ergonomic Side Rail
            if (!hideChrome && placement == ToolbarPlacement.SIDE_RAIL) {
                ErgonomicSideRail(
                    state = state,
                    onFocusOmnibox = { rect -> onFocusOmniboxFromRect(rect, true) },
                    onGoBack = onGoBack,
                    onGoForward = onGoForward,
                    onReloadOrStop = onReloadOrStop,
                    onOpenReaderMode = onOpenReaderMode,
                    onOpenSettings = onOpenSettings,
                    onOpenTabSwitcher = onOpenTabSwitcher,
                    onCreateTab = onCreateTab
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FoldedSidebarDrawerOverlay(
    state: BrowserUiState,
    reduceMotion: Boolean,
    hasBottomToolbar: Boolean,
    onCloseFoldedSidebar: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCreateSpace: (String, String, Boolean) -> Unit,
    onDeleteSpace: (String) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks
) {
    AnimatedVisibility(
        visible = state.isFoldedSidebarOpen,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(240)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(200))
    ) {
        val scrimInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null,
                    onClick = onCloseFoldedSidebar
                )
        )
    }

    val drawerDragOffsetPx = remember { Animatable(0f) }
    val drawerScope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val currentOnCloseFoldedSidebar by rememberUpdatedState(onCloseFoldedSidebar)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    LaunchedEffect(state.isFoldedSidebarOpen) {
        if (state.isFoldedSidebarOpen) {
            drawerDragOffsetPx.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = state.isFoldedSidebarOpen,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            slideInHorizontally(tween(280, easing = EmphasizedDecelerateEasing)) { -it }
        },
        exit = if (reduceMotion) {
            ExitTransition.None
        } else {
            slideOutHorizontally(tween(220, easing = EmphasizedAccelerateEasing)) { -it }
        },
        modifier = Modifier.align(Alignment.CenterStart)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset { IntOffset(drawerDragOffsetPx.value.roundToInt(), 0) }
                .pointerInput(dismissThresholdPx) {
                    var currentDragOffset = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            currentDragOffset = drawerDragOffsetPx.value
                        },
                        onDragEnd = {
                            if (currentDragOffset < -dismissThresholdPx) {
                                currentOnCloseFoldedSidebar()
                            } else {
                                currentDragOffset = 0f
                                drawerScope.launch {
                                    if (currentReduceMotion) {
                                        drawerDragOffsetPx.snapTo(0f)
                                    } else {
                                        drawerDragOffsetPx.animateTo(
                                            0f,
                                            tween(180, easing = EmphasizedDecelerateEasing)
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            currentDragOffset = 0f
                            drawerScope.launch {
                                if (currentReduceMotion) {
                                    drawerDragOffsetPx.snapTo(0f)
                                } else {
                                    drawerDragOffsetPx.animateTo(
                                        0f,
                                        tween(180, easing = EmphasizedDecelerateEasing)
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            currentDragOffset = (currentDragOffset + dragAmount).coerceAtMost(0f)
                            val target = currentDragOffset
                            drawerScope.launch {
                                drawerDragOffsetPx.snapTo(target)
                            }
                        }
                    )
                }
        ) {
            ArcSpacesSidebar(
                state = state,
                expanded = true,
                isOverlayDrawer = true,
                bottomBarPresent = hasBottomToolbar,
                onToggleExpanded = onCloseFoldedSidebar,
                onCloseDrawer = onCloseFoldedSidebar,
                onSelectSpace = onSelectSpace,
                onTogglePrivate = onTogglePrivate,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onCreateTab = onCreateTab,
                onCreatePrivateTab = onCreatePrivateTab,
                onCreateSpace = onCreateSpace,
                onDeleteSpace = onDeleteSpace,
                itemCallbacks = itemCallbacks
            )
        }
    }
}

private class DragAccum(
    var x: Float = 0f,
    var y: Float = 0f
)

@Composable
private fun FullOmniboxToolbar(
    state: BrowserUiState,
    isTop: Boolean,
    showInlineNav: Boolean,
    showTrailingActions: Boolean,
    onFocusOmnibox: (Rect?) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onSwitchAdjacentTab: (Int) -> Unit,
    showSidebarToggle: Boolean = false,
    isSidebarOpen: Boolean = false,
    onToggleSidebar: () -> Unit = {}
) {
    val activeTab = state.activeTab
    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = activeTab?.isPrivate == true || state.isPrivateView
    val tabCount = state.loadedNavigableTabs.size.coerceAtLeast(1)
    val accentTint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary
    val accentContainer = if (isPrivate) foxColors.privateContainer else MaterialTheme.colorScheme.primaryContainer

    val density = LocalDensity.current
    val horizontalSwipeThresholdPx = with(density) { 48.dp.toPx() }
    val verticalSwipeThresholdPx = with(density) { 40.dp.toPx() }
    val dragAccum = remember { DragAccum() }
    val currentOnSwitchAdjacentTab by rememberUpdatedState(onSwitchAdjacentTab)
    val currentOnOpenTabSwitcher by rememberUpdatedState(onOpenTabSwitcher)

    val swipeModifier = if (state.settings.swipeUrlBarSwitchTabs) {
        Modifier.pointerInput(isTop, state.loadedNavigableTabs.size, horizontalSwipeThresholdPx, verticalSwipeThresholdPx) {
            detectDragGestures(
                onDragStart = {
                    dragAccum.x = 0f
                    dragAccum.y = 0f
                },
                onDragEnd = {
                    if (kotlin.math.abs(dragAccum.x) > horizontalSwipeThresholdPx &&
                        kotlin.math.abs(dragAccum.x) > kotlin.math.abs(dragAccum.y)
                    ) {
                        if (dragAccum.x < 0) currentOnSwitchAdjacentTab(1) else currentOnSwitchAdjacentTab(-1)
                    } else if ((isTop && dragAccum.y > verticalSwipeThresholdPx) ||
                        (!isTop && dragAccum.y < -verticalSwipeThresholdPx)
                    ) {
                        currentOnOpenTabSwitcher()
                    }
                    dragAccum.x = 0f
                    dragAccum.y = 0f
                },
                onDrag = { _, dragAmount ->
                    dragAccum.x += dragAmount.x
                    dragAccum.y += dragAmount.y
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
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                )
                .then(
                    if (isTop) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                        )
                    } else {
                        Modifier.navigationBarsPadding()
                    }
                )
        ) {
            if (!isTop) {
                ToolbarProgressSlot(
                    isLoading = activeTab?.isLoading == true,
                    progress = activeTab?.progress ?: 0,
                    dividerAlignment = Alignment.TopCenter
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showSidebarToggle) {
                    Surface(
                        onClick = onToggleSidebar,
                        shape = CircleShape,
                        color = if (isSidebarOpen) accentContainer else Color.Transparent,
                        border = if (isSidebarOpen) BorderStroke(1.dp, accentTint.copy(alpha = 0.45f)) else null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                                contentDescription = if (isSidebarOpen) {
                                    "Close workspace sidebar"
                                } else {
                                    "Open workspace sidebar"
                                },
                                tint = if (isSidebarOpen) accentTint else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer { scaleX = -1f }
                            )
                        }
                    }
                }

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

                // Rounded Omnibox Capsule Pill (26dp radius + swipe tab switch + bounds tracking for morph expand)
                var urlPillRect by remember { mutableStateOf<Rect?>(null) }
                Surface(
                    onClick = { onFocusOmnibox(urlPillRect) },
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
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) urlPillRect = coords.boundsInRoot()
                        }
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

                    // Settings & Quick Actions trigger
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings & Quick Actions",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isTop) {
                ToolbarProgressSlot(
                    isLoading = activeTab?.isLoading == true,
                    progress = activeTab?.progress ?: 0,
                    dividerAlignment = Alignment.BottomCenter
                )
            }
        }
    }
}

@Composable
private fun ToolbarProgressSlot(
    isLoading: Boolean,
    progress: Int,
    dividerAlignment: Alignment,
    modifier: Modifier = Modifier,
    showIdleDivider: Boolean = true
) {
    val reduceMotion = rememberReduceMotion()
    val targetFraction = if (isLoading) (progress / 100f).coerceIn(0.08f, 1f) else 1f
    val animatedFraction = animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 160),
        label = "page_load_progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.5.dp)
    ) {
        if (showIdleDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.align(dividerAlignment)
            )
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(80)),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            LinearProgressIndicator(
                progress = { animatedFraction.value },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitBottomActionBar(
    state: BrowserUiState,
    isSidebarOpen: Boolean,
    onToggleSidebar: () -> Unit,
    onShareActiveTab: () -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val activeTab = state.activeTab
    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = activeTab?.isPrivate == true || state.isPrivateView
    val tabCount = state.loadedNavigableTabs.size.coerceAtLeast(1)
    val accentTint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary
    val accentContainer = if (isPrivate) foxColors.privateContainer else MaterialTheme.colorScheme.primaryContainer

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                )
                .navigationBarsPadding()
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Leftmost: Arc/Zen left workspace sidebar toggle
                Surface(
                    onClick = onToggleSidebar,
                    shape = CircleShape,
                    color = if (isSidebarOpen) accentContainer else Color.Transparent,
                    border = if (isSidebarOpen) BorderStroke(1.dp, accentTint.copy(alpha = 0.45f)) else null,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                            contentDescription = if (isSidebarOpen) {
                                "Close workspace sidebar"
                            } else {
                                "Open workspace sidebar"
                            },
                            tint = if (isSidebarOpen) accentTint else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer { scaleX = -1f }
                        )
                    }
                }

                // 2. Copy & Share clean URL
                IconButton(
                    onClick = onShareActiveTab,
                    enabled = activeTab != null && !activeTab.isStartPage,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Copy and share link"
                    )
                }

                // 3. Center: New Tab (+) — tap for Start Page tab, long-press for Private tab
                Surface(
                    shape = CircleShape,
                    color = accentContainer,
                    border = BorderStroke(1.dp, accentTint.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onCreateTab,
                            onLongClick = onCreatePrivateTab,
                            onClickLabel = "New tab",
                            onLongClickLabel = "New private tab"
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New tab",
                            tint = accentTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 4. Tab switcher count badge
                TabCountBadgeButton(
                    count = tabCount,
                    isPrivate = isPrivate,
                    onClick = onOpenTabSwitcher
                )

                // 5. Settings (with Quick Actions at top)
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
    onFocusOmnibox: (Rect?) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onCreateTab: () -> Unit
) {
    val activeTab = state.activeTab
    val isPrivate = activeTab?.isPrivate == true || state.isPrivateView
    val tabCount = state.loadedNavigableTabs.size.coerceAtLeast(1)
    var searchButtonRect by remember { mutableStateOf<Rect?>(null) }

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
                    onClick = { onFocusOmnibox(searchButtonRect) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) searchButtonRect = coords.boundsInRoot()
                        }
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
                if (activeTab != null && !activeTab.isStartPage && activeTab.isReaderAvailable) {
                    IconButton(
                        onClick = onOpenReaderMode,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = "Reader mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        contentDescription = if (activeTab?.isLoading == true) "Stop loading" else "Reload page"
                    )
                }
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings & Quick Actions",
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
    val reduceMotion = rememberReduceMotion()
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
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        when {
                            reduceMotion ->
                                (EnterTransition.None togetherWith ExitTransition.None) using null
                            targetState > initialState ->
                                (slideInVertically(tween(180, easing = EmphasizedDecelerateEasing)) { it / 2 } +
                                    fadeIn(tween(140)) togetherWith
                                    slideOutVertically(tween(140, easing = EmphasizedAccelerateEasing)) { -it / 2 } +
                                    fadeOut(tween(100))) using null
                            else ->
                                (slideInVertically(tween(180, easing = EmphasizedDecelerateEasing)) { -it / 2 } +
                                    fadeIn(tween(140)) togetherWith
                                    slideOutVertically(tween(140, easing = EmphasizedAccelerateEasing)) { it / 2 } +
                                    fadeOut(tween(100))) using null
                        }
                    },
                    label = "tab_count_roll"
                ) { displayedCount ->
                    Text(
                        text = if (displayedCount > 99) "99+" else displayedCount.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isPrivate) foxColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
    val focusRequester = remember { FocusRequester() }
    val textFieldState = rememberTextFieldState(initialText = state.query)
    val currentText = textFieldState.text.toString()
    var lastSyncedQuery by remember { mutableStateOf(state.query) }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { typed ->
                if (typed != lastSyncedQuery) {
                    lastSyncedQuery = typed
                    onQueryChange(typed)
                }
            }
    }
    LaunchedEffect(state.query) {
        if (state.query != lastSyncedQuery) {
            lastSyncedQuery = state.query
            if (textFieldState.text.toString() != state.query) {
                textFieldState.setTextAndPlaceCursorAtEnd(state.query)
            }
        }
    }
    LaunchedEffect(state.visible) {
        if (state.visible) {
            focusRequester.requestFocus()
        }
    }

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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                state = textFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = { onFindNext(true) },
                decorator = { inner ->
                    if (currentText.isEmpty()) {
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
                    .focusRequester(focusRequester)
            )

            if (currentText.isNotEmpty()) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    onDeny: () -> Unit,
    onDismiss: () -> Unit
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
        onDismissRequest = onDismiss,
        title = { Text("${prompt.host} requests access") },
        text = {
            Text("Allow ${prompt.host} to use $label?")
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
