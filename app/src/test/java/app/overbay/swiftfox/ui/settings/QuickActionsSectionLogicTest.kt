package app.overbay.swiftfox.ui.settings

import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsSectionLogicTest {

    @Test
    fun resolveWebDarkTileUiHighlightsOnlyOverridesAndReportsAutoResolution() {
        val followSettings = BrowserSettings(webDarkMode = WebDarkModePreference.FOLLOW_APP)

        val startPage = resolveWebDarkTileUi(
            isWebPage = false,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = null,
            isChromeDark = true,
            skippedNativeDark = false
        )
        assertFalse(startPage.enabled)
        assertFalse(startPage.highlighted)
        assertEquals("Open a page first", startPage.subtitle)
        assertEquals(WebDarkTileIcon.AUTO, startPage.icon)

        val autoDark = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = null,
            isChromeDark = true,
            skippedNativeDark = false
        )
        assertTrue(autoDark.enabled)
        assertFalse(autoDark.highlighted)
        assertEquals("Auto · Dark", autoDark.subtitle)
        assertEquals(WebDarkTileIcon.AUTO, autoDark.icon)

        val autoNative = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = null,
            isChromeDark = true,
            skippedNativeDark = true
        )
        assertFalse(autoNative.highlighted)
        assertEquals("Auto · Native", autoNative.subtitle)

        val autoLight = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = null,
            isChromeDark = false,
            skippedNativeDark = false
        )
        assertFalse(autoLight.highlighted)
        assertEquals("Auto · Light", autoLight.subtitle)

        val autoJsOff = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = false,
            settings = followSettings,
            siteOverride = null,
            isChromeDark = true,
            skippedNativeDark = false
        )
        assertFalse(autoJsOff.highlighted)
        assertEquals("Auto · JS off", autoJsOff.subtitle)

        val forceDark = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = SiteOverrideEntity(host = "example.com", webDarkModeOverride = true),
            isChromeDark = false,
            skippedNativeDark = false
        )
        assertTrue(forceDark.enabled)
        assertTrue(forceDark.highlighted)
        assertEquals("Force dark", forceDark.subtitle)
        assertEquals(WebDarkTileIcon.DARK, forceDark.icon)

        val forceLight = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = SiteOverrideEntity(host = "example.com", webDarkModeOverride = false),
            isChromeDark = true,
            skippedNativeDark = false
        )
        assertTrue(forceLight.enabled)
        assertTrue(forceLight.highlighted)
        assertEquals("Force light", forceLight.subtitle)
        assertEquals(WebDarkTileIcon.LIGHT, forceLight.icon)

        val hostlessPopup = resolveWebDarkTileUi(
            isWebPage = true,
            jsEnabledForSite = true,
            settings = followSettings,
            siteOverride = null,
            isChromeDark = true,
            skippedNativeDark = false,
            hasSiteHost = false
        )
        assertFalse(hostlessPopup.enabled)
        assertFalse(hostlessPopup.highlighted)
        assertEquals("Unavailable here", hostlessPopup.subtitle)
        assertEquals(WebDarkTileIcon.AUTO, hostlessPopup.icon)
    }

    @Test
    fun buildCompactQuickActionRowsChunksIntoBalancedTwoColumnRowsAndSetsCues() {
        assertTrue(
            buildCompactQuickActionRows(
                isWebPage = false,
                isReaderAvailable = true,
                hasActiveSiteRules = true,
                showPinEssential = false,
                showSendToZen = true
            ).isEmpty()
        )

        val privateRows = buildCompactQuickActionRows(
            isWebPage = true,
            isReaderAvailable = false,
            hasActiveSiteRules = false,
            showPinEssential = false,
            showSendToZen = false
        )
        assertEquals(listOf(2, 2), privateRows.map { it.size })
        assertEquals(
            listOf(
                CompactQuickActionId.FIND_IN_PAGE,
                CompactQuickActionId.SITE_RULES,
                CompactQuickActionId.READER_MODE,
                CompactQuickActionId.PRINT
            ),
            privateRows.flatten().map { it.id }
        )

        val unsyncedRows = buildCompactQuickActionRows(
            isWebPage = true,
            isReaderAvailable = true,
            hasActiveSiteRules = false,
            showPinEssential = true,
            showSendToZen = false
        )
        assertEquals(listOf(2, 2, 1), unsyncedRows.map { it.size })
        assertEquals(
            listOf(
                CompactQuickActionId.FIND_IN_PAGE,
                CompactQuickActionId.SITE_RULES,
                CompactQuickActionId.READER_MODE,
                CompactQuickActionId.PIN_ESSENTIAL,
                CompactQuickActionId.PRINT
            ),
            unsyncedRows.flatten().map { it.id }
        )

        val siteRulesUnsynced = unsyncedRows[0][1]
        assertFalse(siteRulesUnsynced.highlightedCue)
        assertNull(siteRulesUnsynced.stateAnnouncement)

        val readerUnsynced = unsyncedRows[1][0]
        assertTrue(readerUnsynced.highlightedCue)
        assertEquals("Article ready", readerUnsynced.stateAnnouncement)

        val syncedRows = buildCompactQuickActionRows(
            isWebPage = true,
            isReaderAvailable = false,
            hasActiveSiteRules = true,
            showPinEssential = true,
            showSendToZen = true
        )
        assertEquals(listOf(2, 2, 2), syncedRows.map { it.size })
        assertEquals(CompactQuickActionId.SEND_TO_ZEN, syncedRows[2][1].id)
        assertTrue(syncedRows[0][1].highlightedCue)
        assertEquals("Custom rules active", syncedRows[0][1].stateAnnouncement)
        assertFalse(syncedRows[1][0].highlightedCue)
    }
}
