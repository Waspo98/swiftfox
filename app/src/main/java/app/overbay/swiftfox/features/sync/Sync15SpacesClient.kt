package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val httpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val userAgent = "Swiftfox/${BuildConfig.VERSION_NAME} (Android)"

    private class TokenServerSession(
        val apiEndpoint: String,
        val hawkId: String,
        val hawkKey: ByteArray
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

    suspend fun fetchZenSnapshot(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String
    ): ZenFetchResult = withContext(Dispatchers.IO) {
        val session = exchangeTokenServerCreds(kid, fxaAccessToken, tokenServerUrl)
        val collections = fetchInfoCollections(session)
        if (!collections.containsKey(ZenSpacesCodec.SPACES_COLLECTION)) {
            return@withContext ZenFetchResult.CollectionAbsent
        }
        val keyRing = fetchCollectionKeys(session, SyncCrypto.fromKSyncBase64(syncKeyBase64))
            ?: return@withContext ZenFetchResult.CollectionAbsent

        val prefsState = if (collections.containsKey(ZenSpacesCodec.PREFS_COLLECTION)) {
            val (prefsRecords, _) = fetchCollectionWithLastModified(
                session = session,
                collection = ZenSpacesCodec.PREFS_COLLECTION,
                limit = 100
            )
            val sortedPrefs = prefsRecords.sortedBy {
                (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            }
            val cleartextPrefs = decryptBsoList(sortedPrefs, keyRing.keysFor(ZenSpacesCodec.PREFS_COLLECTION))
                .records
                .map { it.second }
            ZenSpacesCodec.decodePrefs(cleartextPrefs)
        } else {
            ZenSpacesCodec.ZenPrefsState()
        }

        val (spaceRecords, _) = fetchCollectionWithLastModified(
            session = session,
            collection = ZenSpacesCodec.SPACES_COLLECTION,
            limit = 2500
        )
        val decryptResult = decryptBsoList(spaceRecords, keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION))
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
        title: String,
        asEssential: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            require(
                cleanUrl.startsWith("http://", ignoreCase = true) ||
                    cleanUrl.startsWith("https://", ignoreCase = true)
            ) { "Only http:// and https:// URLs can be sent to Zen Space" }

            val session = exchangeTokenServerCreds(kid, fxaAccessToken, tokenServerUrl)
            val keyRing = fetchCollectionKeys(session, SyncCrypto.fromKSyncBase64(syncKeyBase64))
                ?: throw IOException("Sync encryption keys are not initialized on this account")
            val keys = keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION)

            val metaPayload = fetchMetaGlobalPayload(session)
            check(ZenSpacesCodec.metaGlobalAllowsSpacesWrite(metaPayload, json)) {
                "Zen Spaces write blocked by incompatible or missing meta/global record"
            }

            val separateEssentials = if (asEssential) {
                val collections = fetchInfoCollections(session)
                if (collections.containsKey(ZenSpacesCodec.PREFS_COLLECTION)) {
                    val (prefsRecords, _) = fetchCollectionWithLastModified(
                        session = session,
                        collection = ZenSpacesCodec.PREFS_COLLECTION,
                        limit = 100
                    )
                    val sortedPrefs = prefsRecords.sortedBy {
                        (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                    }
                    val cleartextPrefs = decryptBsoList(sortedPrefs, keyRing.keysFor(ZenSpacesCodec.PREFS_COLLECTION))
                        .records
                        .map { it.second }
                    ZenSpacesCodec.decodePrefs(cleartextPrefs).separateEssentials == true
                } else {
                    false
                }
            } else {
                false
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
                val effectiveEssentialBucket = if (separateEssentials) {
                    containerGuid?.takeIf { it.isNotBlank() } ?: "default"
                } else {
                    "default"
                }
                val newTabCleartext = ZenSpacesCodec.encodeNewTabRecord(
                    tabId = newTabId,
                    workspaceUuid = if (asEssential) null else workspaceUuid,
                    url = cleanUrl,
                    title = title,
                    essential = asEssential,
                    containerGuid = if (asEssential) {
                        effectiveEssentialBucket.takeIf { it != "default" }
                    } else {
                        containerGuid
                    }
                )

                val outgoingRecords = mutableListOf<JsonObject>()
                outgoingRecords += buildJsonObject {
                    put("id", JsonPrimitive(newTabId))
                    put(
                        "payload",
                        JsonPrimitive(SyncCrypto.encryptBSO(newTabCleartext.toString().toByteArray(Charsets.UTF_8), keys))
                    )
                }

                if (asEssential) {
                    val layoutEntry = decrypted.firstOrNull { (_, obj) ->
                        !ZenSpacesCodec.isDeletedTombstone(obj) &&
                            (obj["kind"] as? JsonPrimitive)?.content == "layout"
                    }
                    if (layoutEntry == null && ("layout" in decryptResult.failedIds || decryptResult.failedCount > 0)) {
                        throw IOException(
                            "Refusing to overwrite Zen layout because existing layout record could not be decrypted"
                        )
                    }
                    val layoutId = layoutEntry?.first ?: "layout"
                    val updatedLayout = rewriteLayoutWithEssential(
                        existing = layoutEntry?.second,
                        bucket = effectiveEssentialBucket,
                        tabId = newTabId
                    )
                    outgoingRecords += buildJsonObject {
                        put("id", JsonPrimitive(layoutId))
                        put(
                            "payload",
                            JsonPrimitive(SyncCrypto.encryptBSO(updatedLayout.toString().toByteArray(Charsets.UTF_8), keys))
                        )
                    }
                } else {
                    val updatedSpace = rewriteSpaceWithChild(spaceCleartext.second, newTabId)
                    outgoingRecords += buildJsonObject {
                        put("id", JsonPrimitive(spaceCleartext.first))
                        put(
                            "payload",
                            JsonPrimitive(SyncCrypto.encryptBSO(updatedSpace.toString().toByteArray(Charsets.UTF_8), keys))
                        )
                    }
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

    private fun rewriteLayoutWithEssential(
        existing: JsonObject?,
        bucket: String,
        tabId: String
    ): JsonObject {
        val root = existing ?: buildJsonObject {
            put("id", JsonPrimitive("layout"))
            put("kind", JsonPrimitive("layout"))
            put("data", JsonObject(emptyMap()))
        }
        val dataObj = root["data"] as? JsonObject ?: JsonObject(emptyMap())
        val essentialsObj = dataObj["essentials"] as? JsonObject ?: JsonObject(emptyMap())
        val currentBucket = (essentialsObj[bucket] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()
        val updatedBucket = (currentBucket + tabId).distinct()

        val newEssentials = buildJsonObject {
            for ((k, v) in essentialsObj) {
                if (k != bucket) put(k, v)
            }
            put(bucket, buildJsonArray { updatedBucket.forEach { add(JsonPrimitive(it)) } })
        }
        val newData = buildJsonObject {
            for ((k, v) in dataObj) {
                if (k != "essentials") put(k, v)
            }
            put("essentials", newEssentials)
        }
        return buildJsonObject {
            for ((k, v) in root) {
                if (k != "data") put(k, v)
            }
            put("data", newData)
        }
    }

    private fun exchangeTokenServerCreds(
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
        val id = (obj["id"] as? JsonPrimitive)?.content
            ?: throw IOException("Token server missing hawk id")
        val key = (obj["key"] as? JsonPrimitive)?.content
            ?: throw IOException("Token server missing hawk key")
        return TokenServerSession(
            apiEndpoint = endpoint,
            hawkId = id,
            hawkKey = key.toByteArray(Charsets.UTF_8)
        )
    }

    private fun fetchCollectionKeys(
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

    private fun fetchInfoCollections(session: TokenServerSession): Map<String, Double> {
        val resp = requestHawk(session, "GET", "/info/collections", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return emptyMap()
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0 }
    }

    private fun fetchMetaGlobalPayload(session: TokenServerSession): String? {
        val resp = requestHawk(session, "GET", "/storage/meta/global", allowCodes = setOf(404))
        if (resp.code == 404 || resp.body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return null
        return (obj["payload"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun fetchCollectionWithLastModified(
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
                if (nextOffset.isNullOrBlank() || nextOffset == offset) break
                offset = nextOffset
            }
            if (!conflicted) return out to lastModified
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

    private fun requestHawk(
        session: TokenServerSession,
        method: String,
        path: String,
        bodyBytes: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        allowCodes: Set<Int> = emptySet()
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
            resource = resource
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
        if (resp.code !in 200..299 && resp.code !in allowCodes) {
            throw IOException("Sync storage HTTP ${resp.code} on $path")
        }
        return resp
    }

    private fun executeRaw(request: Request): HttpResult {
        httpClient.newCall(request).execute().use { response ->
            val headers = linkedMapOf<String, String>()
            for (name in response.headers.names()) {
                response.header(name)?.let { headers[name] = it }
            }
            val body = response.body.string()
            return HttpResult(response.code, body, headers)
        }
    }

    companion object {
        private const val SYNC_15_TOKEN_SUFFIX = "/1.0/sync/1.5"

        /**
         * Matches Mozilla `sync15/src/client/token.rs::fixup_server_url`:
         * `FirefoxAccount.getTokenServerEndpointURL()` returns the bare base URL
         * (e.g. `https://token.services.mozilla.com/`), so append `/1.0/sync/1.5`
         * unless already present.
         */
        fun resolveTokenServerUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) throw IOException("Empty token server URL")
            return if (trimmed.endsWith(SYNC_15_TOKEN_SUFFIX, ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed$SYNC_15_TOKEN_SUFFIX"
            }
        }
    }
}
