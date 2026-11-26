package app.overbay.swiftfox.ui.browser

/**
 * Pure 1:1 scroll-linked progress controller for browser toolbar minimizing.
 *
 * Tracks continuous [collapseProgress] (`0f` = fully expanded, `1f` = fully hidden):
 * - During active finger drags, [onDragDelta] converts raw screen-space vertical movement
 *   1:1 against [travelDistancePx] and reports the exact consumed pixel shift so the host
 *   WebView can translate synchronously without double-scrolling or layout jitter.
 * - On finger release, [resolveSnapTarget] snaps smoothly to `0f` or `1f` using fling velocity
 *   and midpoint hysteresis so toolbars never linger half-hidden.
 * - During momentum flings without finger contact, [onFlingScroll] continues advancing
 *   downward collapse 1:1 and reveals bars when coasting back to the top of the document.
 * - Short / `100vh` non-scrollable pages never collapse.
 */
class ToolbarScrollCollapseController(
    private val topRevealThresholdPx: Int,
    private val snapMidpoint: Float = 0.45f,
    private val reversalDeadzonePx: Float = 0f,
    private val reversalConfirmDistancePx: Float = 0f,
    private val nearCollapsedRevealMultiplier: Float = 2.5f,
    private val nearCollapsedThreshold: Float = 0.85f
) {
    var collapseProgress: Float = 0f
        private set

    private var gestureDeltaY: Float = 0f
    private var downPeakDeltaY: Float? = null
    private var upTroughDeltaY: Float? = null

    /** Resets per-gesture directional tracking at touch start (`ACTION_DOWN`). */
    fun onGestureStart() {
        gestureDeltaY = 0f
        downPeakDeltaY = null
        upTroughDeltaY = null
    }

    /**
     * Applies a raw screen-space vertical finger delta (`rawDeltaY > 0` when dragging finger up
     * to scroll down the page; `< 0` when dragging finger down to scroll up).
     *
     * Returns the exact signed pixel distance consumed into toolbar movement.
     */
    fun onDragDelta(
        rawDeltaY: Float,
        travelDistancePx: Float,
        scrollRange: Int,
        scrollExtent: Int,
        minScrollableOverflowPx: Int,
        enabled: Boolean,
        forceExpanded: Boolean
    ): Float {
        if (!enabled || forceExpanded || travelDistancePx <= 0f) {
            collapseProgress = 0f
            return 0f
        }

        val scrollableOverflow = (scrollRange - scrollExtent).coerceAtLeast(0)
        if (scrollableOverflow < minScrollableOverflowPx) {
            if (collapseProgress > 0f) {
                collapseProgress = 0f
            }
            return 0f
        }

        if (rawDeltaY == 0f) return 0f

        val old = collapseProgress
        val oldGestureDeltaY = gestureDeltaY
        val newGestureDeltaY = oldGestureDeltaY + rawDeltaY
        gestureDeltaY = newGestureDeltaY
        val effectiveDeltaY = applyReversalDeadzone(
            rawDeltaY = rawDeltaY,
            oldGestureDeltaY = oldGestureDeltaY,
            newGestureDeltaY = newGestureDeltaY
        )

        val next = if (effectiveDeltaY == 0f) {
            old
        } else {
            (old + effectiveDeltaY / travelDistancePx).coerceIn(0f, 1f).also {
                collapseProgress = it
            }
        }
        updateDirectionalExtrema(
            rawDeltaY = rawDeltaY,
            oldProgress = old,
            newProgress = next,
            newGestureDeltaY = newGestureDeltaY
        )
        return (next - old) * travelDistancePx
    }

    private fun updateDirectionalExtrema(
        rawDeltaY: Float,
        oldProgress: Float,
        newProgress: Float,
        newGestureDeltaY: Float
    ) {
        if (rawDeltaY > 0f) {
            if (oldProgress < FULL_COLLAPSE_EPSILON && newProgress >= FULL_COLLAPSE_EPSILON) {
                // Re-anchor downward peak upon returning to fully collapsed so subsequent lift-off
                // wobbles get a fresh deadzone & confirmation window.
                downPeakDeltaY = newGestureDeltaY
                upTroughDeltaY = null
                return
            }
            val currentTrough = upTroughDeltaY
            if (currentTrough == null) {
                if (newProgress >= FULL_COLLAPSE_EPSILON || newGestureDeltaY > 0f) {
                    downPeakDeltaY = maxOf(downPeakDeltaY ?: newGestureDeltaY, newGestureDeltaY)
                }
                return
            }
            val confirmThreshold = if (newProgress <= EXPANDED_EPSILON) {
                maxOf(reversalConfirmDistancePx, reversalDeadzonePx)
            } else {
                reversalConfirmDistancePx
            }
            if (newProgress >= FULL_COLLAPSE_EPSILON ||
                (confirmThreshold > 0f && (newGestureDeltaY - currentTrough) >= confirmThreshold)
            ) {
                // Confirmed wave reversal back into downward scrolling: start a fresh peak.
                downPeakDeltaY = newGestureDeltaY
                upTroughDeltaY = null
            }
        } else if (rawDeltaY < 0f) {
            if (oldProgress > EXPANDED_EPSILON && newProgress <= EXPANDED_EPSILON) {
                // Re-anchor upward trough upon returning to fully expanded so subsequent upward
                // lift-off wobbles get a fresh deadzone & confirmation window.
                upTroughDeltaY = newGestureDeltaY
                downPeakDeltaY = null
                return
            }
            val currentPeak = downPeakDeltaY
            if (currentPeak == null) {
                if (newProgress <= EXPANDED_EPSILON || newGestureDeltaY < 0f) {
                    upTroughDeltaY = minOf(upTroughDeltaY ?: newGestureDeltaY, newGestureDeltaY)
                }
                return
            }
            val confirmThreshold = if (newProgress >= FULL_COLLAPSE_EPSILON) {
                maxOf(reversalConfirmDistancePx, reversalDeadzonePx)
            } else {
                reversalConfirmDistancePx
            }
            if (newProgress <= EXPANDED_EPSILON ||
                (confirmThreshold > 0f && (currentPeak - newGestureDeltaY) >= confirmThreshold)
            ) {
                // Confirmed wave reversal back into upward scrolling: start a fresh trough.
                upTroughDeltaY = newGestureDeltaY
                downPeakDeltaY = null
            }
        }
    }

    private fun applyReversalDeadzone(
        rawDeltaY: Float,
        oldGestureDeltaY: Float,
        newGestureDeltaY: Float
    ): Float {
        if (reversalDeadzonePx <= 0f) return rawDeltaY
        // Reversing downward (revealing) from fully collapsed after scrolling down in this gesture.
        val peak = downPeakDeltaY
        if (rawDeltaY < 0f && collapseProgress >= FULL_COLLAPSE_EPSILON && peak != null) {
            val prevExcess = ((peak - oldGestureDeltaY) - reversalDeadzonePx).coerceAtLeast(0f)
            val newExcess = ((peak - newGestureDeltaY) - reversalDeadzonePx).coerceAtLeast(0f)
            return -(newExcess - prevExcess)
        }
        // Reversing upward (collapsing) from fully expanded after scrolling up in this gesture.
        val trough = upTroughDeltaY
        if (rawDeltaY > 0f && collapseProgress <= EXPANDED_EPSILON && trough != null) {
            val prevExcess = ((oldGestureDeltaY - trough) - reversalDeadzonePx).coerceAtLeast(0f)
            val newExcess = ((newGestureDeltaY - trough) - reversalDeadzonePx).coerceAtLeast(0f)
            return newExcess - prevExcess
        }
        return rawDeltaY
    }

    /**
     * Suppresses weak or lift-off rebound velocities that contradict the current gesture's
     * dominant scroll direction without meaningful reverse finger travel.
     */
    fun sanitizeSnapVelocity(
        velocityY: Float,
        minFlingVelocityPxPerSec: Float
    ): Float {
        if (reversalConfirmDistancePx <= 0f) return velocityY
        val peak = downPeakDeltaY
        if (velocityY >= minFlingVelocityPxPerSec && peak != null) {
            val downwardReversalFromPeak = (peak - gestureDeltaY).coerceAtLeast(0f)
            if (downwardReversalFromPeak < reversalConfirmDistancePx) {
                return 0f
            }
        }
        val trough = upTroughDeltaY
        if (velocityY <= -minFlingVelocityPxPerSec && trough != null) {
            val upwardReversalFromPeak = (gestureDeltaY - trough).coerceAtLeast(0f)
            if (upwardReversalFromPeak < reversalConfirmDistancePx) {
                return 0f
            }
        }
        return velocityY
    }

    /**
     * Handles momentum scroll callbacks from WebView while no finger is actively dragging.
     *
     * Returns a target snap progress (`0f` or `1f`) when an animated reveal/hide should start,
     * or `null` when [collapseProgress] was updated inline (or left unchanged).
     */
    fun onFlingScroll(
        scrollY: Int,
        deltaY: Int,
        travelDistancePx: Float,
        scrollRange: Int,
        scrollExtent: Int,
        minScrollableOverflowPx: Int,
        enabled: Boolean,
        forceExpanded: Boolean
    ): Float? {
        if (!enabled || forceExpanded || travelDistancePx <= 0f) {
            return if (collapseProgress > 0f) 0f else null
        }

        val scrollableOverflow = (scrollRange - scrollExtent).coerceAtLeast(0)
        if (scrollableOverflow < minScrollableOverflowPx) {
            return if (collapseProgress > 0f) 0f else null
        }

        val safeScrollY = scrollY.coerceAtLeast(0)
        if (deltaY < 0 && safeScrollY <= topRevealThresholdPx) {
            return if (collapseProgress > 0f) 0f else null
        }

        if (deltaY > 0 && collapseProgress < 1f) {
            collapseProgress = (collapseProgress + deltaY / travelDistancePx).coerceIn(0f, 1f)
        }
        return null
    }

    /**
     * Resolves the settled snap target (`0f` = expanded, `1f` = hidden) when the user lifts
     * their finger (`velocityY < 0` for an upward flick / scrolling down; `> 0` for downward flick).
     * Short / `100vh` non-scrollable pages always resolve to `0f` so toolbars never hide off-screen.
     *
     * When [webViewTranslatesWithTopBar] is `true` (top or split toolbar), dragging up from the top
     * of a page shifts `WebView.translationY` 1:1 while `scrollY` stays near `0`, so a low-velocity
     * release past [snapMidpoint] finishes collapsing instead of springing back open. When `false`
     * (bottom-only or side-rail placement), `WebView` scrolls directly to `scrollY == 0`, so
     * releasing right at the top of the page reveals the toolbar cleanly.
     */
    fun resolveSnapTarget(
        velocityY: Float,
        minFlingVelocityPxPerSec: Float,
        scrollY: Int,
        scrollRange: Int,
        scrollExtent: Int,
        minScrollableOverflowPx: Int,
        enabled: Boolean,
        forceExpanded: Boolean,
        webViewTranslatesWithTopBar: Boolean = true
    ): Float {
        if (!enabled || forceExpanded) return 0f
        val scrollableOverflow = (scrollRange - scrollExtent).coerceAtLeast(0)
        if (scrollableOverflow < minScrollableOverflowPx) return 0f
        val sanitizedVelocity = sanitizeSnapVelocity(velocityY, minFlingVelocityPxPerSec)
        if (sanitizedVelocity <= -minFlingVelocityPxPerSec) return 1f
        val revealMinVelocity = if (collapseProgress >= nearCollapsedThreshold) {
            minFlingVelocityPxPerSec * nearCollapsedRevealMultiplier
        } else {
            minFlingVelocityPxPerSec
        }
        if (sanitizedVelocity >= revealMinVelocity) return 0f
        if (!webViewTranslatesWithTopBar && scrollY.coerceAtLeast(0) <= topRevealThresholdPx) {
            return 0f
        }
        return if (collapseProgress >= snapMidpoint) 1f else 0f
    }

    /**
     * Resolves the settled snap target after a coasting momentum fling comes to rest while
     * toolbars are partially collapsed.
     */
    fun resolveSettledSnapTarget(
        enabled: Boolean,
        forceExpanded: Boolean
    ): Float {
        if (!enabled || forceExpanded) return 0f
        return if (collapseProgress >= snapMidpoint) 1f else 0f
    }

    fun setProgress(value: Float) {
        collapseProgress = value.coerceIn(0f, 1f)
    }
}

internal const val EXPANDED_EPSILON = 0.001f
internal const val FULL_COLLAPSE_EPSILON = 0.999f
