package app.overbay.swiftfox

import app.overbay.swiftfox.features.sync.HawkAuth
import app.overbay.swiftfox.features.sync.Sync15SpacesClient
import app.overbay.swiftfox.features.sync.SyncCrypto
import app.overbay.swiftfox.features.sync.SyncCryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.util.Base64

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
        } catch (_: SyncCryptoException) {
            // Expected; now test non-hex hmac
        }
        val b64 = Base64.getEncoder().encodeToString(ByteArray(16))
        SyncCrypto.decryptBSO("""{"ciphertext":"$b64","IV":"$b64","hmac":"zzzz"}""", keys)
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
    }
}
