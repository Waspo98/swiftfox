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
    private val snapMidpoint: Float = 0.45f
) {
    var collapseProgress: Float = 0f
        private set

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
        val next = (old + rawDeltaY / travelDistancePx).coerceIn(0f, 1f)
        collapseProgress = next
        return (next - old) * travelDistancePx
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
        if (safeScrollY <= topRevealThresholdPx) {
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
     */
    fun resolveSnapTarget(
        velocityY: Float,
        minFlingVelocityPxPerSec: Float,
        scrollY: Int,
        scrollRange: Int,
        scrollExtent: Int,
        minScrollableOverflowPx: Int,
        enabled: Boolean,
        forceExpanded: Boolean
    ): Float {
        if (!enabled || forceExpanded) return 0f
        val scrollableOverflow = (scrollRange - scrollExtent).coerceAtLeast(0)
        if (scrollableOverflow < minScrollableOverflowPx) return 0f
        if (velocityY <= -minFlingVelocityPxPerSec) return 1f
        if (velocityY >= minFlingVelocityPxPerSec) return 0f
        if (scrollY.coerceAtLeast(0) <= topRevealThresholdPx) return 0f
        return if (collapseProgress >= snapMidpoint) 1f else 0f
    }

    fun setProgress(value: Float) {
        collapseProgress = value.coerceIn(0f, 1f)
    }
}
