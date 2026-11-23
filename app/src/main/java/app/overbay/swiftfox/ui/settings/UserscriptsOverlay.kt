package app.overbay.swiftfox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher

private const val NEW_USERSCRIPT_TEMPLATE = """// ==UserScript==
// @name         Highlight Headings
// @namespace    swiftfox.local
// @version      1.0
// @description  Adds a warm left accent border to main headings.
// @match        https://example.com/*
// @run-at       document-end
// @grant        GM_addStyle
// ==/UserScript==

(function () {
  'use strict';
  GM_addStyle('h1 { border-left: 4px solid #E86A17; padding-left: 10px; }');
})();
"""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserscriptsOverlay(
    scripts: List<UserscriptEntity>,
    onBack: () -> Unit,
    onToggleScript: (String, Boolean) -> Unit,
    onInstallFromUrl: (String) -> Unit,
    onImportLocalFile: () -> Unit,
    onSaveScriptCode: (existingId: String?, code: String) -> Unit,
    onDeleteScript: (String) -> Unit
) {
    var showInstallUrlDialog by rememberSaveable { mutableStateOf(false) }
    var installUrlDraft by rememberSaveable { mutableStateOf("") }

    var showEditorDialog by rememberSaveable { mutableStateOf(false) }
    var editingScriptId by rememberSaveable { mutableStateOf<String?>(null) }
    var codeDraft by rememberSaveable { mutableStateOf("") }

    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteScript = remember(scripts, pendingDeleteId) {
        pendingDeleteId?.let { id -> scripts.firstOrNull { it.id == id } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Userscripts",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserscriptActionPill(
                    label = "Install URL",
                    icon = Icons.Rounded.Link,
                    emphasized = false,
                    onClick = {
                        installUrlDraft = ""
                        showInstallUrlDialog = true
                    }
                )
                UserscriptActionPill(
                    label = "Import .user.js",
                    icon = Icons.Rounded.FileOpen,
                    emphasized = false,
                    onClick = onImportLocalFile
                )
                UserscriptActionPill(
                    label = "New script",
                    icon = Icons.Rounded.Add,
                    emphasized = true,
                    onClick = {
                        editingScriptId = null
                        codeDraft = NEW_USERSCRIPT_TEMPLATE
                        showEditorDialog = true
                    }
                )
            }

            if (scripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = 32.dp,
                            end = 32.dp,
                            top = 24.dp,
                            bottom = innerPadding.calculateBottomPadding() + 24.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "No userscripts installed",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Install a .user.js script from a URL, import a file, or write one with GM_addStyle, GM_getValue, and GM_xmlhttpRequest.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = scripts,
                        key = { it.id },
                        contentType = { "userscript_row" }
                    ) { script ->
                        UserscriptRow(
                            script = script,
                            onToggle = { enabled -> onToggleScript(script.id, enabled) },
                            onEdit = {
                                editingScriptId = script.id
                                codeDraft = script.code
                                showEditorDialog = true
                            },
                            onDelete = { pendingDeleteId = script.id }
                        )
                    }
                }
            }
        }
    }

    if (showInstallUrlDialog) {
        val urlIsValid = UserscriptUrlMatcher.isHttpOrHttps(installUrlDraft)
        AlertDialog(
            onDismissRequest = { showInstallUrlDialog = false },
            title = {
                Text(
                    text = "Install from URL",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter an http:// or https:// link to a .user.js file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = installUrlDraft,
                        onValueChange = { installUrlDraft = it },
                        placeholder = { Text("https://example.com/script.user.js") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        isError = installUrlDraft.isNotBlank() && !urlIsValid,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = installUrlDraft.trim()
                        showInstallUrlDialog = false
                        onInstallFromUrl(target)
                    },
                    enabled = urlIsValid,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInstallUrlDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditorDialog) {
        val parsedPreview = remember(codeDraft) {
            UserscriptParser.parse(codeDraft, fallbackName = "Custom Userscript")
        }
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp),
            title = {
                Text(
                    text = if (editingScriptId == null) "New userscript" else "Edit userscript",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${parsedPreview.name} · ${parsedPreview.runAt.headerValue} · ${parsedPreview.matches.size} @match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = codeDraft,
                        onValueChange = { codeDraft = it },
                        placeholder = { Text("// ==UserScript== ...") },
                        shape = RoundedCornerShape(16.dp),
                        minLines = 8,
                        maxLines = 14,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val savedCode = codeDraft
                        val targetId = editingScriptId
                        showEditorDialog = false
                        onSaveScriptCode(targetId, savedCode)
                    },
                    enabled = codeDraft.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditorDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingDeleteScript != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = {
                Text(
                    text = "Delete userscript?",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Text(
                    text = "Remove \"${pendingDeleteScript.name}\" and clear its saved GM storage values.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingDeleteScript.id
                        pendingDeleteId = null
                        onDeleteScript(id)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteId = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UserscriptActionPill(
    label: String,
    icon: ImageVector,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserscriptRow(
    script: UserscriptEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val matches = remember(script.matchesJson) {
        UserscriptParser.decodeStringList(script.matchesJson)
    }
    val matchSummary = remember(matches) {
        when {
            matches.isEmpty() -> "No @match"
            matches.size == 1 -> matches.first()
            else -> "${matches.first()} (+${matches.size - 1})"
        }
    }
    val sourceLabel = remember(script.sourceUrl) {
        val url = script.sourceUrl
        if (url.isNullOrBlank()) {
            "Local script"
        } else {
            OmniboxEngine.extractHost(url).ifBlank { url }
        }
    }

    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (script.enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (script.description.isNotBlank()) {
                        Text(
                            text = script.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = script.enabled,
                    onCheckedChange = onToggle
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UserscriptMetaChip(text = matchSummary)
                UserscriptMetaChip(text = script.runAt)
                UserscriptMetaChip(text = "v${script.version}")
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${script.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${script.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UserscriptMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
