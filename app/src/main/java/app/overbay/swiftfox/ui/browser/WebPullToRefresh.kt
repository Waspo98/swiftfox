package app.overbay.swiftfox.ui.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln

/**
 * Pure state machine for browser pull-to-refresh driven by Chromium's native top-boundary
 * `WebView.overScrollBy` signal combined with passive DOM touch/scroll gating.
 *
 * Replaces `SwipeRefreshLayout` completely so `WebView` always receives 100% unmodified,
 * un-intercepted Chromium touch events. Inner CSS scroll containers (`overflow-y: auto/scroll`,
 * e.g. `fi.google.com`'s `.SSPGKf`), `overscroll-behavior-y: contain|none`, `touch-action`,
 * iframes, canvases, and JS `preventDefault()` gestures never get hijacked.
 */
internal class PullToRefreshController(
    private val touchSlopPx: Int,
    private val maxUnoverscrolledDownwardDragPx: Float,
    val triggerDistancePx: Float,
    val maxPullDistancePx: Float = triggerDistancePx * 1.65f,
    private val dragRate: Float = 0.55f,
    private val showDeadzonePx: Float = 6f
) {
    var touchActive: Boolean = false
        private set
    var eligible: Boolean = false
        private set
    var rawOverscrollPx: Float = 0f
        private set
    var pullDistancePx: Float = 0f
        private set

    val isThresholdReached: Boolean
        get() = pullDistancePx >= triggerDistancePx

    private var downRawY: Float = 0f
    private var maxDownwardDragPx: Float = 0f
    private var maxUpwardDragPx: Float = 0f
    private var hasReceivedTopOverscroll: Boolean = false

    fun onTouchDown(
        rawY: Float,
        initialScrollY: Int,
        initialCollapseProgress: Float,
        enabled: Boolean
    ) {
        touchActive = true
        rawOverscrollPx = 0f
        pullDistancePx = 0f
        downRawY = rawY
        maxDownwardDragPx = 0f
        maxUpwardDragPx = 0f
        hasReceivedTopOverscroll = false
        eligible = enabled && initialScrollY <= 0 && initialCollapseProgress <= 0.001f
    }

    /**
     * Tracks finger movement (`rawDy = prevRawY - rawY`; `> 0` when dragging finger up).
     * Returns `true` when an actively pulled indicator retracted during this move so callers
     * can keep toolbar collapse paused while retracting.
     */
    fun onTouchMove(
        rawY: Float,
        rawDy: Float,
        currentScrollY: Int,
        currentCollapseProgress: Float,
        domBlocked: Boolean
    ): Boolean {
        if (!touchActive) return false

        if (domBlocked) {
            blockByDom()
            return false
        }

        if (rawOverscrollPx > 0f) {
            if (rawDy > 0f) {
                rawOverscrollPx = (rawOverscrollPx - rawDy).coerceAtLeast(0f)
                pullDistancePx = computeDampedPullPx(rawOverscrollPx)
                return true
            }
            return false
        }

        if (!eligible) return false

        if (currentScrollY > 0 || currentCollapseProgress > 0.001f) {
            eligible = false
            return false
        }

        val netDownward = rawY - downRawY
        if (netDownward > maxDownwardDragPx) maxDownwardDragPx = netDownward
        val netUpward = downRawY - rawY
        if (netUpward > maxUpwardDragPx) maxUpwardDragPx = netUpward

        // Scrolling down into content disqualifies pull-to-refresh for this gesture so
        // reversing back up without lifting never triggers refresh mid-scroll.
        if (maxUpwardDragPx > touchSlopPx) {
            eligible = false
            return false
        }

        // Substantial downward drag without Chromium top overscroll means an inner scroller,
        // cross-origin iframe, or JS touch handler consumed the drag. Latch off so reaching
        // the top of that inner scroller mid-gesture never pops pull-to-refresh.
        if (!hasReceivedTopOverscroll && maxDownwardDragPx > maxUnoverscrolledDownwardDragPx) {
            eligible = false
            return false
        }

        return false
    }

    /**
     * Evaluates Chromium's `WebView.overScrollBy` top overscroll (`deltaY < 0` while `scrollY <= 0`).
     * Returns `true` if consumed into pull-to-refresh.
     */
    fun onTopOverscroll(
        deltaX: Int,
        deltaY: Int,
        scrollY: Int,
        collapseProgress: Float,
        horizontalAxisLocked: Boolean,
        domBlocked: Boolean
    ): Boolean {
        if (!touchActive || !eligible || horizontalAxisLocked) return false
        if (domBlocked) {
            blockByDom()
            return false
        }
        if (deltaY >= 0 || scrollY > 0 || collapseProgress > 0.001f) return false
        if (abs(deltaY) < abs(deltaX)) return false

        hasReceivedTopOverscroll = true
        rawOverscrollPx += abs(deltaY).toFloat()
        pullDistancePx = computeDampedPullPx(rawOverscrollPx)
        return true
    }

    /**
     * Called when the passive DOM touch/scroll observer reports `overscroll-behavior-y: contain|none`,
     * a scrolled inner container (`scrollTop > 0`), `touch-action` restriction, active inner DOM
     * scroll, or JS `preventDefault()`. Immediately cancels any in-flight pull for this gesture.
     */
    fun blockByDom(): Boolean {
        val wasPulling = pullDistancePx > 0f || rawOverscrollPx > 0f
        eligible = false
        rawOverscrollPx = 0f
        pullDistancePx = 0f
        return wasPulling
    }

    fun cancelPull(): Boolean {
        val wasPulling = pullDistancePx > 0f || rawOverscrollPx > 0f
        eligible = false
        rawOverscrollPx = 0f
        pullDistancePx = 0f
        return wasPulling
    }

    /**
     * Finishes touch tracking on `ACTION_UP` or `ACTION_CANCEL`.
     * Returns `true` iff the pull met [triggerDistancePx] on a non-cancelled pointer release.
     */
    fun onTouchEnd(isCancel: Boolean, domBlocked: Boolean): Boolean {
        val shouldTrigger = !isCancel && !domBlocked && eligible && pullDistancePx >= triggerDistancePx
        touchActive = false
        eligible = false
        rawOverscrollPx = 0f
        pullDistancePx = 0f
        maxDownwardDragPx = 0f
        maxUpwardDragPx = 0f
        hasReceivedTopOverscroll = false
        return shouldTrigger
    }

    fun reset() {
        touchActive = false
        eligible = false
        rawOverscrollPx = 0f
        pullDistancePx = 0f
        maxDownwardDragPx = 0f
        maxUpwardDragPx = 0f
        hasReceivedTopOverscroll = false
    }

    private fun computeDampedPullPx(rawPx: Float): Float {
        if (rawPx <= showDeadzonePx) return 0f
        val linear = (rawPx - showDeadzonePx) * dragRate
        if (linear <= triggerDistancePx) return linear
        val extraRange = (maxPullDistancePx - triggerDistancePx).coerceAtLeast(1f)
        val overflow = linear - triggerDistancePx
        // Smooth asymptotic rubber-band resistance beyond trigger threshold
        val dampedOverflow = extraRange * (1f - 1f / (1f + ln(1.0 + (overflow / extraRange).toDouble()).toFloat()))
        return (triggerDistancePx + dampedOverflow).coerceAtMost(maxPullDistancePx)
    }
}

