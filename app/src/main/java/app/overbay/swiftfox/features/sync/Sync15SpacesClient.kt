package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

sealed class ZenFetchResult {
    data class Success(val snapshot: ZenSpacesCodec.ZenSyncSnapshot) : ZenFetchResult()
    data object CollectionAbsent : ZenFetchResult()
}

/**
 * Encrypted Sync 1.5 HTTP client for Zen Browser's `spaces` & `prefs` collections.
 * Uses the active Mozilla Account's `oldsync` Bearer token + `kSync` bundle to obtain
 * Sync Storage Server Hawk credentials via Mozilla's Token Server.
 */
class Sync15SpacesClient(
    httpClientProvider: () -> OkHttpClient
) {
    constructor(httpClient: OkHttpClient) : this({ httpClient })

    // Dedicated client that shares connection pool/dispatcher but never follows HTTP/SSL redirects
    // so Bearer/Hawk credentials and X-KeyID can never leak across redirect boundaries.
    private val noRedirectClient: OkHttpClient by lazy {
        httpClientProvider().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val userAgent = "Swiftfox/${BuildConfig.VERSION_NAME} (Android)"

    private data class CachedAuthContext(
        val cacheKey: String,
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

    private class TokenServerSession(
        val apiEndpoint: String,
        val hawkId: String,
        val hawkKey: ByteArray,
        @Volatile var clockSkewSeconds: Long = 0L
    )

    private class CollectionKeyRing(
        val defaultKeys: SyncCrypto.SyncKeyBundle,
        val collectionKeys: Map<String, SyncCrypto.SyncKeyBundle>
    ) {
        fun keysFor(collection: String): SyncCrypto.SyncKeyBundle =
            collectionKeys[collection] ?: defaultKeys
    }

    private class DecryptBatchResult(
        val records: List<Pair<String, JsonObject>>,
        val failedIds: Set<String>
    ) {
        val failedCount: Int get() = failedIds.size
    }

    private data class HttpResult(
        val code: Int,
        val body: String,
        val headers: Map<String, String>
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    private suspend fun getOrFetchSessionAndKeys(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String
    ): Pair<TokenServerSession, CollectionKeyRing?> {
        val nowNano = System.nanoTime()
        val key = "$kid|${fxaAccessToken.hashCode()}|${syncKeyBase64.hashCode()}|$tokenServerUrl"
        synchronized(authCacheLock) {
            val hit = cachedAuth
            if (hit != null && hit.cacheKey == key && (nowNano - hit.createdAtNano) in 0L until AUTH_CACHE_TTL_NANOS) {
                return hit.session to hit.keyRing
            }
        }
        val session = exchangeTokenServerCreds(kid, fxaAccessToken, tokenServerUrl)
        val keyRing = fetchCollectionKeys(session, SyncCrypto.fromKSyncBase64(syncKeyBase64))
        synchronized(authCacheLock) {
            cachedAuth = CachedAuthContext(
                cacheKey = key,
                createdAtNano = System.nanoTime(),
                session = session,
                keyRing = keyRing
            )
        }
        return session to keyRing
    }

    suspend fun fetchZenSnapshot(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String
    ): ZenFetchResult = withContext(Dispatchers.IO) {
        val (session, cachedKeyRing) = getOrFetchSessionAndKeys(kid, fxaAccessToken, syncKeyBase64, tokenServerUrl)
        val collections = fetchInfoCollections(session)
        if (!collections.containsKey(ZenSpacesCodec.SPACES_COLLECTION)) {
            return@withContext ZenFetchResult.CollectionAbsent
        }
        val keyRing = cachedKeyRing ?: return@withContext ZenFetchResult.CollectionAbsent

        val prefsState = if (collections.containsKey(ZenSpacesCodec.PREFS_COLLECTION)) {
            val (prefsRecords, _) = fetchCollectionWithLastModified(
                session = session,
                collection = ZenSpacesCodec.PREFS_COLLECTION,
                limit = 100
            )
            val sortedPrefs = prefsRecords.sortedBy {
                (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            }
            val prefsDecrypt = decryptBsoList(sortedPrefs, keyRing.keysFor(ZenSpacesCodec.PREFS_COLLECTION))
            if (prefsDecrypt.failedCount > 0) {
                throw SyncCryptoException(
                    "Could not decrypt ${prefsDecrypt.failedCount} Zen prefs records (possible Sync key mismatch)"
                )
            }
            ZenSpacesCodec.decodePrefs(prefsDecrypt.records.map { it.second })
        } else {
            ZenSpacesCodec.ZenPrefsState()
        }

        val (spaceRecords, _) = fetchCollectionWithLastModified(
            session = session,
            collection = ZenSpacesCodec.SPACES_COLLECTION,
            limit = 2500
        )
        val sortedSpaceRecords = spaceRecords.sortedBy {
            (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
        }
        val decryptResult = decryptBsoList(sortedSpaceRecords, keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION))
        if ("layout" in decryptResult.failedIds) {
            throw SyncCryptoException("Could not decrypt Zen Spaces layout record")
        }
        if (decryptResult.failedCount > 0) {
            throw SyncCryptoException(
                "Could not decrypt ${decryptResult.failedCount} Zen Spaces records (possible Sync key mismatch)"
            )
        }
        val snapshot = ZenSpacesCodec.assembleSnapshot(decryptResult.records, prefsState)
        ZenFetchResult.Success(snapshot)
    }

    suspend fun sendTabToZenSpace(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String,
        workspaceUuid: String,
        url: String,
        title: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            require(
                cleanUrl.startsWith("http://", ignoreCase = true) ||
                    cleanUrl.startsWith("https://", ignoreCase = true)
            ) { "Only http:// and https:// URLs can be sent to Zen Space" }

            val (session, cachedKeyRing) = getOrFetchSessionAndKeys(kid, fxaAccessToken, syncKeyBase64, tokenServerUrl)
            val keyRing = cachedKeyRing
                ?: throw IOException("Sync encryption keys are not initialized on this account")
            val keys = keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION)

            val metaPayload = fetchMetaGlobalPayload(session)
            check(ZenSpacesCodec.metaGlobalAllowsSpacesWrite(metaPayload, json)) {
                "Zen Spaces write blocked by incompatible or missing meta/global record"
            }

            var attempts = 0
            while (true) {
                attempts++
                val (records, lastModified) = fetchCollectionWithLastModified(
                    session,
                    ZenSpacesCodec.SPACES_COLLECTION
                )
                val decryptResult = decryptBsoList(records, keys)
                val decrypted = decryptResult.records

                val newTabId = UUID.randomUUID().toString().lowercase(Locale.ROOT)
                val spaceCleartext = decrypted.firstOrNull { (_, obj) ->
                    !ZenSpacesCodec.isDeletedTombstone(obj) &&
                        (obj["kind"] as? JsonPrimitive)?.content == "space" &&
                        ((obj["data"] as? JsonObject)?.get("uuid") as? JsonPrimitive)?.content == workspaceUuid
                }
                check(spaceCleartext != null) {
                    "Target Zen Space was not found on Sync server"
                }

                val spaceData = spaceCleartext.second["data"] as? JsonObject ?: JsonObject(emptyMap())
                val containerGuid = (spaceData["containerGuid"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val newTabCleartext = ZenSpacesCodec.encodeNewTabRecord(
                    tabId = newTabId,
                    workspaceUuid = workspaceUuid,
                    url = cleanUrl,
                    title = title,
                    essential = false,
                    containerGuid = containerGuid
                )

                val outgoingRecords = mutableListOf<JsonObject>()
                outgoingRecords += buildJsonObject {
                    put("id", JsonPrimitive(newTabId))
                    put(
                        "payload",
                        JsonPrimitive(SyncCrypto.encryptBSO(newTabCleartext.toString().toByteArray(Charsets.UTF_8), keys))
                    )
                }

                val updatedSpace = rewriteSpaceWithChild(spaceCleartext.second, newTabId)
                outgoingRecords += buildJsonObject {
                    put("id", JsonPrimitive(spaceCleartext.first))
                    put(
                        "payload",
                        JsonPrimitive(SyncCrypto.encryptBSO(updatedSpace.toString().toByteArray(Charsets.UTF_8), keys))
                    )
                }

                val postBody = JsonArray(outgoingRecords).toString().toByteArray(Charsets.UTF_8)
                val extraHeaders = if (!lastModified.isNullOrBlank()) {
                    mapOf("X-If-Unmodified-Since" to lastModified)
                } else {
                    emptyMap()
                }
                val resp = requestHawk(
                    session = session,
                    method = "POST",
                    path = "/storage/${ZenSpacesCodec.SPACES_COLLECTION}",
                    bodyBytes = postBody,
                    extraHeaders = extraHeaders,
                    allowCodes = setOf(412)
                )
                if (resp.code == 412) {
                    if (attempts < 2) continue
                    throw IOException("Sync conflict while updating Zen Space (412)")
                }
                if (resp.code !in 200..299) {
                    throw IOException("Sync upload failed (${resp.code})")
                }
                val respObj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull()
                val failed = respObj?.get("failed") as? JsonObject
                if (failed != null && failed.isNotEmpty()) {
                    throw IOException("Sync upload partial failure: $failed")
                }
                break
            }
            Result.success(Unit)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun rewriteSpaceWithChild(existing: JsonObject, newTabId: String): JsonObject {
        val dataObj = existing["data"] as? JsonObject ?: JsonObject(emptyMap())
        val currentChildren = (dataObj["children"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()
        val updatedChildren = (currentChildren + newTabId).distinct()

        val newData = buildJsonObject {
            for ((k, v) in dataObj) {
                if (k != "children") put(k, v)
            }
            put("children", buildJsonArray { updatedChildren.forEach { add(JsonPrimitive(it)) } })
        }
        return buildJsonObject {
            for ((k, v) in existing) {
                if (k != "data") put(k, v)
            }
            put("data", newData)
        }
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

    private suspend fun fetchInfoCollections(session: TokenServerSession): Map<String, Double> {
        val resp = requestHawk(session, "GET", "/info/collections", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return emptyMap()
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0 }
    }

    private suspend fun fetchMetaGlobalPayload(session: TokenServerSession): String? {
        val resp = requestHawk(session, "GET", "/storage/meta/global", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return null
        return (obj["payload"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private suspend fun fetchCollectionWithLastModified(
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

    private fun decryptBsoList(
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

    private suspend fun requestHawk(
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
                    val out = java.io.ByteArrayOutputStream()
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
    }
}
