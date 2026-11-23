package app.overbay.swiftfox.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.DevicePosture
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.SwiftfoxBrand
import app.overbay.swiftfox.ui.theme.SwiftfoxBrandLockup
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import app.overbay.swiftfox.ui.theme.spaceIconFor

@Composable
fun StartPageContent(
    state: BrowserUiState,
    onFocusOmnibox: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSelectSpace: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onAddEssential: (String, String) -> Unit,
    onRemoveEssential: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddEssentialDialog by remember { mutableStateOf(false) }
    var essentialToRemove by remember { mutableStateOf<EssentialEntity?>(null) }

    val foxColors = LocalSwiftfoxColors.current
    val isPrivate = state.activeTab?.isPrivate == true || state.isPrivateView
    val isUnfolded = state.windowState.posture == DevicePosture.UNFOLDED

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isUnfolded) 720.dp else 540.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand mark & wordmark
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isUnfolded) 12.dp else 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPrivate) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = foxColors.privateContainer,
                        border = BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SwiftfoxEmblem(
                                size = 38.dp,
                                variant = SwiftfoxEmblemVariant.PURPLE
                            )
                        }
                    }
                    Text(
                        text = "Private Space",
                        style = MaterialTheme.typography.headlineMedium,
                        color = foxColors.privateAccent
                    )
                    Text(
                        text = "Isolated WebView profile. History and cookies clear when your last private tab closes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val wordmarkColor = if (
                        state.settings.accentPreset == AccentPreset.FOX_AMBER &&
                        !state.settings.useDynamicColor
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    }
                    SwiftfoxBrandLockup(
                        emblemSize = 40.dp,
                        wordmarkSize = 32.sp,
                        variant = SwiftfoxEmblemVariant.ORANGE,
                        wordmarkColor = wordmarkColor,
                        showBetaBadge = SwiftfoxBrand.isBetaBuild
                    )
                }
            }

            // Primary Google Search Capsule
            Surface(
                onClick = onFocusOmnibox,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isPrivate) {
                        foxColors.privateAccent.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPrivate) {
                            foxColors.privateContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Search ${state.settings.searchEngine.label} or type URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Spaces switcher pills (normal mode, hidden when unfolded sidebar already shows Spaces)
            val sidebarVisible = isUnfolded && state.activeProfile.showSpacesSidebar
            if (!isPrivate && !sidebarVisible && state.spaces.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = state.spaces,
                        key = { it.id },
                        contentType = { "start_space_pill" }
                    ) { space ->
                        val selected = space.id == state.activeSpaceId
                        Surface(
                            onClick = { onSelectSpace(space.id) },
                            shape = RoundedCornerShape(22.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = if (selected) {
                                BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                            } else {
                                null
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = spaceIconFor(space.iconKey),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = space.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Essentials pinned grid
            if (!isPrivate) {
                val essentials = state.activeSpaceEssentials
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Essentials",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val totalEssentials = essentials.size + 1
                    val columns = when {
                        !isUnfolded -> 4
                        totalEssentials <= 4 -> totalEssentials.coerceAtLeast(1)
                        totalEssentials <= 8 -> 4
                        else -> 5
                    }
                    val rows = (essentials + null).chunked(columns)

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    if (item != null) {
                                        EssentialTile(
                                            essential = item,
                                            onClick = { onOpenUrl(item.url) },
                                            onLongClick = { essentialToRemove = item },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        AddEssentialTile(
                                            onClick = { showAddEssentialDialog = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Active open tabs / recent jump cards (suppress duplicate open-tab cards when expanded sidebar already shows them)
            val expandedSidebarVisible = isUnfolded && state.activeProfile.showSpacesSidebar && state.isUnfoldedRailExpanded
            val jumpTabs = if (expandedSidebarVisible) {
                emptyList()
            } else {
                state.visibleTabs.filter { !it.isStartPage && it.id != state.activeTabId }.take(4)
            }
            val recentBookmarks = state.bookmarks.take(4)
            val recentHistory = state.history.take(4)

            if (!isPrivate && (jumpTabs.isNotEmpty() || recentBookmarks.isNotEmpty() || recentHistory.isNotEmpty())) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val sectionTitle = when {
                        jumpTabs.isNotEmpty() -> "Open in ${state.activeSpace?.name ?: "Space"}"
                        recentBookmarks.isNotEmpty() -> "Bookmarks"
                        else -> "Recent"
                    }
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val cards: List<StartJumpItem> = when {
                        jumpTabs.isNotEmpty() -> jumpTabs.map {
                            StartJumpItem(
                                title = it.title.ifBlank { it.displayHost },
                                subtitle = buildString {
                                    append(it.displayHost)
                                    if (it.resolvedSiteMode == SiteMode.DESKTOP) append(" · Desktop")
                                },
                                icon = if (it.resolvedSiteMode == SiteMode.DESKTOP) Icons.Rounded.DesktopWindows else Icons.Rounded.Tab,
                                onClick = { onSelectTab(it.id) }
                            )
                        }
                        recentBookmarks.isNotEmpty() -> recentBookmarks.map {
                            StartJumpItem(
                                title = it.title,
                                subtitle = OmniboxEngine.extractHost(it.url),
                                icon = Icons.Rounded.Bookmark,
                                onClick = { onOpenUrl(it.url) }
                            )
                        }
                        else -> recentHistory.map {
                            StartJumpItem(
                                title = it.title,
                                subtitle = OmniboxEngine.extractHost(it.url),
                                icon = Icons.Rounded.History,
                                onClick = { onOpenUrl(it.url) }
                            )
                        }
                    }

                    cards.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { card ->
                                Surface(
                                    onClick = card.onClick,
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 84.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = card.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = card.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = card.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEssentialDialog) {
        AddEssentialDialog(
            onDismiss = { showAddEssentialDialog = false },
            onConfirm = { title, url ->
                showAddEssentialDialog = false
                onAddEssential(title, url)
            }
        )
    }

    essentialToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { essentialToRemove = null },
            title = { Text("Remove Essential?") },
            text = { Text("Remove ${target.title} (${target.domain}) from your pinned Essentials?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveEssential(target.id)
                        essentialToRemove = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { essentialToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class StartJumpItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EssentialTile(
    essential: EssentialEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = essential.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = essential.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddEssentialTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Pin Essential",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun AddEssentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin Essential") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Label") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address (e.g. github.com)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(title, url) },
                enabled = url.isNotBlank()
            ) {
                Text("Pin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
