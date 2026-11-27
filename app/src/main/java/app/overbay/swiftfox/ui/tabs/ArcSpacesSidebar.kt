package app.overbay.swiftfox.ui.tabs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import app.overbay.swiftfox.ui.icons.automirrored.VolumeUp
import androidx.compose.material.icons.rounded.Add
import app.overbay.swiftfox.ui.icons.rounded.ChevronLeft
import app.overbay.swiftfox.ui.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import app.overbay.swiftfox.ui.icons.rounded.CreateNewFolder
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.DesktopWindows
import app.overbay.swiftfox.ui.icons.rounded.DragIndicator
import app.overbay.swiftfox.ui.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import app.overbay.swiftfox.ui.icons.rounded.ExpandMore
import app.overbay.swiftfox.ui.icons.rounded.Folder
import app.overbay.swiftfox.ui.icons.rounded.FolderOpen
import app.overbay.swiftfox.ui.icons.rounded.Palette
import app.overbay.swiftfox.ui.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import app.overbay.swiftfox.ui.icons.rounded.Shield
import app.overbay.swiftfox.ui.icons.rounded.StarOutline
import app.overbay.swiftfox.ui.icons.rounded.SwapVert
import app.overbay.swiftfox.ui.icons.rounded.Tab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.EmphasizedDecelerateEasing
import app.overbay.swiftfox.ui.components.CustomIconPickerSheet
import app.overbay.swiftfox.ui.components.DragAxis
import app.overbay.swiftfox.ui.components.EditTitleAndUrlDialog
import app.overbay.swiftfox.ui.components.EditorialFooterActionGlyph
import app.overbay.swiftfox.ui.components.EditorialSpaceFooterGlyph
import app.overbay.swiftfox.ui.components.EditorialSpaceRailGlyph
import app.overbay.swiftfox.ui.components.ItemActionBottomSheet
import app.overbay.swiftfox.ui.components.RenameSpaceDialog
import app.overbay.swiftfox.ui.components.ReorderModeBanner
import app.overbay.swiftfox.ui.components.SheetActionItem
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.components.SpaceIconGlyph
import app.overbay.swiftfox.ui.components.dragReorderContainer
import app.overbay.swiftfox.ui.components.dragReorderItem
import app.overbay.swiftfox.ui.components.horizontalFadingEdges
import app.overbay.swiftfox.ui.components.parseSpaceColorHexOrNull
import app.overbay.swiftfox.ui.components.rememberDragReorderController
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxBrandAppearance
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxAppIcon
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import app.overbay.swiftfox.ui.theme.rememberReduceMotion

private enum class SidebarReorderMode {
    NONE,
    SPACES,
    ESSENTIALS,
    PINNED_TABS,
    FOLDER_TABS,
    ZEN_PINNED,
    OPEN_TABS
}

