package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePinnedFolderLogicTest {

    private val personal = SpaceEntity(id = "space_personal", name = "Personal", iconKey = "home", sortOrder = 0)
    private val work = SpaceEntity(id = "space_work", name = "Work", iconKey = "work", sortOrder = 1)
    private val spaces = listOf(personal, work)

    @Test
    fun buildLocalPinnedDisplayList_interleavesFoldersAndStandalonePinsBySortOrder() {
        val folderA = PinnedFolderEntity(id = "f_a", spaceId = "space_personal", name = "Docs", sortOrder = 1)
        val folderB = PinnedFolderEntity(id = "f_b", spaceId = "space_personal", name = "Social", sortOrder = 3)
        val pinTop = BrowserTabUi(
            id = "p_top",
            spaceId = "space_personal",
            title = "Mail",
            url = "https://mail.example.com",
            isPinned = true,
            sortOrder = 0
        )
        val pinInsideA1 = BrowserTabUi(
            id = "p_a1",
            spaceId = "space_personal",
            title = "Kotlin",
            url = "https://kotlinlang.org",
            isPinned = true,
            folderId = "f_a",
            sortOrder = 1
        )
        val pinInsideA0 = BrowserTabUi(
            id = "p_a0",
            spaceId = "space_personal",
            title = "Android",
            url = "https://developer.android.com",
            isPinned = true,
            folderId = "f_a",
            sortOrder = 0
        )
        val pinMiddle = BrowserTabUi(
            id = "p_mid",
            spaceId = "space_personal",
            title = "News",
            url = "https://news.ycombinator.com",
            isPinned = true,
            sortOrder = 2
        )

        val entries = WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(
            folders = listOf(folderB, folderA),
            pinnedTabs = listOf(pinInsideA1, pinMiddle, pinTop, pinInsideA0),
            spaceId = "space_personal"
        )

        assertEquals(
            listOf("tab:p_top", "folder:f_a", "tab:p_mid", "folder:f_b"),
            entries.map { it.entryKey }
        )
        val groupA = entries[1] as SidebarPinnedEntry.FolderGroup
        assertEquals(listOf("p_a0", "p_a1"), groupA.tabs.map { it.id })
        val groupB = entries[3] as SidebarPinnedEntry.FolderGroup
        assertTrue(groupB.tabs.isEmpty())
    }

    @Test
    fun createFolderFromUngroupedPin_replacesSlotAndGroupsTab() {
        val pin0 = BrowserTabUi(id = "p0", spaceId = "space_personal", title = "A", url = "https://a.test", isPinned = true, sortOrder = 0)
        val pin1 = BrowserTabUi(id = "p1", spaceId = "space_personal", title = "B", url = "https://b.test", isPinned = true, sortOrder = 1)
        val pin2 = BrowserTabUi(id = "p2", spaceId = "space_personal", title = "C", url = "https://c.test", isPinned = true, sortOrder = 2)

        val (folder, normalized) = WorkspacePinnedFolderLogic.applyCreatePinnedFolder(
            folders = emptyList(),
            tabs = listOf(pin0, pin1, pin2),
            spaceId = "space_personal",
            folderName = "Reading",
            initialTabId = "p1",
            folderId = "f_read"
        )!!

        assertEquals("Reading", folder.name)
        assertEquals(1, folder.sortOrder)
        val display = WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(normalized.folders, normalized.tabs, "space_personal")
        assertEquals(listOf("tab:p0", "folder:f_read", "tab:p2"), display.map { it.entryKey })
        val group = display[1] as SidebarPinnedEntry.FolderGroup
        assertEquals(listOf("p1"), group.tabs.map { it.id })
    }

    @Test
    fun deleteFolder_keepPinsPreservesOrderAndUngroups_deletePinsRemovesTabs() {
        val folder = PinnedFolderEntity(id = "f1", spaceId = "space_personal", name = "Dev", sortOrder = 1)
        val topPin = BrowserTabUi(id = "p_top", spaceId = "space_personal", title = "Top", url = "https://top.test", isPinned = true, sortOrder = 0)
        val child0 = BrowserTabUi(id = "p_c0", spaceId = "space_personal", title = "C0", url = "https://c0.test", isPinned = true, folderId = "f1", sortOrder = 0)
        val child1 = BrowserTabUi(id = "p_c1", spaceId = "space_personal", title = "C1", url = "https://c1.test", isPinned = true, folderId = "f1", sortOrder = 1)
        val botPin = BrowserTabUi(id = "p_bot", spaceId = "space_personal", title = "Bot", url = "https://bot.test", isPinned = true, sortOrder = 2)

        val keepResult = WorkspacePinnedFolderLogic.applyDeletePinnedFolder(
            folders = listOf(folder),
            tabs = listOf(topPin, child0, child1, botPin),
            folderId = "f1",
            deleteContainedTabs = false
        )!!
        assertTrue(keepResult.updatedFolders.isEmpty())
        assertTrue(keepResult.removedTabs.isEmpty())
        val keepDisplay = WorkspacePinnedFolderLogic.buildLocalPinnedDisplayList(keepResult.updatedFolders, keepResult.updatedTabs, "space_personal")
        assertEquals(listOf("tab:p_top", "tab:p_c0", "tab:p_c1", "tab:p_bot"), keepDisplay.map { it.entryKey })
        assertTrue(keepResult.updatedTabs.all { it.folderId == null })

        val deleteResult = WorkspacePinnedFolderLogic.applyDeletePinnedFolder(
            folders = listOf(folder),
            tabs = listOf(topPin, child0, child1, botPin),
            folderId = "f1",
            deleteContainedTabs = true
        )!!
        assertTrue(deleteResult.updatedFolders.isEmpty())
        assertEquals(listOf("p_c0", "p_c1"), deleteResult.removedTabs.map { it.id })
        assertEquals(listOf("p_top", "p_bot"), deleteResult.updatedTabs.map { it.id })
    }

    @Test
    fun buildZenPinnedDisplayList_groupsSameFolderNamePreservingFirstPosition() {
        val z1 = ZenPinnedItemEntity(id = "z1", spaceId = "space_personal", zenWorkspaceUuid = "ws", title = "Top", url = "https://a.test", folderName = null, sortOrder = 0)
        val z2 = ZenPinnedItemEntity(id = "z2", spaceId = "space_personal", zenWorkspaceUuid = "ws", title = "Docs 1", url = "https://b.test", folderName = "Work / Docs", sortOrder = 1)
        val z3 = ZenPinnedItemEntity(id = "z3", spaceId = "space_personal", zenWorkspaceUuid = "ws", title = "Docs 2", url = "https://c.test", folderName = "Work / Docs", sortOrder = 2)
        val z4 = ZenPinnedItemEntity(id = "z4", spaceId = "space_personal", zenWorkspaceUuid = "ws", title = "Bottom", url = "https://d.test", folderName = null, sortOrder = 3)

        val entries = WorkspacePinnedFolderLogic.buildZenPinnedDisplayList(listOf(z1, z2, z3, z4), "space_personal")
        assertEquals(3, entries.size)
        assertTrue(entries[0] is SidebarZenPinnedEntry.StandaloneItem)
        val group = entries[1] as SidebarZenPinnedEntry.FolderGroup
        assertEquals("Work / Docs", group.folderName)
        assertEquals(listOf("z2", "z3"), group.items.map { it.id })
        assertTrue(entries[2] is SidebarZenPinnedEntry.StandaloneItem)
    }

    @Test
    fun moveTabToSpace_followsOnlyWhenActiveAndClearsFolderOnPinnedMove() {
        val folder = PinnedFolderEntity(id = "f1", spaceId = "space_personal", name = "Dev", sortOrder = 0)
        val activeTab = BrowserTabUi(
            id = "tab_active",
            spaceId = "space_personal",
            title = "Active",
            url = "https://active.test"
        )
        val backgroundPinned = BrowserTabUi(
            id = "pin_bg",
            spaceId = "space_personal",
            title = "BgPin",
            url = "https://bg.test",
            isPinned = true,
            isUnloaded = true,
            pinnedUrl = "https://bg.test",
            folderId = "f1",
            sortOrder = 0
        )
        val state = BrowserUiState(
            spaces = spaces,
            activeSpaceId = "space_personal",
            pinnedFolders = listOf(folder),
            tabs = listOf(backgroundPinned, activeTab),
            activeTabId = activeTab.id
        )

        // Moving background pinned tab stays in current Space and clears folderId
        val bgMove = WorkspacePinnedFolderLogic.applyMoveTabToSpace(state, backgroundPinned.id, "space_work")!!
        assertFalse(bgMove.followedToTargetSpace)
        assertEquals("space_personal", bgMove.nextActiveSpaceId)
        assertEquals("tab_active", bgMove.nextActiveTabId)
        assertEquals("space_work", bgMove.movedTab.spaceId)
        assertNull(bgMove.movedTab.folderId)

        // Moving active tab follows to destination Space
        val activeMove = WorkspacePinnedFolderLogic.applyMoveTabToSpace(state, activeTab.id, "space_work")!!
        assertTrue(activeMove.followedToTargetSpace)
        assertEquals("space_work", activeMove.nextActiveSpaceId)
        assertEquals("tab_active", activeMove.nextActiveTabId)
        assertEquals("space_work", activeMove.movedTab.spaceId)
    }

    @Test
    fun moveZenPinnedToSpace_detachesIntoLocalPinnedAndFollowsOnlyIfActive() {
        val zenLoaded = ZenPinnedItemEntity(
            id = "ws_loaded",
            spaceId = "space_personal",
            zenWorkspaceUuid = "ws",
            title = "GitHub",
            url = "https://github.com",
            folderName = "Code"
        )
        val zenUnloaded = ZenPinnedItemEntity(
            id = "ws_unloaded",
            spaceId = "space_personal",
            zenWorkspaceUuid = "ws",
            title = "Docs",
            url = "https://kotlinlang.org"
        )
        val boundTab = BrowserTabUi(
            id = "tab_zen_live",
            spaceId = "space_personal",
            title = "GitHub",
            url = "https://github.com/issues",
            zenPinnedId = zenLoaded.id
        )
        val state = BrowserUiState(
            spaces = spaces,
            activeSpaceId = "space_personal",
            zenPinnedItems = listOf(zenLoaded, zenUnloaded),
            tabs = listOf(boundTab),
            activeTabId = boundTab.id
        )

        // Moving unloaded Zen item stays in current Space and creates an unloaded local pin
        val unloadedMove = WorkspacePinnedFolderLogic.applyMoveZenPinnedToSpace(
            state = state,
            zenPinnedId = zenUnloaded.id,
            targetSpaceId = "space_work",
            newLocalTabId = "local_from_zen"
        )!!
        assertFalse(unloadedMove.followedToTargetSpace)
        assertEquals("space_personal", unloadedMove.nextActiveSpaceId)
        assertEquals(boundTab.id, unloadedMove.nextActiveTabId)
        assertEquals("local_from_zen", unloadedMove.createdOrUpdatedLocalTab.id)
        assertTrue(unloadedMove.createdOrUpdatedLocalTab.isPinned)
        assertTrue(unloadedMove.createdOrUpdatedLocalTab.isUnloaded)
        assertNull(unloadedMove.createdOrUpdatedLocalTab.zenPinnedId)
        assertEquals("space_work", unloadedMove.createdOrUpdatedLocalTab.spaceId)
        assertEquals(listOf(zenLoaded), unloadedMove.updatedZenItems)

        // Moving loaded active Zen item preserves live tab id/URL and follows to destination Space
        val loadedMove = WorkspacePinnedFolderLogic.applyMoveZenPinnedToSpace(
            state = state,
            zenPinnedId = zenLoaded.id,
            targetSpaceId = "space_work"
        )!!
        assertTrue(loadedMove.followedToTargetSpace)
        assertEquals("space_work", loadedMove.nextActiveSpaceId)
        assertEquals(boundTab.id, loadedMove.nextActiveTabId)
        val converted = loadedMove.createdOrUpdatedLocalTab
        assertEquals(boundTab.id, converted.id)
        assertEquals("https://github.com/issues", converted.url)
        assertEquals("https://github.com", converted.pinnedUrl)
        assertTrue(converted.isPinned)
        assertFalse(converted.isUnloaded)
        assertNull(converted.zenPinnedId)
        assertNull(converted.folderId)
        assertEquals("space_work", converted.spaceId)
        assertNotNull(loadedMove.updatedTabs.firstOrNull { it.id == boundTab.id })
    }
}
