package app.overbay.swiftfox

import androidx.compose.runtime.snapshots.Snapshot
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.engine.DomTouchGateState
import app.overbay.swiftfox.engine.ScriptInjector
import app.overbay.swiftfox.ui.browser.FrameCallbackScheduler
import app.overbay.swiftfox.ui.browser.GestureAxis
import app.overbay.swiftfox.ui.browser.PullToRefreshController
import app.overbay.swiftfox.ui.browser.PullToRefreshVisualState
import app.overbay.swiftfox.ui.browser.ToolbarCollapseBridge
import app.overbay.swiftfox.ui.browser.ToolbarScrollCollapseController
import app.overbay.swiftfox.ui.browser.VerticalDragGestureState
import app.overbay.swiftfox.ui.browser.ViewportHeightMode
import app.overbay.swiftfox.ui.browser.computeWebViewHeightPx
import app.overbay.swiftfox.ui.browser.computeWebViewTranslationYPx
import app.overbay.swiftfox.ui.browser.resolveViewportHeightMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolbarScrollCollapseControllerTest {

    private class FakeFrameScheduler : FrameCallbackScheduler {
        val posted = mutableListOf<Runnable>()
        val cancelled = mutableListOf<Runnable>()

        override fun postOnNextFrame(action: Runnable) {
            posted += action
        }

        override fun cancelFrameCallback(action: Runnable) {
            cancelled += action
            posted.remove(action)
        }

        fun runPendingFrames() {
            val batch = posted.toList()
            posted.clear()
            batch.forEach { it.run() }
        }
    }

    @Test
    fun hideToolbarsOnScrollDefaultsToEnabled() {
        assertTrue(BrowserSettings().hideToolbarsOnScroll)
    }

    @Test
    fun tracksFingerDragOneToOneAndClampsWithinZeroToOne() {
        val controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20)
        val travel = 200f

        // Drag up 50px -> 0.25 progress, exact 50px consumed
        val consumed1 = controller.onDragDelta(
            rawDeltaY = 50f,
            travelDistancePx = travel,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertEquals(50f, consumed1, 0.001f)
        assertEquals(0.25f, controller.collapseProgress, 0.001f)

        // Drag up 200px more -> clamps at 1.0, only 150px consumed (remainder passes to WebView scroll)
        val consumed2 = controller.onDragDelta(
            rawDeltaY = 200f,
            travelDistancePx = travel,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertEquals(150f, consumed2, 0.001f)
        assertEquals(1f, controller.collapseProgress, 0.001f)

        // Drag back down 80px -> 0.60 progress, -80px consumed
        val consumed3 = controller.onDragDelta(
            rawDeltaY = -80f,
            travelDistancePx = travel,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertEquals(-80f, consumed3, 0.001f)
        assertEquals(0.6f, controller.collapseProgress, 0.001f)
    }

    @Test
    fun resolvesSnapTargetFromVelocityMidpointTopAndScrollability() {
        val controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20, snapMidpoint = 0.45f)

        // Low-velocity release below midpoint -> snaps back open
        controller.setProgress(0.30f)
        assertEquals(
            0f,
            controller.resolveSnapTarget(
                velocityY = -50f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 400,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false
            ),
            0.001f
        )

        // Fast upward flick (negative velocityY) on scrollable page -> snaps hidden even below midpoint
        assertEquals(
            1f,
            controller.resolveSnapTarget(
                velocityY = -600f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 0,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false
            ),
            0.001f
        )

        // Fast upward flick on short / 100vh non-scrollable page -> MUST stay expanded (0f)
        assertEquals(
            0f,
            controller.resolveSnapTarget(
                velocityY = -900f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 0,
                scrollRange = 1200,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false
            ),
            0.001f
        )

        // Low-velocity release above midpoint away from top -> snaps hidden
        controller.setProgress(0.55f)
        assertEquals(
            1f,
            controller.resolveSnapTarget(
                velocityY = 0f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 400,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false,
                webViewTranslatesWithTopBar = false
            ),
            0.001f
        )

        // Top/split mode: low-velocity release above midpoint right at top of page (scrollY stayed ~0
        // due to 1:1 top-bar WebView translation) -> finishes collapsing to 1f instead of springing open
        assertEquals(
            1f,
            controller.resolveSnapTarget(
                velocityY = 0f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 10,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false,
                webViewTranslatesWithTopBar = true
            ),
            0.001f
        )

        // Bottom-only / side-rail mode: WebView scrolls directly without top-bar translation, so
        // releasing right at the top of the page (scrollY <= threshold) reveals the bar to 0f
        assertEquals(
            0f,
            controller.resolveSnapTarget(
                velocityY = 0f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 10,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false,
                webViewTranslatesWithTopBar = false
            ),
            0.001f
        )

        // Fast downward flick (positive velocityY) -> snaps expanded
        controller.setProgress(0.85f)
        assertEquals(
            0f,
            controller.resolveSnapTarget(
                velocityY = 500f,
                minFlingVelocityPxPerSec = 150f,
                scrollY = 800,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = true,
                forceExpanded = false
            ),
            0.001f
        )
    }

    @Test
    fun shortNonScrollablePagesNeverCollapseAndFlingToTopRevealsBars() {
        val controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20)

        val consumedShort = controller.onDragDelta(
            rawDeltaY = 120f,
            travelDistancePx = 200f,
            scrollRange = 1200,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertEquals(0f, consumedShort, 0.001f)
        assertEquals(0f, controller.collapseProgress, 0.001f)

        // Coasting downward advances collapse 1:1
        val snapDuringDownFling = controller.onFlingScroll(
            scrollY = 200,
            deltaY = 100,
            travelDistancePx = 200f,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertNull(snapDuringDownFling)
        assertEquals(0.5f, controller.collapseProgress, 0.001f)

        // Coasting back to top requests animated reveal to 0f
        val snapAtTop = controller.onFlingScroll(
            scrollY = 0,
            deltaY = -200,
            travelDistancePx = 200f,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertEquals(0f, snapAtTop)
    }

    @Test
    fun disablingSettingOrForcingExpandedResetsProgress() {
        val controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20)
        controller.setProgress(0.8f)

        assertEquals(
            0f,
            controller.onDragDelta(
                rawDeltaY = 40f,
                travelDistancePx = 200f,
                scrollRange = 3000,
                scrollExtent = 1000,
                minScrollableOverflowPx = 400,
                enabled = false,
                forceExpanded = false
            ),
            0.001f
        )
        assertEquals(0f, controller.collapseProgress, 0.001f)
    }

    @Test
    fun multiTouchBeforeVerticalLockAlwaysCancelsTrackingCleanly() {
        val gesture = VerticalDragGestureState(touchSlop = 16)
        gesture.onDown(rawX = 200f, rawY = 500f)
        assertTrue(gesture.trackingActive)
        assertEquals(GestureAxis.UNDECIDED, gesture.axis)

        // Second finger touches down immediately for pinch-zoom before crossing vertical slop
        assertTrue(gesture.onPointerDown())
        assertFalse(gesture.trackingActive)
        assertEquals(GestureAxis.IGNORED, gesture.axis)
        assertFalse(gesture.shouldRecordVelocityOnMove())
        assertFalse(gesture.shouldRecordTerminalVelocity())

        // Subsequent moves & pointer-up must be ignored without re-triggering finish
        assertEquals(VerticalDragGestureState.MoveResult.Ignored, gesture.onMove(200f, 300f))
        assertFalse(gesture.onUpOrCancel(isCancel = false).shouldFinish)
    }

    @Test
    fun horizontalSwipeCancelsImmediatelyAndSimpleTapNeverAppliesVelocity() {
        val gesture = VerticalDragGestureState(touchSlop = 16)

        // Horizontal carousel swipe with slight diagonal drift cancels immediately on lock
        gesture.onDown(rawX = 200f, rawY = 500f)
        assertEquals(
            VerticalDragGestureState.MoveResult.LockedHorizontalCancel,
            gesture.onMove(rawX = 240f, rawY = 485f)
        )
        assertFalse(gesture.trackingActive)
        assertEquals(GestureAxis.IGNORED, gesture.axis)
        assertFalse(gesture.shouldRecordTerminalVelocity())
        assertFalse(gesture.onUpOrCancel(isCancel = false).shouldFinish)

        // Quick tap without crossing vertical slop finishes as cancelled (zero fling velocity)
        gesture.onDown(rawX = 200f, rawY = 500f)
        assertEquals(VerticalDragGestureState.MoveResult.Ignored, gesture.onMove(202f, 496f))
        assertFalse(gesture.shouldRecordTerminalVelocity())
        val tapUp = gesture.onUpOrCancel(isCancel = false)
        assertTrue(tapUp.shouldFinish)
        assertTrue(tapUp.cancelled)
    }

    @Test
    fun verticalDragTracksDeltaAndFinishesWithVelocity() {
        val gesture = VerticalDragGestureState(touchSlop = 16)
        gesture.onDown(rawX = 200f, rawY = 500f)

        val move1 = gesture.onMove(rawX = 202f, rawY = 460f) // dy = -40 -> exceeds slop + 2 (18)
        assertTrue(move1 is VerticalDragGestureState.MoveResult.VerticalDelta)
        assertEquals(24f, (move1 as VerticalDragGestureState.MoveResult.VerticalDelta).rawDy, 0.001f)
        assertEquals(GestureAxis.VERTICAL, gesture.axis)
        assertTrue(gesture.shouldRecordTerminalVelocity())

        val up = gesture.onUpOrCancel(isCancel = false)
        assertTrue(up.shouldFinish)
        assertFalse(up.cancelled)
    }

    @Test
    fun splitModeUsesTopBarHeightAsTravelDistanceForOneToOneWebViewTracking() {
        val bridge = ToolbarCollapseBridge(
            controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20),
            sideRailTravelPx = 150f,
            minExtraOverflowPx = 120,
            minScrollableFloorPx = 400
        )

        // Split mode: bottomBar (with nav bar inset) is taller than topBar; travel must lock 1:1 to topBar
        bridge.topBarHeightPx = 175
        bridge.bottomBarHeightPx = 245
        assertEquals(175f, bridge.effectiveTravelDistancePx(), 0.001f)

        // Bottom-only mode: no top bar -> tracks bottomBar 1:1
        bridge.topBarHeightPx = 0
        bridge.bottomBarHeightPx = 245
        assertEquals(245f, bridge.effectiveTravelDistancePx(), 0.001f)

        // Side-rail mode: neither top nor bottom bar -> falls back to sideRailTravelPx
        bridge.topBarHeightPx = 0
        bridge.bottomBarHeightPx = 0
        assertEquals(150f, bridge.effectiveTravelDistancePx(), 0.001f)
    }

    @Test
    fun applyProgressFlushesComposeSnapshotNotificationsSynchronously() {
        val bridge = ToolbarCollapseBridge(
            controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20),
            sideRailTravelPx = 150f,
            minExtraOverflowPx = 120,
            minScrollableFloorPx = 400
        )
        // Flush any unrelated prior global writes before registering our observer
        Snapshot.sendApplyNotifications()
        var progressStateNotified = false
        val handle = Snapshot.registerApplyObserver { changed, _ ->
            if (bridge.progressState in changed) {
                progressStateNotified = true
            }
        }
        try {
            bridge.applyProgress(0.65f, expandedViewport = true)
            assertTrue(
                "Expected applyProgress to flush Compose snapshot notifications synchronously so topBar/bottomBar stay on the exact same VSync frame as WebView.translationY",
                progressStateNotified
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun downwardFlingNearTopDoesNotSpringToolbarsBackOpen() {
        val controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20)
        controller.setProgress(1f)

        // Downward scroll (deltaY > 0) while scrollY is still within topRevealThresholdPx must stay collapsed
        val snapOnDownScroll = controller.onFlingScroll(
            scrollY = 12,
            deltaY = 8,
            travelDistancePx = 175f,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertNull(snapOnDownScroll)
        assertEquals(1f, controller.collapseProgress, 0.001f)

        // Upward fling back to top (deltaY < 0, scrollY <= threshold) still reveals toolbars to 0f
        val snapOnUpScrollToTop = controller.onFlingScroll(
            scrollY = 12,
            deltaY = -12,
            travelDistancePx = 175f,
            scrollRange = 3000,
            scrollExtent = 1000,
            minScrollableOverflowPx = 400,
            enabled = true,
            forceExpanded = false
        )
        assertEquals(0f, snapOnUpScrollToTop)

        // Settling a mid-collapse fling snaps cleanly to 0f or 1f around midpoint
        controller.setProgress(0.40f)
        assertEquals(0f, controller.resolveSettledSnapTarget(enabled = true, forceExpanded = false), 0.001f)
        controller.setProgress(0.50f)
        assertEquals(1f, controller.resolveSettledSnapTarget(enabled = true, forceExpanded = false), 0.001f)
        assertEquals(0f, controller.resolveSettledSnapTarget(enabled = true, forceExpanded = true), 0.001f)
    }

    @Test
    fun verticalLockDoesNotReversePastPreviousUndecidedMove() {
        val gesture = VerticalDragGestureState(touchSlop = 16)
        gesture.onDown(rawX = 200f, rawY = 500f)

        // Intermediate move lands at dy = -17 (past touchSlop=16, but <= touchSlop+2=18 so still UNDECIDED)
        assertEquals(VerticalDragGestureState.MoveResult.Ignored, gesture.onMove(rawX = 200f, rawY = 483f))

        // Next move advances 2px further to 481 (dy = -19 -> locks VERTICAL).
        // Delta must be 2px (from 483 -> 481), never 3px (484 -> 481) which would shift WebView backwards by 1px.
        val move = gesture.onMove(rawX = 200f, rawY = 481f)
        assertTrue(move is VerticalDragGestureState.MoveResult.VerticalDelta)
        assertEquals(2f, (move as VerticalDragGestureState.MoveResult.VerticalDelta).rawDy, 0.001f)
    }

    @Test
    fun cancelFlingWorkResyncsControllerToRenderedProgressAndCancelsExactHost() = runTest {
        val bridge = ToolbarCollapseBridge(
            controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20),
            sideRailTravelPx = 150f,
            minExtraOverflowPx = 120,
            minScrollableFloorPx = 400
        ).apply {
            topBarHeightPx = 200
            bottomBarHeightPx = 200
        }
        val hostA = FakeFrameScheduler()
        val animatedTargets = mutableListOf<Float>()

        // Queue a deferred fling progress update (0.0 -> 0.30) without running the next frame yet
        bridge.onFlingScrollMetrics(
            host = hostA,
            scrollY = 100,
            deltaY = 60,
            scrollRange = 3000,
            scrollExtent = 1000,
            enabled = true,
            forceExpanded = false,
            isEnabledProvider = { true },
            isForceExpandedProvider = { false },
            coroutineScope = this,
            onAnimateCollapseTo = { animatedTargets += it },
            isHostActive = { true }
        )
        assertEquals(0.30f, bridge.controller.collapseProgress, 0.001f)
        assertEquals(0f, bridge.renderedProgress, 0.001f)
        assertEquals(1, hostA.posted.size)

        // Cancelling fling work must remove the callback from hostA, cancel settle timer,
        // and resync controller back to renderedProgress (0f) so animations never jump to 0.30f.
        bridge.cancelFlingWork()
        assertTrue(hostA.posted.isEmpty())
        assertEquals(1, hostA.cancelled.size)
        assertEquals(0f, bridge.controller.collapseProgress, 0.001f)

        advanceTimeBy(200)
        runCurrent()
        assertTrue(animatedTargets.isEmpty())
    }

    @Test
    fun staleHostRunnableCannotStealActiveHostFlingProgressOrTriggerStaleSnap() = runTest {
        val bridge = ToolbarCollapseBridge(
            controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20),
            sideRailTravelPx = 150f,
            minExtraOverflowPx = 120,
            minScrollableFloorPx = 400
        ).apply {
            topBarHeightPx = 200
            bottomBarHeightPx = 200
        }
        val hostA = FakeFrameScheduler()
        val hostB = FakeFrameScheduler()
        var activeHost: FrameCallbackScheduler = hostA
        val animatedTargets = mutableListOf<Float>()

        // Host A schedules a deferred fling progress update
        bridge.onFlingScrollMetrics(
            host = hostA,
            scrollY = 100,
            deltaY = 50,
            scrollRange = 3000,
            scrollExtent = 1000,
            enabled = true,
            forceExpanded = false,
            isEnabledProvider = { true },
            isForceExpandedProvider = { false },
            coroutineScope = this,
            onAnimateCollapseTo = { animatedTargets += it },
            isHostActive = { activeHost === hostA }
        )
        val staleRunnableFromA = hostA.posted.single()

        // Switch to host B, cancelling host A's work and queuing host B's fling progress (0.0 -> 0.40)
        activeHost = hostB
        bridge.cancelFlingWork()
        bridge.onFlingScrollMetrics(
            host = hostB,
            scrollY = 200,
            deltaY = 80,
            scrollRange = 3000,
            scrollExtent = 1000,
            enabled = true,
            forceExpanded = false,
            isEnabledProvider = { true },
            isForceExpandedProvider = { false },
            coroutineScope = this,
            onAnimateCollapseTo = { animatedTargets += it },
            isHostActive = { activeHost === hostB }
        )

        // Even if host A's stale runnable still fires, it must be a complete no-op for host B
        staleRunnableFromA.run()
        assertEquals(0f, bridge.renderedProgress, 0.001f)
        assertEquals(1, hostB.posted.size)

        // Host B's runnable still applies its full 0.40f progress cleanly
        hostB.runPendingFrames()
        assertEquals(0.40f, bridge.renderedProgress, 0.001f)

        // After settle delay, mid-collapse fling snaps cleanly to 0f (0.40 < 0.45 midpoint)
        advanceTimeBy(125)
        runCurrent()
        assertEquals(listOf(0f), animatedTargets)
    }

    @Test
    fun sameFrameSnapAndProgressCallbacksNeverCollideInSingleSlot() = runTest {
        val bridge = ToolbarCollapseBridge(
            controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20),
            sideRailTravelPx = 150f,
            minExtraOverflowPx = 120,
            minScrollableFloorPx = 400
        ).apply {
            topBarHeightPx = 200
            bottomBarHeightPx = 200
        }
        val host = FakeFrameScheduler()
        val animatedTargets = mutableListOf<Float>()

        // Start rendered at 0.50f
        bridge.applyProgress(0.50f, expandedViewport = true)

        // Event 1 on same frame: upward coast near top queues a snap-to-0f runnable
        bridge.onFlingScrollMetrics(
            host = host,
            scrollY = 10,
            deltaY = -20,
            scrollRange = 3000,
            scrollExtent = 1000,
            enabled = true,
            forceExpanded = false,
            isEnabledProvider = { true },
            isForceExpandedProvider = { false },
            coroutineScope = this,
            onAnimateCollapseTo = { animatedTargets += it },
            isHostActive = { true }
        )
        assertEquals(1, host.posted.size)

        // Event 2 on same frame: downward delta supersedes the snap runnable with progress 0.65f
        bridge.onFlingScrollMetrics(
            host = host,
            scrollY = 40,
            deltaY = 30,
            scrollRange = 3000,
            scrollExtent = 1000,
            enabled = true,
            forceExpanded = false,
            isEnabledProvider = { true },
            isForceExpandedProvider = { false },
            coroutineScope = this,
            onAnimateCollapseTo = { animatedTargets += it },
            isHostActive = { true }
        )
        assertEquals(1, host.cancelled.size)
        assertEquals(1, host.posted.size)

        // Frame executes progress update (0.65f), NOT the cancelled snap-to-0f
        host.runPendingFrames()
        assertEquals(0.65f, bridge.renderedProgress, 0.001f)
        assertTrue(animatedTargets.isEmpty())

        // After settle delay, mid-collapse fling settles to 1f (0.65 >= 0.45 midpoint)
        advanceTimeBy(125)
        runCurrent()
        assertEquals(listOf(1f), animatedTargets)
    }

    @Test
    fun webViewHeightNeverOverhangsContainerBottomAcrossAllPlacementsAndProgress() {
        val containerH = 2206
        val placements = listOf(
            // Split (top + bottom)
            182 to 264,
            // Top-only
            182 to 0,
            // Bottom-only
            0 to 264,
            // Side-rail
            0 to 0
        )
        val progressSamples = listOf(0f, 0.001f, 0.05f, 0.24f, 0.47f, 0.5f, 0.85f, 0.99f, 0.998f, 0.999f, 1f)

        for ((topH, bottomH) in placements) {
            for (expanded in listOf(false, true)) {
                for (p in progressSamples) {
                    val mode = resolveViewportHeightMode(
                        topBarHeightPx = topH,
                        collapseProgress = p,
                        expandedViewport = expanded
                    )
                    val childH = computeWebViewHeightPx(
                        containerHeightPx = containerH,
                        topBarHeightPx = topH,
                        bottomBarHeightPx = bottomH,
                        mode = mode
                    )
                    val ty = computeWebViewTranslationYPx(
                        topBarHeightPx = topH,
                        collapseProgress = p
                    )
                    assertTrue(
                        "WebView bottom (${childH + ty}) must never overhang container ($containerH) for top=$topH bottom=$bottomH expanded=$expanded p=$p mode=$mode",
                        childH + ty <= containerH + 0.001f
                    )
                }
            }
        }
    }

    @Test
    fun splitAndTopModesDeferFullViewportExpansionUntilTopBarTranslationReachesZero() {
        val containerH = 2206
        val topH = 182
        val bottomH = 264

        // Resting expanded: inset by both bars so fixed web footers clear the bottom bar
        val restMode = resolveViewportHeightMode(topH, collapseProgress = 0f, expandedViewport = false)
        assertEquals(ViewportHeightMode.INSET_BOTH, restMode)
        assertEquals(
            containerH - topH - bottomH,
            computeWebViewHeightPx(containerH, topH, bottomH, restMode)
        )

        // Mid-collapse drag (ty > 0): must NOT expand to full containerH (which overhangs by ty
        // and lets Chromium's inner visual viewport overscroll sticky headers behind status bar)
        val midMode = resolveViewportHeightMode(topH, collapseProgress = 0.45f, expandedViewport = true)
        assertEquals(ViewportHeightMode.INSET_TOP_ONLY, midMode)
        assertEquals(
            containerH - topH,
            computeWebViewHeightPx(containerH, topH, bottomH, midMode)
        )

        // Fully collapsed (p >= 0.999f): translationY snaps to exact 0f and viewport expands to FULL
        val fullMode = resolveViewportHeightMode(topH, collapseProgress = 0.999f, expandedViewport = true)
        assertEquals(ViewportHeightMode.FULL, fullMode)
        assertEquals(0f, computeWebViewTranslationYPx(topH, collapseProgress = 0.999f), 0f)
        assertEquals(containerH, computeWebViewHeightPx(containerH, topH, bottomH, fullMode))

        // Mid-drag pull-down reversal from 1f -> 0.90f immediately drops back to INSET_TOP_ONLY
        val reverseMode = resolveViewportHeightMode(topH, collapseProgress = 0.90f, expandedViewport = true)
        assertEquals(ViewportHeightMode.INSET_TOP_ONLY, reverseMode)

        // Bottom-only mode (topH == 0): translationY is always 0f, so FULL is safe immediately
        val bottomOnlyMode = resolveViewportHeightMode(0, collapseProgress = 0.25f, expandedViewport = true)
        assertEquals(ViewportHeightMode.FULL, bottomOnlyMode)
        assertEquals(0f, computeWebViewTranslationYPx(0, collapseProgress = 0.25f), 0f)
    }

    @Test
    fun pullToRefreshControllerEnforcesDomScrollTopZeroGateAndCrossOriginPendingGuard() {
        val ptr = PullToRefreshController(
            triggerDistancePx = 100f,
            dragRate = 0.5f,
            showDeadzonePx = 10f
        )

        // Case 1: Scrolled inner container (e.g. fi.google.com .SSPGKf with scrollTop > 1),
        // nested overscroll-behavior-y: contain modal, or overscroll-behavior-y: none -> BLOCKED
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertFalse(
            ptr.onVerticalDragDelta(
                rawDy = -90f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.BLOCKED
            )
        )
        assertEquals(0f, ptr.pullDistancePx, 0.001f)
        assertFalse(ptr.eligible)
        assertFalse(
            ptr.onTouchEnd(
                isCancel = false,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.BLOCKED
            )
        )

        // Case 2: Cross-origin iframe / unacked touch stays PENDING while DOM gate is active:
        // downward drag must NOT pull while PENDING, and releasing never triggers refresh
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertFalse(
            ptr.onVerticalDragDelta(
                rawDy = -120f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.PENDING
            )
        )
        assertEquals(0f, ptr.pullDistancePx, 0.001f)
        assertTrue(ptr.eligible)
        assertFalse(
            ptr.onTouchEnd(
                isCancel = false,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.PENDING
            )
        )

        // Case 3: Async DOM touchGate block (e.g. capture scroll event or JS preventDefault())
        // arriving mid-pull immediately cancels and disqualifies the active pull
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = -120f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertTrue(ptr.pullDistancePx > 0f)
        assertTrue(ptr.blockByDom())
        assertEquals(0f, ptr.pullDistancePx, 0.001f)
        assertFalse(ptr.eligible)
        assertFalse(
            ptr.onTouchEnd(
                isCancel = false,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.BLOCKED
            )
        )

        // Case 4: JS-disabled / error page fallback (hasDomTouchGate = false) uses native scrollY == 0
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = -230f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = false,
                domGateState = DomTouchGateState.PENDING
            )
        )
        assertTrue(ptr.isThresholdReached)
        assertTrue(
            ptr.onTouchEnd(
                isCancel = false,
                hasDomTouchGate = false,
                domGateState = DomTouchGateState.PENDING
            )
        )
    }

    @Test
    fun pullToRefreshControllerTriggersAtScrollTopZeroRetractsSmoothlyAndResetsOnCancel() {
        val ptr = PullToRefreshController(
            triggerDistancePx = 100f,
            dragRate = 0.5f,
            showDeadzonePx = 10f
        )
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )

        // Small downward pull inside deadzone stays invisible
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = -8f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertEquals(0f, ptr.pullDistancePx, 0.001f)

        // Pull past trigger threshold (raw 8 + 220 = 228 -> (228 - 10) * 0.5 = 109 >= 100)
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = -220f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertTrue(ptr.isThresholdReached)

        // Pushing finger upward retracts smoothly below threshold while remaining above 0
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = 100f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertFalse(ptr.isThresholdReached)
        assertTrue(ptr.pullDistancePx > 0f)

        // Releasing below threshold does NOT trigger refresh
        assertFalse(
            ptr.onTouchEnd(
                isCancel = false,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )

        // Retracting all the way back to 0 disqualifies the rest of that gesture
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = -80f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = 100f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertEquals(0f, ptr.pullDistancePx, 0.001f)
        assertFalse(ptr.eligible)
        assertFalse(
            ptr.onVerticalDragDelta(
                rawDy = -250f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )

        // Full pull at scrollTop == 0 released cleanly triggers refresh
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertTrue(
            ptr.onVerticalDragDelta(
                rawDy = -260f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertTrue(ptr.isThresholdReached)
        assertTrue(
            ptr.onTouchEnd(
                isCancel = false,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )

        // Full pull cancelled (ACTION_CANCEL or pinch-zoom) never triggers refresh
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        ptr.onVerticalDragDelta(
            rawDy = -260f,
            currentScrollY = 0,
            canWebViewScrollUp = false,
            currentCollapseProgress = 0f,
            hasDomTouchGate = true,
            domGateState = DomTouchGateState.ALLOWED
        )
        assertTrue(ptr.isThresholdReached)
        assertFalse(
            ptr.onTouchEnd(
                isCancel = true,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
    }

    @Test
    fun pullToRefreshControllerDisqualifiesNonTopStartsCollapsedBarsUpwardScrollsAndAborts() {
        val ptr = PullToRefreshController(
            triggerDistancePx = 100f
        )

        // Non-zero initial scrollY disqualifies immediately
        ptr.onTouchDown(
            initialScrollY = 120,
            canWebViewScrollUp = true,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertFalse(ptr.eligible)
        assertFalse(
            ptr.onVerticalDragDelta(
                rawDy = -80f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )

        // canWebViewScrollUp == true disqualifies even when scrollY reports 0
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = true,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertFalse(ptr.eligible)

        // Collapsed toolbars disqualify immediately
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0.8f,
            enabled = true
        )
        assertFalse(ptr.eligible)

        // Dragging finger UP (scrolling down into the page) disqualifies reversal without lifting
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        assertTrue(ptr.eligible)
        assertFalse(
            ptr.onVerticalDragDelta(
                rawDy = 40f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )
        assertFalse(ptr.eligible)
        assertFalse(
            ptr.onVerticalDragDelta(
                rawDy = -160f,
                currentScrollY = 0,
                canWebViewScrollUp = false,
                currentCollapseProgress = 0f,
                hasDomTouchGate = true,
                domGateState = DomTouchGateState.ALLOWED
            )
        )

        // Multi-touch cancelPull resets pull and disqualifies gesture
        ptr.onTouchDown(
            initialScrollY = 0,
            canWebViewScrollUp = false,
            initialCollapseProgress = 0f,
            enabled = true
        )
        ptr.onVerticalDragDelta(
            rawDy = -120f,
            currentScrollY = 0,
            canWebViewScrollUp = false,
            currentCollapseProgress = 0f,
            hasDomTouchGate = true,
            domGateState = DomTouchGateState.ALLOWED
        )
        assertTrue(ptr.cancelPull())
        assertFalse(ptr.eligible)
        assertEquals(0f, ptr.pullDistancePx, 0.001f)
    }

    @Test
    fun pullToRefreshVisualStateClearsPulseOnRePullPreservesRetractOriginAndBumpsClearGeneration() {
        val visualState = PullToRefreshVisualState(triggerDistancePx = 100f)
        assertEquals(0, visualState.clearGeneration)

        // Normal drag + release below threshold preserves activePullPx so overlay animates back smoothly
        visualState.updatePull(pullPx = 48f, dragging = true)
        assertTrue(visualState.isDragging)
        assertEquals(48f, visualState.activePullPx.floatValue, 0.001f)
        visualState.updatePull(pullPx = 0f, dragging = false)
        assertFalse(visualState.isDragging)
        assertEquals(48f, visualState.activePullPx.floatValue, 0.001f)

        // Starting a new pull during an active refresh pulse clears isRefreshingPulse so
        // cancelling/releasing below threshold never replays a phantom pulse
        visualState.triggerRefreshPulse()
        assertTrue(visualState.isRefreshingPulse)
        assertFalse(visualState.isDragging)
        visualState.updatePull(pullPx = 24f, dragging = true)
        assertTrue(visualState.isDragging)
        assertFalse(visualState.isRefreshingPulse)
        assertEquals(24f, visualState.activePullPx.floatValue, 0.001f)
        visualState.updatePull(pullPx = 0f, dragging = false)
        assertFalse(visualState.isDragging)
        assertFalse(visualState.isRefreshingPulse)

        // Explicit clear (tab switch / Start Page / posture change) resets everything and bumps generation
        visualState.triggerRefreshPulse()
        visualState.clear()
        assertFalse(visualState.isDragging)
        assertFalse(visualState.isRefreshingPulse)
        assertEquals(0f, visualState.activePullPx.floatValue, 0.001f)
        assertEquals(1, visualState.clearGeneration)
    }

    @Test
    fun flushSnapshotForHostCoalescesPerFrameAndClearsOnCancelOrHostSwap() {
        val bridge = ToolbarCollapseBridge(
            controller = ToolbarScrollCollapseController(topRevealThresholdPx = 20),
            sideRailTravelPx = 150f,
            minExtraOverflowPx = 120,
            minScrollableFloorPx = 400
        )
        val hostA = FakeFrameScheduler()
        val hostB = FakeFrameScheduler()

        // First flush on hostA queues a single VSync reset callback; second on same frame coalesces
        assertTrue(bridge.flushSnapshotForHost(hostA))
        assertTrue(bridge.snapshotFlushQueuedOnFrame)
        assertEquals(1, hostA.posted.size)
        assertFalse(bridge.flushSnapshotForHost(hostA))
        assertEquals(1, hostA.posted.size)

        // Running the scheduled frame callback clears the latch so the next frame flushes again
        hostA.runPendingFrames()
        assertFalse(bridge.snapshotFlushQueuedOnFrame)
        assertTrue(bridge.flushSnapshotForHost(hostA))
        assertEquals(1, hostA.posted.size)

        // Swapping to hostB while hostA's reset callback is still pending cancels hostA's callback
        // and immediately flushes on hostB (never permanently latching if hostA is detached)
        assertTrue(bridge.flushSnapshotForHost(hostB))
        assertEquals(1, hostA.cancelled.size)
        assertTrue(hostA.posted.isEmpty())
        assertEquals(1, hostB.posted.size)
        assertTrue(bridge.snapshotFlushQueuedOnFrame)

        // cancelFlingWork() (invoked on attachActiveContainer/detachContainer) cancels hostB and clears latch
        bridge.cancelFlingWork()
        assertFalse(bridge.snapshotFlushQueuedOnFrame)
        assertEquals(1, hostB.cancelled.size)
        assertTrue(hostB.posted.isEmpty())
    }

    @Test
    fun touchScrollGateScriptAttachesWindowBubbleListenersAndChecksBodyAndRootOverscroll() {
        val script = ScriptInjector.buildTouchScrollGateScript()
        assertTrue(script.contains("if (window !== window.top) return;"))
        assertTrue(script.contains("window.addEventListener('touchstart', onTouchBubble, { passive: true })"))
        assertTrue(script.contains("window.addEventListener('touchmove', onTouchBubble, { passive: true })"))
        assertTrue(script.contains("window.addEventListener('load', attachWindowBubbleListeners"))
        assertTrue(script.contains("[document.body, document.documentElement]"))
    }
}