@Composable
fun ArcSpacesSidebar(
    state: BrowserUiState,
    expanded: Boolean,
    isOverlayDrawer: Boolean,
    bottomBarPresent: Boolean,
    onToggleExpanded: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onCreateSpace: (String, String) -> Unit,
    onDeleteSpace: (String) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks = WorkspaceItemCallbacks(onDeleteSpace = onDeleteSpace),
    modifier: Modifier = Modifier
) {
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var showCloseAllSheet by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(SidebarReorderMode.NONE) }
    var reorderingFolderId by remember { mutableStateOf<String?>(null) }

    // Active action-sheet & dialog targets
    var actionSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToRename by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToPickIcon by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }

    var actionEssential by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToEdit by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToPickIcon by remember { mutableStateOf<EssentialEntity?>(null) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderInitialTabId by remember { mutableStateOf<String?>(null) }
    var actionFolder by remember { mutableStateOf<PinnedFolderEntity?>(null) }
    var folderToRename by remember { mutableStateOf<PinnedFolderEntity?>(null) }
    var folderToPickIcon by remember { mutableStateOf<PinnedFolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<PinnedFolderEntity?>(null) }

    var actionTab by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToEdit by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToPickIcon by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToMoveToFolder by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToMoveToSpace by remember { mutableStateOf<BrowserTabUi?>(null) }

    var actionZenPinned by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToEdit by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToPickIcon by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToMoveToSpace by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }

    var railQuickPickLocalFolderId by remember { mutableStateOf<String?>(null) }
    var railQuickPickZenFolder by remember { mutableStateOf<Pair<String, String>?>(null) }

    val reduceMotion = rememberReduceMotion()
    val isEffectiveExpanded = isOverlayDrawer || expanded

    // Exit reorder mode cleanly if the sidebar collapses or switches private/space view
    LaunchedEffect(isEffectiveExpanded, state.isPrivateView, state.activeSpaceId) {
        reorderMode = SidebarReorderMode.NONE
        reorderingFolderId = null
    }
    LaunchedEffect(reorderMode) {
        if (reorderMode != SidebarReorderMode.FOLDER_TABS) {
            reorderingFolderId = null
        }
    }

    val expandedWidth = if (state.windowState.windowWidthDp < 780) 232.dp else 264.dp
    val targetWidth = if (expanded) expandedWidth else 72.dp
    val animatedWidthState = animateDpAsState(
        targetValue = targetWidth,
        animationSpec = if (reduceMotion) snap() else tween(260, easing = EmphasizedDecelerateEasing),
        label = "arc_sidebar_width"
    )
    val contentTargetWidth = if (isEffectiveExpanded) expandedWidth else 72.dp

    val containerModifier = if (isOverlayDrawer) {
        modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 304.dp)
            .fillMaxHeight()
    } else {
        modifier
            .layout { measurable, constraints ->
                val widthPx = animatedWidthState.value.roundToPx().coerceIn(
                    constraints.minWidth,
                    constraints.maxWidth
                )
                val placeable = measurable.measure(
                    constraints.copy(minWidth = widthPx, maxWidth = widthPx)
                )
                layout(widthPx, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
            .fillMaxHeight()
            .clipToBounds()
    }

    val drawerShape = if (isOverlayDrawer) {
        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    } else {
        RectangleShape
    }

    Surface(
        modifier = containerModifier,
        shape = drawerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isOverlayDrawer) 3.dp else 1.dp,
        border = if (isOverlayDrawer) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        } else {
            null
        }
    ) {
        val innerColumnModifier = if (isOverlayDrawer) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxHeight()
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .width(contentTargetWidth)
        }
        Crossfade(
            targetState = isEffectiveExpanded,
            animationSpec = if (reduceMotion) snap() else tween(200),
            modifier = innerColumnModifier,
            label = "arc_sidebar_mode_crossfade"
        ) { showExpanded ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                    .statusBarsPadding()
                    .then(if (!bottomBarPresent) Modifier.navigationBarsPadding() else Modifier)
                    .padding(
                        horizontal = when {
                            isOverlayDrawer -> 14.dp
                            showExpanded -> 12.dp
                            else -> 8.dp
                        },
                        vertical = if (isOverlayDrawer) 12.dp else 10.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showExpanded) {
                    ArcExpandedSidebarContent(
                        state = state,
                        isOverlayDrawer = isOverlayDrawer,
                        reorderMode = reorderMode,
                        reorderingFolderId = reorderingFolderId,
                        onExitReorderMode = {
                            reorderMode = SidebarReorderMode.NONE
                            reorderingFolderId = null
                        },
                        onToggleExpanded = onToggleExpanded,
                        onCloseDrawer = onCloseDrawer,
                        onSelectSpace = onSelectSpace,
                        onTogglePrivate = onTogglePrivate,
                        onSelectTab = onSelectTab,
                        onCloseTab = onCloseTab,
                        onCreateTab = onCreateTab,
                        onCreatePrivateTab = onCreatePrivateTab,
                        onRequestCreateSpace = { showCreateSpaceDialog = true },
                        onRequestCreateFolder = {
                            createFolderInitialTabId = null
                            showCreateFolderDialog = true
                        },
                        onRequestCloseAll = { showCloseAllSheet = true },
                        onLongPressSpace = { actionSpace = it },
                        onLongPressEssential = { actionEssential = it },
                        onLongPressFolder = { actionFolder = it },
                        onLongPressTab = { actionTab = it },
                        onLongPressZenPinned = { actionZenPinned = it },
                        itemCallbacks = itemCallbacks
                    )
                } else {
                    ArcCollapsedSidebarRail(
                        state = state,
                        onToggleExpanded = onToggleExpanded,
                        onSelectSpace = onSelectSpace,
                        onTogglePrivate = onTogglePrivate,
                        onSelectTab = onSelectTab,
                        onCreateTab = onCreateTab,
                        onCreatePrivateTab = onCreatePrivateTab,
                        onLongPressSpace = { actionSpace = it },
                        onLongPressEssential = { actionEssential = it },
                        onOpenLocalFolderQuickPick = { folderId -> railQuickPickLocalFolderId = folderId },
                        onOpenZenFolderQuickPick = { spaceId, folderName ->
                            railQuickPickZenFolder = spaceId to folderName
                        },
                        onLongPressFolder = { actionFolder = it },
                        onLongPressTab = { actionTab = it },
                        onLongPressZenPinned = { actionZenPinned = it },
                        itemCallbacks = itemCallbacks
                    )
                }
            }
        }
    }

    // --- Dialogs & Action Sheets ---

    if (showCreateSpaceDialog) {
        CreateSpaceDialog(
            onDismiss = { showCreateSpaceDialog = false },
            onConfirm = { name, iconKey ->
                showCreateSpaceDialog = false
                onCreateSpace(name, iconKey)
            }
        )
    }

    actionSpace?.let { target ->
        ItemActionBottomSheet(
            title = target.name,
            subtitle = "Space",
            leadingIcon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SpaceIconGlyph(
                            iconKey = target.iconKey,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 22.dp
                        )
                    }
                }
            },
            actions = buildList {
                add(
                    SheetActionItem(
                        label = "Rename Space",
                        icon = Icons.Rounded.Edit,
                        onClick = { spaceToRename = target }
                    )
                )
                add(
                    SheetActionItem(
                        label = "Change icon",
                        icon = Icons.Rounded.Palette,
                        onClick = { spaceToPickIcon = target }
                    )
                )
                if (!state.isPrivateView && target.id == state.activeSpaceId) {
                    add(
                        SheetActionItem(
                            label = "New pinned folder",
                            icon = Icons.Rounded.CreateNewFolder,
                            onClick = {
                                createFolderInitialTabId = null
                                showCreateFolderDialog = true
                            }
                        )
                    )
                }
                add(
                    SheetActionItem(
                        label = "Reorder Spaces",
                        icon = Icons.Rounded.SwapVert,
                        enabled = state.spaces.size > 1 && isEffectiveExpanded,
                        onClick = { reorderMode = SidebarReorderMode.SPACES }
                    )
                )
                add(
                    SheetActionItem(
                        label = "Delete Space",
                        icon = Icons.Rounded.DeleteOutline,
                        isDestructive = true,
                        enabled = state.spaces.size > 1,
                        onClick = { spaceToDelete = target }
                    )
                )
            },
            onDismiss = { actionSpace = null }
        )
    }

    spaceToRename?.let { target ->
        RenameSpaceDialog(
            initialName = target.name,
            onDismiss = { spaceToRename = null },
            onConfirm = { newName ->
                itemCallbacks.onRenameSpace(target.id, newName)
                spaceToRename = null
            }
        )
    }

    spaceToPickIcon?.let { target ->
        CustomIconPickerSheet(
            title = "Icon for ${target.name}",
            currentIconSpec = target.iconKey,
            allowResetToFavicon = false,
            onSelectIcon = { spec ->
                if (spec != null) itemCallbacks.onSetSpaceIcon(target.id, spec)
            },
            onDismiss = { spaceToPickIcon = null }
        )
    }

    spaceToDelete?.let { target ->
        DeleteSpaceDialog(
            space = target,
            onDismiss = { spaceToDelete = null },
            onConfirm = {
                onDeleteSpace(target.id)
                spaceToDelete = null
            }
        )
    }

    if (showCloseAllSheet) {
        CloseAllTabsSheet(
            state = state,
            onCloseLoadedPinnedAndEssentials = itemCallbacks.onCloseAllLoadedPinnedAndEssentials,
            onCloseOpenTabsInSpace = itemCallbacks.onCloseAllOpenTabsInSpace,
            onCloseEverythingInSpace = itemCallbacks.onCloseEverythingInSpace,
            onDismiss = { showCloseAllSheet = false }
        )
    }

    actionEssential?.let { target ->
        val boundTab = state.tabForEssential(target.id)
        val essentialActions = buildList {
            if (boundTab != null) {
                add(
                    SheetActionItem(
                        label = "Close tab",
                        icon = Icons.Rounded.Close,
                        onClick = { itemCallbacks.onCloseEssentialTab(target.id) }
                    )
                )
                if (!boundTab.url.equals(target.url, ignoreCase = true)) {
                    add(
                        SheetActionItem(
                            label = "Reset to pinned URL",
                            icon = Icons.Rounded.Refresh,
                            onClick = { itemCallbacks.onResetEssentialTab(target.id) }
                        )
                    )
                }
            }
            add(
                SheetActionItem(
                    label = "Edit title & URL",
                    icon = Icons.Rounded.Edit,
                    onClick = { essentialToEdit = target }
                )
            )
            add(
                SheetActionItem(
                    label = "Set custom icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { essentialToPickIcon = target }
                )
            )
            add(
                SheetActionItem(
                    label = "Reorder essentials",
                    icon = Icons.Rounded.SwapVert,
                    enabled = state.activeSpaceEssentials.size > 1,
                    onClick = { reorderMode = SidebarReorderMode.ESSENTIALS }
                )
            )
            add(
                SheetActionItem(
                    label = "Delete",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { itemCallbacks.onRemoveEssential(target.id) }
                )
            )
        }
        val subtitleText = when {
            boundTab == null -> target.domain
            boundTab.displayHost.isNotBlank() && !boundTab.displayHost.equals(target.domain, ignoreCase = true) ->
                "${boundTab.displayHost} · Open"
            else -> "${target.domain} · Open"
        }
        ItemActionBottomSheet(
            title = target.title,
            subtitle = subtitleText,
            leadingIcon = {
                SiteIconBadge(
                    url = target.url,
                    fallbackLabel = target.title,
                    customIcon = target.customIcon,
                    size = 40.dp,
                    iconSize = 24.dp,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    fillImageToContainer = true
                )
            },
            actions = essentialActions,
            onDismiss = { actionEssential = null }
        )
    }

    essentialToEdit?.let { target ->
        EditTitleAndUrlDialog(
            dialogTitle = "Edit Essential",
            initialTitle = target.title,
            initialUrl = target.url,
            onDismiss = { essentialToEdit = null },
            onConfirm = { title, url ->
                itemCallbacks.onUpdateEssential(target.id, title, url)
                essentialToEdit = null
            }
        )
    }

    essentialToPickIcon?.let { target ->
        CustomIconPickerSheet(
            title = "Icon for ${target.title}",
            currentIconSpec = target.customIcon,
            allowResetToFavicon = true,
            previewUrl = target.url,
            previewLabel = target.title,
            onSelectIcon = { spec ->
                itemCallbacks.onSetEssentialCustomIcon(target.id, spec)
            },
            onDismiss = { essentialToPickIcon = null }
        )
    }

    actionTab?.let { target ->
        val isPrivate = target.isPrivate
        val tabActions = buildList {
            if (!isPrivate) {
                if (target.isPinned && !target.isUnloaded) {
                    add(
                        SheetActionItem(
                            label = "Close tab",
                            icon = Icons.Rounded.Close,
                            onClick = { itemCallbacks.onClosePinnedTab(target.id) }
                        )
                    )
                    if (!target.pinnedUrl.isNullOrBlank() && !target.url.equals(target.pinnedUrl, ignoreCase = true)) {
                        add(
                            SheetActionItem(
                                label = "Reset to pinned URL",
                                icon = Icons.Rounded.Refresh,
                                onClick = { itemCallbacks.onResetPinnedTab(target.id) }
                            )
                        )
                    }
                }
                add(
                    SheetActionItem(
                        label = if (target.isPinned) "Unpin tab" else "Pin tab",
                        icon = Icons.Rounded.PushPin,
                        onClick = { itemCallbacks.onSetTabPinned(target.id, !target.isPinned) }
                    )
                )
                if (!target.isStartPage) {
                    add(
                        SheetActionItem(
                            label = "Add to Essentials",
                            icon = Icons.Rounded.StarOutline,
                            onClick = { itemCallbacks.onAddTabToEssentials(target.id) }
                        )
                    )
                }
                add(
                    SheetActionItem(
                        label = if (target.isPinned) "Edit pinned URL & label" else "Edit tab URL & label",
                        icon = Icons.Rounded.Edit,
                        onClick = { tabToEdit = target }
                    )
                )
                add(
                    SheetActionItem(
                        label = "Set custom icon",
                        icon = Icons.Rounded.Palette,
                        onClick = { tabToPickIcon = target }
                    )
                )
                if (target.isPinned) {
                    add(
                        SheetActionItem(
                            label = "Move to folder",
                            icon = Icons.Rounded.FolderOpen,
                            onClick = { tabToMoveToFolder = target }
                        )
                    )
                }
                if (state.spaces.size > 1) {
                    add(
                        SheetActionItem(
                            label = "Move to Space",
                            icon = Icons.Rounded.DriveFileMove,
                            onClick = { tabToMoveToSpace = target }
                        )
                    )
                }
            }
            if (isEffectiveExpanded) {
                if (target.isPinned && !isPrivate) {
                    val folderId = target.folderId
                    if (folderId != null) {
                        val folderTabCount = state.visiblePinnedTabs.count { it.folderId == folderId }
                        add(
                            SheetActionItem(
                                label = "Reorder tabs in folder",
                                icon = Icons.Rounded.SwapVert,
                                enabled = folderTabCount > 1,
                                onClick = {
                                    reorderingFolderId = folderId
                                    reorderMode = SidebarReorderMode.FOLDER_TABS
                                }
                            )
                        )
                    }
                    val topLevelCount = WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(
                        state.pinnedFolders,
                        state.tabs,
                        target.spaceId
                    ).size
                    add(
                        SheetActionItem(
                            label = "Reorder pinned",
                            icon = Icons.Rounded.SwapVert,
                            enabled = topLevelCount > 1,
                            onClick = { reorderMode = SidebarReorderMode.PINNED_TABS }
                        )
                    )
                } else {
                    add(
                        SheetActionItem(
                            label = "Reorder tabs",
                            icon = Icons.Rounded.SwapVert,
                            enabled = state.visibleRegularTabs.size > 1,
                            onClick = { reorderMode = SidebarReorderMode.OPEN_TABS }
                        )
                    )
                }
            }
            add(
                SheetActionItem(
                    label = if (target.isPinned) "Delete pin" else "Close tab",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = {
                        if (target.isPinned) {
                            itemCallbacks.onDeletePinnedTab(target.id)
                        } else {
                            onCloseTab(target.id)
                        }
                    }
                )
            )
        }
        val tabSubtitle = buildString {
            append(target.displayHost.ifBlank { "Start Page" })
            if (target.isPinned && !isPrivate) {
                append(if (target.isUnloaded) " · Unloaded" else " · Open")
            }
        }
        ItemActionBottomSheet(
            title = target.displayTitle,
            subtitle = tabSubtitle,
            leadingIcon = {
                if (target.isStartPage && target.customIcon == null) {
                    val brandAppearance = LocalSwiftfoxBrandAppearance.current
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SwiftfoxEmblem(
                                size = 22.dp,
                                variant = if (isPrivate) {
                                    SwiftfoxEmblemVariant.PURPLE
                                } else {
                                    brandAppearance.effectiveEmblemVariant
                                },
                                isLightBackground = !isPrivate && brandAppearance.isLightChrome
                            )
                        }
                    }
                } else {
                    SiteIconBadge(
                        url = target.url,
                        fallbackLabel = target.displayHost.ifBlank { target.displayTitle },
                        customIcon = if (isPrivate) null else target.customIcon,
                        privateTabId = if (isPrivate) target.id else null,
                        size = 40.dp,
                        iconSize = 24.dp,
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        fillImageToContainer = true
                    )
                }
            },
            actions = tabActions,
            onDismiss = { actionTab = null }
        )
    }

    tabToEdit?.let { target ->
        EditTitleAndUrlDialog(
            dialogTitle = if (target.isPinned) "Edit Pinned Tab" else "Edit Tab",
            initialTitle = target.displayTitle,
            initialUrl = if (target.isStartPage) "" else target.url,
            onDismiss = { tabToEdit = null },
            onConfirm = { title, url ->
                itemCallbacks.onEditTabTitleAndUrl(target.id, title, url)
                tabToEdit = null
            }
        )
    }

    tabToPickIcon?.let { target ->
        CustomIconPickerSheet(
            title = "Icon for ${target.displayTitle}",
            currentIconSpec = target.customIcon,
            allowResetToFavicon = true,
            previewUrl = target.url,
            previewLabel = target.displayHost.ifBlank { target.displayTitle },
            onSelectIcon = { spec ->
                itemCallbacks.onSetTabCustomIcon(target.id, spec)
            },
            onDismiss = { tabToPickIcon = null }
        )
    }

    actionZenPinned?.let { target ->
        val boundTab = state.tabForZenPinned(target.id)
        val zenActions = buildList {
            if (boundTab != null) {
                add(
                    SheetActionItem(
                        label = "Close tab",
                        icon = Icons.Rounded.Close,
                        onClick = { itemCallbacks.onCloseZenPinnedTab(target.id) }
                    )
                )
                if (!boundTab.url.equals(target.url, ignoreCase = true)) {
                    add(
                        SheetActionItem(
                            label = "Reset to pinned URL",
                            icon = Icons.Rounded.Refresh,
                            onClick = { itemCallbacks.onResetZenPinnedTab(target.id) }
                        )
                    )
                }
            }
            add(
                SheetActionItem(
                    label = "Edit title & URL",
                    icon = Icons.Rounded.Edit,
                    onClick = { zenPinnedToEdit = target }
                )
            )
            add(
                SheetActionItem(
                    label = "Set custom icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { zenPinnedToPickIcon = target }
                )
            )
            add(
                SheetActionItem(
                    label = "Add to Essentials",
                    icon = Icons.Rounded.StarOutline,
                    onClick = { itemCallbacks.onAddZenPinnedToEssentials(target.id) }
                )
            )
            if (state.spaces.size > 1) {
                add(
                    SheetActionItem(
                        label = "Move to Space",
                        icon = Icons.Rounded.DriveFileMove,
                        onClick = { zenPinnedToMoveToSpace = target }
                    )
                )
            }
            add(
                SheetActionItem(
                    label = "Reorder synced pinned",
                    icon = Icons.Rounded.SwapVert,
                    enabled = state.activeSpaceZenPinned.size > 1,
                    onClick = { reorderMode = SidebarReorderMode.ZEN_PINNED }
                )
            )
            add(
                SheetActionItem(
                    label = "Delete",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { itemCallbacks.onDeleteZenPinned(target.id) }
                )
            )
        }
        val hostLabel = OmniboxEngine.extractHost(target.url).ifBlank { target.url }
        val zenSubtitle = if (boundTab != null) "$hostLabel · Open" else hostLabel
        ItemActionBottomSheet(
            title = target.title,
            subtitle = zenSubtitle,
            leadingIcon = {
                SiteIconBadge(
                    url = target.url,
                    fallbackLabel = target.title,
                    customIcon = target.customIcon,
                    size = 40.dp,
                    iconSize = 24.dp,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    fillImageToContainer = true
                )
            },
            actions = zenActions,
            onDismiss = { actionZenPinned = null }
        )
    }

    zenPinnedToEdit?.let { target ->
        EditTitleAndUrlDialog(
            dialogTitle = "Edit Pinned Item",
            initialTitle = target.title,
            initialUrl = target.url,
            onDismiss = { zenPinnedToEdit = null },
            onConfirm = { title, url ->
                itemCallbacks.onUpdateZenPinned(target.id, title, url)
                zenPinnedToEdit = null
            }
        )
    }

    zenPinnedToPickIcon?.let { target ->
        CustomIconPickerSheet(
            title = "Icon for ${target.title}",
            currentIconSpec = target.customIcon,
            allowResetToFavicon = true,
            previewUrl = target.url,
            previewLabel = target.title,
            onSelectIcon = { spec ->
                itemCallbacks.onSetZenPinnedCustomIcon(target.id, spec)
            },
            onDismiss = { zenPinnedToPickIcon = null }
        )
    }

    if (showCreateFolderDialog) {
        PinnedFolderNameDialog(
            dialogTitle = "New Pinned Folder",
            confirmLabel = "Create",
            onDismiss = {
                showCreateFolderDialog = false
                createFolderInitialTabId = null
            },
            onConfirm = { name ->
                val initialId = createFolderInitialTabId
                showCreateFolderDialog = false
                createFolderInitialTabId = null
                itemCallbacks.onCreatePinnedFolder(name, initialId)
            }
        )
    }

    actionFolder?.let { target ->
        val folderTabs = state.tabs.filter {
            !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab &&
                it.spaceId == target.spaceId && it.folderId == target.id
        }
        val topLevelCount = WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(
            state.pinnedFolders,
            state.tabs,
            target.spaceId
        ).size
        ItemActionBottomSheet(
            title = target.name,
            subtitle = "Pinned folder · ${folderTabs.size} ${if (folderTabs.size == 1) "tab" else "tabs"}",
            leadingIcon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val iconSpec = target.customIcon
                        if (iconSpec != null) {
                            SpaceIconGlyph(
                                iconKey = iconSpec,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 22.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            actions = buildList {
                add(
                    SheetActionItem(
                        label = "Rename folder",
                        icon = Icons.Rounded.Edit,
                        onClick = { folderToRename = target }
                    )
                )
                add(
                    SheetActionItem(
                        label = "Set custom icon",
                        icon = Icons.Rounded.Palette,
                        onClick = { folderToPickIcon = target }
                    )
                )
                if (isEffectiveExpanded) {
                    add(
                        SheetActionItem(
                            label = "Reorder tabs in folder",
                            icon = Icons.Rounded.SwapVert,
                            enabled = folderTabs.size > 1,
                            onClick = {
                                if (!target.isExpanded) {
                                    itemCallbacks.onTogglePinnedFolderExpanded(target.id)
                                }
                                reorderingFolderId = target.id
                                reorderMode = SidebarReorderMode.FOLDER_TABS
                            }
                        )
                    )
                    add(
                        SheetActionItem(
                            label = "Reorder pinned",
                            icon = Icons.Rounded.SwapVert,
                            enabled = topLevelCount > 1,
                            onClick = { reorderMode = SidebarReorderMode.PINNED_TABS }
                        )
                    )
                }
                add(
                    SheetActionItem(
                        label = "Delete folder",
                        icon = Icons.Rounded.DeleteOutline,
                        isDestructive = true,
                        onClick = { folderToDelete = target }
                    )
                )
            },
            onDismiss = { actionFolder = null }
        )
    }

    folderToRename?.let { target ->
        PinnedFolderNameDialog(
            dialogTitle = "Rename Folder",
            initialName = target.name,
            confirmLabel = "Save",
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                itemCallbacks.onRenamePinnedFolder(target.id, newName)
                folderToRename = null
            }
        )
    }

    folderToPickIcon?.let { target ->
        CustomIconPickerSheet(
            title = "Icon for ${target.name}",
            currentIconSpec = target.customIcon ?: "icon:folder",
            allowResetToFavicon = false,
            onSelectIcon = { spec ->
                itemCallbacks.onSetPinnedFolderCustomIcon(target.id, spec)
            },
            onDismiss = { folderToPickIcon = null }
        )
    }

    folderToDelete?.let { target ->
        val containedCount = state.tabs.count {
            !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab &&
                it.spaceId == target.spaceId && it.folderId == target.id
        }
        DeletePinnedFolderDialog(
            folder = target,
            containedTabCount = containedCount,
            onDismiss = { folderToDelete = null },
            onKeepPinnedTabs = {
                folderToDelete = null
                itemCallbacks.onDeletePinnedFolder(target.id, false)
            },
            onDeleteFolderAndPins = {
                folderToDelete = null
                itemCallbacks.onDeletePinnedFolder(target.id, true)
            }
        )
    }

    tabToMoveToFolder?.let { target ->
        val spaceFolders = remember(state.pinnedFolders, target.spaceId) {
            state.pinnedFolders
                .filter { it.spaceId == target.spaceId }
                .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
        }
        val tabCounts = remember(state.tabs, target.spaceId) {
            state.tabs
                .filter { !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab && it.spaceId == target.spaceId && it.folderId != null }
                .groupingBy { it.folderId!! }
                .eachCount()
        }
        MoveToFolderBottomSheet(
            tabTitle = target.displayTitle,
            currentFolderId = target.folderId,
            foldersInSpace = spaceFolders,
            tabCountByFolder = tabCounts,
            onMoveToFolder = { folderId -> itemCallbacks.onMovePinnedTabToFolder(target.id, folderId) },
            onRequestNewFolder = {
                createFolderInitialTabId = target.id
                showCreateFolderDialog = true
            },
            onDismiss = { tabToMoveToFolder = null }
        )
    }

    tabToMoveToSpace?.let { target ->
        MoveToSpaceBottomSheet(
            tabTitle = target.displayTitle,
            currentSpaceId = target.spaceId,
            spaces = state.spaces,
            onSelectSpace = { destSpaceId -> itemCallbacks.onMoveTabToSpace(target.id, destSpaceId) },
            onDismiss = { tabToMoveToSpace = null }
        )
    }

    zenPinnedToMoveToSpace?.let { target ->
        MoveToSpaceBottomSheet(
            tabTitle = target.title,
            currentSpaceId = target.spaceId,
            spaces = state.spaces,
            onSelectSpace = { destSpaceId -> itemCallbacks.onMoveZenPinnedToSpace(target.id, destSpaceId) },
            onDismiss = { zenPinnedToMoveToSpace = null }
        )
    }

    railQuickPickLocalFolderId?.let { folderId ->
        val folder = state.pinnedFolders.firstOrNull { it.id == folderId }
        if (folder == null) {
            LaunchedEffect(folderId) { railQuickPickLocalFolderId = null }
        } else {
            val childTabs = remember(state.tabs, folder.id, folder.spaceId) {
                state.tabs
                    .filter {
                        !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab &&
                            it.spaceId == folder.spaceId && it.folderId == folder.id
                    }
                    .sortedWith(compareBy<BrowserTabUi> { it.sortOrder }.thenBy { it.id })
            }
            val quickItems = remember(childTabs, state.activeTabId) {
                childTabs.map { tab ->
                    RailFolderQuickPickItem(
                        id = tab.id,
                        title = tab.displayTitle,
                        url = tab.url,
                        subtitle = tab.displayHost.ifBlank { "Start Page" },
                        customIcon = tab.customIcon,
                        isLoaded = !tab.isUnloaded,
                        isActive = !tab.isUnloaded && tab.id == state.activeTabId,
                        isDesktopMode = tab.resolvedSiteMode == SiteMode.DESKTOP,
                        isPlayingMedia = tab.isPlayingMedia
                    )
                }
            }
            CollapsedRailFolderQuickPickSheet(
                folderName = folder.name,
                folderCustomIcon = folder.customIcon,
                isSyncedZenFolder = false,
                items = quickItems,
                onSelectItem = { tabId -> itemCallbacks.onActivatePinnedTab(tabId) },
                onLongPressItem = { tabId ->
                    actionTab = childTabs.firstOrNull { it.id == tabId }
                },
                onCloseLoadedItem = { tabId -> itemCallbacks.onClosePinnedTab(tabId) },
                onManageFolder = { actionFolder = folder },
                onDismiss = { railQuickPickLocalFolderId = null }
            )
        }
    }

    railQuickPickZenFolder?.let { (spaceId, folderName) ->
        val zenItems = remember(state.zenPinnedItems, spaceId, folderName) {
            state.zenPinnedItems.filter {
                it.spaceId == spaceId && it.folderName?.trim() == folderName
            }
        }
        val quickItems = remember(zenItems, state.tabs, state.activeTabId) {
            zenItems.map { item ->
                val boundTab = state.tabForZenPinned(item.id)
                RailFolderQuickPickItem(
                    id = item.id,
                    title = item.title,
                    url = item.url,
                    subtitle = boundTab?.displayHost?.takeIf { it.isNotBlank() }
                        ?: OmniboxEngine.extractHost(item.url),
                    customIcon = item.customIcon,
                    isLoaded = boundTab != null,
                    isActive = boundTab != null && boundTab.id == state.activeTabId,
                    isDesktopMode = boundTab?.resolvedSiteMode == SiteMode.DESKTOP,
                    isPlayingMedia = boundTab?.isPlayingMedia == true
                )
            }
        }
        CollapsedRailFolderQuickPickSheet(
            folderName = folderName,
            folderCustomIcon = null,
            isSyncedZenFolder = true,
            items = quickItems,
            onSelectItem = { zenId -> itemCallbacks.onActivateZenPinned(zenId) },
            onLongPressItem = { zenId ->
                actionZenPinned = zenItems.firstOrNull { it.id == zenId }
            },
            onCloseLoadedItem = { zenId -> itemCallbacks.onCloseZenPinnedTab(zenId) },
            onManageFolder = null,
            onDismiss = { railQuickPickZenFolder = null }
        )
    }
}

