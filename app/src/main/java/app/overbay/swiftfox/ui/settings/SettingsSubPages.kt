package app.overbay.swiftfox.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import app.overbay.swiftfox.ui.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import app.overbay.swiftfox.ui.icons.rounded.DarkMode
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.DesktopWindows
import app.overbay.swiftfox.ui.icons.rounded.LightMode
import app.overbay.swiftfox.ui.icons.rounded.RestartAlt
import app.overbay.swiftfox.ui.icons.rounded.Smartphone
import app.overbay.swiftfox.ui.icons.rounded.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.AppIconBackgroundMode
import app.overbay.swiftfox.data.prefs.AppIconColorStyle
import app.overbay.swiftfox.data.prefs.AppIconPalette
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.TabAutoClearDuration
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.ui.browser.BrowserOverlay
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.BrowserViewModel
import app.overbay.swiftfox.ui.components.SwiftfoxSegmentedControl
import app.overbay.swiftfox.ui.theme.SwiftfoxAppIcon
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import app.overbay.swiftfox.ui.theme.toEmblemVariant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.NumberFormat
import kotlin.math.roundToInt

internal fun LazyListScope.appearanceSettingsItems(
    state: BrowserUiState,
    viewModel: BrowserViewModel
) {
    val settings = state.settings
    item(key = "appearance_chrome", contentType = "settings_section") {
        val isNightSurfaceActive = settings.themeMode != ThemeMode.DAY
        SettingsSection(title = "App Chrome") {
            SettingGroup(label = "Theme mode") {
                SwiftfoxSegmentedControl(
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    onSelect = { viewModel.setThemeMode(it) },
                    labelFor = { it.label },
                    iconFor = { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                            ThemeMode.DAY -> Icons.Rounded.LightMode
                            ThemeMode.NIGHT -> Icons.Rounded.DarkMode
                        }
                    }
                )
            }

            SettingGroup(
                label = "Night surface",
                supportingText = if (!isNightSurfaceActive) {
                    "Applies in Night mode or when Android dark theme is active"
                } else {
                    null
                }
            ) {
                SwiftfoxSegmentedControl(
                    options = NightSurfaceStyle.entries,
                    selected = settings.nightSurfaceStyle,
                    onSelect = { viewModel.setNightSurfaceStyle(it) },
                    labelFor = { it.label },
                    enabled = isNightSurfaceActive
                )
            }

            SettingGroup(
                label = "Accent palette",
                supportingText = if (settings.useDynamicColor) {
                    "Android wallpaper colors active — tap any preset to switch back"
                } else {
                    null
                }
            ) {
                val presetRows = remember { AccentPreset.entries.chunked(2) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .alpha(if (settings.useDynamicColor) 0.52f else 1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetRows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val selected = !settings.useDynamicColor && settings.accentPreset == preset
                                AccentPresetTile(
                                    preset = preset,
                                    selected = selected,
                                    onClick = {
                                        viewModel.setUseDynamicColor(false)
                                        viewModel.setAccentPreset(preset)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowPresets.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            SettingsSwitchRow(
                title = "Material You dynamic color",
                subtitle = "Match Android wallpaper palette (keeps AMOLED Black)",
                checked = settings.useDynamicColor,
                onCheckedChange = { viewModel.setUseDynamicColor(it) }
            )
        }
    }

    item(key = "appearance_app_icon", contentType = "settings_section") {
        val isChromeDark = settings.themeMode.isDarkActive(isSystemInDarkTheme())
        val isBeta = SwiftfoxBrand.isBetaBuild
        val resolvedIcon = settings.resolveAppIcon(isChromeDark = isChromeDark, isBetaBuild = isBeta)
        val accentIconOptions = remember {
            listOf(
                AppIconColorStyle.FOX_AMBER to AppIconPalette.FOX_AMBER,
                AppIconColorStyle.MIDNIGHT_INDIGO to AppIconPalette.MIDNIGHT_INDIGO,
                AppIconColorStyle.FOREST to AppIconPalette.FOREST,
                AppIconColorStyle.MONOCHROME to AppIconPalette.MONOCHROME
            )
        }

        SettingsSection(title = "App Icon") {
            SettingGroup(
                label = "Icon background",
                supportingText = if (settings.appIconBackgroundMode == AppIconBackgroundMode.FOLLOW_THEME) {
                    val activeLabel = if (resolvedIcon.isLightBackground) "Light" else "Dark"
                    "Follows app theme · Currently using $activeLabel icon"
                } else {
                    null
                }
            ) {
                SwiftfoxSegmentedControl(
                    options = AppIconBackgroundMode.entries,
                    selected = settings.appIconBackgroundMode,
                    onSelect = { viewModel.setAppIconBackgroundMode(it) },
                    labelFor = { mode ->
                        when (mode) {
                            AppIconBackgroundMode.DARK -> "Dark"
                            AppIconBackgroundMode.LIGHT -> "Light"
                            AppIconBackgroundMode.FOLLOW_THEME -> "Auto"
                        }
                    },
                    iconFor = { mode ->
                        when (mode) {
                            AppIconBackgroundMode.DARK -> Icons.Rounded.DarkMode
                            AppIconBackgroundMode.LIGHT -> Icons.Rounded.LightMode
                            AppIconBackgroundMode.FOLLOW_THEME -> Icons.Rounded.BrightnessAuto
                        }
                    }
                )
            }

            SettingGroup(
                label = "Icon style",
                supportingText = if (settings.appIconMatchAccent) {
                    if (settings.useDynamicColor) {
                        "Material You is active — matching your ${settings.accentPreset.label} preset"
                    } else {
                        "Matching your active ${settings.accentPreset.label} accent — tap any style to customize"
                    }
                } else {
                    null
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .alpha(if (settings.appIconMatchAccent) 0.52f else 1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBeta) {
                        AppIconVariantTile(
                            title = "Beta Violet",
                            subtitle = "Default Swiftfox Beta icon",
                            palette = AppIconPalette.BETA_VIOLET,
                            isLightBackground = resolvedIcon.isLightBackground,
                            selected = !settings.appIconMatchAccent &&
                                settings.appIconColorStyle == AppIconColorStyle.CHANNEL_DEFAULT,
                            onClick = { viewModel.setAppIconColorStyle(AppIconColorStyle.CHANNEL_DEFAULT) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val rows = remember(accentIconOptions) { accentIconOptions.chunked(2) }
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (style, palette) ->
                                val selected = !settings.appIconMatchAccent && (
                                    settings.appIconColorStyle == style ||
                                        (!isBeta &&
                                            style == AppIconColorStyle.FOX_AMBER &&
                                            settings.appIconColorStyle == AppIconColorStyle.CHANNEL_DEFAULT)
                                    )
                                AppIconVariantTile(
                                    title = style.label,
                                    palette = palette,
                                    isLightBackground = resolvedIcon.isLightBackground,
                                    selected = selected,
                                    onClick = { viewModel.setAppIconColorStyle(style) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            SettingsSwitchRow(
                title = "Match app accent",
                subtitle = "Automatically switch icon color to match your Accent palette",
                checked = settings.appIconMatchAccent,
                onCheckedChange = { viewModel.setAppIconMatchAccent(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            SettingsSwitchRow(
                title = "Use this icon throughout the app",
                subtitle = "Apply this icon style to the Start Page, sidebar, tab switcher, and About card",
                checked = settings.syncAppIconInApp,
                onCheckedChange = { viewModel.setSyncAppIconInApp(it) }
            )

            Text(
                text = "Your launcher may take a moment to refresh the home screen icon after switching styles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    item(key = "appearance_web_dark", contentType = "settings_section") {
        val reduceMotion = rememberReduceMotion()
        val webDarkActive = settings.webDarkMode != WebDarkModePreference.OFF
        var brightnessDraft by remember(settings.darkReaderBrightness) {
            mutableFloatStateOf(settings.darkReaderBrightness.toFloat())
        }
        var contrastDraft by remember(settings.darkReaderContrast) {
            mutableFloatStateOf(settings.darkReaderContrast.toFloat())
        }
        var sepiaDraft by remember(settings.darkReaderSepia) {
            mutableFloatStateOf(settings.darkReaderSepia.toFloat())
        }

        SettingsSection(title = "Web Content Dark Mode") {
            SettingGroup(label = "Darken websites") {
                SwiftfoxSegmentedControl(
                    options = WebDarkModePreference.entries,
                    selected = settings.webDarkMode,
                    onSelect = { viewModel.setWebDarkMode(it) },
                    labelFor = { it.label }
                )
            }

            AnimatedVisibility(
                visible = webDarkActive,
                enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SettingsSwitchRow(
                        title = "Pure black pages (AMOLED)",
                        subtitle = "Force true black (#000000) backgrounds and crisp white text, including native dark sites",
                        checked = settings.darkReaderAmoled,
                        onCheckedChange = { viewModel.setDarkReaderAmoled(it) }
                    )
                    SettingsSwitchRow(
                        title = "Keep native dark sites",
                        subtitle = if (settings.darkReaderAmoled) {
                            "Overridden while Pure black pages (AMOLED) is active"
                        } else {
                            "Leave websites that already support dark mode untouched"
                        },
                        checked = settings.darkReaderSkipNativeDark,
                        enabled = !settings.darkReaderAmoled,
                        onCheckedChange = { viewModel.setDarkReaderSkipNativeDark(it) }
                    )

                    val hasCustomAdjustments = brightnessDraft.roundToInt() != 100 ||
                        contrastDraft.roundToInt() != 100 ||
                        sepiaDraft.roundToInt() != 0
                    DarkReaderPreviewCard(
                        amoled = settings.darkReaderAmoled,
                        brightness = brightnessDraft.roundToInt(),
                        contrast = contrastDraft.roundToInt(),
                        sepia = sepiaDraft.roundToInt(),
                        showReset = hasCustomAdjustments,
                        onReset = {
                            brightnessDraft = 100f
                            contrastDraft = 100f
                            sepiaDraft = 0f
                            viewModel.resetDarkReaderAdjustments()
                        }
                    )

                    SettingsSliderRow(
                        label = "Brightness",
                        value = brightnessDraft,
                        valueRange = 50f..150f,
                        onValueChange = { brightnessDraft = it },
                        onValueChangeFinished = {
                            val next = brightnessDraft.roundToInt()
                            if (next != settings.darkReaderBrightness) {
                                viewModel.setDarkReaderBrightness(next)
                            }
                        }
                    )
                    SettingsSliderRow(
                        label = "Contrast",
                        value = contrastDraft,
                        valueRange = 50f..150f,
                        onValueChange = { contrastDraft = it },
                        onValueChangeFinished = {
                            val next = contrastDraft.roundToInt()
                            if (next != settings.darkReaderContrast) {
                                viewModel.setDarkReaderContrast(next)
                            }
                        }
                    )
                    SettingsSliderRow(
                        label = "Sepia warmth",
                        value = sepiaDraft,
                        valueRange = 0f..100f,
                        onValueChange = { sepiaDraft = it },
                        onValueChangeFinished = {
                            val next = sepiaDraft.roundToInt()
                            if (next != settings.darkReaderSepia) {
                                viewModel.setDarkReaderSepia(next)
                            }
                        }
                    )
                }
            }
        }
    }
}

internal fun LazyListScope.foldableLayoutSettingsItems(
    state: BrowserUiState,
    viewModel: BrowserViewModel
) {
    val settings = state.settings
    val isUnfolded = state.windowState.posture == DevicePosture.UNFOLDED

    item(key = "foldable_status", contentType = "status_banner") {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .widthIn(max = SettingsMaxContentWidth)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isUnfolded) Icons.Rounded.TabletAndroid else Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUnfolded) "Unfolded · Large display active" else "Folded · Cover screen active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Folded and unfolded screens keep independent toolbar and site defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    item(key = "foldable_folded", contentType = "settings_section") {
        val foldedPlacements = remember { ToolbarPlacement.entries.filterNot { it.unfoldedOnly } }
        SettingsSection(title = "Folded · Cover Screen") {
            SettingGroup(label = "Toolbar placement") {
                SwiftfoxSegmentedControl(
                    options = foldedPlacements,
                    selected = settings.foldedProfile.toolbarPlacement,
                    onSelect = { viewModel.setToolbarPlacement(DevicePosture.FOLDED, it) },
                    labelFor = { it.label }
                )
            }
            SettingGroup(label = "Default site view") {
                SwiftfoxSegmentedControl(
                    options = SiteMode.entries,
                    selected = settings.foldedProfile.defaultSiteMode,
                    onSelect = { viewModel.setDefaultSiteMode(DevicePosture.FOLDED, it) },
                    labelFor = { it.label },
                    iconFor = { mode ->
                        if (mode == SiteMode.DESKTOP) Icons.Rounded.DesktopWindows else Icons.Rounded.Smartphone
                    }
                )
            }
        }
    }

    item(key = "foldable_unfolded", contentType = "settings_section") {
        SettingsSection(title = "Unfolded · Large Display") {
            SettingGroup(label = "Toolbar placement") {
                SwiftfoxSegmentedControl(
                    options = ToolbarPlacement.entries,
                    selected = settings.unfoldedProfile.toolbarPlacement,
                    onSelect = { viewModel.setToolbarPlacement(DevicePosture.UNFOLDED, it) },
                    labelFor = { it.label }
                )
            }
            SettingGroup(label = "Default site view") {
                SwiftfoxSegmentedControl(
                    options = SiteMode.entries,
                    selected = settings.unfoldedProfile.defaultSiteMode,
                    onSelect = { viewModel.setDefaultSiteMode(DevicePosture.UNFOLDED, it) },
                    labelFor = { it.label },
                    iconFor = { mode ->
                        if (mode == SiteMode.DESKTOP) Icons.Rounded.DesktopWindows else Icons.Rounded.Smartphone
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsSwitchRow(
                title = "Spaces sidebar",
                subtitle = "Show collapsible Zen-style vertical Spaces rail on large displays",
                checked = settings.unfoldedProfile.showSpacesSidebar,
                onCheckedChange = { viewModel.setUnfoldedSpacesSidebar(it) }
            )
        }
    }

    item(key = "foldable_scroll", contentType = "settings_section") {
        SettingsSection(title = "Scrolling Behavior") {
            SettingsSwitchRow(
                title = "Hide toolbars on scroll",
                subtitle = "Auto-hide browser bars while scrolling down a webpage",
                checked = settings.hideToolbarsOnScroll,
                onCheckedChange = { viewModel.setHideToolbarsOnScroll(it) }
            )
        }
    }
}

internal fun LazyListScope.tabsWorkspacesSettingsItems(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    onOpenAutoClearPicker: () -> Unit
) {
    val settings = state.settings
    item(key = "tabs_workspaces_section", contentType = "settings_section") {
        SettingsSection(title = "Tab Management") {
            SettingsNavigationRow(
                title = "Auto-clear opened tabs",
                subtitle = tabAutoClearSubtitle(settings.tabAutoClearDuration),
                trailingBadge = settings.tabAutoClearDuration.label,
                onClick = onOpenAutoClearPicker
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsSwitchRow(
                title = "Confirm closing active Essentials & Pins",
                subtitle = "Ask before closing an open Essential or pinned tab on a second tap",
                checked = settings.confirmClosePinnedOnSecondTap,
                onCheckedChange = { viewModel.setConfirmClosePinnedOnSecondTap(it) }
            )
        }
    }
}

internal fun LazyListScope.adBlockScriptsSettingsItems(
    state: BrowserUiState,
    viewModel: BrowserViewModel
) {
    val settings = state.settings
    item(key = "adblock_section", contentType = "settings_section") {
        val engineLabel = if (state.adblockStatus.isNativeRust) "Native Rust engine" else "Built-in matcher"
        val formattedRules = remember(state.adblockStatus.totalRules) {
            NumberFormat.getIntegerInstance().format(state.adblockStatus.totalRules)
        }
        SettingsSection(title = "Ad & Tracker Blocking") {
            SettingsSwitchRow(
                title = "Block ads & trackers",
                subtitle = "$engineLabel · $formattedRules active rules",
                checked = settings.adblockEnabled,
                onCheckedChange = { viewModel.setAdblockEnabled(it) }
            )
            SettingsSwitchRow(
                title = "Hide empty ad spaces",
                subtitle = if (settings.adblockEnabled) {
                    "Collapse leftover banners, popups, and tracking widgets"
                } else {
                    "Turn on ad & tracker blocking above to enable element hiding"
                },
                checked = settings.adblockCosmeticEnabled,
                enabled = settings.adblockEnabled,
                onCheckedChange = { viewModel.setAdblockCosmeticEnabled(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsNavigationRow(
                title = "Filter lists",
                subtitle = "${state.filterLists.count { it.enabled }} enabled · Add custom EasyList or uBO URLs",
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

    item(key = "userscripts_section", contentType = "settings_section") {
        SettingsSection(title = "Userscripts") {
            SettingsSwitchRow(
                title = "Run custom userscripts",
                subtitle = "Execute installed .user.js scripts on matching websites",
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
}

internal fun LazyListScope.searchGesturesSettingsItems(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    onOpenSearchEnginePicker: () -> Unit
) {
    val settings = state.settings
    item(key = "search_section", contentType = "settings_section") {
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

        SettingsSection(title = "Search") {
            SettingsNavigationRow(
                title = "Default search engine",
                subtitle = searchEngineSubtitle(settings.searchEngine, settings.customSearchUrlTemplate),
                trailingBadge = settings.searchEngine.label,
                onClick = onOpenSearchEnginePicker
            )

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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            SettingsSwitchRow(
                title = "Live search suggestions",
                subtitle = "Show query completions as you type in the address bar",
                checked = settings.searchSuggestionsEnabled,
                onCheckedChange = { viewModel.setSearchSuggestionsEnabled(it) }
            )
        }
    }

    item(key = "gestures_section", contentType = "settings_section") {
        SettingsSection(title = "Gestures") {
            SettingsSwitchRow(
                title = "Address bar swipe gestures",
                subtitle = "Swipe sideways to switch tabs, or swipe toward the page for tab switcher",
                checked = settings.swipeUrlBarSwitchTabs,
                onCheckedChange = { viewModel.setSwipeUrlBarSwitchTabs(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsSwitchRow(
                title = "Pull to refresh",
                subtitle = "Pull down from the top of a webpage to reload",
                checked = settings.pullToRefreshEnabled,
                onCheckedChange = { viewModel.setPullToRefreshEnabled(it) }
            )
        }
    }
}

internal fun LazyListScope.privacyMediaSettingsItems(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    onOpenClearDataDialog: () -> Unit
) {
    val settings = state.settings
    item(key = "privacy_toggles", contentType = "settings_section") {
        SettingsSection(title = "Privacy & Media") {
            SettingsSwitchRow(
                title = "HTTPS-Only mode",
                subtitle = "Automatically upgrade public sites to encrypted HTTPS (local networks exempt)",
                checked = settings.httpsOnlyMode,
                onCheckedChange = { viewModel.setHttpsOnlyMode(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsSwitchRow(
                title = "Block third-party cookies",
                subtitle = "Block cross-site cookies (may break Sign in with Google or SSO; adblock still blocks trackers)",
                checked = settings.blockThirdPartyCookies,
                onCheckedChange = { viewModel.setBlockThirdPartyCookies(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsSwitchRow(
                title = "Background media playback",
                subtitle = "Keep audio and video playing when you lock the screen or switch apps",
                checked = settings.backgroundMediaEnabled,
                onCheckedChange = { viewModel.setBackgroundMediaEnabled(it) }
            )
        }
    }

    item(key = "external_links", contentType = "settings_section") {
        SettingsSection(title = "External App Links") {
            SettingGroup(
                label = "When a link matches an installed app",
                supportingText = settings.externalAppLinkPolicy.description
            ) {
                SwiftfoxSegmentedControl(
                    options = ExternalAppLinkPolicy.entries,
                    selected = settings.externalAppLinkPolicy,
                    onSelect = { viewModel.setExternalAppLinkPolicy(it) },
                    labelFor = { policy ->
                        when (policy) {
                            ExternalAppLinkPolicy.ASK -> "Ask"
                            ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX -> "In Swiftfox"
                            ExternalAppLinkPolicy.NATIVE_APP -> "Open app"
                        }
                    }
                )
            }
        }
    }

    if (state.siteOverrides.isNotEmpty()) {
        item(key = "site_overrides", contentType = "settings_section") {
            var showAllSiteOverrides by remember { mutableStateOf(false) }
            val allEntries = remember(state.siteOverrides) {
                state.siteOverrides.values.sortedBy { it.host }
            }
            val entries = if (showAllSiteOverrides) allEntries else allEntries.take(6)

            SettingsSection(title = "Saved Site Overrides (${allEntries.size})") {
                Column {
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
                            Text(if (showAllSiteOverrides) "Show fewer" else "Show all (${allEntries.size})")
                        }
                    }
                }
            }
        }
    }

    item(key = "browsing_data", contentType = "settings_section") {
        SettingsSection(title = "Browsing Data") {
            Text(
                text = "Choose whether to remove visited history, cached files, or saved cookies and site logins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenClearDataDialog,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Clear browsing data...")
            }
        }
    }
}

@Composable
private fun AppIconVariantTile(
    title: String,
    palette: AppIconPalette,
    isLightBackground: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        },
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SwiftfoxAppIcon(
                size = 38.dp,
                variant = palette.toEmblemVariant(),
                isLightBackground = isLightBackground
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentPresetTile(
    preset: AccentPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatchColor = Color(preset.previewHex)
    val checkTint = if (preset == AccentPreset.FOX_AMBER) Color(0xFF1F0A00) else Color.White

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        },
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(swatchColor),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = checkTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DarkReaderPreviewCard(
    amoled: Boolean,
    brightness: Int,
    contrast: Int,
    sepia: Int,
    showReset: Boolean,
    onReset: () -> Unit
) {
    val bgColor = if (amoled) {
        Color.Black
    } else {
        applyDarkReaderFilter(Color(0xFF181818), brightness, contrast, sepia)
    }
    val headingColor = applyDarkReaderFilter(
        if (amoled) Color.White else Color(0xFFE8E8E8),
        brightness,
        contrast,
        sepia
    )
    val bodyColor = applyDarkReaderFilter(
        if (amoled) Color.White else Color(0xFFC8C8C8),
        brightness,
        contrast,
        sepia
    )
    val linkColor = applyDarkReaderFilter(MaterialTheme.colorScheme.primary, brightness, contrast, sepia)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Sample webpage preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = headingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Body copy tone updates live as you tune sliders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "swiftfox.overbay.app",
                    style = MaterialTheme.typography.labelSmall,
                    color = linkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showReset) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Reset")
                }
            }
        }
    }
}

private fun applyDarkReaderFilter(
    source: Color,
    brightness: Int,
    contrast: Int,
    sepia: Int
): Color {
    val b = brightness / 100f
    val c = contrast / 100f
    val s = (sepia / 100f).coerceIn(0f, 1f)

    fun adjust(ch: Float): Float = ((ch * b - 0.5f) * c + 0.5f).coerceIn(0f, 1f)

    val r0 = adjust(source.red)
    val g0 = adjust(source.green)
    val b0 = adjust(source.blue)

    val sr = (r0 * 0.393f + g0 * 0.769f + b0 * 0.189f).coerceIn(0f, 1f)
    val sg = (r0 * 0.349f + g0 * 0.686f + b0 * 0.168f).coerceIn(0f, 1f)
    val sb = (r0 * 0.272f + g0 * 0.534f + b0 * 0.131f).coerceIn(0f, 1f)

    val r = (r0 * (1f - s) + sr * s).coerceIn(0f, 1f)
    val g = (g0 * (1f - s) + sg * s).coerceIn(0f, 1f)
    val bl = (b0 * (1f - s) + sb * s).coerceIn(0f, 1f)
    return Color(r, g, bl, source.alpha)
}

private fun tabAutoClearSubtitle(duration: TabAutoClearDuration): String = when (duration) {
    TabAutoClearDuration.NEVER -> "Keep open tabs indefinitely until closed manually"
    else -> "Close inactive tabs and reset loaded pins after ${duration.label.lowercase()}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabAutoClearPickerSheet(
    selected: TabAutoClearDuration,
    onSelect: (TabAutoClearDuration) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Auto-clear opened tabs",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Closes inactive tabs and restores loaded pins or Essentials to their pinned URL. Active and media-playing tabs stay open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    TabAutoClearDuration.entries.forEachIndexed { idx, option ->
                        val isSelected = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(option)
                                        onDismiss()
                                    }
                                )
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (idx < TabAutoClearDuration.entries.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun searchEngineSubtitle(engine: SearchEngineId, customTemplate: String): String = when (engine) {
    SearchEngineId.GOOGLE -> "google.com · Live suggestions supported"
    SearchEngineId.DUCKDUCKGO -> "duckduckgo.com · Privacy-first search"
    SearchEngineId.KAGI -> "kagi.com · Ad-free search"
    SearchEngineId.BRAVE -> "search.brave.com · Independent index"
    SearchEngineId.STARTPAGE -> "startpage.com · Private Google results"
    SearchEngineId.CUSTOM -> customTemplate.ifBlank { "Custom URL template with %s" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchEnginePickerSheet(
    selected: SearchEngineId,
    customTemplate: String,
    onSelect: (SearchEngineId) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Default search engine",
                style = MaterialTheme.typography.titleLarge
            )
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    SearchEngineId.entries.forEachIndexed { idx, engine ->
                        val isSelected = engine == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(engine)
                                        onDismiss()
                                    }
                                )
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engine.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = searchEngineSubtitle(engine, customTemplate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (idx < SearchEngineId.entries.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClearBrowsingDataDialog(
    onDismiss: () -> Unit,
    onConfirm: (clearHistory: Boolean, clearCookiesAndSiteData: Boolean, clearCache: Boolean) -> Unit
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(false) }
    val canClear = clearHistory || clearCache || clearCookies

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClearDataCheckboxRow(
                    title = "Browsing history",
                    subtitle = "Visited pages and navigation history",
                    checked = clearHistory,
                    onCheckedChange = { clearHistory = it }
                )
                ClearDataCheckboxRow(
                    title = "Cached files & thumbnails",
                    subtitle = "Frees storage without signing you out",
                    checked = clearCache,
                    onCheckedChange = { clearCache = it }
                )
                ClearDataCheckboxRow(
                    title = "Cookies & site logins",
                    subtitle = "Signs you out of websites across profiles",
                    checked = clearCookies,
                    onCheckedChange = { clearCookies = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(clearHistory, clearCookies, clearCache) },
                enabled = canClear,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ClearDataCheckboxRow(
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
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = SettingsMaxContentWidth)
            .fillMaxWidth()
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
internal fun SettingGroup(
    label: String,
    supportingText: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
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
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    trailingBadge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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
        if (!trailingBadge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = trailingBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val pct = value.roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.semantics {
                stateDescription = "$pct percent"
            }
        )
    }
}
