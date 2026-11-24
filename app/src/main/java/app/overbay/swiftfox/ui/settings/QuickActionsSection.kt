package app.overbay.swiftfox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.SendToMobile
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.WebDarkEngine
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.ui.browser.BrowserUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionsSection(
    state: BrowserUiState,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShareCleanUrl: () -> Unit,
    onPrintPage: () -> Unit,
    onToggleSiteMode: () -> Unit,
    onToggleWebDarkMode: () -> Unit,
    onToggleShieldAdblock: () -> Unit,
    onEnterPip: () -> Unit,
    onLaunchZapper: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenSiteRules: () -> Unit,
    onFindInPage: () -> Unit,
    onPinEssential: () -> Unit,
    onSendToZenSpace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = state.activeTab
    val isWebPage = activeTab != null && !activeTab.isStartPage
    val isDesktop = activeTab?.resolvedSiteMode == SiteMode.DESKTOP
    val isWebDark = state.activeTabWebDarkEffective
    val siteAdblockDisabled = state.activeSiteOverride?.adblockDisabled == true
    val isShieldActive = state.settings.adblockEnabled && !siteAdblockDisabled

    val darkSubtitle = when {
        !isWebDark -> "Site native"
        activeTab?.darkReaderSkippedNative == true -> "Native dark kept"
        state.settings.webDarkEngine == WebDarkEngine.ALGORITHMIC_LITE -> "Lite mode on"
        state.settings.darkReaderAmoled -> "Dark Reader AMOLED"
        else -> "Dark Reader on"
    }

    val shieldEnabled = !isWebPage || state.settings.adblockEnabled
    val shieldSubtitle = when {
        isWebPage && !state.settings.adblockEnabled -> "Disabled in Settings"
        !state.settings.adblockEnabled -> "Shield off"
        isWebPage && siteAdblockDisabled -> "Paused on site"
        isWebPage -> "${activeTab.blockedCount} blocked"
        else -> "${state.adblockStatus.totalRules} rules ready"
    }
    val jsEnabledForSite = state.activeSiteOverride?.javascriptEnabled != false

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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = onGoBack,
                        enabled = activeTab?.canGoBack == true,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onGoForward,
                        enabled = activeTab?.canGoForward == true,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward"
                        )
                    }

                    if (isWebPage) {
                        FilledTonalIconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = if (state.isActiveUrlBookmarked) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            }
                        ) {
                            Icon(
                                imageVector = if (state.isActiveUrlBookmarked) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = if (state.isActiveUrlBookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Bookmark page"
                                }
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onShareCleanUrl,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share clean link"
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onPrintPage,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Print,
                                contentDescription = "Print or save PDF"
                            )
                        }
                    }
                }
            }

            // 2x2 Tactile toggle grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickControlTile(
                        title = if (isDesktop) "Desktop site" else "Mobile site",
                        subtitle = "${state.windowState.posture.label}: ${state.activeProfile.defaultSiteMode.label}",
                        icon = if (isDesktop) Icons.Rounded.DesktopWindows else Icons.Rounded.Smartphone,
                        active = isDesktop && isWebPage,
                        enabled = isWebPage,
                        onClick = onToggleSiteMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    QuickControlTile(
                        title = "Web dark",
                        subtitle = darkSubtitle,
                        icon = if (isWebDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        active = isWebDark,
                        onClick = onToggleWebDarkMode,
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
                    QuickControlTile(
                        title = "Shield",
                        subtitle = shieldSubtitle,
                        icon = if (isShieldActive) Icons.Rounded.Shield else Icons.Rounded.Security,
                        active = isShieldActive,
                        enabled = shieldEnabled,
                        onClick = onToggleShieldAdblock,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    QuickControlTile(
                        title = "PiP / Play",
                        subtitle = when {
                            !isWebPage -> "Open a page first"
                            activeTab.isPlayingMedia -> "Playing now"
                            else -> "Media ready"
                        },
                        icon = Icons.Rounded.PictureInPictureAlt,
                        active = isWebPage && activeTab.isPlayingMedia,
                        enabled = isWebPage,
                        onClick = onEnterPip,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Page-specific action pills
            if (isWebPage) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionPill(
                        label = "Zap element",
                        icon = Icons.Rounded.AutoFixHigh,
                        enabled = jsEnabledForSite,
                        onClick = onLaunchZapper
                    )
                    QuickActionPill(
                        label = "Reader mode",
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        onClick = onOpenReaderMode
                    )
                    QuickActionPill(
                        label = "Site rules",
                        icon = Icons.Rounded.Tune,
                        onClick = onOpenSiteRules
                    )
                    QuickActionPill(
                        label = "Find in page",
                        icon = Icons.Rounded.FindInPage,
                        onClick = onFindInPage
                    )
                    QuickActionPill(
                        label = "Pin essential",
                        icon = Icons.Rounded.PushPin,
                        onClick = onPinEssential
                    )
                    if (!activeTab.isPrivate &&
                        state.syncStatus.accountState is SyncAccountState.Connected &&
                        !state.activeSpace?.syncGuid.isNullOrBlank()
                    ) {
                        QuickActionPill(
                            label = "Send to Zen",
                            icon = Icons.AutoMirrored.Rounded.SendToMobile,
                            onClick = onSendToZenSpace
                        )
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
    enabled: Boolean = true
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
    val border = if (active) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) iconColor else iconColor.copy(alpha = 0.45f),
                    modifier = Modifier.padding(9.dp)
                )
            }
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
                        active -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
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
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
