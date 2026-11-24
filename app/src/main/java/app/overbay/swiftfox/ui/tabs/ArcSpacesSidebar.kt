package app.overbay.swiftfox.ui.tabs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.EssentialEntity
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
import app.overbay.swiftfox.ui.components.ItemActionBottomSheet
import app.overbay.swiftfox.ui.components.RenameSpaceDialog
import app.overbay.swiftfox.ui.components.ReorderModeBanner
import app.overbay.swiftfox.ui.components.SheetActionItem
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.components.SpaceIconGlyph
import app.overbay.swiftfox.ui.components.dragReorderItem
import app.overbay.swiftfox.ui.components.rememberDragReorderController
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import app.overbay.swiftfox.ui.theme.rememberReduceMotion

private enum class SidebarReorderMode {
    NONE,
    SPACES,
    ESSENTIALS,
    PINNED_TABS,
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
    onOpenEssential: (String) -> Unit,
    onCreateSpace: (String, String, Boolean) -> Unit,
    onDeleteSpace: (String) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks = WorkspaceItemCallbacks(onDeleteSpace = onDeleteSpace),
    modifier: Modifier = Modifier
) {
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(SidebarReorderMode.NONE) }

    // Active action-sheet & dialog targets
    var actionSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToRename by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToPickIcon by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }

    var actionEssential by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToEdit by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToPickIcon by remember { mutableStateOf<EssentialEntity?>(null) }

    var actionTab by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToEdit by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToPickIcon by remember { mutableStateOf<BrowserTabUi?>(null) }

    var actionZenPinned by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToEdit by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToPickIcon by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }

    val reduceMotion = rememberReduceMotion()
    val isEffectiveExpanded = isOverlayDrawer || expanded

    // Exit reorder mode cleanly if the sidebar collapses or switches private/space view
    LaunchedEffect(isEffectiveExpanded, state.isPrivateView, state.activeSpaceId) {
        if (!isEffectiveExpanded && reorderMode != SidebarReorderMode.SPACES && reorderMode != SidebarReorderMode.OPEN_TABS) {
            reorderMode = SidebarReorderMode.NONE
        }
    }

    val expandedWidth = if (state.windowState.windowWidthDp < 780) 232.dp else 264.dp
    val targetWidth = if (expanded) expandedWidth else 72.dp
    val animatedWidth by animateDpAsState(
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
            .width(animatedWidth)
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
                        onExitReorderMode = { reorderMode = SidebarReorderMode.NONE },
                        onToggleExpanded = onToggleExpanded,
                        onCloseDrawer = onCloseDrawer,
                        onSelectSpace = onSelectSpace,
                        onTogglePrivate = onTogglePrivate,
                        onSelectTab = onSelectTab,
                        onCloseTab = onCloseTab,
                        onCreateTab = onCreateTab,
                        onCreatePrivateTab = onCreatePrivateTab,
                        onOpenEssential = onOpenEssential,
                        onRequestCreateSpace = { showCreateSpaceDialog = true },
                        onLongPressSpace = { actionSpace = it },
                        onLongPressEssential = { actionEssential = it },
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
                        onLongPressTab = { actionTab = it }
                    )
                }
            }
        }
    }

    // --- Dialogs & Action Sheets ---

    if (showCreateSpaceDialog) {
        CreateSpaceDialog(
            onDismiss = { showCreateSpaceDialog = false },
            onConfirm = { name, iconKey, isolated ->
                showCreateSpaceDialog = false
                onCreateSpace(name, iconKey, isolated)
            }
        )
    }

    actionSpace?.let { target ->
        ItemActionBottomSheet(
            title = target.name,
            subtitle = if (!target.containerProfileName.isNullOrBlank()) "Isolated container workspace" else "Workspace",
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
            actions = listOf(
                SheetActionItem(
                    label = "Rename workspace",
                    icon = Icons.Rounded.Edit,
                    onClick = { spaceToRename = target }
                ),
                SheetActionItem(
                    label = "Change icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { spaceToPickIcon = target }
                ),
                SheetActionItem(
                    label = "Reorder workspaces",
                    icon = Icons.Rounded.SwapVert,
                    enabled = state.spaces.size > 1 && isEffectiveExpanded,
                    onClick = { reorderMode = SidebarReorderMode.SPACES }
                ),
                SheetActionItem(
                    label = "Delete workspace",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    enabled = state.spaces.size > 1,
                    onClick = { spaceToDelete = target }
                )
            ),
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

    actionEssential?.let { target ->
        ItemActionBottomSheet(
            title = target.title,
            subtitle = target.domain,
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
            actions = listOf(
                SheetActionItem(
                    label = "Edit title & URL",
                    icon = Icons.Rounded.Edit,
                    onClick = { essentialToEdit = target }
                ),
                SheetActionItem(
                    label = "Set custom icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { essentialToPickIcon = target }
                ),
                SheetActionItem(
                    label = "Reorder essentials",
                    icon = Icons.Rounded.SwapVert,
                    enabled = state.activeSpaceEssentials.size > 1,
                    onClick = { reorderMode = SidebarReorderMode.ESSENTIALS }
                ),
                SheetActionItem(
                    label = "Delete",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { itemCallbacks.onRemoveEssential(target.id) }
                )
            ),
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
            }
            val groupSize = if (target.isPinned && !isPrivate) {
                state.visiblePinnedTabs.size
            } else {
                state.visibleRegularTabs.size
            }
            if (isEffectiveExpanded) {
                add(
                    SheetActionItem(
                        label = if (target.isPinned) "Reorder pinned tabs" else "Reorder tabs",
                        icon = Icons.Rounded.SwapVert,
                        enabled = groupSize > 1,
                        onClick = {
                            reorderMode = if (target.isPinned && !isPrivate) {
                                SidebarReorderMode.PINNED_TABS
                            } else {
                                SidebarReorderMode.OPEN_TABS
                            }
                        }
                    )
                )
            }
            add(
                SheetActionItem(
                    label = if (target.isPinned) "Close & delete pinned tab" else "Close tab",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { onCloseTab(target.id) }
                )
            )
        }
        ItemActionBottomSheet(
            title = target.displayTitle,
            subtitle = target.displayHost.ifBlank { "Start Page" },
            leadingIcon = {
                if (target.isStartPage && target.customIcon == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SwiftfoxEmblem(
                                size = 22.dp,
                                variant = if (isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
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
        ItemActionBottomSheet(
            title = target.title,
            subtitle = OmniboxEngine.extractHost(target.url).ifBlank { target.url },
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
            actions = listOf(
                SheetActionItem(
                    label = "Edit title & URL",
                    icon = Icons.Rounded.Edit,
                    onClick = { zenPinnedToEdit = target }
                ),
                SheetActionItem(
                    label = "Set custom icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { zenPinnedToPickIcon = target }
                ),
                SheetActionItem(
                    label = "Add to Essentials",
                    icon = Icons.Rounded.StarOutline,
                    onClick = { itemCallbacks.onAddZenPinnedToEssentials(target.id) }
                ),
                SheetActionItem(
                    label = "Reorder synced pinned",
                    icon = Icons.Rounded.SwapVert,
                    enabled = state.activeSpaceZenPinned.size > 1,
                    onClick = { reorderMode = SidebarReorderMode.ZEN_PINNED }
                ),
                SheetActionItem(
                    label = "Delete",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { itemCallbacks.onDeleteZenPinned(target.id) }
                )
            ),
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
}

@Composable
private fun ColumnScope.ArcExpandedSidebarContent(
    state: BrowserUiState,
    isOverlayDrawer: Boolean,
    reorderMode: SidebarReorderMode,
    onExitReorderMode: () -> Unit,
    onToggleExpanded: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onOpenEssential: (String) -> Unit,
    onRequestCreateSpace: () -> Unit,
    onLongPressSpace: (SpaceEntity) -> Unit,
    onLongPressEssential: (EssentialEntity) -> Unit,
    onLongPressTab: (BrowserTabUi) -> Unit,
    onLongPressZenPinned: (ZenPinnedItemEntity) -> Unit,
    itemCallbacks: WorkspaceItemCallbacks
) {
    val foxColors = LocalSwiftfoxColors.current
    val emblemVariant = if (state.isPrivateView) {
        SwiftfoxEmblemVariant.PURPLE
    } else {
        SwiftfoxEmblemVariant.ORANGE
    }
    val activeSpace = state.activeSpace

    // Drag-and-drop reorder controllers per section (active only in Reorder Mode)
    val essentials = state.activeSpaceEssentials
    val localPinnedTabs = state.visiblePinnedTabs
    val zenPinned = state.activeSpaceZenPinned
    val regularTabs = state.visibleRegularTabs

    val spacesReorder = rememberDragReorderController(
        sourceItems = state.spaces,
        keyOf = { it.id },
        axis = DragAxis.HORIZONTAL,
        onCommitOrder = itemCallbacks.onReorderSpaces
    )
    val essentialsReorder = rememberDragReorderController(
        sourceItems = essentials,
        keyOf = { it.id },
        axis = DragAxis.BOTH,
        onCommitOrder = itemCallbacks.onReorderEssentials
    )
    val pinnedTabsReorder = rememberDragReorderController(
        sourceItems = localPinnedTabs,
        keyOf = { it.id },
        axis = DragAxis.VERTICAL,
        onCommitOrder = { ids -> itemCallbacks.onReorderTabs(ids, true) }
    )
    val zenPinnedReorder = rememberDragReorderController(
        sourceItems = zenPinned,
        keyOf = { it.id },
        axis = DragAxis.VERTICAL,
        onCommitOrder = itemCallbacks.onReorderZenPinned
    )
    val openTabsReorder = rememberDragReorderController(
        sourceItems = regularTabs,
        keyOf = { it.id },
        axis = DragAxis.VERTICAL,
        onCommitOrder = { ids -> itemCallbacks.onReorderTabs(ids, false) }
    )

    // 1. Header: emblem + active Space title + collapse/close button
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SwiftfoxEmblem(
            size = 24.dp,
            variant = emblemVariant
        )
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
            if (!state.isPrivateView && !activeSpace?.containerProfileName.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Isolated container",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
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
        reorderMode == SidebarReorderMode.ZEN_PINNED ||
        reorderMode == SidebarReorderMode.OPEN_TABS
    if (isVerticalListReordering) {
        val bannerText = when (reorderMode) {
            SidebarReorderMode.ESSENTIALS -> "Drag essentials to reorder"
            SidebarReorderMode.PINNED_TABS -> "Drag pinned tabs"
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
    val displayedPinnedTabs = if (reorderMode == SidebarReorderMode.PINNED_TABS) pinnedTabsReorder.items.toList() else localPinnedTabs
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
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayedEssentials.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { ess ->
                                SidebarEssentialTile(
                                    essential = ess,
                                    isReorderMode = isReorderingEss,
                                    onClick = {
                                        if (!isReorderingEss) onOpenEssential(ess.url)
                                    },
                                    onLongClick = {
                                        if (!isReorderingEss) onLongPressEssential(ess)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .dragReorderItem(
                                            key = ess.id,
                                            enabled = isReorderingEss,
                                            controller = essentialsReorder
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

        // Dedicated Arc/Zen-style Pinned Tabs section (local pinned tabs + Zen synced pinned items)
        if (!state.isPrivateView && (displayedPinnedTabs.isNotEmpty() || displayedZenPinned.isNotEmpty())) {
            item(key = "pinned_section_header", contentType = "sidebar_section_header") {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
            items(
                items = displayedPinnedTabs,
                key = { "pinned_${it.id}" },
                contentType = { "sidebar_pinned_tab_row" }
            ) { tab ->
                val isReorderingPinned = reorderMode == SidebarReorderMode.PINNED_TABS
                SidebarTabRow(
                    tab = tab,
                    isActive = tab.id == state.activeTabId,
                    isPinnedStyle = true,
                    isReorderMode = isReorderingPinned,
                    onSelect = { if (!isReorderingPinned) onSelectTab(tab.id) },
                    onLongPress = { if (!isReorderingPinned) onLongPressTab(tab) },
                    onClose = { onCloseTab(tab.id) },
                    modifier = Modifier
                        .then(if (!isReorderingPinned) Modifier.animateItem() else Modifier)
                        .dragReorderItem(
                            key = tab.id,
                            enabled = isReorderingPinned,
                            controller = pinnedTabsReorder
                        )
                )
            }
            items(
                items = displayedZenPinned,
                key = { "zen_${it.id}" },
                contentType = { "sidebar_zen_pinned_row" }
            ) { item ->
                val isReorderingZen = reorderMode == SidebarReorderMode.ZEN_PINNED
                SidebarZenPinnedRow(
                    item = item,
                    isReorderMode = isReorderingZen,
                    onClick = { if (!isReorderingZen) onOpenEssential(item.url) },
                    onLongPress = { if (!isReorderingZen) onLongPressZenPinned(item) },
                    modifier = Modifier
                        .then(if (!isReorderingZen) Modifier.animateItem() else Modifier)
                        .dragReorderItem(
                            key = item.id,
                            enabled = isReorderingZen,
                            controller = zenPinnedReorder
                        )
                )
            }
            item(key = "pinned_section_divider", contentType = "sidebar_divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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

    // 4. Bottom Icon-Only Workspace Switcher & Private Toggle pinned in thumb reach
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    if (reorderMode == SidebarReorderMode.SPACES) {
        ReorderModeBanner(
            visible = true,
            label = "Drag workspaces",
            onDone = onExitReorderMode
        )
    }

    val isReorderingSpaces = reorderMode == SidebarReorderMode.SPACES
    val displayedSpaces = if (isReorderingSpaces) spacesReorder.items.toList() else state.spaces

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
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
            SidebarBottomSpaceIconTile(
                space = space,
                selected = selected,
                isReorderMode = isReorderingSpaces,
                onClick = { if (!isReorderingSpaces) onSelectSpace(space.id) },
                onLongClick = { if (!isReorderingSpaces) onLongPressSpace(space) },
                modifier = Modifier
                    .then(if (!isReorderingSpaces) Modifier.animateItem() else Modifier)
                    .dragReorderItem(
                        key = space.id,
                        enabled = isReorderingSpaces,
                        controller = spacesReorder
                    )
            )
        }

        if (!isReorderingSpaces) {
            item(key = "sidebar_bottom_private", contentType = "sidebar_bottom_private") {
                Surface(
                    onClick = { onTogglePrivate(!state.isPrivateView) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (state.isPrivateView) {
                        foxColors.privateContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = if (state.isPrivateView) {
                        BorderStroke(1.5.dp, foxColors.privateAccent)
                    } else {
                        null
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Private Space",
                            tint = if (state.isPrivateView) foxColors.privateAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item(key = "sidebar_bottom_add_space", contentType = "sidebar_bottom_add_space") {
                Surface(
                    onClick = onRequestCreateSpace,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Create Space",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarEssentialTile(
    essential: EssentialEntity,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (isReorderMode) 1.5.dp else 1.dp,
            color = if (isReorderMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            }
        ),
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
                contentDescription = "Open ${essential.title} (${essential.domain})"
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
                tint = MaterialTheme.colorScheme.primary,
                fillImageToContainer = true
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarZenPinnedRow(
    item: ZenPinnedItemEntity,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isReorderMode) 1.5.dp else 1.dp,
            color = if (isReorderMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            }
        ),
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                fillImageToContainer = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = item.folderName?.takeIf { it.isNotBlank() }
                    ?: OmniboxEngine.extractHost(item.url)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = when {
                    isReorderMode -> Icons.Rounded.DragIndicator
                    item.isPinned -> Icons.Rounded.PushPin
                    else -> Icons.Rounded.Tab
                },
                contentDescription = if (isReorderMode) "Drag to reorder" else "Pinned item",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isReorderMode) 1f else 0.72f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarTabRow(
    tab: BrowserTabUi,
    isActive: Boolean,
    isPinnedStyle: Boolean,
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
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        isActive && isPrivate -> foxColors.onPrivateContainer
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        isReorderMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isActive && isPrivate -> BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.65f))
        isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        isPinnedStyle -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
            if (tab.isStartPage && tab.customIcon == null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    SwiftfoxEmblem(
                        size = 16.dp,
                        variant = if (isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
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
                    fillImageToContainer = true
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
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tab.resolvedSiteMode == SiteMode.DESKTOP) {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = "Desktop mode",
                            tint = badgeTint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (tab.isPlayingMedia) {
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
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned tab",
                        tint = badgeTint.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp)
                    )
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
private fun SidebarBottomSpaceIconTile(
    space: SpaceEntity,
    selected: Boolean,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = when {
            isReorderMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            selected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else -> null
        },
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
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
                contentDescription = "${space.name} workspace"
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            SpaceIconGlyph(
                iconKey = space.iconKey,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = space.name,
                size = 20.dp
            )
            val dotColor = parseHexColorOrNull(space.themeColorHex)
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
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
    onLongPressTab: (BrowserTabUi) -> Unit
) {
    val foxColors = LocalSwiftfoxColors.current
    val emblemVariant = if (state.isPrivateView) {
        SwiftfoxEmblemVariant.PURPLE
    } else {
        SwiftfoxEmblemVariant.ORANGE
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SwiftfoxEmblem(
            size = 26.dp,
            variant = emblemVariant
        )
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
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { onSelectSpace(space.id) },
                        onLongClick = { onLongPressSpace(space) }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SpaceIconGlyph(
                        iconKey = space.iconKey,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = space.name,
                        size = 20.dp
                    )
                    val dotColor = parseHexColorOrNull(space.themeColorHex)
                    if (dotColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(7.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
        Surface(
            onClick = { onTogglePrivate(!state.isPrivateView) },
            shape = RoundedCornerShape(16.dp),
            color = if (state.isPrivateView) {
                foxColors.privateContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Private Space",
                    tint = if (state.isPrivateView) foxColors.privateAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = state.visibleTabs,
            key = { it.id },
            contentType = { "sidebar_tab_collapsed" }
        ) { tab ->
            val isActive = tab.id == state.activeTabId
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isActive && tab.isPrivate -> foxColors.privateContainer
                    isActive -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = when {
                    isActive && tab.isPrivate -> BorderStroke(1.5.dp, foxColors.privateAccent)
                    isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    tab.isPinned -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    else -> null
                },
                modifier = Modifier
                    .animateItem()
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { onSelectTab(tab.id) },
                        onLongClick = { onLongPressTab(tab) }
                    )
                    .semantics {
                        contentDescription = "Switch to ${tab.displayTitle} (${tab.displayHost.ifBlank { "Start Page" }})"
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (tab.isStartPage && tab.customIcon == null) {
                        SwiftfoxEmblem(
                            size = 22.dp,
                            variant = if (tab.isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
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
                            fillImageToContainer = true
                        )
                    }
                    if (tab.isPinned && !tab.isPrivate) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
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
