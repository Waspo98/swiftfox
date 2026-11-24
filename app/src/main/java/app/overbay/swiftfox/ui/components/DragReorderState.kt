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
import androidx.compose.material.icons.rounded.DragIndicator
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
 * Pure helper for moving an item inside a key list, unit-testable outside Compose.
 */
internal fun <T> moveListItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) return items
    val mutable = items.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable
}

/**
 * Coordinates drag-and-drop reordering for 1D rows, 1D lists, and 2D grids in explicit Reorder Mode.
 *
 * Because layout slots are tracked via `boundsInRoot()` BEFORE `graphicsLayer`, swapping two items
 * immediately compensates `dragOffset` by `(oldSlot.topLeft - newSlot.topLeft)` so the dragged tile
 * remains pixel-locked under the user's finger with zero jump or flicker.
 */
class DragReorderController<T>(
    private val keyOf: (T) -> String,
    private val axis: DragAxis,
    private val onCommitOrder: (List<String>) -> Unit
) {
    val items: SnapshotStateList<T> = mutableStateListOf()

    var draggingKey by mutableStateOf<String?>(null)
        private set

    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    private val slotBounds = LinkedHashMap<String, Rect>()
    private var lastSwapTimeMs = 0L

    fun syncFromSource(source: List<T>) {
        if (draggingKey != null) return
        val sourceKeys = source.map(keyOf)
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

    fun startDrag(key: String, haptic: HapticFeedback?) {
        draggingKey = key
        dragOffset = Offset.Zero
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
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

        val now = System.currentTimeMillis()
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
        draggingKey = null
        dragOffset = Offset.Zero
        if (wasDragging) {
            onCommitOrder(items.map(keyOf))
        }
    }
}

@Composable
fun <T> rememberDragReorderController(
    sourceItems: List<T>,
    keyOf: (T) -> String,
    axis: DragAxis,
    onCommitOrder: (List<String>) -> Unit
): DragReorderController<T> {
    val latestCommit by rememberUpdatedState(onCommitOrder)
    val latestKeyOf by rememberUpdatedState(keyOf)
    val controller = remember(axis) {
        DragReorderController<T>(
            keyOf = { latestKeyOf(it) },
            axis = axis,
            onCommitOrder = { latestCommit(it) }
        ).apply {
            syncFromSource(sourceItems)
        }
    }
    LaunchedEffect(sourceItems, controller.draggingKey) {
        controller.syncFromSource(sourceItems)
    }
    return controller
}

/**
 * Attaches drag-and-drop reorder behavior to an item when [enabled] is true.
 * Handles slot tracking, finger-locked drag translation, lifted scale, and z-index elevation.
 */
fun <T> Modifier.dragReorderItem(
    key: String,
    enabled: Boolean,
    controller: DragReorderController<T>
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val isDragging = enabled && controller.draggingKey == key
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.06f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "drag_item_scale"
    )
    val translation = if (isDragging) controller.dragOffset else Offset.Zero

    this
        .zIndex(if (isDragging) 12f else 0f)
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                controller.updateSlotBounds(key, coords.boundsInRoot())
            }
        }
        .then(
            if (enabled) {
                Modifier.pointerInput(key, enabled) {
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
            translationX = translation.x
            translationY = translation.y
            scaleX = scale
            scaleY = scale
            alpha = if (isDragging) 0.94f else 1f
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
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
