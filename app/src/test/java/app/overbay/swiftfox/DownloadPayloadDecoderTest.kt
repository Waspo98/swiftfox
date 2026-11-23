package app.overbay.swiftfox

import app.overbay.swiftfox.features.downloads.DownloadCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test
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
    }
}
