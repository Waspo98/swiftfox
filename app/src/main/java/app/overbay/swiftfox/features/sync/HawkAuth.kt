package app.overbay.swiftfox.features.sync

import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/**
 * Hawk HTTP MAC authentication for Mozilla Sync 1.5 Storage Server requests.
 * Signs the verbatim request-line resource with percent-encoding preserved.
 */
object HawkAuth {
    private val random = SecureRandom()

    fun authorization(
        method: String,
        url: URL,
        id: String,
        key: ByteArray,
        payloadHash: String? = null,
        resource: String? = null,
        fixedTimestamp: Long? = null,
        fixedNonce: String? = null,
        clockSkewSeconds: Long = 0L
    ): String {
        val ts = fixedTimestamp ?: ((System.currentTimeMillis() / 1000L) + clockSkewSeconds)
        val nonce = fixedNonce ?: randomNonce()
        val host = url.host
        val port = if (url.port != -1) url.port else if (url.protocol.equals("https", ignoreCase = true)) 443 else 80
        val path = if (resource != null) {
            if (resource.startsWith("/")) resource else "/$resource"
        } else {
            val p = url.path.ifEmpty { "/" }
            val q = url.query
            if (!q.isNullOrEmpty()) "$p?$q" else p
        }
        val hash = payloadHash.orEmpty()
        val normalized = listOf(
            "hawk.1.header",
            ts.toString(),
            nonce,
            method.uppercase(Locale.ROOT),
            path,
            host.lowercase(Locale.ROOT),
            port.toString(),
            hash,
            "",
            ""
        ).joinToString("\n")
        val macB64 = Base64.getEncoder().encodeToString(
            SyncCrypto.hmacSha256(key, normalized.toByteArray(Charsets.UTF_8))
        )
        val parts = mutableListOf(
            "id=\"$id\"",
            "ts=\"$ts\"",
            "nonce=\"$nonce\"",
            "mac=\"$macB64\""
        )
        if (hash.isNotEmpty()) {
            parts += "hash=\"$hash\""
        }
        return "Hawk " + parts.joinToString(", ")
    }

    fun payloadHash(body: ByteArray, contentType: String = "application/json"): String {
        val normalizedType = contentType.split(";").firstOrNull()?.trim().orEmpty().lowercase(Locale.ROOT)
        val prefix = "hawk.1.payload\n$normalizedType\n".toByteArray(Charsets.UTF_8)
        val data = prefix + body + byteArrayOf(0x0A)
        return Base64.getEncoder().encodeToString(SyncCrypto.sha256(data))
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
            .replace("+", "")
            .replace("/", "")
            .replace("=", "")
    }
}
