package app.overbay.swiftfox.ui.zapper

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import app.overbay.swiftfox.ui.icons.automirrored.Undo
import androidx.compose.material.icons.rounded.Add
import app.overbay.swiftfox.ui.icons.rounded.AutoFixHigh
import app.overbay.swiftfox.ui.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import app.overbay.swiftfox.ui.icons.rounded.Remove
import app.overbay.swiftfox.ui.icons.rounded.Visibility
import app.overbay.swiftfox.ui.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.features.zapper.ZapperUiState
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import kotlin.math.roundToInt

@Composable
fun ZapperHudOverlay(
    state: ZapperUiState,
    onAdjustScope: (Int) -> Unit,
    onSetScopeDepth: (Int) -> Unit,
    onSetPreview: (Boolean) -> Unit,
    onZapOnce: () -> Unit,
    onBlockPermanently: () -> Unit,
    onUndoTemporaryZap: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()

    val containerModifier = modifier
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .widthIn(max = 560.dp)
        .fillMaxWidth()
        .let { base -> if (reduceMotion) base else base.animateContentSize() }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(
                alpha = if (state.isPreviewing) 0.95f else 0.65f
            )
        ),
        modifier = containerModifier
    ) {
        if (state.selectedSelector == null) {
            IdleZapperHudContent(
                state = state,
                onUndoTemporaryZap = onUndoTemporaryZap,
                onDone = onDone
            )
        } else {
            SelectedZapperHudContent(
                state = state,
                onAdjustScope = onAdjustScope,
                onSetScopeDepth = onSetScopeDepth,
                onSetPreview = onSetPreview,
                onZapOnce = onZapOnce,
                onBlockPermanently = onBlockPermanently,
                onUndoTemporaryZap = onUndoTemporaryZap,
                onDone = onDone
            )
        }
    }
}

@Composable
private fun IdleZapperHudContent(
    state: ZapperUiState,
    onUndoTemporaryZap: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoFixHigh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Tap any element to zap",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val statusText = state.lastStatusMessage
                ?: if (state.temporaryZapCount > 0 && !state.lastTemporaryZapSummary.isNullOrBlank()) {
                    "Hid ${state.lastTemporaryZapSummary}"
                } else {
                    null
                }
            if (!statusText.isNullOrBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (state.temporaryZapCount > 0) {
            ZapperActionPill(
                label = "Undo",
                icon = Icons.AutoMirrored.Rounded.Undo,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onUndoTemporaryZap
            )
        }
        ZapperActionPill(
            label = "Done",
            icon = null,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconTint = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            onClick = onDone
        )
    }
}

@Composable
private fun SelectedZapperHudContent(
    state: ZapperUiState,
    onAdjustScope: (Int) -> Unit,
    onSetScopeDepth: (Int) -> Unit,
    onSetPreview: (Boolean) -> Unit,
    onZapOnce: () -> Unit,
    onBlockPermanently: () -> Unit,
    onUndoTemporaryZap: () -> Unit,
    onDone: () -> Unit
) {
    val matchLabel = if (state.matchCount == 1) "1 match" else "${state.matchCount} matches"
    val maxDepth = state.maxScopeDepth.coerceAtLeast(0)
    val clampedDepth = state.scopeDepth.coerceIn(0, maxDepth)
    var sliderDepth by remember(maxDepth) { mutableFloatStateOf(clampedDepth.toFloat()) }
    var lastDispatchedDepth by remember(maxDepth) { mutableIntStateOf(clampedDepth) }
    var isDraggingSlider by remember(maxDepth) { mutableStateOf(false) }

    LaunchedEffect(clampedDepth, maxDepth, isDraggingSlider) {
        if (!isDraggingSlider) {
            sliderDepth = clampedDepth.toFloat()
            lastDispatchedDepth = clampedDepth
        }
    }
    val displayedDepth = if (isDraggingSlider) lastDispatchedDepth else clampedDepth

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: element summary + match count pill + Done close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.selectedSummary.ifBlank { "Selected element" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = matchLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            IconButton(
                onClick = onDone,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Done"
                )
            }
        }

        // Selector preview
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.selectedSelector.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Parent / Child scope controls + Live Preview toggle
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Parent / Child scope",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val scopeStatus = when {
                        maxDepth > 0 && state.isPreviewing -> "${displayedDepth + 1} / ${maxDepth + 1} · Live preview"
                        maxDepth > 0 -> "${displayedDepth + 1} / ${maxDepth + 1}"
                        state.isPreviewing -> "Live preview"
                        else -> null
                    }
                    if (scopeStatus != null) {
                        Text(
                            text = scopeStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ZapperPreviewTogglePill(
                    isPreviewing = state.isPreviewing,
                    onSetPreview = onSetPreview
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onAdjustScope(-1) },
                    enabled = clampedDepth > 0,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Select child element"
                    )
                }

                if (maxDepth > 0) {
                    Slider(
                        value = sliderDepth.coerceIn(0f, maxDepth.toFloat()),
                        onValueChange = { value ->
                            isDraggingSlider = true
                            sliderDepth = value
                            val next = value.roundToInt().coerceIn(0, maxDepth)
                            if (next != lastDispatchedDepth) {
                                lastDispatchedDepth = next
                                onSetScopeDepth(next)
                            }
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            sliderDepth = lastDispatchedDepth.toFloat()
                        },
                        valueRange = 0f..maxDepth.toFloat(),
                        steps = (maxDepth - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = "Exact element",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = { onAdjustScope(1) },
                    enabled = clampedDepth < maxDepth,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Expand to parent element"
                    )
                }
            }
        }

        // Action pills — compact single-row layout so HUD never crowds the viewport
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZapperActionPill(
                label = "Zap once",
                icon = Icons.Rounded.AutoFixHigh,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onZapOnce,
                modifier = Modifier.weight(1f)
            )
            ZapperActionPill(
                label = "Block",
                icon = Icons.Rounded.Block,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                iconTint = MaterialTheme.colorScheme.onPrimary,
                onClick = onBlockPermanently,
                modifier = Modifier.weight(1f)
            )
            if (state.temporaryZapCount > 0) {
                FilledTonalIconButton(
                    onClick = onUndoTemporaryZap,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo temporary zap (${state.temporaryZapCount})"
                    )
                }
            }
        }
    }
}

@Composable
private fun ZapperPreviewTogglePill(
    isPreviewing: Boolean,
    onSetPreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isPreviewing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isPreviewing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isPreviewing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Surface(
        checked = isPreviewing,
        onCheckedChange = onSetPreview,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (isPreviewing) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = if (isPreviewing) {
                    Icons.Rounded.VisibilityOff
                } else {
                    Icons.Rounded.Visibility
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isPreviewing) "Previewing" else "Preview",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ZapperActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
