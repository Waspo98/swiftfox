package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.ui.browser.BrowserOverlay
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.MainFrameLoadError
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
            isReaderAvailable = true,
            darkReaderSkippedNative = true,
            loadError = MainFrameLoadError(
                url = "https://docs.example.com/guide/step-2",
                host = "docs.example.com",
                description = "net::ERR_CONNECTION_REFUSED"
            )
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
        assertFalse(unloaded.isReaderAvailable)
        assertFalse(unloaded.darkReaderSkippedNative)
        assertNull(unloaded.loadError)
    }

    @Test
    fun findReusableBlankTab_reusesMatchingScopeBlankAndRespectsSpaceAndPrivacy() {
        val personalPage = BrowserTabUi(
            id = "tab_web",
            spaceId = "space_personal",
            title = "Bleacher Nation",
            url = "https://www.bleachernation.com/",
            lastAccessedAt = 100L
        )
        val personalBlank = BrowserTabUi(
            id = "tab_blank_personal",
            spaceId = "space_personal",
            title = "New Tab",
            url = OmniboxEngine.NEW_TAB_URL,
            lastAccessedAt = 200L
        )
        val privateBlank = BrowserTabUi(
            id = "tab_blank_private",
            spaceId = "space_personal",
            title = "New Tab",
            url = OmniboxEngine.NEW_TAB_URL,
            isPrivate = true,
            lastAccessedAt = 300L
        )
        val popupBlank = BrowserTabUi(
            id = "tab_popup",
            spaceId = "space_work",
            title = "New Tab",
            url = OmniboxEngine.NEW_TAB_URL,
            isPopupWindow = true,
            lastAccessedAt = 400L
        )
        val tabs = listOf(personalPage, personalBlank, privateBlank, popupBlank)

        // When on a webpage in Personal, jumps to existing Personal blank tab
        assertEquals(
            personalBlank,
            WorkspaceTabResolver.findReusableBlankTab(
                tabs = tabs,
                activeTabId = personalPage.id,
                spaceId = "space_personal",
                isPrivate = false
            )
        )
        // When already on Personal blank tab, stays on it
        assertEquals(
            personalBlank,
            WorkspaceTabResolver.findReusableBlankTab(
                tabs = tabs,
                activeTabId = personalBlank.id,
                spaceId = "space_personal",
                isPrivate = false
            )
        )
        // Work space has only a popup blank (not a Start Page), so returns null
        assertNull(
            WorkspaceTabResolver.findReusableBlankTab(
                tabs = tabs,
                activeTabId = personalPage.id,
                spaceId = "space_work",
                isPrivate = false
            )
        )
        // Private request reuses private blank tab, not personal blank tab
        assertEquals(
            privateBlank,
            WorkspaceTabResolver.findReusableBlankTab(
                tabs = tabs,
                activeTabId = personalPage.id,
                spaceId = "space_work",
                isPrivate = true
            )
        )
    }

    @Test
    fun resolveSessionContainerProfile_honorsPopupProfileOverrideEvenWhenAllSpacesAreContainerized() {
        val allContainerized = listOf(
            SpaceEntity(id = "space_personal", name = "Personal", containerProfileName = "space_personal"),
            SpaceEntity(id = "space_work", name = "Work", containerProfileName = "space_work")
        )
        val essentialPopup = BrowserTabUi(
            id = "tab_popup_oauth",
            spaceId = "space_work",
            title = "Sign in",
            url = OmniboxEngine.NEW_TAB_URL,
            isPopupWindow = true,
            parentTabId = "tab_ess_gmail",
            profileOverride = BrowserSession.DEFAULT_PROFILE_NAME
        )
        val workPopup = BrowserTabUi(
            id = "tab_popup_work",
            spaceId = "space_work",
            title = "SSO",
            url = OmniboxEngine.NEW_TAB_URL,
            isPopupWindow = true,
            parentTabId = "tab_work_app",
            profileOverride = "space_work"
        )
        val normalWorkTab = BrowserTabUi(
            id = "tab_work_app",
            spaceId = "space_work",
            title = "Work App",
            url = "https://work.example.com/"
        )

        // Default profile override resolves to null container (DEFAULT_PROFILE_NAME) even inside Work Space
        assertNull(WorkspaceTabResolver.resolveSessionContainerProfile(essentialPopup, allContainerized))
        // Explicit container override keeps the parent's container profile
        assertEquals("space_work", WorkspaceTabResolver.resolveSessionContainerProfile(workPopup, allContainerized))
        // Normal tabs continue to resolve from their Space container
        assertEquals("space_work", WorkspaceTabResolver.resolveSessionContainerProfile(normalWorkTab, allContainerized))
    }

    @Test
    fun resolveFallbackTabId_returnsToPopupParentTabIncludingEssentials() {
        val essentialParent = BrowserTabUi(
            id = "tab_ess_gmail",
            spaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
            title = "Gmail",
            url = "https://mail.google.com/",
            essentialId = "ess_gmail",
            lastAccessedAt = 100L
        )
        val workTab = BrowserTabUi(
            id = "tab_work",
            spaceId = "space_work",
            title = "Docs",
            url = "https://docs.example.com/",
            lastAccessedAt = 500L
        )

        assertEquals(
            "tab_ess_gmail",
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = listOf(essentialParent, workTab),
                activeSpaceId = "space_work",
                isPrivateView = false,
                preferredTabId = "tab_ess_gmail"
            )
        )
        // Without a preferred parent tab, regular Space fallback still picks workTab over Essential
        assertEquals(
            "tab_work",
            WorkspaceTabResolver.resolveFallbackTabId(
                remainingTabs = listOf(essentialParent, workTab),
                activeSpaceId = "space_work",
                isPrivateView = false
            )
        )
    }

    @Test
    fun tabSwitcherTabsAndCountsIncludeLoadedEssentialsWithoutDoubleCounting() {
        val loadedEss = EssentialEntity(
            id = "ess_verge",
            title = "The Verge",
            url = "https://www.theverge.com",
            domain = "theverge.com"
        )
        val unloadedEss = EssentialEntity(
            id = "ess_bn",
            title = "Bleacher Nation",
            url = "https://www.bleachernation.com",
            domain = "bleachernation.com"
        )
        val workOnlyEss = EssentialEntity(
            id = "ess_jira",
            title = "Jira",
            url = "https://jira.example.com",
            domain = "jira.example.com",
            syncGuid = "jira",
            zenContainerGuid = "container-work"
        )
        val loadedEssTab = BrowserTabUi(
            id = "tab_ess_verge",
            spaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
            title = "The Verge",
            url = "https://www.theverge.com",
            essentialId = loadedEss.id
        )
        val workEssTab = BrowserTabUi(
            id = "tab_ess_jira",
            spaceId = "space_work",
            title = "Jira",
            url = "https://jira.example.com",
            essentialId = workOnlyEss.id
        )
        val personalTab = BrowserTabUi(
            id = "tab_personal_1",
            spaceId = "space_personal",
            title = "New Tab",
            url = OmniboxEngine.NEW_TAB_URL
        )
        val privateTab = BrowserTabUi(
            id = "tab_priv_1",
            spaceId = "space_personal",
            title = "Private",
            url = "https://example.org",
            isPrivate = true
        )

        val sharedState = BrowserUiState(
            settings = BrowserSettings(syncZenSeparateEssentials = false),
            spaces = spaces,
            activeSpaceId = "space_personal",
            essentials = listOf(loadedEss, unloadedEss),
            tabs = listOf(loadedEssTab, personalTab, privateTab),
            activeTabId = personalTab.id
        )

        // Open Essentials appear in tabSwitcherTabs alongside regular tabs; unloaded Essentials do not.
        assertEquals(listOf(loadedEssTab, personalTab), sharedState.tabSwitcherTabs)
        assertEquals(2, sharedState.tabSwitcherCountForSpace("space_personal"))
        assertEquals(1, sharedState.tabSwitcherCountForSpace("space_work"))

        // Private view remains strictly isolated from normal Essentials and tabs.
        val privateState = sharedState.copy(isPrivateView = true, activeTabId = privateTab.id)
        assertEquals(listOf(privateTab), privateState.tabSwitcherTabs)

        // Container-separated Essential stored under space_work is counted once (no double-count).
        val separatedState = BrowserUiState(
            settings = BrowserSettings(syncZenSeparateEssentials = true),
            spaces = spaces,
            activeSpaceId = "space_work",
            essentials = listOf(loadedEss, workOnlyEss),
            tabs = listOf(loadedEssTab, workEssTab, personalTab),
            activeTabId = workEssTab.id
        )
        assertEquals(listOf(loadedEssTab, workEssTab), separatedState.tabSwitcherTabs)
        assertEquals(2, separatedState.tabSwitcherCountForSpace("space_work"))
        assertEquals(2, separatedState.tabSwitcherCountForSpace("space_personal"))
    }

    @Test
    fun resolveTabCardSwipeDismissDirection_handlesDistanceVelocityReversalAndFloor() {
        val width = 400f
        val minFling = 800f

        // Slow drag past 32% distance dismisses in drag direction
        assertEquals(1, resolveTabCardSwipeDismissDirection(offsetX = 132f, velocityX = 100f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))
        assertEquals(-1, resolveTabCardSwipeDismissDirection(offsetX = -132f, velocityX = -100f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))

        // Slow drag below 32% snaps back
        assertEquals(0, resolveTabCardSwipeDismissDirection(offsetX = 110f, velocityX = 200f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))
        assertEquals(0, resolveTabCardSwipeDismissDirection(offsetX = -110f, velocityX = -200f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))

        // Fast fling past 10% floor dismisses cleanly
        assertEquals(1, resolveTabCardSwipeDismissDirection(offsetX = 48f, velocityX = 1000f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))
        assertEquals(-1, resolveTabCardSwipeDismissDirection(offsetX = -48f, velocityX = -1000f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))

        // Tiny jitter (< 10% floor) with high velocity does not accidentally dismiss
        assertEquals(0, resolveTabCardSwipeDismissDirection(offsetX = 24f, velocityX = 1800f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))

        // Dragged right past threshold but flicked sharply back left cancels cleanly
        assertEquals(0, resolveTabCardSwipeDismissDirection(offsetX = 160f, velocityX = -1000f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))
        assertEquals(0, resolveTabCardSwipeDismissDirection(offsetX = -160f, velocityX = 1000f, cardWidthPx = width, minFlingVelocityPxPerSec = minFling))

        // Unmeasured card width never dismisses
        assertEquals(0, resolveTabCardSwipeDismissDirection(offsetX = 200f, velocityX = 2000f, cardWidthPx = 0f, minFlingVelocityPxPerSec = minFling))

        // Exit animation duration carries release velocity and stays snappy (50..105ms)
        assertEquals(50, computeTabCardSwipeExitDurationMs(remainingDistancePx = 220f, velocityX = 6000f, minExitSpeedPxPerSec = 4000f))
        assertEquals(105, computeTabCardSwipeExitDurationMs(remainingDistancePx = 800f, velocityX = 200f, minExitSpeedPxPerSec = 4000f))
        assertTrue(computeTabCardSwipeExitDurationMs(remainingDistancePx = 300f, velocityX = 4000f, minExitSpeedPxPerSec = 4000f) in 50..105)
    }

    @Test
    fun tabSwitcherExcludesUnloadedPinnedTabsAndIncludesOpenPinnedAndZenTabs() {
        val loadedPin = BrowserTabUi(
            id = "tab_pin_open",
            spaceId = "space_personal",
            title = "Open Pin",
            url = "https://example.com/open-pin",
            isPinned = true,
            isUnloaded = false
        )
        val unloadedPin = BrowserTabUi(
            id = "tab_pin_closed",
            spaceId = "space_personal",
            title = "Closed Pin",
            url = "https://example.com/closed-pin",
            isPinned = true,
            isUnloaded = true
        )
        val openZenItem = ZenPinnedItemEntity(
            id = "zen_open",
            spaceId = "space_personal",
            zenWorkspaceUuid = "ws_1",
            title = "Zen Open",
            url = "https://kotlinlang.org"
        )
        val closedZenItem = ZenPinnedItemEntity(
            id = "zen_closed",
            spaceId = "space_personal",
            zenWorkspaceUuid = "ws_1",
            title = "Zen Closed",
            url = "https://developer.android.com"
        )
        val openZenTab = BrowserTabUi(
            id = "tab_zen_open",
            spaceId = "space_personal",
            title = "Zen Open",
            url = "https://kotlinlang.org",
            zenPinnedId = openZenItem.id
        )
        val regularTab = BrowserTabUi(
            id = "tab_reg_1",
            spaceId = "space_personal",
            title = "Regular",
            url = "https://example.org"
        )
        val state = BrowserUiState(
            spaces = spaces,
            activeSpaceId = "space_personal",
            zenPinnedItems = listOf(openZenItem, closedZenItem),
            tabs = listOf(loadedPin, unloadedPin, openZenTab, regularTab),
            activeTabId = regularTab.id
        )

        // Tab switcher shows ONLY actively open tabs — unloaded local pin and closed Zen pin are omitted
        assertEquals(listOf(loadedPin, openZenTab, regularTab), state.tabSwitcherTabs)
        assertEquals(3, state.tabSwitcherCountForSpace("space_personal"))
        // Visible pinned tabs (used by Start Page & Sidebar) still include both loaded and unloaded pins
        assertEquals(listOf(loadedPin, unloadedPin), state.visiblePinnedTabs)
        assertEquals(listOf(openZenItem, closedZenItem), state.activeSpaceZenPinned)
    }

    @Test
    fun defaultExternalAppLinkPolicyBlocksExternalAppPopupsByDefault() {
        assertEquals(
            ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX,
            BrowserSettings().externalAppLinkPolicy
        )
    }

    @Test
    fun startPageSpaceSwitchPreservesStartPageWhenTargetSpaceIsEmptyAndRebindsOnNavigation() {
        val personalStart = BrowserTabUi(
            id = "tab_start",
            spaceId = "space_personal",
            title = "New Tab",
            url = OmniboxEngine.NEW_TAB_URL
        )
        val workPage = BrowserTabUi(
            id = "tab_work",
            spaceId = "space_work",
            title = "Docs",
            url = "https://docs.example.com"
        )
        val startState = BrowserUiState(
            spaces = spaces,
            activeSpaceId = "space_personal",
            tabs = listOf(personalStart),
            activeTabId = personalStart.id
        )

        assertTrue(WorkspaceTabResolver.shouldKeepStartPageOnSpaceSwitch(startState, "space_work"))
        assertFalse(
            WorkspaceTabResolver.shouldKeepStartPageOnSpaceSwitch(
                startState.copy(activeOverlay = BrowserOverlay.TAB_SWITCHER),
                "space_work"
            )
        )
        assertFalse(
            WorkspaceTabResolver.shouldKeepStartPageOnSpaceSwitch(
                startState.copy(tabs = listOf(personalStart, workPage)),
                "space_work"
            )
        )

        // Navigating from the Start Page after switching workspace to Work moves the tab into Work
        assertEquals(
            "space_work",
            WorkspaceTabResolver.resolveNavigationTabSpaceId(personalStart, "space_work")
        )
        // Existing webpage tabs stay in their own Space
        assertEquals(
            "space_work",
            WorkspaceTabResolver.resolveNavigationTabSpaceId(workPage, "space_personal")
        )
    }
}
