package app.overbay.swiftfox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.engine.ContextMenuHit
import app.overbay.swiftfox.engine.ExternalAppPrompt
import app.overbay.swiftfox.engine.HttpsUpgradeErrorPrompt
import app.overbay.swiftfox.engine.SitePermissionPrompt
import app.overbay.swiftfox.ui.icons.automirrored.OpenInNew
import app.overbay.swiftfox.ui.icons.rounded.ContentCopy
import app.overbay.swiftfox.ui.icons.rounded.Download
import app.overbay.swiftfox.ui.icons.rounded.Shield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextMenuBottomSheet(
    hit: ContextMenuHit,
    onDismiss: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInPrivateTab: (() -> Unit)? = null,
    onCopyCleanLink: () -> Unit,
    onShareCleanLink: () -> Unit,
    onDownloadImage: (() -> Unit)?,
    openInNewTabLabel: String = "Open in new tab"
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = hit.url,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ContextActionRow(openInNewTabLabel, Icons.AutoMirrored.Rounded.OpenInNew, onOpenInNewTab)
            if (onOpenInPrivateTab != null) {
                ContextActionRow("Open in private tab", Icons.Rounded.Shield, onOpenInPrivateTab)
            }
            ContextActionRow("Copy clean link", Icons.Rounded.ContentCopy, onCopyCleanLink)
            ContextActionRow("Share clean link", Icons.Rounded.Share, onShareCleanLink)
            if (onDownloadImage != null) {
                ContextActionRow("Save image", Icons.Rounded.Download, onDownloadImage)
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun ExternalAppDialog(
    prompt: ExternalAppPrompt,
    onOpenApp: () -> Unit,
    onStayInSwiftfox: () -> Unit,
    stayLabel: String = "Stay in Swiftfox"
) {
    AlertDialog(
        onDismissRequest = onStayInSwiftfox,
        title = { Text("Open in ${prompt.appLabel}?") },
        text = {
            Text(
                text = "This link wants to open in ${prompt.appLabel}.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenApp) {
                Text("Open app")
            }
        },
        dismissButton = {
            TextButton(onClick = onStayInSwiftfox) {
                Text(stayLabel)
            }
        }
    )
}

@Composable
internal fun HttpsFallbackDialog(
    prompt: HttpsUpgradeErrorPrompt,
    onAllowHttp: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("HTTPS unavailable for ${prompt.host}") },
        text = {
            Text(
                text = "${prompt.reason}. Swiftfox upgraded this request to HTTPS. Continue over unencrypted HTTP for this site?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onAllowHttp) {
                Text("Load HTTP")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Go back")
            }
        }
    )
}

@Composable
internal fun SitePermissionDialog(
    prompt: SitePermissionPrompt,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = if (prompt.isGeolocation) {
        "your location"
    } else {
        prompt.resources.joinToString(", ") { res ->
            when {
                res.contains("VIDEO", true) -> "camera"
                res.contains("AUDIO", true) -> "microphone"
                res.contains("PROTECTED", true) -> "protected media (DRM)"
                else -> "device capability"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${prompt.host} requests access") },
        text = {
            Text("Allow ${prompt.host} to use $label?")
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAllowOnce) {
                    Text("Once")
                }
                TextButton(onClick = onAlwaysAllow) {
                    Text("Always")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Deny")
            }
        }
    )
}
