package app.overbay.swiftfox.ui.browser

import android.graphics.Bitmap
import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.DownloadRecordEntity
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SyncedRemoteTabEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.adblock.AdblockEngineStatus
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.reader.ReaderArticle
import app.overbay.swiftfox.features.sync.RemoteDeviceTabsGroup
import app.overbay.swiftfox.features.sync.SyncStatusSnapshot
import app.overbay.swiftfox.features.zapper.ZapperUiState
import app.overbay.swiftfox.ui.tabs.AutoClearedTabsSnapshot
import app.overbay.swiftfox.ui.tabs.PendingPinCloseTarget

enum class BrowserOverlay {
    NONE,
    TAB_SWITCHER,
    SETTINGS,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    FILTER_LISTS,
    USERSCRIPTS,
    ZAPPED_RULES,
    SYNC,
    SYNCED_TABS
}

enum class SettingsCategory(val title: String) {
    APPEARANCE("Appearance & Theme"),
    FOLDABLE_LAYOUT("Foldable & Layout"),
    TABS_WORKSPACES("Tabs & Workspaces"),
    ADBLOCK_SCRIPTS("Ad Blocking & Scripts"),
    SEARCH_GESTURES("Search & Gestures"),
    PRIVACY_MEDIA("Privacy, Links & Media")
}

data class MainFrameLoadError(
    val url: String,
    val host: String,
    val description: String,
    val resolvedIps: List<String>? = null,
    val hasSinkholeOrLoopbackIp: Boolean = false,
    val directProbeSummary: String? = null
)

data class BrowserTabUi(
    val id: String,
    val spaceId: String,
    val title: String,
    val url: String,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val essentialId: String? = null,
    val zenPinnedId: String? = null,
    val isUnloaded: Boolean = false,
    val pinnedUrl: String? = null,
    val customIcon: String? = null,
    val customTitle: String? = null,
    val sortOrder: Int = 0,
    val isPopupWindow: Boolean = false,
    val parentTabId: String? = null,
    val profileOverride: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val siteModeOverride: SiteMode? = null,
    val resolvedSiteMode: SiteMode = SiteMode.MOBILE,
    val isPlayingMedia: Boolean = false,
    val isPlayingVideo: Boolean = false,
    val isReaderAvailable: Boolean = false,
    val darkReaderSkippedNative: Boolean = false,
    val loadError: MainFrameLoadError? = null,
    val sessionRevision: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
) {
    val isEssentialTab: Boolean
        get() = essentialId != null

    val isBoundWorkspaceTab: Boolean
        get() = essentialId != null || zenPinnedId != null

    val isStartPage: Boolean = !isPopupWindow && (url.isBlank() || url == OmniboxEngine.NEW_TAB_URL)

    val displayHost: String = if (isStartPage) "" else OmniboxEngine.extractHost(url).ifBlank { url }

    val displayTitle: String
        get() = customTitle?.takeIf { it.isNotBlank() } ?: title.ifBlank { "New Tab" }

    val isSecureHttps: Boolean = url.startsWith("https://", ignoreCase = true)
}

data class FindInPageState(
    val visible: Boolean = false,
    val query: String = "",
    val activeMatchOrdinal: Int = 0,
    val totalMatches: Int = 0
)

