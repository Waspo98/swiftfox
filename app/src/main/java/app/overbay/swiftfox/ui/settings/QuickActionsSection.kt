package app.overbay.swiftfox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.icons.automirrored.MenuBook
import app.overbay.swiftfox.ui.icons.automirrored.OpenInNew
import app.overbay.swiftfox.ui.icons.automirrored.SendToMobile
import app.overbay.swiftfox.ui.icons.rounded.AutoFixHigh
import app.overbay.swiftfox.ui.icons.rounded.Bookmark
import app.overbay.swiftfox.ui.icons.rounded.BookmarkBorder
import app.overbay.swiftfox.ui.icons.rounded.BrightnessAuto
import app.overbay.swiftfox.ui.icons.rounded.ChevronRight
import app.overbay.swiftfox.ui.icons.rounded.DarkMode
import app.overbay.swiftfox.ui.icons.rounded.DesktopWindows
import app.overbay.swiftfox.ui.icons.rounded.FindInPage
import app.overbay.swiftfox.ui.icons.rounded.LightMode
import app.overbay.swiftfox.ui.icons.rounded.PictureInPictureAlt
import app.overbay.swiftfox.ui.icons.rounded.Print
import app.overbay.swiftfox.ui.icons.rounded.PushPin
import app.overbay.swiftfox.ui.icons.rounded.Security
import app.overbay.swiftfox.ui.icons.rounded.Shield
import app.overbay.swiftfox.ui.icons.rounded.Tune
import java.text.NumberFormat

internal enum class WebDarkTileIcon {
    AUTO,
    DARK,
    LIGHT
}

internal data class WebDarkTileUi(
    val subtitle: String,
    val highlighted: Boolean,
    val icon: WebDarkTileIcon,
    val enabled: Boolean
)

internal enum class CompactQuickActionId {
    FIND_IN_PAGE,
    SITE_RULES,
    READER_MODE,
    PIN_ESSENTIAL,
    PRINT,
    SEND_TO_ZEN
}

internal data class CompactQuickActionSpec(
    val id: CompactQuickActionId,
    val label: String,
    val highlightedCue: Boolean = false,
    val stateAnnouncement: String? = null
)

internal fun resolveWebDarkTileUi(
    isWebPage: Boolean,
    jsEnabledForSite: Boolean,
    settings: BrowserSettings,
    siteOverride: SiteOverrideEntity?,
    isChromeDark: Boolean,
    skippedNativeDark: Boolean,
    hasSiteHost: Boolean = true
): WebDarkTileUi {
    if (!isWebPage) {
        return WebDarkTileUi(
            subtitle = "Open a page first",
            highlighted = false,
            icon = WebDarkTileIcon.AUTO,
            enabled = false
        )
    }
    if (!hasSiteHost) {
        return WebDarkTileUi(
            subtitle = "Unavailable here",
            highlighted = false,
            icon = WebDarkTileIcon.AUTO,
            enabled = false
        )
    }
    return when (siteOverride?.webDarkModeOverride) {
        true -> WebDarkTileUi(
            subtitle = if (jsEnabledForSite) "Force dark" else "Force dark · JS off",
            highlighted = true,
            icon = WebDarkTileIcon.DARK,
            enabled = true
        )
        false -> WebDarkTileUi(
            subtitle = "Force light",
            highlighted = true,
            icon = WebDarkTileIcon.LIGHT,
            enabled = true
        )
        null -> {
            val resolved = DarkReaderConfig.resolve(settings, siteOverride, isChromeDark)
            val subtitle = when {
                !jsEnabledForSite -> "Auto · JS off"
                skippedNativeDark && resolved.enabled -> "Auto · Native"
                resolved.enabled -> "Auto · Dark"
                else -> "Auto · Light"
            }
            WebDarkTileUi(
                subtitle = subtitle,
                highlighted = false,
                icon = WebDarkTileIcon.AUTO,
                enabled = true
            )
        }
    }
}

