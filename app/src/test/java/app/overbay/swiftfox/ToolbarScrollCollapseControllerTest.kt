package app.overbay.swiftfox

import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.ui.browser.GestureAxis
import app.overbay.swiftfox.ui.browser.ToolbarScrollCollapseController
import app.overbay.swiftfox.ui.browser.VerticalDragGestureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarScrollCollapseControllerTest {

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
                forceExpanded = false
            ),
            0.001f
        )

        // Low-velocity release above midpoint right at top of page -> snaps expanded
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
                forceExpanded = false
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
}
