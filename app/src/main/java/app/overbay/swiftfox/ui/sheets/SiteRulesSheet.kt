package app.overbay.swiftfox.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import app.overbay.swiftfox.ui.icons.rounded.AutoFixHigh
import app.overbay.swiftfox.ui.icons.rounded.DeleteOutline
import app.overbay.swiftfox.ui.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.ui.components.SwiftfoxSegmentedControl
import app.overbay.swiftfox.ui.components.SwiftfoxSelectablePill

private enum class UserAgentPreset(val label: String, val uaString: String?) {
    DEFAULT("Default", null),
    CHROME_DESKTOP(
        "Chrome Desktop",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    ),
    FIREFOX_ANDROID(
        "Firefox Android",
        "Mozilla/5.0 (Android 15; Mobile; rv:138.0) Gecko/138.0 Firefox/138.0"
    ),
    SAFARI_IOS(
        "Safari iOS",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"
    ),
    CUSTOM("Custom", "__CUSTOM__")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SiteRulesSheet(
    host: String,
    override: SiteOverrideEntity?,
    zappedRulesForHost: List<ZappedRuleEntity>,
    onDismiss: () -> Unit,
    onSetAdblockDisabled: (Boolean) -> Unit,
    onSetWebDarkOverride: (Boolean?) -> Unit,
    onSetSiteModeOverride: (SiteMode?) -> Unit = {},
    onSetJavascriptEnabled: (Boolean) -> Unit,
    onSetCookiesEnabled: (Boolean) -> Unit,
    onSetHttpsBypass: (Boolean) -> Unit,
    onSetCustomUserAgent: (String?) -> Unit = {},
    onDeleteZappedRule: (String) -> Unit,
    onLaunchZapper: () -> Unit,
    onResetAll: () -> Unit,
    globalAdblockEnabled: Boolean = true,
    isNativeRust: Boolean = true,
    isPrivate: Boolean = false,
    darkReaderSkippedNative: Boolean = false,
    allowLayoutAndUaOverrides: Boolean = true,
    persistentRuleIds: Set<String> = emptySet(),
    snackbarMessage: String? = null,
    snackbarId: Long = 0L,
    onClearSnackbar: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetSnackbarHostState = remember { SnackbarHostState() }
    val javascriptEnabled = override?.javascriptEnabled != false
    val savedUa = override?.customUserAgent?.takeIf { it.isNotBlank() }
    val matchedPreset = when (savedUa) {
        null -> UserAgentPreset.DEFAULT
        UserAgentPreset.CHROME_DESKTOP.uaString -> UserAgentPreset.CHROME_DESKTOP
        UserAgentPreset.FIREFOX_ANDROID.uaString -> UserAgentPreset.FIREFOX_ANDROID
        UserAgentPreset.SAFARI_IOS.uaString -> UserAgentPreset.SAFARI_IOS
        else -> UserAgentPreset.CUSTOM
    }

    var selectedUaPreset by remember(host, savedUa) { mutableStateOf(matchedPreset) }
    var customUaDraft by remember(host, savedUa) { mutableStateOf(savedUa.orEmpty()) }

    LaunchedEffect(snackbarId, snackbarMessage) {
        if (snackbarMessage == null) {
            sheetSnackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        sheetSnackbarHostState.currentSnackbarData?.dismiss()
        sheetSnackbarHostState.showSnackbar(
            message = snackbarMessage,
            duration = SnackbarDuration.Short
        )
        onClearSnackbar()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Site Rules",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onResetAll,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Reset all")
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val adblockSubtitle = when {
                        !globalAdblockEnabled -> "Disabled globally in Settings"
                        override?.adblockDisabled == true -> "Paused on $host"
                        isNativeRust -> "Active via Native Rust engine"
                        else -> "Active via Built-in matcher"
                    }
                    SiteSwitchRow(
                        title = "Ad & tracker blocking",
                        subtitle = adblockSubtitle,
                        checked = globalAdblockEnabled && override?.adblockDisabled != true,
                        enabled = globalAdblockEnabled,
                        onCheckedChange = { enabled -> onSetAdblockDisabled(!enabled) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SiteSwitchRow(
                        title = "JavaScript",
                        subtitle = "Allow scripts (disabling also pauses Dark Reader, Zapper & Reader)",
                        checked = override?.javascriptEnabled ?: true,
                        onCheckedChange = onSetJavascriptEnabled
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SiteSwitchRow(
                        title = "Cookies",
                        subtitle = "Store cookies for this browsing profile while on this host",
                        checked = override?.cookiesEnabled ?: true,
                        onCheckedChange = onSetCookiesEnabled
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SiteSwitchRow(
                        title = "Allow HTTP (bypass HTTPS-Only)",
                        subtitle = "Permit unencrypted HTTP loads for this host",
                        checked = override?.httpsOnlyBypass == true,
                        onCheckedChange = onSetHttpsBypass
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Web dark mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (override?.webDarkModeOverride == null && darkReaderSkippedNative) {
                    Text(
                        text = "Native dark mode detected and kept — choose Force dark to override",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val darkOptions = remember { listOf(null, true, false) }
                SwiftfoxSegmentedControl(
                    options = darkOptions,
                    selected = override?.webDarkModeOverride,
                    onSelect = onSetWebDarkOverride,
                    labelFor = { choice ->
                        when (choice) {
                            null -> "Default"
                            true -> "Force dark"
                            false -> "Force light"
                        }
                    }
                )
            }

            if (allowLayoutAndUaOverrides) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Site layout",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val layoutOptions = remember { listOf<SiteMode?>(null, SiteMode.MOBILE, SiteMode.DESKTOP) }
                    val selectedMode = when (override?.siteModeOverride) {
                        SiteMode.MOBILE.name -> SiteMode.MOBILE
                        SiteMode.DESKTOP.name -> SiteMode.DESKTOP
                        else -> null
                    }
                    SwiftfoxSegmentedControl(
                        options = layoutOptions,
                        selected = selectedMode,
                        onSelect = onSetSiteModeOverride,
                        labelFor = { choice ->
                            when (choice) {
                                null -> "Posture"
                                SiteMode.MOBILE -> "Mobile"
                                SiteMode.DESKTOP -> "Desktop"
                            }
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Custom User-Agent",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserAgentPreset.entries.forEach { preset ->
                            SwiftfoxSelectablePill(
                                label = preset.label,
                                selected = selectedUaPreset == preset,
                                onClick = {
                                    selectedUaPreset = preset
                                    when (preset) {
                                        UserAgentPreset.DEFAULT -> {
                                            customUaDraft = ""
                                            onSetCustomUserAgent(null)
                                        }
                                        UserAgentPreset.CUSTOM -> Unit
                                        else -> {
                                            customUaDraft = preset.uaString.orEmpty()
                                            onSetCustomUserAgent(preset.uaString)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    if (selectedUaPreset == UserAgentPreset.CUSTOM) {
                        val trimmedCustomUa = customUaDraft.trim()
                        val customUaValid = trimmedCustomUa.all { it in '\u0020'..'\u007E' }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = customUaDraft,
                                onValueChange = { customUaDraft = it },
                                label = { Text("User-Agent string") },
                                isError = trimmedCustomUa.isNotEmpty() && !customUaValid,
                                supportingText = if (trimmedCustomUa.isNotEmpty() && !customUaValid) {
                                    { Text("Use printable ASCII characters only") }
                                } else {
                                    null
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    val nextUa = trimmedCustomUa.takeIf { it.isNotEmpty() }
                                    if (nextUa == null) {
                                        selectedUaPreset = UserAgentPreset.DEFAULT
                                    }
                                    onSetCustomUserAgent(nextUa)
                                },
                                enabled = customUaValid && (trimmedCustomUa.isNotEmpty() || savedUa != null),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Zapped elements (${zappedRulesForHost.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    onClick = onLaunchZapper,
                    enabled = javascriptEnabled,
                    shape = RoundedCornerShape(24.dp),
                    color = if (javascriptEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    },
                    contentColor = if (javascriptEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
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
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = if (javascriptEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Zap element", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (zappedRulesForHost.isEmpty()) {
                Text(
                    text = "No permanent zaps saved for $host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        zappedRulesForHost.forEachIndexed { idx, rule ->
                            val isSavedReadOnlyInPrivate = isPrivate && rule.id in persistentRuleIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.label.ifBlank { rule.selector },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitleLine = buildString {
                                        if (isPrivate) {
                                            append(if (isSavedReadOnlyInPrivate) "Saved (normal mode)" else "Private session")
                                            if (rule.label.isNotBlank()) append(" · ")
                                        }
                                        if (rule.label.isNotBlank()) {
                                            append(rule.selector)
                                        }
                                    }
                                    if (subtitleLine.isNotEmpty()) {
                                        Text(
                                            text = subtitleLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isSavedReadOnlyInPrivate) {
                                    Text(
                                        text = "Saved",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = { onDeleteZappedRule(rule.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete zapped rule"
                                        )
                                    }
                                }
                            }
                            if (idx < zappedRulesForHost.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
        SnackbarHost(
            hostState = sheetSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        }
    }
}

@Composable
private fun SiteSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp),
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
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}