internal fun buildCompactQuickActionRows(
    isWebPage: Boolean,
    isReaderAvailable: Boolean,
    hasActiveSiteRules: Boolean,
    showPinEssential: Boolean = isWebPage,
    showSendToZen: Boolean
): List<List<CompactQuickActionSpec>> {
    if (!isWebPage) return emptyList()
    val actions = buildList {
        add(
            CompactQuickActionSpec(
                id = CompactQuickActionId.FIND_IN_PAGE,
                label = "Find in page"
            )
        )
        add(
            CompactQuickActionSpec(
                id = CompactQuickActionId.SITE_RULES,
                label = "Site rules",
                highlightedCue = hasActiveSiteRules,
                stateAnnouncement = if (hasActiveSiteRules) "Custom rules active" else null
            )
        )
        add(
            CompactQuickActionSpec(
                id = CompactQuickActionId.READER_MODE,
                label = "Reader mode",
                highlightedCue = isReaderAvailable,
                stateAnnouncement = if (isReaderAvailable) "Article ready" else null
            )
        )
        if (showPinEssential) {
            add(
                CompactQuickActionSpec(
                    id = CompactQuickActionId.PIN_ESSENTIAL,
                    label = "Pin essential"
                )
            )
        }
        add(
            CompactQuickActionSpec(
                id = CompactQuickActionId.PRINT,
                label = "Print"
            )
        )
        if (showSendToZen) {
            add(
                CompactQuickActionSpec(
                    id = CompactQuickActionId.SEND_TO_ZEN,
                    label = "Send to Zen"
                )
            )
        }
    }
    return actions.chunked(2)
}

