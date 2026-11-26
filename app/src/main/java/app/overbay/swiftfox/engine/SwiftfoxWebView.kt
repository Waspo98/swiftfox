package app.overbay.swiftfox.engine

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

enum class DomTouchGateState {
    PENDING,
    ALLOWED,
    BLOCKED
}

class SwiftfoxWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    @Volatile
    var allowBackgroundPlayback: Boolean = false

    @Volatile
    var isMediaCurrentlyPlaying: Boolean = false

    var onScrollMetricsChanged: ((scrollY: Int, deltaY: Int, scrollRange: Int, scrollExtent: Int) -> Unit)? = null

    /**
     * Tracks whether the passive main-frame `touchGate` script is active on the current document
     * and whether the active touch gesture started cleanly at `scrollTop == 0` with no blocking
     * inner scroller, modal, `overscroll-behavior-y: none`, `touch-action`, or JS `preventDefault()`.
     */
    @Volatile
    var hasDomTouchGate: Boolean = false
        private set

    @Volatile
    var domTouchGateState: DomTouchGateState = DomTouchGateState.PENDING
        private set

    var onDomPullToRefreshBlocked: (() -> Unit)? = null

    fun onPageNavigationStarted() {
        hasDomTouchGate = false
        domTouchGateState = DomTouchGateState.PENDING
    }

    fun markDomTouchGateReady() {
        hasDomTouchGate = true
    }

    fun beginDomTouchGesture() {
        domTouchGateState = DomTouchGateState.PENDING
    }

    fun updateDomTouchGate(blocked: Boolean) {
        hasDomTouchGate = true
        domTouchGateState = if (blocked) DomTouchGateState.BLOCKED else DomTouchGateState.ALLOWED
        if (blocked) {
            onDomPullToRefreshBlocked?.invoke()
        }
    }

    val verticalScrollRangePx: Int
        get() = computeVerticalScrollRange()

    val verticalScrollExtentPx: Int
        get() = computeVerticalScrollExtent()

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE && allowBackgroundPlayback && isMediaCurrentlyPlaying) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val listener = onScrollMetricsChanged ?: return
        if (t != oldt) {
            listener(t, t - oldt, computeVerticalScrollRange(), computeVerticalScrollExtent())
        }
    }
}
