package app.overbay.swiftfox.ui.sheets

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.ui.browser.BrowserOverlay
import app.overbay.swiftfox.ui.browser.BrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickControlsSheet(
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShareCleanUrl: () -> Unit,
    onPrintPage: () -> Unit,
    onToggleSiteMode: () -> Unit,
    onToggleWebDarkMode: () -> Unit,
    onToggleHttpsOnly: () -> Unit,
    onEnterPip: () -> Unit,
    onFindInPage: () -> Unit,
    onPinEssential: () -> Unit,
    onCycleToolbarPlacement: () -> Unit,
    onOpenOverlay: (BrowserOverlay) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        QuickControlsContent(
            state = state,
            onToggleBookmark = onToggleBookmark,
            onShareCleanUrl = onShareCleanUrl,
            onPrintPage = onPrintPage,
            onToggleSiteMode = onToggleSiteMode,
            onToggleWebDarkMode = onToggleWebDarkMode,
            onToggleHttpsOnly = onToggleHttpsOnly,
            onEnterPip = onEnterPip,
            onFindInPage = onFindInPage,
            onPinEssential = onPinEssential,
            onCycleToolbarPlacement = onCycleToolbarPlacement,
            onOpenOverlay = onOpenOverlay
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickControlsContent(
    state: BrowserUiState,
    onToggleBookmark: () -> Unit,
    onShareCleanUrl: () -> Unit,
    onPrintPage: () -> Unit,
    onToggleSiteMode: () -> Unit,
    onToggleWebDarkMode: () -> Unit,
    onToggleHttpsOnly: () -> Unit,
    onEnterPip: () -> Unit,
    onFindInPage: () -> Unit,
    onPinEssential: () -> Unit,
    onCycleToolbarPlacement: () -> Unit,
    onOpenOverlay: (BrowserOverlay) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = state.activeTab
    val isWebPage = activeTab != null && !activeTab.isStartPage
    val isDesktop = activeTab?.resolvedSiteMode == SiteMode.DESKTOP
    val isWebDark = state.activeTabWebDarkEffective
    val isHttpsOnly = state.settings.httpsOnlyMode

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header row: page context + primary page actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isWebPage) activeTab.title.ifBlank { activeTab.displayHost } else "Quick Controls",
                    style = MaterialTheme.typography.titleLarge,
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
                        imageVector = if (state.isActiveUrlBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (state.isActiveUrlBookmarked) "Remove bookmark" else "Bookmark page"
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

        // Expressive 2x2 tactile toggle grid (Signature Move)
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
                    active = isDesktop,
                    onClick = onToggleSiteMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                QuickControlTile(
                    title = "Web dark",
                    subtitle = if (isWebDark) "Darkening on" else "Site native",
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
                    subtitle = if (isHttpsOnly) "HTTPS-Only on" else "HTTPS-Only off",
                    icon = Icons.Rounded.Security,
                    active = isHttpsOnly,
                    onClick = onToggleHttpsOnly,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                QuickControlTile(
                    title = "PiP / Play",
                    subtitle = if (activeTab?.isPlayingMedia == true) "Playing now" else "Media ready",
                    icon = Icons.Rounded.PictureInPictureAlt,
                    active = activeTab?.isPlayingMedia == true,
                    onClick = onEnterPip,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // Secondary action pills
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isWebPage) {
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
            }
            QuickActionPill(
                label = "Bar: ${state.activeProfile.toolbarPlacement.label}",
                icon = Icons.Rounded.ViewAgenda,
                onClick = onCycleToolbarPlacement
            )
            QuickActionPill(
                label = "Bookmarks",
                icon = Icons.Rounded.Bookmarks,
                onClick = { onOpenOverlay(BrowserOverlay.BOOKMARKS) }
            )
            QuickActionPill(
                label = "History",
                icon = Icons.Rounded.History,
                onClick = { onOpenOverlay(BrowserOverlay.HISTORY) }
            )
            QuickActionPill(
                label = "Downloads",
                icon = Icons.Rounded.Download,
                onClick = { onOpenOverlay(BrowserOverlay.DOWNLOADS) }
            )
            QuickActionPill(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = { onOpenOverlay(BrowserOverlay.SETTINGS) }
            )
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
    modifier: Modifier = Modifier
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
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
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
                    tint = iconColor,
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
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.primary,
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
