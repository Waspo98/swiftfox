package app.overbay.swiftfox

import app.overbay.swiftfox.features.downloads.DownloadCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets

class DownloadPayloadDecoderTest {

    @Test
    fun decodesBase64DataUri() {
        val (mime, bytes) = DownloadCoordinator.decodeDataOrBase64Payload(
            payload = "data:text/plain;base64,SGVsbG8gU3dpZnRmb3g=",
            fallbackMime = "application/octet-stream"
        )
        assertEquals("text/plain", mime)
        assertEquals("Hello Swiftfox", String(bytes, StandardCharsets.UTF_8))
    }

    @Test
    fun decodesPlainUrlEncodedDataUriWithoutCrashing() {
        val (mime, bytes) = DownloadCoordinator.decodeDataOrBase64Payload(
            payload = "data:text/csv,name%2Cvalue%0Afox%2C120",
            fallbackMime = "application/octet-stream"
        )
        assertEquals("text/csv", mime)
        assertEquals("name,value\nfox,120", String(bytes, StandardCharsets.UTF_8))
    }

    @Test
    fun decodesUtf8PercentEncodedDataUriAndDistinguishesNonBase64Charset() {
        val (mimeUtf8, bytesUtf8) = DownloadCoordinator.decodeDataOrBase64Payload(
            payload = "data:text/plain;charset=utf-8,Fox%20%F0%9F%A6%8A",
            fallbackMime = "application/octet-stream"
        )
        assertEquals("text/plain", mimeUtf8)
        assertEquals("Fox 🦊", String(bytesUtf8, StandardCharsets.UTF_8))

        val (mimeLiteral, bytesLiteral) = DownloadCoordinator.decodeDataOrBase64Payload(
            payload = "data:text/plain;charset=utf-8,value;base64,literal",
            fallbackMime = "application/octet-stream"
        )
        assertEquals("text/plain", mimeLiteral)
        assertEquals("value;base64,literal", String(bytesLiteral, StandardCharsets.UTF_8))
    }

    @Test
    fun extractsRfc5987FilenameStarAndTruncatesLongNamesPreservingExtension() {
        assertEquals(
            "report 2026 🦊.pdf",
            DownloadCoordinator.extractContentDispositionFileName(
                """attachment; filename="fallback.pdf"; filename*=UTF-8''report%202026%20%F0%9F%A6%8A.pdf"""
            )
        )

        val longName = "a".repeat(220) + ".pdf"
        val sanitized = DownloadCoordinator.sanitizeFileName(longName)
        assertEquals(180, sanitized.length)
        assertEquals(true, sanitized.endsWith(".pdf"))

        // Quoted, unquoted, and RFC 5987 non-UTF8 Content-Disposition variants
        assertEquals(
            "quarterly \"final\".pdf",
            DownloadCoordinator.extractContentDispositionFileName(
                """attachment; filename="quarterly \"final\".pdf""""
            )
        )
        assertEquals(
            "archive.zip",
            DownloadCoordinator.extractContentDispositionFileName("attachment; filename=archive.zip; size=42")
        )
        assertEquals(
            "£ rates.pdf",
            DownloadCoordinator.extractContentDispositionFileName(
                "attachment; filename*=ISO-8859-1''%A3%20rates.pdf"
            )
        )
    }

    @Test
    fun decodesPercentEncodedBase64RejectsMalformedAndEnforcesCapsAndSanitizers() {
        // Percent-encoded base64 ("ab>" -> "YWI+" -> "YWI%2B")
        val (mime, bytes) = DownloadCoordinator.decodeDataOrBase64Payload(
            payload = "data:text/plain;base64,YWI%2B",
            fallbackMime = "application/octet-stream"
        )
        assertEquals("text/plain", mime)
        assertEquals("ab>", String(bytes, StandardCharsets.UTF_8))

        // Missing comma in data: URI must fail explicitly
        try {
            DownloadCoordinator.decodeDataOrBase64Payload("data:text/plain;base64", "application/octet-stream")
            fail("Expected IllegalArgumentException for missing comma")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // Oversized payload > 25 MB cap must throw IOException
        val hugePayload = "data:application/octet-stream;base64," + "A".repeat((DownloadCoordinator.MAX_DATA_DOWNLOAD_BYTES * 4 / 3) + 2048)
        try {
            DownloadCoordinator.decodeDataOrBase64Payload(hugePayload, "application/octet-stream")
            fail("Expected IOException for oversized download payload")
        } catch (_: IOException) {
            // Expected
        }

        // Filename traversal, dotfile, and control-char sanitization
        val traversal = DownloadCoordinator.sanitizeFileName("../../etc\\passwd\u0000.txt")
        assertFalse(traversal.contains(".."))
        assertFalse(traversal.contains("/"))
        assertFalse(traversal.contains("\\"))
        assertFalse(traversal.contains("\u0000"))

        val dotOnly = DownloadCoordinator.sanitizeFileName("...")
        assertTrue(dotOnly.startsWith("swiftfox_download_"))

        // Header sanitization removes CRLF and non-ASCII characters (including UTF-8 cookies)
        assertEquals(
            "Swiftfox/1.0 (Android)",
            DownloadCoordinator.sanitizeHeaderValue("  Swiftfox/1.0\r\n (Android)🦊  ")
        )
        assertEquals(
            "sid=abc; city=Mnchen",
            DownloadCoordinator.sanitizeHeaderValue("sid=abc; city=München")
        )

        // MIME normalization strips charset/boundary parameters
        assertEquals("text/plain", DownloadCoordinator.normalizeMimeType("text/plain; charset=utf-8"))
        assertEquals("application/json", DownloadCoordinator.normalizeMimeType(" application/json ; charset=UTF-8 "))
        assertEquals("application/octet-stream", DownloadCoordinator.normalizeMimeType(" ; charset=utf-8"))
        assertEquals("application/octet-stream", DownloadCoordinator.normalizeMimeType(null))
    }
}
