package app.overbay.swiftfox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.ui.theme.rememberReduceMotion

internal data class HorizontalFadeFractions(
    val leftFraction: Float,
    val rightFraction: Float
)

internal fun resolveHorizontalFadeFractions(
    showStartFade: Boolean,
    showEndFade: Boolean,
    startFadeWidthPx: Float,
    endFadeWidthPx: Float,
    widthPx: Float,
    isRtl: Boolean
): HorizontalFadeFractions? {
    if ((!showStartFade && !showEndFade) || widthPx <= 1f) return null
    val showLeft = if (isRtl) showEndFade else showStartFade
    val showRight = if (isRtl) showStartFade else showEndFade
    val leftWidthPx = if (isRtl) endFadeWidthPx else startFadeWidthPx
    val rightWidthPx = if (isRtl) startFadeWidthPx else endFadeWidthPx
    val leftCap = if (isRtl) 0.34f else 0.28f
    val rightCap = if (isRtl) 0.28f else 0.34f
    val leftFraction = if (showLeft) (leftWidthPx / widthPx).coerceIn(0f, leftCap) else 0f
    val rightFraction = if (showRight) (rightWidthPx / widthPx).coerceIn(0f, rightCap) else 0f
    if (leftFraction <= 0f && rightFraction <= 0f) return null
    return HorizontalFadeFractions(
        leftFraction = leftFraction,
        rightFraction = rightFraction
    )
}

/**
 * Applies smooth, RTL-aware, background-agnostic fading masks on the leading and/or trailing edges
 * of a horizontally scrollable row using `CompositingStrategy.Offscreen` + `BlendMode.DstIn`.
 * Keeps a stable modifier chain (switching to `CompositingStrategy.Auto` when idle) and caches
 * gradient brushes via `drawWithCache` so 120Hz scrolling stays free of mid-fling node churn.
 */
