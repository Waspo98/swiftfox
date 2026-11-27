package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import java.util.UUID

sealed interface SidebarPinnedEntry {
    val entryKey: String
    val sortOrder: Int

    data class FolderGroup(
        val folder: PinnedFolderEntity,
        val tabs: List<BrowserTabUi>
    ) : SidebarPinnedEntry {
        override val entryKey: String = folderEntryKey(folder.id)
        override val sortOrder: Int = folder.sortOrder
    }

    data class StandalonePin(
        val tab: BrowserTabUi
    ) : SidebarPinnedEntry {
        override val entryKey: String = tabEntryKey(tab.id)
        override val sortOrder: Int = tab.sortOrder
    }

    companion object {
        fun folderEntryKey(folderId: String): String = "folder:$folderId"
        fun tabEntryKey(tabId: String): String = "tab:$tabId"
    }
}

sealed interface SidebarZenPinnedEntry {
    val entryKey: String

    data class FolderGroup(
        val folderKey: String,
        val folderName: String,
        val items: List<ZenPinnedItemEntity>
    ) : SidebarZenPinnedEntry {
        override val entryKey: String = "zen_folder:$folderKey"
    }

    data class StandaloneItem(
        val item: ZenPinnedItemEntity
    ) : SidebarZenPinnedEntry {
        override val entryKey: String = "zen_item:${item.id}"
    }
}

data class NormalizedSpacePinnedOrder(
    val folders: List<PinnedFolderEntity>,
    val tabs: List<BrowserTabUi>,
    val updatedFolderIds: Set<String>,
    val updatedTabIds: Set<String>
)

data class MoveTabResult(
    val updatedTabs: List<BrowserTabUi>,
    val updatedFolders: List<PinnedFolderEntity>,
    val movedTab: BrowserTabUi,
    val followedToTargetSpace: Boolean,
    val nextActiveSpaceId: String,
    val nextActiveTabId: String?
)

data class MoveZenPinnedResult(
    val updatedZenItems: List<ZenPinnedItemEntity>,
    val updatedTabs: List<BrowserTabUi>,
    val createdOrUpdatedLocalTab: BrowserTabUi,
    val followedToTargetSpace: Boolean,
    val nextActiveSpaceId: String,
    val nextActiveTabId: String?
)

data class DeleteFolderResult(
    val updatedFolders: List<PinnedFolderEntity>,
    val updatedTabs: List<BrowserTabUi>,
    val removedTabs: List<BrowserTabUi>
)

/**
 * Pure layout, ordering, folder-management, and cross-Space move helpers for Pinned Tabs and
 * Workspaces so state transitions remain deterministic and unit-testable outside Android Context.
 */
object WorkspacePinnedFolderLogic {

