package app.overbay.swiftfox.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.features.adblock.AdblockEngineStatus
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterListsOverlay(
    filterLists: List<FilterListEntity>,
    adblockStatus: AdblockEngineStatus,
    isUpdating: Boolean,
    onBack: () -> Unit,
    onUpdateAll: () -> Unit,
    onToggleList: (String, Boolean) -> Unit,
    onAddCustomList: (name: String, url: String) -> Unit,
    onDeleteList: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteList by remember { mutableStateOf<FilterListEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Filter lists",
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
                    TextButton(
                        onClick = onUpdateAll,
                        enabled = !isUpdating && !adblockStatus.isCompiling,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(end = 8.dp)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Updating",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Update all",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(56.dp),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = {
                    Text(
                        text = "Add filter list",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "adblock_status_pill", contentType = "status_pill") {
                AdblockStatusBanner(status = adblockStatus, isUpdating = isUpdating)
                Spacer(Modifier.height(4.dp))
            }

            items(
                items = filterLists,
                key = { it.id },
                contentType = { "filter_list_row" }
            ) { item ->
                FilterListRow(
                    item = item,
                    onToggle = { enabled -> onToggleList(item.id, enabled) },
                    onDelete = { pendingDeleteList = item }
                )
            }
        }
    }

    if (showAddDialog) {
        AddCustomFilterListDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                showAddDialog = false
                onAddCustomList(name, url)
            }
        )
    }

    pendingDeleteList?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteList = null },
            title = { Text("Delete filter list?") },
            text = {
                Text("Remove \"${target.name}\" and its cached filter rules from Swiftfox?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteList = null
                        onDeleteList(target.id)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteList = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdblockStatusBanner(
    status: AdblockEngineStatus,
    isUpdating: Boolean
) {
    val isNative = status.isNativeRust
    val containerColor = if (isNative) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isNative) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = if (isNative) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
    val formattedRules = remember(status.totalRules) {
        NumberFormat.getIntegerInstance().format(status.totalRules)
    }
    val engineLabel = if (isNative) "adblock-rust JNI" else "Kotlin fallback"
    val statusDetail = when {
        status.isCompiling -> "Compiling filter rules…"
        isUpdating -> "$engineLabel · Syncing lists…"
        else -> "$engineLabel · $formattedRules active rules"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (isNative) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Content Blocker Engine",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FilterListRow(
    item: FilterListEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val formattedRules = remember(item.ruleCount) {
        NumberFormat.getIntegerInstance().format(item.ruleCount)
    }
    val subtitleText = when {
        !item.lastError.isNullOrBlank() -> "Update failed: ${item.lastError}"
        item.lastUpdatedAt > 0L -> {
            val relative = remember(item.lastUpdatedAt) {
                DateUtils.getRelativeTimeSpanString(
                    item.lastUpdatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            }
            "$formattedRules rules · Updated $relative"
        }
        item.enabled -> "Starter protection active · Tap Update all to sync"
        else -> "Disabled · Not downloaded"
    }
    val subtitleColor = if (!item.lastError.isNullOrBlank()) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!item.isBuiltIn) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete custom filter list"
                    )
                }
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun AddCustomFilterListDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val cleanUrl = url.trim()
    val validUrl = (cleanUrl.startsWith("https://", ignoreCase = true) ||
        cleanUrl.startsWith("http://", ignoreCase = true)) &&
        cleanUrl.toHttpUrlOrNull() != null
    val canSubmit = name.isNotBlank() && validUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom filter list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    placeholder = { Text("e.g. Regional Adblock") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Filter list URL") },
                    placeholder = { Text("https://example.com/filters.txt") },
                    singleLine = true,
                    isError = url.isNotBlank() && !validUrl,
                    supportingText = if (url.isNotBlank() && !validUrl) {
                        { Text("Enter a valid http:// or https:// URL") }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), cleanUrl) },
                enabled = canSubmit,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