fun Modifier.horizontalFadingEdges(
    showStartFade: Boolean,
    showEndFade: Boolean,
    startFadeWidth: Dp = 20.dp,
    endFadeWidth: Dp = 28.dp
): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (showStartFade || showEndFade) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithCache {
        val fractions = resolveHorizontalFadeFractions(
            showStartFade = showStartFade,
            showEndFade = showEndFade,
            startFadeWidthPx = startFadeWidth.toPx(),
            endFadeWidthPx = endFadeWidth.toPx(),
            widthPx = size.width,
            isRtl = layoutDirection == LayoutDirection.Rtl
        )
        val brush = fractions?.let { (leftFrac, rightFrac) ->
            val stops = buildList {
                if (leftFrac > 0f) {
                    add(0f to Color.Transparent)
                    add(leftFrac to Color.Black)
                } else {
                    add(0f to Color.Black)
                }
                if (rightFrac > 0f) {
                    add((1f - rightFrac).coerceAtLeast(leftFrac) to Color.Black)
                    add(1f to Color.Transparent)
                } else {
                    add(1f to Color.Black)
                }
            }.toTypedArray()
            Brush.horizontalGradient(colorStops = stops)
        }
        onDrawWithContent {
            drawContent()
            if (brush != null) {
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
        }
    }

/**
 * Full-bleed borderless Editorial Tabs strip for switching between Swiftfox Spaces on the
 * Start Page and Tab Switcher.
 *
 * Inactive Spaces recede into quiet text + icon labels without heavy pill containers; the active
 * Space illuminates in crisp foreground ink with a smooth `2.5dp` accent underline bar anchored
 * to a subtle hairline rule. Every tab enforces a `48dp` interactive hit target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorialSpacesTabStrip(
    spaces: List<SpaceEntity>,
    selectedSpaceId: String?,
    onSelectSpace: (String) -> Unit,
    onLongPressSpace: (SpaceEntity) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    iconSize: Dp = 17.dp,
    tabCountForSpace: ((String) -> Int)? = null,
    showThemeDot: Boolean = false,
    showLockIndicator: Boolean = false,
    isReorderMode: Boolean = false,
    reorderController: DragReorderController<SpaceEntity>? = null,
    onCreateSpaceClick: (() -> Unit)? = null
) {
    val reduceMotion = rememberReduceMotion()
    val listState = rememberLazyListState()
    val canScrollBackward by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val canScrollForward by remember(listState) { derivedStateOf { listState.canScrollForward } }

    // Keep the active Space smoothly scrolled into view when changed externally
    LaunchedEffect(selectedSpaceId, spaces.size, isReorderMode) {
        if (isReorderMode || selectedSpaceId == null) return@LaunchedEffect
        val index = spaces.indexOfFirst { it.id == selectedSpaceId }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { item ->
                item.index == index &&
                    item.offset >= 0 &&
                    item.offset + item.size <= listState.layoutInfo.viewportEndOffset
            }
            if (!visible) {
                if (reduceMotion) {
                    listState.scrollToItem(index)
                } else {
                    listState.animateScrollToItem(index)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        LazyRow(
            state = listState,
            userScrollEnabled = !isReorderMode,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (isReorderMode) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdges(
                    showStartFade = !isReorderMode && canScrollBackward,
                    showEndFade = !isReorderMode && canScrollForward
                )
        ) {
            items(
                items = spaces,
                key = { it.id },
                contentType = { "editorial_space_tab" }
            ) { space ->
                val isSelected = selectedSpaceId == space.id
                val rawCount = tabCountForSpace?.invoke(space.id)
                val visibleCount = rawCount?.takeIf { it > 0 }
                val itemModifier = Modifier
                    .then(if (!isReorderMode) Modifier.animateItem() else Modifier)
                    .then(
                        if (reorderController != null) {
                            Modifier.dragReorderItem(
                                key = space.id,
                                enabled = isReorderMode,
                                controller = reorderController
                            )
                        } else {
                            Modifier
                        }
                    )

                EditorialSpaceTabItem(
                    space = space,
                    selected = isSelected,
                    iconSize = iconSize,
                    visibleTabCount = visibleCount,
                    showThemeDot = showThemeDot,
                    showLockIndicator = showLockIndicator,
                    isReorderMode = isReorderMode,
                    onClick = { if (!isReorderMode) onSelectSpace(space.id) },
                    onLongClick = { if (!isReorderMode) onLongPressSpace(space) },
                    modifier = itemModifier
                )
            }

            if (!isReorderMode && onCreateSpaceClick != null) {
                item(key = "editorial_add_space", contentType = "editorial_add_space") {
                    EditorialAddSpaceTab(onClick = onCreateSpaceClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorialSpaceTabItem(
    space: SpaceEntity,
    selected: Boolean,
    iconSize: Dp,
    visibleTabCount: Int?,
    showThemeDot: Boolean,
    showLockIndicator: Boolean,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val colorSpec = if (reduceMotion) snap<Color>() else tween(durationMillis = 160)
    val floatSpec = if (reduceMotion) snap<Float>() else tween(durationMillis = 200, easing = FastOutSlowInEasing)

    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        },
        animationSpec = colorSpec,
        label = "space_tab_label_color"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = colorSpec,
        label = "space_tab_icon_color"
    )
    val indicatorProgress by animateFloatAsState(
        targetValue = if (selected && !isReorderMode) 1f else 0f,
        animationSpec = floatSpec,
        label = "space_tab_indicator"
    )

    val dotColor = if (showThemeDot) parseSpaceColorHexOrNull(space.themeColorHex) else null
    val hasContainer = showLockIndicator && !space.containerProfileName.isNullOrBlank()
    val accessibilityDescription = buildString {
        append(space.name)
        append(" Space")
        if (hasContainer) append(", isolated container")
        if (visibleTabCount != null) {
            append(", ")
            append(visibleTabCount)
            append(if (visibleTabCount == 1) " open tab" else " open tabs")
        }
    }

    if (isReorderMode) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
            modifier = modifier
                .padding(vertical = 4.dp)
                .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Reorder ${space.name} Space"
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                SpaceIconGlyph(
                    iconKey = space.iconKey,
                    tint = MaterialTheme.colorScheme.primary,
                    size = iconSize
                )
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = accessibilityDescription
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            SpaceIconGlyph(
                iconKey = space.iconKey,
                tint = iconColor,
                size = iconSize
            )
            Text(
                text = space.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
            if (hasContainer) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            if (visibleTabCount != null) {
                Text(
                    text = visibleTabCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    },
                    maxLines = 1
                )
            }
        }

        // Sleek editorial active indicator anchored cleanly on the bottom hairline
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(2.5.dp)
                .graphicsLayer {
                    scaleX = indicatorProgress
                    alpha = indicatorProgress
                }
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun EditorialAddSpaceTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = Color.Transparent,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "New Space"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "New",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

/**
 * Bare-glyph Editorial Space button for the expanded Spaces sidebar footer.
 *
 * Keeps inactive Spaces visually weightless (no chunky rounded square box) while lighting up
 * the active Space in [accentColor] with a crisp `18dp × 2.5dp` bottom bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorialSpaceFooterGlyph(
    selected: Boolean,
    accentColor: Color,
    contentDescriptionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isReorderMode: Boolean = false,
    themeDotColor: Color? = null,
    glyph: @Composable (tint: Color) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val colorSpec = if (reduceMotion) snap<Color>() else tween(durationMillis = 160)
    val floatSpec = if (reduceMotion) snap<Float>() else tween(durationMillis = 200, easing = FastOutSlowInEasing)

    val tint by animateColorAsState(
        targetValue = if (selected) {
            accentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
        },
        animationSpec = colorSpec,
        label = "footer_space_glyph_tint"
    )
    val indicatorProgress by animateFloatAsState(
        targetValue = if (selected && !isReorderMode) 1f else 0f,
        animationSpec = floatSpec,
        label = "footer_space_indicator"
    )

    if (isReorderMode) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
            modifier = modifier
                .size(48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Reorder $contentDescriptionText"
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                glyph(MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = contentDescriptionText
            },
        contentAlignment = Alignment.Center
    ) {
        glyph(tint)

        if (themeDotColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(themeDotColor)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .width(18.dp)
                .height(2.5.dp)
                .graphicsLayer {
                    scaleX = indicatorProgress
                    alpha = indicatorProgress
                }
                .clip(CircleShape)
                .background(accentColor)
        )
    }
}

/**
 * Bare-glyph Editorial Space button for the collapsed vertical sidebar rail.
 *
 * Uses a leading vertical `3dp × 20dp` accent pill on the active Space so the navigation zone
 * visually separates at a glance from rounded-square Essential/tab favicons below it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorialSpaceRailGlyph(
    selected: Boolean,
    accentColor: Color,
    contentDescriptionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    themeDotColor: Color? = null,
    glyph: @Composable (tint: Color) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val colorSpec = if (reduceMotion) snap<Color>() else tween(durationMillis = 160)
    val floatSpec = if (reduceMotion) snap<Float>() else tween(durationMillis = 200, easing = FastOutSlowInEasing)

    val tint by animateColorAsState(
        targetValue = if (selected) {
            accentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
        },
        animationSpec = colorSpec,
        label = "rail_space_glyph_tint"
    )
    val indicatorProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = floatSpec,
        label = "rail_space_indicator"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = contentDescriptionText
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 1.dp)
                .width(3.dp)
                .height(20.dp)
                .graphicsLayer {
                    scaleY = indicatorProgress
                    alpha = indicatorProgress
                }
                .clip(CircleShape)
                .background(accentColor)
        )

        glyph(tint)

        if (themeDotColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(themeDotColor)
            )
        }
    }
}

/**
 * Quiet borderless icon action matching the Editorial footer glyph density (e.g., New Space `+`).
 */
@Composable
fun EditorialFooterActionGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

internal fun parseSpaceColorHexOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or rgb).toInt())
}
