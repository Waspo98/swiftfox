package app.overbay.swiftfox.ui.settings

import android.app.role.RoleManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import app.overbay.swiftfox.ui.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CheckCircle
import app.overbay.swiftfox.ui.icons.rounded.DashboardCustomize
import app.overbay.swiftfox.ui.icons.rounded.Devices
import app.overbay.swiftfox.ui.icons.rounded.Download
import app.overbay.swiftfox.ui.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import app.overbay.swiftfox.ui.icons.rounded.OpenInBrowser
import app.overbay.swiftfox.ui.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import app.overbay.swiftfox.ui.icons.rounded.Shield
import app.overbay.swiftfox.ui.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.swiftfox.BuildConfig
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.ui.browser.BrowserOverlay
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.BrowserViewModel
import app.overbay.swiftfox.ui.browser.EmphasizedAccelerateEasing
import app.overbay.swiftfox.ui.browser.EmphasizedDecelerateEasing
import app.overbay.swiftfox.ui.browser.SettingsCategory
import app.overbay.swiftfox.ui.theme.BetaChannelPill
import app.overbay.swiftfox.ui.theme.SwiftfoxAppIcon
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.SwiftfoxWordmark
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
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
    val activeCategory = state.activeSettingsCategory
    val reduceMotion = rememberReduceMotion()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showSearchEnginePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefaultBrowser by remember { mutableStateOf(checkDefaultBrowserHeld(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultBrowser = checkDefaultBrowserHeld(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeCategory) {
        scrollBehavior.state.contentOffset = 0f
    }

    val layoutDirection = LocalLayoutDirection.current
    val remoteTabGroups = remember(state.syncedRemoteTabs) { state.remoteTabGroups }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeCategory?.title ?: "Settings",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = if (activeCategory != null) "Back to Settings" else "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            top = 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        )

        AnimatedContent(
            targetState = activeCategory,
            transitionSpec = {
                when {
                    reduceMotion ->
                        (EnterTransition.None togetherWith ExitTransition.None) using null
                    initialState == null && targetState != null ->
                        (slideInHorizontally(tween(260, easing = EmphasizedDecelerateEasing)) { (it * 0.28f).toInt() } +
                            fadeIn(tween(180)) togetherWith
                            slideOutHorizontally(tween(220, easing = EmphasizedAccelerateEasing)) { -(it * 0.14f).toInt() } +
                            fadeOut(tween(140))) using null
                    initialState != null && targetState == null ->
                        (slideInHorizontally(tween(240, easing = EmphasizedDecelerateEasing)) { -(it * 0.14f).toInt() } +
                            fadeIn(tween(180)) togetherWith
                            slideOutHorizontally(tween(220, easing = EmphasizedAccelerateEasing)) { (it * 0.28f).toInt() } +
                            fadeOut(tween(140))) using null
                    else ->
                        (fadeIn(tween(160)) togetherWith fadeOut(tween(120))) using null
                }
            },
            label = "settingsCategoryTransition"
        ) { category ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (category) {
                    null -> {
                        // Active page quick actions
                        item(key = "quick_actions", contentType = "settings_section") {
                            val activeBlockedCount by viewModel.activeTabBlockedCount.collectAsStateWithLifecycle()
                            QuickActionsSection(
                                state = state,
                                activeTabBlockedCount = activeBlockedCount,
                                onGoBack = {
                                    viewModel.setOverlay(BrowserOverlay.NONE)
                                    viewModel.goBack()
                                },
                                onGoForward = {
                                    viewModel.setOverlay(BrowserOverlay.NONE)
                                    viewModel.goForward()
                                },
                                onReloadOrStop = {
                                    viewModel.setOverlay(BrowserOverlay.NONE)
                                    viewModel.reloadOrStop()
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

                        // Preferences Category Hub
                        item(key = "preferences_hub", contentType = "settings_section") {
                            val formattedRules = remember(state.adblockStatus.totalRules) {
                                NumberFormat.getIntegerInstance().format(state.adblockStatus.totalRules)
                            }
                            SettingsSection(title = "Preferences") {
                                SettingsNavigationRow(
                                    title = SettingsCategory.APPEARANCE.title,
                                    subtitle = appearanceSummary(state.settings),
                                    icon = Icons.Rounded.Palette,
                                    onClick = { viewModel.setSettingsCategory(SettingsCategory.APPEARANCE) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = SettingsCategory.FOLDABLE_LAYOUT.title,
                                    subtitle = foldableSummary(state.settings),
                                    icon = Icons.Rounded.DashboardCustomize,
                                    onClick = { viewModel.setSettingsCategory(SettingsCategory.FOLDABLE_LAYOUT) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = SettingsCategory.ADBLOCK_SCRIPTS.title,
                                    subtitle = adblockSummary(
                                        settings = state.settings,
                                        formattedRules = formattedRules,
                                        activeScripts = state.userscripts.count { it.enabled }
                                    ),
                                    icon = Icons.Rounded.Shield,
                                    onClick = { viewModel.setSettingsCategory(SettingsCategory.ADBLOCK_SCRIPTS) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = SettingsCategory.SEARCH_GESTURES.title,
                                    subtitle = searchGesturesSummary(state.settings),
                                    icon = Icons.Rounded.Search,
                                    onClick = { viewModel.setSettingsCategory(SettingsCategory.SEARCH_GESTURES) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = SettingsCategory.PRIVACY_MEDIA.title,
                                    subtitle = privacySummary(state.settings, state.siteOverrides.size),
                                    icon = Icons.Rounded.Lock,
                                    onClick = { viewModel.setSettingsCategory(SettingsCategory.PRIVACY_MEDIA) }
                                )
                            }
                        }

                        // Library (Bookmarks, History, Downloads)
                        item(key = "library", contentType = "settings_section") {
                            SettingsSection(title = "Library") {
                                SettingsNavigationRow(
                                    title = "Bookmarks",
                                    subtitle = "${state.bookmarks.size} saved · Import or export Chrome HTML",
                                    icon = Icons.Rounded.Bookmarks,
                                    onClick = { viewModel.setOverlay(BrowserOverlay.BOOKMARKS) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = "History",
                                    subtitle = "${state.history.size} visited pages",
                                    icon = Icons.Rounded.History,
                                    onClick = { viewModel.setOverlay(BrowserOverlay.HISTORY) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = "Downloads",
                                    subtitle = "${state.downloads.size} downloads",
                                    icon = Icons.Rounded.Download,
                                    onClick = { viewModel.setOverlay(BrowserOverlay.DOWNLOADS) }
                                )
                            }
                        }

                        // Account & Sync
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
                                    icon = Icons.Rounded.Sync,
                                    onClick = { viewModel.setOverlay(BrowserOverlay.SYNC) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingsNavigationRow(
                                    title = "Synced Open Tabs",
                                    subtitle = "${remoteTabGroups.size} devices · ${state.syncedRemoteTabs.size} open tabs",
                                    icon = Icons.Rounded.Devices,
                                    onClick = { viewModel.setOverlay(BrowserOverlay.SYNCED_TABS) }
                                )
                            }
                        }

                        // About & Default Browser card
                        item(key = "about_swiftfox", contentType = "about_card") {
                            AboutAndSystemCard(
                                isDefaultBrowser = isDefaultBrowser,
                                onOpenDefaultBrowserSettings = onOpenDefaultBrowserSettings
                            )
                        }
                    }

                    SettingsCategory.APPEARANCE -> {
                        appearanceSettingsItems(state = state, viewModel = viewModel)
                    }

                    SettingsCategory.FOLDABLE_LAYOUT -> {
                        foldableLayoutSettingsItems(state = state, viewModel = viewModel)
                    }

                    SettingsCategory.ADBLOCK_SCRIPTS -> {
                        adBlockScriptsSettingsItems(state = state, viewModel = viewModel)
                    }

                    SettingsCategory.SEARCH_GESTURES -> {
                        searchGesturesSettingsItems(
                            state = state,
                            viewModel = viewModel,
                            onOpenSearchEnginePicker = { showSearchEnginePicker = true }
                        )
                    }

                    SettingsCategory.PRIVACY_MEDIA -> {
                        privacyMediaSettingsItems(
                            state = state,
                            viewModel = viewModel,
                            onOpenClearDataDialog = { showClearDataDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showSearchEnginePicker) {
        SearchEnginePickerSheet(
            selected = state.settings.searchEngine,
            customTemplate = state.settings.customSearchUrlTemplate,
            onSelect = { viewModel.setSearchEngine(it) },
            onDismiss = { showSearchEnginePicker = false }
        )
    }

    if (showClearDataDialog) {
        ClearBrowsingDataDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirm = { clearHistory, clearCookies, clearCache ->
                showClearDataDialog = false
                viewModel.clearBrowsingData(
                    clearHistory = clearHistory,
                    clearCookiesAndSiteData = clearCookies,
                    clearCache = clearCache
                )
            }
        )
    }
}

private fun checkDefaultBrowserHeld(context: android.content.Context): Boolean = runCatching {
    val roleManager = context.getSystemService(RoleManager::class.java)
    roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
}.getOrDefault(false)

private fun appearanceSummary(settings: BrowserSettings): String = buildList {
    add(settings.themeMode.label)
    if (settings.themeMode != ThemeMode.DAY) {
        add(settings.nightSurfaceStyle.label)
    }
    add(if (settings.useDynamicColor) "Material You" else settings.accentPreset.label)
    when (settings.webDarkMode) {
        WebDarkModePreference.OFF -> add("Web dark off")
        WebDarkModePreference.FOLLOW_APP -> add(if (settings.darkReaderAmoled) "Dark Reader AMOLED" else "Dark Reader")
        WebDarkModePreference.ALWAYS_ON -> add(if (settings.darkReaderAmoled) "Always dark AMOLED" else "Always dark")
    }
}.joinToString(" · ")

private fun foldableSummary(settings: BrowserSettings): String =
    "Folded: ${settings.foldedProfile.toolbarPlacement.label} / ${settings.foldedProfile.defaultSiteMode.label} · " +
        "Unfolded: ${settings.unfoldedProfile.toolbarPlacement.label} / ${settings.unfoldedProfile.defaultSiteMode.label}"

private fun adblockSummary(
    settings: BrowserSettings,
    formattedRules: String,
    activeScripts: Int
): String {
    val shieldPart = if (settings.adblockEnabled) "Shield on ($formattedRules rules)" else "Shield off"
    val scriptPart = when {
        !settings.userscriptsEnabled -> "Scripts off"
        activeScripts == 1 -> "1 script active"
        else -> "$activeScripts scripts active"
    }
    return "$shieldPart · $scriptPart"
}

private fun searchGesturesSummary(settings: BrowserSettings): String = buildList {
    add(settings.searchEngine.label)
    val gestures = buildList {
        if (settings.searchSuggestionsEnabled) add("Suggestions")
        if (settings.swipeUrlBarSwitchTabs) add("URL swipes")
        if (settings.pullToRefreshEnabled) add("Pull to refresh")
    }
    if (gestures.isNotEmpty()) {
        add(gestures.joinToString(", "))
    }
}.joinToString(" · ")

private fun privacySummary(settings: BrowserSettings, siteOverrideCount: Int): String = buildList {
    if (settings.httpsOnlyMode) add("HTTPS-Only")
    if (settings.blockThirdPartyCookies) add("3P cookies blocked")
    val linkShort = when (settings.externalAppLinkPolicy) {
        ExternalAppLinkPolicy.ASK -> "Ask for links"
        ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX -> "Links in Swiftfox"
        ExternalAppLinkPolicy.NATIVE_APP -> "Open native apps"
    }
    add(linkShort)
    if (siteOverrideCount > 0) {
        add("$siteOverrideCount site ${if (siteOverrideCount == 1) "rule" else "rules"}")
    }
}.joinToString(" · ")

@Composable
private fun AboutAndSystemCard(
    isDefaultBrowser: Boolean,
    onOpenDefaultBrowserSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
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
                        text = "120Hz smooth scrolling · Native ad blocking · Firefox & Zen Sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            if (isDefaultBrowser) {
                Surface(
                    onClick = onOpenDefaultBrowserSettings,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default browser active",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap to manage Android default browser apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onOpenDefaultBrowserSettings,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Set Swiftfox as default browser")
                }
            }
        }
    }
}
