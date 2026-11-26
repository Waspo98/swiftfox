package app.overbay.swiftfox.ui.webapps

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.db.WebAppEntity
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.webapps.PageInstallCandidate
import app.overbay.swiftfox.features.webapps.WebAppManifestParser
import app.overbay.swiftfox.ui.components.CustomIconPickerSheet
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.icons.rounded.Apps
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.InstallMobile
import app.overbay.swiftfox.ui.icons.rounded.PushPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebAppInstallSheet(
    candidate: PageInstallCandidate,
    existingApp: WebAppEntity?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean,
        customIconSpec: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameDraft by remember(candidate.startUrl, existingApp?.id) {
        mutableStateOf(existingApp?.name ?: candidate.suggestedName)
    }
    var openAsStandalone by remember(candidate.startUrl, existingApp?.id) {
        mutableStateOf(existingApp?.openAsStandalone ?: candidate.isStandaloneCapable)
    }
    var showSlimHeader by remember(candidate.startUrl, existingApp?.id) {
        mutableStateOf(existingApp?.showSlimHeader ?: false)
    }
    var drawBehindStatusBar by remember(candidate.startUrl, existingApp?.id) {
        mutableStateOf(existingApp?.drawBehindStatusBar ?: false)
    }
    var customIconSpec by remember(candidate.startUrl, existingApp?.id) {
        mutableStateOf(existingApp?.customIcon)
    }
    var showIconPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = when {
                    existingApp != null -> "Update Home Screen App"
                    candidate.isStandaloneCapable -> "Install Web App"
                    else -> "Add to Home Screen"
                },
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WebAppPreviewIcon(
                    appId = existingApp?.id,
                    iconPath = existingApp?.iconPath,
                    iconUrl = candidate.bestIconUrl,
                    pageUrl = candidate.startUrl,
                    customIconSpec = customIconSpec,
                    label = nameDraft.ifBlank { candidate.suggestedName },
                    backgroundColorHex = candidate.backgroundColorHex ?: candidate.themeColorHex,
                    size = 56.dp,
                    modifier = Modifier.clickable { showIconPicker = true }
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (candidate.isStandaloneCapable) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = if (candidate.isStandaloneCapable) {
                                "Installable PWA (${candidate.displayMode.wireValue})"
                            } else {
                                "Webpage shortcut"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (candidate.isStandaloneCapable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = candidate.startUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { showIconPicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Customize icon")
                    }
                }
            }

            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("App or shortcut name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openAsStandalone = !openAsStandalone }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Open as standalone app", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (openAsStandalone) {
                                "Opens in its own edge-to-edge window in Recents"
                            } else {
                                "Opens as a regular tab inside Swiftfox"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = openAsStandalone,
                        onCheckedChange = { openAsStandalone = it }
                    )
                }
            }

            if (openAsStandalone) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSlimHeader = !showSlimHeader }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show slim header bar", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Include a compact Back, host & controls bar at the top",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showSlimHeader,
                            onCheckedChange = { showSlimHeader = it }
                        )
                    }
                }

                if (!showSlimHeader) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { drawBehindStatusBar = !drawBehindStatusBar }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Draw behind status bar", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Extend page content directly behind the transparent status bar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = drawBehindStatusBar,
                                onCheckedChange = { drawBehindStatusBar = it }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onConfirm(
                            nameDraft.trim().ifBlank { candidate.suggestedName },
                            openAsStandalone,
                            showSlimHeader,
                            drawBehindStatusBar,
                            customIconSpec
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when {
                            existingApp != null -> "Save & Pin"
                            openAsStandalone -> "Install"
                            else -> "Add"
                        }
                    )
                }
            }
        }
    }

    if (showIconPicker) {
        CustomIconPickerSheet(
            title = "App Icon",
            currentIconSpec = customIconSpec,
            allowResetToFavicon = true,
            previewUrl = candidate.startUrl,
            previewLabel = nameDraft.ifBlank { candidate.suggestedName },
            onSelectIcon = { customIconSpec = it },
            onDismiss = { showIconPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebAppEditSheet(
    webApp: WebAppEntity,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean,
        customIconSpec: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameDraft by remember(webApp.id) { mutableStateOf(webApp.name) }
    var openAsStandalone by remember(webApp.id) { mutableStateOf(webApp.openAsStandalone) }
    var showSlimHeader by remember(webApp.id) { mutableStateOf(webApp.showSlimHeader) }
    var drawBehindStatusBar by remember(webApp.id) { mutableStateOf(webApp.drawBehindStatusBar) }
    var customIconSpec by remember(webApp.id) { mutableStateOf(webApp.customIcon) }
    var showIconPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Edit Web App", style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WebAppPreviewIcon(
                    appId = webApp.id,
                    iconPath = webApp.iconPath,
                    iconUrl = webApp.iconUrl,
                    pageUrl = webApp.startUrl,
                    customIconSpec = customIconSpec,
                    label = nameDraft.ifBlank { webApp.name },
                    backgroundColorHex = webApp.backgroundColorHex ?: webApp.themeColorHex,
                    size = 56.dp,
                    modifier = Modifier.clickable { showIconPicker = true }
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = webApp.startUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { showIconPicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Customize icon")
                    }
                }
            }

            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openAsStandalone = !openAsStandalone }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Open as standalone app", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (openAsStandalone) "Standalone window in Recents" else "Opens in Swiftfox tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = openAsStandalone, onCheckedChange = { openAsStandalone = it })
                }
            }

            if (openAsStandalone) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSlimHeader = !showSlimHeader }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show slim header bar", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Include Back, host & controls menu at top",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showSlimHeader, onCheckedChange = { showSlimHeader = it })
                    }
                }

                if (!showSlimHeader) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { drawBehindStatusBar = !drawBehindStatusBar }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Draw behind status bar", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Extend page content directly behind the transparent status bar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = drawBehindStatusBar,
                                onCheckedChange = { drawBehindStatusBar = it }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSave(
                            nameDraft.trim().ifBlank { webApp.name },
                            openAsStandalone,
                            showSlimHeader,
                            drawBehindStatusBar,
                            customIconSpec
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (showIconPicker) {
        CustomIconPickerSheet(
            title = "App Icon",
            currentIconSpec = customIconSpec,
            allowResetToFavicon = true,
            previewUrl = webApp.startUrl,
            previewLabel = nameDraft.ifBlank { webApp.name },
            onSelectIcon = { customIconSpec = it },
            onDismiss = { showIconPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppsOverlay(
    webApps: List<WebAppEntity>,
    onBack: () -> Unit,
    onLaunchWebApp: (WebAppEntity) -> Unit,
    onRepinWebApp: (WebAppEntity) -> Unit,
    onUpdateWebApp: (
        existing: WebAppEntity,
        name: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean,
        customIconSpec: String?
    ) -> Unit,
    onRemoveWebApp: (WebAppEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingApp by remember { mutableStateOf<WebAppEntity?>(null) }
    var deletingApp by remember { mutableStateOf<WebAppEntity?>(null) }

    val trimmed = searchQuery.trim()
    val filtered = remember(webApps, trimmed) {
        if (trimmed.isEmpty()) webApps
        else webApps.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.host.contains(trimmed, ignoreCase = true) ||
                it.startUrl.contains(trimmed, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Web Apps") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (webApps.isEmpty()) {
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
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No installed web apps yet",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Open any site and tap Install app or Add to Home in Quick Actions (or the address bar badge on supported PWAs) to pin it to your Home screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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
                if (webApps.size > 3) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter web apps & shortcuts") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filtered,
                        key = { it.id },
                        contentType = { "web_app_row" }
                    ) { app ->
                        Surface(
                            onClick = { onLaunchWebApp(app) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                WebAppPreviewIcon(
                                    appId = app.id,
                                    iconPath = app.iconPath,
                                    iconUrl = app.iconUrl,
                                    pageUrl = app.startUrl,
                                    customIconSpec = app.customIcon,
                                    label = app.name,
                                    backgroundColorHex = app.backgroundColorHex ?: app.themeColorHex,
                                    size = 44.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${app.host} · ${if (app.openAsStandalone) "Standalone PWA" else "Browser shortcut"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onRepinWebApp(app) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = "Pin to Home screen",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                var actionsExpanded by remember(app.id) { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { actionsExpanded = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "More actions for ${app.name}"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = actionsExpanded,
                                        onDismissRequest = { actionsExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Pin to Home screen") },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.PushPin, contentDescription = null)
                                            },
                                            onClick = {
                                                actionsExpanded = false
                                                onRepinWebApp(app)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Edit, contentDescription = null)
                                            },
                                            onClick = {
                                                actionsExpanded = false
                                                editingApp = app
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                actionsExpanded = false
                                                deletingApp = app
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingApp?.let { target ->
        WebAppEditSheet(
            webApp = target,
            onDismiss = { editingApp = null },
            onSave = { name, standalone, slimHeader, drawBehindStatusBar, customIcon ->
                editingApp = null
                onUpdateWebApp(target, name, standalone, slimHeader, drawBehindStatusBar, customIcon)
            }
        )
    }

    deletingApp?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingApp = null },
            title = { Text("Remove ${target.name}?") },
            text = {
                Text(
                    "Removes ${target.name} from your Swiftfox Web Apps list and disables its home-screen shortcut.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingApp = null
                        onRemoveWebApp(target)
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingApp = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun WebAppPreviewIcon(
    appId: String?,
    iconPath: String?,
    iconUrl: String?,
    pageUrl: String,
    customIconSpec: String?,
    label: String,
    backgroundColorHex: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val hasCustom = remember(customIconSpec) {
        CustomIconSpec.parse(customIconSpec) != CustomIconSpec.None
    }
    if (hasCustom) {
        SiteIconBadge(
            url = pageUrl,
            fallbackLabel = label,
            customIcon = customIconSpec,
            size = size,
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val app = context.applicationContext as? SwiftfoxApp
    var bitmap by remember(appId, iconPath, iconUrl, pageUrl) {
        mutableStateOf<Bitmap?>(app?.webAppRepository?.iconHelper?.loadCachedIconFile(iconPath))
    }
    LaunchedEffect(appId, iconPath, iconUrl, pageUrl) {
        if (app == null) return@LaunchedEffect
        val helper = app.webAppRepository.iconHelper
        val resolved = withContext(Dispatchers.IO) {
            helper.loadCachedIconFile(iconPath)
                ?: helper.fetchBestIconBitmap(iconUrl = iconUrl, pageUrl = pageUrl)
        }
        if (resolved != null) {
            bitmap = resolved
        }
    }

    val img = remember(bitmap) { bitmap?.asImageBitmap() }
    if (img != null) {
        val bgArgb = remember(backgroundColorHex) {
            WebAppManifestParser.parseColorInt(backgroundColorHex)
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(if (bgArgb != null) Color(bgArgb) else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = img,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        SiteIconBadge(
            url = pageUrl,
            fallbackLabel = label,
            customIcon = null,
            size = size,
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
        )
    }
}
