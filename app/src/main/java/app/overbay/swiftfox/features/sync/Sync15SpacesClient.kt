package app.overbay.swiftfox.features.sync

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import java.io.File
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

            mutateSpacesCollectionWithRetry(
                kid = kid,
                fxaAccessToken = fxaAccessToken,
                syncKeyBase64 = syncKeyBase64,
                tokenServerUrl = tokenServerUrl,
                requireCollectionExists = true,
                conflictErrorMessage = "Sync conflict while updating Zen Space (412)"
            ) { decrypted ->
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
                listOf(
                    newTabId to newTabCleartext,
                    spaceCleartext.first to updatedSpace
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun applyPendingMutations(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String,
        ops: List<ZenSpacesCodec.ZenPendingOp>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (ops.isEmpty()) return@withContext Result.success(0)
        try {
            val written = mutateSpacesCollectionWithRetry(
                kid = kid,
                fxaAccessToken = fxaAccessToken,
                syncKeyBase64 = syncKeyBase64,
                tokenServerUrl = tokenServerUrl,
                requireCollectionExists = false,
                conflictErrorMessage = "Sync conflict while pushing Zen pinned edits (412)"
            ) { decrypted ->
                ZenSpacesCodec.buildMutationBsos(decrypted, ops)
            }
            Result.success(written)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun mutateSpacesCollectionWithRetry(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String,
        requireCollectionExists: Boolean,
        conflictErrorMessage: String,
        buildUpdates: (decrypted: List<Pair<String, JsonObject>>) -> List<Pair<String, JsonObject>>
    ): Int {
        val cacheKey = Sync15Transport.AuthCacheKey(kid, fxaAccessToken, syncKeyBase64, tokenServerUrl)
        val (session, cachedKeyRing) = transport.getOrFetchSession(cacheKey)
        if (!requireCollectionExists) {
            val collections = transport.fetchInfoCollections(session)
            if (!collections.containsKey(ZenSpacesCodec.SPACES_COLLECTION)) {
                return 0
            }
        }
        val keyRing = transport.getOrFetchCollectionKeyRing(cacheKey, session, cachedKeyRing)
            ?: throw IOException("Sync encryption keys are not initialized on this account")
        val keys = keyRing.keysFor(ZenSpacesCodec.SPACES_COLLECTION)
        val limits = transport.fetchInfoConfiguration(session)

        val metaPayload = transport.fetchMetaGlobalPayload(session)
        check(ZenSpacesCodec.metaGlobalAllowsSpacesWrite(metaPayload, transport.json)) {
            "Zen Spaces write blocked by incompatible or missing meta/global record"
        }

        for (attempt in 1..2) {
            val (records, lastModified) = transport.fetchCollectionWithLastModified(
                session = session,
                collection = ZenSpacesCodec.SPACES_COLLECTION,
                limit = 2500
            )
            val sortedRecords = records.sortedBy {
                (it["modified"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            }
            val decryptResult = transport.decryptBsoList(sortedRecords, keys)
            if (decryptResult.failedCount > 0) {
                throw SyncCryptoException(
                    "Could not decrypt ${decryptResult.failedCount} Zen Spaces records (possible Sync key mismatch)"
                )
            }
            val outgoing = buildUpdates(decryptResult.records)
            if (outgoing.isEmpty()) return 0

            when (
                transport.postEncryptedBsosChunked(
                    session = session,
                    collection = ZenSpacesCodec.SPACES_COLLECTION,
                    keys = keys,
                    records = outgoing,
                    initialIfUnmodifiedSince = lastModified,
                    limits = limits
                )
            ) {
                is Sync15Transport.PostChunkResult.Success -> return outgoing.size
                Sync15Transport.PostChunkResult.Conflict -> {
                    if (attempt < 2) continue
                    throw IOException(conflictErrorMessage)
                }
            }
        }
        throw IOException(conflictErrorMessage)
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

internal class ZenSpacesPendingOpsStore internal constructor(
    private val storeFileProvider: () -> File,
    private val json: Json
) {
    constructor(context: Context, json: Json) : this(
        storeFileProvider = { File(context.applicationContext.filesDir, OPS_FILE_NAME) },
        json = json
    )

    internal constructor(storeFile: File, json: Json) : this(
        storeFileProvider = { storeFile },
        json = json
    )

    // Lock ordering: always acquire diskLock before lock when both are needed.
    private val diskLock = Any()
    private val lock = Any()
    private val cachedOps = mutableListOf<ZenSpacesCodec.ZenPendingOp>()
    private val bufferedBeforeLoad = mutableListOf<ZenSpacesCodec.ZenPendingOp>()
    private var loadedFromDisk = false

    private fun storeFile(): File = storeFileProvider()

    fun enqueueBuffered(op: ZenSpacesCodec.ZenPendingOp) {
        synchronized(lock) {
            if (loadedFromDisk) {
                val next = ZenSpacesCodec.coalescePendingOps(cachedOps, op)
                cachedOps.clear()
                cachedOps.addAll(next)
            } else {
                bufferedBeforeLoad += op
            }
        }
    }

    fun persistPendingToDisk() {
        synchronized(diskLock) {
            val snapshotToSave = synchronized(lock) {
                ensureLoadedLocked()
                cachedOps.toList()
            }
            writeAtomicOps(snapshotToSave)
        }
    }

    fun snapshotPending(): List<ZenSpacesCodec.ZenPendingOp> {
        synchronized(diskLock) {
            var hadBuffered = false
            val snapshot = synchronized(lock) {
                hadBuffered = bufferedBeforeLoad.isNotEmpty()
                ensureLoadedLocked()
                cachedOps.toList()
            }
            if (hadBuffered) {
                writeAtomicOps(snapshot)
            }
            return snapshot
        }
    }

    fun hasPending(): Boolean = synchronized(lock) {
        if (loadedFromDisk) cachedOps.isNotEmpty() else bufferedBeforeLoad.isNotEmpty() || storeFile().isFile
    }

    fun removeApplied(applied: List<ZenSpacesCodec.ZenPendingOp>) {
        if (applied.isEmpty()) return
        synchronized(diskLock) {
            val remaining = synchronized(lock) {
                ensureLoadedLocked()
                val toRemove = applied.toMutableList()
                val iterator = cachedOps.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    val idx = toRemove.indexOf(item)
                    if (idx >= 0) {
                        iterator.remove()
                        toRemove.removeAt(idx)
                    }
                }
                cachedOps.toList()
            }
            writeAtomicOps(remaining)
        }
    }

    fun clear() {
        synchronized(diskLock) {
            synchronized(lock) {
                bufferedBeforeLoad.clear()
                cachedOps.clear()
                loadedFromDisk = true
            }
            runCatching { storeFile().delete() }
        }
    }

    private fun ensureLoadedLocked() {
        if (loadedFromDisk) return
        val file = storeFile()
        val diskOps = if (file.isFile) {
            runCatching {
                json.decodeFromString<List<ZenSpacesCodec.ZenPendingOp>>(file.readText(Charsets.UTF_8))
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        var merged = diskOps
        for (op in bufferedBeforeLoad) {
            merged = ZenSpacesCodec.coalescePendingOps(merged, op)
        }
        bufferedBeforeLoad.clear()
        cachedOps.clear()
        cachedOps.addAll(merged)
        loadedFromDisk = true
    }

    private fun writeAtomicOps(ops: List<ZenSpacesCodec.ZenPendingOp>) {
        val target = storeFile()
        if (ops.isEmpty()) {
            runCatching { target.delete() }
            return
        }
        val parent = target.parentFile ?: return
        parent.mkdirs()
        val payload = json.encodeToString(ops)
        var tmp: File? = null
        try {
            tmp = File.createTempFile("${target.name}.", ".tmp", parent)
            tmp.writeText(payload, Charsets.UTF_8)
            val renamed = if (target.exists()) {
                tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))
            } else {
                tmp.renameTo(target)
            }
            if (!renamed) tmp.delete()
        } catch (ce: CancellationException) {
            tmp?.delete()
            throw ce
        } catch (_: Throwable) {
            tmp?.delete()
        }
    }

    companion object {
        private const val OPS_FILE_NAME = "zen_spaces_pending_ops.json"
    }
}
