package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder

/**
 * Shared Mozilla Sync 1.5 HTTP, Token Server, Hawk signing, and BSO encryption transport
 * used across custom encrypted Sync collections (Zen `spaces`/`prefs` and Swiftfox `swiftfox`).
 */
internal class Sync15Transport(
    httpClientProvider: () -> OkHttpClient
) {
    // Dedicated client that shares connection pool/dispatcher but never follows HTTP/SSL redirects
    // so Bearer/Hawk credentials and X-KeyID can never leak across redirect boundaries.
    private val noRedirectClient: OkHttpClient by lazy {
        httpClientProvider().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
    val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val userAgent = "Swiftfox/${BuildConfig.VERSION_NAME} (Android)"

    data class AuthCacheKey(
        val kid: String,
        val fxaAccessToken: String,
        val syncKeyBase64: String,
        val tokenServerUrl: String
    )

    private data class CachedAuthContext(
        val cacheKey: AuthCacheKey,
        val createdAtNano: Long,
        val session: TokenServerSession,
        val keyRing: CollectionKeyRing?
    )

    private val authCacheLock = Any()
    private var cachedAuth: CachedAuthContext? = null

    fun invalidateAuthCache() {
        synchronized(authCacheLock) {
            cachedAuth = null
        }
    }

    class TokenServerSession(
        val apiEndpoint: String,
        val hawkId: String,
        val hawkKey: ByteArray,
        @Volatile var clockSkewSeconds: Long = 0L
    )

    class CollectionKeyRing(
        val defaultKeys: SyncCrypto.SyncKeyBundle,
        val collectionKeys: Map<String, SyncCrypto.SyncKeyBundle>
    ) {
        fun keysFor(collection: String): SyncCrypto.SyncKeyBundle =
            collectionKeys[collection] ?: defaultKeys
    }

    class DecryptBatchResult(
        val records: List<Pair<String, JsonObject>>,
        val failedIds: Set<String>
    ) {
        val failedCount: Int get() = failedIds.size
    }

    data class ServerLimits(
        val maxRecordPayloadBytes: Int = DEFAULT_MAX_RECORD_PAYLOAD_BYTES,
        val maxPostRecords: Int = DEFAULT_MAX_POST_RECORDS,
        val maxPostBytes: Int = DEFAULT_MAX_POST_BYTES
    )

    sealed class PostChunkResult {
        data class Success(
            val uploadedIds: List<String>,
            val skippedOversizedIds: List<String>,
            val lastModified: String?
        ) : PostChunkResult()

        data object Conflict : PostChunkResult()
    }

    data class HttpResult(
        val code: Int,
        val body: String,
        val headers: Map<String, String>
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    suspend fun getOrFetchSession(
        cacheKey: AuthCacheKey
    ): Pair<TokenServerSession, CollectionKeyRing?> {
        val nowNano = System.nanoTime()
        synchronized(authCacheLock) {
            val hit = cachedAuth
            if (hit != null && hit.cacheKey == cacheKey && (nowNano - hit.createdAtNano) in 0L until AUTH_CACHE_TTL_NANOS) {
                return hit.session to hit.keyRing
            }
        }
        val session = exchangeTokenServerCreds(cacheKey.kid, cacheKey.fxaAccessToken, cacheKey.tokenServerUrl)
        synchronized(authCacheLock) {
            cachedAuth = CachedAuthContext(
                cacheKey = cacheKey,
                createdAtNano = System.nanoTime(),
                session = session,
                keyRing = null
            )
        }
        return session to null
    }

    suspend fun getOrFetchCollectionKeyRing(
        cacheKey: AuthCacheKey,
        session: TokenServerSession,
        cachedKeyRing: CollectionKeyRing?
    ): CollectionKeyRing? {
        if (cachedKeyRing != null) return cachedKeyRing
        val fetched = fetchCollectionKeys(session, SyncCrypto.fromKSyncBase64(cacheKey.syncKeyBase64))
        if (fetched != null) {
            synchronized(authCacheLock) {
                val cur = cachedAuth
                if (cur != null && cur.cacheKey == cacheKey && cur.session === session) {
                    cachedAuth = cur.copy(keyRing = fetched)
                }
            }
        }
        return fetched
    }

    private suspend fun exchangeTokenServerCreds(
        kid: String,
        fxaAccessToken: String,
        tokenServerUrl: String
    ): TokenServerSession {
        val resolvedUrl = resolveTokenServerUrl(tokenServerUrl)
        val req = Request.Builder()
            .url(resolvedUrl)
            .get()
            .header("Authorization", "Bearer $fxaAccessToken")
            .header("X-KeyID", kid)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .build()
        val resp = executeRaw(req)
        if (resp.code !in 200..299) {
            throw IOException("Token server error (${resp.code})")
        }
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }
            .getOrElse { throw IOException("Token server returned invalid JSON") }
        val endpoint = (obj["api_endpoint"] as? JsonPrimitive)?.content?.trimEnd('/')
            ?: throw IOException("Token server missing api_endpoint")
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            throw IOException("Sync storage endpoint must use HTTPS")
        }
        val id = (obj["id"] as? JsonPrimitive)?.content
            ?: throw IOException("Token server missing hawk id")
        val key = (obj["key"] as? JsonPrimitive)?.content
            ?: throw IOException("Token server missing hawk key")
        val serverTs = (obj["timestamp"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
        val skew = if (serverTs != null && serverTs > 0L) {
            serverTs - (System.currentTimeMillis() / 1000L)
        } else {
            0L
        }
        return TokenServerSession(
            apiEndpoint = endpoint,
            hawkId = id,
            hawkKey = key.toByteArray(Charsets.UTF_8),
            clockSkewSeconds = skew
        )
    }

    private suspend fun fetchCollectionKeys(
        session: TokenServerSession,
        syncRootKeys: SyncCrypto.SyncKeyBundle
    ): CollectionKeyRing? {
        val resp = requestHawk(session, "GET", "/storage/crypto/keys", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return null
        val record = json.parseToJsonElement(resp.body).jsonObject
        val payloadStr = (record["payload"] as? JsonPrimitive)?.content
            ?: throw IOException("Missing crypto/keys payload")
        val cleartextBytes = SyncCrypto.decryptBSO(payloadStr, syncRootKeys)
        val keysJson = json.parseToJsonElement(String(cleartextBytes, Charsets.UTF_8)).jsonObject

        val defaultPair = keysJson["default"]?.jsonArray
            ?: throw IOException("Missing default keys in crypto/keys")
        if (defaultPair.size < 2) {
            throw IOException("Malformed default keys in crypto/keys")
        }
        val defaultEnc = (defaultPair[0] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IOException("Malformed default encryption key in crypto/keys")
        val defaultHmac = (defaultPair[1] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IOException("Malformed default HMAC key in crypto/keys")
        val defaultBundle = SyncCrypto.fromBase64Pair(defaultEnc, defaultHmac)

        val collectionsMap = linkedMapOf<String, SyncCrypto.SyncKeyBundle>()
        val collectionsObj = keysJson["collections"] as? JsonObject
        if (collectionsObj != null) {
            for ((col, el) in collectionsObj) {
                val pair = el as? JsonArray ?: continue
                if (pair.size >= 2) {
                    val encStr = (pair[0] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                    val hmacStr = (pair[1] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                    runCatching {
                        SyncCrypto.fromBase64Pair(encStr, hmacStr)
                    }.getOrNull()?.let { collectionsMap[col] = it }
                }
            }
        }
        return CollectionKeyRing(defaultBundle, collectionsMap)
    }

    suspend fun fetchInfoCollections(session: TokenServerSession): Map<String, Double> {
        val resp = requestHawk(session, "GET", "/info/collections", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return emptyMap()
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0 }
    }

    suspend fun fetchInfoConfiguration(session: TokenServerSession): ServerLimits {
        val resp = runCatching {
            requestHawk(session, "GET", "/info/configuration", allowCodes = setOf(404))
        }.getOrNull() ?: return ServerLimits()
        if (resp.code == 404 || resp.body.isBlank()) return ServerLimits()
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return ServerLimits()
        val maxPayload = (obj["max_record_payload_bytes"] as? JsonPrimitive)?.intOrNull
            ?.takeIf { it >= 16 * 1024 }
            ?: DEFAULT_MAX_RECORD_PAYLOAD_BYTES
        val maxPostRecords = (obj["max_post_records"] as? JsonPrimitive)?.intOrNull
            ?.coerceIn(1, 500)
            ?: DEFAULT_MAX_POST_RECORDS
        val maxPostBytes = (obj["max_post_bytes"] as? JsonPrimitive)?.intOrNull
            ?.takeIf { it >= 64 * 1024 }
            ?: DEFAULT_MAX_POST_BYTES
        return ServerLimits(
            maxRecordPayloadBytes = maxPayload,
            maxPostRecords = maxPostRecords,
            maxPostBytes = maxPostBytes
        )
    }

    suspend fun fetchMetaGlobalPayload(session: TokenServerSession): String? {
        val resp = requestHawk(session, "GET", "/storage/meta/global", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return null
        return (obj["payload"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    suspend fun fetchCollectionWithLastModified(
        session: TokenServerSession,
        collection: String,
        limit: Int = 2500
    ): Pair<List<JsonObject>, String?> {
        var restarts = 0
        while (true) {
            val out = mutableListOf<JsonObject>()
            var offset: String? = null
            var pages = 0
            var lastModified: String? = null
            var conflicted = false
            while (pages < 50) {
                var path = "/storage/$collection?full=1&limit=$limit"
                if (!offset.isNullOrBlank()) {
                    path += "&offset=" + URLEncoder.encode(offset, "UTF-8").replace("+", "%20")
                }
                val headers = if (!offset.isNullOrBlank() && !lastModified.isNullOrBlank()) {
                    mapOf("X-If-Unmodified-Since" to lastModified)
                } else {
                    emptyMap()
                }
                val resp = requestHawk(
                    session = session,
                    method = "GET",
                    path = path,
                    extraHeaders = headers,
                    allowCodes = setOf(404, 412)
                )
                if (resp.code == 412) {
                    conflicted = true
                    break
                }
                if (resp.code == 404) {
                    if (pages == 0) {
                        return out to (lastModified ?: resp.header("X-Last-Modified"))
                    }
                    throw IOException("Collection '$collection' disappeared during pagination")
                }
                if (resp.body.isBlank()) {
                    throw IOException("Sync storage returned empty body for '$collection'")
                }
                if (lastModified == null) {
                    lastModified = resp.header("X-Last-Modified")
                }
                val arr = runCatching { json.parseToJsonElement(resp.body).jsonArray }
                    .getOrElse { throw IOException("Sync storage returned malformed JSON for '$collection'") }
                arr.forEach { el -> (el as? JsonObject)?.let { out += it } }
                pages++
                val nextOffset = resp.header("X-Weave-Next-Offset")
                if (nextOffset.isNullOrBlank() || nextOffset == offset) {
                    offset = null
                    break
                }
                offset = nextOffset
            }
            if (!conflicted) {
                if (!offset.isNullOrBlank()) {
                    throw IOException("Sync collection '$collection' exceeded maximum pagination limit")
                }
                return out to lastModified
            }
            if (restarts >= 1) throw IOException("Collection '$collection' changed while reading")
            restarts++
        }
    }

    fun decryptBsoList(
        records: List<JsonObject>,
        keys: SyncCrypto.SyncKeyBundle
    ): DecryptBatchResult {
        val decrypted = mutableListOf<Pair<String, JsonObject>>()
        val failedIds = linkedSetOf<String>()
        for (record in records) {
            val id = (record["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: continue
            val payload = (record["payload"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: continue
            val obj = runCatching {
                val raw = SyncCrypto.decryptBSO(payload, keys)
                json.parseToJsonElement(String(raw, Charsets.UTF_8)).jsonObject
            }.getOrNull()
            if (obj == null) {
                failedIds += id
            } else {
                decrypted += id to obj
            }
        }
        return DecryptBatchResult(decrypted, failedIds)
    }

    suspend fun postEncryptedBsosChunked(
        session: TokenServerSession,
        collection: String,
        keys: SyncCrypto.SyncKeyBundle,
        records: List<Pair<String, JsonObject>>,
        initialIfUnmodifiedSince: String?,
        limits: ServerLimits = ServerLimits()
    ): PostChunkResult {
        if (records.isEmpty()) {
            return PostChunkResult.Success(emptyList(), emptyList(), initialIfUnmodifiedSince)
        }

        val maxSinglePayload = (limits.maxRecordPayloadBytes - 256).coerceAtLeast(16 * 1024)
        val maxPostRecords = limits.maxPostRecords.coerceIn(1, 100)
        val maxPostBytes = (limits.maxPostBytes - 4096).coerceAtLeast(32 * 1024)

        val encryptedEntries = mutableListOf<Triple<String, JsonObject, Int>>()
        val skippedOversizedIds = mutableListOf<String>()
        for ((bsoId, cleartextObj) in records) {
            val encryptedPayload = SyncCrypto.encryptBSO(
                cleartextObj.toString().toByteArray(Charsets.UTF_8),
                keys
            )
            val payloadSize = encryptedPayload.toByteArray(Charsets.UTF_8).size
            if (payloadSize > maxSinglePayload) {
                skippedOversizedIds += bsoId
                continue
            }
            val bsoObj = buildJsonObject {
                put("id", JsonPrimitive(bsoId))
                put("payload", JsonPrimitive(encryptedPayload))
            }
            val bsoBytes = bsoObj.toString().toByteArray(Charsets.UTF_8).size
            encryptedEntries += Triple(bsoId, bsoObj, bsoBytes)
        }

        val uploadedIds = mutableListOf<String>()
        var currentIfUnmodifiedSince = initialIfUnmodifiedSince
        var idx = 0
        while (idx < encryptedEntries.size) {
            val chunkObjs = mutableListOf<JsonObject>()
            val chunkIds = mutableListOf<String>()
            var chunkBytes = 2 // []
            while (idx < encryptedEntries.size && chunkObjs.size < maxPostRecords) {
                val (id, obj, objBytes) = encryptedEntries[idx]
                val nextBytes = chunkBytes + objBytes + if (chunkObjs.isEmpty()) 0 else 1
                if (chunkObjs.isNotEmpty() && nextBytes > maxPostBytes) {
                    break
                }
                chunkObjs += obj
                chunkIds += id
                chunkBytes = nextBytes
                idx++
            }
            val postBody = JsonArray(chunkObjs).toString().toByteArray(Charsets.UTF_8)
            val extraHeaders = if (!currentIfUnmodifiedSince.isNullOrBlank()) {
                mapOf("X-If-Unmodified-Since" to currentIfUnmodifiedSince)
            } else {
                emptyMap()
            }
            val resp = requestHawk(
                session = session,
                method = "POST",
                path = "/storage/$collection",
                bodyBytes = postBody,
                extraHeaders = extraHeaders,
                allowCodes = setOf(412)
            )
            if (resp.code == 412) {
                return PostChunkResult.Conflict
            }
            if (resp.code !in 200..299) {
                throw IOException("Sync upload failed (${resp.code})")
            }
            val respObj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull()
            val failed = respObj?.get("failed") as? JsonObject
            if (failed != null && failed.isNotEmpty()) {
                throw IOException("Sync upload partial failure: $failed")
            }
            uploadedIds += chunkIds
            currentIfUnmodifiedSince = resp.header("X-Last-Modified")
                ?: (respObj?.get("modified") as? JsonPrimitive)?.content
                ?: currentIfUnmodifiedSince
        }

        return PostChunkResult.Success(
            uploadedIds = uploadedIds,
            skippedOversizedIds = skippedOversizedIds,
            lastModified = currentIfUnmodifiedSince
        )
    }

    suspend fun requestHawk(
        session: TokenServerSession,
        method: String,
        path: String,
        bodyBytes: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        allowCodes: Set<Int> = emptySet(),
        allowClockRetry: Boolean = true
    ): HttpResult {
        val fullUrlString = session.apiEndpoint + path
        val url = URL(fullUrlString)
        val resource = url.path + (url.query?.let { "?$it" } ?: "")
        val payloadHash = bodyBytes?.let { HawkAuth.payloadHash(it, "application/json") }
        val auth = HawkAuth.authorization(
            method = method,
            url = url,
            id = session.hawkId,
            key = session.hawkKey,
            payloadHash = payloadHash,
            resource = resource,
            clockSkewSeconds = session.clockSkewSeconds
        )
        val builder = Request.Builder()
            .url(fullUrlString)
            .header("Authorization", auth)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)

        for ((k, v) in extraHeaders) {
            builder.header(k, v)
        }
        if (method == "GET") {
            builder.get()
        } else {
            builder.method(method, (bodyBytes ?: ByteArray(0)).toRequestBody(jsonMediaType))
        }
        val resp = executeRaw(builder.build())
        if (resp.code == 401 && allowClockRetry) {
            val serverTs = parseHawkServerTimestamp(resp.header("WWW-Authenticate"))
                ?: resp.header("X-Weave-Timestamp")?.toDoubleOrNull()?.toLong()
            if (serverTs != null && serverTs > 0L) {
                session.clockSkewSeconds = serverTs - (System.currentTimeMillis() / 1000L)
                return requestHawk(
                    session = session,
                    method = method,
                    path = path,
                    bodyBytes = bodyBytes,
                    extraHeaders = extraHeaders,
                    allowCodes = allowCodes,
                    allowClockRetry = false
                )
            }
        }
        if (resp.code !in 200..299 && resp.code !in allowCodes) {
            if (resp.code == 401) {
                invalidateAuthCache()
            }
            throw IOException("Sync storage HTTP ${resp.code} on $path")
        }
        return resp
    }

    private fun parseHawkServerTimestamp(wwwAuth: String?): Long? {
        if (wwwAuth.isNullOrBlank()) return null
        val match = HAWK_TS_REGEX.find(wwwAuth) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private suspend fun executeRaw(request: Request): HttpResult {
        coroutineContext.ensureActive()
        val call = noRedirectClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion {
            call.cancel()
        }
        try {
            call.execute().use { response ->
                coroutineContext.ensureActive()
                val headers = linkedMapOf<String, String>()
                for (name in response.headers.names()) {
                    response.header(name)?.let { headers[name] = it }
                }
                val contentLen = response.body.contentLength()
                if (contentLen > MAX_SYNC_RESPONSE_BYTES) {
                    throw IOException("Sync response exceeds safety size limit")
                }
                val body = response.body.byteStream().use { stream ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = stream.read(buf)
                        if (read == -1) break
                        total += read
                        if (total > MAX_SYNC_RESPONSE_BYTES) {
                            throw IOException("Sync response exceeds safety size limit")
                        }
                        out.write(buf, 0, read)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
                return HttpResult(response.code, body, headers)
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    companion object {
        private const val SYNC_15_TOKEN_SUFFIX = "/1.0/sync/1.5"
        private const val AUTH_CACHE_TTL_NANOS = 4L * 60L * 1_000_000_000L
        private const val MAX_SYNC_RESPONSE_BYTES = 32 * 1024 * 1024
        private const val DEFAULT_MAX_RECORD_PAYLOAD_BYTES = 2 * 1024 * 1024
        private const val DEFAULT_MAX_POST_RECORDS = 100
        private const val DEFAULT_MAX_POST_BYTES = 2 * 1024 * 1024
        private val HAWK_TS_REGEX = Regex("""\bts\s*=\s*"?(\d+)"?""", RegexOption.IGNORE_CASE)

        /**
         * Matches Mozilla `sync15/src/client/token.rs::fixup_server_url`:
         * `FirefoxAccount.getTokenServerEndpointURL()` returns the bare base URL
         * (e.g. `https://token.services.mozilla.com/`), so append `/1.0/sync/1.5`
         * unless already present. Enforces HTTPS so Sync tokens never travel in cleartext.
         */
        fun resolveTokenServerUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) throw IOException("Empty token server URL")
            if (!trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("https:///", ignoreCase = true)
            ) {
                throw IOException("Token server URL must be a valid HTTPS URL")
            }
            val parsed = trimmed.toHttpUrlOrNull()
            if (parsed == null || !parsed.isHttps || parsed.host.isBlank()) {
                throw IOException("Token server URL must be a valid HTTPS URL")
            }
            return if (trimmed.endsWith(SYNC_15_TOKEN_SUFFIX, ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed$SYNC_15_TOKEN_SUFFIX"
            }
        }

        /**
         * Safety write gate: refuses writes if `meta/global` reports an unknown Sync
         * storageVersion (!= 5), target engine version > [maxVersion], or [collection] declined.
         */
        fun metaGlobalAllowsCollectionWrite(
            metaGlobalPayloadJson: String?,
            collection: String,
            maxVersion: Double = 1.0,
            parser: Json = Json { ignoreUnknownKeys = true }
        ): Boolean {
            if (metaGlobalPayloadJson.isNullOrBlank()) return false
            return runCatching {
                val obj = parser.parseToJsonElement(metaGlobalPayloadJson) as? JsonObject ?: return false
                val storagePrim = obj["storageVersion"] as? JsonPrimitive ?: return false
                if (storagePrim.isString) return false
                val storageVersion = storagePrim.doubleOrNull ?: return false
                if (storageVersion != 5.0) return false
                val declined = obj["declined"] as? JsonArray
                if (declined != null && declined.any {
                        (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content == collection
                    }
                ) {
                    return false
                }
                val engines = obj["engines"] as? JsonObject ?: return true
                val targetEngine = engines[collection] as? JsonObject ?: return true
                val versionEl = targetEngine["version"] ?: return true
                val versionPrim = versionEl as? JsonPrimitive ?: return false
                if (versionPrim.isString) return false
                val version = versionPrim.doubleOrNull ?: return false
                version <= maxVersion
            }.getOrDefault(false)
        }
    }
}
