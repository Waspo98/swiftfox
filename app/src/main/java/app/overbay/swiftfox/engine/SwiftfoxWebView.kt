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

    var onScrollTopChanged: ((Boolean) -> Unit)? = null

    private var lastAtTop: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE && allowBackgroundPlayback && isMediaCurrentlyPlaying) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val atTop = t <= 0
        if (atTop != lastAtTop) {
            lastAtTop = atTop
            onScrollTopChanged?.invoke(atTop)
        }
    }
}
