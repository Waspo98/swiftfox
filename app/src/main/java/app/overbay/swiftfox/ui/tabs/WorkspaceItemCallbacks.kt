package app.overbay.swiftfox.ui.tabs

import androidx.compose.runtime.Immutable

/**
 * Immutable callback bundle for long-press editing, pinning, custom icon selection,
 * and drag-and-drop reordering across Workspaces, Essentials, Pinned Tabs, and Open Tabs.
 */
@Immutable
data class WorkspaceItemCallbacks(
    val onRenameSpace: (spaceId: String, newName: String) -> Unit = { _, _ -> },
    val onSetSpaceIcon: (spaceId: String, iconKey: String) -> Unit = { _, _ -> },
    val onReorderSpaces: (orderedIds: List<String>) -> Unit = {},
    val onDeleteSpace: (spaceId: String) -> Unit = {},
    val onAddEssential: (title: String, url: String) -> Unit = { _, _ -> },
    val onUpdateEssential: (id: String, title: String, url: String) -> Unit = { _, _, _ -> },
    val onSetEssentialCustomIcon: (id: String, customIcon: String?) -> Unit = { _, _ -> },
    val onReorderEssentials: (orderedIds: List<String>) -> Unit = {},
    val onRemoveEssential: (id: String) -> Unit = {},
    val onActivateEssential: (essentialId: String) -> Unit = {},
    val onCloseEssentialTab: (essentialId: String) -> Unit = {},
    val onResetEssentialTab: (essentialId: String) -> Unit = {},
    val onSetTabPinned: (tabId: String, pinned: Boolean) -> Unit = { _, _ -> },
    val onActivatePinnedTab: (tabId: String) -> Unit = {},
    val onClosePinnedTab: (tabId: String) -> Unit = {},
    val onResetPinnedTab: (tabId: String) -> Unit = {},
    val onDeletePinnedTab: (tabId: String) -> Unit = {},
    val onEditTabTitleAndUrl: (tabId: String, title: String, url: String) -> Unit = { _, _, _ -> },
    val onSetTabCustomIcon: (tabId: String, customIcon: String?) -> Unit = { _, _ -> },
    val onAddTabToEssentials: (tabId: String) -> Unit = {},
    val onReorderTabs: (orderedIds: List<String>, pinnedSection: Boolean) -> Unit = { _, _ -> },
    val onUpdateZenPinned: (id: String, title: String, url: String) -> Unit = { _, _, _ -> },
    val onSetZenPinnedCustomIcon: (id: String, customIcon: String?) -> Unit = { _, _ -> },
    val onAddZenPinnedToEssentials: (id: String) -> Unit = {},
    val onDeleteZenPinned: (id: String) -> Unit = {},
    val onReorderZenPinned: (orderedIds: List<String>) -> Unit = {},
    val onActivateZenPinned: (zenPinnedId: String) -> Unit = {},
    val onCloseZenPinnedTab: (zenPinnedId: String) -> Unit = {},
    val onResetZenPinnedTab: (zenPinnedId: String) -> Unit = {},
    val onCloseAllLoadedPinnedAndEssentials: () -> Unit = {},
    val onCloseAllOpenTabsInSpace: () -> Unit = {},
    val onCloseEverythingInSpace: () -> Unit = {}
)
