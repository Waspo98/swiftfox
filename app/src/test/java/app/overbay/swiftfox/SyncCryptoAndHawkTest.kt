package app.overbay.swiftfox

import app.overbay.swiftfox.features.sync.HawkAuth
import app.overbay.swiftfox.features.sync.Sync15SpacesClient
import app.overbay.swiftfox.features.sync.SyncCrypto
import app.overbay.swiftfox.features.sync.SyncCryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SyncCryptoAndHawkTest {

    @Test
    fun fromKSyncBase64Splits64BytesIntoEncAndHmacKeys() {
        val raw = ByteArray(64) { it.toByte() }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)

        val bundle = SyncCrypto.fromKSyncBase64(encoded)
        assertArrayEquals(raw.copyOfRange(0, 32), bundle.encryptionKey)
        assertArrayEquals(raw.copyOfRange(32, 64), bundle.hmacKey)
    }

    @Test(expected = SyncCryptoException::class)
    fun fromKSyncBase64RejectsShortKey() {
        val raw = ByteArray(32) { 1 }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        SyncCrypto.fromKSyncBase64(encoded)
    }

    @Test(expected = SyncCryptoException::class)
    fun fromKSyncBase64RejectsMalformedBase64() {
        SyncCrypto.fromKSyncBase64("not!!valid!!base64url!!")
    }

    @Test
    fun encryptAndDecryptBsoRoundTripsUtf8Json() {
        val keys = SyncCrypto.SyncKeyBundle(
            encryptionKey = ByteArray(32) { (it + 10).toByte() },
            hmacKey = ByteArray(32) { (it + 70).toByte() }
        )
        val payload = """{"id":"space-1","kind":"space","data":{"name":"Work 🦊"}}"""
        val encryptedJson = SyncCrypto.encryptBSO(payload.toByteArray(Charsets.UTF_8), keys)
        val decrypted = String(SyncCrypto.decryptBSO(encryptedJson, keys), Charsets.UTF_8)
        assertEquals(payload, decrypted)
    }

    @Test(expected = SyncCryptoException::class)
    fun decryptBsoRejectsTamperedCiphertext() {
        val keys = SyncCrypto.SyncKeyBundle(
            encryptionKey = ByteArray(32) { 3 },
            hmacKey = ByteArray(32) { 9 }
        )
        val encryptedJson = SyncCrypto.encryptBSO("secret-data".toByteArray(Charsets.UTF_8), keys)
        val tampered = encryptedJson.replace("\"ciphertext\":\"", "\"ciphertext\":\"AA")
        SyncCrypto.decryptBSO(tampered, keys)
    }

    @Test(expected = SyncCryptoException::class)
    fun decryptBsoRejectsInvalidIvLength() {
        val keys = SyncCrypto.SyncKeyBundle(
            encryptionKey = ByteArray(32) { 5 },
            hmacKey = ByteArray(32) { 6 }
        )
        val ciphertextB64 = Base64.getEncoder().encodeToString(ByteArray(16))
        val shortIvB64 = Base64.getEncoder().encodeToString(ByteArray(8))
        val hmac = SyncCrypto.hex(
            SyncCrypto.hmacSha256(keys.hmacKey, ciphertextB64.toByteArray(Charsets.UTF_8))
        )
        val json = """{"ciphertext":"$ciphertextB64","IV":"$shortIvB64","hmac":"$hmac"}"""
        SyncCrypto.decryptBSO(json, keys)
    }

    @Test(expected = SyncCryptoException::class)
    fun decryptBsoRejectsNonHexHmacAndMalformedJson() {
        val keys = SyncCrypto.SyncKeyBundle(ByteArray(32), ByteArray(32))
        try {
            SyncCrypto.decryptBSO("{not-json", keys)
            fail("Expected SyncCryptoException for malformed JSON")
        } catch (_: SyncCryptoException) {
            // Expected; now test non-hex hmac
        }
        val b64 = Base64.getEncoder().encodeToString(ByteArray(16))
        SyncCrypto.decryptBSO("""{"ciphertext":"$b64","IV":"$b64","hmac":"zzzz"}""", keys)
    }

    @Test
    fun hawkAuthorizationMatchesIndependentSpecVectorAndAppliesClockSkew() {
        val body = """{"hello":"world"}""".toByteArray(Charsets.UTF_8)
        val contentType = "application/json; charset=utf-8"
        val hash = HawkAuth.payloadHash(body, contentType)

        // Independent Hawk 1.0 payload hash check
        val expectedPayloadInput = ("hawk.1.payload\napplication/json\n").toByteArray(Charsets.UTF_8) +
            body + byteArrayOf(0x0A)
        val expectedHash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(expectedPayloadInput)
        )
        assertEquals(expectedHash, hash)

        val key = "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn".toByteArray(Charsets.UTF_8)
        val url = URL("https://sync.services.mozilla.com/1.5/42/storage/spaces?full=1")
        val resource = "/1.5/42/storage/spaces?full=1"
        val header = HawkAuth.authorization(
            method = "POST",
            url = url,
            id = "id-42",
            key = key,
            payloadHash = hash,
            resource = resource,
            fixedTimestamp = 1700000123L,
            fixedNonce = "abc999"
        )

        // Independent Hawk 1.0 header MAC check
        val normalized = "hawk.1.header\n1700000123\nabc999\nPOST\n$resource\nsync.services.mozilla.com\n443\n$expectedHash\n\n"
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val expectedMac = Base64.getEncoder().encodeToString(mac)
        assertEquals(
            """Hawk id="id-42", ts="1700000123", nonce="abc999", mac="$expectedMac", hash="$expectedHash"""",
            header
        )

        // Clock skew shifts emitted timestamp when fixedTimestamp is omitted
        val nowSec = System.currentTimeMillis() / 1000L
        val skewedHeader = HawkAuth.authorization(
            method = "GET",
            url = url,
            id = "id-42",
            key = key,
            clockSkewSeconds = 180L
        )
        val tsMatch = Regex("""ts="(\d+)"""").find(skewedHeader)
        assertTrue(tsMatch != null)
        val emittedTs = tsMatch!!.groupValues[1].toLong()
        assertTrue(emittedTs in (nowSec + 175L)..(nowSec + 185L))
    }

    @Test
    fun hawkAuthorizationProducesDeterministicVectorAndPreservesPercentEncoding() {
        val body = """[{"id":"abc"}]""".toByteArray(Charsets.UTF_8)
        val hash = HawkAuth.payloadHash(body, "application/json; charset=utf-8")
        assertTrue(hash.isNotBlank())

        val key = "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn".toByteArray(Charsets.UTF_8)
        val url = URL("https://sync.services.mozilla.com/1.5/12345/storage/spaces?full=1&ids=a%20b")
        val resource = "/1.5/12345/storage/spaces?full=1&ids=a%20b"

        val header1 = HawkAuth.authorization(
            method = "POST",
            url = url,
            id = "hawk-id-1",
            key = key,
            payloadHash = hash,
            resource = resource,
            fixedTimestamp = 1700000000L,
            fixedNonce = "nonce123"
        )
        val header2 = HawkAuth.authorization(
            method = "POST",
            url = url,
            id = "hawk-id-1",
            key = key,
            payloadHash = hash,
            resource = resource,
            fixedTimestamp = 1700000000L,
            fixedNonce = "nonce123"
        )

        assertEquals(header1, header2)
        assertTrue(header1.startsWith("Hawk id=\"hawk-id-1\", ts=\"1700000000\", nonce=\"nonce123\", mac=\""))
        assertTrue(header1.contains(", hash=\"$hash\""))

        // Verify different percent-encoded resource changes MAC
        val headerDifferentResource = HawkAuth.authorization(
            method = "POST",
            url = url,
            id = "hawk-id-1",
            key = key,
            payloadHash = hash,
            resource = "/1.5/12345/storage/spaces?full=1&ids=a+b",
            fixedTimestamp = 1700000000L,
            fixedNonce = "nonce123"
        )
        assertTrue(header1 != headerDifferentResource)
    }

    @Test
    fun resolvesTokenServerEndpointPathConsistently() {
        assertEquals(
            "https://token.services.mozilla.com/1.0/sync/1.5",
            Sync15SpacesClient.resolveTokenServerUrl("https://token.services.mozilla.com")
        )
        assertEquals(
            "https://token.services.mozilla.com/1.0/sync/1.5",
            Sync15SpacesClient.resolveTokenServerUrl("https://token.services.mozilla.com/")
        )
        assertEquals(
            "https://token.services.mozilla.com/1.0/sync/1.5",
            Sync15SpacesClient.resolveTokenServerUrl("https://token.services.mozilla.com/1.0/sync/1.5")
        )
        assertEquals(
            "https://token.services.mozilla.com/1.0/sync/1.5",
            Sync15SpacesClient.resolveTokenServerUrl("https://token.services.mozilla.com/1.0/sync/1.5/")
        )
        assertEquals(
            "https://sync.example.com/token/1.0/sync/1.5",
            Sync15SpacesClient.resolveTokenServerUrl("https://sync.example.com/token/")
        )

        for (invalid in listOf(
            "http://token.services.mozilla.com",
            "http://token.services.mozilla.com/1.0/sync/1.5",
            "javascript:alert(1)",
            "   ",
            "https:///1.0/sync/1.5"
        )) {
            try {
                Sync15SpacesClient.resolveTokenServerUrl(invalid)
                fail("Expected IOException for $invalid")
            } catch (_: java.io.IOException) {
                // Expected HTTPS-only enforcement
            }
        }
    }
}
