package app.overbay.swiftfox.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.TabThumbnailsSnapshot
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import app.overbay.swiftfox.ui.theme.firstDisplaySymbol
import app.overbay.swiftfox.ui.theme.spaceIconFor

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
    onOpenEssential: (String) -> Unit,
    onOpenSyncedTabs: () -> Unit = {},
    onOpenZenPinnedUrl: (String) -> Unit = onOpenEssential
) {
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var spaceToDelete by remember { mutableStateOf<SpaceEntity?>(null) }
    val foxColors = LocalSwiftfoxColors.current

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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    text = if (state.isPrivateView) "Private Tabs" else (state.activeSpace?.name ?: "Spaces"),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    onClick = onOpenSyncedTabs,
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.syncedRemoteTabs.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
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
                            modifier = Modifier.size(18.dp)
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

                FilterChip(
                    selected = state.isPrivateView,
                    onClick = { onTogglePrivate(!state.isPrivateView) },
                    label = { Text("Private") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = foxColors.privateContainer,
                        selectedLabelColor = foxColors.onPrivateContainer,
                        selectedLeadingIconColor = foxColors.privateAccent
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            // Spaces pill strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = state.spaces,
                    key = { it.id },
                    contentType = { "space_pill" }
                ) { space ->
                    val selected = !state.isPrivateView && state.activeSpaceId == space.id
                    SpacePillChip(
                        space = space,
                        tabCount = state.tabs.count { !it.isPrivate && it.spaceId == space.id },
                        selected = selected,
                        onClick = { onSelectSpace(space.id) },
                        onLongClick = if (state.spaces.size > 1) {
                            { spaceToDelete = space }
                        } else {
                            null
                        }
                    )
                }

                item(key = "add_space", contentType = "add_space_pill") {
                    Surface(
                        onClick = { showCreateSpaceDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Create Space",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Space", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Pinned Essentials compact strip
            val essentials = state.activeSpaceEssentials
            if (!state.isPrivateView && essentials.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = essentials,
                        key = { it.id },
                        contentType = { "essential_compact" }
                    ) { essential ->
                        CompactEssentialPill(
                            essential = essential,
                            onClick = { onOpenEssential(essential.url) }
                        )
                    }
                }
            }

            // Zen Browser synced pinned items for active Space
            val zenPinned = state.activeSpaceZenPinned
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
                        CompactZenPinnedPill(
                            item = item,
                            onClick = { onOpenZenPinnedUrl(item.url) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Visual tab card grid
            val tabs = state.visibleTabs
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
                        TabPreviewCard(
                            tab = tab,
                            isActive = tab.id == state.activeTabId,
                            thumbnail = thumbnails.bitmaps[tab.id],
                            onSelect = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) }
                        )
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
private fun SpacePillChip(
    space: SpaceEntity,
    tabCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
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
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = space.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (!space.containerProfileName.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Isolated container",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = tabCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactEssentialPill(
    essential: EssentialEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstDisplaySymbol(essential.title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = essential.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactZenPinnedPill(
    item: ZenPinnedItemEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (item.isPinned) Icons.Rounded.PushPin else Icons.Rounded.Tab,
                contentDescription = if (item.isPinned) "Pinned item" else "Remote tab",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (!item.folderName.isNullOrBlank()) "${item.folderName} · ${item.title}" else item.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or rgb).toInt())
}

@Composable
private fun TabPreviewCard(
    tab: BrowserTabUi,
    isActive: Boolean,
    thumbnail: android.graphics.Bitmap?,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val foxColors = LocalSwiftfoxColors.current
    val borderColor = when {
        isActive && tab.isPrivate -> foxColors.privateAccent
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor)
    ) {
        Column {
            // Card header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tab.title.ifBlank { "New Tab" },
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
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close ${tab.title}",
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
                    if (tab.isStartPage) {
                        SwiftfoxEmblem(
                            size = 34.dp,
                            variant = if (tab.isPrivate) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.displayHost.take(1).uppercase().ifBlank { "S" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("star") }
    var isolatedContainer by remember { mutableStateOf(false) }

    val iconOptions = listOf("home", "work", "book", "code", "shop", "star")

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
