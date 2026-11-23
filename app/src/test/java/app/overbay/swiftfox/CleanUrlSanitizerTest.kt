package app.overbay.swiftfox

import app.overbay.swiftfox.features.sharing.CleanUrlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanUrlSanitizerTest {

    @Test
    fun stripsTrackingParametersAndPreservesFunctionalQuery() {
        val dirty = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123&utm_source=share&feature=shared&t=42"
        val result = CleanUrlSanitizer.sanitize(dirty)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42", result.url)
        assertEquals(3, result.strippedCount)
    }

    @Test
    fun stripsAllQueryParamsCleanlyKeepingFragment() {
        val dirty = "https://arstechnica.com/gadgets/2026/05/review/?fbclid=IwAR999&utm_campaign=front#comments"
        val result = CleanUrlSanitizer.sanitize(dirty)
        assertEquals("https://arstechnica.com/gadgets/2026/05/review/#comments", result.url)
        assertEquals(2, result.strippedCount)
    }

    @Test
    fun preservesSpaHashRoutesAndNonYoutubeSiParam() {
        val spaHash = "https://example.com/#/dashboard?utm_source=internal&tab=2"
        assertEquals(spaHash, CleanUrlSanitizer.sanitize(spaHash).url)
        assertEquals(0, CleanUrlSanitizer.sanitize(spaHash).strippedCount)

        val genericSi = "https://books.example.org/search?q=fox&si=10"
        assertEquals(genericSi, CleanUrlSanitizer.sanitize(genericSi).url)
        assertEquals(0, CleanUrlSanitizer.sanitize(genericSi).strippedCount)
    }

    @Test
    fun leavesCleanUrlsUntouched() {
        val clean = "https://github.com/search?q=swiftfox&type=repositories"
        val result = CleanUrlSanitizer.sanitize(clean)
        assertEquals(clean, result.url)
        assertEquals(0, result.strippedCount)
    }

    @Test
    fun stripsExactIcidAndIcidPrefixWithoutOverMatching() {
        val url = "https://shop.example.com/item?icid=hero1&icid_home=banner&acid=keep&icidfoo=keep2"
        val result = CleanUrlSanitizer.sanitize(url)
        assertEquals("https://shop.example.com/item?acid=keep&icidfoo=keep2", result.url)
        assertEquals(2, result.strippedCount)
    }
}
