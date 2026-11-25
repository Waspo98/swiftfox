package app.overbay.swiftfox.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EditorialSpaceTabsTest {

    @Test
    fun resolveHorizontalFadeFractions_returnsNullWhenBothDisabledOrWidthTooSmall() {
        assertNull(
            resolveHorizontalFadeFractions(
                showStartFade = false,
                showEndFade = false,
                startFadeWidthPx = 40f,
                endFadeWidthPx = 56f,
                widthPx = 400f,
                isRtl = false
            )
        )
        assertNull(
            resolveHorizontalFadeFractions(
                showStartFade = true,
                showEndFade = true,
                startFadeWidthPx = 40f,
                endFadeWidthPx = 56f,
                widthPx = 1f,
                isRtl = false
            )
        )
    }

    @Test
    fun resolveHorizontalFadeFractions_mapsStartToLeftAndEndToRightInLtr() {
        val startOnly = resolveHorizontalFadeFractions(
            showStartFade = true,
            showEndFade = false,
            startFadeWidthPx = 40f,
            endFadeWidthPx = 80f,
            widthPx = 400f,
            isRtl = false
        )
        assertNotNull(startOnly)
        assertEquals(0.10f, startOnly!!.leftFraction, 0.0001f)
        assertEquals(0f, startOnly.rightFraction, 0.0001f)

        val endOnly = resolveHorizontalFadeFractions(
            showStartFade = false,
            showEndFade = true,
            startFadeWidthPx = 40f,
            endFadeWidthPx = 80f,
            widthPx = 400f,
            isRtl = false
        )
        assertNotNull(endOnly)
        assertEquals(0f, endOnly!!.leftFraction, 0.0001f)
        assertEquals(0.20f, endOnly.rightFraction, 0.0001f)
    }

    @Test
    fun resolveHorizontalFadeFractions_mirrorsEdgesAndCapsInRtl() {
        val startOnlyRtl = resolveHorizontalFadeFractions(
            showStartFade = true,
            showEndFade = false,
            startFadeWidthPx = 40f,
            endFadeWidthPx = 80f,
            widthPx = 400f,
            isRtl = true
        )
        assertNotNull(startOnlyRtl)
        assertEquals(0f, startOnlyRtl!!.leftFraction, 0.0001f)
        assertEquals(0.10f, startOnlyRtl.rightFraction, 0.0001f)

        val endOnlyRtl = resolveHorizontalFadeFractions(
            showStartFade = false,
            showEndFade = true,
            startFadeWidthPx = 40f,
            endFadeWidthPx = 80f,
            widthPx = 400f,
            isRtl = true
        )
        assertNotNull(endOnlyRtl)
        assertEquals(0.20f, endOnlyRtl!!.leftFraction, 0.0001f)
        assertEquals(0f, endOnlyRtl.rightFraction, 0.0001f)

        val clampedRtl = resolveHorizontalFadeFractions(
            showStartFade = true,
            showEndFade = true,
            startFadeWidthPx = 200f,
            endFadeWidthPx = 200f,
            widthPx = 200f,
            isRtl = true
        )
        assertNotNull(clampedRtl)
        assertEquals(0.34f, clampedRtl!!.leftFraction, 0.0001f)
        assertEquals(0.28f, clampedRtl.rightFraction, 0.0001f)
    }

    @Test
    fun parseSpaceColorHexOrNull_parsesValidSixDigitHexAndRejectsInvalidInputs() {
        assertEquals(Color(0xFFFF9147.toInt()), parseSpaceColorHexOrNull("#FF9147"))
        assertEquals(Color(0xFF1A2B3C.toInt()), parseSpaceColorHexOrNull("  1a2b3c "))
        assertNull(parseSpaceColorHexOrNull(null))
        assertNull(parseSpaceColorHexOrNull(""))
        assertNull(parseSpaceColorHexOrNull("#FFF"))
        assertNull(parseSpaceColorHexOrNull("#12345678"))
        assertNull(parseSpaceColorHexOrNull("#ZZZZZZ"))
    }
}
