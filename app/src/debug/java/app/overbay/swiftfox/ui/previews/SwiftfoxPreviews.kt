package app.overbay.swiftfox.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.features.foldable.FoldableWindowState
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.features.sync.SyncStatusSnapshot
import app.overbay.swiftfox.ui.browser.BrowserShellLayout
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.TabThumbnailsSnapshot
import app.overbay.swiftfox.ui.settings.QuickActionsSection
import app.overbay.swiftfox.ui.sync.SyncOverlay
import app.overbay.swiftfox.ui.tabs.SpacesTabSwitcher
import app.overbay.swiftfox.ui.theme.SwiftfoxTheme

private fun previewState(
    posture: DevicePosture = DevicePosture.FOLDED,
    startPageActive: Boolean = true
): BrowserUiState {
    val spaces = listOf(
        SpaceEntity("space_personal", "Personal", "home", null, 0),
        SpaceEntity("space_work", "Work", "work", "space_work", 1),
        SpaceEntity("space_reading", "Reading", "book", null, 2)
    )
    val essentials = listOf(
        EssentialEntity("e1", null, "Google", "https://www.google.com", "google.com", 0),
        EssentialEntity("e2", null, "GitHub", "https://github.com", "github.com", 1),
        EssentialEntity("e3", null, "YouTube", "https://m.youtube.com", "youtube.com", 2),
        EssentialEntity("e4", null, "Reddit", "https://www.reddit.com", "reddit.com", 3),
        EssentialEntity("e5", null, "Wikipedia", "https://en.wikipedia.org", "wikipedia.org", 4),
        EssentialEntity("e6", null, "Hacker News", "https://news.ycombinator.com", "news.ycombinator.com", 5)
    )
    val tabs = listOf(
        BrowserTabUi(
            id = "t1",
            spaceId = "space_personal",
            title = "New Tab",
            url = "about:blank",
            resolvedSiteMode = if (posture == DevicePosture.UNFOLDED) SiteMode.DESKTOP else SiteMode.MOBILE
        ),
        BrowserTabUi(
            id = "t2",
            spaceId = "space_personal",
            title = "Ars Technica — Technology Lab",
            url = "https://arstechnica.com",
            canGoBack = true,
            resolvedSiteMode = SiteMode.MOBILE
        ),
        BrowserTabUi(
            id = "t3",
            spaceId = "space_personal",
            title = "Zen Browser — Calm Web",
            url = "https://zen-browser.app",
            resolvedSiteMode = SiteMode.DESKTOP
        ),
        BrowserTabUi(
            id = "t4",
            spaceId = "space_personal",
            title = "Android Developers",
            url = "https://developer.android.com",
            resolvedSiteMode = SiteMode.DESKTOP
        )
    )
    return BrowserUiState(
        settings = BrowserSettings(),
        windowState = FoldableWindowState(
            posture = posture,
            windowWidthDp = if (posture == DevicePosture.UNFOLDED) 673 else 412
        ),
        spaces = spaces,
        activeSpaceId = "space_personal",
        essentials = essentials,
        tabs = tabs,
        activeTabId = if (startPageActive) "t1" else "t2"
    )
}

@Preview(name = "Folded Dark Start", device = Devices.PHONE, showSystemUi = true)
@Composable
fun FoldedStartDarkPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.DARK,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        BrowserShellLayout(
            state = previewState(DevicePosture.FOLDED, startPageActive = true),
            onFocusOmnibox = {},
            onFocusSearchFresh = {},
            onGoBack = {},
            onGoForward = {},
            onReloadOrStop = {},
            onOpenReaderMode = {},
            onOpenTabSwitcher = {},
            onOpenSettings = {},
            onSwitchAdjacentTab = {},
            onToggleWorkspaceSidebar = {},
            onToggleUnfoldedRail = {},
            onCloseFoldedSidebar = {},
            onShareActiveTab = {},
            onSelectSpace = {},
            onTogglePrivate = {},
            onCreateSpace = { _, _, _ -> },
            onDeleteSpace = {},
            onSelectTab = {},
            onCloseTab = {},
            onCreateTab = {},
            onOpenUrl = {},
            onAddEssential = { _, _ -> },
            onRemoveEssential = {},
            onFindQueryChange = {},
            onFindNext = {},
            onCloseFind = {},
            onAdjustZapperScope = {},
            onSetZapperScopeDepth = {},
            onSetZapperPreview = {},
            onZapOnce = {},
            onBlockPermanently = {},
            onUndoTemporaryZap = {},
            onStopZapper = {},
            tabWebContent = { _, _ -> }
        )
    }
}

