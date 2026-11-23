package app.overbay.swiftfox.features.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Firefox Sync 1.5 AES-256-CBC + HMAC-SHA256 BSO cryptography.
 * Matches Mozilla `sync15/src/key_bundle.rs` and `crypto.rs`.
 */
object SyncCrypto {

    private val random = SecureRandom()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    class SyncKeyBundle(
        val encryptionKey: ByteArray,
        val hmacKey: ByteArray
    ) {
        init {
            if (encryptionKey.size != 32) {
                throw SyncCryptoException("Invalid encryption key length: ${encryptionKey.size}")
            }
            if (hmacKey.size != 32) {
                throw SyncCryptoException("Invalid HMAC key length: ${hmacKey.size}")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SyncKeyBundle) return false
            return MessageDigest.isEqual(encryptionKey, other.encryptionKey) &&
                MessageDigest.isEqual(hmacKey, other.hmacKey)
        }

        override fun hashCode(): Int =
            31 * encryptionKey.contentHashCode() + hmacKey.contentHashCode()
    }

    /**
     * Decodes a 64-byte Sync key encoded as URL-safe base64 (with or without padding),
     * splitting [0..31] into AES encryptionKey and [32..63] into hmacKey.
     */
    fun fromKSyncBase64(ksyncB64Url: String): SyncKeyBundle {
        val cleaned = ksyncB64Url.trim()
        if (cleaned.isEmpty()) throw SyncCryptoException("Empty kSync key")
        val raw = try {
            Base64.getUrlDecoder().decode(cleaned)
        } catch (e: IllegalArgumentException) {
            throw SyncCryptoException("Invalid base64url kSync key", e)
        }
        if (raw.size != 64) {
            throw SyncCryptoException("Expected 64-byte kSync key, got ${raw.size}")
        }
        return SyncKeyBundle(
            encryptionKey = raw.copyOfRange(0, 32),
            hmacKey = raw.copyOfRange(32, 64)
        )
    }

    /**
     * Decodes a collection key pair from `crypto/keys` ([encKeyB64, hmacKeyB64]).
     */
    fun fromBase64Pair(encB64: String, hmacB64: String): SyncKeyBundle {
        val enc = b64Decode(encB64.trim()) ?: throw SyncCryptoException("Invalid base64 encryption key")
        val hmac = b64Decode(hmacB64.trim()) ?: throw SyncCryptoException("Invalid base64 HMAC key")
        return SyncKeyBundle(enc, hmac)
    }

    fun decryptBSO(payloadJson: String, keys: SyncKeyBundle): ByteArray {
        val env: JsonObject = try {
            jsonParser.parseToJsonElement(payloadJson).jsonObject
        } catch (e: Exception) {
            throw SyncCryptoException("Malformed BSO payload JSON", e)
        }
        val ciphertextB64 = (env["ciphertext"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw SyncCryptoException("Missing BSO ciphertext")
        val ivB64 = (env["IV"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw SyncCryptoException("Missing BSO IV")
        val hmacHex = (env["hmac"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw SyncCryptoException("Missing BSO hmac")

        val expectedHmac = try {
            unhex(hmacHex)
        } catch (e: SyncCryptoException) {
            throw SyncCryptoException("BSO HMAC mismatch (malformed hex)", e)
        }
        if (expectedHmac.size != 32) {
            throw SyncCryptoException("BSO HMAC mismatch (invalid length)")
        }
        val actualHmac = hmacSha256(keys.hmacKey, ciphertextB64.toByteArray(Charsets.UTF_8))
        if (!MessageDigest.isEqual(actualHmac, expectedHmac)) {
            throw SyncCryptoException("BSO HMAC mismatch")
        }

        val ciphertext = b64Decode(ciphertextB64)
            ?: throw SyncCryptoException("Invalid BSO ciphertext base64")
        val iv = b64Decode(ivB64)
            ?: throw SyncCryptoException("Invalid BSO IV base64")
        if (iv.size != 16) {
            throw SyncCryptoException("Invalid BSO IV length: ${iv.size}")
        }
        if (ciphertext.isEmpty() || ciphertext.size % 16 != 0) {
            throw SyncCryptoException("Invalid BSO ciphertext length: ${ciphertext.size}")
        }

        return try {
            aes256Cbc(ciphertext, keys.encryptionKey, iv, decrypt = true)
        } catch (e: GeneralSecurityException) {
            throw SyncCryptoException("BSO AES decryption failed", e)
        }
    }

    fun encryptBSO(plaintext: ByteArray, keys: SyncKeyBundle): String {
        val iv = ByteArray(16).also { random.nextBytes(it) }
        val cipherBytes = try {
            aes256Cbc(plaintext, keys.encryptionKey, iv, decrypt = false)
        } catch (e: GeneralSecurityException) {
            throw SyncCryptoException("BSO AES encryption failed", e)
        }
        val ciphertextB64 = Base64.getEncoder().encodeToString(cipherBytes)
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val hmacHex = hex(hmacSha256(keys.hmacKey, ciphertextB64.toByteArray(Charsets.UTF_8)))
        return buildJsonObject {
            put("ciphertext", JsonPrimitive(ciphertextB64))
            put("IV", JsonPrimitive(ivB64))
            put("hmac", JsonPrimitive(hmacHex))
        }.toString()
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(Locale.ROOT, it) }

    fun unhex(rawInput: String): ByteArray {
        val raw = rawInput.trim()
        if (raw.length % 2 != 0) throw SyncCryptoException("Invalid hex length")
        val out = ByteArray(raw.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(raw[i * 2], 16)
            val lo = Character.digit(raw[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) throw SyncCryptoException("Invalid hex character")
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun aes256Cbc(
        data: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        decrypt: Boolean
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            if (decrypt) Cipher.DECRYPT_MODE else Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv)
        )
        return cipher.doFinal(data)
    }

    private fun b64Decode(raw: String): ByteArray? {
        val cleaned = buildString(raw.length) {
            for (ch in raw) {
                if (ch != '\r' && ch != '\n' && ch != ' ' && ch != '\t') append(ch)
            }
        }
        return try {
            Base64.getDecoder().decode(cleaned)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
