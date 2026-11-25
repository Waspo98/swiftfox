package app.overbay.swiftfox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.ui.theme.rememberReduceMotion

/**
 * Full-width connected tonal segmented selector for 2-4 mutually exclusive choices.
 *
 * Uses a 48dp touch-target floor on every segment while keeping a sleek concentric
 * active capsule inside a surfaceContainerHigh track so labels never look boxy or wireframe.
 */
@Composable
fun <T> SwiftfoxSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String,
    modifier: Modifier = Modifier,
    iconFor: ((T) -> ImageVector)? = null,
    fontFamilyFor: ((T) -> FontFamily)? = null,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return

    val reduceMotion = rememberReduceMotion()
    val fontScale = LocalDensity.current.fontScale
    val activeIconFor = if (options.size <= 2 || (options.size == 3 && fontScale < 1.2f)) {
        iconFor
    } else {
        null
    }
    val isDense = options.size >= 4
    val baseTextStyle = if (isDense) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }

    val trackColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
    }
    val trackBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (enabled) 0.35f else 0.20f
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = trackColor,
        border = BorderStroke(1.dp, trackBorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val targetContainer = when {
                    isSelected && enabled -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    else -> Color.Transparent
                }
                val targetContent = when {
                    isSelected && enabled -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                }

                val containerColorState = animateColorAsState(
                    targetValue = targetContainer,
                    animationSpec = if (reduceMotion) snap() else tween(durationMillis = 160),
                    label = "segmentContainer"
                )
                val contentColor by animateColorAsState(
                    targetValue = targetContent,
                    animationSpec = if (reduceMotion) snap() else tween(durationMillis = 160),
                    label = "segmentContent"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelect(option) }
                        )
                        .padding(3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .drawBehind {
                            val bg = containerColorState.value
                            if (bg.alpha > 0f) {
                                drawRect(bg)
                            }
                        }
                        .padding(
                            horizontal = if (isDense) 6.dp else 10.dp,
                            vertical = 8.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        if (activeIconFor != null) {
                            Icon(
                                imageVector = activeIconFor(option),
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = labelFor(option),
                            style = baseTextStyle.copy(
                                fontFamily = fontFamilyFor?.invoke(option) ?: baseTextStyle.fontFamily,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tonal filled pill for wrapable or horizontally scrolling single-choice presets (such as
 * User-Agent presets or Bookmark folder filters) without hollow wireframe borders.
 */
@Composable
fun SwiftfoxSelectablePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheckmark: Boolean = true,
    role: Role = Role.RadioButton
) {
    val reduceMotion = rememberReduceMotion()
    val targetBg = when {
        selected && enabled -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
    }
    val targetFg = when {
        selected && enabled -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 150),
        label = "pillBg"
    )
    val fgColor by animateColorAsState(
        targetValue = targetFg,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 150),
        label = "pillFg"
    )
    val border = if (selected) {
        BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f)
        )
    } else {
        BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.28f else 0.16f)
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        contentColor = fgColor,
        border = border,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected && showCheckmark) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
