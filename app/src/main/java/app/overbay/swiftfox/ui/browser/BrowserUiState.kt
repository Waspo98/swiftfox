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
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.adblock.AdblockEngineStatus
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.reader.ReaderArticle
import app.overbay.swiftfox.features.sync.RemoteDeviceTabsGroup
import app.overbay.swiftfox.features.sync.SyncStatusSnapshot
import app.overbay.swiftfox.features.zapper.ZapperUiState

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

data class BrowserTabUi(
    val id: String,
    val spaceId: String,
    val title: String,
    val url: String,
    val isPrivate: Boolean = false,
    val isPopupWindow: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val siteModeOverride: SiteMode? = null,
    val resolvedSiteMode: SiteMode = SiteMode.MOBILE,
    val isPlayingMedia: Boolean = false,
    val isPlayingVideo: Boolean = false,
    val blockedCount: Int = 0,
    val isReaderAvailable: Boolean = false,
    val darkReaderSkippedNative: Boolean = false,
    val sessionRevision: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
) {
    val isStartPage: Boolean
        get() = !isPopupWindow && (url.isBlank() || url == OmniboxEngine.NEW_TAB_URL)

    val displayHost: String
        get() = if (isStartPage) "" else OmniboxEngine.extractHost(url).ifBlank { url }

    val isSecureHttps: Boolean
        get() = url.startsWith("https://", ignoreCase = true)
}

data class FindInPageState(
    val visible: Boolean = false,
    val query: String = "",
    val activeMatchOrdinal: Int = 0,
    val totalMatches: Int = 0
)

data class BrowserUiState(
    val settings: BrowserSettings = BrowserSettings(),
    val windowState: FoldableWindowState = FoldableWindowState(),
    val spaces: List<SpaceEntity> = emptyList(),
    val activeSpaceId: String = "space_personal",
    val essentials: List<EssentialEntity> = emptyList(),
    val tabs: List<BrowserTabUi> = emptyList(),
    val activeTabId: String? = null,
    val isPrivateView: Boolean = false,
    val isUnfoldedRailExpanded: Boolean = true,
    val isOmniboxFocused: Boolean = false,
    val omniboxQuery: String = "",
    val omniboxSuggestions: List<OmniboxSuggestion> = emptyList(),
    val isQuickControlsOpen: Boolean = false,
    val isSiteRulesOpen: Boolean = false,
    val activeOverlay: BrowserOverlay = BrowserOverlay.NONE,
    val overlayBackStack: List<BrowserOverlay> = emptyList(),
    val findInPage: FindInPageState = FindInPageState(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
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
    val syncStatus: SyncStatusSnapshot = SyncStatusSnapshot(),
    val syncedRemoteTabs: List<SyncedRemoteTabEntity> = emptyList(),
    val zenPinnedItems: List<ZenPinnedItemEntity> = emptyList(),
    val fxaAuthUrl: String? = null,
    val isQrPairingOpen: Boolean = false
) {
    val activeTab: BrowserTabUi?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: visibleTabs.firstOrNull()

    val visibleTabs: List<BrowserTabUi>
        get() = if (isPrivateView) {
            tabs.filter { it.isPrivate }
        } else {
            tabs.filter { !it.isPrivate && it.spaceId == activeSpaceId }
        }

    val activeSpace: SpaceEntity?
        get() = spaces.firstOrNull { it.id == activeSpaceId } ?: spaces.firstOrNull()

    val activeProfile
        get() = settings.profileFor(windowState.posture)

    val isActiveUrlBookmarked: Boolean
        get() {
            val url = activeTab?.url ?: return false
            if (url == OmniboxEngine.NEW_TAB_URL) return false
            return bookmarks.any { it.url == url }
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

    val activeTabWebDarkEffective: Boolean
        get() {
            val siteOver = activeSiteOverride?.webDarkModeOverride
            if (siteOver != null) return siteOver
            return when (settings.webDarkMode) {
                WebDarkModePreference.OFF -> false
                WebDarkModePreference.ALWAYS_ON -> true
                WebDarkModePreference.FOLLOW_APP -> when (settings.themeMode) {
                    ThemeMode.DAY -> false
                    ThemeMode.NIGHT -> true
                    ThemeMode.SYSTEM -> isSystemDark
                }
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

    val activeSpaceEssentials: List<EssentialEntity>
        get() {
            val separate = settings.syncZenSeparateEssentials
            if (separate == false) return essentials
            val spaceContainer = activeSpace?.zenContainerGuid?.takeIf { it.isNotBlank() }
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
}

data class TabThumbnailsSnapshot(
    val bitmaps: Map<String, Bitmap> = emptyMap()
)