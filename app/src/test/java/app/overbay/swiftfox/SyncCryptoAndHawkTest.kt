package app.overbay.swiftfox

import app.overbay.swiftfox.features.sync.HawkAuth
import app.overbay.swiftfox.features.sync.Sync15SpacesClient
import app.overbay.swiftfox.features.sync.SyncCrypto
import app.overbay.swiftfox.features.sync.SyncCryptoException
import app.overbay.swiftfox.features.sync.ZenFetchResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
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
            } catch (_: IOException) {
                // Expected HTTPS-only enforcement
            }
        }
    }

    @Test
    fun sync15SpacesClientRejectsRedirectsAndRetriesHawkClockSkewToDecryptSnapshot() = runBlocking {
        val rawKSync = ByteArray(64) { (it + 7).toByte() }
        val syncKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawKSync)
        val rootKeys = SyncCrypto.fromKSyncBase64(syncKeyBase64)

        val defaultEnc = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 21).toByte() })
        val defaultHmac = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 55).toByte() })
        val defaultKeys = SyncCrypto.fromBase64Pair(defaultEnc, defaultHmac)

        val keysCleartext = """{"id":"keys","default":["$defaultEnc","$defaultHmac"],"collections":{}}"""
        val keysBso = """{"id":"keys","payload":${Json.encodeToString(SyncCrypto.encryptBSO(keysCleartext.toByteArray(), rootKeys))}}"""

        val spaceCleartext = """{"id":"sp-work","kind":"space","data":{"uuid":"sp-work","name":"Work","icon":"work","children":[]}}"""
        val spacesBody = """[{"id":"sp-work","payload":${Json.encodeToString(SyncCrypto.encryptBSO(spaceCleartext.toByteArray(), defaultKeys))}}]"""

        // 1. Verify redirects are NOT followed (302 throws IOException instead of leaking Bearer/X-KeyID)
        var redirectHops = 0
        val redirectClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                redirectHops++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "http://attacker.example/steal")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val syncRedirectTest = Sync15SpacesClient(redirectClient)
        try {
            syncRedirectTest.fetchZenSnapshot(
                kid = "kid-1",
                fxaAccessToken = "secret-token",
                syncKeyBase64 = syncKeyBase64,
                tokenServerUrl = "https://token.services.mozilla.com"
            )
            fail("Expected IOException on 302 redirect")
        } catch (err: IOException) {
            assertTrue(err.message.orEmpty().contains("302"))
            assertEquals(1, redirectHops)
        }

        // 2. Verify full fetchZenSnapshot + 401 Hawk clock-skew retry
        var cryptoKeysCalls = 0
        val mockSyncClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                fun resp(code: Int, body: String, extraHeaders: Map<String, String> = emptyMap()): Response {
                    val b = Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code in 200..299) "OK" else "ERR")
                        .body(body.toResponseBody("application/json".toMediaType()))
                    extraHeaders.forEach { (k, v) -> b.header(k, v) }
                    return b.build()
                }
                when {
                    url == "https://token.services.mozilla.com/1.0/sync/1.5" -> resp(
                        200,
                        """{"api_endpoint":"https://sync.example.org/1.5/42","id":"hawk-1","key":"hawk-secret","timestamp":1700000000}"""
                    )
                    url.endsWith("/storage/crypto/keys") -> {
                        cryptoKeysCalls++
                        if (cryptoKeysCalls == 1) {
                            resp(401, "", mapOf("WWW-Authenticate" to """Hawk ts="1700000123", tsm="abc""""))
                        } else {
                            resp(200, keysBso)
                        }
                    }
                    url.endsWith("/info/collections") -> resp(200, """{"spaces":1700000123.5}""")
                    url.contains("/storage/spaces") -> resp(200, spacesBody, mapOf("X-Last-Modified" to "1700000123.5"))
                    else -> resp(404, "")
                }
            }
            .build()

        val result = Sync15SpacesClient(mockSyncClient).fetchZenSnapshot(
            kid = "kid-1",
            fxaAccessToken = "tok-1",
            syncKeyBase64 = syncKeyBase64,
            tokenServerUrl = "https://token.services.mozilla.com"
        )
        assertTrue(result is ZenFetchResult.Success)
        val snap = (result as ZenFetchResult.Success).snapshot
        assertEquals(2, cryptoKeysCalls)
        assertEquals(listOf("Work"), snap.spaces.map { it.name })
    }
}
