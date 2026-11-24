package app.overbay.swiftfox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.DownloadRecordEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.components.SwiftfoxSelectablePill
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksOverlay(
    bookmarks: List<BookmarkEntity>,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onImportBookmarksHtml: () -> Unit = {},
    onExportBookmarksHtml: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    val folders = remember(bookmarks) {
        bookmarks
            .map { it.folder.trim().ifBlank { "Bookmarks" } }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    LaunchedEffect(folders) {
        if (selectedFolder != null && folders.none { it.equals(selectedFolder, ignoreCase = true) }) {
            selectedFolder = null
        }
    }

    val filtered = remember(bookmarks, searchQuery, selectedFolder) {
        bookmarks.filter { bm ->
            val normalizedFolder = bm.folder.trim().ifBlank { "Bookmarks" }
            val folderMatch = selectedFolder == null || normalizedFolder.equals(selectedFolder, ignoreCase = true)
            val q = searchQuery.trim()
            val queryMatch = q.isEmpty() ||
                bm.title.contains(q, ignoreCase = true) ||
                bm.url.contains(q, ignoreCase = true) ||
                normalizedFolder.contains(q, ignoreCase = true)
            folderMatch && queryMatch
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onImportBookmarksHtml, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Upload, contentDescription = "Import HTML bookmarks")
                    }
                    if (bookmarks.isNotEmpty()) {
                        IconButton(onClick = onExportBookmarksHtml, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Download, contentDescription = "Export HTML bookmarks")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (bookmarks.isEmpty()) {
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
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "No bookmarks yet",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Bookmark any page from Settings → Quick Actions, sync with Firefox/Zen, or import a Chrome bookmarks.html file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = onImportBookmarksHtml,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Import bookmarks (.html)")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter bookmarks or folders") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                if (folders.size > 1) {
                    LazyRow(
                        modifier = Modifier.selectableGroup(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "folder_all", contentType = "folder_chip") {
                            SwiftfoxSelectablePill(
                                label = "All (${bookmarks.size})",
                                selected = selectedFolder == null,
                                onClick = { selectedFolder = null }
                            )
                        }
                        items(
                            items = folders,
                            key = { "folder_item_${it.lowercase(Locale.ROOT)}" },
                            contentType = { "folder_chip" }
                        ) { folderName ->
                            val isSelected = selectedFolder.equals(folderName, ignoreCase = true)
                            SwiftfoxSelectablePill(
                                label = folderName,
                                selected = isSelected,
                                onClick = {
                                    selectedFolder = if (isSelected) null else folderName
                                }
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No matching bookmarks",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedFolder = null
                                }
                            ) {
                                Text("Clear filters")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = innerPadding.calculateBottomPadding() + 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { it.id },
                            contentType = { "bookmark_row" }
                        ) { item ->
                            val host = OmniboxEngine.extractHost(item.url)
                            val sub = if (item.folder.isNotBlank() && !item.folder.equals("Bookmarks", ignoreCase = true)) {
                                "${item.folder} · $host"
                            } else {
                                host
                            }
                            LibraryItemRow(
                                title = item.title,
                                subtitle = sub,
                                faviconUrl = item.url,
                                onClick = { onOpenUrl(item.url) },
                                onDelete = { onDeleteBookmark(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryOverlay(
    history: List<HistoryEntity>,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Clear all")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            EmptyLibraryState(
                title = "No browsing history",
                message = "Visited pages appear here. Private tabs are never recorded.",
                icon = Icons.Rounded.History,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = history,
                    key = { it.url },
                    contentType = { "history_row" }
                ) { entry ->
                    LibraryItemRow(
                        title = entry.title,
                        subtitle = OmniboxEngine.extractHost(entry.url),
                        faviconUrl = entry.url,
                        onClick = { onOpenUrl(entry.url) },
                        onDelete = { onDeleteEntry(entry.url) }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear browsing history?") },
            text = {
                Text("Deletes all recorded page visits in Swiftfox. Your bookmarks and open tabs are not affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsOverlay(
    downloads: List<DownloadRecordEntity>,
    onBack: () -> Unit,
    onOpenDownload: (DownloadRecordEntity) -> Unit = {},
    onDeleteDownload: (DownloadRecordEntity) -> Unit = {},
    onClearAll: () -> Unit = {}
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Clear all")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            EmptyLibraryState(
                title = "No downloads yet",
                message = "Files and blob downloads saved through Swiftfox appear here and in your Downloads folder.",
                icon = Icons.Rounded.DownloadDone,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = downloads,
                    key = { it.id },
                    contentType = { "download_row" }
                ) { rec ->
                    LibraryItemRow(
                        title = rec.fileName,
                        subtitle = "${rec.status} · ${rec.mimeType}",
                        onClick = { onOpenDownload(rec) },
                        onDelete = { onDeleteDownload(rec) }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear download history?") },
            text = {
                Text("Removes all entries from your Swiftfox downloads list. Files already saved in your device Downloads folder are preserved.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LibraryItemRow(
    title: String,
    subtitle: String,
    faviconUrl: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!faviconUrl.isNullOrBlank()) {
                SiteIconBadge(
                    url = faviconUrl,
                    fallbackLabel = title.ifBlank { subtitle },
                    size = 32.dp,
                    iconSize = 20.dp,
                    shape = RoundedCornerShape(10.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    fillImageToContainer = true
                )
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
