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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxBrandAppearance
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxAppIcon
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
    onCreateSpace: (String, String) -> Unit,
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
        state.zenPinnedItems,
        state.spaces,
        state.settings.syncZenSeparateEssentials
    ) {
        state.spaces.associate { space -> space.id to state.tabSwitcherCountForSpace(space.id) }
    }
    val tabs = remember(
        state.tabs,
        state.essentials,
        state.zenPinnedItems,
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
                isReorderMode = isReorderingSpaces,
                reorderController = spacesReorder,
                onCreateSpaceClick = { showCreateSpaceDialog = true }
            )

            // Visual tab card grid (only actively open tabs in the current Space)
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
                        val brandAppearance = LocalSwiftfoxBrandAppearance.current
                        if (!state.isPrivateView && brandAppearance.syncInApp) {
                            SwiftfoxAppIcon(
                                size = 44.dp,
                                variant = brandAppearance.effectiveAppIconVariant,
                                isLightBackground = brandAppearance.effectiveAppIconLightBackground
                            )
                        } else {
                            SwiftfoxEmblem(
                                size = 42.dp,
                                variant = if (state.isPrivateView) {
                                    SwiftfoxEmblemVariant.PURPLE
                                } else {
                                    brandAppearance.effectiveEmblemVariant
                                },
                                isLightBackground = !state.isPrivateView && brandAppearance.isLightChrome
                            )
                        }
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
                        val isPinnedTab = tab.isPinned && !tab.isPrivate
                        val closeOrUnloadTab = {
                            val essId = tab.essentialId
                            val zenId = tab.zenPinnedId
                            when {
                                essId != null -> itemCallbacks.onCloseEssentialTab(essId)
                                zenId != null -> itemCallbacks.onCloseZenPinnedTab(zenId)
                                isPinnedTab -> itemCallbacks.onClosePinnedTab(tab.id)
                                else -> onCloseTab(tab.id)
                            }
                        }
                        SwipeToDismissTabCard(
                            tabId = tab.id,
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
                                isActive = tab.id == state.activeTabId,
                                thumbnail = thumbnails.bitmaps[tab.id],
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
        val boundZenPinned = target.zenPinnedId?.let { id -> state.zenPinnedItems.firstOrNull { it.id == id } }
        val actions = buildList {
            if (!isPrivate) {
                when {
                    boundEssential != null -> {
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
                    }
                    boundZenPinned != null -> {
                        if (!target.url.equals(boundZenPinned.url, ignoreCase = true)) {
                            add(
                                SheetActionItem(
                                    label = "Reset to pinned URL",
                                    icon = Icons.Rounded.Refresh,
                                    onClick = { itemCallbacks.onResetZenPinnedTab(boundZenPinned.id) }
                                )
                            )
                        }
                        add(
                            SheetActionItem(
                                label = "Edit title & URL",
                                icon = Icons.Rounded.Edit,
                                onClick = { zenPinnedToEdit = boundZenPinned }
                            )
                        )
                        add(
                            SheetActionItem(
                                label = "Set custom icon",
                                icon = Icons.Rounded.Palette,
                                onClick = { zenPinnedToPickIcon = boundZenPinned }
                            )
                        )
                        add(
                            SheetActionItem(
                                label = "Add to Essentials",
                                icon = Icons.Rounded.StarOutline,
                                onClick = { itemCallbacks.onAddZenPinnedToEssentials(boundZenPinned.id) }
                            )
                        )
                    }
                    else -> {
                        if (target.isPinned) {
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
            }
            add(
                SheetActionItem(
                    label = if (target.isPinned && boundEssential == null && boundZenPinned == null) {
                        "Delete pin"
                    } else {
                        "Close tab"
                    },
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = {
                        when {
                            boundEssential != null -> itemCallbacks.onCloseEssentialTab(boundEssential.id)
                            boundZenPinned != null -> itemCallbacks.onCloseZenPinnedTab(boundZenPinned.id)
                            target.isPinned -> itemCallbacks.onDeletePinnedTab(target.id)
                            else -> onCloseTab(target.id)
                        }
                    }
                )
            )
        }
        val subtitle = buildString {
            append(target.displayHost.ifBlank { "Start Page" })
            if ((target.isPinned || boundZenPinned != null) && !isPrivate) {
                append(" · Pinned")
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
            val baseMsg = "Closes all open tabs in ${space.name}."
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

    Box(
        modifier = modifier
            .zIndex(if (isDragging || isDismissing || useDragOffset) 1f else 0f)
            .onSizeChanged { cardWidthPx = it.width }
            .pointerInput(tabId, minFlingVelocityPx, minExitSpeedPx) {
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
                                    } finally {
                                        invokeDismissOnce()
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
    thumbnail: android.graphics.Bitmap?,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foxColors = LocalSwiftfoxColors.current
    val isPinnedCard = !tab.isPrivate && (tab.isPinned || tab.zenPinnedId != null)
    val borderColor = when {
        isActive && tab.isPrivate -> foxColors.privateAccent
        isActive -> MaterialTheme.colorScheme.primary
        isPinnedCard -> MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress
            )
    ) {
        Column {
            // Card header with real favicon/custom icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 2.dp,
                        top = 6.dp,
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab.isStartPage && tab.customIcon == null) {
                    val brandAppearance = LocalSwiftfoxBrandAppearance.current
                    SwiftfoxEmblem(
                        size = 18.dp,
                        variant = if (tab.isPrivate) {
                            SwiftfoxEmblemVariant.PURPLE
                        } else {
                            brandAppearance.effectiveEmblemVariant
                        },
                        isLightBackground = !tab.isPrivate && brandAppearance.isLightChrome
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isPinnedCard) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned tab",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = tab.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = tab.displayHost.ifBlank { "Start Page" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    if (tab.isStartPage && tab.customIcon == null) {
                        val brandAppearance = LocalSwiftfoxBrandAppearance.current
                        if (!tab.isPrivate && brandAppearance.syncInApp) {
                            SwiftfoxAppIcon(
                                size = 40.dp,
                                variant = brandAppearance.effectiveAppIconVariant,
                                isLightBackground = brandAppearance.effectiveAppIconLightBackground
                            )
                        } else {
                            SwiftfoxEmblem(
                                size = 34.dp,
                                variant = if (tab.isPrivate) {
                                    SwiftfoxEmblemVariant.PURPLE
                                } else {
                                    brandAppearance.effectiveEmblemVariant
                                },
                                isLightBackground = !tab.isPrivate && brandAppearance.isLightChrome
                            )
                        }
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
                }

                if (tab.resolvedSiteMode == SiteMode.DESKTOP || tab.isPlayingMedia) {
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
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("star") }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon) },
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
