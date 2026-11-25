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
import androidx.compose.runtime.mutableIntStateOf
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
import app.overbay.swiftfox.engine.DomTouchGateState
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.ln

/**
 * Pure state machine for top-of-page (`scrollTop == 0`) browser pull-to-refresh driven directly
 * from `WebViewContainer`'s vertical finger drag deltas and gated by passive main-frame DOM
 * `touchGate` inspection.
 *
 * Keeps `WebView` completely free of `SwipeRefreshLayout` touch interception so inner CSS scroll
 * containers (e.g. `fi.google.com`'s `.SSPGKf`), scrolled elements (`scrollTop > 1`), nested
 * `overscroll-behavior-y: contain` modals, `overscroll-behavior-y: none` apps, restrictive
 * `touch-action` regions, cross-origin iframes, and JS `preventDefault()` handlers scroll
 * cleanly while top-of-page pull-to-refresh works reliably at `scrollTop == 0`.
 */
internal class PullToRefreshController(
    val triggerDistancePx: Float,
    val maxPullDistancePx: Float = triggerDistancePx * 1.65f,
    private val dragRate: Float = 0.55f,
    private val showDeadzonePx: Float = 12f
) {
    var touchActive: Boolean = false
        private set
    var eligible: Boolean = false
        private set
    var rawPullPx: Float = 0f
        private set
    var pullDistancePx: Float = 0f
        private set

    val isThresholdReached: Boolean
        get() = pullDistancePx >= triggerDistancePx

    fun onTouchDown(
        initialScrollY: Int,
        canWebViewScrollUp: Boolean,
        initialCollapseProgress: Float,
        enabled: Boolean
    ) {
        touchActive = true
        rawPullPx = 0f
        pullDistancePx = 0f
        eligible = enabled &&
            initialScrollY <= 0 &&
            !canWebViewScrollUp &&
            initialCollapseProgress <= 0.001f
    }

    /**
     * Evaluates a vertical-locked finger drag delta (`rawDy = lastRawY - rawY`; `< 0` when
     * dragging finger downward to pull-to-refresh, `> 0` when dragging finger upward to scroll).
     *
     * Returns `true` while pull-to-refresh actively holds/retracts the indicator so toolbar
     * collapse stays paused during the pull.
     */
    fun onVerticalDragDelta(
        rawDy: Float,
        currentScrollY: Int,
        canWebViewScrollUp: Boolean,
        currentCollapseProgress: Float,
        hasDomTouchGate: Boolean,
        domGateState: DomTouchGateState
    ): Boolean {
        if (!touchActive) return false

        if (domGateState == DomTouchGateState.BLOCKED) {
            blockByDom()
            return false
        }

        // Actively pulling down: allow smooth downward extension and upward retraction.
        if (rawPullPx > 0f) {
            if (currentScrollY > 0 || canWebViewScrollUp || currentCollapseProgress > 0.001f) {
                cancelPull()
                return false
            }
            if (rawDy < 0f) {
                rawPullPx += -rawDy
                pullDistancePx = computeDampedPullPx(rawPullPx)
                return true
            }
            if (rawDy > 0f) {
                rawPullPx = (rawPullPx - rawDy).coerceAtLeast(0f)
                pullDistancePx = computeDampedPullPx(rawPullPx)
                if (rawPullPx <= 0f) {
                    // User retracted all the way back to 0 and pushed upward into content:
                    // disqualify for the rest of this gesture.
                    eligible = false
                }
                return true
            }
            return true
        }

        if (!eligible) return false

        if (currentScrollY > 0 || canWebViewScrollUp || currentCollapseProgress > 0.001f) {
            eligible = false
            return false
        }

        // First vertical movement is upward (scrolling down into the page) -> disqualify for this gesture.
        if (rawDy > 0f) {
            eligible = false
            return false
        }

        if (rawDy >= 0f) return false

        // When the passive DOM touchGate is installed on the page, require an explicit ALLOWED
        // reply (scrollTop == 0 across root & all ancestors). Cross-origin iframes stay PENDING
        // because main-frame touchstart never fires, keeping them safely protected. When JS is
        // disabled (hasDomTouchGate == false), fall back to native scrollY == 0 gating.
        if (hasDomTouchGate && domGateState != DomTouchGateState.ALLOWED) {
            return false
        }

        rawPullPx += -rawDy
        pullDistancePx = computeDampedPullPx(rawPullPx)
        return true
    }

    fun blockByDom(): Boolean = cancelPull()

    fun cancelPull(): Boolean {
        val wasPulling = pullDistancePx > 0f || rawPullPx > 0f
        eligible = false
        rawPullPx = 0f
        pullDistancePx = 0f
        return wasPulling
    }

    /**
     * Finishes touch tracking on `ACTION_UP` or `ACTION_CANCEL`.
     * Returns `true` iff the pull met [triggerDistancePx] on a non-cancelled release.
     */
    fun onTouchEnd(
        isCancel: Boolean,
        hasDomTouchGate: Boolean,
        domGateState: DomTouchGateState
    ): Boolean {
        val domPermits = domGateState != DomTouchGateState.BLOCKED &&
            (!hasDomTouchGate || domGateState == DomTouchGateState.ALLOWED)
        val shouldTrigger = !isCancel && domPermits && eligible && pullDistancePx >= triggerDistancePx
        touchActive = false
        eligible = false
        rawPullPx = 0f
        pullDistancePx = 0f
        return shouldTrigger
    }

    fun reset() {
        touchActive = false
        eligible = false
        rawPullPx = 0f
        pullDistancePx = 0f
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
    var clearGeneration by mutableIntStateOf(0)
        private set

    fun updatePull(pullPx: Float, dragging: Boolean) {
        if (dragging) {
            if (isRefreshingPulse) {
                isRefreshingPulse = false
            }
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
        clearGeneration++
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

    LaunchedEffect(
        visualState.isDragging,
        visualState.isRefreshingPulse,
        visualState.clearGeneration,
        reduceMotion
    ) {
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
                if (startPx <= 0.5f) {
                    if (animatedPullPx.value != 0f) {
                        animatedPullPx.snapTo(0f)
                    }
                } else {
                    if (reduceMotion) {
                        animatedPullPx.snapTo(0f)
                    } else {
                        animatedPullPx.snapTo(startPx)
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