class PullToRefreshVisualState(val triggerDistancePx: Float) {
    val activePullPx = mutableFloatStateOf(0f)
    var isDragging by mutableStateOf(false)
    var isRefreshingPulse by mutableStateOf(false)

    fun updatePull(pullPx: Float, dragging: Boolean) {
        if (dragging) {
            if (activePullPx.floatValue != pullPx) {
                activePullPx.floatValue = pullPx
            }
            if (!isDragging) {
                isDragging = true
            }
        } else {
            // Preserve last activePullPx so PullToRefreshIndicatorOverlay smoothly animates
            // back to 0f from the release point instead of snapping immediately.
            if (isDragging) {
                isDragging = false
            }
            if (pullPx <= 0f && !isRefreshingPulse && activePullPx.floatValue <= 0.5f) {
                activePullPx.floatValue = 0f
            }
        }
    }

    fun triggerRefreshPulse() {
        isDragging = false
        isRefreshingPulse = true
    }

    fun clear() {
        isDragging = false
        isRefreshingPulse = false
        activePullPx.floatValue = 0f
    }
}

@Composable
internal fun PullToRefreshIndicatorOverlay(
    visualState: PullToRefreshVisualState,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { 42.dp.toPx() }
    val restOffsetPx = with(density) { 14.dp.toPx() }

    val animatedPullPx = remember { Animatable(0f) }

    LaunchedEffect(visualState.isDragging, visualState.isRefreshingPulse, reduceMotion) {
        when {
            visualState.isDragging -> {
                animatedPullPx.stop()
            }
            visualState.isRefreshingPulse -> {
                val holdPx = visualState.triggerDistancePx * 0.78f
                if (reduceMotion) {
                    animatedPullPx.snapTo(holdPx)
                } else {
                    animatedPullPx.snapTo(visualState.activePullPx.floatValue.coerceAtLeast(holdPx))
                    animatedPullPx.animateTo(
                        targetValue = holdPx,
                        animationSpec = tween(durationMillis = 120, easing = EmphasizedDecelerateEasing)
                    )
                }
                delay(280)
                if (reduceMotion) {
                    animatedPullPx.snapTo(0f)
                } else {
                    animatedPullPx.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
                    )
                }
                visualState.activePullPx.floatValue = 0f
                visualState.isRefreshingPulse = false
            }
            else -> {
                val startPx = visualState.activePullPx.floatValue
                if (startPx > 0.5f || animatedPullPx.value > 0.5f) {
                    if (reduceMotion) {
                        animatedPullPx.snapTo(0f)
                    } else {
                        animatedPullPx.snapTo(startPx.coerceAtLeast(animatedPullPx.value))
                        animatedPullPx.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 150, easing = EmphasizedDecelerateEasing)
                        )
                    }
                    visualState.activePullPx.floatValue = 0f
                }
            }
        }
    }

    val isVisible by remember(visualState) {
        derivedStateOf {
            visualState.isRefreshingPulse ||
                (visualState.isDragging && visualState.activePullPx.floatValue > 1f) ||
                animatedPullPx.value > 1f
        }
    }
    val thresholdReached by remember(visualState) {
        derivedStateOf {
            visualState.isDragging &&
                visualState.activePullPx.floatValue >= visualState.triggerDistancePx
        }
    }

    LaunchedEffect(thresholdReached) {
        if (thresholdReached) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    if (!isVisible) return

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (thresholdReached || visualState.isRefreshingPulse) {
        accentColor.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Surface(
        shape = CircleShape,
        color = surfaceColor,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .size(42.dp)
            .graphicsLayer {
                val currentPull = if (visualState.isDragging) {
                    visualState.activePullPx.floatValue
                } else {
                    animatedPullPx.value
                }
                val fraction = (currentPull / visualState.triggerDistancePx.coerceAtLeast(1f))
                    .coerceIn(0f, 1.35f)
                translationY = -indicatorSizePx + currentPull + restOffsetPx * fraction.coerceAtMost(1f)
                alpha = (fraction * 1.5f).coerceIn(0f, 1f)
                val s = (0.72f + 0.28f * fraction.coerceAtMost(1f)).coerceIn(0.72f, 1.04f)
                scaleX = s
                scaleY = s
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (visualState.isRefreshingPulse) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    color = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Pull to refresh",
                    tint = if (thresholdReached) accentColor else mutedColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            if (!reduceMotion) {
                                val currentPull = if (visualState.isDragging) {
                                    visualState.activePullPx.floatValue
                                } else {
                                    animatedPullPx.value
                                }
                                val fraction = (currentPull / visualState.triggerDistancePx.coerceAtLeast(1f))
                                    .coerceIn(0f, 1.5f)
                                rotationZ = fraction * 270f
                            }
                        }
                )
            }
        }
    }
}
