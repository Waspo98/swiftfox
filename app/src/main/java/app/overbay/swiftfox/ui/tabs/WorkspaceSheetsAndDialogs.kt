package app.overbay.swiftfox.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.components.SpaceIconGlyph
import app.overbay.swiftfox.ui.components.parseSpaceColorHexOrNull
import app.overbay.swiftfox.ui.icons.automirrored.VolumeUp
import app.overbay.swiftfox.ui.icons.rounded.CreateNewFolder
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.DesktopWindows
import app.overbay.swiftfox.ui.icons.rounded.DriveFileMove
import app.overbay.swiftfox.ui.icons.rounded.Folder
import app.overbay.swiftfox.ui.icons.rounded.FolderOpen
import app.overbay.swiftfox.ui.icons.rounded.Tune

/**
 * Dialog for creating or renaming a pinned tab folder.
 */
@Composable
fun PinnedFolderNameDialog(
    dialogTitle: String,
    initialName: String = "",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Confirmation dialog when deleting a pinned folder. If the folder contains pinned tabs, offers
 * both "Keep pinned tabs" (moves them to top-level Pinned) and "Delete folder & pins".
 */
@Composable
fun DeletePinnedFolderDialog(
    folder: PinnedFolderEntity,
    containedTabCount: Int,
    onDismiss: () -> Unit,
    onKeepPinnedTabs: () -> Unit,
    onDeleteFolderAndPins: () -> Unit
) {
    if (containedTabCount <= 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete \"${folder.name}\"?") },
            text = {
                Text(
                    text = "Remove this empty folder from your Pinned section?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteFolderAndPins) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    val tabWord = if (containedTabCount == 1) "pinned tab" else "pinned tabs"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${folder.name}\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This folder contains $containedTabCount $tabWord. What would you like to do?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    onClick = onKeepPinnedTabs,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep pinned tabs",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Move $tabWord out to top-level Pinned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    onClick = onDeleteFolderAndPins,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete folder & pins",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Remove the folder and close all $containedTabCount $tabWord",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Material 3 bottom sheet for picking a destination folder (or removing from the current folder)
 * for a local pinned tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderBottomSheet(
    tabTitle: String,
    currentFolderId: String?,
    foldersInSpace: List<PinnedFolderEntity>,
    tabCountByFolder: Map<String, Int>,
    onMoveToFolder: (folderId: String?) -> Unit,
    onRequestNewFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Move to folder",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = tabTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (currentFolderId != null) {
                Surface(
                    onClick = {
                        onDismiss()
                        onMoveToFolder(null)
                    },
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
                            imageVector = Icons.Rounded.DriveFileMove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Remove from folder (top-level)",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            foldersInSpace.forEach { folder ->
                val isCurrent = folder.id == currentFolderId
                val count = tabCountByFolder[folder.id] ?: 0
                Surface(
                    onClick = {
                        onDismiss()
                        onMoveToFolder(folder.id)
                    },
                    enabled = !isCurrent,
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val iconSpec = folder.customIcon
                        if (iconSpec != null) {
                            SpaceIconGlyph(
                                iconKey = iconSpec,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 20.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$count ${if (count == 1) "tab" else "tabs"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Current folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = {
                    onDismiss()
                    onRequestNewFolder()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "New folder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Material 3 bottom sheet for moving any normal tab, local pinned tab, or Zen synced pinned item
 * to another Space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToSpaceBottomSheet(
    tabTitle: String,
    currentSpaceId: String,
    spaces: List<SpaceEntity>,
    onSelectSpace: (targetSpaceId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val destinations = remember(spaces, currentSpaceId) {
        spaces.filter { it.id != currentSpaceId }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Move to Space",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = tabTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            destinations.forEach { space ->
                val themeDot = parseSpaceColorHexOrNull(space.themeColorHex)
                Surface(
                    onClick = {
                        onDismiss()
                        onSelectSpace(space.id)
                    },
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
                        SpaceIconGlyph(
                            iconKey = space.iconKey,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 20.dp
                        )
                        Text(
                            text = space.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (themeDot != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(themeDot)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class RailFolderQuickPickItem(
    val id: String,
    val title: String,
    val url: String,
    val subtitle: String,
    val customIcon: String?,
    val isLoaded: Boolean,
    val isActive: Boolean,
    val isDesktopMode: Boolean = false,
    val isPlayingMedia: Boolean = false
)

/**
 * Quick-pick bottom sheet shown when tapping a folder tile in the collapsed 72dp sidebar rail.
 * Lets the user open or manage pinned tabs inside that folder without expanding the full sidebar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollapsedRailFolderQuickPickSheet(
    folderName: String,
    folderCustomIcon: String?,
    isSyncedZenFolder: Boolean,
    items: List<RailFolderQuickPickItem>,
    onSelectItem: (String) -> Unit,
    onLongPressItem: (String) -> Unit,
    onCloseLoadedItem: ((String) -> Unit)? = null,
    onManageFolder: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (folderCustomIcon != null) {
                            SpaceIconGlyph(
                                iconKey = folderCustomIcon,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${items.size} ${if (items.size == 1) "pinned tab" else "pinned tabs"}")
                            if (isSyncedZenFolder) append(" · Synced")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onManageFolder != null) {
                    IconButton(
                        onClick = {
                            onDismiss()
                            onManageFolder()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Manage $folderName",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No pinned tabs in this folder yet. Long-press any pinned tab to move it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items.forEach { item ->
                    val containerColor = when {
                        item.isActive -> MaterialTheme.colorScheme.primaryContainer
                        item.isLoaded -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
                    val border = when {
                        item.isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        item.isLoaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        border = border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    onDismiss()
                                    onSelectItem(item.id)
                                },
                                onLongClick = {
                                    onDismiss()
                                    onLongPressItem(item.id)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = if (item.isLoaded && onCloseLoadedItem != null) 4.dp else 14.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SiteIconBadge(
                                url = item.url,
                                fallbackLabel = item.title,
                                customIcon = item.customIcon,
                                size = 30.dp,
                                iconSize = 18.dp,
                                shape = RoundedCornerShape(8.dp),
                                containerColor = if (item.isActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                fillImageToContainer = true,
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (item.isLoaded || item.isActive) 1f else 0.7f
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (item.isLoaded && item.isDesktopMode) {
                                            Icon(
                                                imageVector = Icons.Rounded.DesktopWindows,
                                                contentDescription = "Desktop mode",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        if (item.isLoaded && item.isPlayingMedia) {
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
                            if (item.isLoaded) {
                                Box(
                                    modifier = Modifier
                                        .size(if (item.isActive) 8.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (item.isLoaded && onCloseLoadedItem != null) {
                                IconButton(
                                    onClick = { onCloseLoadedItem(item.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close ${item.title}",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
