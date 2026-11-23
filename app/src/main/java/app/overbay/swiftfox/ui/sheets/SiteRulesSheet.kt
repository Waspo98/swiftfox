package app.overbay.swiftfox.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.SiteMode

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
    onSetSiteModeOverride: (SiteMode?) -> Unit,
    onSetJavascriptEnabled: (Boolean) -> Unit,
    onSetCookiesEnabled: (Boolean) -> Unit,
    onSetHttpsBypass: (Boolean) -> Unit,
    onSetCustomUserAgent: (String?) -> Unit,
    onDeleteZappedRule: (String) -> Unit,
    onLaunchZapper: () -> Unit,
    onResetAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
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
                    SiteSwitchRow(
                        title = "Ad & tracker blocking",
                        subtitle = if (override?.adblockDisabled == true) "Paused on $host" else "Active via adblock-rust",
                        checked = override?.adblockDisabled != true,
                        onCheckedChange = { enabled -> onSetAdblockDisabled(!enabled) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SiteSwitchRow(
                        title = "JavaScript",
                        subtitle = "Allow scripts to execute on this host",
                        checked = override?.javascriptEnabled ?: true,
                        onCheckedChange = onSetJavascriptEnabled
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SiteSwitchRow(
                        title = "Cookies",
                        subtitle = "Store site login & session cookies",
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleFilterChip("Global", override?.webDarkModeOverride == null) { onSetWebDarkOverride(null) }
                    RuleFilterChip("Force dark", override?.webDarkModeOverride == true) { onSetWebDarkOverride(true) }
                    RuleFilterChip("Force light", override?.webDarkModeOverride == false) { onSetWebDarkOverride(false) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Site layout",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mode = override?.siteModeOverride
                    RuleFilterChip("Posture default", mode == null) { onSetSiteModeOverride(null) }
                    RuleFilterChip("Mobile", mode == SiteMode.MOBILE.name) { onSetSiteModeOverride(SiteMode.MOBILE) }
                    RuleFilterChip("Desktop", mode == SiteMode.DESKTOP.name) { onSetSiteModeOverride(SiteMode.DESKTOP) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Custom User-Agent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UserAgentPreset.entries.forEach { preset ->
                        RuleFilterChip(
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customUaDraft,
                            onValueChange = { customUaDraft = it },
                            label = { Text("User-Agent string") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                onSetCustomUserAgent(customUaDraft.trim().takeIf { it.isNotEmpty() })
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Apply")
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
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            tint = MaterialTheme.colorScheme.primary,
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
                                    if (rule.label.isNotBlank()) {
                                        Text(
                                            text = rule.selector,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
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
    }
}

@Composable
private fun SiteSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RuleFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.heightIn(min = 48.dp)
    )
}