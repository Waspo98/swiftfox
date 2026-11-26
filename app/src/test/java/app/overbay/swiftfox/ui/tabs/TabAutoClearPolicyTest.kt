package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.data.prefs.TabAutoClearDuration
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabAutoClearPolicyTest {

    private val hourMs = 60L * 60L * 1000L
    private val dayMs = 24L * hourMs
    private val now = 10_000_000_000L

    @Test
    fun durationsExposeExpectedThresholdsAndAggressivenessComparison() {
        assertEquals(dayMs, TabAutoClearDuration.HOURS_24.durationMs)
        assertEquals(2L * dayMs, TabAutoClearDuration.HOURS_48.durationMs)
        assertEquals(7L * dayMs, TabAutoClearDuration.WEEK_1.durationMs)
        assertEquals(30L * dayMs, TabAutoClearDuration.MONTH_1.durationMs)
        assertNull(TabAutoClearDuration.NEVER.durationMs)

        assertTrue(TabAutoClearDuration.HOURS_24.isMoreAggressiveThan(TabAutoClearDuration.HOURS_48))
        assertTrue(TabAutoClearDuration.MONTH_1.isMoreAggressiveThan(TabAutoClearDuration.NEVER))
        assertFalse(TabAutoClearDuration.WEEK_1.isMoreAggressiveThan(TabAutoClearDuration.HOURS_24))
        assertFalse(TabAutoClearDuration.NEVER.isMoreAggressiveThan(TabAutoClearDuration.HOURS_24))
        assertFalse(TabAutoClearDuration.WEEK_1.isMoreAggressiveThan(TabAutoClearDuration.WEEK_1))
    }

    @Test
    fun evaluate_expiresStaleRegularLoadedPinsAndEssentialsWhileProtectingActiveAndMediaTabs() {
        val activeTab = BrowserTabUi(
            id = "tab_active",
            spaceId = "space_personal",
            title = "Active",
            url = "https://example.com/active",
            lastAccessedAt = now - 10L * dayMs
        )
        val staleRegular = BrowserTabUi(
            id = "tab_stale_reg",
            spaceId = "space_personal",
            title = "Old Article",
            url = "https://example.com/old",
            lastAccessedAt = now - 8L * dayMs
        )
        val freshRegular = BrowserTabUi(
            id = "tab_fresh_reg",
            spaceId = "space_personal",
            title = "Fresh Article",
            url = "https://example.com/fresh",
            lastAccessedAt = now - 3L * dayMs
        )
        val staleMediaRegular = BrowserTabUi(
            id = "tab_media",
            spaceId = "space_personal",
            title = "Music",
            url = "https://music.example.com",
            isPlayingMedia = true,
            lastAccessedAt = now - 14L * dayMs
        )
        val staleLoadedLocalPin = BrowserTabUi(
            id = "tab_pin_loaded",
            spaceId = "space_personal",
            title = "Drifted Pin",
            url = "https://docs.example.com/subpage",
            isPinned = true,
            isUnloaded = false,
            pinnedUrl = "https://docs.example.com",
            lastAccessedAt = now - 8L * dayMs
        )
        val alreadyUnloadedLocalPin = BrowserTabUi(
            id = "tab_pin_unloaded",
            spaceId = "space_personal",
            title = "Sleeping Pin",
            url = "https://news.ycombinator.com",
            isPinned = true,
            isUnloaded = true,
            pinnedUrl = "https://news.ycombinator.com",
            lastAccessedAt = now - 30L * dayMs
        )
        val staleLoadedEssential = BrowserTabUi(
            id = "tab_ess_loaded",
            spaceId = WorkspaceTabResolver.ESSENTIAL_GLOBAL_SPACE_ID,
            title = "Gmail Thread",
            url = "https://mail.google.com/mail/u/0/#inbox/123",
            essentialId = "ess_gmail",
            pinnedUrl = "https://mail.google.com",
            lastAccessedAt = now - 9L * dayMs
        )
        val staleLoadedZenPin = BrowserTabUi(
            id = "tab_zen_loaded",
            spaceId = "space_work",
            title = "Jira Ticket",
            url = "https://jira.example.com/browse/FOX-1",
            zenPinnedId = "zen_jira",
            pinnedUrl = "https://jira.example.com",
            lastAccessedAt = now - 7L * dayMs
        )

        val plan = TabAutoClearPolicy.evaluate(
            tabs = listOf(
                activeTab,
                staleRegular,
                freshRegular,
                staleMediaRegular,
                staleLoadedLocalPin,
                alreadyUnloadedLocalPin,
                staleLoadedEssential,
                staleLoadedZenPin
            ),
            activeTabId = activeTab.id,
            normalThresholdMs = TabAutoClearDuration.WEEK_1.durationMs,
            nowMs = now
        )

        assertTrue(plan.expiredPrivateTabIds.isEmpty())
        assertEquals(listOf(staleRegular.id), plan.expiredRegularTabs.map { it.id })
        assertEquals(
            listOf(staleLoadedLocalPin.id, staleLoadedEssential.id, staleLoadedZenPin.id),
            plan.expiredPinnedOrBoundTabs.map { it.id }
        )
        assertEquals(4, plan.normalClearedCount)
        assertTrue(plan.hasAnyChanges)
    }

    @Test
    fun evaluate_privateTabsAlwaysExpireAfter24HoursEvenWhenNormalAutoClearIsNever() {
        val staleActivePrivate = BrowserTabUi(
            id = "priv_active_old",
            spaceId = "space_personal",
            title = "Old Private",
            url = "https://example.org/1",
            isPrivate = true,
            lastAccessedAt = now - 25L * hourMs
        )
        val freshPrivate = BrowserTabUi(
            id = "priv_fresh",
            spaceId = "space_personal",
            title = "Fresh Private",
            url = "https://example.org/2",
            isPrivate = true,
            lastAccessedAt = now - 23L * hourMs
        )
        val mediaPrivate = BrowserTabUi(
            id = "priv_media",
            spaceId = "space_personal",
            title = "Audio Private",
            url = "https://example.org/3",
            isPrivate = true,
            isPlayingMedia = true,
            lastAccessedAt = now - 30L * hourMs
        )
        val oldNormalTab = BrowserTabUi(
            id = "norm_old",
            spaceId = "space_personal",
            title = "Old Normal",
            url = "https://example.com",
            lastAccessedAt = now - 60L * dayMs
        )

        val plan = TabAutoClearPolicy.evaluate(
            tabs = listOf(staleActivePrivate, freshPrivate, mediaPrivate, oldNormalTab),
            activeTabId = staleActivePrivate.id,
            normalThresholdMs = TabAutoClearDuration.NEVER.durationMs,
            nowMs = now
        )

        assertEquals(listOf(staleActivePrivate.id), plan.expiredPrivateTabIds)
        assertTrue(plan.expiredRegularTabs.isEmpty())
        assertTrue(plan.expiredPinnedOrBoundTabs.isEmpty())
        assertEquals(0, plan.normalClearedCount)
        assertTrue(plan.hasAnyChanges)
        assertNull(TabAutoClearPolicy.formatAutoClearMessage(0, 0))
    }

    @Test
    fun formatAutoClearMessage_handlesRegularAndPinnedPluralization() {
        assertNull(TabAutoClearPolicy.formatAutoClearMessage(0, 0))
        assertEquals("Auto-cleared 1 inactive tab", TabAutoClearPolicy.formatAutoClearMessage(1, 0))
        assertEquals("Auto-cleared 3 inactive tabs", TabAutoClearPolicy.formatAutoClearMessage(2, 1))
        assertEquals("Auto-cleared 1 inactive pinned tab", TabAutoClearPolicy.formatAutoClearMessage(0, 1))
        assertEquals("Auto-cleared 2 inactive pinned tabs", TabAutoClearPolicy.formatAutoClearMessage(0, 2))
    }
}
