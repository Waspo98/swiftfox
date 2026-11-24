package app.overbay.swiftfox.ui.tabs

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
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.EmphasizedDecelerateEasing
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import app.overbay.swiftfox.ui.theme.firstDisplaySymbol
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import app.overbay.swiftfox.ui.theme.spaceIconFor

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
    modifier: Modifier = Modifier
) {
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }
    val reduceMotion = rememberReduceMotion()
    val isEffectiveExpanded = isOverlayDrawer || expanded

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
        Column(
            modifier = innerColumnModifier
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                .statusBarsPadding()
                .then(if (!bottomBarPresent) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    horizontal = when {
                        isOverlayDrawer -> 14.dp
                        isEffectiveExpanded -> 12.dp
                        else -> 8.dp
                    },
                    vertical = if (isOverlayDrawer) 12.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isEffectiveExpanded) {
                ArcExpandedSidebarContent(
                    state = state,
                    isOverlayDrawer = isOverlayDrawer,
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
                    onRequestDeleteSpace = { spaceToDelete = it }
                )
            } else {
                ArcCollapsedSidebarRail(
                    state = state,
                    onToggleExpanded = onToggleExpanded,
                    onSelectSpace = onSelectSpace,
                    onTogglePrivate = onTogglePrivate,
                    onSelectTab = onSelectTab,
                    onCreateTab = onCreateTab,
                    onCreatePrivateTab = onCreatePrivateTab
                )
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
}

@Composable
private fun ColumnScope.ArcExpandedSidebarContent(
    state: BrowserUiState,
    isOverlayDrawer: Boolean,
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
    onRequestDeleteSpace: (SpaceEntity) -> Unit
) {
    val foxColors = LocalSwiftfoxColors.current
    val emblemVariant = if (state.isPrivateView) {
        SwiftfoxEmblemVariant.PURPLE
    } else {
        SwiftfoxEmblemVariant.ORANGE
    }
    val activeSpace = state.activeSpace

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

    // 2. Scrollable Essentials Grid + Zen pinned items + open tabs + New Tab row
    // Keeping Essentials inside LazyColumn ensures large Zen-synced grids or landscape viewports
    // never squeeze the open tabs list or New Tab action out of view.
    val essentials = state.activeSpaceEssentials
    val zenPinned = state.activeSpaceZenPinned
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!state.isPrivateView && essentials.isNotEmpty()) {
            item(key = "sidebar_essentials_grid", contentType = "sidebar_essentials_grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    essentials.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { ess ->
                                SidebarEssentialTile(
                                    essential = ess,
                                    onClick = { onOpenEssential(ess.url) },
                                    modifier = Modifier.weight(1f)
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

        if (!state.isPrivateView && zenPinned.isNotEmpty()) {
            item(key = "zen_pinned_header", contentType = "sidebar_section_header") {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
            items(
                items = zenPinned,
                key = { "zen_${it.id}" },
                contentType = { "sidebar_zen_pinned_row" }
            ) { item ->
                SidebarZenPinnedRow(
                    item = item,
                    onClick = { onOpenEssential(item.url) }
                )
            }
            item(key = "zen_pinned_divider", contentType = "sidebar_divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }

        items(
            items = state.visibleTabs,
            key = { it.id },
            contentType = { "sidebar_tab_row" }
        ) { tab ->
            SidebarTabRow(
                tab = tab,
                isActive = tab.id == state.activeTabId,
                onSelect = { onSelectTab(tab.id) },
                onClose = { onCloseTab(tab.id) }
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

    // 4. Bottom Space Switcher & Private Toggle pinned at bottom in thumb reach
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            items = state.spaces,
            key = { it.id },
            contentType = { "sidebar_bottom_space" }
        ) { space ->
            val selected = !state.isPrivateView && state.activeSpaceId == space.id
            SidebarBottomSpaceChip(
                space = space,
                selected = selected,
                onClick = { onSelectSpace(space.id) },
                onLongClick = if (state.spaces.size > 1) {
                    { onRequestDeleteSpace(space) }
                } else {
                    null
                }
            )
        }

        item(key = "sidebar_bottom_private", contentType = "sidebar_bottom_private") {
            Surface(
                onClick = { onTogglePrivate(!state.isPrivateView) },
                shape = RoundedCornerShape(20.dp),
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
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Private Space",
                        tint = if (state.isPrivateView) foxColors.privateAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Private",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isPrivateView) foxColors.onPrivateContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
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

@Composable
private fun SidebarEssentialTile(
    essential: EssentialEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = modifier
            .heightIn(min = 52.dp)
            .semantics {
                contentDescription = "Open ${essential.title} (${essential.domain})"
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = firstDisplaySymbol(essential.title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = essential.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SidebarZenPinnedRow(
    item: ZenPinnedItemEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (item.isPinned) Icons.Rounded.PushPin else Icons.Rounded.Tab,
                contentDescription = if (item.isPinned) "Pinned item" else "Remote tab",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.folderName.isNullOrBlank()) {
                    Text(
                        text = item.folderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarTabRow(
    tab: BrowserTabUi,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
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
        isActive && isPrivate -> BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.65f))
        isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        else -> null
    }
    val badgeBg = when {
        isPrivate -> foxColors.privateContainer
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val badgeTint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                if (tab.isStartPage) {
                    SwiftfoxEmblem(
                        size = 16.dp,
                        variant = if (isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
                    )
                } else {
                    Text(
                        text = firstDisplaySymbol(tab.displayHost, fallback = "S"),
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeTint
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.title.ifBlank { "New Tab" },
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
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close ${tab.title}",
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarBottomSpaceChip(
    space: SpaceEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val dotColor = parseHexColorOrNull(space.themeColorHex)
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Icon(
                imageVector = spaceIconFor(space.iconKey),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = space.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ColumnScope.ArcCollapsedSidebarRail(
    state: BrowserUiState,
    onToggleExpanded: () -> Unit,
    onSelectSpace: (String) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit
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
                onClick = { onSelectSpace(space.id) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = spaceIconFor(space.iconKey),
                        contentDescription = space.name,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
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
                onClick = { onSelectTab(tab.id) },
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isActive && tab.isPrivate -> foxColors.privateContainer
                    isActive -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Switch to ${tab.title.ifBlank { "New Tab" }} (${tab.displayHost.ifBlank { "Start Page" }})"
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = firstDisplaySymbol(tab.displayHost.ifBlank { tab.title }, fallback = "S"),
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            isActive && tab.isPrivate -> foxColors.privateAccent
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
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
