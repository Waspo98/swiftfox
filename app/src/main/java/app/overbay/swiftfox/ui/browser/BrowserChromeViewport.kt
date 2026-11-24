package app.overbay.swiftfox.ui.browser

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.overbay.swiftfox.engine.SwiftfoxWebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class ChromeBarHeights {
    var topPx by mutableIntStateOf(0)
    var bottomPx by mutableIntStateOf(0)

    fun update(top: Int, bottom: Int) {
        if (topPx != top) topPx = top
        if (bottomPx != bottom) bottomPx = bottom
    }
}

internal fun Modifier.insetByChromeHeights(chromeHeights: ChromeBarHeights): Modifier =
    layout { measurable, constraints ->
        val top = chromeHeights.topPx.coerceAtLeast(0)
        val bottom = chromeHeights.bottomPx.coerceAtLeast(0)
        val availableH = (constraints.maxHeight - top - bottom).coerceAtLeast(0)
        val placeable = measurable.measure(
            constraints.copy(minHeight = availableH, maxHeight = availableH)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, top)
        }
    }

@Composable
internal fun BrowserChromeViewportLayout(
    modifier: Modifier,
    collapseProgressProvider: () -> Float,
    onChromeHeightsMeasured: (topBarPx: Int, bottomBarPx: Int) -> Unit,
    statusBarStrip: @Composable () -> Unit,
    findBar: @Composable () -> Unit,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    hudOverlay: @Composable () -> Unit,
    foldedDrawer: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.layoutId("content")) { content() }
            Box(modifier = Modifier.layoutId("hud")) { hudOverlay() }
            Box(modifier = Modifier.layoutId("topBar")) { topBar() }
            Box(modifier = Modifier.layoutId("statusBar")) { statusBarStrip() }
            Box(modifier = Modifier.layoutId("findBar")) { findBar() }
            Box(modifier = Modifier.layoutId("drawer")) { foldedDrawer() }
            Box(modifier = Modifier.layoutId("bottomBar")) { bottomBar() }
        }
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val looseW = constraints.copy(minWidth = maxW, maxWidth = maxW, minHeight = 0, maxHeight = maxH)

        val statusPlaceable = measurables.first { it.layoutId == "statusBar" }.measure(looseW)
        val findPlaceable = measurables.first { it.layoutId == "findBar" }.measure(looseW)
        val topBarPlaceable = measurables.first { it.layoutId == "topBar" }.measure(looseW)
        val bottomBarPlaceable = measurables.first { it.layoutId == "bottomBar" }.measure(looseW)

        val statusH = statusPlaceable.height
        val findH = findPlaceable.height
        val topBarH = topBarPlaceable.height
        val bottomBarH = bottomBarPlaceable.height
        val viewportTop = (statusH + findH).coerceAtMost(maxH)
        val viewportH = (maxH - viewportTop).coerceAtLeast(0)

        // Publish chrome heights BEFORE measuring content so StartPage & WebViewContainer align on frame 1
        onChromeHeightsMeasured(topBarH, bottomBarH)

        val contentPlaceable = measurables.first { it.layoutId == "content" }.measure(
            constraints.copy(minWidth = maxW, maxWidth = maxW, minHeight = viewportH, maxHeight = viewportH)
        )
        val hudH = (viewportH - topBarH - bottomBarH).coerceAtLeast(0)
        val hudPlaceable = measurables.first { it.layoutId == "hud" }.measure(
            constraints.copy(minWidth = maxW, maxWidth = maxW, minHeight = hudH, maxHeight = hudH)
        )
        val drawerH = (maxH - bottomBarH).coerceAtLeast(0)
        val drawerPlaceable = measurables.first { it.layoutId == "drawer" }.measure(
            constraints.copy(minWidth = maxW, maxWidth = maxW, minHeight = drawerH, maxHeight = drawerH)
        )

        layout(maxW, maxH) {
            // Z-order: content < hud < topBar < statusBar & findBar < foldedDrawer < bottomBar
            contentPlaceable.placeRelative(0, viewportTop)
            hudPlaceable.placeRelative(0, viewportTop + topBarH)
            if (topBarH > 0) {
                topBarPlaceable.placeRelativeWithLayer(0, viewportTop) {
                    val p = collapseProgressProvider().coerceIn(0f, 1f)
                    translationY = topBarH * (1f - p) - topBarH
                }
            }
            if (statusH > 0) {
                statusPlaceable.placeRelative(0, 0)
            }
            if (findH > 0) {
                findPlaceable.placeRelative(0, statusH)
            }
            drawerPlaceable.placeRelative(0, 0)
            if (bottomBarH > 0) {
                bottomBarPlaceable.placeRelativeWithLayer(0, maxH - bottomBarH) {
                    val p = collapseProgressProvider().coerceIn(0f, 1f)
                    translationY = bottomBarH * p
                }
            }
        }
    }
}

