package app.overbay.swiftfox.features.userscripts

import app.overbay.swiftfox.data.db.UserscriptDao
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.UserscriptStorageDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

/**
 * Manages persistence, remote/local installation, updates, and URL/phase matching for `.user.js` scripts.
 */
class UserscriptRepository internal constructor(
    private val scriptDao: UserscriptDao,
    private val storageDao: UserscriptStorageDao,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    constructor(
        scriptDao: UserscriptDao,
        storageDao: UserscriptStorageDao,
        client: OkHttpClient = OkHttpClient()
    ) : this(scriptDao, storageDao, client, Dispatchers.IO)

    fun observeAll(): Flow<List<UserscriptEntity>> = scriptDao.observeAll()

    suspend fun getAll(): List<UserscriptEntity> = scriptDao.getAll()

    suspend fun getById(id: String): UserscriptEntity? = scriptDao.getById(id)

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
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                    }
                    response.body?.string().orEmpty()
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
        return try {
            val meta = UserscriptParser.parse(code, fallbackName = fallbackName)
            val normalizedSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
            val existing = if (normalizedSourceUrl != null) {
                scriptDao.getAll().firstOrNull { it.sourceUrl == normalizedSourceUrl }
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
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val existing = scriptDao.getById(id) ?: return
        if (existing.enabled != enabled) {
            scriptDao.upsert(
                existing.copy(
                    enabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteScript(id: String) {
        scriptDao.deleteById(id)
        storageDao.deleteAllForScript(id)
    }

    suspend fun matchingScriptsFor(
        url: String,
        runAt: UserscriptRunAt
    ): List<Pair<UserscriptEntity, Map<String, String>>> {
        if (!UserscriptUrlMatcher.isHttpOrHttps(url)) return emptyList()

        val enabledScripts = scriptDao.getEnabled()
        if (enabledScripts.isEmpty()) return emptyList()

        val matched = enabledScripts.filter { script ->
            if (UserscriptRunAt.fromHeader(script.runAt) != runAt) return@filter false
            val matches = UserscriptParser.decodeStringList(script.matchesJson)
            val excludes = UserscriptParser.decodeStringList(script.excludesJson)
            UserscriptUrlMatcher.matches(url, matches, excludes)
        }
        if (matched.isEmpty()) return emptyList()

        return matched.map { script ->
            val storageMap = storageDao.getAllForScript(script.id)
                .associate { it.storageKey to it.valueJson }
            script to storageMap
        }
    }
}
