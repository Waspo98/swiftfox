package app.overbay.swiftfox.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import app.overbay.swiftfox.ui.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Edit
import app.overbay.swiftfox.ui.icons.rounded.History
import app.overbay.swiftfox.ui.icons.rounded.Palette
import app.overbay.swiftfox.ui.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import app.overbay.swiftfox.ui.icons.rounded.StarOutline
import app.overbay.swiftfox.ui.icons.rounded.SwapVert
import app.overbay.swiftfox.ui.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSearchScope
import app.overbay.swiftfox.ui.components.CustomIconPickerSheet
import app.overbay.swiftfox.ui.components.DragAxis
import app.overbay.swiftfox.ui.components.EditTitleAndUrlDialog
import app.overbay.swiftfox.ui.components.EditorialSpacesTabStrip
import app.overbay.swiftfox.ui.components.ItemActionBottomSheet
import app.overbay.swiftfox.ui.components.RenameSpaceDialog
import app.overbay.swiftfox.ui.components.ReorderModeBanner
import app.overbay.swiftfox.ui.components.SheetActionItem
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.components.SpaceIconGlyph
import app.overbay.swiftfox.ui.components.dragReorderContainer
import app.overbay.swiftfox.ui.components.dragReorderItem
import app.overbay.swiftfox.ui.components.horizontalFadingEdges
import app.overbay.swiftfox.ui.components.rememberDragReorderController
import app.overbay.swiftfox.ui.tabs.DeleteSpaceDialog
import app.overbay.swiftfox.ui.tabs.WorkspaceItemCallbacks
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.SwiftfoxBrandLockup
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant

private enum class StartPageRowReorderMode {
    NONE,
    ESSENTIALS,
    PINNED_TABS,
    ZEN_PINNED
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPageContent(
    state: BrowserUiState,
    onFocusOmnibox: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSelectSpace: (String) -> Unit,
    onSelectStartPageSpace: (String) -> Unit = onSelectSpace,
    onSelectTab: (String) -> Unit,
    onAddEssential: (String, String) -> Unit,
    onRemoveEssential: (String) -> Unit,
    onCloseTab: (String) -> Unit = {},
    onFocusOmniboxFromRect: (Rect?) -> Unit = { onFocusOmnibox() },
    onFocusOmniboxWithScope: (Rect?, OmniboxSearchScope) -> Unit = { rect, _ -> onFocusOmniboxFromRect(rect) },
    onOpenSearchSettings: () -> Unit = {},
    hideSearchCapsuleProvider: () -> Boolean = { false },
    itemCallbacks: WorkspaceItemCallbacks = WorkspaceItemCallbacks(
        onAddEssential = onAddEssential,
        onRemoveEssential = onRemoveEssential
    ),
    addBottomNavInset: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showAddEssentialDialog by remember { mutableStateOf(false) }
    var rowReorderMode by remember { mutableStateOf(StartPageRowReorderMode.NONE) }
    var isReorderingSpaces by remember { mutableStateOf(false) }
    val searchCapsuleBounds = remember { BoundsHolder() }

    var actionEssential by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToEdit by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToPickIcon by remember { mutableStateOf<EssentialEntity?>(null) }

    var actionPinnedTab by remember { mutableStateOf<BrowserTabUi?>(null) }
    var pinnedTabToEdit by remember { mutableStateOf<BrowserTabUi?>(null) }
    var pinnedTabToPickIcon by remember { mutableStateOf<BrowserTabUi?>(null) }

    var actionZenPinned by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToEdit by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToPickIcon by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }

