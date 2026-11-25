package app.overbay.swiftfox.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import app.overbay.swiftfox.ui.icons.rounded.Computer
import app.overbay.swiftfox.ui.icons.rounded.Devices
import app.overbay.swiftfox.ui.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import app.overbay.swiftfox.ui.icons.rounded.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.SyncedRemoteTabEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.sync.RemoteDeviceTabsGroup
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.components.SiteIconBadge
import java.util.Locale

private data class SyncedTabRowItem(
    val tab: SyncedRemoteTabEntity,
    val displayHost: String
)

private data class SyncedDeviceDisplayGroup(
    val group: RemoteDeviceTabsGroup,
    val rows: List<SyncedTabRowItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedTabsOverlay(
    state: BrowserUiState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenSyncSettings: () -> Unit
) {
    val isConnected = state.syncStatus.accountState is SyncAccountState.Connected
    val displayGroups = remember(state.syncedRemoteTabs) {
        state.remoteTabGroups.map { group ->
            val seenIds = HashSet<String>(group.tabs.size)
            val uniqueRows = ArrayList<SyncedTabRowItem>(group.tabs.size)
            for (tab in group.tabs) {
                if (seenIds.add(tab.id)) {
                    uniqueRows.add(
                        SyncedTabRowItem(
                            tab = tab,
                            displayHost = OmniboxEngine.extractHost(tab.url).ifBlank { tab.url }
                        )
                    )
                }
            }
            SyncedDeviceDisplayGroup(
                group = group,
                rows = uniqueRows
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Synced Open Tabs",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isConnected) {
                        IconButton(
                            onClick = onSyncNow,
                            enabled = !state.syncStatus.isSyncing,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (state.syncStatus.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .semantics { contentDescription = "Syncing" },
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Sync now")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Sign in to view open tabs",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Connect your Mozilla Account to open tabs from Desktop Firefox and Zen Browser.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onOpenSyncSettings,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Open Firefox & Zen Sync")
                    }
                }
            }
        } else if (displayGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No remote tabs yet",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Tap the refresh icon above once your desktop Firefox or Zen Browser has synced open tabs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                )
            ) {
                displayGroups.forEachIndexed { groupIdx, displayGroup ->
                    val group = displayGroup.group
                    val rows = displayGroup.rows
                    item(
                        key = "device_header_${group.deviceId}",
                        contentType = "remote_device_header"
                    ) {
                        RemoteDeviceHeaderRow(
                            group = group,
                            hasTabs = rows.isNotEmpty(),
                            modifier = Modifier.padding(top = if (groupIdx == 0) 0.dp else 16.dp)
                        )
                    }
                    itemsIndexed(
                        items = rows,
                        key = { _, item -> "remote_tab_${group.deviceId}_${item.tab.id}" },
                        contentType = { _, _ -> "remote_tab_row" }
                    ) { idx, item ->
                        RemoteTabItemRow(
                            item = item,
                            isLast = idx == rows.lastIndex,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteDeviceHeaderRow(
    group: RemoteDeviceTabsGroup,
    hasTabs: Boolean,
    modifier: Modifier = Modifier
) {
    val deviceIcon = when (group.deviceType.uppercase(Locale.ROOT)) {
        "MOBILE" -> Icons.Rounded.PhoneAndroid
        "TABLET" -> Icons.Rounded.TabletAndroid
        "DESKTOP" -> Icons.Rounded.Computer
        else -> Icons.Rounded.Devices
    }
    val shape = if (hasTabs) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    } else {
        RoundedCornerShape(22.dp)
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                start = 18.dp,
                end = 18.dp,
                top = 18.dp,
                bottom = if (hasTabs) 10.dp else 18.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = deviceIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = group.deviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${group.tabs.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun RemoteTabItemRow(
    item: SyncedTabRowItem,
    isLast: Boolean,
    onOpenUrl: (String) -> Unit
) {
    val shape = if (isLast) {
        RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
    } else {
        RectangleShape
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                start = 18.dp,
                end = 18.dp,
                bottom = if (isLast) 12.dp else 0.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenUrl(item.tab.url) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SiteIconBadge(
                    url = item.tab.url,
                    fallbackLabel = item.tab.title.ifBlank { item.displayHost },
                    size = 30.dp,
                    iconSize = 18.dp,
                    shape = RoundedCornerShape(9.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    fillImageToContainer = true
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.tab.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.displayHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!isLast) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}