@Composable
private fun ColumnScope.ArcExpandedSidebarContent(
    state: BrowserUiState,
    isOverlayDrawer: Boolean,
    reorderMode: SidebarReorderMode,
    reorderingFolderId: String?,
    onExitReorderMode: () -> Unit,
    onToggleExpanded: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onRequestCreateSpace: () -> Unit,
    onRequestCreateFolder: () -> Unit,
    onRequestCloseAll: () -> Unit,
    onLongPressSpace: (SpaceEntity) -> Unit,
    onLongPressEssential: (EssentialEntity) -> Unit,
    onLongPressFolder: (PinnedFolderEntity) -> Unit,
    onLongPressTab: (BrowserTabUi) -> Unit,
    onLongPressZenPinned: (ZenPinnedItemEntity) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks
) {
    val foxColors = LocalSwiftfoxColors.current
    val brandAppearance = LocalSwiftfoxBrandAppearance.current
    val emblemVariant = if (state.isPrivateView) {
        SwiftfoxEmblemVariant.PURPLE
    } else {
        brandAppearance.effectiveEmblemVariant
    }
    val activeSpace = state.activeSpace

    // Drag-and-drop reorder controllers per section (active only in Reorder Mode)
    val essentials = remember(
        state.essentials,
        state.spaces,
        state.activeSpaceId,
        state.isPrivateView,
        state.settings.syncZenSeparateEssentials
    ) {
        state.activeSpaceEssentials
    }
    val localPinnedEntries = remember(
        state.pinnedFolders,
        state.tabs,
        state.activeSpaceId,
        state.isPrivateView
    ) {
        if (state.isPrivateView) emptyList()
        else WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(
            folders = state.pinnedFolders,
            pinnedTabs = state.tabs,
            spaceId = state.activeSpaceId
        )
    }
    val activeReorderingFolderTabs = remember(localPinnedEntries, reorderingFolderId) {
        if (reorderingFolderId == null) emptyList()
        else {
            (localPinnedEntries.firstOrNull {
                it is SidebarPinnedEntry.FolderGroup && it.folder.id == reorderingFolderId
            } as? SidebarPinnedEntry.FolderGroup)?.tabs.orEmpty()
        }
    }
    val zenPinned = remember(state.zenPinnedItems, state.activeSpaceId, state.isPrivateView) {
        state.activeSpaceZenPinned
    }
    val zenPinnedEntries = remember(zenPinned, state.activeSpaceId, state.isPrivateView) {
        if (state.isPrivateView) emptyList()
        else WorkspacePinnedFolderLogic.buildZenPinnedDisplayList(zenPinned, state.activeSpaceId)
    }
    val regularTabs = remember(state.tabs, state.activeSpaceId, state.isPrivateView) {
        state.visibleRegularTabs
    }
    val tabByEssentialId = remember(state.tabs) {
        buildMap {
            for (tab in state.tabs) {
                val essId = tab.essentialId
                if (!tab.isPrivate && essId != null && !containsKey(essId)) {
                    put(essId, tab)
                }
            }
        }
    }
    val tabByZenPinnedId = remember(state.tabs) {
        buildMap {
            for (tab in state.tabs) {
                val zenId = tab.zenPinnedId
                if (!tab.isPrivate && zenId != null && !containsKey(zenId)) {
                    put(zenId, tab)
                }
            }
        }
    }

    val spacesReorder = rememberDragReorderController(
        sourceItems = state.spaces,
        keyOf = { it.id },
        axis = DragAxis.HORIZONTAL,
        onCommitOrder = itemCallbacks.onReorderSpaces,
        enabled = reorderMode == SidebarReorderMode.SPACES
    )
    val essentialsReorder = rememberDragReorderController(
        sourceItems = essentials,
        keyOf = { it.id },
        axis = DragAxis.BOTH,
        onCommitOrder = itemCallbacks.onReorderEssentials,
        enabled = reorderMode == SidebarReorderMode.ESSENTIALS
    )
    val topLevelPinnedReorder = rememberDragReorderController(
        sourceItems = localPinnedEntries,
        keyOf = { it.entryKey },
        axis = DragAxis.VERTICAL,
        onCommitOrder = itemCallbacks.onReorderTopLevelPinned,
        enabled = reorderMode == SidebarReorderMode.PINNED_TABS
    )
    val folderTabsReorder = rememberDragReorderController(
        sourceItems = activeReorderingFolderTabs,
        keyOf = { it.id },
        axis = DragAxis.VERTICAL,
        onCommitOrder = { ids ->
            reorderingFolderId?.let { folderId ->
                itemCallbacks.onReorderFolderPinnedTabs(folderId, ids)
            }
        },
        enabled = reorderMode == SidebarReorderMode.FOLDER_TABS && reorderingFolderId != null
    )
    val zenPinnedReorder = rememberDragReorderController(
        sourceItems = zenPinned,
        keyOf = { it.id },
        axis = DragAxis.VERTICAL,
        onCommitOrder = itemCallbacks.onReorderZenPinned,
        enabled = reorderMode == SidebarReorderMode.ZEN_PINNED
    )
    val openTabsReorder = rememberDragReorderController(
        sourceItems = regularTabs,
        keyOf = { it.id },
        axis = DragAxis.VERTICAL,
        onCommitOrder = { ids -> itemCallbacks.onReorderTabs(ids, false) },
        enabled = reorderMode == SidebarReorderMode.OPEN_TABS
    )

    // 1. Header: emblem + active Space title + collapse/close button
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!state.isPrivateView && brandAppearance.syncInApp) {
            SwiftfoxAppIcon(
                size = 26.dp,
                variant = brandAppearance.effectiveAppIconVariant,
                isLightBackground = brandAppearance.effectiveAppIconLightBackground
            )
        } else {
            SwiftfoxEmblem(
                size = 24.dp,
                variant = emblemVariant,
                isLightBackground = !state.isPrivateView && brandAppearance.isLightChrome
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (state.isPrivateView) "Private" else (activeSpace?.name ?: "Spaces"),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isPrivateView) foxColors.privateAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        IconButton(
            onClick = if (isOverlayDrawer) onCloseDrawer else onToggleExpanded,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isOverlayDrawer) Icons.Rounded.Close else Icons.Rounded.ChevronLeft,
                contentDescription = if (isOverlayDrawer) "Close Spaces sidebar" else "Collapse Spaces sidebar"
            )
        }
    }

    // Active reorder mode banner for list/grid sections
    val isVerticalListReordering = reorderMode == SidebarReorderMode.ESSENTIALS ||
        reorderMode == SidebarReorderMode.PINNED_TABS ||
        reorderMode == SidebarReorderMode.FOLDER_TABS ||
        reorderMode == SidebarReorderMode.ZEN_PINNED ||
        reorderMode == SidebarReorderMode.OPEN_TABS
    if (isVerticalListReordering) {
        val bannerText = when (reorderMode) {
            SidebarReorderMode.ESSENTIALS -> "Drag essentials to reorder"
            SidebarReorderMode.PINNED_TABS -> "Drag folders & pinned tabs"
            SidebarReorderMode.FOLDER_TABS -> "Drag folder tabs to reorder"
            SidebarReorderMode.ZEN_PINNED -> "Drag synced pinned"
            SidebarReorderMode.OPEN_TABS -> "Drag tabs to reorder"
            SidebarReorderMode.SPACES, SidebarReorderMode.NONE -> "Drag to reorder"
        }
        ReorderModeBanner(
            visible = true,
            label = bannerText,
            onDone = onExitReorderMode
        )
    }

    // 2. Scrollable Essentials icon grid + Pinned Tabs section + Open Tabs + New Tab row
    val displayedEssentials = if (reorderMode == SidebarReorderMode.ESSENTIALS) essentialsReorder.items.toList() else essentials
    val displayedTopLevelPinned = if (reorderMode == SidebarReorderMode.PINNED_TABS) topLevelPinnedReorder.items.toList() else localPinnedEntries
    val displayedZenPinned = if (reorderMode == SidebarReorderMode.ZEN_PINNED) zenPinnedReorder.items.toList() else zenPinned
    val displayedRegularTabs = if (reorderMode == SidebarReorderMode.OPEN_TABS) openTabsReorder.items.toList() else regularTabs

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        userScrollEnabled = !isVerticalListReordering,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!state.isPrivateView && displayedEssentials.isNotEmpty()) {
            item(key = "sidebar_essentials_grid", contentType = "sidebar_essentials_grid") {
                val isReorderingEss = reorderMode == SidebarReorderMode.ESSENTIALS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dragReorderContainer(
                            enabled = isReorderingEss,
                            controller = essentialsReorder
                        )
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayedEssentials.chunked(4).forEach { rowItems ->
                        val rowHasDragging = isReorderingEss &&
                            rowItems.any { it.id == essentialsReorder.draggingKey }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (rowHasDragging) 12f else 0f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { ess ->
                                val boundTab = tabByEssentialId[ess.id]
                                val isEssLoaded = boundTab != null
                                val isEssActive = boundTab != null && boundTab.id == state.activeTabId
                                SidebarEssentialTile(
                                    essential = ess,
                                    isLoaded = isEssLoaded,
                                    isActive = isEssActive,
                                    isPlayingMedia = boundTab?.isPlayingMedia == true,
                                    isReorderMode = isReorderingEss,
                                    onClick = {
                                        if (!isReorderingEss) itemCallbacks.onActivateEssential(ess.id)
                                    },
                                    onLongClick = {
                                        if (!isReorderingEss) onLongPressEssential(ess)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .dragReorderItem(
                                            key = ess.id,
                                            enabled = isReorderingEss,
                                            controller = essentialsReorder,
                                            handlePointerInput = false
                                        )
                                )
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Arc/Zen-style Pinned Tabs section (local folders + local pinned tabs + Zen synced pinned items)
        if (!state.isPrivateView && (displayedTopLevelPinned.isNotEmpty() || displayedZenPinned.isNotEmpty())) {
            item(key = "pinned_section_header", contentType = "sidebar_section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pinned",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isVerticalListReordering) {
                        IconButton(
                            onClick = onRequestCreateFolder,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = "New pinned folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            displayedTopLevelPinned.forEach { entry ->
                when (entry) {
                    is SidebarPinnedEntry.FolderGroup -> {
                        val folder = entry.folder
                        val isReorderingTopLevel = reorderMode == SidebarReorderMode.PINNED_TABS
                        val isReorderingThisFolderTabs = reorderMode == SidebarReorderMode.FOLDER_TABS &&
                            reorderingFolderId == folder.id
                        val hasActiveChild = entry.tabs.any { !it.isUnloaded && it.id == state.activeTabId }
                        val hasLoadedChild = entry.tabs.any { !it.isUnloaded }
                        val isExpandedInUi = (folder.isExpanded || isReorderingThisFolderTabs) && !isReorderingTopLevel

                        item(
                            key = entry.entryKey,
                            contentType = "sidebar_pinned_folder_row"
                        ) {
                            SidebarCollapsibleFolderRow(
                                name = folder.name,
                                itemCount = entry.tabs.size,
                                customIcon = folder.customIcon,
                                isExpanded = isExpandedInUi,
                                isSyncedZenFolder = false,
                                hasActiveChild = hasActiveChild,
                                hasLoadedChild = hasLoadedChild,
                                isReorderMode = isReorderingTopLevel,
                                onToggleExpand = {
                                    if (!isReorderingTopLevel && !isReorderingThisFolderTabs) {
                                        itemCallbacks.onTogglePinnedFolderExpanded(folder.id)
                                    }
                                },
                                onLongPress = {
                                    if (!isVerticalListReordering) {
                                        onLongPressFolder(folder)
                                    }
                                },
                                modifier = Modifier
                                    .then(if (!isReorderingTopLevel) Modifier.animateItem() else Modifier)
                                    .dragReorderItem(
                                        key = entry.entryKey,
                                        enabled = isReorderingTopLevel,
                                        controller = topLevelPinnedReorder
                                    )
                            )
                        }

                        if (isExpandedInUi) {
                            val childTabs = if (isReorderingThisFolderTabs) {
                                folderTabsReorder.items.toList()
                            } else {
                                entry.tabs
                            }
                            if (childTabs.isEmpty()) {
                                item(
                                    key = "empty_folder_${folder.id}",
                                    contentType = "sidebar_empty_folder_hint"
                                ) {
                                    Text(
                                        text = "Empty folder · Long-press a pin to add",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 4.dp)
                                    )
                                }
                            } else {
                                items(
                                    items = childTabs,
                                    key = { "pinned_${it.id}" },
                                    contentType = { "sidebar_pinned_tab_row" }
                                ) { tab ->
                                    SidebarTabRow(
                                        tab = tab,
                                        isActive = !tab.isUnloaded && tab.id == state.activeTabId,
                                        isPinnedStyle = true,
                                        isLoaded = !tab.isUnloaded,
                                        isReorderMode = isReorderingThisFolderTabs,
                                        onSelect = {
                                            if (!isReorderingThisFolderTabs) itemCallbacks.onActivatePinnedTab(tab.id)
                                        },
                                        onLongPress = {
                                            if (!isVerticalListReordering) onLongPressTab(tab)
                                        },
                                        onClose = { itemCallbacks.onClosePinnedTab(tab.id) },
                                        modifier = Modifier
                                            .then(if (!isReorderingThisFolderTabs) Modifier.animateItem() else Modifier)
                                            .padding(start = 16.dp)
                                            .dragReorderItem(
                                                key = tab.id,
                                                enabled = isReorderingThisFolderTabs,
                                                controller = folderTabsReorder
                                            )
                                    )
                                }
                            }
                        }
                    }

                    is SidebarPinnedEntry.StandalonePin -> {
                        val tab = entry.tab
                        val isReorderingPinned = reorderMode == SidebarReorderMode.PINNED_TABS
                        item(
                            key = "pinned_${tab.id}",
                            contentType = "sidebar_pinned_tab_row"
                        ) {
                            SidebarTabRow(
                                tab = tab,
                                isActive = !tab.isUnloaded && tab.id == state.activeTabId,
                                isPinnedStyle = true,
                                isLoaded = !tab.isUnloaded,
                                isReorderMode = isReorderingPinned,
                                onSelect = {
                                    if (!isReorderingPinned && reorderMode != SidebarReorderMode.FOLDER_TABS) {
                                        itemCallbacks.onActivatePinnedTab(tab.id)
                                    }
                                },
                                onLongPress = {
                                    if (!isVerticalListReordering) onLongPressTab(tab)
                                },
                                onClose = { itemCallbacks.onClosePinnedTab(tab.id) },
                                modifier = Modifier
                                    .then(if (!isReorderingPinned) Modifier.animateItem() else Modifier)
                                    .dragReorderItem(
                                        key = entry.entryKey,
                                        enabled = isReorderingPinned,
                                        controller = topLevelPinnedReorder
                                    )
                            )
                        }
                    }
                }
            }

            if (reorderMode == SidebarReorderMode.ZEN_PINNED) {
                items(
                    items = displayedZenPinned,
                    key = { "zen_${it.id}" },
                    contentType = { "sidebar_zen_pinned_row" }
                ) { item ->
                    val boundTab = tabByZenPinnedId[item.id]
                    SidebarZenPinnedRow(
                        item = item,
                        boundTab = boundTab,
                        isActive = boundTab != null && boundTab.id == state.activeTabId,
                        isReorderMode = true,
                        onClick = {},
                        onLongPress = {},
                        modifier = Modifier.dragReorderItem(
                            key = item.id,
                            enabled = true,
                            controller = zenPinnedReorder
                        )
                    )
                }
            } else {
                zenPinnedEntries.forEach { zenEntry ->
                    when (zenEntry) {
                        is SidebarZenPinnedEntry.FolderGroup -> {
                            val isExpanded = state.isZenFolderExpanded(state.activeSpaceId, zenEntry.folderName)
                            val hasActive = zenEntry.items.any { item ->
                                val bound = tabByZenPinnedId[item.id]
                                bound != null && bound.id == state.activeTabId
                            }
                            val hasLoaded = zenEntry.items.any { item -> tabByZenPinnedId[item.id] != null }
                            item(
                                key = zenEntry.entryKey,
                                contentType = "sidebar_zen_folder_row"
                            ) {
                                SidebarCollapsibleFolderRow(
                                    name = zenEntry.folderName,
                                    itemCount = zenEntry.items.size,
                                    customIcon = null,
                                    isExpanded = isExpanded,
                                    isSyncedZenFolder = true,
                                    hasActiveChild = hasActive,
                                    hasLoadedChild = hasLoaded,
                                    isReorderMode = false,
                                    onToggleExpand = {
                                        if (!isVerticalListReordering) {
                                            itemCallbacks.onToggleZenFolderExpanded(state.activeSpaceId, zenEntry.folderName)
                                        }
                                    },
                                    onLongPress = null,
                                    modifier = Modifier.animateItem()
                                )
                            }
                            if (isExpanded) {
                                items(
                                    items = zenEntry.items,
                                    key = { "zen_${it.id}" },
                                    contentType = { "sidebar_zen_pinned_row" }
                                ) { item ->
                                    val boundTab = tabByZenPinnedId[item.id]
                                    SidebarZenPinnedRow(
                                        item = item,
                                        boundTab = boundTab,
                                        isActive = boundTab != null && boundTab.id == state.activeTabId,
                                        isReorderMode = false,
                                        showFolderSubtitle = false,
                                        onClick = {
                                            if (!isVerticalListReordering) itemCallbacks.onActivateZenPinned(item.id)
                                        },
                                        onLongPress = {
                                            if (!isVerticalListReordering) onLongPressZenPinned(item)
                                        },
                                        modifier = Modifier
                                            .animateItem()
                                            .padding(start = 16.dp)
                                    )
                                }
                            }
                        }

                        is SidebarZenPinnedEntry.StandaloneItem -> {
                            val item = zenEntry.item
                            val boundTab = tabByZenPinnedId[item.id]
                            item(
                                key = "zen_${item.id}",
                                contentType = "sidebar_zen_pinned_row"
                            ) {
                                SidebarZenPinnedRow(
                                    item = item,
                                    boundTab = boundTab,
                                    isActive = boundTab != null && boundTab.id == state.activeTabId,
                                    isReorderMode = false,
                                    onClick = {
                                        if (!isVerticalListReordering) itemCallbacks.onActivateZenPinned(item.id)
                                    },
                                    onLongPress = {
                                        if (!isVerticalListReordering) onLongPressZenPinned(item)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }

            item(key = "pinned_section_divider", contentType = "sidebar_divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }

        // Open Tabs header with Close all button
        val canCloseAll = !isVerticalListReordering && WorkspaceTabResolver.hasAnythingToCloseAll(state)
        if (displayedRegularTabs.isNotEmpty() || canCloseAll) {
            item(key = "open_tabs_header", contentType = "sidebar_section_header") {
                WorkspaceTabsHeaderRow(
                    title = if (state.isPrivateView) "Private tabs" else "Open tabs",
                    showCloseAll = canCloseAll,
                    isPrivate = state.isPrivateView,
                    onClickCloseAll = onRequestCloseAll
                )
            }
        }

        // Open Tabs list
        items(
            items = displayedRegularTabs,
            key = { it.id },
            contentType = { "sidebar_tab_row" }
        ) { tab ->
            val isReorderingTabs = reorderMode == SidebarReorderMode.OPEN_TABS
            SidebarTabRow(
                tab = tab,
                isActive = tab.id == state.activeTabId,
                isPinnedStyle = false,
                isLoaded = true,
                isReorderMode = isReorderingTabs,
                onSelect = { if (!isReorderingTabs) onSelectTab(tab.id) },
                onLongPress = { if (!isReorderingTabs) onLongPressTab(tab) },
                onClose = { onCloseTab(tab.id) },
                modifier = Modifier
                    .then(if (!isReorderingTabs) Modifier.animateItem() else Modifier)
                    .dragReorderItem(
                        key = tab.id,
                        enabled = isReorderingTabs,
                        controller = openTabsReorder
                    )
            )
        }

        item(key = "sidebar_new_tab_row", contentType = "sidebar_new_tab_row") {
            val newTabIsPrivate = state.isPrivateView
            val newTabContainerColor = if (newTabIsPrivate) {
                foxColors.privateAccent
            } else {
                MaterialTheme.colorScheme.primary
            }
            val newTabContentColor = if (newTabIsPrivate) {
                foxColors.onPrivateAccent
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
            val newTabLabel = if (newTabIsPrivate) "Private tab" else "New tab"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onCreateTab,
                    shape = RoundedCornerShape(20.dp),
                    color = newTabContainerColor,
                    contentColor = newTabContentColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = newTabLabel,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(newTabLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (!newTabIsPrivate) {
                    Surface(
                        onClick = onCreatePrivateTab,
                        shape = RoundedCornerShape(16.dp),
                        color = foxColors.privateContainer,
                        contentColor = foxColors.privateAccent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "New private tab",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 4. Bottom Editorial Bare-Glyph Spaces Switcher & Private Toggle pinned in thumb reach
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f))

    if (reorderMode == SidebarReorderMode.SPACES) {
        ReorderModeBanner(
            visible = true,
            label = "Drag Spaces",
            onDone = onExitReorderMode
        )
    }

    val isReorderingSpaces = reorderMode == SidebarReorderMode.SPACES
    val displayedSpaces = if (isReorderingSpaces) spacesReorder.items.toList() else state.spaces
    val reduceMotion = rememberReduceMotion()
    val footerListState = rememberLazyListState()
    val footerCanScrollBackwardState = remember(footerListState) { derivedStateOf { footerListState.canScrollBackward } }
    val footerCanScrollForwardState = remember(footerListState) { derivedStateOf { footerListState.canScrollForward } }

    LaunchedEffect(state.activeSpaceId, state.isPrivateView, displayedSpaces.size, isReorderingSpaces) {
        if (isReorderingSpaces) return@LaunchedEffect
        val targetIndex = if (state.isPrivateView) {
            displayedSpaces.size + 1 // Spaces + 1 divider item before the Private glyph
        } else {
            displayedSpaces.indexOfFirst { it.id == state.activeSpaceId }
        }
        if (targetIndex >= 0) {
            val visible = footerListState.layoutInfo.visibleItemsInfo.any { item ->
                item.index == targetIndex &&
                    item.offset >= 0 &&
                    item.offset + item.size <= footerListState.layoutInfo.viewportEndOffset
            }
            if (!visible) {
                if (reduceMotion) {
                    footerListState.scrollToItem(targetIndex)
                } else {
                    footerListState.animateScrollToItem(targetIndex)
                }
            }
        }
    }

    LazyRow(
        state = footerListState,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalFadingEdges(
                showStartFadeProvider = { !isReorderingSpaces && footerCanScrollBackwardState.value },
                showEndFadeProvider = { !isReorderingSpaces && footerCanScrollForwardState.value },
                startFadeWidth = 14.dp,
                endFadeWidth = 22.dp
            ),
        userScrollEnabled = !isReorderingSpaces,
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            items = displayedSpaces,
            key = { it.id },
            contentType = { "sidebar_bottom_space_icon" }
        ) { space ->
            val selected = !state.isPrivateView && state.activeSpaceId == space.id
            EditorialSpaceFooterGlyph(
                selected = selected,
                accentColor = MaterialTheme.colorScheme.primary,
                contentDescriptionText = "${space.name} Space",
                onClick = { if (!isReorderingSpaces) onSelectSpace(space.id) },
                onLongClick = { if (!isReorderingSpaces) onLongPressSpace(space) },
                isReorderMode = isReorderingSpaces,
                themeDotColor = parseSpaceColorHexOrNull(space.themeColorHex),
                modifier = Modifier
                    .then(if (!isReorderingSpaces) Modifier.animateItem() else Modifier)
                    .dragReorderItem(
                        key = space.id,
                        enabled = isReorderingSpaces,
                        controller = spacesReorder
                    )
            ) { glyphTint ->
                SpaceIconGlyph(
                    iconKey = space.iconKey,
                    tint = glyphTint,
                    size = 20.dp
                )
            }
        }

        if (!isReorderingSpaces) {
            item(key = "sidebar_bottom_divider", contentType = "sidebar_bottom_divider") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            }

            item(key = "sidebar_bottom_private", contentType = "sidebar_bottom_private") {
                EditorialSpaceFooterGlyph(
                    selected = state.isPrivateView,
                    accentColor = foxColors.privateAccent,
                    contentDescriptionText = "Private Space",
                    onClick = { onTogglePrivate(!state.isPrivateView) }
                ) { glyphTint ->
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = glyphTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item(key = "sidebar_bottom_add_space", contentType = "sidebar_bottom_add_space") {
                EditorialFooterActionGlyph(
                    icon = Icons.Rounded.Add,
                    contentDescription = "New Space",
                    onClick = onRequestCreateSpace
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarEssentialTile(
    essential: EssentialEntity,
    isLoaded: Boolean,
    isActive: Boolean,
    isPlayingMedia: Boolean,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isLoaded -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val border = when {
        isReorderMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isActive -> BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
        isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    }
    val statusDescription = when {
        isActive -> "Active"
        isLoaded -> "Loaded"
        else -> "Unloaded"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!isReorderMode) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = "${essential.title} (${essential.domain}), $statusDescription"
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            SiteIconBadge(
                url = essential.url,
                fallbackLabel = essential.title,
                customIcon = essential.customIcon,
                size = 48.dp,
                iconSize = 24.dp,
                shape = RoundedCornerShape(14.dp),
                tint = if (isActive || isLoaded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fillImageToContainer = true,
                modifier = Modifier.graphicsLayer {
                    alpha = if (isLoaded || isActive || isReorderMode) 1f else 0.66f
                }
            )

            if (isLoaded && !isReorderMode) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.5.dp)
                        .width(if (isActive) 20.dp else 12.dp)
                        .height(3.5.dp)
                ) {}
            }

            if (isPlayingMedia && !isReorderMode) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Playing media",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarCollapsibleFolderRow(
    name: String,
    itemCount: Int,
    customIcon: String?,
    isExpanded: Boolean,
    isSyncedZenFolder: Boolean,
    hasActiveChild: Boolean,
    hasLoadedChild: Boolean,
    isReorderMode: Boolean,
    onToggleExpand: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = if (reduceMotion) snap() else tween(160),
        label = "folder_chevron_rotation"
    )
    val showCollapsedIndicator = !isExpanded && !isReorderMode && (hasActiveChild || hasLoadedChild)
    val containerColor = when {
        hasActiveChild && !isExpanded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        isExpanded -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val border = when {
        isReorderMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        hasActiveChild && !isExpanded -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        hasLoadedChild && !isExpanded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!isReorderMode) {
                    Modifier.combinedClickable(
                        onClick = onToggleExpand,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                val stateWord = if (isExpanded) "Expanded" else "Collapsed"
                contentDescription = "$name folder, $itemCount items, $stateWord"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (customIcon != null) {
                        SpaceIconGlyph(
                            iconKey = customIcon,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 18.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("$itemCount ${if (itemCount == 1) "item" else "items"}")
                        if (isSyncedZenFolder) append(" · Synced")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isReorderMode) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else if (showCollapsedIndicator) {
                Box(
                    modifier = Modifier
                        .size(if (hasActiveChild) 8.dp else 7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarZenPinnedRow(
    item: ZenPinnedItemEntity,
    boundTab: BrowserTabUi?,
    isActive: Boolean,
    isReorderMode: Boolean,
    showFolderSubtitle: Boolean = true,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoaded = boundTab != null
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isLoaded -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isLoaded -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }
    val border = when {
        isReorderMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    }
    val statusLabel = when {
        isActive -> "Active"
        isLoaded -> "Loaded"
        else -> "Unloaded"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!isReorderMode) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = "${item.title}, $statusLabel"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SiteIconBadge(
                url = item.url,
                fallbackLabel = item.title,
                customIcon = item.customIcon,
                size = 28.dp,
                iconSize = 18.dp,
                shape = RoundedCornerShape(8.dp),
                containerColor = if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                fillImageToContainer = true,
                modifier = Modifier.graphicsLayer {
                    alpha = if (isLoaded || isActive || isReorderMode) 1f else 0.68f
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    boundTab != null && boundTab.displayHost.isNotBlank() -> boundTab.displayHost
                    showFolderSubtitle && !item.folderName.isNullOrBlank() -> item.folderName
                    else -> OmniboxEngine.extractHost(item.url)
                }
                if (subtitle.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) {
                                contentColor.copy(alpha = 0.78f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isLoaded) 1f else 0.72f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (boundTab?.resolvedSiteMode == SiteMode.DESKTOP) {
                            Icon(
                                imageVector = Icons.Rounded.DesktopWindows,
                                contentDescription = "Desktop mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (boundTab?.isPlayingMedia == true) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Playing media",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLoaded && !isReorderMode) {
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 8.dp else 7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Icon(
                    imageVector = when {
                        isReorderMode -> Icons.Rounded.DragIndicator
                        item.isPinned -> Icons.Rounded.PushPin
                        else -> Icons.Rounded.Tab
                    },
                    contentDescription = if (isReorderMode) "Drag to reorder" else "Pinned item",
                    tint = when {
                        isReorderMode || isActive -> MaterialTheme.colorScheme.primary
                        isLoaded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarTabRow(
    tab: BrowserTabUi,
    isActive: Boolean,
    isPinnedStyle: Boolean,
    isLoaded: Boolean = true,
    isReorderMode: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = tab.isPrivate
    val containerColor = when {
        isActive && isPrivate -> foxColors.privateContainer
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isPinnedStyle && !isLoaded -> MaterialTheme.colorScheme.surfaceContainerLow
        isPinnedStyle -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        isActive && isPrivate -> foxColors.onPrivateContainer
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isPinnedStyle && !isLoaded -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        isReorderMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isActive && isPrivate -> BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.65f))
        isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isPinnedStyle && isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
        isPinnedStyle -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        else -> null
    }
    val badgeBg = when {
        isPrivate -> foxColors.privateContainer
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val badgeTint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (!isReorderMode) {
                    Modifier.combinedClickable(
                        onClick = onSelect,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = if (isPinnedStyle || isReorderMode) 12.dp else 2.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val iconAlpha = if (isLoaded || isActive || isReorderMode || !isPinnedStyle) 1f else 0.68f
            if (tab.isStartPage && tab.customIcon == null) {
                val brandAppearance = LocalSwiftfoxBrandAppearance.current
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .graphicsLayer { alpha = iconAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    SwiftfoxEmblem(
                        size = 16.dp,
                        variant = if (isPrivate) {
                            SwiftfoxEmblemVariant.PURPLE
                        } else {
                            brandAppearance.effectiveEmblemVariant
                        },
                        isLightBackground = !isPrivate && brandAppearance.isLightChrome
                    )
                }
            } else {
                SiteIconBadge(
                    url = tab.url,
                    fallbackLabel = tab.displayHost.ifBlank { tab.displayTitle },
                    customIcon = if (isPrivate) null else tab.customIcon,
                    privateTabId = if (isPrivate) tab.id else null,
                    size = 28.dp,
                    iconSize = 18.dp,
                    shape = RoundedCornerShape(8.dp),
                    containerColor = badgeBg,
                    tint = badgeTint,
                    fillImageToContainer = true,
                    modifier = Modifier.graphicsLayer { alpha = iconAlpha }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tab.displayHost.ifBlank { "Start Page" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            contentColor.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isLoaded || !isPinnedStyle) 1f else 0.72f
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isLoaded && tab.resolvedSiteMode == SiteMode.DESKTOP) {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = "Desktop mode",
                            tint = badgeTint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (isLoaded && tab.isPlayingMedia) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Playing media",
                            tint = badgeTint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            when {
                isReorderMode -> {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isPinnedStyle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLoaded) {
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 8.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (isLoaded) "Loaded pinned tab" else "Unloaded pinned tab",
                            tint = when {
                                isActive -> badgeTint
                                isLoaded -> badgeTint.copy(alpha = 0.78f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                else -> {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close ${tab.displayTitle}",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.ArcCollapsedSidebarRail(
    state: BrowserUiState,
    onToggleExpanded: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onLongPressSpace: (SpaceEntity) -> Unit,
    onLongPressEssential: (EssentialEntity) -> Unit,
    onOpenLocalFolderQuickPick: (String) -> Unit,
    onOpenZenFolderQuickPick: (spaceId: String, folderName: String) -> Unit,
    onLongPressFolder: (PinnedFolderEntity) -> Unit,
    onLongPressTab: (BrowserTabUi) -> Unit,
    onLongPressZenPinned: (ZenPinnedItemEntity) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks
) {
    val foxColors = LocalSwiftfoxColors.current
    val brandAppearance = LocalSwiftfoxBrandAppearance.current
    val emblemVariant = if (state.isPrivateView) {
        SwiftfoxEmblemVariant.PURPLE
    } else {
        brandAppearance.effectiveEmblemVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.isPrivateView && brandAppearance.syncInApp) {
            SwiftfoxAppIcon(
                size = 28.dp,
                variant = brandAppearance.effectiveAppIconVariant,
                isLightBackground = brandAppearance.effectiveAppIconLightBackground
            )
        } else {
            SwiftfoxEmblem(
                size = 26.dp,
                variant = emblemVariant,
                isLightBackground = !state.isPrivateView && brandAppearance.isLightChrome
            )
        }
        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Expand Spaces sidebar"
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
    ) {
        state.spaces.forEach { space ->
            val selected = !state.isPrivateView && state.activeSpaceId == space.id
            EditorialSpaceRailGlyph(
                selected = selected,
                accentColor = MaterialTheme.colorScheme.primary,
                contentDescriptionText = "${space.name} Space",
                onClick = { onSelectSpace(space.id) },
                onLongClick = { onLongPressSpace(space) },
                themeDotColor = parseSpaceColorHexOrNull(space.themeColorHex)
            ) { glyphTint ->
                SpaceIconGlyph(
                    iconKey = space.iconKey,
                    tint = glyphTint,
                    size = 20.dp
                )
            }
        }
        EditorialSpaceRailGlyph(
            selected = state.isPrivateView,
            accentColor = foxColors.privateAccent,
            contentDescriptionText = "Private Space",
            onClick = { onTogglePrivate(!state.isPrivateView) }
        ) { glyphTint ->
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    val railEssentials = remember(
        state.essentials,
        state.spaces,
        state.activeSpaceId,
        state.isPrivateView,
        state.settings.syncZenSeparateEssentials
    ) {
        state.activeSpaceEssentials
    }
    val railZenPinned = remember(state.zenPinnedItems, state.activeSpaceId, state.isPrivateView) {
        state.activeSpaceZenPinned
    }
    val railLocalPinnedEntries = remember(
        state.pinnedFolders,
        state.tabs,
        state.activeSpaceId,
        state.isPrivateView
    ) {
        if (state.isPrivateView) emptyList()
        else WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(
            folders = state.pinnedFolders,
            pinnedTabs = state.tabs,
            spaceId = state.activeSpaceId
        )
    }
    val railZenEntries = remember(railZenPinned, state.activeSpaceId, state.isPrivateView) {
        if (state.isPrivateView) emptyList()
        else WorkspacePinnedFolderLogic.buildZenPinnedDisplayList(railZenPinned, state.activeSpaceId)
    }
    val railOpenOrPrivateTabs = remember(state.tabs, state.activeSpaceId, state.isPrivateView) {
        if (state.isPrivateView) state.visibleTabs else state.visibleRegularTabs
    }
    val railTabByEssentialId = remember(state.tabs) {
        buildMap {
            for (tab in state.tabs) {
                val essId = tab.essentialId
                if (!tab.isPrivate && essId != null && !containsKey(essId)) {
                    put(essId, tab)
                }
            }
        }
    }
    val railTabByZenPinnedId = remember(state.tabs) {
        buildMap {
            for (tab in state.tabs) {
                val zenId = tab.zenPinnedId
                if (!tab.isPrivate && zenId != null && !containsKey(zenId)) {
                    put(zenId, tab)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.isPrivateView && railEssentials.isNotEmpty()) {
            items(
                items = railEssentials,
                key = { "rail_ess_${it.id}" },
                contentType = { "sidebar_ess_collapsed" }
            ) { ess ->
                val boundTab = railTabByEssentialId[ess.id]
                SidebarEssentialTile(
                    essential = ess,
                    isLoaded = boundTab != null,
                    isActive = boundTab != null && boundTab.id == state.activeTabId,
                    isPlayingMedia = boundTab?.isPlayingMedia == true,
                    isReorderMode = false,
                    onClick = { itemCallbacks.onActivateEssential(ess.id) },
                    onLongClick = { onLongPressEssential(ess) },
                    modifier = Modifier
                        .animateItem()
                        .size(48.dp)
                )
            }
        }
        if (!state.isPrivateView && railLocalPinnedEntries.isNotEmpty()) {
            items(
                items = railLocalPinnedEntries,
                key = { "rail_${it.entryKey}" },
                contentType = { entry ->
                    when (entry) {
                        is SidebarPinnedEntry.FolderGroup -> "sidebar_folder_collapsed"
                        is SidebarPinnedEntry.StandalonePin -> "sidebar_tab_collapsed"
                    }
                }
            ) { entry ->
                when (entry) {
                    is SidebarPinnedEntry.FolderGroup -> {
                        val hasActive = entry.tabs.any { !it.isUnloaded && it.id == state.activeTabId }
                        val hasLoaded = entry.tabs.any { !it.isUnloaded }
                        CollapsedRailFolderTile(
                            name = entry.folder.name,
                            customIcon = entry.folder.customIcon,
                            isActive = hasActive,
                            isLoaded = hasLoaded,
                            onClick = { onOpenLocalFolderQuickPick(entry.folder.id) },
                            onLongClick = { onLongPressFolder(entry.folder) },
                            modifier = Modifier.animateItem()
                        )
                    }

                    is SidebarPinnedEntry.StandalonePin -> {
                        val tab = entry.tab
                        CollapsedRailTabTile(
                            tab = tab,
                            isActive = !tab.isUnloaded && tab.id == state.activeTabId,
                            isLoaded = !tab.isUnloaded,
                            onClick = { itemCallbacks.onActivatePinnedTab(tab.id) },
                            onLongClick = { onLongPressTab(tab) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        if (!state.isPrivateView && railZenEntries.isNotEmpty()) {
            items(
                items = railZenEntries,
                key = { "rail_${it.entryKey}" },
                contentType = { entry ->
                    when (entry) {
                        is SidebarZenPinnedEntry.FolderGroup -> "sidebar_zen_folder_collapsed"
                        is SidebarZenPinnedEntry.StandaloneItem -> "sidebar_zen_collapsed"
                    }
                }
            ) { entry ->
                when (entry) {
                    is SidebarZenPinnedEntry.FolderGroup -> {
                        val hasActive = entry.items.any { item ->
                            val bound = railTabByZenPinnedId[item.id]
                            bound != null && bound.id == state.activeTabId
                        }
                        val hasLoaded = entry.items.any { item -> railTabByZenPinnedId[item.id] != null }
                        CollapsedRailFolderTile(
                            name = entry.folderName,
                            customIcon = null,
                            isActive = hasActive,
                            isLoaded = hasLoaded,
                            onClick = { onOpenZenFolderQuickPick(state.activeSpaceId, entry.folderName) },
                            onLongClick = null,
                            modifier = Modifier.animateItem()
                        )
                    }

                    is SidebarZenPinnedEntry.StandaloneItem -> {
                        val item = entry.item
                        val boundTab = railTabByZenPinnedId[item.id]
                        val isLoaded = boundTab != null
                        val isActive = boundTab != null && boundTab.id == state.activeTabId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isActive -> MaterialTheme.colorScheme.primaryContainer
                                isLoaded -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = when {
                                isActive -> BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
                                isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
                                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                            },
                            modifier = Modifier
                                .animateItem()
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { itemCallbacks.onActivateZenPinned(item.id) },
                                    onLongClick = { onLongPressZenPinned(item) }
                                )
                                .semantics {
                                    val status = when {
                                        isActive -> "Active"
                                        isLoaded -> "Loaded"
                                        else -> "Unloaded"
                                    }
                                    contentDescription = "${item.title}, $status"
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SiteIconBadge(
                                    url = item.url,
                                    fallbackLabel = item.title,
                                    customIcon = item.customIcon,
                                    size = 48.dp,
                                    iconSize = 24.dp,
                                    shape = RoundedCornerShape(14.dp),
                                    tint = if (isActive || isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fillImageToContainer = true,
                                    modifier = Modifier.graphicsLayer { alpha = if (isLoaded || isActive) 1f else 0.66f }
                                )
                                if (isLoaded) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 3.5.dp)
                                            .width(if (isActive) 20.dp else 12.dp)
                                            .height(3.5.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
        }
        items(
            items = railOpenOrPrivateTabs,
            key = { it.id },
            contentType = { "sidebar_tab_collapsed" }
        ) { tab ->
            CollapsedRailTabTile(
                tab = tab,
                isActive = !tab.isUnloaded && tab.id == state.activeTabId,
                isLoaded = !tab.isUnloaded,
                onClick = { onSelectTab(tab.id) },
                onLongClick = { onLongPressTab(tab) },
                modifier = Modifier.animateItem()
            )
        }
    }

    val newTabIsPrivate = state.isPrivateView
    val newTabContainerColor = if (newTabIsPrivate) {
        foxColors.privateAccent
    } else {
        MaterialTheme.colorScheme.primary
    }
    val newTabContentColor = if (newTabIsPrivate) {
        foxColors.onPrivateAccent
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val newTabLabel = if (newTabIsPrivate) "Private tab" else "New tab"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!newTabIsPrivate) {
            Surface(
                onClick = onCreatePrivateTab,
                shape = RoundedCornerShape(16.dp),
                color = foxColors.privateContainer,
                contentColor = foxColors.privateAccent,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "New private tab",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Surface(
            onClick = onCreateTab,
            shape = RoundedCornerShape(20.dp),
            color = newTabContainerColor,
            contentColor = newTabContentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = newTabLabel,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsedRailFolderTile(
    name: String,
    customIcon: String?,
    isActive: Boolean,
    isLoaded: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer
            isLoaded -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        border = when {
            isActive -> BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
            isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        },
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription = "$name folder"
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (customIcon != null) {
                SpaceIconGlyph(
                    iconKey = customIcon,
                    tint = if (isActive || isLoaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    size = 22.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (isActive || isLoaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            if (isLoaded) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.5.dp)
                        .width(if (isActive) 20.dp else 12.dp)
                        .height(3.5.dp)
                ) {}
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsedRailTabTile(
    tab: BrowserTabUi,
    isActive: Boolean,
    isLoaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foxColors = LocalSwiftfoxColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isActive && tab.isPrivate -> foxColors.privateContainer
            isActive -> MaterialTheme.colorScheme.primaryContainer
            tab.isPinned && !isLoaded -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = when {
            isActive && tab.isPrivate -> BorderStroke(1.5.dp, foxColors.privateAccent)
            isActive -> BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
            tab.isPinned && isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
            tab.isPinned -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            else -> null
        },
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription = "Switch to ${tab.displayTitle} (${tab.displayHost.ifBlank { "Start Page" }})"
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            val iconAlpha = if (isLoaded || isActive || !tab.isPinned) 1f else 0.66f
            if (tab.isStartPage && tab.customIcon == null) {
                val brandAppearance = LocalSwiftfoxBrandAppearance.current
                SwiftfoxEmblem(
                    size = 22.dp,
                    variant = if (tab.isPrivate) {
                        SwiftfoxEmblemVariant.PURPLE
                    } else {
                        brandAppearance.effectiveEmblemVariant
                    },
                    isLightBackground = !tab.isPrivate && brandAppearance.isLightChrome,
                    modifier = Modifier.graphicsLayer { alpha = iconAlpha }
                )
            } else {
                SiteIconBadge(
                    url = tab.url,
                    fallbackLabel = tab.displayHost.ifBlank { tab.displayTitle },
                    customIcon = if (tab.isPrivate) null else tab.customIcon,
                    privateTabId = if (tab.isPrivate) tab.id else null,
                    size = 48.dp,
                    iconSize = 24.dp,
                    shape = RoundedCornerShape(14.dp),
                    tint = when {
                        isActive && tab.isPrivate -> foxColors.privateAccent
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fillImageToContainer = true,
                    modifier = Modifier.graphicsLayer { alpha = iconAlpha }
                )
            }
            if (tab.isPinned && !tab.isPrivate && isLoaded) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.5.dp)
                        .width(if (isActive) 20.dp else 12.dp)
                        .height(3.5.dp)
                ) {}
            }
        }
    }
}
