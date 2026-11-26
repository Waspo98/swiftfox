package app.overbay.swiftfox.features.adblock

import android.content.Context
import app.overbay.swiftfox.data.db.FilterListDao
import app.overbay.swiftfox.data.db.FilterListEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages built-in and custom adblock filter list downloads, caching under
 * `filesDir/adblock/lists/<id>.txt`, Room persistence, and `AdblockEngine` recompilation.
 */
class FilterListRepository(
    context: Context,
    private val dao: FilterListDao,
    private val engine: AdblockEngine,
    private val clientProvider: () -> OkHttpClient
) {
    constructor(
        context: Context,
        dao: FilterListDao,
        engine: AdblockEngine,
        client: OkHttpClient = defaultHttpClient()
    ) : this(context, dao, engine, { client })

    private val client: OkHttpClient
        get() = clientProvider()
    private val appContext = context.applicationContext ?: context
    // Serializes network sync passes and structural add/delete mutations so deleted custom lists
    // cannot be resurrected by an in-flight download or duplicated by double-taps.
    private val bulkUpdateMutex = Mutex()

    suspend fun updateAllEnabled(): Int = bulkUpdateMutex.withLock {
        val enabled = dao.getEnabled()
        var checkedOrUpdated = 0
        var anyContentChanged = false
        var firstFailure: Throwable? = null

        enabled.forEach { entity ->
            val outcome = downloadAndPersistList(entity)
            outcome.onSuccess { result ->
                checkedOrUpdated++
                if (result.contentChanged) {
                    anyContentChanged = true
                }
            }.onFailure { err ->
                if (firstFailure == null) firstFailure = err
            }
        }

        if (anyContentChanged || !engine.hasSerializedCache()) {
            val allLists = dao.getAll()
            engine.rebuildFromLists(
                enabledLists = allLists.filter { it.enabled },
                hasAnyConfiguredLists = allLists.isNotEmpty()
            )
        }
        val failure = firstFailure
        if (enabled.isNotEmpty() && checkedOrUpdated == 0 && failure != null) {
            throw failure
        }
        checkedOrUpdated
    }

    suspend fun addCustomList(name: String, url: String): Result<FilterListEntity> {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Filter list name cannot be empty"))
        }
        val parsedUrl = cleanUrl.toHttpUrlOrNull()
        if (parsedUrl == null || (parsedUrl.scheme != "http" && parsedUrl.scheme != "https")) {
            return Result.failure(IllegalArgumentException("Filter list URL must use http:// or https://"))
        }

        val existingLists = dao.getAll()
        if (existingLists.count { !it.isBuiltIn } >= MAX_CUSTOM_FILTER_LISTS) {
            return Result.failure(IllegalArgumentException("Maximum of $MAX_CUSTOM_FILTER_LISTS custom filter lists reached"))
        }
        if (existingLists.any { it.url.trim().equals(cleanUrl, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("This filter list URL is already added"))
        }

        val draft = FilterListEntity(
            id = "fl_custom_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            name = cleanName,
            url = cleanUrl,
            enabled = true,
            isBuiltIn = false
        )

        val downloadResult = fetchList(draft)
        return downloadResult.fold(
            onSuccess = { fetched ->
                val content = fetched.content
                if (content.isNullOrBlank()) {
                    return@fold Result.failure(IllegalStateException("Unexpected empty response for custom list"))
                }
                bulkUpdateMutex.withLock {
                    val latestAll = dao.getAll()
                    if (latestAll.any { it.url.trim().equals(cleanUrl, ignoreCase = true) }) {
                        return@withLock Result.failure(IllegalArgumentException("This filter list URL is already added"))
                    }
                    if (latestAll.count { !it.isBuiltIn } >= MAX_CUSTOM_FILTER_LISTS) {
                        return@withLock Result.failure(IllegalArgumentException("Maximum of $MAX_CUSTOM_FILTER_LISTS custom filter lists reached"))
                    }
                    val written = withContext(Dispatchers.IO) {
                        writeAtomic(listFile(draft.id), content)
                    }
                    if (!written) {
                        return@withLock Result.failure(IllegalStateException("Could not save filter list to storage"))
                    }
                    val saved = draft.copy(
                        ruleCount = AdblockEngine.countFilterRules(content),
                        lastUpdatedAt = System.currentTimeMillis(),
                        etag = fetched.etag,
                        lastError = null
                    )
                    dao.upsert(saved)
                    val allLists = dao.getAll()
                    engine.rebuildFromLists(
                        enabledLists = allLists.filter { it.enabled },
                        hasAnyConfiguredLists = allLists.isNotEmpty()
                    )
                    Result.success(saved)
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    suspend fun toggleList(id: String, enabled: Boolean): Result<FilterListEntity?> = bulkUpdateMutex.withLock {
        val existing = dao.getById(id) ?: return@withLock Result.success(null)
        dao.upsert(existing.copy(enabled = enabled, lastError = if (enabled) existing.lastError else null))
        var downloadFailure: Throwable? = null
        if (enabled && (existing.lastUpdatedAt == 0L || !withContext(Dispatchers.IO) { listFile(existing.id).isFile })) {
            downloadAndPersistList(existing.copy(enabled = true)).onFailure { err ->
                downloadFailure = err
            }
        }
        val allLists = dao.getAll()
        engine.rebuildFromLists(
            enabledLists = allLists.filter { it.enabled },
            hasAnyConfiguredLists = allLists.isNotEmpty()
        )
        val latest = dao.getById(id)
        val err = downloadFailure
        if (err != null) {
            Result.failure(err)
        } else {
            Result.success(latest)
        }
    }

    suspend fun deleteCustomList(id: String) = bulkUpdateMutex.withLock {
        val existing = dao.getById(id) ?: return@withLock
        if (existing.isBuiltIn) return@withLock
        withContext(Dispatchers.IO) {
            listFile(id).delete()
        }
        dao.deleteById(id)
        val allLists = dao.getAll()
        engine.rebuildFromLists(
            enabledLists = allLists.filter { it.enabled },
            hasAnyConfiguredLists = allLists.isNotEmpty()
        )
    }

    /**
     * Applies synced filter list additions, enabled-state updates, and custom-list deletions
     * under [bulkUpdateMutex], fetching missing cache files for newly enabled lists and rebuilding
     * [AdblockEngine] when any rules changed.
     */
    suspend fun applySyncedFilterLists(
        upserts: List<FilterListEntity>,
        deleteCustomIds: List<String>
    ) {
        if (upserts.isEmpty() && deleteCustomIds.isEmpty()) return
        val enabledMissingFiles = bulkUpdateMutex.withLock {
            for (id in deleteCustomIds) {
                val existing = dao.getById(id) ?: continue
                if (!existing.isBuiltIn) {
                    withContext(Dispatchers.IO) { listFile(id).delete() }
                    dao.deleteById(id)
                }
            }
            if (upserts.isNotEmpty()) {
                dao.upsertAll(upserts)
            }
            val allLists = dao.getAll()
            engine.rebuildFromLists(
                enabledLists = allLists.filter { it.enabled },
                hasAnyConfiguredLists = allLists.isNotEmpty()
            )
            withContext(Dispatchers.IO) {
                allLists.filter { it.enabled && (it.lastUpdatedAt == 0L || !listFile(it.id).isFile) }
            }
        }
        if (enabledMissingFiles.isNotEmpty()) {
            var anyDownloaded = false
            bulkUpdateMutex.withLock {
                for (entity in enabledMissingFiles) {
                    val latest = dao.getById(entity.id) ?: continue
                    if (!latest.enabled) continue
                    downloadAndPersistList(latest).onSuccess { outcome ->
                        if (outcome.contentChanged) anyDownloaded = true
                    }
                }
                if (anyDownloaded) {
                    val allLists = dao.getAll()
                    engine.rebuildFromLists(
                        enabledLists = allLists.filter { it.enabled },
                        hasAnyConfiguredLists = allLists.isNotEmpty()
                    )
                }
            }
        }
    }

    suspend fun syncIfNeverUpdated() {
        val enabled = dao.getEnabled()
        val needsInitialSync = withContext(Dispatchers.IO) {
            enabled.any { list ->
                list.isBuiltIn && (list.lastUpdatedAt == 0L || !listFile(list.id).isFile)
            }
        }
        if (needsInitialSync) {
            try {
                updateAllEnabled()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Starter filters remain active; periodic WorkManager sync will retry.
            }
        }
    }

    private data class PersistOutcome(
        val contentChanged: Boolean
    )

    private suspend fun downloadAndPersistList(entity: FilterListEntity): Result<PersistOutcome> {
        val now = System.currentTimeMillis()
        return try {
            val fetchedFirst = fetchList(entity).getOrThrow()
            val latest = dao.getById(entity.id)
                ?: throw IllegalStateException("Filter list was deleted during sync")
            val fileExists = withContext(Dispatchers.IO) { listFile(latest.id).isFile }
            val fetched = if (fetchedFirst.notModified && !fileExists) {
                fetchList(latest, skipConditionalHeaders = true).getOrThrow()
            } else {
                fetchedFirst
            }

            val outcome = if (fetched.notModified && fileExists) {
                val updated = latest.copy(
                    lastUpdatedAt = now,
                    lastError = null
                )
                dao.upsert(updated)
                PersistOutcome(contentChanged = false)
            } else {
                val content = fetched.content
                if (content.isNullOrBlank()) {
                    throw IllegalStateException("Downloaded empty filter list")
                }
                val written = withContext(Dispatchers.IO) {
                    writeAtomic(listFile(latest.id), content)
                }
                if (!written) {
                    throw IllegalStateException("Could not write filter list cache")
                }
                val updated = latest.copy(
                    ruleCount = AdblockEngine.countFilterRules(content),
                    lastUpdatedAt = now,
                    etag = fetched.etag ?: latest.etag,
                    lastError = null
                )
                dao.upsert(updated)
                PersistOutcome(contentChanged = true)
            }
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            val latest = runCatching { dao.getById(entity.id) }.getOrNull()
            if (latest != null) {
                runCatching {
                    dao.upsert(
                        latest.copy(
                            lastError = error.message?.take(120) ?: "Update failed"
                        )
                    )
                }
            }
            Result.failure(error)
        }
    }

    private data class FetchedList(
        val content: String? = null,
        val etag: String? = null,
        val notModified: Boolean = false
    )

    private suspend fun fetchList(
        entity: FilterListEntity,
        skipConditionalHeaders: Boolean = false
    ): Result<FetchedList> =
        withContext(Dispatchers.IO) {
            try {
                val parsedUrl = entity.url.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Invalid filter list URL")
                if (parsedUrl.scheme != "http" && parsedUrl.scheme != "https") {
                    throw IllegalArgumentException("Unsupported scheme: ${parsedUrl.scheme}")
                }

                val reqBuilder = Request.Builder()
                    .url(parsedUrl)
                    .header("User-Agent", "Swiftfox-Adblock/1.0")
                if (!skipConditionalHeaders && !entity.etag.isNullOrBlank() && listFile(entity.id).isFile) {
                    reqBuilder.header("If-None-Match", entity.etag)
                }

                val call = client.newCall(reqBuilder.build())
                val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { response ->
                        val fetched = when {
                            response.code == 304 -> {
                                FetchedList(
                                    content = null,
                                    etag = response.header("ETag") ?: entity.etag,
                                    notModified = true
                                )
                            }
                            response.isSuccessful -> {
                                val bodyStream = response.body.byteStream()
                                val content = readBoundedUtf8(bodyStream, MAX_FILTER_LIST_BYTES)
                                if (content.isBlank()) {
                                    throw IllegalStateException("Downloaded empty filter list")
                                }
                                FetchedList(
                                    content = content,
                                    etag = response.header("ETag"),
                                    notModified = false
                                )
                            }
                            else -> {
                                throw IllegalStateException("HTTP ${response.code}")
                            }
                        }
                        Result.success(fetched)
                    }
                } finally {
                    cancelHandle?.dispose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun readBoundedUtf8(input: java.io.InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalStateException("Filter list exceeds ${maxBytes / (1024 * 1024)} MB limit")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun listsDir(): File =
        File(File(appContext.filesDir, "adblock"), "lists").apply { mkdirs() }

    private fun listFile(id: String): File =
        File(listsDir(), "$id.txt")

    private fun writeAtomic(target: File, content: String): Boolean {
        val parent = target.parentFile ?: return false
        parent.mkdirs()
        var tmp: File? = null
        return try {
            tmp = File.createTempFile("${target.name}.", ".tmp", parent)
            tmp.writeText(content, Charsets.UTF_8)
            val renamed = if (target.exists()) {
                tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))
            } else {
                tmp.renameTo(target)
            }
            if (!renamed) {
                tmp.delete()
            }
            renamed
        } catch (e: CancellationException) {
            tmp?.delete()
            throw e
        } catch (_: Throwable) {
            tmp?.delete()
            false
        }
    }

    companion object {
        private const val MAX_FILTER_LIST_BYTES = 16 * 1024 * 1024
        internal const val MAX_CUSTOM_FILTER_LISTS = 20

        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