    fun buildLocalPinnedDisplayList(
        folders: List<PinnedFolderEntity>,
        pinnedTabs: List<BrowserTabUi>,
        spaceId: String
    ): List<SidebarPinnedEntry> {
        val spaceFolders = folders.filter { it.spaceId == spaceId }
        val folderIds = spaceFolders.mapTo(mutableSetOf()) { it.id }
        val spacePins = pinnedTabs.filter {
            !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab && it.spaceId == spaceId
        }
        val groupedByFolder = spacePins
            .filter { it.folderId != null && it.folderId in folderIds }
            .groupBy { it.folderId!! }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy<BrowserTabUi> { it.sortOrder }.thenBy { it.id })
            }

        val entries = buildList<SidebarPinnedEntry> {
            spaceFolders.forEach { folder ->
                add(SidebarPinnedEntry.FolderGroup(folder, groupedByFolder[folder.id].orEmpty()))
            }
            spacePins
                .filter { it.folderId == null || it.folderId !in folderIds }
                .forEach { tab ->
                    add(SidebarPinnedEntry.StandalonePin(tab))
                }
        }
        return entries.sortedWith(
            compareBy<SidebarPinnedEntry> { it.sortOrder }
                .thenByDescending { it is SidebarPinnedEntry.FolderGroup }
                .thenBy { it.entryKey }
        )
    }

    fun buildZenPinnedDisplayList(
        zenPinnedItems: List<ZenPinnedItemEntity>,
        spaceId: String
    ): List<SidebarZenPinnedEntry> {
        if (zenPinnedItems.isEmpty()) return emptyList()
        val groupedItemsByName = linkedMapOf<String, MutableList<ZenPinnedItemEntity>>()
        val orderedTokens = mutableListOf<Any>() // either String (folderName) or ZenPinnedItemEntity
        for (item in zenPinnedItems) {
            val trimmedFolder = item.folderName?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmedFolder == null) {
                orderedTokens += item
            } else {
                val bucket = groupedItemsByName[trimmedFolder]
                if (bucket == null) {
                    groupedItemsByName[trimmedFolder] = mutableListOf(item)
                    orderedTokens += trimmedFolder
                } else {
                    bucket += item
                }
            }
        }
        return orderedTokens.map { token ->
            when (token) {
                is String -> SidebarZenPinnedEntry.FolderGroup(
                    folderKey = BrowserUiState.zenFolderKey(spaceId, token),
                    folderName = token,
                    items = groupedItemsByName.getValue(token)
                )
                is ZenPinnedItemEntity -> SidebarZenPinnedEntry.StandaloneItem(token)
                else -> error("Unexpected token")
            }
        }
    }

    fun nextTopLevelSortOrder(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        spaceId: String
    ): Int {
        val entries = buildLocalPinnedDisplayList(folders, tabs, spaceId)
        return if (entries.isEmpty()) 0 else (entries.maxOf { it.sortOrder } + 1).coerceAtLeast(entries.size)
    }

    fun nextFolderTabSortOrder(
        tabs: List<BrowserTabUi>,
        spaceId: String,
        folderId: String
    ): Int {
        val grouped = tabs.filter {
            !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab &&
                it.spaceId == spaceId && it.folderId == folderId
        }
        return if (grouped.isEmpty()) 0 else (grouped.maxOf { it.sortOrder } + 1).coerceAtLeast(grouped.size)
    }

    fun computeLocalPinnedOrderIndex(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        target: BrowserTabUi
    ): Int {
        val validFolderIds = folders
            .asSequence()
            .filter { it.spaceId == target.spaceId }
            .mapTo(mutableSetOf()) { it.id }
        val resolvedFolderId = target.folderId?.takeIf { it in validFolderIds }
        return if (resolvedFolderId != null) {
            tabs.filter {
                !it.isPrivate && !it.isBoundWorkspaceTab && it.isPinned &&
                    it.spaceId == target.spaceId && it.folderId == resolvedFolderId
            }.sortedWith(compareBy<BrowserTabUi> { it.sortOrder }.thenBy { it.id })
                .indexOfFirst { it.id == target.id }
                .coerceAtLeast(0)
        } else {
            val key = SidebarPinnedEntry.tabEntryKey(target.id)
            buildLocalPinnedDisplayList(folders, tabs, target.spaceId)
                .indexOfFirst { it.entryKey == key }
                .coerceAtLeast(0)
        }
    }

    fun normalizeSpacePinnedOrders(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        spaceId: String
    ): NormalizedSpacePinnedOrder {
        val topLevel = buildLocalPinnedDisplayList(folders, tabs, spaceId)
        val topOrderByFolderId = HashMap<String, Int>()
        val topOrderByTabId = HashMap<String, Int>()
        val folderChildOrderByTabId = HashMap<String, Int>()

        topLevel.forEachIndexed { idx, entry ->
            when (entry) {
                is SidebarPinnedEntry.FolderGroup -> {
                    topOrderByFolderId[entry.folder.id] = idx
                    entry.tabs.forEachIndexed { childIdx, child ->
                        folderChildOrderByTabId[child.id] = childIdx
                    }
                }
                is SidebarPinnedEntry.StandalonePin -> {
                    topOrderByTabId[entry.tab.id] = idx
                }
            }
        }

        val updatedFolderIds = mutableSetOf<String>()
        val nextFolders = folders.map { folder ->
            if (folder.spaceId != spaceId) {
                folder
            } else {
                val targetOrder = topOrderByFolderId[folder.id] ?: folder.sortOrder
                if (folder.sortOrder != targetOrder) {
                    updatedFolderIds += folder.id
                    folder.copy(sortOrder = targetOrder)
                } else {
                    folder
                }
            }
        }

        val validFolderIds = nextFolders.asSequence()
            .filter { it.spaceId == spaceId }
            .mapTo(mutableSetOf()) { it.id }

        val updatedTabIds = mutableSetOf<String>()
        val nextTabs = tabs.map { tab ->
            if (tab.isPrivate || !tab.isPinned || tab.isBoundWorkspaceTab || tab.spaceId != spaceId) {
                tab
            } else {
                val resolvedFolderId = tab.folderId?.takeIf { it in validFolderIds }
                val targetOrder = if (resolvedFolderId != null) {
                    folderChildOrderByTabId[tab.id] ?: tab.sortOrder
                } else {
                    topOrderByTabId[tab.id] ?: tab.sortOrder
                }
                if (tab.folderId != resolvedFolderId || tab.sortOrder != targetOrder) {
                    updatedTabIds += tab.id
                    tab.copy(folderId = resolvedFolderId, sortOrder = targetOrder)
                } else {
                    tab
                }
            }
        }

        return NormalizedSpacePinnedOrder(
            folders = nextFolders,
            tabs = nextTabs,
            updatedFolderIds = updatedFolderIds,
            updatedTabIds = updatedTabIds
        )
    }

    fun applyCreatePinnedFolder(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        spaceId: String,
        folderName: String,
        initialTabId: String? = null,
        folderId: String = "pf_${UUID.randomUUID().toString().take(8)}",
        nowMs: Long = System.currentTimeMillis()
    ): Pair<PinnedFolderEntity, NormalizedSpacePinnedOrder>? {
        val cleanName = folderName.trim()
        if (cleanName.isEmpty()) return null
        val initialTab = initialTabId?.let { id ->
            tabs.firstOrNull {
                it.id == id && !it.isPrivate && !it.isBoundWorkspaceTab && it.isPinned && it.spaceId == spaceId
            }
        }
        val currentTopLevel = buildLocalPinnedDisplayList(folders, tabs, spaceId)
        val initialTopIndex = if (initialTab != null && initialTab.folderId == null) {
            currentTopLevel.indexOfFirst { it.entryKey == SidebarPinnedEntry.tabEntryKey(initialTab.id) }
        } else {
            -1
        }
        val newFolderOrder = if (initialTopIndex >= 0) {
            initialTopIndex
        } else {
            nextTopLevelSortOrder(folders, tabs, spaceId)
        }
        val newFolder = PinnedFolderEntity(
            id = folderId,
            spaceId = spaceId,
            name = cleanName,
            customIcon = null,
            isExpanded = true,
            sortOrder = newFolderOrder,
            createdAt = nowMs
        )
        // Shift top-level entries at or after initialTopIndex when replacing an ungrouped pin's slot
        val preShiftedFolders = if (initialTopIndex >= 0) {
            folders.map { f ->
                if (f.spaceId == spaceId && f.sortOrder >= newFolderOrder) f.copy(sortOrder = f.sortOrder + 1) else f
            } + newFolder
        } else {
            folders + newFolder
        }
        val preShiftedTabs = tabs.map { tab ->
            when {
                tab.id == initialTab?.id -> tab.copy(folderId = folderId, sortOrder = 0)
                initialTopIndex >= 0 && !tab.isPrivate && tab.isPinned && !tab.isBoundWorkspaceTab &&
                    tab.spaceId == spaceId && tab.folderId == null && tab.sortOrder >= newFolderOrder ->
                    tab.copy(sortOrder = tab.sortOrder + 1)
                else -> tab
            }
        }
        val normalized = normalizeSpacePinnedOrders(preShiftedFolders, preShiftedTabs, spaceId)
        val finalFolder = normalized.folders.first { it.id == folderId }
        return finalFolder to normalized
    }

    fun applyMovePinnedTabToFolder(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        tabId: String,
        targetFolderId: String?
    ): NormalizedSpacePinnedOrder? {
        val target = tabs.firstOrNull {
            it.id == tabId && !it.isPrivate && !it.isBoundWorkspaceTab && it.isPinned
        } ?: return null
        val spaceId = target.spaceId
        if (targetFolderId != null && folders.none { it.id == targetFolderId && it.spaceId == spaceId }) {
            return null
        }
        if (target.folderId == targetFolderId) return null

        val nextOrder = if (targetFolderId == null) {
            nextTopLevelSortOrder(folders, tabs, spaceId)
        } else {
            nextFolderTabSortOrder(tabs, spaceId, targetFolderId)
        }
        val nextFolders = if (targetFolderId != null) {
            folders.map { f -> if (f.id == targetFolderId && !f.isExpanded) f.copy(isExpanded = true) else f }
        } else {
            folders
        }
        val nextTabs = tabs.map { tab ->
            if (tab.id == tabId) tab.copy(folderId = targetFolderId, sortOrder = nextOrder) else tab
        }
        return normalizeSpacePinnedOrders(nextFolders, nextTabs, spaceId)
    }

    fun applyReorderTopLevelPinned(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        spaceId: String,
        orderedEntryKeys: List<String>
    ): NormalizedSpacePinnedOrder {
        val currentTopLevel = buildLocalPinnedDisplayList(folders, tabs, spaceId)
        val entryByKey = currentTopLevel.associateBy { it.entryKey }
        val seen = mutableSetOf<String>()
        val reorderedEntries = buildList {
            for (key in orderedEntryKeys) {
                val entry = entryByKey[key]
                if (entry != null && seen.add(key)) add(entry)
            }
            for (entry in currentTopLevel) {
                if (seen.add(entry.entryKey)) add(entry)
            }
        }

        val topOrderByFolderId = HashMap<String, Int>()
        val topOrderByTabId = HashMap<String, Int>()
        val orderedStandaloneTabIds = mutableListOf<String>()
        reorderedEntries.forEachIndexed { idx, entry ->
            when (entry) {
                is SidebarPinnedEntry.FolderGroup -> topOrderByFolderId[entry.folder.id] = idx
                is SidebarPinnedEntry.StandalonePin -> {
                    topOrderByTabId[entry.tab.id] = idx
                    orderedStandaloneTabIds += entry.tab.id
                }
            }
        }

        val updatedFolderIds = mutableSetOf<String>()
        val nextFolders = folders.map { folder ->
            if (folder.spaceId != spaceId) folder
            else {
                val nextOrder = topOrderByFolderId[folder.id] ?: folder.sortOrder
                if (nextOrder != folder.sortOrder) {
                    updatedFolderIds += folder.id
                    folder.copy(sortOrder = nextOrder)
                } else folder
            }
        }

        val tabById = tabs.associateBy { it.id }
        val orderedStandaloneTabs = orderedStandaloneTabIds.mapNotNull { tabById[it] }
        val standaloneSet = orderedStandaloneTabIds.toSet()
        var standaloneCursor = 0
        val updatedTabIds = mutableSetOf<String>()
        val nextTabs = tabs.map { tab ->
            if (tab.id in standaloneSet && standaloneCursor < orderedStandaloneTabs.size) {
                val replacement = orderedStandaloneTabs[standaloneCursor++]
                val nextOrder = topOrderByTabId[replacement.id] ?: replacement.sortOrder
                updatedTabIds += replacement.id
                replacement.copy(sortOrder = nextOrder)
            } else {
                tab
            }
        }
        return NormalizedSpacePinnedOrder(
            folders = nextFolders,
            tabs = nextTabs,
            updatedFolderIds = updatedFolderIds,
            updatedTabIds = updatedTabIds
        )
    }

    fun applyReorderFolderPinnedTabs(
        tabs: List<BrowserTabUi>,
        spaceId: String,
        folderId: String,
        orderedTabIds: List<String>
    ): Pair<List<BrowserTabUi>, Set<String>> {
        val folderTabs = tabs.filter {
            !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab &&
                it.spaceId == spaceId && it.folderId == folderId
        }
        val byId = folderTabs.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val reorderedSubset = buildList {
            for (id in orderedTabIds) {
                val t = byId[id]
                if (t != null && seen.add(id)) add(t)
            }
            for (t in folderTabs) {
                if (seen.add(t.id)) add(t)
            }
        }.mapIndexed { idx, tab -> tab.copy(sortOrder = idx) }
        val targetSet = reorderedSubset.mapTo(mutableSetOf()) { it.id }
        var cursor = 0
        val nextTabs = tabs.map { tab ->
            if (tab.id in targetSet && cursor < reorderedSubset.size) {
                reorderedSubset[cursor++]
            } else {
                tab
            }
        }
        return nextTabs to targetSet
    }

    fun applyDeletePinnedFolder(
        folders: List<PinnedFolderEntity>,
        tabs: List<BrowserTabUi>,
        folderId: String,
        deleteContainedTabs: Boolean
    ): DeleteFolderResult? {
        val targetFolder = folders.firstOrNull { it.id == folderId } ?: return null
        val spaceId = targetFolder.spaceId
        val containedTabs = tabs
            .filter {
                !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab &&
                    it.spaceId == spaceId && it.folderId == folderId
            }
            .sortedWith(compareBy<BrowserTabUi> { it.sortOrder }.thenBy { it.id })

        if (deleteContainedTabs) {
            val removedIds = containedTabs.mapTo(mutableSetOf()) { it.id }
            val remainingFolders = folders.filterNot { it.id == folderId }
            val remainingTabs = tabs.filterNot { it.id in removedIds }
            val normalized = normalizeSpacePinnedOrders(remainingFolders, remainingTabs, spaceId)
            return DeleteFolderResult(
                updatedFolders = normalized.folders,
                updatedTabs = normalized.tabs,
                removedTabs = containedTabs
            )
        }

        // Keep contained tabs by inserting them into top-level Pinned at the deleted folder's position
        val topLevel = buildLocalPinnedDisplayList(folders, tabs, spaceId)
        val expandedTopLevelIds = buildList<Pair<Boolean, String>> {
            for (entry in topLevel) {
                when (entry) {
                    is SidebarPinnedEntry.FolderGroup -> {
                        if (entry.folder.id == folderId) {
                            containedTabs.forEach { add(false to it.id) }
                        } else {
                            add(true to entry.folder.id)
                        }
                    }
                    is SidebarPinnedEntry.StandalonePin -> add(false to entry.tab.id)
                }
            }
        }
        val newOrderByFolderId = HashMap<String, Int>()
        val newOrderByTabId = HashMap<String, Int>()
        expandedTopLevelIds.forEachIndexed { idx, (isFolder, id) ->
            if (isFolder) newOrderByFolderId[id] = idx else newOrderByTabId[id] = idx
        }
        val remainingFolders = folders
            .filterNot { it.id == folderId }
            .map { f ->
                if (f.spaceId == spaceId) f.copy(sortOrder = newOrderByFolderId[f.id] ?: f.sortOrder) else f
            }
        val updatedTabs = tabs.map { tab ->
            if (!tab.isPrivate && tab.isPinned && !tab.isBoundWorkspaceTab && tab.spaceId == spaceId) {
                val clearedFolder = if (tab.folderId == folderId) null else tab.folderId
                val nextOrder = if (clearedFolder == null) {
                    newOrderByTabId[tab.id] ?: tab.sortOrder
                } else {
                    tab.sortOrder
                }
                tab.copy(folderId = clearedFolder, sortOrder = nextOrder)
            } else {
                tab
            }
        }
        return DeleteFolderResult(
            updatedFolders = remainingFolders,
            updatedTabs = updatedTabs,
            removedTabs = emptyList()
        )
    }

    fun applyMoveTabToSpace(
        state: BrowserUiState,
        tabId: String,
        targetSpaceId: String
    ): MoveTabResult? {
        if (state.spaces.none { it.id == targetSpaceId }) return null
        val target = state.tabs.firstOrNull { it.id == tabId } ?: return null
        if (target.isPrivate || target.isBoundWorkspaceTab || target.spaceId == targetSpaceId) return null

        val sourceSpaceId = target.spaceId
        val wasActive = !state.isPrivateView && state.activeTabId == tabId
        val destSortOrder = if (target.isPinned) {
            nextTopLevelSortOrder(state.pinnedFolders, state.tabs, targetSpaceId)
        } else {
            val destRegular = state.tabs.filter {
                !it.isPrivate && !it.isPinned && !it.isBoundWorkspaceTab && it.spaceId == targetSpaceId
            }
            if (destRegular.isEmpty()) 0 else (destRegular.maxOf { it.sortOrder } + 1).coerceAtLeast(destRegular.size)
        }
        val movedTab = target.copy(
            spaceId = targetSpaceId,
            folderId = null,
            sortOrder = destSortOrder
        )
        val withoutTarget = state.tabs.filterNot { it.id == tabId }
        val reorderedTabs = if (target.isPinned) {
            val destPinned = withoutTarget.filter {
                !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == targetSpaceId && it.isPinned
            }
            val insertAfterId = destPinned.lastOrNull()?.id
            if (insertAfterId != null) {
                val idx = withoutTarget.indexOfFirst { it.id == insertAfterId } + 1
                withoutTarget.toMutableList().apply { add(idx, movedTab) }
            } else {
                listOf(movedTab) + withoutTarget
            }
        } else {
            withoutTarget + movedTab
        }

        val normalizedSource = if (target.isPinned) {
            normalizeSpacePinnedOrders(state.pinnedFolders, reorderedTabs, sourceSpaceId)
        } else {
            NormalizedSpacePinnedOrder(state.pinnedFolders, reorderedTabs, emptySet(), emptySet())
        }

        val finalMovedTab = normalizedSource.tabs.first { it.id == tabId }
        return MoveTabResult(
            updatedTabs = normalizedSource.tabs,
            updatedFolders = normalizedSource.folders,
            movedTab = finalMovedTab,
            followedToTargetSpace = wasActive,
            nextActiveSpaceId = if (wasActive) targetSpaceId else state.activeSpaceId,
            nextActiveTabId = state.activeTabId
        )
    }

    fun applyMoveZenPinnedToSpace(
        state: BrowserUiState,
        zenPinnedId: String,
        targetSpaceId: String,
        newLocalTabId: String = UUID.randomUUID().toString()
    ): MoveZenPinnedResult? {
        if (state.spaces.none { it.id == targetSpaceId }) return null
        val item = state.zenPinnedItems.firstOrNull { it.id == zenPinnedId } ?: return null
        if (item.spaceId == targetSpaceId) return null

        val boundTab = state.tabForZenPinned(zenPinnedId)
        val wasActive = boundTab != null && !state.isPrivateView && state.activeTabId == boundTab.id
        val destSortOrder = nextTopLevelSortOrder(state.pinnedFolders, state.tabs, targetSpaceId)

        val convertedTab = if (boundTab != null) {
            boundTab.copy(
                spaceId = targetSpaceId,
                isPinned = true,
                zenPinnedId = null,
                essentialId = null,
                folderId = null,
                isUnloaded = false,
                pinnedUrl = item.url,
                customTitle = item.title,
                customIcon = item.customIcon,
                sortOrder = destSortOrder
            )
        } else {
            BrowserTabUi(
                id = newLocalTabId,
                spaceId = targetSpaceId,
                title = item.title,
                url = item.url,
                isPrivate = false,
                isPinned = true,
                essentialId = null,
                zenPinnedId = null,
                isUnloaded = true,
                pinnedUrl = item.url,
                customIcon = item.customIcon,
                customTitle = item.title,
                folderId = null,
                sortOrder = destSortOrder
            )
        }

        val withoutOld = if (boundTab != null) {
            state.tabs.filterNot { it.id == boundTab.id }
        } else {
            state.tabs
        }
        val destPinned = withoutOld.filter {
            !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == targetSpaceId && it.isPinned
        }
        val insertAfterId = destPinned.lastOrNull()?.id
        val updatedTabs = if (insertAfterId != null) {
            val idx = withoutOld.indexOfFirst { it.id == insertAfterId } + 1
            withoutOld.toMutableList().apply { add(idx, convertedTab) }
        } else {
            listOf(convertedTab) + withoutOld
        }

        return MoveZenPinnedResult(
            updatedZenItems = state.zenPinnedItems.filterNot { it.id == zenPinnedId },
            updatedTabs = updatedTabs,
            createdOrUpdatedLocalTab = convertedTab,
            followedToTargetSpace = wasActive,
            nextActiveSpaceId = if (wasActive) targetSpaceId else state.activeSpaceId,
            nextActiveTabId = if (wasActive) convertedTab.id else state.activeTabId
        )
    }
}