@Preview(name = "Folded Day Start", device = Devices.PHONE, showSystemUi = true)
@Composable
fun FoldedStartDayPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.DAY,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        BrowserShellLayout(
            state = previewState(DevicePosture.FOLDED, startPageActive = true),
            onFocusOmnibox = {},
            onFocusSearchFresh = {},
            onGoBack = {},
            onGoForward = {},
            onReloadOrStop = {},
            onOpenReaderMode = {},
            onOpenTabSwitcher = {},
            onOpenSettings = {},
            onSwitchAdjacentTab = {},
            onToggleWorkspaceSidebar = {},
            onToggleUnfoldedRail = {},
            onCloseFoldedSidebar = {},
            onShareActiveTab = {},
            onSelectSpace = {},
            onTogglePrivate = {},
            onCreateSpace = { _, _, _ -> },
            onDeleteSpace = {},
            onSelectTab = {},
            onCloseTab = {},
            onCreateTab = {},
            onOpenUrl = {},
            onAddEssential = { _, _ -> },
            onRemoveEssential = {},
            onFindQueryChange = {},
            onFindNext = {},
            onCloseFind = {},
            onAdjustZapperScope = {},
            onSetZapperScopeDepth = {},
            onSetZapperPreview = {},
            onZapOnce = {},
            onBlockPermanently = {},
            onUndoTemporaryZap = {},
            onStopZapper = {},
            tabWebContent = { _, _ -> }
        )
    }
}

@Preview(
    name = "Folded AMOLED 1.3x Font",
    device = Devices.PHONE,
    fontScale = 1.3f,
    showSystemUi = true
)
@Composable
fun FoldedAmoledLargeFontPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.AMOLED,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        BrowserShellLayout(
            state = previewState(DevicePosture.FOLDED, startPageActive = true),
            onFocusOmnibox = {},
            onFocusSearchFresh = {},
            onGoBack = {},
            onGoForward = {},
            onReloadOrStop = {},
            onOpenReaderMode = {},
            onOpenTabSwitcher = {},
            onOpenSettings = {},
            onSwitchAdjacentTab = {},
            onToggleWorkspaceSidebar = {},
            onToggleUnfoldedRail = {},
            onCloseFoldedSidebar = {},
            onShareActiveTab = {},
            onSelectSpace = {},
            onTogglePrivate = {},
            onCreateSpace = { _, _, _ -> },
            onDeleteSpace = {},
            onSelectTab = {},
            onCloseTab = {},
            onCreateTab = {},
            onOpenUrl = {},
            onAddEssential = { _, _ -> },
            onRemoveEssential = {},
            onFindQueryChange = {},
            onFindNext = {},
            onCloseFind = {},
            onAdjustZapperScope = {},
            onSetZapperScopeDepth = {},
            onSetZapperPreview = {},
            onZapOnce = {},
            onBlockPermanently = {},
            onUndoTemporaryZap = {},
            onStopZapper = {},
            tabWebContent = { _, _ -> }
        )
    }
}

@Preview(name = "Folded Sidebar Drawer", device = Devices.PHONE, showSystemUi = true)
@Composable
fun FoldedSidebarDrawerPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.DARK,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        BrowserShellLayout(
            state = previewState(DevicePosture.FOLDED, startPageActive = true).copy(isFoldedSidebarOpen = true),
            onFocusOmnibox = {},
            onFocusSearchFresh = {},
            onGoBack = {},
            onGoForward = {},
            onReloadOrStop = {},
            onOpenReaderMode = {},
            onOpenTabSwitcher = {},
            onOpenSettings = {},
            onSwitchAdjacentTab = {},
            onToggleWorkspaceSidebar = {},
            onToggleUnfoldedRail = {},
            onCloseFoldedSidebar = {},
            onShareActiveTab = {},
            onSelectSpace = {},
            onTogglePrivate = {},
            onCreateSpace = { _, _, _ -> },
            onDeleteSpace = {},
            onSelectTab = {},
            onCloseTab = {},
            onCreateTab = {},
            onOpenUrl = {},
            onAddEssential = { _, _ -> },
            onRemoveEssential = {},
            onFindQueryChange = {},
            onFindNext = {},
            onCloseFind = {},
            onAdjustZapperScope = {},
            onSetZapperScopeDepth = {},
            onSetZapperPreview = {},
            onZapOnce = {},
            onBlockPermanently = {},
            onUndoTemporaryZap = {},
            onStopZapper = {},
            tabWebContent = { _, _ -> }
        )
    }
}

