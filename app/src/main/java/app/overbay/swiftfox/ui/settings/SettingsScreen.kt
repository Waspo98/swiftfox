package app.overbay.swiftfox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.BuildConfig
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit
) {
    val settings = state.settings
    var showClearDataDialog by remember { mutableStateOf(false) }
    var customTemplateDraft by remember(settings.customSearchUrlTemplate) {
        mutableStateOf(settings.customSearchUrlTemplate)
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Appearance & Theme
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
            }

            // Foldable & Layout Profiles
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
            }

            // Search & Gestures
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
                    OutlinedTextField(
                        value = customTemplateDraft,
                        onValueChange = {
                            customTemplateDraft = it
                            viewModel.setCustomSearchUrlTemplate(it)
                        },
                        label = { Text("Custom search URL (use %s for query)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
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

            // Privacy, Links & Media
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
                    Column {
                        ExternalAppLinkPolicy.entries.forEachIndexed { idx, policy ->
                            val selected = settings.externalAppLinkPolicy == policy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setExternalAppLinkPolicy(policy) }
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
                            val entries = state.siteOverrides.values.take(6)
                            entries.forEachIndexed { idx, entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.host,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.resetSiteOverride(entry.host) },
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

            // System Integration
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
                Text(
                    text = "Swiftfox v${BuildConfig.VERSION_NAME} · Android WebView 120Hz Engine · System Autofill enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
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
        label = { Text(label) },
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
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