@Composable
internal fun ExitingTabThumbnailFallback(
    tabId: String,
    thumbnailsFlow: StateFlow<TabThumbnailsSnapshot>,
    modifier: Modifier = Modifier
) {
    val thumbnails by thumbnailsFlow.collectAsStateWithLifecycle()
    val fallbackBitmap = remember(thumbnails.bitmaps[tabId]) {
        thumbnails.bitmaps[tabId]?.asImageBitmap()
    }
    if (fallbackBitmap != null) {
        Image(
            bitmap = fallbackBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    }
}

internal interface FrameCallbackScheduler {
    fun postOnNextFrame(action: Runnable)
    fun cancelFrameCallback(action: Runnable)
}

internal class ToolbarCollapseBridge(
    val controller: ToolbarScrollCollapseController,
    val sideRailTravelPx: Float,
    val minExtraOverflowPx: Int,
    val minScrollableFloorPx: Int,
    val refreshSpinnerDropPx: Int
) {
    val progressState = mutableFloatStateOf(0f)
    val chromeHeights = ChromeBarHeights()

    val renderedProgress: Float
        get() = progressState.floatValue

    val hasTopBar: Boolean
        get() = topBarHeightPx > 0

    var topBarHeightPx: Int = 0
    var bottomBarHeightPx: Int = 0
    var activeWebContainer: WebViewContainer? = null
    var activeSwipeLayout: SwipeRefreshLayout? = null
    var isUserDragging: Boolean = false
    var snapJob: Job? = null

    private var flingSettleJob: Job? = null
    private var pendingFlingProgress: Float? = null
    private var pendingFlingRunnable: Runnable? = null
    private var pendingFlingHost: FrameCallbackScheduler? = null
    private var pendingFlingIsSnap: Boolean = false

    fun effectiveTravelDistancePx(): Float = when {
        topBarHeightPx > 0 -> topBarHeightPx.toFloat()
        bottomBarHeightPx > 0 -> bottomBarHeightPx.toFloat()
        else -> sideRailTravelPx
    }

    fun minScrollableOverflowPx(): Int =
        (topBarHeightPx + bottomBarHeightPx + minExtraOverflowPx).coerceAtLeast(minScrollableFloorPx)

    fun attachActiveContainer(
        webContainer: WebViewContainer,
        swipeLayout: SwipeRefreshLayout
    ) {
        if (activeWebContainer !== webContainer) {
            cancelFlingWork()
        }
        activeWebContainer = webContainer
        activeSwipeLayout = swipeLayout
    }

    fun detachContainer(webContainer: WebViewContainer?) {
        if (webContainer == null) return
        if (pendingFlingHost === webContainer) {
            cancelFlingWork()
        }
        if (activeWebContainer === webContainer) {
            cancelFlingWork()
            activeWebContainer = null
            activeSwipeLayout = null
        }
    }

    fun cancelFlingWork() {
        flingSettleJob?.cancel()
        flingSettleJob = null
        val runnable = pendingFlingRunnable
        val host = pendingFlingHost
        pendingFlingRunnable = null
        pendingFlingHost = null
        pendingFlingIsSnap = false
        if (runnable != null) {
            host?.cancelFrameCallback(runnable)
        }
        if (pendingFlingProgress != null) {
            pendingFlingProgress = null
            controller.setProgress(progressState.floatValue)
        }
    }

    fun onFlingScrollMetrics(
        host: FrameCallbackScheduler,
        scrollY: Int,
        deltaY: Int,
        scrollRange: Int,
        scrollExtent: Int,
        enabled: Boolean,
        forceExpanded: Boolean,
        isEnabledProvider: () -> Boolean,
        isForceExpandedProvider: () -> Boolean,
        coroutineScope: CoroutineScope,
        onAnimateCollapseTo: (Float) -> Unit,
        isHostActive: () -> Boolean = { activeWebContainer === host },
        settleDelayMs: Long = 120L
    ) {
        if (isUserDragging || snapJob?.isActive == true) return
        val snapTarget = controller.onFlingScroll(
            scrollY = scrollY,
            deltaY = deltaY,
            travelDistancePx = effectiveTravelDistancePx(),
            scrollRange = scrollRange,
            scrollExtent = scrollExtent,
            minScrollableOverflowPx = minScrollableOverflowPx(),
            enabled = enabled,
            forceExpanded = forceExpanded
        )
        if (snapTarget != null) {
            cancelFlingWork()
            // Defer off computeScroll()/draw so Compose and WebView animate in lockstep.
            lateinit var runnable: Runnable
            runnable = Runnable {
                if (pendingFlingRunnable !== runnable) return@Runnable
                pendingFlingRunnable = null
                pendingFlingHost = null
                pendingFlingIsSnap = false
                if (isHostActive() && !isUserDragging && snapJob?.isActive != true) {
                    onAnimateCollapseTo(snapTarget)
                }
            }
            pendingFlingRunnable = runnable
            pendingFlingHost = host
            pendingFlingIsSnap = true
            host.postOnNextFrame(runnable)
            return
        }

        val next = controller.collapseProgress
        if (progressState.floatValue == next && pendingFlingProgress == null) return

        // If a snap runnable was queued earlier on this same frame, cancel it before scheduling
        // a progress runnable so the two runnable types never collide in one slot.
        if (pendingFlingIsSnap) {
            val prevRunnable = pendingFlingRunnable
            val prevHost = pendingFlingHost
            pendingFlingRunnable = null
            pendingFlingHost = null
            pendingFlingIsSnap = false
            if (prevRunnable != null) {
                prevHost?.cancelFrameCallback(prevRunnable)
            }
        }

        pendingFlingProgress = next
        if (pendingFlingRunnable == null) {
            lateinit var runnable: Runnable
            runnable = Runnable {
                if (pendingFlingRunnable !== runnable) return@Runnable
                pendingFlingRunnable = null
                pendingFlingHost = null
                pendingFlingIsSnap = false
                val pending = pendingFlingProgress ?: return@Runnable
                pendingFlingProgress = null
                if (!isHostActive() || isUserDragging || snapJob?.isActive == true) {
                    controller.setProgress(progressState.floatValue)
                    return@Runnable
                }
                applyProgress(
                    progress = pending,
                    expandedViewport = pending > 0.001f
                )
            }
            pendingFlingRunnable = runnable
            pendingFlingHost = host
            pendingFlingIsSnap = false
            host.postOnNextFrame(runnable)
        }

        flingSettleJob?.cancel()
        flingSettleJob = null
        if (next > 0.001f && next < 0.999f) {
            flingSettleJob = coroutineScope.launch {
                delay(settleDelayMs)
                flingSettleJob = null
                if (isHostActive() && !isUserDragging && snapJob?.isActive != true) {
                    val current = controller.collapseProgress
                    if (current > 0.001f && current < 0.999f) {
                        val settledTarget = controller.resolveSettledSnapTarget(
                            enabled = isEnabledProvider(),
                            forceExpanded = isForceExpandedProvider()
                        )
                        onAnimateCollapseTo(settledTarget)
                    }
                }
            }
        }
    }

    fun onChromeHeightsMeasured(topPx: Int, bottomPx: Int) {
        val changed = topBarHeightPx != topPx || bottomBarHeightPx != bottomPx
        topBarHeightPx = topPx
        bottomBarHeightPx = bottomPx
        chromeHeights.update(topPx, bottomPx)
        if (changed) {
            val container = activeWebContainer
            val swipe = activeSwipeLayout
            val applyViewMetrics = {
                val progress = renderedProgress
                container?.updateChromeMetrics(
                    topBarHeightPx = topBarHeightPx,
                    bottomBarHeightPx = bottomBarHeightPx,
                    collapseProgress = progress,
                    expandedViewport = progress > 0.001f || isUserDragging
                )
                swipe?.setProgressViewOffset(
                    false,
                    topBarHeightPx,
                    topBarHeightPx + refreshSpinnerDropPx
                )
            }
            if (container != null && container.isInLayout) {
                container.post { applyViewMetrics() }
            } else if (swipe != null && swipe.isInLayout) {
                swipe.post { applyViewMetrics() }
            } else {
                applyViewMetrics()
            }
        }
    }

    fun applyProgress(progress: Float, expandedViewport: Boolean) {
        val clamped = progress.coerceIn(0f, 1f)
        controller.setProgress(clamped)
        val changed = progressState.floatValue != clamped
        progressState.floatValue = clamped
        activeWebContainer?.updateChromeMetrics(
            topBarHeightPx = topBarHeightPx,
            bottomBarHeightPx = bottomBarHeightPx,
            collapseProgress = clamped,
            expandedViewport = expandedViewport
        )
        if (changed) {
            // Flush Compose layer observers synchronously so topBar/bottomBar RenderNodes update
            // on the exact same VSync frame as WebView.translationY (preventing 1-frame overlap).
            Snapshot.sendApplyNotifications()
        }
    }
}

internal enum class GestureAxis {
    UNDECIDED,
    VERTICAL,
    IGNORED
}

internal data class PointerUpOutcome(
    val shouldFinish: Boolean,
    val cancelled: Boolean
)

/**
 * Pure touch gesture state machine for [WebViewContainer] vertical scroll-collapse tracking.
 * Ensures multi-touch (`ACTION_POINTER_DOWN`) and horizontal drags (`GestureAxis.IGNORED`)
 * always cancel active tracking cleanly so `isUserDragging` and `VelocityTracker` never leak
 * and horizontal swipes or simple taps never fling toolbars hidden/open.
 */
internal class VerticalDragGestureState(private val touchSlop: Int) {
    var trackingActive: Boolean = false
        private set
    var axis: GestureAxis = GestureAxis.UNDECIDED
        private set
    var lastMoveShiftedWebView: Boolean = false

    private var downRawX: Float = 0f
    private var downRawY: Float = 0f
    private var prevRawY: Float = 0f
    private var lastRawY: Float = 0f

    sealed class MoveResult {
        data object Ignored : MoveResult()
        data object LockedHorizontalCancel : MoveResult()
        data class VerticalDelta(val rawDy: Float) : MoveResult()
    }

    fun onDown(rawX: Float, rawY: Float) {
        downRawX = rawX
        downRawY = rawY
        prevRawY = rawY
        lastRawY = rawY
        axis = GestureAxis.UNDECIDED
        lastMoveShiftedWebView = false
        trackingActive = true
    }

    fun onPointerDown(): Boolean {
        val shouldFinish = trackingActive
        axis = GestureAxis.IGNORED
        trackingActive = false
        lastMoveShiftedWebView = false
        return shouldFinish
    }

    fun onMove(rawX: Float, rawY: Float): MoveResult {
        if (!trackingActive || axis == GestureAxis.IGNORED) return MoveResult.Ignored
        val totalDx = rawX - downRawX
        val totalDy = rawY - downRawY
        if (axis == GestureAxis.UNDECIDED) {
            val absDx = abs(totalDx)
            val absDy = abs(totalDy)
            // Cross past Android touchSlop so Chromium already exited tap/long-press detection
            if (absDy > touchSlop + 2 && absDy > absDx) {
                axis = GestureAxis.VERTICAL
                lastRawY = if (totalDy < 0f) {
                    minOf(downRawY - touchSlop.toFloat(), prevRawY)
                } else {
                    maxOf(downRawY + touchSlop.toFloat(), prevRawY)
                }
            } else if (absDx > touchSlop && absDx >= absDy) {
                axis = GestureAxis.IGNORED
                trackingActive = false
                lastMoveShiftedWebView = false
                return MoveResult.LockedHorizontalCancel
            } else {
                prevRawY = rawY
            }
        }
        if (axis == GestureAxis.VERTICAL) {
            val rawDy = lastRawY - rawY // > 0 when dragging finger up (scrolling down)
            lastRawY = rawY
            prevRawY = rawY
            return if (rawDy != 0f) MoveResult.VerticalDelta(rawDy) else MoveResult.Ignored
        }
        return MoveResult.Ignored
    }

    fun shouldRecordVelocityOnMove(): Boolean = trackingActive && axis != GestureAxis.IGNORED

    fun shouldRecordTerminalVelocity(): Boolean = trackingActive && axis == GestureAxis.VERTICAL

    fun onUpOrCancel(isCancel: Boolean): PointerUpOutcome {
        if (!trackingActive) return PointerUpOutcome(shouldFinish = false, cancelled = true)
        val cancelled = isCancel || axis != GestureAxis.VERTICAL
        trackingActive = false
        axis = GestureAxis.UNDECIDED
        return PointerUpOutcome(shouldFinish = true, cancelled = cancelled)
    }

    fun reset() {
        trackingActive = false
        axis = GestureAxis.UNDECIDED
        lastMoveShiftedWebView = false
    }
}

internal class WebViewContainer(context: Context) : FrameLayout(context), FrameCallbackScheduler {
    var lastPrimaryArgb: Int? = null
    var lastSurfaceArgb: Int? = null

    override fun postOnNextFrame(action: Runnable) {
        postOnAnimation(action)
    }

    override fun cancelFrameCallback(action: Runnable) {
        removeCallbacks(action)
    }

    private var topBarHeightPx: Int = 0
    private var bottomBarHeightPx: Int = 0
    var currentCollapseProgress: Float = 0f
        private set
    var isViewportExpanded: Boolean = false
        private set

    var onGestureStart: (() -> Unit)? = null
    var onGestureDragDelta: ((rawDeltaY: Float, webView: SwiftfoxWebView) -> Float)? = null
    var onGestureEnd: ((velocityY: Float, webView: SwiftfoxWebView, handoffFling: Boolean) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val minFlingVelocityPx = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocityPx = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private val gestureState = VerticalDragGestureState(touchSlop)
    private var velocityTracker: VelocityTracker? = null

    fun clearGestureCallbacks() {
        onGestureStart = null
        onGestureDragDelta = null
        onGestureEnd = null
        gestureState.reset()
        recycleVelocityTracker()
    }

    fun updateChromeMetrics(
        topBarHeightPx: Int,
        bottomBarHeightPx: Int,
        collapseProgress: Float,
        expandedViewport: Boolean
    ) {
        val layoutChanged = this.topBarHeightPx != topBarHeightPx ||
            this.bottomBarHeightPx != bottomBarHeightPx ||
            this.isViewportExpanded != expandedViewport
        this.topBarHeightPx = topBarHeightPx
        this.bottomBarHeightPx = bottomBarHeightPx
        this.currentCollapseProgress = collapseProgress.coerceIn(0f, 1f)
        this.isViewportExpanded = expandedViewport

        syncChildTranslation()
        if (layoutChanged) {
            if (isInLayout) {
                post { requestLayout() }
            } else {
                requestLayout()
            }
        }
    }

    private fun syncChildTranslation() {
        val child = getChildAt(0) ?: return
        val targetTy = if (topBarHeightPx > 0) {
            topBarHeightPx * (1f - currentCollapseProgress)
        } else {
            0f
        }
        if (child.translationY != targetTy) {
            child.translationY = targetTy
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)

        val child = getChildAt(0) ?: return
        val childHeight = if (isViewportExpanded) {
            height
        } else {
            (height - topBarHeightPx - bottomBarHeightPx).coerceAtLeast(1)
        }
        child.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        syncChildTranslation()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val childWebView = getChildAt(0) as? SwiftfoxWebView
        val dragCallback = onGestureDragDelta
        if (childWebView == null || dragCallback == null) {
            gestureState.reset()
            recycleVelocityTracker()
            return super.dispatchTouchEvent(ev)
        }

        val action = ev.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain()
                addScreenVelocityEvent(ev)
                gestureState.onDown(ev.rawX, ev.rawY)
                onGestureStart?.invoke()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (gestureState.onPointerDown()) {
                    finishGesture(childWebView, cancelled = true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureState.shouldRecordVelocityOnMove()) {
                    addScreenVelocityEvent(ev)
                }
                when (val result = gestureState.onMove(ev.rawX, ev.rawY)) {
                    VerticalDragGestureState.MoveResult.Ignored -> Unit
                    VerticalDragGestureState.MoveResult.LockedHorizontalCancel -> {
                        finishGesture(childWebView, cancelled = true)
                    }
                    is VerticalDragGestureState.MoveResult.VerticalDelta -> {
                        val consumedPx = dragCallback(result.rawDy, childWebView)
                        // Sync child translationY synchronously BEFORE super.dispatchTouchEvent
                        // so Android transforms ev into childWebView with zero frame lag.
                        syncChildTranslation()
                        gestureState.lastMoveShiftedWebView =
                            topBarHeightPx > 0 && abs(consumedPx) > 0.5f
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureState.shouldRecordTerminalVelocity()) {
                    addScreenVelocityEvent(ev)
                }
            }
        }

        val handled = super.dispatchTouchEvent(ev)

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            val outcome = gestureState.onUpOrCancel(isCancel = (action == MotionEvent.ACTION_CANCEL))
            if (outcome.shouldFinish) {
                finishGesture(childWebView, cancelled = outcome.cancelled)
            }
        }
        return handled
    }

    private fun addScreenVelocityEvent(ev: MotionEvent) {
        val tracker = velocityTracker ?: return
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX, ev.rawY)
        tracker.addMovement(copy)
        copy.recycle()
    }

    private fun finishGesture(childWebView: SwiftfoxWebView, cancelled: Boolean) {
        val vy = if (!cancelled) {
            velocityTracker?.let { vt ->
                vt.computeCurrentVelocity(1000, maxFlingVelocityPx)
                vt.yVelocity
            } ?: 0f
        } else {
            0f
        }
        val shouldHandoffFling = !cancelled &&
            gestureState.lastMoveShiftedWebView &&
            abs(vy) >= minFlingVelocityPx
        gestureState.lastMoveShiftedWebView = false
        recycleVelocityTracker()
        onGestureEnd?.invoke(vy, childWebView, shouldHandoffFling)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        gestureState.reset()
        recycleVelocityTracker()
        super.onDetachedFromWindow()
    }
}

internal fun findWebViewContainer(swipeLayout: SwipeRefreshLayout): WebViewContainer? {
    for (i in 0 until swipeLayout.childCount) {
        val child = swipeLayout.getChildAt(i)
        if (child is WebViewContainer) return child
    }
    return null
}