    var actionSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToRename by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToPickIcon by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }

    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = state.activeTab?.isPrivate == true || state.isPrivateView
    val isUnfolded = state.windowState.posture == DevicePosture.UNFOLDED

    LaunchedEffect(isPrivate, state.activeSpaceId) {
        rowReorderMode = StartPageRowReorderMode.NONE
        isReorderingSpaces = false
    }

    val essentials = remember(
        state.essentials,
        state.spaces,
        state.activeSpaceId,
        state.settings.syncZenSeparateEssentials
    ) {
        state.activeSpaceEssentials
    }
    val localPinnedTabs = remember(state.tabs, state.activeSpaceId, isPrivate) {
        if (isPrivate) emptyList() else state.visiblePinnedTabs
    }
    val zenPinned = remember(state.zenPinnedItems, state.activeSpaceId, isPrivate) {
        if (isPrivate) emptyList() else state.activeSpaceZenPinned
    }
    val tabByEssentialId = remember(state.tabs) {
        buildMap {
            for (tab in state.tabs) {
                if (!tab.isPrivate && tab.essentialId != null && !containsKey(tab.essentialId)) {
                    put(tab.essentialId, tab)
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
    val isReorderingRow = rowReorderMode != StartPageRowReorderMode.NONE
    val essentialsReorder = rememberDragReorderController(
        sourceItems = essentials,
        keyOf = { it.id },
        axis = DragAxis.BOTH,
        onCommitOrder = itemCallbacks.onReorderEssentials,
        enabled = rowReorderMode == StartPageRowReorderMode.ESSENTIALS
    )
    val pinnedTabsReorder = rememberDragReorderController(
        sourceItems = localPinnedTabs,
        keyOf = { it.id },
        axis = DragAxis.BOTH,
        onCommitOrder = { ids -> itemCallbacks.onReorderTabs(ids, true) },
        enabled = rowReorderMode == StartPageRowReorderMode.PINNED_TABS
    )
    val zenPinnedReorder = rememberDragReorderController(
        sourceItems = zenPinned,
        keyOf = { it.id },
        axis = DragAxis.BOTH,
        onCommitOrder = itemCallbacks.onReorderZenPinned,
        enabled = rowReorderMode == StartPageRowReorderMode.ZEN_PINNED
    )
    val spacesReorder = rememberDragReorderController(
        sourceItems = state.spaces,
        keyOf = { it.id },
        axis = DragAxis.HORIZONTAL,
        onCommitOrder = itemCallbacks.onReorderSpaces,
        enabled = isReorderingSpaces
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isUnfolded) 720.dp else 540.dp)
                .fillMaxWidth()
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = !isReorderingRow
                )
                .padding(vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand mark & wordmark
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = if (isUnfolded) 12.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPrivate) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = foxColors.privateContainer,
                        border = BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SwiftfoxEmblem(
                                size = 38.dp,
                                variant = SwiftfoxEmblemVariant.PURPLE
                            )
                        }
                    }
                    Text(
                        text = "Private Space",
                        style = MaterialTheme.typography.headlineMedium,
                        color = foxColors.privateAccent
                    )
                    Text(
                        text = "Isolated WebView profile. History and cookies clear when your last private tab closes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val wordmarkColor = if (
                        state.settings.accentPreset == AccentPreset.FOX_AMBER &&
                        !state.settings.useDynamicColor
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    }
                    SwiftfoxBrandLockup(
                        emblemSize = 40.dp,
                        wordmarkSize = 32.sp,
                        variant = SwiftfoxEmblemVariant.ORANGE,
                        wordmarkColor = wordmarkColor,
                        showBetaBadge = SwiftfoxBrand.isBetaBuild
                    )
                }
            }

            // Primary Search Capsule (queries live root bounds at tap time for smooth Omnibox morph expand)
            Surface(
                onClick = { onFocusOmniboxFromRect(searchCapsuleBounds.currentBoundsInRoot()) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isPrivate) {
                        foxColors.privateAccent.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 56.dp)
                    .onPlaced { coords ->
                        searchCapsuleBounds.coordinates = coords
                    }
                    .graphicsLayer {
                        alpha = if (hideSearchCapsuleProvider()) 0f else 1f
                    }
            ) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SearchScopeSelectorAnchor(
                        defaultEngine = state.settings.searchEngine,
                        customSearchUrlTemplate = state.settings.customSearchUrlTemplate,
                        activeScope = null,
                        isPrivate = isPrivate,
                        compact = false,
                        onSelectScope = { scope ->
                            onFocusOmniboxWithScope(searchCapsuleBounds.currentBoundsInRoot(), scope)
                        },
                        onOpenSearchSettings = onOpenSearchSettings
                    )
                    Text(
                        text = "Search ${state.settings.searchEngine.displayName(state.settings.customSearchUrlTemplate)} or type URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Full-bleed Editorial Spaces tab strip (hidden in Private mode or when unfolded sidebar shows Spaces)
            val sidebarVisible = isUnfolded && state.activeProfile.showSpacesSidebar
            if (!isPrivate && !sidebarVisible && state.spaces.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReorderModeBanner(
                        visible = isReorderingSpaces,
                        label = "Drag Spaces to reorder",
                        onDone = { isReorderingSpaces = false },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    val displayedSpaces = if (isReorderingSpaces) spacesReorder.items.toList() else state.spaces
                    EditorialSpacesTabStrip(
                        spaces = displayedSpaces,
                        selectedSpaceId = state.activeSpaceId,
                        onSelectSpace = onSelectStartPageSpace,
                        onLongPressSpace = { actionSpace = it },
                        horizontalPadding = 20.dp,
                        iconSize = 16.dp,
                        isReorderMode = isReorderingSpaces,
                        reorderController = spacesReorder
                    )
                }
            }

            // Unified horizontally scrollable "Essentials & Pinned" row (Essentials first, then active Space's pins)
            if (!isPrivate) {
                val displayedEssentials = if (rowReorderMode == StartPageRowReorderMode.ESSENTIALS) {
                    essentialsReorder.items.toList()
                } else {
                    essentials
                }
                val displayedLocalPinned = if (rowReorderMode == StartPageRowReorderMode.PINNED_TABS) {
                    pinnedTabsReorder.items.toList()
                } else {
                    localPinnedTabs
                }
                val displayedZenPinned = if (rowReorderMode == StartPageRowReorderMode.ZEN_PINNED) {
                    zenPinnedReorder.items.toList()
                } else {
                    zenPinned
                }
                val hasPinnedInSpace = displayedLocalPinned.isNotEmpty() || displayedZenPinned.isNotEmpty()
                val rowListState = rememberLazyListState()
                val canScrollBackwardState = remember(rowListState) { derivedStateOf { rowListState.canScrollBackward } }
                val canScrollForwardState = remember(rowListState) { derivedStateOf { rowListState.canScrollForward } }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Essentials & Pinned",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    ReorderModeBanner(
                        visible = isReorderingRow,
                        label = when (rowReorderMode) {
                            StartPageRowReorderMode.ESSENTIALS -> "Drag essentials to reorder"
                            StartPageRowReorderMode.PINNED_TABS -> "Drag pinned tabs to reorder"
                            StartPageRowReorderMode.ZEN_PINNED -> "Drag synced pins to reorder"
                            StartPageRowReorderMode.NONE -> "Drag tiles to reorder"
                        },
                        onDone = { rowReorderMode = StartPageRowReorderMode.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    if (isReorderingRow) {
                        val columns = if (isUnfolded) 5 else 4
                        when (rowReorderMode) {
                            StartPageRowReorderMode.ESSENTIALS -> {
                                val rows = displayedEssentials.chunked(columns)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .dragReorderContainer(
                                            enabled = true,
                                            controller = essentialsReorder
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rows.forEach { rowItems ->
                                        val rowHasDragging = rowItems.any { it.id == essentialsReorder.draggingKey }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (rowHasDragging) 12f else 0f),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { item ->
                                                val boundTab = tabByEssentialId[item.id]
                                                WorkspaceLauncherTile(
                                                    title = item.title,
                                                    url = item.url,
                                                    customIcon = item.customIcon,
                                                    isLoaded = boundTab != null,
                                                    isReorderMode = true,
                                                    onClick = {},
                                                    onLongClick = {},
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .dragReorderItem(
                                                            key = item.id,
                                                            enabled = true,
                                                            controller = essentialsReorder,
                                                            handlePointerInput = false
                                                        )
                                                )
                                            }
                                            repeat(columns - rowItems.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            StartPageRowReorderMode.PINNED_TABS -> {
                                val rows = displayedLocalPinned.chunked(columns)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .dragReorderContainer(
                                            enabled = true,
                                            controller = pinnedTabsReorder
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rows.forEach { rowItems ->
                                        val rowHasDragging = rowItems.any { it.id == pinnedTabsReorder.draggingKey }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (rowHasDragging) 12f else 0f),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { tab ->
                                                WorkspaceLauncherTile(
                                                    title = tab.displayTitle,
                                                    url = tab.url,
                                                    customIcon = tab.customIcon,
                                                    isLoaded = !tab.isUnloaded,
                                                    isReorderMode = true,
                                                    onClick = {},
                                                    onLongClick = {},
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .dragReorderItem(
                                                            key = tab.id,
                                                            enabled = true,
                                                            controller = pinnedTabsReorder,
                                                            handlePointerInput = false
                                                        )
                                                )
                                            }
                                            repeat(columns - rowItems.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            StartPageRowReorderMode.ZEN_PINNED -> {
                                val rows = displayedZenPinned.chunked(columns)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .dragReorderContainer(
                                            enabled = true,
                                            controller = zenPinnedReorder
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rows.forEach { rowItems ->
                                        val rowHasDragging = rowItems.any { it.id == zenPinnedReorder.draggingKey }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (rowHasDragging) 12f else 0f),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { item ->
                                                val boundTab = tabByZenPinnedId[item.id]
                                                WorkspaceLauncherTile(
                                                    title = item.title,
                                                    url = item.url,
                                                    customIcon = item.customIcon,
                                                    isLoaded = boundTab != null,
                                                    isReorderMode = true,
                                                    onClick = {},
                                                    onLongClick = {},
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .dragReorderItem(
                                                            key = item.id,
                                                            enabled = true,
                                                            controller = zenPinnedReorder,
                                                            handlePointerInput = false
                                                        )
                                                )
                                            }
                                            repeat(columns - rowItems.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            StartPageRowReorderMode.NONE -> Unit
                        }
                    } else {
                        LazyRow(
                            state = rowListState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalFadingEdges(
                                    showStartFadeProvider = { canScrollBackwardState.value },
                                    showEndFadeProvider = { canScrollForwardState.value }
                                )
                        ) {
                            items(
                                items = essentials,
                                key = { "ess_${it.id}" },
                                contentType = { "launcher_tile" }
                            ) { item ->
                                val boundTab = tabByEssentialId[item.id]
                                WorkspaceLauncherTile(
                                    title = item.title,
                                    url = item.url,
                                    customIcon = item.customIcon,
                                    isLoaded = boundTab != null,
                                    isReorderMode = false,
                                    onClick = { itemCallbacks.onActivateEssential(item.id) },
                                    onLongClick = { actionEssential = item },
                                    modifier = Modifier
                                        .width(68.dp)
                                        .animateItem()
                                )
                            }

                            if (essentials.isNotEmpty() && hasPinnedInSpace) {
                                item(key = "essentials_pinned_divider", contentType = "launcher_divider") {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .height(56.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(28.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                        )
                                    }
                                }
                            }

                            items(
                                items = localPinnedTabs,
                                key = { "pin_${it.id}" },
                                contentType = { "launcher_tile" }
                            ) { tab ->
                                WorkspaceLauncherTile(
                                    title = tab.displayTitle,
                                    url = tab.url,
                                    customIcon = tab.customIcon,
                                    isLoaded = !tab.isUnloaded,
                                    isReorderMode = false,
                                    onClick = { itemCallbacks.onActivatePinnedTab(tab.id) },
                                    onLongClick = { actionPinnedTab = tab },
                                    modifier = Modifier
                                        .width(68.dp)
                                        .animateItem()
                                )
                            }

                            items(
                                items = zenPinned,
                                key = { "zen_${it.id}" },
                                contentType = { "launcher_tile" }
                            ) { item ->
                                val boundTab = tabByZenPinnedId[item.id]
                                WorkspaceLauncherTile(
                                    title = item.title,
                                    url = item.url,
                                    customIcon = item.customIcon,
                                    isLoaded = boundTab != null,
                                    isReorderMode = false,
                                    onClick = { itemCallbacks.onActivateZenPinned(item.id) },
                                    onLongClick = { actionZenPinned = item },
                                    modifier = Modifier
                                        .width(68.dp)
                                        .animateItem()
                                )
                            }

                            item(key = "add_essential_tile", contentType = "launcher_add") {
                                AddEssentialTile(
                                    onClick = { showAddEssentialDialog = true },
                                    modifier = Modifier.width(68.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Active open tabs / recent jump cards with web favicons
            val expandedSidebarVisible = isUnfolded && state.activeProfile.showSpacesSidebar && state.isUnfoldedRailExpanded
            val jumpTabs = remember(
                expandedSidebarVisible,
                state.tabs,
                state.activeSpaceId,
                state.activeTabId,
                state.isPrivateView
            ) {
                if (expandedSidebarVisible) {
                    emptyList()
                } else {
                    state.visibleTabs.filter { !it.isStartPage && !it.isUnloaded && it.id != state.activeTabId }.take(4)
                }
            }
            val recentBookmarks = remember(state.bookmarks) { state.bookmarks.take(4) }
            val recentHistory = remember(state.history) { state.history.take(4) }

            if (!isPrivate && (jumpTabs.isNotEmpty() || recentBookmarks.isNotEmpty() || recentHistory.isNotEmpty())) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val sectionTitle = when {
                        jumpTabs.isNotEmpty() -> "Open in ${state.activeSpace?.name ?: "Space"}"
                        recentBookmarks.isNotEmpty() -> "Bookmarks"
                        else -> "Recent"
                    }
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val cards: List<StartJumpItem> = remember(jumpTabs, recentBookmarks, recentHistory, onSelectTab, onOpenUrl) {
                        when {
                            jumpTabs.isNotEmpty() -> jumpTabs.map {
                                StartJumpItem(
                                    title = it.displayTitle.ifBlank { it.displayHost },
                                    subtitle = buildString {
                                        append(it.displayHost)
                                        if (it.resolvedSiteMode == SiteMode.DESKTOP) append(" · Desktop")
                                    },
                                    url = it.url,
                                    customIcon = it.customIcon,
                                    fallbackIcon = if (it.resolvedSiteMode == SiteMode.DESKTOP) Icons.Rounded.DesktopWindows else Icons.Rounded.Tab,
                                    onClick = { onSelectTab(it.id) }
                                )
                            }
                            recentBookmarks.isNotEmpty() -> recentBookmarks.map {
                                StartJumpItem(
                                    title = it.title,
                                    subtitle = OmniboxEngine.extractHost(it.url),
                                    url = it.url,
                                    customIcon = null,
                                    fallbackIcon = Icons.Rounded.Bookmark,
                                    onClick = { onOpenUrl(it.url) }
                                )
                            }
                            else -> recentHistory.map {
                                StartJumpItem(
                                    title = it.title,
                                    subtitle = OmniboxEngine.extractHost(it.url),
                                    url = it.url,
                                    customIcon = null,
                                    fallbackIcon = Icons.Rounded.History,
                                    onClick = { onOpenUrl(it.url) }
                                )
                            }
                        }
                    }

                    cards.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { card ->
                                Surface(
                                    onClick = card.onClick,
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 84.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SiteIconBadge(
                                                url = card.url,
                                                fallbackLabel = card.title,
                                                customIcon = card.customIcon,
                                                size = 22.dp,
                                                iconSize = 16.dp,
                                                shape = RoundedCornerShape(6.dp),
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                fallbackVector = card.fallbackIcon,
                                                fillImageToContainer = true
                                            )
                                            Text(
                                                text = card.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = card.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (addBottomNavInset) {
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (showAddEssentialDialog) {
        AddEssentialDialog(
            onDismiss = { showAddEssentialDialog = false },
            onConfirm = { title, url ->
                showAddEssentialDialog = false
                onAddEssential(title, url)
            }
        )
    }

    actionEssential?.let { target ->
        val boundTab = state.tabForEssential(target.id)
        val actions = buildList {
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
                    label = "Reorder",
                    icon = Icons.Rounded.SwapVert,
                    enabled = essentials.size > 1,
                    onClick = { rowReorderMode = StartPageRowReorderMode.ESSENTIALS }
                )
            )
            add(
                SheetActionItem(
                    label = "Delete",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { onRemoveEssential(target.id) }
                )
            )
        }
        ItemActionBottomSheet(
            title = target.title,
            subtitle = if (boundTab != null) "${target.domain} · Open" else target.domain,
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
            actions = actions,
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

    actionPinnedTab?.let { target ->
        val actions = buildList {
            if (!target.isUnloaded) {
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
                    label = "Unpin tab",
                    icon = Icons.Rounded.PushPin,
                    onClick = { itemCallbacks.onSetTabPinned(target.id, false) }
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
                    label = "Edit pinned URL & label",
                    icon = Icons.Rounded.Edit,
                    onClick = { pinnedTabToEdit = target }
                )
            )
            add(
                SheetActionItem(
                    label = "Set custom icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { pinnedTabToPickIcon = target }
                )
            )
            add(
                SheetActionItem(
                    label = "Reorder pinned tabs",
                    icon = Icons.Rounded.SwapVert,
                    enabled = localPinnedTabs.size > 1,
                    onClick = { rowReorderMode = StartPageRowReorderMode.PINNED_TABS }
                )
            )
            add(
                SheetActionItem(
                    label = "Delete pin",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { itemCallbacks.onDeletePinnedTab(target.id) }
                )
            )
        }
        val subtitle = buildString {
            append(target.displayHost.ifBlank { "Start Page" })
            append(if (target.isUnloaded) " · Unloaded" else " · Open")
        }
        ItemActionBottomSheet(
            title = target.displayTitle,
            subtitle = subtitle,
            leadingIcon = {
                SiteIconBadge(
                    url = target.url,
                    fallbackLabel = target.displayHost.ifBlank { target.displayTitle },
                    customIcon = target.customIcon,
                    size = 40.dp,
                    iconSize = 24.dp,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    fillImageToContainer = true
                )
            },
            actions = actions,
            onDismiss = { actionPinnedTab = null }
        )
    }

    pinnedTabToEdit?.let { target ->
        EditTitleAndUrlDialog(
            dialogTitle = "Edit Pinned Tab",
            initialTitle = target.displayTitle,
            initialUrl = if (target.isStartPage) "" else target.url,
            onDismiss = { pinnedTabToEdit = null },
            onConfirm = { title, url ->
                itemCallbacks.onEditTabTitleAndUrl(target.id, title, url)
                pinnedTabToEdit = null
            }
        )
    }

    pinnedTabToPickIcon?.let { target ->
        CustomIconPickerSheet(
            title = "Icon for ${target.displayTitle}",
            currentIconSpec = target.customIcon,
            allowResetToFavicon = true,
            previewUrl = target.url,
            previewLabel = target.displayHost.ifBlank { target.displayTitle },
            onSelectIcon = { spec -> itemCallbacks.onSetTabCustomIcon(target.id, spec) },
            onDismiss = { pinnedTabToPickIcon = null }
        )
    }

    actionZenPinned?.let { target ->
        val boundTab = state.tabForZenPinned(target.id)
        val actions = buildList {
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
            add(
                SheetActionItem(
                    label = "Reorder synced pinned",
                    icon = Icons.Rounded.SwapVert,
                    enabled = zenPinned.size > 1,
                    onClick = { rowReorderMode = StartPageRowReorderMode.ZEN_PINNED }
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
        ItemActionBottomSheet(
            title = target.title,
            subtitle = if (boundTab != null) "$hostLabel · Open" else hostLabel,
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
            actions = actions,
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
            onSelectIcon = { spec -> itemCallbacks.onSetZenPinnedCustomIcon(target.id, spec) },
            onDismiss = { zenPinnedToPickIcon = null }
        )
    }

    actionSpace?.let { target ->
        ItemActionBottomSheet(
            title = target.name,
            subtitle = if (!target.containerProfileName.isNullOrBlank()) "Isolated container Space" else "Space",
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
                    label = "Rename Space",
                    icon = Icons.Rounded.Edit,
                    onClick = { spaceToRename = target }
                ),
                SheetActionItem(
                    label = "Change icon",
                    icon = Icons.Rounded.Palette,
                    onClick = { spaceToPickIcon = target }
                ),
                SheetActionItem(
                    label = "Reorder Spaces",
                    icon = Icons.Rounded.SwapVert,
                    enabled = state.spaces.size > 1,
                    onClick = { isReorderingSpaces = true }
                ),
                SheetActionItem(
                    label = "Delete Space",
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
                itemCallbacks.onDeleteSpace(target.id)
                spaceToDelete = null
            }
        )
    }
}

private data class StartJumpItem(
    val title: String,
    val subtitle: String,
    val url: String,
    val customIcon: String?,
    val fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceLauncherTile(
    title: String,
    url: String,
    customIcon: String?,
    isLoaded: Boolean,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
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
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isLoaded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            border = BorderStroke(
                width = if (isReorderMode) 1.5.dp else 1.dp,
                color = when {
                    isReorderMode -> MaterialTheme.colorScheme.primary
                    isLoaded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                }
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                SiteIconBadge(
                    url = url,
                    fallbackLabel = title,
                    customIcon = customIcon,
                    size = 56.dp,
                    iconSize = 30.dp,
                    shape = RoundedCornerShape(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    monogramFontSize = 22.sp,
                    fillImageToContainer = true,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (isLoaded || isReorderMode) 1f else 0.78f
                    }
                )
                if (isLoaded && !isReorderMode) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .width(16.dp)
                            .height(3.5.dp)
                    ) {}
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (isLoaded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddEssentialTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Pin Essential",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddEssentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin Essential") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Label") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address (e.g. github.com)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(title, url) },
                enabled = url.isNotBlank()
            ) {
                Text("Pin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
