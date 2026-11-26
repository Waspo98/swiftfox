package app.overbay.swiftfox.features.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import java.io.IOException
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
class Sync15SpacesClient internal constructor(
    private val transport: Sync15Transport
) {
    constructor(httpClientProvider: () -> OkHttpClient) : this(Sync15Transport(httpClientProvider))
    constructor(httpClient: OkHttpClient) : this(Sync15Transport { httpClient })

    fun invalidateAuthCache() {
        transport.invalidateAuthCache()
    }

    suspend fun fetchZenSnapshot(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String
    ): ZenFetchResult = withContext(Dispatchers.IO) {
        val cacheKey = Sync15Transport.AuthCacheKey(kid, fxaAccessToken, syncKeyBase64, tokenServerUrl)
        val (session, cachedKeyRing) = transport.getOrFetchSession(cacheKey)
        val collections = transport.fetchInfoCollections(session)
        if (!collections.containsKey(ZenSpacesCodec.SPACES_COLLECTION)) {
            return@withContext ZenFetchResult.CollectionAbsent
        }
        val keyRing = transport.getOrFetchCollectionKeyRing(cacheKey, session, cachedKeyRing)
            ?: return@withContext ZenFetchResult.CollectionAbsent

        val prefsState = if (collections.containsKey(ZenSpacesCodec.PREFS_COLLECTION)) {
            val (prefsRecords, _) = transport.fetchCollectionWithLastModified(
                session = session,
                collection = ZenSpacesCodec.PREFS_COLLECTION,
                limit = 100
            )
            val sortedPrefs = prefsRecords.sortedBy {
                (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            }
            val prefsDecrypt = transport.decryptBsoList(
                sortedPrefs,
                keyRing.keysFor(ZenSpacesCodec.PREFS_COLLECTION)
            )
            if (prefsDecrypt.failedCount > 0) {
                throw SyncCryptoException(
                    "Could not decrypt ${prefsDecrypt.failedCount} Zen prefs records (possible Sync key mismatch)"
                )
            }
            ZenSpacesCodec.decodePrefs(prefsDecrypt.records.map { it.second })
        } else {
            ZenSpacesCodec.ZenPrefsState()
        }

        val (spaceRecords, _) = transport.fetchCollectionWithLastModified(
            session = session,
            collection = ZenSpacesCodec.SPACES_COLLECTION,
            limit = 2500
        )
        val sortedSpaceRecords = spaceRecords.sortedBy {
            (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
        }
        val decryptResult = transport.decryptBsoList(
            sortedSpaceRecords,
            keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION)
        )
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

            val cacheKey = Sync15Transport.AuthCacheKey(kid, fxaAccessToken, syncKeyBase64, tokenServerUrl)
            val (session, cachedKeyRing) = transport.getOrFetchSession(cacheKey)
            val keyRing = transport.getOrFetchCollectionKeyRing(cacheKey, session, cachedKeyRing)
                ?: throw IOException("Sync encryption keys are not initialized on this account")
            val keys = keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION)

            val metaPayload = transport.fetchMetaGlobalPayload(session)
            check(ZenSpacesCodec.metaGlobalAllowsSpacesWrite(metaPayload, transport.json)) {
                "Zen Spaces write blocked by incompatible or missing meta/global record"
            }

            var attempts = 0
            while (true) {
                attempts++
                val (records, lastModified) = transport.fetchCollectionWithLastModified(
                    session,
                    ZenSpacesCodec.SPACES_COLLECTION
                )
                val decryptResult = transport.decryptBsoList(records, keys)
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
                val updatedSpace = rewriteSpaceWithChild(spaceCleartext.second, newTabId)

                when (
                    transport.postEncryptedBsosChunked(
                        session = session,
                        collection = ZenSpacesCodec.SPACES_COLLECTION,
                        keys = keys,
                        records = listOf(
                            newTabId to newTabCleartext,
                            spaceCleartext.first to updatedSpace
                        ),
                        initialIfUnmodifiedSince = lastModified
                    )
                ) {
                    is Sync15Transport.PostChunkResult.Success -> break
                    Sync15Transport.PostChunkResult.Conflict -> {
                        if (attempts < 2) continue
                        throw IOException("Sync conflict while updating Zen Space (412)")
                    }
                }
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
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

    companion object {
        fun resolveTokenServerUrl(rawUrl: String): String =
            Sync15Transport.resolveTokenServerUrl(rawUrl)
    }
}
