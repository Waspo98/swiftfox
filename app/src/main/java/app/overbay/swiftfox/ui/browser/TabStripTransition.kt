package app.overbay.swiftfox.ui.browser

import androidx.compose.animation.core.CubicBezierEasing

internal val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
internal val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Resolves the horizontal slide direction for switching between two tabs in the active strip:
 * `+1` slides the new tab in from the right (next tab / newly created tab),
 * `-1` slides the previous tab in from the left,
 * `0` disables directional sliding (Space change, fullscreen overlay return, or reduced motion).
 */
internal fun resolveTabSlideDirection(
    previousTabId: String?,
    nextTabId: String?,
    previousVisibleIds: List<String>,
    nextVisibleIds: List<String>,
    previousSpaceKey: String,
    nextSpaceKey: String,
    wasFullscreenOverlayOpen: Boolean,
    reduceMotion: Boolean
): Int {
    if (
        reduceMotion ||
        previousTabId == null ||
        nextTabId == null ||
        previousTabId == nextTabId ||
        wasFullscreenOverlayOpen ||
        previousSpaceKey != nextSpaceKey
    ) {
        return 0
    }

    val newIdx = nextVisibleIds.indexOf(nextTabId)
    if (newIdx < 0) return 0

    val oldIdxInNext = nextVisibleIds.indexOf(previousTabId)
    if (oldIdxInNext >= 0) {
        return if (newIdx > oldIdxInNext) 1 else -1
    }

    val prevIdx = previousVisibleIds.indexOf(previousTabId)
    if (prevIdx < 0) return 0
    return if (newIdx < prevIdx) -1 else 1
}
