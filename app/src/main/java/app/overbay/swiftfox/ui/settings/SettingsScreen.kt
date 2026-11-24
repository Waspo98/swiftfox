package app.overbay.swiftfox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.swiftfox.BuildConfig
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.data.prefs.WebDarkEngine
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.ui.browser.BrowserOverlay
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.BrowserViewModel
import app.overbay.swiftfox.ui.theme.BetaChannelPill
import app.overbay.swiftfox.ui.theme.SwiftfoxAppIcon
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.SwiftfoxWordmark
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.NumberFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    primaryAccentHex: String,
    onShareCleanUrl: () -> Unit,
    onPrintPage: () -> Unit,
    onEnterPip: () -> Unit,
    onBack: () -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit
) {
    val settings = state.settings
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAllSiteOverrides by remember { mutableStateOf(false) }
    var customTemplateDraft by remember { mutableStateOf(settings.customSearchUrlTemplate) }
    var lastCommittedTemplate by remember { mutableStateOf(settings.customSearchUrlTemplate) }
    LaunchedEffect(settings.customSearchUrlTemplate) {
        if (settings.customSearchUrlTemplate != lastCommittedTemplate) {
            lastCommittedTemplate = settings.customSearchUrlTemplate
            if (customTemplateDraft.trim() != settings.customSearchUrlTemplate) {
                customTemplateDraft = settings.customSearchUrlTemplate
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val remoteTabGroups = remember(state.syncedRemoteTabs) { state.remoteTabGroups }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Quick Actions for the active tab/page
            item(key = "quick_actions", contentType = "settings_section") {
                QuickActionsSection(
                    state = state,
                    onGoBack = {
                        viewModel.setOverlay(BrowserOverlay.NONE)
                        viewModel.goBack()
                    },
                    onGoForward = {
                        viewModel.setOverlay(BrowserOverlay.NONE)
                        viewModel.goForward()
                    },
                    onToggleBookmark = { viewModel.toggleBookmarkForActiveTab() },
                    onShareCleanUrl = {
                        viewModel.setOverlay(BrowserOverlay.NONE)
                        onShareCleanUrl()
                    },
                    onPrintPage = {
                        viewModel.setOverlay(BrowserOverlay.NONE)
                        onPrintPage()
                    },
                    onToggleSiteMode = { viewModel.toggleActiveTabSiteMode() },
                    onToggleWebDarkMode = { viewModel.toggleActiveSiteWebDarkMode() },
                    onToggleShieldAdblock = { viewModel.toggleActiveSiteAdblock() },
                    onEnterPip = {
                        viewModel.setOverlay(BrowserOverlay.NONE)
                        onEnterPip()
                    },
                    onLaunchZapper = { viewModel.startElementZapper(primaryAccentHex) },
                    onOpenReaderMode = { viewModel.openReaderMode() },
                    onOpenSiteRules = { viewModel.toggleSiteRulesSheet(true) },
                    onFindInPage = { viewModel.openFindInPage() },
                    onPinEssential = { viewModel.pinCurrentTabToEssentials() },
                    onSendToZenSpace = { viewModel.sendActiveTabToZenSpace() }
                )
            }

            // Library (Bookmarks, History, Downloads)
            item(key = "library", contentType = "settings_section") {
                SettingsSection(title = "Library") {
                    SettingsNavigationRow(
                        title = "Bookmarks",
                        subtitle = "${state.bookmarks.size} saved · Import or export Chrome HTML",
                        onClick = { viewModel.setOverlay(BrowserOverlay.BOOKMARKS) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    SettingsNavigationRow(
                        title = "History",
                        subtitle = "${state.history.size} visited pages",
                        onClick = { viewModel.setOverlay(BrowserOverlay.HISTORY) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    SettingsNavigationRow(
                        title = "Downloads",
                        subtitle = "${state.downloads.size} downloads",
                        onClick = { viewModel.setOverlay(BrowserOverlay.DOWNLOADS) }
                    )
                }
            }

            // Account, Sync & Interop
            item(key = "account_sync", contentType = "settings_section") {
            SettingsSection(title = "Account & Sync") {
                val syncSubtitle = when (val acct = state.syncStatus.accountState) {
                    is SyncAccountState.Connected -> {
                        val who = acct.email ?: acct.displayName ?: "Mozilla Account"
                        val suffix = state.syncStatus.lastSyncSummary?.let { " · $it" }.orEmpty()
                        "$who$suffix"
                    }
                    is SyncAccountState.NeedsReauth -> acct.message
                    SyncAccountState.Authenticating -> "Completing Mozilla sign-in..."
                    SyncAccountState.SignedOut -> "Sync bookmarks, history, open tabs & Zen Spaces"
                }
                SettingsNavigationRow(
                    title = "Firefox & Zen Sync",
                    subtitle = syncSubtitle,
                    onClick = { viewModel.setOverlay(BrowserOverlay.SYNC) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SettingsNavigationRow(
                    title = "Synced Open Tabs",
                    subtitle = "${remoteTabGroups.size} devices · ${state.syncedRemoteTabs.size} open tabs",
                    onClick = { viewModel.setOverlay(BrowserOverlay.SYNCED_TABS) }
                )
            }
            }

            // Appearance & Theme
            item(key = "appearance_theme", contentType = "settings_section") {
            SettingsSection(title = "Appearance & Theme") {
                SettingGroup(label = "Theme mode") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            SelectionChip(
                                label = mode.label,
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                        }
                    }
                }

                SettingGroup(label = "Night surface") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NightSurfaceStyle.entries.forEach { style ->
                            SelectionChip(
                                label = style.label,
                                selected = settings.nightSurfaceStyle == style,
                                onClick = { viewModel.setNightSurfaceStyle(style) }
                            )
                        }
                    }
                }

                SettingGroup(label = "Accent palette") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val selected = !settings.useDynamicColor && settings.accentPreset == preset
                            Surface(
                                onClick = {
                                    viewModel.setUseDynamicColor(false)
                                    viewModel.setAccentPreset(preset)
                                },
                                shape = RoundedCornerShape(22.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.previewHex)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(preset.label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                SettingsSwitchRow(
                    title = "Material You dynamic color",
                    subtitle = "Match Android wallpaper palette (keeps AMOLED black if selected)",
                    checked = settings.useDynamicColor,
                    onCheckedChange = { viewModel.setUseDynamicColor(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingGroup(label = "Web content dark mode") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WebDarkModePreference.entries.forEach { pref ->
                            SelectionChip(
                                label = pref.label,
                                selected = settings.webDarkMode == pref,
                                onClick = { viewModel.setWebDarkMode(pref) }
                            )
                        }
                    }
                }

                SettingGroup(label = "Web dark engine") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WebDarkEngine.entries.forEach { engine ->
                            SelectionChip(
                                label = engine.label,
                                selected = settings.webDarkEngine == engine,
                                onClick = { viewModel.setWebDarkEngine(engine) }
                            )
                        }
                    }
                }

                if (settings.webDarkEngine == WebDarkEngine.DARK_READER) {
                    SettingsSwitchRow(
                        title = "Dark Reader AMOLED (#000000)",
                        subtitle = "Render pure black webpage backgrounds while preserving images",
                        checked = settings.darkReaderAmoled,
                        onCheckedChange = { viewModel.setDarkReaderAmoled(it) }
                    )
                    SettingsSwitchRow(
                        title = "Skip native dark sites",
                        subtitle = "Leave sites already advertising dark mode untouched",
                        checked = settings.darkReaderSkipNativeDark,
                        onCheckedChange = { viewModel.setDarkReaderSkipNativeDark(it) }
                    )
                    SettingsSliderRow(
                        label = "Brightness",
                        value = settings.darkReaderBrightness.toFloat(),
                        valueRange = 50f..150f,
                        onValueChange = { viewModel.setDarkReaderBrightness(it.roundToInt()) }
                    )
                    SettingsSliderRow(
                        label = "Contrast",
                        value = settings.darkReaderContrast.toFloat(),
                        valueRange = 50f..150f,
                        onValueChange = { viewModel.setDarkReaderContrast(it.roundToInt()) }
                    )
                    SettingsSliderRow(
                        label = "Sepia warmth",
                        value = settings.darkReaderSepia.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { viewModel.setDarkReaderSepia(it.roundToInt()) }
                    )
                }
            }
            }

            // Ad Blocking & Element Zapper
            item(key = "adblock_zapper", contentType = "settings_section") {
            SettingsSection(title = "Ad Blocking & Element Zapper") {
                val engineLabel = if (state.adblockStatus.isNativeRust) "adblock-rust JNI" else "Kotlin matcher"
                val formattedRules = remember(state.adblockStatus.totalRules) {
                    NumberFormat.getIntegerInstance().format(state.adblockStatus.totalRules)
                }
                SettingsSwitchRow(
                    title = "Native ad & tracker blocking",
                    subtitle = "$engineLabel · $formattedRules active rules",
                    checked = settings.adblockEnabled,
                    onCheckedChange = { viewModel.setAdblockEnabled(it) }
                )
                SettingsSwitchRow(
                    title = "Cosmetic element hiding",
                    subtitle = "Hide empty ad containers, banners, and tracking widgets",
                    checked = settings.adblockCosmeticEnabled,
                    onCheckedChange = { viewModel.setAdblockCosmeticEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SettingsNavigationRow(
                    title = "Filter lists",
                    subtitle = "${state.filterLists.count { it.enabled }} enabled · Add custom EasyList/uBO URLs",
                    onClick = { viewModel.setOverlay(BrowserOverlay.FILTER_LISTS) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SettingsNavigationRow(
                    title = "Zapped element rules",
                    subtitle = "${state.zappedRules.size} saved per-site rules",
                    onClick = { viewModel.setOverlay(BrowserOverlay.ZAPPED_RULES) }
                )
            }
            }

            // Userscripts & Reader
            item(key = "userscripts", contentType = "settings_section") {
            SettingsSection(title = "Userscripts & Extensibility") {
                SettingsSwitchRow(
                    title = "Enable userscripts (.user.js)",
                    subtitle = "Run @match scripts with GM_addStyle, storage, and GM_xmlhttpRequest",
                    checked = settings.userscriptsEnabled,
                    onCheckedChange = { viewModel.setUserscriptsEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SettingsNavigationRow(
                    title = "Manage userscripts",
                    subtitle = "${state.userscripts.count { it.enabled }} active · ${state.userscripts.size} installed",
                    onClick = { viewModel.setOverlay(BrowserOverlay.USERSCRIPTS) }
                )
            }
            }

            // Foldable & Layout Profiles
            item(key = "foldable_profiles", contentType = "settings_section") {
            SettingsSection(title = "Foldable & Layout Profiles") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Active posture: ${state.windowState.posture.label} (${state.windowState.windowWidthDp}dp)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                SettingGroup(label = "Folded toolbar placement") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolbarPlacement.entries.filterNot { it.unfoldedOnly }.forEach { placement ->
                            SelectionChip(
                                label = placement.label,
                                selected = settings.foldedProfile.toolbarPlacement == placement,
                                onClick = { viewModel.setToolbarPlacement(DevicePosture.FOLDED, placement) }
                            )
                        }
                    }
                }

                SettingGroup(label = "Folded default site mode") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SiteMode.entries.forEach { mode ->
                            SelectionChip(
                                label = mode.label,
                                selected = settings.foldedProfile.defaultSiteMode == mode,
                                onClick = { viewModel.setDefaultSiteMode(DevicePosture.FOLDED, mode) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                SettingGroup(label = "Unfolded toolbar placement") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolbarPlacement.entries.forEach { placement ->
                            SelectionChip(
                                label = placement.label,
                                selected = settings.unfoldedProfile.toolbarPlacement == placement,
                                onClick = { viewModel.setToolbarPlacement(DevicePosture.UNFOLDED, placement) }
                            )
                        }
                    }
                }

                SettingGroup(label = "Unfolded default site mode") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SiteMode.entries.forEach { mode ->
                            SelectionChip(
                                label = mode.label,
                                selected = settings.unfoldedProfile.defaultSiteMode == mode,
                                onClick = { viewModel.setDefaultSiteMode(DevicePosture.UNFOLDED, mode) }
                            )
                        }
                    }
                }

                SettingsSwitchRow(
                    title = "Unfolded Spaces sidebar",
                    subtitle = "Show collapsible Zen-style vertical Spaces rail on large displays",
                    checked = settings.unfoldedProfile.showSpacesSidebar,
                    onCheckedChange = { viewModel.setUnfoldedSpacesSidebar(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                SettingsSwitchRow(
                    title = "Hide toolbars on scroll",
                    subtitle = "Auto-hide top and bottom bars when scrolling down a webpage",
                    checked = settings.hideToolbarsOnScroll,
                    onCheckedChange = { viewModel.setHideToolbarsOnScroll(it) }
                )
            }
            }

            // Search & Gestures
            item(key = "search_gestures", contentType = "settings_section") {
            SettingsSection(title = "Search & Gestures") {
                SettingGroup(label = "Default search engine") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchEngineId.entries.forEach { engine ->
                            SelectionChip(
                                label = engine.label,
                                selected = settings.searchEngine == engine,
                                onClick = { viewModel.setSearchEngine(engine) }
                            )
                        }
                    }
                }

                if (settings.searchEngine == SearchEngineId.CUSTOM) {
                    val trimmedDraft = customTemplateDraft.trim()
                    val hasHttpScheme = trimmedDraft.startsWith("https://", ignoreCase = true) ||
                        trimmedDraft.startsWith("http://", ignoreCase = true)
                    val isValidTemplate = trimmedDraft.contains("%s") &&
                        hasHttpScheme &&
                        trimmedDraft.replace("%s", "test").toHttpUrlOrNull() != null
                    val hasUnsavedChanges = trimmedDraft != settings.customSearchUrlTemplate
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customTemplateDraft,
                            onValueChange = { customTemplateDraft = it },
                            label = { Text("Custom search URL (use %s for query)") },
                            isError = trimmedDraft.isNotEmpty() && !isValidTemplate,
                            supportingText = {
                                Text(
                                    text = when {
                                        trimmedDraft.isEmpty() || isValidTemplate ->
                                            "Example: https://kagi.com/search?q=%s"
                                        !trimmedDraft.contains("%s") ->
                                            "Must include %s where the search query goes"
                                        !hasHttpScheme ->
                                            "URL must start with https:// or http://"
                                        else ->
                                            "Enter a valid http:// or https:// search URL"
                                    }
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (hasUnsavedChanges && isValidTemplate) {
                            OutlinedButton(
                                onClick = {
                                    lastCommittedTemplate = trimmedDraft
                                    viewModel.setCustomSearchUrlTemplate(trimmedDraft)
                                    viewModel.showSnackbar("Saved custom search engine")
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Save custom search URL")
                            }
                        }
                    }
                }

                SettingsSwitchRow(
                    title = "Live search suggestions",
                    subtitle = "Show real-time query completions in the omnibox",
                    checked = settings.searchSuggestionsEnabled,
                    onCheckedChange = { viewModel.setSearchSuggestionsEnabled(it) }
                )
                SettingsSwitchRow(
                    title = "URL bar swipe gestures",
                    subtitle = "Swipe left/right to switch tabs, swipe toward content for tab switcher",
                    checked = settings.swipeUrlBarSwitchTabs,
                    onCheckedChange = { viewModel.setSwipeUrlBarSwitchTabs(it) }
                )
                SettingsSwitchRow(
                    title = "Pull to refresh",
                    subtitle = "Swipe down at top of a page to reload",
                    checked = settings.pullToRefreshEnabled,
                    onCheckedChange = { viewModel.setPullToRefreshEnabled(it) }
                )
            }
            }

            // Privacy, Links & Media
            item(key = "privacy_media", contentType = "settings_section") {
            SettingsSection(title = "Privacy, Links & Media") {
                SettingsSwitchRow(
                    title = "HTTPS-Only mode",
                    subtitle = "Upgrade public links to HTTPS automatically (LAN/localhost exempt)",
                    checked = settings.httpsOnlyMode,
                    onCheckedChange = { viewModel.setHttpsOnlyMode(it) }
                )
                SettingsSwitchRow(
                    title = "Block third-party cookies",
                    subtitle = "Restrict cross-site tracking cookies in WebView",
                    checked = settings.blockThirdPartyCookies,
                    onCheckedChange = { viewModel.setBlockThirdPartyCookies(it) }
                )
                SettingsSwitchRow(
                    title = "Background media playback",
                    subtitle = "Keep HTML5 audio & YouTube playing on screen-off via MediaSession",
                    checked = settings.backgroundMediaEnabled,
                    onCheckedChange = { viewModel.setBackgroundMediaEnabled(it) }
                )

                // Flattened radio rows (no nested cards inside SettingsSection)
                SettingGroup(label = "External app links") {
                    Column(modifier = Modifier.selectableGroup()) {
                        ExternalAppLinkPolicy.entries.forEachIndexed { idx, policy ->
                            val selected = settings.externalAppLinkPolicy == policy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { viewModel.setExternalAppLinkPolicy(policy) }
                                    )
                                    .heightIn(min = 56.dp)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(policy.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = policy.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (idx < ExternalAppLinkPolicy.entries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }

                if (state.siteOverrides.isNotEmpty()) {
                    SettingGroup(label = "Saved site overrides (${state.siteOverrides.size})") {
                        Column {
                            val allEntries = remember(state.siteOverrides) {
                                state.siteOverrides.values.sortedBy { it.host }
                            }
                            val entries = if (showAllSiteOverrides) allEntries else allEntries.take(6)
                            entries.forEachIndexed { idx, entry ->
                                val summary = buildList {
                                    entry.siteModeOverride?.let { add(it.lowercase().replaceFirstChar(Char::uppercase)) }
                                    if (entry.httpsOnlyBypass) add("HTTP allowed")
                                    if (entry.adblockDisabled) add("Shield off")
                                    if (!entry.javascriptEnabled) add("JS off")
                                    if (!entry.cookiesEnabled) add("Cookies off")
                                    entry.webDarkModeOverride?.let { add(if (it) "Dark on" else "Dark off") }
                                    entry.allowCamera?.let { add(if (it) "Camera allowed" else "Camera blocked") }
                                    entry.allowMic?.let { add(if (it) "Mic allowed" else "Mic blocked") }
                                    entry.allowLocation?.let { add(if (it) "Location allowed" else "Location blocked") }
                                    entry.allowProtectedMedia?.let { add(if (it) "DRM allowed" else "DRM blocked") }
                                    entry.externalAppPolicyOverride?.let { raw ->
                                        val label = ExternalAppLinkPolicy.entries.find { it.name == raw }?.label ?: raw
                                        add("External links: $label")
                                    }
                                    if (!entry.customUserAgent.isNullOrBlank()) add("Custom UA")
                                }.joinToString(" · ")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.host,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (summary.isNotEmpty()) {
                                            Text(
                                                text = summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.resetSiteOverride(entry.host, forcePersistent = true) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Reset ${entry.host}"
                                        )
                                    }
                                }
                                if (idx < entries.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }
                            if (allEntries.size > 6) {
                                TextButton(
                                    onClick = { showAllSiteOverrides = !showAllSiteOverrides },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        if (showAllSiteOverrides) "Show fewer" else "Show all (${allEntries.size})"
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showClearDataDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Clear history, cookies & cache")
                }
            }
            }

            // System Integration
            item(key = "system_integration", contentType = "settings_section") {
            SettingsSection(title = "System Integration") {
                Button(
                    onClick = onOpenDefaultBrowserSettings,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Set Swiftfox as default browser")
                }
            }
            }

            // About Swiftfox brand card
            item(key = "about_swiftfox", contentType = "about_card") {
                AboutSwiftfoxCard()
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear browsing data?") },
            text = { Text("This removes browsing history, cookies, site storage, and WebView cache.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataDialog = false
                        viewModel.clearAllBrowsingData()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AboutSwiftfoxCard() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SwiftfoxAppIcon(
                size = 54.dp,
                variant = SwiftfoxBrand.appChannelVariant
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwiftfoxWordmark(
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (SwiftfoxBrand.isBetaBuild) {
                        BetaChannelPill()
                    }
                }
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} · Overbay.app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "120Hz WebView · adblock-rust · Firefox & Zen Sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingGroup(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun SelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    var draftValue by remember(value) { mutableFloatStateOf(value) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${draftValue.roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = draftValue,
            onValueChange = { draftValue = it },
            onValueChangeFinished = {
                if (draftValue != value) {
                    onValueChange(draftValue)
                }
            },
            valueRange = valueRange
        )
    }
}