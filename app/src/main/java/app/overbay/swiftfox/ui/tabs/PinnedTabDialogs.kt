package app.overbay.swiftfox.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.components.ItemActionBottomSheet
import app.overbay.swiftfox.ui.components.SheetActionItem
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors

/**
 * Confirmation dialog shown when tapping an already-active Essential or Pinned tab a second time.
 */
@Composable
fun ClosePinnedTabConfirmationDialog(
    target: PendingPinCloseTarget,
    onDismiss: () -> Unit,
    onConfirm: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember(target.tabId) { mutableStateOf(false) }
    val kindNoun = when (target.kind) {
        PinnedTabKind.ESSENTIAL -> "Essential"
        PinnedTabKind.PINNED_TAB, PinnedTabKind.ZEN_PINNED -> "pin"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close tab?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Close the open tab for \"${target.title}\"? Your $kindNoun will stay right here and reload next time you tap it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(
                            value = dontShowAgain,
                            role = Role.Checkbox,
                            onValueChange = { dontShowAgain = it }
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null
                    )
                    Text(
                        text = "Don't show this dialog again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain) }) {
                Text("Close tab")
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
 * Section header pairing a label ("Open tabs" / "Private tabs") with an accessible "Close all"
 * pill when there are loaded Essentials/Pins or closable tabs in the current workspace.
 */
@Composable
fun WorkspaceTabsHeaderRow(
    title: String,
    showCloseAll: Boolean,
    isPrivate: Boolean,
    onClickCloseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foxColors = LocalSwiftfoxColors.current
    val titleColor = if (isPrivate) {
        foxColors.privateAccent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (showCloseAll) 48.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showCloseAll) {
            Surface(
                onClick = onClickCloseAll,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ClearAll,
                        contentDescription = null,
                        tint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Close all",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Context-aware Close All action sheet shared by the Spaces sidebar and the Tab Switcher.
 */
@Composable
fun CloseAllTabsSheet(
    state: BrowserUiState,
    onCloseLoadedPinnedAndEssentials: () -> Unit,
    onCloseOpenTabsInSpace: () -> Unit,
    onCloseEverythingInSpace: () -> Unit,
    onDismiss: () -> Unit
) {
    val foxColors = LocalSwiftfoxColors.current
    val spaceName = state.activeSpace?.name ?: "Space"
    val loadedPinnedAndEssentials = WorkspaceTabResolver.collectLoadedEssentialsAndPinsInScope(state)
    val hasClosableRegular = WorkspaceTabResolver.hasClosableRegularTabs(state)
    val regularCount = state.visibleRegularTabs.size
    val privateCount = state.tabs.count { it.isPrivate }

    val actions = if (state.isPrivateView) {
        listOf(
            SheetActionItem(
                label = "Close all private tabs ($privateCount)",
                icon = Icons.Rounded.DeleteSweep,
                isDestructive = true,
                enabled = privateCount > 0,
                onClick = onCloseOpenTabsInSpace
            )
        )
    } else {
        buildList {
            if (loadedPinnedAndEssentials.isNotEmpty()) {
                add(
                    SheetActionItem(
                        label = "Close loaded Essentials & Pins (${loadedPinnedAndEssentials.size})",
                        icon = Icons.Rounded.PushPin,
                        onClick = onCloseLoadedPinnedAndEssentials
                    )
                )
            }
            if (hasClosableRegular) {
                add(
                    SheetActionItem(
                        label = "Close open tabs in $spaceName ($regularCount)",
                        icon = Icons.Rounded.Close,
                        onClick = onCloseOpenTabsInSpace
                    )
                )
            }
            if (loadedPinnedAndEssentials.isNotEmpty() && hasClosableRegular) {
                add(
                    SheetActionItem(
                        label = "Close everything in $spaceName",
                        icon = Icons.Rounded.LayersClear,
                        isDestructive = true,
                        onClick = onCloseEverythingInSpace
                    )
                )
            }
        }
    }

    if (actions.isEmpty()) {
        onDismiss()
        return
    }

    ItemActionBottomSheet(
        title = if (state.isPrivateView) "Close Private Tabs" else "Close Tabs",
        subtitle = if (state.isPrivateView) {
            "Clears private session data and returns to $spaceName"
        } else {
            "Essentials and Pins stay saved and reload lazily on tap"
        },
        leadingIcon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (state.isPrivateView) {
                    foxColors.privateContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isPrivateView) Icons.Rounded.Shield else Icons.Rounded.LayersClear,
                        contentDescription = null,
                        tint = if (state.isPrivateView) foxColors.privateAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        actions = actions,
        onDismiss = onDismiss
    )
}