data class BrowserUiState(
    val settings: BrowserSettings = BrowserSettings(),
    val hasLoadedSettings: Boolean = false,
    val windowState: FoldableWindowState = FoldableWindowState(),
    val spaces: List<SpaceEntity> = emptyList(),
    val activeSpaceId: String = "space_personal",
    val essentials: List<EssentialEntity> = emptyList(),
    val tabs: List<BrowserTabUi> = emptyList(),
    val activeTabId: String? = null,
    val isPrivateView: Boolean = false,
    val isFoldedSidebarOpen: Boolean = false,
    val isUnfoldedRailExpanded: Boolean = true,
    val isOmniboxFocused: Boolean = false,
    val isSiteRulesOpen: Boolean = false,
    val activeOverlay: BrowserOverlay = BrowserOverlay.NONE,
    val activeSettingsCategory: SettingsCategory? = null,
    val overlayBackStack: List<BrowserOverlay> = emptyList(),
    val findInPage: FindInPageState = FindInPageState(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val bookmarkedUrls: Set<String> = emptySet(),
    val history: List<HistoryEntity> = emptyList(),
    val downloads: List<DownloadRecordEntity> = emptyList(),
    val siteOverrides: Map<String, SiteOverrideEntity> = emptyMap(),
    val privateSiteOverrides: Map<String, SiteOverrideEntity> = emptyMap(),
    val filterLists: List<FilterListEntity> = emptyList(),
    val isUpdatingFilterLists: Boolean = false,
    val adblockStatus: AdblockEngineStatus = AdblockEngineStatus(),
    val zappedRules: List<ZappedRuleEntity> = emptyList(),
    val privateZappedRules: Map<String, List<ZappedRuleEntity>> = emptyMap(),
    val zapper: ZapperUiState = ZapperUiState(),
    val userscripts: List<UserscriptEntity> = emptyList(),
    val readerArticle: ReaderArticle? = null,
    val isReaderLoading: Boolean = false,
    val contextMenuHit: ContextMenuHit? = null,
    val externalAppPrompt: ExternalAppPrompt? = null,
    val httpsErrorPrompt: HttpsUpgradeErrorPrompt? = null,
    val permissionPrompt: SitePermissionPrompt? = null,
    val isInPictureInPicture: Boolean = false,
    val isFullscreenVideo: Boolean = false,
    val isSystemDark: Boolean = true,
    val snackbarMessage: String? = null,
    val snackbarId: Long = 0L,
    val recentlyClosedTab: BrowserTabUi? = null,
    val recentlyResetSiteOverride: SiteOverrideEntity? = null,
    val recentlyAutoClearedTabs: AutoClearedTabsSnapshot? = null,
    val syncStatus: SyncStatusSnapshot = SyncStatusSnapshot(),
    val syncedRemoteTabs: List<SyncedRemoteTabEntity> = emptyList(),
    val zenPinnedItems: List<ZenPinnedItemEntity> = emptyList(),
    val pendingPinCloseTarget: PendingPinCloseTarget? = null,
    val fxaAuthUrl: String? = null,
    val isQrPairingOpen: Boolean = false
) {
    val activeTab: BrowserTabUi?
        get() = tabs.firstOrNull { it.id == activeTabId && !it.isUnloaded }
            ?: visibleRegularTabs.firstOrNull()
            ?: visibleTabs.firstOrNull { !it.isUnloaded }

    val visibleTabs: List<BrowserTabUi>
        get() = if (isPrivateView) {
            tabs.filter { it.isPrivate }
        } else {
            val spaceTabs = tabs.filter { !it.isPrivate && !it.isBoundWorkspaceTab && it.spaceId == activeSpaceId }
            val pinned = spaceTabs.filter { it.isPinned }
            val regular = spaceTabs.filter { !it.isPinned }
            pinned + regular
        }

    val visiblePinnedTabs: List<BrowserTabUi>
        get() = if (isPrivateView) {
            emptyList()
        } else {
            tabs.filter { !it.isPrivate && it.isPinned && !it.isBoundWorkspaceTab && it.spaceId == activeSpaceId }
        }

    val visibleRegularTabs: List<BrowserTabUi>
        get() = if (isPrivateView) {
            tabs.filter { it.isPrivate }
        } else {
            tabs.filter { !it.isPrivate && !it.isPinned && !it.isBoundWorkspaceTab && it.spaceId == activeSpaceId }
        }

    fun tabForEssential(essentialId: String): BrowserTabUi? =
        tabs.firstOrNull { !it.isPrivate && it.essentialId == essentialId }

    fun tabForZenPinned(zenPinnedId: String): BrowserTabUi? =
        tabs.firstOrNull { !it.isPrivate && it.zenPinnedId == zenPinnedId }

    fun loadedEssentialsForSpace(spaceId: String): List<BrowserTabUi> =
        if (isPrivateView) {
            emptyList()
        } else {
            essentialsForSpace(spaceId).mapNotNull { ess -> tabForEssential(ess.id) }
        }

    val loadedActiveEssentials: List<BrowserTabUi>
        get() = loadedEssentialsForSpace(activeSpaceId)

    /**
     * Tabs rendered as visual preview cards in the Tab Switcher: strictly actively open/loaded
     * tabs in the current view (loaded Essentials, loaded local pinned tabs, loaded Zen pinned
     * tabs, and open regular tabs), excluding unloaded pinned tabs.
     */
    val tabSwitcherTabs: List<BrowserTabUi>
        get() = loadedNavigableTabs

    /**
     * Count badge shown on each Space pill inside the Tab Switcher, matching the exact set of
     * actively open tab cards rendered for that Space.
     */
    fun tabSwitcherCountForSpace(spaceId: String): Int {
        val loadedEssCount = essentialsForSpace(spaceId)
            .mapNotNull { ess -> tabForEssential(ess.id)?.id }
            .distinct()
            .size
        val spaceZenIds = zenPinnedItems
            .asSequence()
            .filter { it.spaceId == spaceId }
            .mapTo(HashSet()) { it.id }
        val spaceLoadedCount = tabs.count { tab ->
            !tab.isPrivate && !tab.isUnloaded && !tab.isEssentialTab && (
                if (tab.zenPinnedId != null) tab.zenPinnedId in spaceZenIds else tab.spaceId == spaceId
            )
        }
        return loadedEssCount + spaceLoadedCount
    }

    val loadedActiveZenPinned: List<BrowserTabUi>
        get() = if (isPrivateView) {
            emptyList()
        } else {
            activeSpaceZenPinned.mapNotNull { pin -> tabForZenPinned(pin.id) }
        }

    /**
     * All currently loaded tabs reachable in the current view (used for URL-bar swipe switching,
     * directional tab transitions, collapsed rail, and live tab counts).
     */
    val loadedNavigableTabs: List<BrowserTabUi>
        get() = if (isPrivateView) {
            tabs.filter { it.isPrivate }
        } else {
            loadedActiveEssentials +
                visiblePinnedTabs.filter { !it.isUnloaded } +
                loadedActiveZenPinned +
                visibleRegularTabs
        }

    /**
     * Zero-list-allocation navigable tab counter for toolbar count badges.
     */
    fun loadedNavigableTabCount(): Int {
        if (isPrivateView) {
            var count = 0
            for (tab in tabs) {
                if (tab.isPrivate) count++
            }
            return count
        }
        val activeEss = essentialsForSpace(activeSpaceId)
        val activeEssIds = if (activeEss.isEmpty()) emptySet() else activeEss.mapTo(HashSet(activeEss.size * 2)) { it.id }
        val activeZenIds = if (zenPinnedItems.isEmpty()) {
            emptySet()
        } else {
            HashSet<String>().also { set ->
                for (pin in zenPinnedItems) {
                    if (pin.spaceId == activeSpaceId) set.add(pin.id)
                }
            }
        }
        var count = 0
        for (tab in tabs) {
            if (tab.isPrivate || tab.isUnloaded) continue
            val essId = tab.essentialId
            val zenId = tab.zenPinnedId
            val inCurrentView = when {
                essId != null -> essId in activeEssIds
                zenId != null -> zenId in activeZenIds
                else -> tab.spaceId == activeSpaceId
            }
            if (inCurrentView) count++
        }
        return count
    }

    val activeSpace: SpaceEntity?
        get() = spaces.firstOrNull { it.id == activeSpaceId } ?: spaces.firstOrNull()

    val activeProfile
        get() = settings.profileFor(windowState.posture)

    val isActiveUrlBookmarked: Boolean
        get() {
            val url = activeTab?.url ?: return false
            if (url == OmniboxEngine.NEW_TAB_URL) return false
            val canonical = OmniboxEngine.canonicalizeUrlSchemeAndHost(url)
            return if (bookmarkedUrls.isNotEmpty() || bookmarks.isEmpty()) {
                url in bookmarkedUrls || (canonical != url && canonical in bookmarkedUrls)
            } else {
                bookmarks.any { it.url == url || it.url == canonical }
            }
        }

    val activeSiteOverride: SiteOverrideEntity?
        get() {
            val tab = activeTab
            val host = OmniboxEngine.normalizeHost(tab?.displayHost)
            if (host.isBlank()) return null
            val isPriv = tab?.isPrivate ?: isPrivateView
            return if (isPriv) {
                privateSiteOverrides[host] ?: siteOverrides[host]
            } else {
                siteOverrides[host]
            }
        }

    val remoteTabGroups: List<RemoteDeviceTabsGroup>
        get() = syncedRemoteTabs
            .groupBy { it.deviceId }
            .map { (deviceId, items) ->
                val first = items.first()
                RemoteDeviceTabsGroup(
                    deviceId = deviceId,
                    deviceName = first.deviceName,
                    deviceType = first.deviceType,
                    tabs = items.sortedBy { it.position }
                )
            }

    val activeSpaceZenPinned: List<ZenPinnedItemEntity>
        get() = zenPinnedItems.filter { it.spaceId == activeSpaceId }

    fun essentialsForSpace(spaceId: String): List<EssentialEntity> {
        val separate = settings.syncZenSeparateEssentials
        if (separate == false) return essentials
        val spaceContainer = spaces.firstOrNull { it.id == spaceId }?.zenContainerGuid?.takeIf { it.isNotBlank() }
        val shouldSeparate = separate ?: essentials.any {
            !it.zenContainerGuid.isNullOrBlank() && it.zenContainerGuid != "default"
        }
        return if (!shouldSeparate) {
            essentials
        } else {
            essentials.filter { ess ->
                ess.syncGuid == null ||
                    ess.zenContainerGuid.isNullOrBlank() ||
                    ess.zenContainerGuid == (spaceContainer ?: "default")
            }
        }
    }

    val activeSpaceEssentials: List<EssentialEntity>
        get() = essentialsForSpace(activeSpaceId)
}

data class TabThumbnailsSnapshot(
    val bitmaps: Map<String, Bitmap> = emptyMap()
)