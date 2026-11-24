package app.overbay.swiftfox.features.icons

import app.overbay.swiftfox.ui.components.moveListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIconAndFaviconTest {

    @Test
    fun customIconSpecParsesAndEncodesAllVariants() {
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse(null))
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("   "))
        assertNull(CustomIconSpec.None.encode())

        val mat = CustomIconSpec.parse("home")
        assertEquals(CustomIconSpec.MaterialIcon("home"), mat)
        assertEquals("home", mat.encode())

        val prefixedMat = CustomIconSpec.parse("icon:Rocket")
        assertEquals(CustomIconSpec.MaterialIcon("rocket"), prefixedMat)

        val emoji = CustomIconSpec.parse("emoji:🦊")
        assertEquals(CustomIconSpec.Emoji("🦊"), emoji)
        assertEquals("emoji:🦊", emoji.encode())

        val trimmedEmoji = CustomIconSpec.emojiSpec("ABCD")
        assertEquals(CustomIconSpec.Emoji("AB"), trimmedEmoji)

        val img = CustomIconSpec.parse("image:custom_abc123.png")
        assertEquals(CustomIconSpec.LocalImage("custom_abc123.png"), img)
        assertEquals("image:custom_abc123.png", img.encode())
    }

    @Test
    fun customIconSpecRejectsPathTraversalInLocalImages() {
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("image:../secret.png"))
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("image:foo/bar.png"))
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("image:foo\\bar.png"))
    }

    @Test
    fun htmlIconExtractorPrioritizesTouchAndPngAndSkipsSvg() {
        val html = """
            <html><head>
              <link rel="icon" type="image/svg+xml" href="/logo.svg">
              <link rel="shortcut icon" href="/favicon.ico">
              <link rel="icon" type="image/png" sizes="32x32" href="/icons/icon-32.png">
              <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
            </head></html>
        """.trimIndent()

        val candidates = FaviconRepository.extractIconCandidatesFromHtml(html, "https://overbay.app/dash")
        assertEquals(
            listOf(
                "https://overbay.app/apple-touch-icon.png",
                "https://overbay.app/icons/icon-32.png",
                "https://overbay.app/favicon.ico"
            ),
            candidates
        )
    }

    @Test
    fun publicFallbackHostGuardProtectsLanAndLocalHosts() {
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("localhost"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("192.168.86.200"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("10.0.0.5"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("nas.local"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("router.lan"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("[::1]"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("intranet"))

        assertTrue(FaviconRepository.isPublicFallbackEligibleHost("github.com"))
        assertTrue(FaviconRepository.isPublicFallbackEligibleHost("overbay.app"))
    }

    @Test
    fun moveListItemReordersSafelyAcrossBounds() {
        val original = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveListItem(original, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), moveListItem(original, 3, 0))
        assertEquals(original, moveListItem(original, 1, 1))
        assertEquals(original, moveListItem(original, -1, 2))
        assertEquals(original, moveListItem(original, 1, 99))
    }
}
