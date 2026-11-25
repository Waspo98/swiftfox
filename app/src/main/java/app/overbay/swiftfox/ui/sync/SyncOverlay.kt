package app.overbay.swiftfox.ui.sync

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.features.sync.SyncAccountState
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.components.SwiftfoxSegmentedControl
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblem
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncOverlay(
    state: BrowserUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onPairQr: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSyncedTabs: () -> Unit,
    onToggleSyncBookmarks: (Boolean) -> Unit,
    onToggleSyncHistory: (Boolean) -> Unit,
    onToggleSyncTabs: (Boolean) -> Unit,
    onToggleSyncZenSpaces: (Boolean) -> Unit,
    onChangeSyncInterval: (Int) -> Unit,
    onImportBookmarksHtml: () -> Unit,
    onExportBookmarksHtml: () -> Unit
) {
    val syncStatus = state.syncStatus
    val settings = state.settings
    val remoteTabGroups = remember(state.syncedRemoteTabs) { state.remoteTabGroups }
    val intervals = remember { listOf(0 to "Manual", 30 to "30 min", 60 to "1 hour", 240 to "4 hours") }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Firefox & Zen Sync",
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
                    if (syncStatus.accountState is SyncAccountState.Connected) {
                        IconButton(
                            onClick = onSyncNow,
                            enabled = !syncStatus.isSyncing,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (syncStatus.isSyncing) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (val account = syncStatus.accountState) {
                SyncAccountState.SignedOut,
                SyncAccountState.Authenticating -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SwiftfoxEmblem(
                                            size = 32.dp,
                                            variant = SwiftfoxEmblemVariant.ORANGE
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Connect Firefox or Zen Browser",
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Two-way encrypted sync for Bookmarks, History, Open Tabs, and Zen Spaces & Essentials.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!syncStatus.isEngineAvailable || !syncStatus.lastSyncError.isNullOrBlank()) {
                                SyncBannerRow(
                                    message = syncStatus.lastSyncError
                                        ?: "Mozilla sync native library unavailable on this device.",
                                    isError = true
                                )
                            }

                            val isAuthenticating = account is SyncAccountState.Authenticating
                            Button(
                                onClick = onSignIn,
                                enabled = syncStatus.isEngineAvailable && !isAuthenticating,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .semantics { contentDescription = "Signing in" },
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text("Signing in...")
                                } else {
                                    Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Sign in with Mozilla Account")
                                }
                            }

                            OutlinedButton(
                                onClick = onPairQr,
                                enabled = syncStatus.isEngineAvailable && !isAuthenticating,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Pair with QR code")
                            }
                        }
                    }
                }

                is SyncAccountState.NeedsReauth -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.email ?: "Mozilla Account needs attention",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = account.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!syncStatus.lastSyncError.isNullOrBlank() &&
                                syncStatus.lastSyncError != account.message
                            ) {
                                SyncBannerRow(message = syncStatus.lastSyncError, isError = true)
                            }
                            Button(
                                onClick = onSignIn,
                                enabled = syncStatus.isEngineAvailable,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text("Sign in again")
                            }
                            OutlinedButton(
                                onClick = { showSignOutDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Disconnect account")
                            }
                        }
                    }
                }

                is SyncAccountState.Connected -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                val initial = (
                                    account.displayName?.trim()?.takeIf { it.isNotEmpty() }
                                        ?: account.email?.trim()?.takeIf { it.isNotEmpty() }
                                        ?: "M"
                                    )
                                    .take(1)
                                    .uppercase(Locale.ROOT)
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initial,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.displayName?.takeIf { it.isNotBlank() }
                                            ?: account.email
                                            ?: "Mozilla Account",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!account.email.isNullOrBlank()) {
                                        Text(
                                            text = account.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val lastText = if (syncStatus.lastSyncAtMs > 0L) {
                                        "Last synced " + DateUtils.getRelativeTimeSpanString(
                                            syncStatus.lastSyncAtMs,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        )
                                    } else {
                                        "Ready to sync"
                                    }
                                    Text(
                                        text = lastText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!syncStatus.lastSyncSummary.isNullOrBlank()) {
                                SyncBannerRow(message = syncStatus.lastSyncSummary, isError = false)
                            }
                            if (!syncStatus.lastSyncError.isNullOrBlank()) {
                                SyncBannerRow(message = syncStatus.lastSyncError, isError = true)
                            }
                            if (!syncStatus.zenStatusNote.isNullOrBlank()) {
                                Text(
                                    text = syncStatus.zenStatusNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SyncStatPill("${state.bookmarks.size} bookmarks")
                                SyncStatPill("${state.syncedRemoteTabs.size} remote tabs")
                                val syncedSpaces = state.spaces.count { !it.syncGuid.isNullOrBlank() }
                                if (syncedSpaces > 0) {
                                    SyncStatPill("$syncedSpaces Zen Spaces")
                                }
                            }

                            Button(
                                onClick = onSyncNow,
                                enabled = !syncStatus.isSyncing,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (syncStatus.isSyncing) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Sync now")
                                }
                            }
                        }
                    }

                    // Sync Engines & Schedule
                    SyncSectionCard(title = "What to sync") {
                        SyncSwitchRow(
                            title = "Bookmarks",
                            subtitle = "Two-way folder & bookmark sync with Firefox / Zen",
                            checked = settings.syncBookmarksEnabled,
                            onCheckedChange = onToggleSyncBookmarks
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        SyncSwitchRow(
                            title = "History",
                            subtitle = "Merge visited sites across devices (Private tabs excluded)",
                            checked = settings.syncHistoryEnabled,
                            onCheckedChange = onToggleSyncHistory
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        SyncSwitchRow(
                            title = "Open tabs",
                            subtitle = "Share your Swiftfox tabs and browse Desktop tabs",
                            checked = settings.syncTabsEnabled,
                            onCheckedChange = onToggleSyncTabs
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        SyncSwitchRow(
                            title = "Zen Spaces & Essentials",
                            subtitle = "Sync encrypted Zen Spaces, Essentials, and pinned tabs",
                            checked = settings.syncZenSpacesEnabled,
                            onCheckedChange = onToggleSyncZenSpaces
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenSyncedTabs)
                                .heightIn(min = 52.dp)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Synced Open Tabs",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${remoteTabGroups.size} devices · ${state.syncedRemoteTabs.size} open tabs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Background sync interval",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val selectedInterval = intervals.firstOrNull {
                                it.first == settings.syncPeriodicIntervalMinutes
                            } ?: intervals.first()
                            SwiftfoxSegmentedControl(
                                options = intervals,
                                selected = selectedInterval,
                                onSelect = { (mins, _) -> onChangeSyncInterval(mins) },
                                labelFor = { (_, label) -> label }
                            )
                        }

                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Sign out of Mozilla Sync")
                        }
                    }
                }
            }

            // Chrome / Netscape HTML Bookmark Interop (always available, even signed out)
            SyncSectionCard(title = "Chrome Bookmark Interop") {
                Text(
                    text = "Import bookmarks exported from Google Chrome, Brave, or Edge (bookmarks.html) while preserving folder hierarchy, or export a standard Netscape HTML backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportBookmarksHtml,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Import", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onExportBookmarksHtml,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Export", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out of Mozilla Sync?") },
            text = {
                Text("Your local bookmarks, history, and open tabs stay safely on this device. Synced remote device tabs will be cleared until you sign in again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SyncSectionCard(
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
private fun SyncSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp, horizontal = 2.dp),
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
private fun SyncStatPill(label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SyncBannerRow(
    message: String,
    isError: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}
