package app.overbay.swiftfox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import app.overbay.swiftfox.ui.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import kotlin.math.hypot

enum class DragAxis {
    HORIZONTAL,
    VERTICAL,
    BOTH
}

/**
 * Coordinates drag-and-drop reordering for 1D rows, 1D lists, and 2D grids in explicit Reorder Mode.
 *
 * Because layout slots are tracked via `boundsInRoot()` BEFORE `graphicsLayer`, swapping two items
 * immediately compensates `dragOffset` by `(oldSlot.topLeft - newSlot.topLeft)` so the dragged tile
 * remains pixel-locked under the user's finger with zero jump or flicker.
 *
 * For 2D `Column`/`Row` grids where items move across rows during drag, attach
 * [dragReorderContainer] to the outer grid container and pass `handlePointerInput = false` to
 * [dragReorderItem] so recomposition across rows never cancels the active drag gesture.
 */
class DragReorderController<T>(
    private val keyOf: (T) -> String,
    private val axis: DragAxis,
    private val onCommitOrder: (List<String>) -> Unit,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    val items: SnapshotStateList<T> = mutableStateListOf()

    var draggingKey by mutableStateOf<String?>(null)
        private set

    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    private val slotBounds = LinkedHashMap<String, Rect>()
    private var lastSwapTimeMs = 0L
    private var pendingCommittedKeys: List<String>? = null
    private var pendingCommitTimeMs = 0L

    fun syncFromSource(source: List<T>) {
        if (draggingKey != null) return
        val sourceKeys = source.map(keyOf)
        val pending = pendingCommittedKeys
        if (pending != null) {
            if (sourceKeys == pending) {
                pendingCommittedKeys = null
            } else if (sourceKeys.toSet() == pending.toSet() && clockMs() - pendingCommitTimeMs < 400L) {
                // Ignore transient pre-commit frame while ViewModel state settles
                return
            } else {
                pendingCommittedKeys = null
            }
        }
        val currentKeys = items.map(keyOf)
        if (sourceKeys != currentKeys || items.size != source.size || items.toList() != source) {
            items.clear()
            items.addAll(source)
            slotBounds.keys.retainAll(sourceKeys.toSet())
        }
    }

    fun updateSlotBounds(key: String, boundsInRoot: Rect) {
        if (boundsInRoot.width > 0f && boundsInRoot.height > 0f) {
            slotBounds[key] = boundsInRoot
        }
    }

    /**
     * Resolves which item's slot contains [rootPoint] (with a small hit-slop margin for gaps
     * between tiles) so container-level drag gestures can start a drag seamlessly.
     */
    fun findKeyAt(rootPoint: Offset, slopPx: Float = 16f): String? {
        val activeKeys = items.map(keyOf)
        // Exact bounding-box hit first
        for (key in activeKeys) {
            val rect = slotBounds[key] ?: continue
            if (rect.contains(rootPoint)) return key
        }
        // Small gap tolerance around tiles
        var bestKey: String? = null
        var bestDist = Float.MAX_VALUE
        for (key in activeKeys) {
            val rect = slotBounds[key] ?: continue
            val expanded = Rect(
                left = rect.left - slopPx,
                top = rect.top - slopPx,
                right = rect.right + slopPx,
                bottom = rect.bottom + slopPx
            )
            if (expanded.contains(rootPoint)) {
                val dist = hypot(rootPoint.x - rect.center.x, rootPoint.y - rect.center.y)
                if (dist < bestDist) {
                    bestDist = dist
                    bestKey = key
                }
            }
        }
        return bestKey
    }

    fun startDrag(key: String, haptic: HapticFeedback?) {
        if (items.none { keyOf(it) == key }) return
        pendingCommittedKeys = null
        draggingKey = key
        dragOffset = Offset.Zero
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragDelta(delta: Offset, haptic: HapticFeedback?) {
        val activeKey = draggingKey ?: return
        onDragDelta(activeKey, delta, haptic)
    }

    fun onDragDelta(key: String, delta: Offset, haptic: HapticFeedback?) {
        if (draggingKey != key) return
        val filteredDelta = when (axis) {
            DragAxis.HORIZONTAL -> Offset(delta.x, delta.y * 0.15f)
            DragAxis.VERTICAL -> Offset(delta.x * 0.15f, delta.y)
            DragAxis.BOTH -> delta
        }
        dragOffset += filteredDelta

        val currentRect = slotBounds[key] ?: return
        val liveCenter = currentRect.center + dragOffset

        val now = clockMs()
        if (now - lastSwapTimeMs < 45L) return

        val fromIdx = items.indexOfFirst { keyOf(it) == key }
        if (fromIdx < 0) return

        var bestTargetIdx = -1
        var bestDist = Float.MAX_VALUE
        for (idx in items.indices) {
            if (idx == fromIdx) continue
            val candidateKey = keyOf(items[idx])
            val rect = slotBounds[candidateKey] ?: continue
            // Slight inset margin prevents rapid ping-ponging right on slot edges
            val insetX = rect.width * 0.12f
            val insetY = rect.height * 0.12f
            val hit = when (axis) {
                DragAxis.HORIZONTAL -> liveCenter.x in (rect.left + insetX)..(rect.right - insetX)
                DragAxis.VERTICAL -> liveCenter.y in (rect.top + insetY)..(rect.bottom - insetY)
                DragAxis.BOTH -> liveCenter.x in (rect.left + insetX)..(rect.right - insetX) &&
                    liveCenter.y in (rect.top + insetY)..(rect.bottom - insetY)
            }
            if (hit) {
                val d = hypot(liveCenter.x - rect.center.x, liveCenter.y - rect.center.y)
                if (d < bestDist) {
                    bestDist = d
                    bestTargetIdx = idx
                }
            }
        }

        if (bestTargetIdx >= 0 && bestTargetIdx != fromIdx) {
            val targetKey = keyOf(items[bestTargetIdx])
            val targetRect = slotBounds[targetKey]
            val moved = items.removeAt(fromIdx)
            items.add(bestTargetIdx, moved)
            if (targetRect != null) {
                val shift = targetRect.topLeft - currentRect.topLeft
                dragOffset -= shift
                // Optimistically reflect swapped layout rectangles until the next measure/layout pass
                slotBounds[key] = targetRect
                slotBounds[targetKey] = currentRect
            }
            lastSwapTimeMs = now
            haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun endDrag() {
        val wasDragging = draggingKey != null
        val committedKeys = if (wasDragging) items.map(keyOf) else null
        if (committedKeys != null) {
            pendingCommittedKeys = committedKeys
            pendingCommitTimeMs = clockMs()
        }
        draggingKey = null
        dragOffset = Offset.Zero
        if (committedKeys != null) {
            onCommitOrder(committedKeys)
        }
    }
}

@Composable
fun <T> rememberDragReorderController(
    sourceItems: List<T>,
    keyOf: (T) -> String,
    axis: DragAxis,
    onCommitOrder: (List<String>) -> Unit,
    enabled: Boolean = true
): DragReorderController<T> {
    val latestCommit by rememberUpdatedState(onCommitOrder)
    val latestKeyOf by rememberUpdatedState(keyOf)
    val controller = remember(axis) {
        DragReorderController<T>(
            keyOf = { latestKeyOf(it) },
            axis = axis,
            onCommitOrder = { latestCommit(it) }
        ).apply {
            if (enabled) syncFromSource(sourceItems)
        }
    }
    LaunchedEffect(sourceItems, enabled) {
        if (enabled) {
            controller.syncFromSource(sourceItems)
        }
    }
    return controller
}

/**
 * Container-level drag gesture handler for 2D grids (such as `Column` of `Row`s) where child tiles
 * move between parent `Row` composables on swap. Keeping `pointerInput` on the stable container
 * guarantees drag gestures stay uninterrupted across any number of row/column swaps.
 */
fun <T> Modifier.dragReorderContainer(
    enabled: Boolean,
    controller: DragReorderController<T>
): Modifier {
    if (!enabled) return this
    return composed {
        val haptic = LocalHapticFeedback.current
        var containerOriginInRoot by remember { mutableStateOf(Offset.Zero) }

        this
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    containerOriginInRoot = coords.positionInRoot()
                }
            }
            .pointerInput(controller) {
                detectDragGestures(
                    onDragStart = { localOffset ->
                        val hitKey = controller.findKeyAt(containerOriginInRoot + localOffset)
                        if (hitKey != null) {
                            controller.startDrag(hitKey, haptic)
                        }
                    },
                    onDragEnd = { controller.endDrag() },
                    onDragCancel = { controller.endDrag() },
                    onDrag = { change, dragAmount ->
                        if (controller.draggingKey != null) {
                            change.consume()
                            controller.onDragDelta(dragAmount, haptic)
                        }
                    }
                )
            }
    }
}

/**
 * Attaches drag-and-drop reorder behavior to an item when [enabled] is true.
 * Handles slot tracking, finger-locked drag translation, lifted scale, and z-index elevation.
 *
 * Set [handlePointerInput] to `false` when the parent grid container uses [dragReorderContainer].
 */
fun <T> Modifier.dragReorderItem(
    key: String,
    enabled: Boolean,
    controller: DragReorderController<T>,
    handlePointerInput: Boolean = true
): Modifier {
    if (!enabled) return this
    return composed {
        val haptic = LocalHapticFeedback.current
        val reduceMotion = rememberReduceMotion()
        val isDragging = controller.draggingKey == key
        val scaleState = animateFloatAsState(
            targetValue = if (isDragging) 1.06f else 1f,
            animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "drag_item_scale"
        )

        this
            .zIndex(if (isDragging) 12f else 0f)
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    controller.updateSlotBounds(key, coords.boundsInRoot())
                }
            }
            .then(
                if (handlePointerInput) {
                    Modifier.pointerInput(key, controller) {
                        detectDragGestures(
                            onDragStart = { controller.startDrag(key, haptic) },
                            onDragEnd = { controller.endDrag() },
                            onDragCancel = { controller.endDrag() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                controller.onDragDelta(key, dragAmount, haptic)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                val offset = if (isDragging) controller.dragOffset else Offset.Zero
                val s = scaleState.value
                translationX = offset.x
                translationY = offset.y
                scaleX = s
                scaleY = s
                alpha = if (isDragging) 0.94f else 1f
            }
    }
}

/**
 * Compact header pill shown above any group currently in Drag-and-Drop Reorder Mode.
 */
@Composable
fun ReorderModeBanner(
    visible: Boolean,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else (fadeIn() + expandVertically()),
        exit = if (reduceMotion) ExitTransition.None else (fadeOut() + shrinkVertically()),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Done", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
