package app.overbay.swiftfox.ui.browser

import android.graphics.Bitmap
import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.DownloadRecordEntity
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion

enum class BrowserOverlay {
    NONE,
    TAB_SWITCHER,
    SETTINGS,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS
}

data class BrowserTabUi(
    val id: String,
    val spaceId: String,
    val title: String,
    val url: String,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val siteModeOverride: SiteMode? = null,
    val resolvedSiteMode: SiteMode = SiteMode.MOBILE,
    val isPlayingMedia: Boolean = false,
    val lastAccessedAt: Long = System.currentTimeMillis()
) {
    val isStartPage: Boolean
        get() = url.isBlank() || url == OmniboxEngine.NEW_TAB_URL

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
    val activeOverlay: BrowserOverlay = BrowserOverlay.NONE,
    val findInPage: FindInPageState = FindInPageState(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val downloads: List<DownloadRecordEntity> = emptyList(),
    val siteOverrides: Map<String, SiteOverrideEntity> = emptyMap(),
    val isWebViewScrolledToTop: Boolean = true,
    val contextMenuHit: ContextMenuHit? = null,
    val externalAppPrompt: ExternalAppPrompt? = null,
    val httpsErrorPrompt: HttpsUpgradeErrorPrompt? = null,
    val permissionPrompt: SitePermissionPrompt? = null,
    val isInPictureInPicture: Boolean = false,
    val isFullscreenVideo: Boolean = false,
    val snackbarMessage: String? = null,
    val recentlyClosedTab: BrowserTabUi? = null
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
            val host = activeTab?.displayHost.orEmpty()
            return if (host.isBlank()) null else siteOverrides[host]
        }

    val activeTabWebDarkEffective: Boolean
        get() {
            val siteOver = activeSiteOverride?.webDarkModeOverride
            if (siteOver != null) return siteOver
            return when (settings.webDarkMode) {
                app.overbay.swiftfox.data.prefs.WebDarkModePreference.OFF -> false
                app.overbay.swiftfox.data.prefs.WebDarkModePreference.ALWAYS_ON -> true
                app.overbay.swiftfox.data.prefs.WebDarkModePreference.FOLLOW_APP -> {
                    settings.themeMode != app.overbay.swiftfox.data.prefs.ThemeMode.DAY
                }
            }
        }
}

data class TabThumbnailsSnapshot(
    val bitmaps: Map<String, Bitmap> = emptyMap()
)
