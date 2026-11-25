package app.overbay.swiftfox.ui.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import app.overbay.swiftfox.ui.icons.automirrored.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.DesktopWindows
import app.overbay.swiftfox.ui.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import app.overbay.swiftfox.ui.icons.rounded.Palette
import app.overbay.swiftfox.ui.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import app.overbay.swiftfox.ui.icons.rounded.Shield
import app.overbay.swiftfox.ui.icons.rounded.StarOutline
import app.overbay.swiftfox.ui.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.TabThumbnailsSnapshot
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
import app.overbay.swiftfox.ui.components.rememberDragReorderController
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import app.overbay.swiftfox.ui.theme.spaceIconFor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun SpacesTabSwitcher(
    state: BrowserUiState,
    thumbnails: TabThumbnailsSnapshot,
    onClose: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateTab: (Boolean) -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onCreateSpace: (String, String, Boolean) -> Unit,
    onDeleteSpace: (String) -> Unit = {},
    onOpenSyncedTabs: () -> Unit = {},
    itemCallbacks: WorkspaceItemCallbacks = WorkspaceItemCallbacks(onDeleteSpace = onDeleteSpace)
) {
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var showCloseAllSheet by remember { mutableStateOf(false) }
    var isReorderingSpaces by remember { mutableStateOf(false) }

    var actionSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToRename by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToPickIcon by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }

    var essentialToEdit by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToPickIcon by remember { mutableStateOf<EssentialEntity?>(null) }

    var actionTab by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToEdit by remember { mutableStateOf<BrowserTabUi?>(null) }
    var tabToPickIcon by remember { mutableStateOf<BrowserTabUi?>(null) }

    var actionZenPinned by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToEdit by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }
    var zenPinnedToPickIcon by remember { mutableStateOf<ZenPinnedItemEntity?>(null) }

    val foxColors = LocalSwiftfoxColors.current

    LaunchedEffect(state.isPrivateView, state.activeSpaceId) {
        isReorderingSpaces = false
    }
    val spacesReorder = rememberDragReorderController(
        sourceItems = state.spaces,
        keyOf = { it.id },
        axis = DragAxis.HORIZONTAL,
        onCommitOrder = itemCallbacks.onReorderSpaces,
        enabled = isReorderingSpaces
    )
    val spaceTabCounts = remember(
        state.tabs,
        state.essentials,
        state.spaces,
        state.settings.syncZenSeparateEssentials
    ) {
        state.spaces.associate { space -> space.id to state.tabSwitcherCountForSpace(space.id) }
    }
    val zenPinned = remember(state.zenPinnedItems, state.activeSpaceId, state.isPrivateView) {
        state.activeSpaceZenPinned
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
    val tabs = remember(
        state.tabs,
        state.essentials,
        state.spaces,
        state.activeSpaceId,
        state.isPrivateView,
        state.settings.syncZenSeparateEssentials
    ) {
        state.tabSwitcherTabs
    }
    val canCloseAll = remember(
        isReorderingSpaces,
        state.tabs,
        state.essentials,
        state.spaces,
        state.zenPinnedItems,
        state.activeSpaceId,
        state.isPrivateView,
        state.settings.syncZenSeparateEssentials
    ) {
        !isReorderingSpaces && WorkspaceTabResolver.hasAnythingToCloseAll(state)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateTab(state.isPrivateView) },
                containerColor = if (state.isPrivateView) {
                    foxColors.privateAccent
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (state.isPrivateView) {
                    foxColors.onPrivateAccent
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                shape = RoundedCornerShape(24.dp),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(if (state.isPrivateView) "Private tab" else "New tab") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to browser"
                    )
                }

                Text(
                    text = if (state.isPrivateView) "Private Tabs" else "Spaces",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (state.isPrivateView) {
                        foxColors.privateAccent
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    onClick = onOpenSyncedTabs,
                    shape = RoundedCornerShape(18.dp),
                    color = if (state.syncedRemoteTabs.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = "Synced open tabs",
                            tint = if (state.syncedRemoteTabs.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        if (state.syncedRemoteTabs.isNotEmpty()) {
                            Text(
                                text = "${state.syncedRemoteTabs.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Surface(
                    checked = state.isPrivateView,
                    onCheckedChange = onTogglePrivate,
                    shape = RoundedCornerShape(18.dp),
                    color = if (state.isPrivateView) {
                        foxColors.privateContainer
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.Switch
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = if (state.isPrivateView) {
                                foxColors.privateAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Private",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.isPrivateView) {
                                foxColors.onPrivateContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (isReorderingSpaces) {
                ReorderModeBanner(
                    visible = true,
                    label = "Drag Spaces to reorder",
                    onDone = { isReorderingSpaces = false },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Full-bleed Editorial Spaces tab strip
            val displayedSpaces = if (isReorderingSpaces) spacesReorder.items.toList() else state.spaces
            EditorialSpacesTabStrip(
                spaces = displayedSpaces,
                selectedSpaceId = if (state.isPrivateView) null else state.activeSpaceId,
                onSelectSpace = onSelectSpace,
                onLongPressSpace = { actionSpace = it },
                horizontalPadding = 16.dp,
                iconSize = 17.dp,
                tabCountForSpace = { spaceId -> spaceTabCounts[spaceId] ?: 0 },
                showThemeDot = true,
                showLockIndicator = true,
                isReorderMode = isReorderingSpaces,
                reorderController = spacesReorder,
                onCreateSpaceClick = { showCreateSpaceDialog = true }
            )

            // Zen Browser synced pinned items for active Space with web favicons
            if (!state.isPrivateView && zenPinned.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = zenPinned,
                        key = { it.id },
                        contentType = { "zen_pinned_pill" }
                    ) { item ->
                        val boundTab = tabByZenPinnedId[item.id]
                        CompactZenPinnedPill(
                            item = item,
                            isLoaded = boundTab != null,
                            isActive = boundTab != null && boundTab.id == state.activeTabId,
                            isPlayingMedia = boundTab?.isPlayingMedia == true,
                            onClick = {
                                if (boundTab != null) {
                                    onSelectTab(boundTab.id)
                                } else {
                                    itemCallbacks.onActivateZenPinned(item.id)
                                }
                            },
                            onLongClick = { actionZenPinned = item }
                        )
                    }
                }
            }

            // Visual tab card grid (open Essentials render right alongside regular tabs as standard cards)
            if (tabs.isNotEmpty() || canCloseAll) {
                Spacer(Modifier.height(10.dp))
                WorkspaceTabsHeaderRow(
                    title = if (state.isPrivateView) "Private tabs" else "Open tabs",
                    showCloseAll = canCloseAll,
                    isPrivate = state.isPrivateView,
                    onClickCloseAll = { showCloseAllSheet = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(6.dp))
            } else {
                Spacer(Modifier.height(14.dp))
            }

            if (tabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwiftfoxEmblem(
                            size = 42.dp,
                            variant = if (state.isPrivateView) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
                        )
                        Text(
                            text = if (state.isPrivateView) "No open private tabs" else "No tabs in this Space",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (state.isPrivateView) {
                                "Private tabs run in an isolated WebView profile and leave no history."
                            } else {
                                "Tap New tab below to start browsing."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 164.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = tabs,
                        key = { it.id },
                        contentType = { "tab_card" }
                    ) { tab ->
                        val isLoaded = !tab.isUnloaded
                        val isPinnedTab = tab.isPinned && !tab.isPrivate
                        val canSwipeDismiss = !isPinnedTab || isLoaded
                        val closeOrUnloadTab = {
                            val essId = tab.essentialId
                            when {
                                essId != null -> itemCallbacks.onCloseEssentialTab(essId)
                                isPinnedTab -> itemCallbacks.onClosePinnedTab(tab.id)
                                else -> onCloseTab(tab.id)
                            }
                        }
                        SwipeToDismissTabCard(
                            tabId = tab.id,
                            enabled = canSwipeDismiss,
                            staysInGridAfterDismiss = isPinnedTab,
                            onDismiss = closeOrUnloadTab,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 120),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 650f
                                ),
                                fadeOutSpec = null
                            )
                        ) { isCardDismissing ->
                            TabPreviewCard(
                                tab = tab,
                                isActive = isLoaded && tab.id == state.activeTabId,
                                isLoaded = isLoaded,
                                thumbnail = if (isLoaded) thumbnails.bitmaps[tab.id] else null,
                                onSelect = {
                                    if (!isCardDismissing) {
                                        onSelectTab(tab.id)
                                    }
                                },
                                onLongPress = { if (!isCardDismissing) actionTab = tab },
                                onClose = { if (!isCardDismissing) closeOrUnloadTab() }
                            )
                        }
                    }
                }
            }
        }
    }

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
            onSelectIcon = { spec -> itemCallbacks.onSetEssentialCustomIcon(target.id, spec) },
            onDismiss = { essentialToPickIcon = null }
        )
    }

    actionTab?.let { target ->
        val isPrivate = target.isPrivate
        val boundEssential = target.essentialId?.let { id -> state.essentials.firstOrNull { it.id == id } }
        val actions = buildList {
            if (!isPrivate) {
                if (boundEssential != null) {
                    if (!target.url.equals(boundEssential.url, ignoreCase = true)) {
                        add(
                            SheetActionItem(
                                label = "Reset to pinned URL",
                                icon = Icons.Rounded.Refresh,
                                onClick = { itemCallbacks.onResetEssentialTab(boundEssential.id) }
                            )
                        )
                    }
                    add(
                        SheetActionItem(
                            label = "Edit title & URL",
                            icon = Icons.Rounded.Edit,
                            onClick = { essentialToEdit = boundEssential }
                        )
                    )
                    add(
                        SheetActionItem(
                            label = "Set custom icon",
                            icon = Icons.Rounded.Palette,
                            onClick = { essentialToPickIcon = boundEssential }
                        )
                    )
                } else {
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
                }
            }
            add(
                SheetActionItem(
                    label = if (target.isPinned) "Delete pin" else "Close tab",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = {
                        val essId = target.essentialId
                        if (essId != null) {
                            itemCallbacks.onCloseEssentialTab(essId)
                        } else {
                            onCloseTab(target.id)
                        }
                    }
                )
            )
        }
        val subtitle = buildString {
            append(target.displayHost.ifBlank { "Start Page" })
            if (target.isPinned && !isPrivate) {
                append(if (target.isUnloaded) " · Unloaded" else " · Open")
            }
        }
        ItemActionBottomSheet(
            title = target.displayTitle,
            subtitle = subtitle,
            leadingIcon = {
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
            },
            actions = actions,
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
            onSelectIcon = { spec -> itemCallbacks.onSetTabCustomIcon(target.id, spec) },
            onDismiss = { tabToPickIcon = null }
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
}

@Composable
internal fun DeleteSpaceDialog(
    space: SpaceEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Space \"${space.name}\"?") },
        text = {
            val baseMsg = if (!space.containerProfileName.isNullOrBlank()) {
                "Closes tabs in ${space.name} and clears its isolated WebView container cookies."
            } else {
                "Closes all open tabs in ${space.name}."
            }
            val syncNote = if (!space.syncGuid.isNullOrBlank()) {
                "\n\nThis Space is synced from Zen Browser and will reappear on the next sync unless removed in Zen Browser."
            } else {
                ""
            }
            Text(text = baseMsg + syncNote)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactZenPinnedPill(
    item: ZenPinnedItemEntity,
    isLoaded: Boolean,
    isActive: Boolean,
    isPlayingMedia: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
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
        isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = border,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SiteIconBadge(
                url = item.url,
                fallbackLabel = item.title,
                customIcon = item.customIcon,
                size = 20.dp,
                iconSize = 16.dp,
                shape = RoundedCornerShape(6.dp),
                fillImageToContainer = true,
                modifier = Modifier.graphicsLayer { alpha = if (isLoaded || isActive) 1f else 0.68f }
            )
            Text(
                text = if (!item.folderName.isNullOrBlank()) "${item.folderName} · ${item.title}" else item.title,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPlayingMedia) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Playing media",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (isLoaded) {
                Box(
                    modifier = Modifier
                        .size(if (isActive) 8.dp else 7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

internal fun resolveTabCardSwipeDismissDirection(
    offsetX: Float,
    velocityX: Float,
    cardWidthPx: Float,
    minFlingVelocityPxPerSec: Float,
    dismissDistanceFraction: Float = 0.32f,
    minFlingDistanceFraction: Float = 0.10f
): Int {
    if (cardWidthPx <= 0f) return 0
    val distanceThreshold = cardWidthPx * dismissDistanceFraction
    val flingDistanceFloor = cardWidthPx * minFlingDistanceFraction

    val dismissRight = (offsetX >= distanceThreshold && velocityX > -minFlingVelocityPxPerSec) ||
        (offsetX >= flingDistanceFloor && velocityX >= minFlingVelocityPxPerSec)
    if (dismissRight) return 1

    val dismissLeft = (offsetX <= -distanceThreshold && velocityX < minFlingVelocityPxPerSec) ||
        (offsetX <= -flingDistanceFloor && velocityX <= -minFlingVelocityPxPerSec)
    if (dismissLeft) return -1

    return 0
}

internal fun computeTabCardSwipeExitDurationMs(
    remainingDistancePx: Float,
    velocityX: Float,
    minExitSpeedPxPerSec: Float
): Int {
    val safeSpeed = maxOf(abs(velocityX), minExitSpeedPxPerSec.coerceAtLeast(1f))
    return ((remainingDistancePx.coerceAtLeast(0f) / safeSpeed) * 1000f)
        .roundToInt()
        .coerceIn(50, 105)
}

@Composable
private fun SwipeToDismissTabCard(
    tabId: String,
    enabled: Boolean,
    staysInGridAfterDismiss: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isDismissing: Boolean) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minFlingVelocityPx = with(density) { 280.dp.toPx() }
    val minExitSpeedPx = with(density) { 1500.dp.toPx() }
    val offsetX = remember(tabId) { Animatable(0f) }
    var dragOffsetPx by remember(tabId) { mutableFloatStateOf(0f) }
    var useDragOffset by remember(tabId) { mutableStateOf(false) }
    var cardWidthPx by remember(tabId) { mutableIntStateOf(0) }
    var isDragging by remember(tabId) { mutableStateOf(false) }
    var isDismissing by remember(tabId) { mutableStateOf(false) }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    val currentStaysInGrid by rememberUpdatedState(staysInGridAfterDismiss)

    LaunchedEffect(tabId, enabled) {
        if (!enabled && !isDismissing) {
            isDragging = false
            useDragOffset = false
            dragOffsetPx = 0f
            if (offsetX.value != 0f) {
                offsetX.snapTo(0f)
            }
        }
    }

    Box(
        modifier = modifier
            .zIndex(if (isDragging || isDismissing || useDragOffset) 1f else 0f)
            .onSizeChanged { cardWidthPx = it.width }
            .pointerInput(tabId, enabled, minFlingVelocityPx, minExitSpeedPx) {
                if (!enabled) return@pointerInput
                val velocityTracker = VelocityTracker()
                var currentDragOffset = 0f
                var crossedThreshold = false
                var hasTrackedFirstSample = false

                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (isDismissing) return@detectHorizontalDragGestures
                            velocityTracker.resetTracking()
                            hasTrackedFirstSample = false
                            currentDragOffset = if (useDragOffset) dragOffsetPx else offsetX.value
                            dragOffsetPx = currentDragOffset
                            useDragOffset = true
                            crossedThreshold = false
                            isDragging = true
                            scope.launch { offsetX.stop() }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isDismissing) return@detectHorizontalDragGestures
                            change.consume()
                            if (!hasTrackedFirstSample) {
                                hasTrackedFirstSample = true
                                if (change.uptimeMillis > change.previousUptimeMillis) {
                                    velocityTracker.addPosition(
                                        change.previousUptimeMillis,
                                        change.previousPosition
                                    )
                                }
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            currentDragOffset += dragAmount
                            val targetOffset = currentDragOffset
                            val threshold = cardWidthPx * 0.32f
                            val nowCrossed = cardWidthPx > 0 && abs(targetOffset) >= threshold
                            if (nowCrossed && !crossedThreshold) {
                                crossedThreshold = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!nowCrossed) {
                                crossedThreshold = false
                            }
                            dragOffsetPx = targetOffset
                        },
                        onDragEnd = {
                            isDragging = false
                            if (isDismissing) return@detectHorizontalDragGestures
                            val finalOffset = currentDragOffset
                            val vx = velocityTracker.calculateVelocity().x
                            val direction = resolveTabCardSwipeDismissDirection(
                                offsetX = finalOffset,
                                velocityX = vx,
                                cardWidthPx = cardWidthPx.toFloat(),
                                minFlingVelocityPxPerSec = minFlingVelocityPx
                            )
                            if (direction != 0) {
                                isDismissing = true
                                val exitTarget = direction * (cardWidthPx.coerceAtLeast(1) * 1.12f)
                                val remainingPx = abs(exitTarget - finalOffset)
                                val exitDurationMs = computeTabCardSwipeExitDurationMs(
                                    remainingDistancePx = remainingPx,
                                    velocityX = vx,
                                    minExitSpeedPxPerSec = minExitSpeedPx
                                )
                                var didInvokeDismiss = false
                                val invokeDismissOnce = {
                                    if (!didInvokeDismiss) {
                                        didInvokeDismiss = true
                                        currentOnDismiss()
                                    }
                                }
                                scope.launch {
                                    try {
                                        offsetX.snapTo(finalOffset)
                                        useDragOffset = false
                                        if (currentReduceMotion) {
                                            offsetX.snapTo(exitTarget)
                                        } else {
                                            offsetX.animateTo(
                                                targetValue = exitTarget,
                                                animationSpec = tween(
                                                    durationMillis = exitDurationMs,
                                                    easing = LinearEasing
                                                )
                                            )
                                        }
                                        invokeDismissOnce()
                                        if (currentStaysInGrid) {
                                            if (currentReduceMotion) {
                                                offsetX.snapTo(0f)
                                            } else {
                                                offsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = 700f
                                                    )
                                                )
                                            }
                                            isDismissing = false
                                        }
                                    } finally {
                                        invokeDismissOnce()
                                        if (currentStaysInGrid) {
                                            isDismissing = false
                                        }
                                    }
                                }
                            } else {
                                scope.launch {
                                    offsetX.snapTo(finalOffset)
                                    useDragOffset = false
                                    if (currentReduceMotion) {
                                        offsetX.snapTo(0f)
                                    } else {
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            initialVelocity = vx,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = 700f
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            if (isDismissing) return@detectHorizontalDragGestures
                            val finalOffset = currentDragOffset
                            scope.launch {
                                offsetX.snapTo(finalOffset)
                                useDragOffset = false
                                if (currentReduceMotion) {
                                    offsetX.snapTo(0f)
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 700f
                                        )
                                    )
                                }
                            }
                        }
                    )
                } finally {
                    isDragging = false
                    if (!isDismissing) {
                        useDragOffset = false
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val dx = if (useDragOffset) dragOffsetPx else offsetX.value
                translationX = dx
                val width = size.width.coerceAtLeast(1f)
                val progress = (abs(dx) / (width * 0.95f)).coerceIn(0f, 1f)
                alpha = (1f - progress).coerceIn(0f, 1f)
                if (!reduceMotion) {
                    rotationZ = sign(dx) * (progress * 5f)
                }
            }
        ) {
            content(isDismissing)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabPreviewCard(
    tab: BrowserTabUi,
    isActive: Boolean,
    isLoaded: Boolean = true,
    thumbnail: android.graphics.Bitmap?,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foxColors = LocalSwiftfoxColors.current
    val borderColor = when {
        isActive && tab.isPrivate -> foxColors.privateAccent
        isActive -> MaterialTheme.colorScheme.primary
        tab.isPinned && isLoaded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
        tab.isPinned -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val cardColor = if (tab.isPinned && !isLoaded) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentAlpha = if (tab.isPinned && !isLoaded) 0.7f else 1f

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
            // Card header with real favicon/custom icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = if (tab.isPinned) 12.dp else 2.dp,
                        top = 6.dp,
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab.isStartPage && tab.customIcon == null) {
                    SwiftfoxEmblem(
                        size = 18.dp,
                        variant = if (tab.isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
                    )
                } else {
                    SiteIconBadge(
                        url = tab.url,
                        fallbackLabel = tab.displayHost.ifBlank { tab.displayTitle },
                        customIcon = if (tab.isPrivate) null else tab.customIcon,
                        privateTabId = if (tab.isPrivate) tab.id else null,
                        size = 24.dp,
                        iconSize = 18.dp,
                        shape = RoundedCornerShape(7.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        fillImageToContainer = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tab.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tab.displayHost.ifBlank { "Start Page" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (tab.isPinned && !tab.isPrivate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                            tint = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close ${tab.displayTitle}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Visual preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = remember(thumbnail) { thumbnail?.asImageBitmap() }
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (tab.isStartPage && tab.customIcon == null) {
                            SwiftfoxEmblem(
                                size = 34.dp,
                                variant = if (tab.isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
                            )
                        } else {
                            SiteIconBadge(
                                url = tab.url,
                                fallbackLabel = tab.displayHost.ifBlank { tab.displayTitle },
                                customIcon = if (tab.isPrivate) null else tab.customIcon,
                                privateTabId = if (tab.isPrivate) tab.id else null,
                                size = 44.dp,
                                iconSize = 28.dp,
                                shape = RoundedCornerShape(14.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                monogramFontSize = 18.sp,
                                fillImageToContainer = true
                            )
                        }
                        if (tab.isPinned && !isLoaded) {
                            Text(
                                text = "Tap to load",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isLoaded && (tab.resolvedSiteMode == SiteMode.DESKTOP || tab.isPlayingMedia)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tab.resolvedSiteMode == SiteMode.DESKTOP) {
                                Icon(
                                    imageVector = Icons.Rounded.DesktopWindows,
                                    contentDescription = "Desktop mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (tab.isPlayingMedia) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Playing media",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("star") }
    var isolatedContainer by remember { mutableStateOf(false) }

    val iconOptions = listOf(
        "home", "work", "book", "code", "shop", "star",
        "rocket", "bolt", "globe", "games", "music", "server"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Space name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconOptions.forEach { key ->
                        val active = selectedIcon == key
                        Surface(
                            onClick = { selectedIcon = key },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = spaceIconFor(key),
                                    contentDescription = key,
                                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Container isolation", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Separate cookies & logins via WebView ProfileStore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isolatedContainer,
                        onCheckedChange = { isolatedContainer = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon, isolatedContainer) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
