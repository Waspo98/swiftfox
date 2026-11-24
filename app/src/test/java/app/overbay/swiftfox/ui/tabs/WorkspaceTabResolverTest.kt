package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTabResolverTest {

    private val personalSpace = SpaceEntity(id = "space_personal", name = "Personal", zenContainerGuid = "default")
    private val workSpace = SpaceEntity(
        id = "space_work",
        name = "Work",
        containerProfileName = "space_work",
        zenContainerGuid = "container-work"
    )
    private val spaces = listOf(personalSpace, workSpace)

    @Test
    fun resolveEssentialSpaceId_usesGlobalSentinelWhenSharedAcrossSpaces() {
        val ess = EssentialEntity(
            id = "ess_gmail",
            title = "Gmail",
            url = "https://mail.google.com",
            domain = "mail.google.com"
        )
        val resolved = WorkspaceTabResolver.resolveEssentialSpaceId(
            essential = ess,
            spaces = spaces,
            activeSpaceId = "space_work",
            settings = BrowserSettings(syncZenSeparateEssentials = false),
            allEssentials = listOf(ess)
        )
        assertEquals(WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID, resolved)
    }

    @Test
    fun resolveEssentialSpaceId_matchesContainerSpaceWhenSeparated() {
        val workEss = EssentialEntity(
            id = "zen_ess_jira",
            title = "Jira",
            url = "https://jira.example.com",
            domain = "jira.example.com",
            syncGuid = "jira",
            zenContainerGuid = "container-work"
        )
        val resolved = WorkspaceTabResolver.resolveEssentialSpaceId(
            essential = workEss,
            spaces = spaces,
            activeSpaceId = "space_personal",
            settings = BrowserSettings(syncZenSeparateEssentials = null),
            allEssentials = listOf(workEss)
        )
        assertEquals("space_work", resolved)
    }

    @Test
    fun boundEssentialAndZenPinnedTabsStayOutOfRegularTabsList() {
        val ess = EssentialEntity(id = "ess_gh", title = "GitHub", url = "https://github.com", domain = "github.com")
        val zenPin = ZenPinnedItemEntity(
            id = "ws_docs",
            spaceId = "space_personal",
            zenWorkspaceUuid = "ws",
            title = "Docs",
            url = "https://developer.android.com"
        )
        val essTab = BrowserTabUi(
            id = "tab_ess",
            spaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
            title = "GitHub",
            url = "https://github.com",
            essentialId = ess.id
        )
        val zenTab = BrowserTabUi(
            id = "tab_zen",
            spaceId = "space_personal",
            title = "Docs",
            url = "https://developer.android.com",
            zenPinnedId = zenPin.id
        )
        val unloadedLocalPin = BrowserTabUi(
            id = "tab_pin_unloaded",
            spaceId = "space_personal",
            title = "News",
            url = "https://news.ycombinator.com",
            isPinned = true,
            isUnloaded = true,
            pinnedUrl = "https://news.ycombinator.com"
        )
        val regularTab = BrowserTabUi(
            id = "tab_reg",
            spaceId = "space_personal",
            title = "Start Page",
            url = OmniboxEngine.NEW_TAB_URL
        )
        val state = BrowserUiState(
            spaces = spaces,
            activeSpaceId = "space_personal",
            essentials = listOf(ess),
            zenPinnedItems = listOf(zenPin),
            tabs = listOf(essTab, zenTab, unloadedLocalPin, regularTab),
            activeTabId = essTab.id
        )

        assertEquals(essTab, state.activeTab)
        assertEquals(essTab, state.tabForEssential(ess.id))
        assertEquals(zenTab, state.tabForZenPinned(zenPin.id))
        assertEquals(listOf(regularTab), state.visibleRegularTabs)
        assertEquals(listOf(unloadedLocalPin), state.visiblePinnedTabs)
        assertEquals(listOf(unloadedLocalPin, regularTab), state.visibleTabs)
        // Unloaded local pin is excluded from loadedNavigableTabs, while loaded Essential & Zen Pin are included
        assertEquals(listOf(essTab, zenTab, regularTab), state.loadedNavigableTabs)
    }

    @Test
    fun resolveFallbackTabId_prefersMostRecentLoadedTabInActiveSpace() {
        val olderReg = BrowserTabUi(
            id = "tab_old",
            spaceId = "space_personal",
            title = "Older",
            url = "https://example.com/1",
            lastAccessedAt = 100L
        )
        val newerReg = BrowserTabUi(
            id = "tab_new",
            spaceId = "space_personal",
            title = "Newer",
            url = "https://example.com/2",
            lastAccessedAt = 500L
        )
        val unloadedPin = BrowserTabUi(
            id = "tab_unloaded_pin",
            spaceId = "space_personal",
            title = "Sleeping Pin",
            url = "https://example.com/pin",
            isPinned = true,
            isUnloaded = true,
            lastAccessedAt = 999L
        )
        val otherSpaceTab = BrowserTabUi(
            id = "tab_work",
            spaceId = "space_work",
            title = "Work",
            url = "https://work.example.com",
            lastAccessedAt = 1200L
        )
        val globalEss = BrowserTabUi(
            id = "tab_ess_other",
            spaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
            title = "Other Essential",
            url = "https://mail.google.com",
            essentialId = "ess_mail",
            lastAccessedAt = 1500L
        )

        val fallback = WorkspaceTabResolver.resolveFallbackTabId(
            remainingTabs = listOf(olderReg, newerReg, unloadedPin, otherSpaceTab, globalEss),
            activeSpaceId = "space_personal",
            isPrivateView = false
        )
        assertEquals("tab_new", fallback)
    }

    @Test
    fun resolveFallbackTabId_returnsNullWhenOnlyUnloadedPinsRemainInSpace() {
        val unloadedPin = BrowserTabUi(
            id = "tab_unloaded_pin",
            spaceId = "space_personal",
            title = "Sleeping Pin",
            url = "https://example.com/pin",
            isPinned = true,
            isUnloaded = true
        )
        val fallback = WorkspaceTabResolver.resolveFallbackTabId(
            remainingTabs = listOf(unloadedPin),
            activeSpaceId = "space_personal",
            isPrivateView = false
        )
        assertNull(fallback)
    }

    @Test
    fun closeAllScoping_detectsLoadedEssentialsAndPinsWithoutCountingBlankStartPage() {
        val ess = EssentialEntity(id = "ess_yt", title = "YouTube", url = "https://youtube.com", domain = "youtube.com")
        val loadedEssTab = BrowserTabUi(
            id = "tab_ess_yt",
            spaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
            title = "YouTube",
            url = "https://youtube.com",
            essentialId = ess.id
        )
        val unloadedPin = BrowserTabUi(
            id = "tab_pin_sleep",
            spaceId = "space_personal",
            title = "Docs",
            url = "https://docs.example.com",
            isPinned = true,
            isUnloaded = true
        )
        val blankStartPage = BrowserTabUi(
            id = "tab_start",
            spaceId = "space_personal",
            title = "New Tab",
            url = OmniboxEngine.NEW_TAB_URL
        )

        val stateWithLoadedEss = BrowserUiState(
            spaces = spaces,
            activeSpaceId = "space_personal",
            essentials = listOf(ess),
            tabs = listOf(loadedEssTab, unloadedPin, blankStartPage),
            activeTabId = loadedEssTab.id
        )
        assertEquals(listOf(loadedEssTab), WorkspaceTabResolver.collectLoadedEssentialsAndPinsInScope(stateWithLoadedEss))
        assertFalse(WorkspaceTabResolver.hasClosableRegularTabs(stateWithLoadedEss))
        assertTrue(WorkspaceTabResolver.hasAnythingToCloseAll(stateWithLoadedEss))

        val cleanState = stateWithLoadedEss.copy(tabs = listOf(unloadedPin, blankStartPage), activeTabId = blankStartPage.id)
        assertTrue(WorkspaceTabResolver.collectLoadedEssentialsAndPinsInScope(cleanState).isEmpty())
        assertFalse(WorkspaceTabResolver.hasClosableRegularTabs(cleanState))
        assertFalse(WorkspaceTabResolver.hasAnythingToCloseAll(cleanState))
    }

    @Test
    fun resolveChildTabSpaceId_fallsBackToActiveSpaceForGlobalEssentialParent() {
        assertEquals(
            "space_work",
            WorkspaceTabResolver.resolveChildTabSpaceId(
                parentSpaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
                activeSpaceId = "space_work"
            )
        )
        assertEquals(
            "space_work",
            WorkspaceTabResolver.resolveChildTabSpaceId(
                parentSpaceId = null,
                activeSpaceId = "space_work"
            )
        )
        assertEquals(
            "space_personal",
            WorkspaceTabResolver.resolveChildTabSpaceId(
                parentSpaceId = "space_personal",
                activeSpaceId = "space_work"
            )
        )
    }

    @Test
    fun toUnloadedPinnedTab_resetsUrlAndTransientSessionFlags() {
        val loadedPin = BrowserTabUi(
            id = "tab_pin",
            spaceId = "space_personal",
            title = "Subpage Title",
            url = "https://docs.example.com/guide/step-2",
            isPinned = true,
            isUnloaded = false,
            pinnedUrl = "https://docs.example.com",
            customTitle = "Docs",
            isLoading = true,
            progress = 75,
            canGoBack = true,
            canGoForward = true,
            isPlayingMedia = true,
            isPlayingVideo = true,
            blockedCount = 14,
            isReaderAvailable = true,
            darkReaderSkippedNative = true
        )

        val unloaded = WorkspaceTabResolver.toUnloadedPinnedTab(loadedPin)
        assertTrue(unloaded.isUnloaded)
        assertEquals("https://docs.example.com", unloaded.url)
        assertEquals("Docs", unloaded.title)
        assertFalse(unloaded.isLoading)
        assertEquals(0, unloaded.progress)
        assertFalse(unloaded.canGoBack)
        assertFalse(unloaded.canGoForward)
        assertFalse(unloaded.isPlayingMedia)
        assertFalse(unloaded.isPlayingVideo)
        assertEquals(0, unloaded.blockedCount)
        assertFalse(unloaded.isReaderAvailable)
        assertFalse(unloaded.darkReaderSkippedNative)
    }
}
