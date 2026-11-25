package app.overbay.swiftfox.engine

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

class SwiftfoxWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    @Volatile
    var allowBackgroundPlayback: Boolean = true

    @Volatile
    var isMediaCurrentlyPlaying: Boolean = false

    var onScrollMetricsChanged: ((scrollY: Int, deltaY: Int, scrollRange: Int, scrollExtent: Int) -> Unit)? = null

    /**
     * Receives native Chromium top-boundary overscroll deltas (`deltaY < 0` while `scrollY <= 0`)
     * from `AwContents.didOverscroll` -> `AwScrollOffsetManager.overScrollBy`. Return `true` when
     * consumed by pull-to-refresh so the view doesn't double-overscroll vertically.
     */
    var onTopOverscroll: ((deltaX: Int, deltaY: Int, scrollY: Int, isTouchEvent: Boolean) -> Boolean)? = null

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

    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        val consumedTopOverscroll = if (deltaY < 0 && scrollY <= 0) {
            onTopOverscroll?.invoke(deltaX, deltaY, scrollY, isTouchEvent) == true
        } else {
            false
        }
        return super.overScrollBy(
            deltaX,
            if (consumedTopOverscroll) 0 else deltaY,
            scrollX,
            scrollY,
            scrollRangeX,
            scrollRangeY,
            maxOverScrollX,
            maxOverScrollY,
            isTouchEvent
        )
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val listener = onScrollMetricsChanged ?: return
        if (t != oldt) {
            listener(t, t - oldt, computeVerticalScrollRange(), computeVerticalScrollExtent())
        }
    }
}
