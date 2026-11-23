package app.overbay.swiftfox.features.adblock

import android.content.Context
import app.overbay.swiftfox.data.db.FilterListDao
import app.overbay.swiftfox.data.db.FilterListEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages built-in and custom adblock filter list downloads, caching under
 * `filesDir/adblock/lists/<id>.txt`, Room persistence, and `AdblockEngine` recompilation.
 */
class FilterListRepository(
    private val context: Context,
    private val dao: FilterListDao,
    private val engine: AdblockEngine,
    private val client: OkHttpClient = defaultHttpClient()
) {
    private val appContext = context.applicationContext ?: context
    private val syncMutex = Mutex()

    suspend fun updateAllEnabled(): Int = syncMutex.withLock {
        val enabled = dao.getEnabled()
        var checkedOrUpdated = 0

        enabled.forEach { entity ->
            val result = downloadAndPersistList(entity)
            if (result.isSuccess) {
                checkedOrUpdated++
            }
        }

        engine.rebuildFromLists(dao.getEnabled())
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

        val draft = FilterListEntity(
            id = "fl_custom_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            name = cleanName,
            url = cleanUrl,
            enabled = true,
            isBuiltIn = false
        )

        return syncMutex.withLock {
            val downloadResult = fetchList(draft)
            downloadResult.fold(
                onSuccess = { fetched ->
                    if (fetched.content == null) {
                        return@fold Result.failure(IllegalStateException("Unexpected empty response for custom list"))
                    }
                    val targetFile = listFile(draft.id)
                    writeAtomic(targetFile, fetched.content)
                    val saved = draft.copy(
                        ruleCount = AdblockEngine.countFilterRules(fetched.content),
                        lastUpdatedAt = System.currentTimeMillis(),
                        etag = fetched.etag,
                        lastError = null
                    )
                    dao.upsert(saved)
                    engine.rebuildFromLists(dao.getEnabled())
                    Result.success(saved)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        }
    }

    suspend fun toggleList(id: String, enabled: Boolean) {
        syncMutex.withLock {
            val existing = dao.getById(id) ?: return
            dao.upsert(existing.copy(enabled = enabled))
            if (enabled && !listFile(existing.id).isFile) {
                downloadAndPersistList(existing.copy(enabled = true))
            }
            engine.rebuildFromLists(dao.getEnabled())
        }
    }

    suspend fun deleteCustomList(id: String) {
        syncMutex.withLock {
            val existing = dao.getById(id) ?: return
            if (existing.isBuiltIn) return
            listFile(id).delete()
            File(listsDir(), "$id.txt.tmp").delete()
            dao.deleteById(id)
            engine.rebuildFromLists(dao.getEnabled())
        }
    }

    suspend fun syncIfNeverUpdated() {
        val enabled = dao.getEnabled()
        val needsInitialSync = enabled.any { list ->
            list.isBuiltIn && (list.lastUpdatedAt == 0L || !listFile(list.id).isFile)
        }
        if (needsInitialSync) {
            updateAllEnabled()
        }
    }

    private suspend fun downloadAndPersistList(entity: FilterListEntity): Result<FilterListEntity> {
        val now = System.currentTimeMillis()
        return fetchList(entity).fold(
            onSuccess = { fetched ->
                val updated = if (fetched.notModified && listFile(entity.id).isFile) {
                    entity.copy(
                        lastUpdatedAt = now,
                        lastError = null
                    )
                } else {
                    val content = fetched.content.orEmpty()
                    writeAtomic(listFile(entity.id), content)
                    entity.copy(
                        ruleCount = AdblockEngine.countFilterRules(content),
                        lastUpdatedAt = now,
                        etag = fetched.etag ?: entity.etag,
                        lastError = null
                    )
                }
                dao.upsert(updated)
                Result.success(updated)
            },
            onFailure = { error ->
                dao.upsert(
                    entity.copy(
                        lastError = error.message?.take(120) ?: "Update failed"
                    )
                )
                Result.failure(error)
            }
        )
    }

    private data class FetchedList(
        val content: String? = null,
        val etag: String? = null,
        val notModified: Boolean = false
    )

    private suspend fun fetchList(entity: FilterListEntity): Result<FetchedList> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsedUrl = entity.url.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Invalid filter list URL")
                if (parsedUrl.scheme != "http" && parsedUrl.scheme != "https") {
                    throw IllegalArgumentException("Unsupported scheme: ${parsedUrl.scheme}")
                }

                val reqBuilder = Request.Builder()
                    .url(parsedUrl)
                    .header("User-Agent", "Swiftfox-Adblock/1.0")
                if (!entity.etag.isNullOrBlank() && listFile(entity.id).isFile) {
                    reqBuilder.header("If-None-Match", entity.etag)
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    when {
                        response.code == 304 -> {
                            FetchedList(
                                content = null,
                                etag = response.header("ETag") ?: entity.etag,
                                notModified = true
                            )
                        }
                        response.isSuccessful -> {
                            val body = response.body?.string().orEmpty()
                            if (body.isBlank()) {
                                throw IllegalStateException("Downloaded empty filter list")
                            }
                            FetchedList(
                                content = body,
                                etag = response.header("ETag"),
                                notModified = false
                            )
                        }
                        else -> {
                            throw IllegalStateException("HTTP ${response.code}")
                        }
                    }
                }
            }
        }

    private fun listsDir(): File =
        File(File(appContext.filesDir, "adblock"), "lists").apply { mkdirs() }

    private fun listFile(id: String): File =
        File(listsDir(), "$id.txt")

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists() && !tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        } else if (!target.exists()) {
            tmp.renameTo(target)
        }
    }

    companion object {
        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
