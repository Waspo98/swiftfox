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
    fun leavesCleanUrlsUntouched() {
        val clean = "https://github.com/search?q=swiftfox&type=repositories"
        val result = CleanUrlSanitizer.sanitize(clean)
        assertEquals(clean, result.url)
        assertEquals(0, result.strippedCount)
    }
}
