package app.overbay.swiftfox

import app.overbay.swiftfox.ui.browser.resolveTabSlideDirection
import app.overbay.swiftfox.ui.theme.firstDisplaySymbol
import org.junit.Assert.assertEquals
import org.junit.Test

class TabStripTransitionTest {

    @Test
    fun slidesInFromRightWhenAppendingNewTab() {
        val dir = resolveTabSlideDirection(
            previousTabId = "t2",
            nextTabId = "t3",
            previousVisibleIds = listOf("t1", "t2"),
            nextVisibleIds = listOf("t1", "t2", "t3"),
            previousSpaceKey = "space_personal",
            nextSpaceKey = "space_personal",
            wasFullscreenOverlayOpen = false,
            reduceMotion = false
        )
        assertEquals(1, dir)
    }

    @Test
    fun slidesByStripOrderWhenSwitchingAdjacentOrAnyVisibleTab() {
        val forward = resolveTabSlideDirection(
            previousTabId = "t1",
            nextTabId = "t3",
            previousVisibleIds = listOf("t1", "t2", "t3"),
            nextVisibleIds = listOf("t1", "t2", "t3"),
            previousSpaceKey = "space_personal",
            nextSpaceKey = "space_personal",
            wasFullscreenOverlayOpen = false,
            reduceMotion = false
        )
        val backward = resolveTabSlideDirection(
            previousTabId = "t3",
            nextTabId = "t1",
            previousVisibleIds = listOf("t1", "t2", "t3"),
            nextVisibleIds = listOf("t1", "t2", "t3"),
            previousSpaceKey = "space_personal",
            nextSpaceKey = "space_personal",
            wasFullscreenOverlayOpen = false,
            reduceMotion = false
        )
        assertEquals(1, forward)
        assertEquals(-1, backward)
    }

    @Test
    fun disablesHorizontalSlideWhenReturningFromFullscreenOverlayOrChangingSpace() {
        assertEquals(
            0,
            resolveTabSlideDirection(
                previousTabId = "t1",
                nextTabId = "t2",
                previousVisibleIds = listOf("t1", "t2"),
                nextVisibleIds = listOf("t1", "t2"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "space_personal",
                wasFullscreenOverlayOpen = true,
                reduceMotion = false
            )
        )
        assertEquals(
            0,
            resolveTabSlideDirection(
                previousTabId = "t1",
                nextTabId = "t4",
                previousVisibleIds = listOf("t1", "t2"),
                nextVisibleIds = listOf("t4"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "space_work",
                wasFullscreenOverlayOpen = false,
                reduceMotion = false
            )
        )
        assertEquals(
            0,
            resolveTabSlideDirection(
                previousTabId = "t1",
                nextTabId = "t4",
                previousVisibleIds = listOf("t1", "t2"),
                nextVisibleIds = listOf("t4"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "private",
                wasFullscreenOverlayOpen = false,
                reduceMotion = false
            )
        )
    }

    @Test
    fun disablesHorizontalSlideWhenReduceMotionEnabledOrSameTab() {
        assertEquals(
            0,
            resolveTabSlideDirection(
                previousTabId = "t1",
                nextTabId = "t2",
                previousVisibleIds = listOf("t1", "t2"),
                nextVisibleIds = listOf("t1", "t2"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "space_personal",
                wasFullscreenOverlayOpen = false,
                reduceMotion = true
            )
        )
        assertEquals(
            0,
            resolveTabSlideDirection(
                previousTabId = "t1",
                nextTabId = "t1",
                previousVisibleIds = listOf("t1", "t2"),
                nextVisibleIds = listOf("t1", "t2"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "space_personal",
                wasFullscreenOverlayOpen = false,
                reduceMotion = false
            )
        )
    }

    @Test
    fun slidesDirectionallyWhenActiveTabIsClosed() {
        // Closing last tab reveals the tab to its left (-1)
        assertEquals(
            -1,
            resolveTabSlideDirection(
                previousTabId = "t3",
                nextTabId = "t2",
                previousVisibleIds = listOf("t1", "t2", "t3"),
                nextVisibleIds = listOf("t1", "t2"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "space_personal",
                wasFullscreenOverlayOpen = false,
                reduceMotion = false
            )
        )
        // Closing middle tab reveals tab that shifts into its index (+1)
        assertEquals(
            1,
            resolveTabSlideDirection(
                previousTabId = "t2",
                nextTabId = "t3",
                previousVisibleIds = listOf("t1", "t2", "t3"),
                nextVisibleIds = listOf("t1", "t3"),
                previousSpaceKey = "space_personal",
                nextSpaceKey = "space_personal",
                wasFullscreenOverlayOpen = false,
                reduceMotion = false
            )
        )
    }

    @Test
    fun firstDisplaySymbolPreservesEmojiSurrogatePairsAndUppercasesLetters() {
        assertEquals("🔥", firstDisplaySymbol("🔥 News"))
        assertEquals("🦊", firstDisplaySymbol(" 🦊 Swiftfox"))
        assertEquals("G", firstDisplaySymbol("github"))
        assertEquals("S", firstDisplaySymbol("   ", fallback = "S"))
    }
}