@Preview(name = "Settings Quick Actions", device = Devices.PHONE, showBackground = true)
@Composable
fun QuickActionsSectionPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.DARK,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            QuickActionsSection(
                state = previewState(DevicePosture.FOLDED, startPageActive = false),
                onGoBack = {},
                onGoForward = {},
                onReloadOrStop = {},
                onToggleBookmark = {},
                onShareCleanUrl = {},
                onPrintPage = {},
                onToggleSiteMode = {},
                onToggleShieldAdblock = {},
                onCycleWebDarkMode = {},
                onEnterPip = {},
                onLaunchZapper = {},
                onOpenSiteRules = {},
                onFindInPage = {},
                onPinEssential = {},
                onSendToZenSpace = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "Spaces Tab Switcher", device = Devices.PHONE, showSystemUi = true)
@Composable
fun SpacesTabSwitcherPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.DARK,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        SpacesTabSwitcher(
            state = previewState(DevicePosture.FOLDED, startPageActive = false),
            thumbnails = TabThumbnailsSnapshot(),
            onClose = {},
            onSelectTab = {},
            onCloseTab = {},
            onCreateTab = {},
            onSelectSpace = {},
            onTogglePrivate = {},
            onCreateSpace = { _, _, _ -> }
        )
    }
}

@Preview(name = "Unfolded Foldable Shell", device = Devices.FOLDABLE, showSystemUi = true)
@Composable
fun UnfoldedFoldableBrowserPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.DARK,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        BrowserShellLayout(
            state = previewState(DevicePosture.UNFOLDED, startPageActive = false),
            onFocusOmnibox = {},
            onFocusSearchFresh = {},
            onGoBack = {},
            onGoForward = {},
            onReloadOrStop = {},
            onOpenReaderMode = {},
            onOpenTabSwitcher = {},
            onOpenSettings = {},
            onSwitchAdjacentTab = {},
            onToggleWorkspaceSidebar = {},
            onToggleUnfoldedRail = {},
            onCloseFoldedSidebar = {},
            onShareActiveTab = {},
            onSelectSpace = {},
            onTogglePrivate = {},
            onCreateSpace = { _, _, _ -> },
            onDeleteSpace = {},
            onSelectTab = {},
            onCloseTab = {},
            onCreateTab = {},
            onOpenUrl = {},
            onAddEssential = { _, _ -> },
            onRemoveEssential = {},
            onFindQueryChange = {},
            onFindNext = {},
            onCloseFind = {},
            onAdjustZapperScope = {},
            onSetZapperScopeDepth = {},
            onSetZapperPreview = {},
            onZapOnce = {},
            onBlockPermanently = {},
            onUndoTemporaryZap = {},
            onStopZapper = {},
            tabWebContent = { tab, _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.displayHost.ifBlank { "arstechnica.com" }, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "120Hz Android WebView Viewport · Desktop Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}

@Preview(name = "Firefox & Zen Sync", device = Devices.PHONE, showSystemUi = true)
@Composable
fun SyncOverlayPreview() {
    SwiftfoxTheme(
        themeMode = ThemeMode.NIGHT,
        nightSurfaceStyle = NightSurfaceStyle.DARK,
        accentPreset = AccentPreset.FOX_AMBER
    ) {
        SyncOverlay(
            state = previewState(DevicePosture.FOLDED, startPageActive = true).copy(
                syncStatus = SyncStatusSnapshot(
                    accountState = SyncAccountState.Connected(
                        email = "user@example.com",
                        displayName = "Alex"
                    ),
                    lastSyncAtMs = System.currentTimeMillis() - 120_000L,
                    lastSyncSummary = "Synced 48 bookmarks · 12 remote tabs · 3 Zen Spaces"
                )
            ),
            onBack = {},
            onSignIn = {},
            onPairQr = {},
            onSyncNow = {},
            onSignOut = {},
            onOpenSyncedTabs = {},
            onToggleSyncBookmarks = {},
            onToggleSyncHistory = {},
            onToggleSyncTabs = {},
            onToggleSyncZenSpaces = {},
            onChangeSyncInterval = {},
            onImportBookmarksHtml = {},
            onExportBookmarksHtml = {}
        )
    }
}
