package app.overbay.swiftfox.features.userscripts

import app.overbay.swiftfox.data.db.UserscriptDao
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.UserscriptStorageDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages persistence, remote/local installation, updates, and URL/phase matching for `.user.js` scripts.
 */
class UserscriptRepository internal constructor(
    private val scriptDao: UserscriptDao,
    private val storageDao: UserscriptStorageDao,
    private val clientProvider: () -> OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    constructor(
        scriptDao: UserscriptDao,
        storageDao: UserscriptStorageDao,
        client: OkHttpClient,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(scriptDao, storageDao, { client }, ioDispatcher)

    constructor(
        scriptDao: UserscriptDao,
        storageDao: UserscriptStorageDao,
        clientProvider: () -> OkHttpClient = { defaultHttpClient() }
    ) : this(scriptDao, storageDao, clientProvider, Dispatchers.IO)

    private data class CompiledUserscript(
        val entity: UserscriptEntity,
        val runAt: UserscriptRunAt,
        val matches: List<String>,
        val excludes: List<String>
    )

    private val cacheMutex = Mutex()

    @Volatile
    private var cachedEnabledScripts: List<CompiledUserscript>? = null

    fun invalidateRuntimeCache() {
        cachedEnabledScripts = null
    }

    private suspend fun getCompiledEnabledScripts(): List<CompiledUserscript> {
        cachedEnabledScripts?.let { return it }
        return cacheMutex.withLock {
            cachedEnabledScripts?.let { return@withLock it }
            val loaded = scriptDao.getEnabled().map { script ->
                CompiledUserscript(
                    entity = script,
                    runAt = UserscriptRunAt.fromHeader(script.runAt),
                    matches = UserscriptParser.decodeStringList(script.matchesJson),
                    excludes = UserscriptParser.decodeStringList(script.excludesJson)
                )
            }
            cachedEnabledScripts = loaded
            loaded
        }
    }

    suspend fun installFromUrl(url: String): Result<UserscriptEntity> {
        val trimmedUrl = url.trim()
        if (!UserscriptUrlMatcher.isHttpOrHttps(trimmedUrl)) {
            return Result.failure(IllegalArgumentException("Userscript URL must use http:// or https://"))
        }
        val httpUrl = trimmedUrl.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid userscript URL"))

        return try {
            val body = withContext(ioDispatcher) {
                val request = Request.Builder()
                    .url(httpUrl)
                    .get()
                    .header("Accept", "text/javascript, application/javascript, text/plain, */*")
                    .build()
                val call = clientProvider().newCall(request)
                val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                        }
                        readBoundedText(response.body, MAX_USERSCRIPT_BYTES)
                    }
                } finally {
                    cancelHandle?.dispose()
                }
            }
            val derivedName = httpUrl.pathSegments
                .lastOrNull { it.isNotBlank() }
                ?.removeSuffix(".user.js")
                ?.removeSuffix(".js")
                ?.takeIf { it.isNotBlank() }
                ?: httpUrl.host
            installFromCode(code = body, fallbackName = derivedName, sourceUrl = trimmedUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun installFromCode(
        code: String,
        fallbackName: String = "Custom Userscript",
        sourceUrl: String? = null
    ): Result<UserscriptEntity> {
        if (code.isBlank()) {
            return Result.failure(IllegalArgumentException("Userscript code cannot be empty."))
        }
        if (code.toByteArray(Charsets.UTF_8).size > MAX_USERSCRIPT_BYTES) {
            return Result.failure(IllegalArgumentException("Userscript exceeds 2 MB size limit."))
        }
        return try {
            val meta = UserscriptParser.parse(code, fallbackName = fallbackName)
            val normalizedSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
            val existing = if (normalizedSourceUrl != null) {
                scriptDao.findBySourceUrl(normalizedSourceUrl)
            } else {
                null
            }
            val now = System.currentTimeMillis()
            val entity = UserscriptEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = meta.name,
                namespace = meta.namespace,
                version = meta.version,
                description = meta.description,
                sourceUrl = normalizedSourceUrl,
                code = code,
                matchesJson = UserscriptParser.encodeStringList(meta.matches),
                excludesJson = UserscriptParser.encodeStringList(meta.excludes),
                runAt = meta.runAt.headerValue,
                grantsJson = UserscriptParser.encodeStringList(meta.grants),
                enabled = existing?.enabled ?: true,
                installedAt = existing?.installedAt ?: now,
                updatedAt = now
            )
            scriptDao.upsert(entity)
            invalidateRuntimeCache()
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun updateCode(id: String, newCode: String): Result<UserscriptEntity> {
        if (newCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Userscript code cannot be empty."))
        }
        if (newCode.toByteArray(Charsets.UTF_8).size > MAX_USERSCRIPT_BYTES) {
            return Result.failure(IllegalArgumentException("Userscript exceeds 2 MB size limit."))
        }
        val existing = scriptDao.getById(id)
            ?: return Result.failure(NoSuchElementException("Userscript not found: $id"))
        return try {
            val meta = UserscriptParser.parse(newCode, fallbackName = existing.name)
            val updated = existing.copy(
                name = meta.name,
                namespace = meta.namespace,
                version = meta.version,
                description = meta.description,
                code = newCode,
                matchesJson = UserscriptParser.encodeStringList(meta.matches),
                excludesJson = UserscriptParser.encodeStringList(meta.excludes),
                runAt = meta.runAt.headerValue,
                grantsJson = UserscriptParser.encodeStringList(meta.grants),
                updatedAt = System.currentTimeMillis()
            )
            scriptDao.upsert(updated)
            invalidateRuntimeCache()
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        scriptDao.updateEnabled(id = id, enabled = enabled, updatedAt = System.currentTimeMillis())
        invalidateRuntimeCache()
    }

    suspend fun deleteScript(id: String) {
        scriptDao.deleteById(id)
        storageDao.deleteAllForScript(id)
        invalidateRuntimeCache()
    }

    suspend fun getScriptById(id: String): UserscriptEntity? = scriptDao.getById(id)

    suspend fun matchingScriptsFor(
        url: String,
        runAt: UserscriptRunAt
    ): List<Pair<UserscriptEntity, Map<String, String>>> {
        if (!UserscriptUrlMatcher.isHttpOrHttps(url)) return emptyList()

        val enabledScripts = getCompiledEnabledScripts()
        if (enabledScripts.isEmpty()) return emptyList()

        val matched = enabledScripts.filter { compiled ->
            compiled.runAt == runAt &&
                UserscriptUrlMatcher.matches(url, compiled.matches, compiled.excludes)
        }
        if (matched.isEmpty()) return emptyList()

        return attachBatchedStorage(matched.map { it.entity })
    }

    /**
     * Returns all enabled `DOCUMENT_START` scripts that could match any URL on [originUrl]'s
     * scheme + host so same-origin client/link navigations (`/` -> `/r/android`) still execute
     * path-scoped `@run-at document-start` userscripts at true `document-start` via their
     * built-in runtime URL guard.
     */
    suspend fun originCandidateDocumentStartScriptsFor(
        originUrl: String
    ): List<Pair<UserscriptEntity, Map<String, String>>> {
        if (!UserscriptUrlMatcher.isHttpOrHttps(originUrl)) return emptyList()

        val enabledScripts = getCompiledEnabledScripts()
        if (enabledScripts.isEmpty()) return emptyList()

        val matched = enabledScripts.filter { compiled ->
            compiled.runAt == UserscriptRunAt.DOCUMENT_START &&
                UserscriptUrlMatcher.canMatchOrigin(originUrl, compiled.matches)
        }
        if (matched.isEmpty()) return emptyList()

        return attachBatchedStorage(matched.map { it.entity })
    }

    private suspend fun attachBatchedStorage(
        scripts: List<UserscriptEntity>
    ): List<Pair<UserscriptEntity, Map<String, String>>> {
        if (scripts.isEmpty()) return emptyList()
        val allStorage = if (scripts.size == 1) {
            storageDao.getAllForScript(scripts[0].id)
        } else {
            storageDao.getAllForScripts(scripts.map { it.id })
        }
        val byScript = allStorage.groupBy { it.scriptId }
        return scripts.map { script ->
            val storageMap = byScript[script.id]
                ?.associate { it.storageKey to it.valueJson }
                ?: emptyMap()
            script to storageMap
        }
    }

    private fun readBoundedText(body: ResponseBody?, maxBytes: Int): String {
        if (body == null) return ""
        val contentLength = body.contentLength()
        if (contentLength > maxBytes) {
            throw IOException("Userscript exceeds 2 MB size limit")
        }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IOException("Userscript exceeds 2 MB size limit")
                }
                out.write(buf, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_USERSCRIPT_BYTES = 2 * 1024 * 1024

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