@Composable
fun QuickActionsSection(
    state: BrowserUiState,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShareCleanUrl: () -> Unit,
    onPrintPage: () -> Unit,
    onToggleSiteMode: () -> Unit,
    onToggleShieldAdblock: () -> Unit,
    onCycleWebDarkMode: () -> Unit = {},
    onEnterPip: () -> Unit,
    onLaunchZapper: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenSiteRules: () -> Unit,
    onFindInPage: () -> Unit,
    onPinEssential: () -> Unit,
    onSendToZenSpace: () -> Unit,
    externalAppLabel: String? = null,
    onOpenInApp: () -> Unit = {},
    activeTabBlockedCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val activeTab = state.activeTab
    val isWebPage = activeTab != null && !activeTab.isStartPage
    val isDesktop = activeTab?.resolvedSiteMode == SiteMode.DESKTOP
    val activeOverride = state.activeSiteOverride
    val siteAdblockDisabled = activeOverride?.adblockDisabled == true
    val isShieldActive = state.settings.adblockEnabled && !siteAdblockDisabled

    val formattedRuleCount = remember(state.adblockStatus.totalRules) {
        NumberFormat.getIntegerInstance().format(state.adblockStatus.totalRules)
    }
    val shieldEnabled = !isWebPage || state.settings.adblockEnabled
    val shieldSubtitle = when {
        isWebPage && !state.settings.adblockEnabled -> "Off in Settings"
        !state.settings.adblockEnabled -> "Shield off"
        isWebPage && siteAdblockDisabled -> "Paused on site"
        isWebPage -> "$activeTabBlockedCount blocked"
        else -> "$formattedRuleCount rules"
    }

    val jsEnabledForSite = activeOverride?.javascriptEnabled != false
    val isBookmarked = state.isActiveUrlBookmarked
    val isChromeDark = when (state.settings.themeMode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.SYSTEM -> state.isSystemDark
    }
    val isPrivateTab = activeTab?.isPrivate == true
    val activeHost = remember(activeTab?.url) {
        OmniboxEngine.extractHost(activeTab?.url.orEmpty())
    }
    val hasSiteHost = isWebPage && activeHost.isNotBlank()
    val webDarkTile = resolveWebDarkTileUi(
        isWebPage = isWebPage,
        jsEnabledForSite = jsEnabledForSite,
        settings = state.settings,
        siteOverride = activeOverride,
        isChromeDark = isChromeDark,
        skippedNativeDark = activeTab?.darkReaderSkippedNative == true,
        hasSiteHost = hasSiteHost
    )
    val webDarkIcon = when (webDarkTile.icon) {
        WebDarkTileIcon.AUTO -> Icons.Rounded.BrightnessAuto
        WebDarkTileIcon.DARK -> Icons.Rounded.DarkMode
        WebDarkTileIcon.LIGHT -> Icons.Rounded.LightMode
    }

    val hostZappedCount = remember(
        isWebPage,
        activeHost,
        isPrivateTab,
        state.zappedRules,
        state.privateZappedRules
    ) {
        if (!isWebPage || activeHost.isBlank()) {
            0
        } else {
            val persistent = state.zappedRules.filter {
                it.enabled && it.host.equals(activeHost, ignoreCase = true)
            }
            if (isPrivateTab) {
                val privateRules = state.privateZappedRules[activeHost].orEmpty().filter { it.enabled }
                (persistent + privateRules).distinctBy { it.id }.size
            } else {
                persistent.size
            }
        }
    }
    val hasActiveSiteRules = isWebPage && (
        (activeOverride != null && !activeOverride.isDefault()) || hostZappedCount > 0
        )

    val showPinEssential = isWebPage && !isPrivateTab
    val showSendToZen = isWebPage &&
        !isPrivateTab &&
        state.syncStatus.accountState is SyncAccountState.Connected &&
        !state.activeSpace?.syncGuid.isNullOrBlank()
    val showPipBanner = isWebPage && (activeTab.isPlayingMedia || activeTab.isPlayingVideo)
    val showExternalAppBanner = isWebPage && !externalAppLabel.isNullOrBlank()

    val compactActionRows = remember(
        isWebPage,
        activeTab?.isReaderAvailable,
        hasActiveSiteRules,
        showPinEssential,
        showSendToZen
    ) {
        buildCompactQuickActionRows(
            isWebPage = isWebPage,
            isReaderAvailable = activeTab?.isReaderAvailable == true,
            hasActiveSiteRules = hasActiveSiteRules,
            showPinEssential = showPinEssential,
            showSendToZen = showSendToZen
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Active page context + compact circular navigation & page actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isWebPage) {
                            activeTab.title.ifBlank { activeTab.displayHost }
                        } else {
                            "Start Page"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isWebPage) {
                            val lockLabel = if (activeTab.isSecureHttps) "HTTPS" else "HTTP"
                            "${activeTab.displayHost} · $lockLabel · ${state.windowState.posture.label}"
                        } else {
                            "${state.windowState.posture.label} · ${state.activeProfile.toolbarPlacement.label} bar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = if (isWebPage) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.spacedBy(12.dp)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickIconAction(
                        label = "Back",
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        enabled = activeTab?.canGoBack == true,
                        onClick = onGoBack,
                        modifier = if (isWebPage) Modifier.weight(1f) else Modifier
                    )
                    QuickIconAction(
                        label = "Forward",
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        enabled = activeTab?.canGoForward == true,
                        onClick = onGoForward,
                        modifier = if (isWebPage) Modifier.weight(1f) else Modifier
                    )
                    if (isWebPage) {
                        QuickIconAction(
                            label = if (activeTab.isLoading) "Stop" else "Reload",
                            icon = if (activeTab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                            onClick = onReloadOrStop,
                            modifier = Modifier.weight(1f)
                        )
                        QuickIconAction(
                            label = if (isBookmarked) "Saved" else "Save",
                            icon = if (isBookmarked) {
                                Icons.Rounded.Bookmark
                            } else {
                                Icons.Rounded.BookmarkBorder
                            },
                            active = isBookmarked,
                            onClick = onToggleBookmark,
                            modifier = Modifier.weight(1f)
                        )
                        QuickIconAction(
                            label = "Share",
                            icon = Icons.Rounded.Share,
                            onClick = onShareCleanUrl,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2x2 Tactile hero grid: Shield, Web Dark, Zapper, Desktop site
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickControlTile(
                        title = "Shield",
                        subtitle = shieldSubtitle,
                        icon = if (isShieldActive) Icons.Rounded.Shield else Icons.Rounded.Security,
                        active = isShieldActive,
                        enabled = shieldEnabled,
                        stateAnnouncement = if (shieldEnabled) {
                            if (isShieldActive) "On" else "Off"
                        } else {
                            null
                        },
                        onClick = onToggleShieldAdblock,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    QuickControlTile(
                        title = "Web Dark",
                        subtitle = webDarkTile.subtitle,
                        icon = webDarkIcon,
                        active = webDarkTile.highlighted,
                        enabled = webDarkTile.enabled,
                        onClick = onCycleWebDarkMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val zapperSubtitle = when {
                        !isWebPage -> "Open a page first"
                        !jsEnabledForSite -> "Requires JS"
                        hostZappedCount > 0 -> "$hostZappedCount zapped"
                        else -> "Hide overlays"
                    }
                    QuickControlTile(
                        title = "Zapper",
                        subtitle = zapperSubtitle,
                        icon = Icons.Rounded.AutoFixHigh,
                        active = false,
                        enabled = isWebPage && jsEnabledForSite,
                        onClick = onLaunchZapper,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    val desktopSubtitle = when {
                        !isWebPage -> "Open a page first"
                        isDesktop -> "On · ${state.windowState.posture.label}"
                        else -> "Off · ${state.windowState.posture.label}"
                    }
                    QuickControlTile(
                        title = "Desktop site",
                        subtitle = desktopSubtitle,
                        icon = Icons.Rounded.DesktopWindows,
                        active = isWebPage && isDesktop,
                        enabled = isWebPage,
                        stateAnnouncement = if (isWebPage) {
                            if (isDesktop) "On" else "Off"
                        } else {
                            null
                        },
                        onClick = onToggleSiteMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Contextual full-width actions + balanced 2-column compact buttons
            if (isWebPage) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showPipBanner) {
                        QuickContextualActionBar(
                            label = "Picture-in-Picture",
                            icon = Icons.Rounded.PictureInPictureAlt,
                            trailingLabel = "Playing",
                            emphasized = true,
                            onClick = onEnterPip
                        )
                    }
                    if (showExternalAppBanner) {
                        val appLabel = externalAppLabel.orEmpty()
                        QuickContextualActionBar(
                            label = if (appLabel.length <= 22) "Open in $appLabel" else "Open in app",
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            emphasized = false,
                            onClick = onOpenInApp
                        )
                    }

                    compactActionRows.forEach { rowSpecs ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowSpecs.forEach { spec ->
                                val icon = when (spec.id) {
                                    CompactQuickActionId.FIND_IN_PAGE -> Icons.Rounded.FindInPage
                                    CompactQuickActionId.SITE_RULES -> Icons.Rounded.Tune
                                    CompactQuickActionId.READER_MODE -> Icons.AutoMirrored.Rounded.MenuBook
                                    CompactQuickActionId.PIN_ESSENTIAL -> Icons.Rounded.PushPin
                                    CompactQuickActionId.PRINT -> Icons.Rounded.Print
                                    CompactQuickActionId.SEND_TO_ZEN -> Icons.AutoMirrored.Rounded.SendToMobile
                                }
                                val onClick = when (spec.id) {
                                    CompactQuickActionId.FIND_IN_PAGE -> onFindInPage
                                    CompactQuickActionId.SITE_RULES -> onOpenSiteRules
                                    CompactQuickActionId.READER_MODE -> onOpenReaderMode
                                    CompactQuickActionId.PIN_ESSENTIAL -> onPinEssential
                                    CompactQuickActionId.PRINT -> onPrintPage
                                    CompactQuickActionId.SEND_TO_ZEN -> onSendToZenSpace
                                }
                                QuickCompactActionButton(
                                    label = spec.label,
                                    icon = icon,
                                    highlightedCue = spec.highlightedCue,
                                    stateAnnouncement = spec.stateAnnouncement,
                                    onClick = onClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickControlTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateAnnouncement: String? = null
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = when {
        !enabled -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
        active -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 68.dp)
            .semantics {
                if (stateAnnouncement != null) {
                    stateDescription = stateAnnouncement
                }
            },
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconColor else iconColor.copy(alpha = 0.45f),
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        active -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickContextualActionBar(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    emphasized: Boolean,
    trailingLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailingLabel != null) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickCompactActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightedCue: Boolean = false,
    stateAnnouncement: String? = null,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.35f else 0.22f)
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                if (stateAnnouncement != null) {
                    stateDescription = stateAnnouncement
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    highlightedCue -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (highlightedCue && enabled) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun QuickIconAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f)
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (active && enabled) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
    } else {
        null
    }

    Column(
        modifier = modifier
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            border = border,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
