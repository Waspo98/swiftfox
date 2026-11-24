package app.overbay.swiftfox.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
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
import app.overbay.swiftfox.ui.tabs.DeleteSpaceDialog
import app.overbay.swiftfox.ui.tabs.WorkspaceItemCallbacks
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.SwiftfoxBrandLockup
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPageContent(
    state: BrowserUiState,
    onFocusOmnibox: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSelectSpace: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onAddEssential: (String, String) -> Unit,
    onRemoveEssential: (String) -> Unit,
    onFocusOmniboxFromRect: (Rect?) -> Unit = { onFocusOmnibox() },
    itemCallbacks: WorkspaceItemCallbacks = WorkspaceItemCallbacks(
        onAddEssential = onAddEssential,
        onRemoveEssential = onRemoveEssential
    ),
    addBottomNavInset: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showAddEssentialDialog by remember { mutableStateOf(false) }
    var isReorderingEssentials by remember { mutableStateOf(false) }
    var isReorderingSpaces by remember { mutableStateOf(false) }
    var searchCapsuleRect by remember { mutableStateOf<Rect?>(null) }

    var actionEssential by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToEdit by remember { mutableStateOf<EssentialEntity?>(null) }
    var essentialToPickIcon by remember { mutableStateOf<EssentialEntity?>(null) }

    var actionSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToRename by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToPickIcon by remember { mutableStateOf<SpaceEntity?>(null) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }

    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = state.activeTab?.isPrivate == true || state.isPrivateView
    val isUnfolded = state.windowState.posture == DevicePosture.UNFOLDED

    val essentials = state.activeSpaceEssentials
    val essentialsReorder = rememberDragReorderController(
        sourceItems = essentials,
        keyOf = { it.id },
        axis = DragAxis.BOTH,
        onCommitOrder = itemCallbacks.onReorderEssentials
    )
    val spacesReorder = rememberDragReorderController(
        sourceItems = state.spaces,
        keyOf = { it.id },
        axis = DragAxis.HORIZONTAL,
        onCommitOrder = itemCallbacks.onReorderSpaces
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
                    enabled = !isReorderingEssentials
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand mark & wordmark
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isUnfolded) 12.dp else 6.dp),
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

            // Primary Search Capsule (records bounds for smooth Omnibox morph expand)
            Surface(
                onClick = { onFocusOmniboxFromRect(searchCapsuleRect) },
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
                    .heightIn(min = 56.dp)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            searchCapsuleRect = coords.boundsInRoot()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPrivate) {
                            foxColors.privateContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Search ${state.settings.searchEngine.label} or type URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Spaces switcher pills (normal mode, hidden when unfolded sidebar already shows Spaces)
            val sidebarVisible = isUnfolded && state.activeProfile.showSpacesSidebar
            if (!isPrivate && !sidebarVisible && state.spaces.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReorderModeBanner(
                        visible = isReorderingSpaces,
                        label = "Drag workspaces to reorder",
                        onDone = { isReorderingSpaces = false }
                    )
                    val displayedSpaces = if (isReorderingSpaces) spacesReorder.items.toList() else state.spaces
                    LazyRow(
                        userScrollEnabled = !isReorderingSpaces,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = displayedSpaces,
                            key = { it.id },
                            contentType = { "start_space_pill" }
                        ) { space ->
                            val selected = space.id == state.activeSpaceId
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                border = when {
                                    isReorderingSpaces -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    selected -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                                    else -> null
                                },
                                modifier = Modifier
                                    .then(if (!isReorderingSpaces) Modifier.animateItem() else Modifier)
                                    .dragReorderItem(
                                        key = space.id,
                                        enabled = isReorderingSpaces,
                                        controller = spacesReorder
                                    )
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .then(
                                        if (!isReorderingSpaces) {
                                            Modifier.combinedClickable(
                                                onClick = { onSelectSpace(space.id) },
                                                onLongClick = { actionSpace = space }
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SpaceIconGlyph(
                                        iconKey = space.iconKey,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        size = 16.dp
                                    )
                                    Text(
                                        text = space.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Essentials pinned grid with real web favicons, long-press action menu, and 2D drag-and-drop reorder
            if (!isPrivate) {
                val displayedEssentials = if (isReorderingEssentials) essentialsReorder.items.toList() else essentials
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Essentials",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    ReorderModeBanner(
                        visible = isReorderingEssentials,
                        label = "Drag tiles to reorder",
                        onDone = { isReorderingEssentials = false }
                    )

                    val totalEssentials = displayedEssentials.size + if (isReorderingEssentials) 0 else 1
                    val columns = when {
                        !isUnfolded -> 4
                        totalEssentials <= 4 -> totalEssentials.coerceAtLeast(1)
                        totalEssentials <= 8 -> 4
                        else -> 5
                    }
                    val gridEntries: List<EssentialEntity?> = if (isReorderingEssentials) {
                        displayedEssentials
                    } else {
                        displayedEssentials + null
                    }
                    val rows = gridEntries.chunked(columns)

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    if (item != null) {
                                        EssentialTile(
                                            essential = item,
                                            isReorderMode = isReorderingEssentials,
                                            onClick = { if (!isReorderingEssentials) onOpenUrl(item.url) },
                                            onLongClick = { if (!isReorderingEssentials) actionEssential = item },
                                            modifier = Modifier
                                                .weight(1f)
                                                .dragReorderItem(
                                                    key = item.id,
                                                    enabled = isReorderingEssentials,
                                                    controller = essentialsReorder
                                                )
                                        )
                                    } else {
                                        AddEssentialTile(
                                            onClick = { showAddEssentialDialog = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Active open tabs / recent jump cards with web favicons
            val expandedSidebarVisible = isUnfolded && state.activeProfile.showSpacesSidebar && state.isUnfoldedRailExpanded
            val jumpTabs = if (expandedSidebarVisible) {
                emptyList()
            } else {
                state.visibleTabs.filter { !it.isStartPage && it.id != state.activeTabId }.take(4)
            }
            val recentBookmarks = state.bookmarks.take(4)
            val recentHistory = state.history.take(4)

            if (!isPrivate && (jumpTabs.isNotEmpty() || recentBookmarks.isNotEmpty() || recentHistory.isNotEmpty())) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    val cards: List<StartJumpItem> = when {
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
                    label = "Reorder",
                    icon = Icons.Rounded.SwapVert,
                    enabled = essentials.size > 1,
                    onClick = { isReorderingEssentials = true }
                ),
                SheetActionItem(
                    label = "Delete",
                    icon = Icons.Rounded.DeleteOutline,
                    isDestructive = true,
                    onClick = { onRemoveEssential(target.id) }
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

    actionSpace?.let { target ->
        ItemActionBottomSheet(
            title = target.name,
            subtitle = "Workspace",
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
                    enabled = state.spaces.size > 1,
                    onClick = { isReorderingSpaces = true }
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
private fun EssentialTile(
    essential: EssentialEntity,
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = if (isReorderMode) 1.5.dp else 1.dp,
                color = if (isReorderMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                }
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                SiteIconBadge(
                    url = essential.url,
                    fallbackLabel = essential.title,
                    customIcon = essential.customIcon,
                    size = 56.dp,
                    iconSize = 30.dp,
                    shape = RoundedCornerShape(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    monogramFontSize = 22.sp,
                    fillImageToContainer = true
                )
            }
        }
        Text(
            text = essential.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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
            maxLines = 1
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
