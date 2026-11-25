package app.overbay.swiftfox.features.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.data.db.DownloadRecordDao
import app.overbay.swiftfox.data.db.DownloadRecordEntity
import app.overbay.swiftfox.engine.BrowserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID

class DownloadCoordinator(
    context: Context,
    private val downloadRecordDao: DownloadRecordDao,
    private val httpClientProvider: () -> OkHttpClient,
    private val downloadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    constructor(
        context: Context,
        downloadRecordDao: DownloadRecordDao,
        httpClient: OkHttpClient,
        downloadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) : this(context, downloadRecordDao, { httpClient }, downloadScope)

    private val appContext: Context = context.applicationContext ?: context

    private val noRedirectClient: OkHttpClient by lazy {
        httpClientProvider().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    @Volatile
    private var completionReceiverRegistered = false

    fun registerCompletionReceiver(scope: CoroutineScope) {
        if (completionReceiverRegistered) return
        synchronized(this) {
            if (completionReceiverRegistered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                    val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (completedId <= 0L) return
                    scope.launch {
                        refreshStatusForSystemId(completedId)
                    }
                }
            }
            val registered = runCatching {
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_EXPORTED
                )
                true
            }.onFailure { err ->
                Log.w("DownloadCoordinator", "Failed to register download receiver: ${err.message}")
            }.getOrDefault(false)
            completionReceiverRegistered = registered
        }
    }

    private fun cookiesForProfile(
        url: String,
        profileName: String,
        isPrivate: Boolean
    ): String? {
        if (isPrivate) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) ||
                profileName == BrowserSession.DEFAULT_PROFILE_NAME
            ) {
                return null
            }
        }
        return runCatching {
            val cm = if (profileName != BrowserSession.DEFAULT_PROFILE_NAME &&
                WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
            ) {
                ProfileStore.getInstance().getProfile(profileName)?.cookieManager
            } else {
                CookieManager.getInstance()
            }
            cm?.getCookie(url)
        }.getOrNull()
    }

    private fun queryInsertedDisplayName(uri: Uri, fallback: String): String {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull() ?: fallback
    }

    suspend fun enqueueHttpDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        profileName: String = BrowserSession.DEFAULT_PROFILE_NAME,
        isPrivate: Boolean = false
    ): Result<DownloadRecordEntity> {
        return try {
            val parsedCheck = url.toHttpUrlOrNull()
                ?: throw IOException("Unsupported download URL")
            if (parsedCheck.scheme != "http" && parsedCheck.scheme != "https") {
                throw IOException("Unsupported download scheme")
            }

            // ProfileStore.getInstance() requires the Main thread on Android WebView multi-profile.
            val initialCookies = withContext(Dispatchers.Main) {
                cookiesForProfile(url, profileName, isPrivate)
            }
            val safeMime = normalizeMimeType(mimeType)
            val fileName = sanitizeFileName(
                extractContentDispositionFileName(contentDisposition)
                    ?: URLUtil.guessFileName(url, contentDisposition, safeMime)
            )

            // Use isolated OkHttp streaming for Private tabs (never persisted in system DownloadManager)
            // and isolated container profiles (so per-hop container cookies stay strictly scoped).
            // Standard default-profile downloads use native Android DownloadManager with Cookie header!
            val requiresIsolatedStream = isPrivate || profileName != BrowserSession.DEFAULT_PROFILE_NAME
            if (requiresIsolatedStream) {
                val initialEntity = DownloadRecordEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    url = if (isPrivate) "" else url.take(512),
                    mimeType = safeMime,
                    systemDownloadId = -1L,
                    status = "Downloading"
                )
                if (!isPrivate) {
                    withContext(Dispatchers.IO) {
                        downloadRecordDao.upsert(initialEntity)
                    }
                }
                downloadScope.launch {
                    try {
                        val completed = downloadViaOkHttp(
                            recordId = initialEntity.id,
                            createdAt = initialEntity.createdAt,
                            url = url,
                            initialFileName = fileName,
                            contentDisposition = contentDisposition,
                            fallbackMime = safeMime,
                            userAgent = userAgent,
                            initialCookies = initialCookies,
                            profileName = profileName,
                            isPrivate = isPrivate
                        )
                        if (!isPrivate) {
                            downloadRecordDao.upsert(completed)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (err: Throwable) {
                        Log.w("DownloadCoordinator", "Isolated download failed: ${err.message}")
                        if (!isPrivate) {
                            runCatching {
                                downloadRecordDao.upsert(initialEntity.copy(status = "Failed"))
                            }
                        }
                    }
                }
                return Result.success(initialEntity)
            }

            val record = withContext(Dispatchers.IO) {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription("Downloading with Swiftfox")
                    setMimeType(safeMime)
                    if (userAgent.isNotBlank()) {
                        val safeUa = sanitizeHeaderValue(userAgent)
                        if (safeUa.isNotEmpty()) {
                            addRequestHeader("User-Agent", safeUa)
                        }
                    }
                    if (!initialCookies.isNullOrBlank()) {
                        val safeCookies = sanitizeHeaderValue(initialCookies)
                        if (safeCookies.isNotEmpty()) {
                            addRequestHeader("Cookie", safeCookies)
                        }
                    }
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = dm.enqueue(request)
                val immediateStatus = queryDownloadManagerStatus(dm, downloadId) ?: "Downloading"
                val entity = DownloadRecordEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    url = url,
                    mimeType = safeMime,
                    systemDownloadId = downloadId,
                    status = immediateStatus
                )
                downloadRecordDao.upsert(entity)
                // Re-check once after upsert in case ACTION_DOWNLOAD_COMPLETE fired before upsert committed
                val postUpsertStatus = queryDownloadManagerStatus(dm, downloadId)
                if (postUpsertStatus != null && postUpsertStatus != entity.status) {
                    val updated = entity.copy(status = postUpsertStatus)
                    downloadRecordDao.upsert(updated)
                    updated
                } else {
                    entity
                }
            }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun downloadViaOkHttp(
        recordId: String,
        createdAt: Long,
        url: String,
        initialFileName: String,
        contentDisposition: String?,
        fallbackMime: String,
        userAgent: String,
        initialCookies: String?,
        profileName: String,
        isPrivate: Boolean
    ): DownloadRecordEntity {
        val initialParsed = url.toHttpUrlOrNull()
            ?: throw IOException("Unsupported download URL")

        var currentUrl = initialParsed
        var hops = 0
        while (hops <= 5) {
            // Query the profile's RFC 6265 CookieManager per redirect hop on Main thread so parent-domain
            // cookies (e.g. `.example.com` -> `cdn.example.com`) work while cross-site redirects stay isolated.
            val hopCookies = if (hops == 0) {
                initialCookies
            } else {
                withContext(Dispatchers.Main) {
                    cookiesForProfile(currentUrl.toString(), profileName, isPrivate)
                }
            }
            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .get()
            if (userAgent.isNotBlank()) {
                val safeUa = sanitizeHeaderValue(userAgent)
                if (safeUa.isNotEmpty()) {
                    reqBuilder.header("User-Agent", safeUa)
                }
            }
            if (!hopCookies.isNullOrBlank()) {
                val safeCookies = sanitizeHeaderValue(hopCookies)
                if (safeCookies.isNotEmpty()) {
                    reqBuilder.header("Cookie", safeCookies)
                }
            }
            val call = noRedirectClient.newCall(reqBuilder.build())
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion {
                call.cancel()
            }
            try {
                call.execute().use { response ->
                    coroutineContext.ensureActive()
                    if (response.isRedirect) {
                        val location = response.header("Location")
                            ?: throw IOException("Redirect without Location header")
                        val nextUrl = currentUrl.resolve(location)
                            ?: throw IOException("Invalid redirect target")
                        if (nextUrl.scheme != "http" && nextUrl.scheme != "https") {
                            throw IOException("Unsupported redirect scheme")
                        }
                        currentUrl = nextUrl
                        hops++
                        return@use
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Download failed (HTTP ${response.code})")
                    }
                    val resolvedMime = response.header("Content-Type")
                        ?.substringBefore(';')
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: fallbackMime
                    val responseDisposition = response.header("Content-Disposition") ?: contentDisposition
                    val guessedFromFinal = runCatching {
                        URLUtil.guessFileName(currentUrl.toString(), responseDisposition, resolvedMime)
                    }.getOrNull()
                    val resolvedName = extractContentDispositionFileName(responseDisposition)
                        ?.let(::sanitizeFileName)
                        ?: guessedFromFinal?.takeIf { it.isNotBlank() }?.let(::sanitizeFileName)
                        ?: initialFileName
                    val body = response.body
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, resolvedName)
                        put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = appContext.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Could not create MediaStore Downloads entry")
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                }
                            }
                        } ?: throw IOException("Could not open Downloads output stream")

                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } catch (e: CancellationException) {
                        runCatching { resolver.delete(uri, null, null) }
                        throw e
                    } catch (t: Throwable) {
                        runCatching { resolver.delete(uri, null, null) }
                        throw t
                    }

                    val actualDisplayName = queryInsertedDisplayName(uri, resolvedName)
                    return DownloadRecordEntity(
                        id = recordId,
                        fileName = actualDisplayName,
                        url = if (isPrivate) "" else currentUrl.toString().take(512),
                        mimeType = resolvedMime,
                        systemDownloadId = -1L,
                        status = "Saved to Downloads",
                        createdAt = createdAt
                    )
                }
            } finally {
                cancelHandle?.dispose()
            }
        }
        throw IOException("Too many redirects while downloading file")
    }

    suspend fun saveBase64Blob(
        fileName: String,
        mimeType: String,
        base64Payload: String,
        sourceUrl: String,
        isPrivate: Boolean = false
    ): Result<DownloadRecordEntity> = withContext(Dispatchers.IO) {
        try {
            val trimmed = base64Payload.trim()
            if (trimmed.length > (MAX_DATA_DOWNLOAD_BYTES * 4L / 3L) + 1024L) {
                throw IOException("Download exceeds maximum supported size")
            }

            // Stream plain base64 payloads (>64 KB) directly through Base64.getMimeDecoder()
            // into MediaStore without materializing a second 25 MB decoded ByteArray in heap.
            val streamableBase64Slice: Pair<String, String>? = run {
                if (trimmed.length <= 65_536) return@run null
                if (trimmed.startsWith("data:", ignoreCase = true)) {
                    val commaIdx = trimmed.indexOf(',')
                    if (commaIdx <= 4) throw IllegalArgumentException("Malformed data URI payload")
                    val meta = trimmed.substring(5, commaIdx)
                    val isBase64 = meta.split(';').any { it.trim().equals("base64", ignoreCase = true) }
                    val dataPart = trimmed.substring(commaIdx + 1)
                    if (isBase64 && !dataPart.contains('%')) {
                        val resolvedMime = meta.substringBefore(';').trim().ifBlank { mimeType }
                        resolvedMime to dataPart
                    } else {
                        null
                    }
                } else {
                    val base64Idx = trimmed.indexOf(";base64,", ignoreCase = true)
                    val clean = if (base64Idx >= 0) trimmed.substring(base64Idx + 8) else trimmed.substringAfter("base64,", trimmed)
                    mimeType to clean
                }
            }

            val safeMime: String
            val safeName = sanitizeFileName(
                fileName.ifBlank { "swiftfox_download_${System.currentTimeMillis()}" }
            )
            val resolver = appContext.contentResolver
            val uri: Uri

            if (streamableBase64Slice != null) {
                safeMime = normalizeMimeType(streamableBase64Slice.first)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore Downloads entry")
                try {
                    val asciiBytes = streamableBase64Slice.second.toByteArray(StandardCharsets.ISO_8859_1)
                    resolver.openOutputStream(uri)?.use { out ->
                        Base64.getMimeDecoder().wrap(ByteArrayInputStream(asciiBytes)).use { decodedIn ->
                            val buf = ByteArray(16 * 1024)
                            var total = 0
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = decodedIn.read(buf)
                                if (read == -1) break
                                total += read
                                if (total > MAX_DATA_DOWNLOAD_BYTES) {
                                    throw IOException("Download exceeds maximum supported size")
                                }
                                out.write(buf, 0, read)
                            }
                        }
                    } ?: error("Could not open Downloads output stream")

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: CancellationException) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw t
                }
            } else {
                val (resolvedMime, bytes) = decodeDataOrBase64Payload(trimmed, mimeType)
                safeMime = normalizeMimeType(resolvedMime)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore Downloads entry")
                try {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(bytes)
                    } ?: error("Could not open Downloads output stream")

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: CancellationException) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw t
                }
            }

            val actualDisplayName = queryInsertedDisplayName(uri, safeName)
            val record = DownloadRecordEntity(
                id = UUID.randomUUID().toString(),
                fileName = actualDisplayName,
                url = if (isPrivate) "" else sourceUrl.take(512),
                mimeType = safeMime,
                systemDownloadId = -1L,
                status = "Saved to Downloads"
            )
            if (!isPrivate) {
                downloadRecordDao.upsert(record)
            }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun reconcileActiveSystemDownloads() = withContext(Dispatchers.IO) {
        try {
            val active = downloadRecordDao.getActiveSystemDownloads()
            if (active.isEmpty()) return@withContext
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val statusById = queryDownloadManagerStatusesBatch(dm, active.map { it.systemDownloadId })
            val updates = active.mapNotNull { rec ->
                val newStatus = statusById[rec.systemDownloadId]
                if (newStatus != null && newStatus != rec.status) rec.copy(status = newStatus) else null
            }
            if (updates.isNotEmpty()) {
                downloadRecordDao.upsertAll(updates)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("DownloadCoordinator", "Failed reconciling active downloads: ${t.message}")
        }
    }

    suspend fun refreshAllStatuses() = withContext(Dispatchers.IO) {
        try {
            val systemRecords = downloadRecordDao.getAll().filter { it.systemDownloadId > 0L }
            if (systemRecords.isEmpty()) return@withContext
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val statusById = queryDownloadManagerStatusesBatch(dm, systemRecords.map { it.systemDownloadId })
            val updates = systemRecords.mapNotNull { rec ->
                val newStatus = statusById[rec.systemDownloadId]
                if (newStatus != null && newStatus != rec.status) rec.copy(status = newStatus) else null
            }
            if (updates.isNotEmpty()) {
                downloadRecordDao.upsertAll(updates)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("DownloadCoordinator", "Failed refreshing download statuses: ${t.message}")
        }
    }

    private suspend fun refreshStatusForSystemId(systemId: Long) = withContext(Dispatchers.IO) {
        try {
            val match = downloadRecordDao.findBySystemId(systemId) ?: return@withContext
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val newStatus = queryDownloadManagerStatus(dm, systemId) ?: return@withContext
            if (match.status != newStatus) {
                downloadRecordDao.upsert(match.copy(status = newStatus))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("DownloadCoordinator", "Failed refreshing download $systemId: ${t.message}")
        }
    }

    private fun queryDownloadManagerStatusesBatch(dm: DownloadManager, ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<Long, String>(ids.size)
        for (chunk in ids.distinct().chunked(200)) {
            val query = DownloadManager.Query().setFilterById(*chunk.toLongArray())
            dm.query(query)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (idCol < 0 || statusCol < 0) return@use
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mapped = mapDownloadManagerStatus(cursor.getInt(statusCol))
                    if (mapped != null) {
                        out[id] = mapped
                    }
                }
            }
        }
        return out
    }

    private fun queryDownloadManagerStatus(dm: DownloadManager, systemId: Long): String? {
        val query = DownloadManager.Query().setFilterById(systemId)
        dm.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val col = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (col < 0) return null
            return mapDownloadManagerStatus(cursor.getInt(col))
        }
        return null
    }

    private fun mapDownloadManagerStatus(statusCode: Int): String? = when (statusCode) {
        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
        DownloadManager.STATUS_FAILED -> "Failed"
        DownloadManager.STATUS_PAUSED -> "Paused"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_PENDING -> "Queued"
        else -> null
    }

    suspend fun deleteRecord(record: DownloadRecordEntity) = withContext(Dispatchers.IO) {
        downloadRecordDao.delete(record)
    }

    suspend fun clearAllRecords() = withContext(Dispatchers.IO) {
        downloadRecordDao.clearAll()
    }

    fun createOpenIntent(record: DownloadRecordEntity): Intent? {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var uri: Uri? = null
        var resolvedMime = record.mimeType.ifBlank { "*/*" }

        if (record.systemDownloadId > 0L) {
            uri = runCatching { dm.getUriForDownloadedFile(record.systemDownloadId) }.getOrNull()
            runCatching { dm.getMimeTypeForDownloadedFile(record.systemDownloadId) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { resolvedMime = it }
        }

        if (uri == null) {
            uri = findMediaStoreDownloadUri(record.fileName)
        }

        if (uri == null) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, record.fileName)
            if (file.exists()) {
                uri = runCatching {
                    FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                }.getOrNull()
            }
        }

        val targetUri = uri ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun findMediaStoreDownloadUri(fileName: String): Uri? {
        return runCatching {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val sort = "${MediaStore.Downloads.DATE_ADDED} DESC"
            appContext.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(fileName),
                sort
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    companion object {
        internal const val MAX_DATA_DOWNLOAD_BYTES = 25 * 1024 * 1024
        private val RFC5987_FILENAME_STAR_REGEX = Regex(
            """(?:^|;)\s*filename\*\s*=\s*([^;]+)""",
            RegexOption.IGNORE_CASE
        )
        private val QUOTED_FILENAME_REGEX = Regex(
            """(?:^|;)\s*filename\s*=\s*"((?:\\.|[^"\\])*)"(?:\s*;|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        private val UNQUOTED_FILENAME_REGEX = Regex(
            """(?:^|;)\s*filename\s*=\s*([^;\s"]+)""",
            RegexOption.IGNORE_CASE
        )
        private val UNESCAPED_QUOTE_REGEX = Regex("""\\(.)""")
        private val UNSAFE_FILENAME_CHARS_REGEX = Regex("""[\u0000-\u001F\u007F:*?"<>|]""")

        internal fun sanitizeHeaderValue(raw: String): String =
            raw.trim().filter { ch -> ch == '\t' || ch in '\u0020'..'\u007E' }

        internal fun normalizeMimeType(raw: String?): String =
            raw?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() } ?: "application/octet-stream"

        internal fun extractContentDispositionFileName(contentDisposition: String?): String? {
            if (contentDisposition.isNullOrBlank()) return null
            val starMatch = RFC5987_FILENAME_STAR_REGEX.find(contentDisposition)
            if (starMatch != null) {
                val rawValue = starMatch.groupValues[1].trim().trim('"')
                val firstQuote = rawValue.indexOf('\'')
                val secondQuote = if (firstQuote >= 0) rawValue.indexOf('\'', firstQuote + 1) else -1
                val charsetName = if (firstQuote > 0) rawValue.substring(0, firstQuote).trim() else ""
                val charset = runCatching {
                    if (charsetName.isNotBlank()) Charset.forName(charsetName) else StandardCharsets.UTF_8
                }.getOrDefault(StandardCharsets.UTF_8)
                val encodedPart = if (secondQuote >= 0) rawValue.substring(secondQuote + 1) else rawValue
                val decoded = String(percentDecodeToBytes(encodedPart), charset).trim()
                if (decoded.isNotBlank()) return decoded
            }
            val quotedMatch = QUOTED_FILENAME_REGEX.find(contentDisposition)
            if (quotedMatch != null) {
                val unescaped = quotedMatch.groupValues[1]
                    .replace(UNESCAPED_QUOTE_REGEX, "$1")
                    .trim()
                if (unescaped.isNotBlank()) return unescaped
            }
            val unquotedMatch = UNQUOTED_FILENAME_REGEX.find(contentDisposition)
            if (unquotedMatch != null) {
                val value = unquotedMatch.groupValues[1].trim()
                if (value.isNotBlank()) return value
            }
            return null
        }

        internal fun sanitizeFileName(raw: String): String {
            val cleaned = raw
                .replace('\\', '_')
                .replace('/', '_')
                .replace("..", "_")
                .replace(UNSAFE_FILENAME_CHARS_REGEX, "_")
                .trim()
                .trim('.')
                .takeIf { candidate -> candidate.isNotBlank() && candidate.any { it != '_' && it != '.' } }
                ?: "swiftfox_download_${System.currentTimeMillis()}"
            if (cleaned.length <= 180) return cleaned
            val dotIdx = cleaned.lastIndexOf('.')
            if (dotIdx in 1 until cleaned.length - 1) {
                val ext = cleaned.substring(dotIdx)
                if (ext.length <= 20) {
                    val maxBase = (180 - ext.length).coerceAtLeast(1)
                    return cleaned.substring(0, maxBase).trimEnd('.') + ext
                }
            }
            return cleaned.take(180)
        }

        internal fun percentDecodeToBytes(raw: String): ByteArray {
            val out = ByteArrayOutputStream(raw.length)
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch == '%' && i + 2 < raw.length) {
                    val hi = raw[i + 1].digitToIntOrNull(16)
                    val lo = raw[i + 2].digitToIntOrNull(16)
                    if (hi != null && lo != null) {
                        out.write((hi shl 4) or lo)
                        i += 3
                        continue
                    }
                }
                if (ch.code <= 0x7F) {
                    out.write(ch.code)
                } else {
                    out.write(ch.toString().toByteArray(StandardCharsets.UTF_8))
                }
                i++
            }
            return out.toByteArray()
        }

        fun decodeDataOrBase64Payload(payload: String, fallbackMime: String): Pair<String, ByteArray> {
            val trimmed = payload.trim()
            if (trimmed.length > (MAX_DATA_DOWNLOAD_BYTES * 4L / 3L) + 1024L) {
                throw IOException("Download exceeds maximum supported size")
            }
            if (trimmed.startsWith("data:", ignoreCase = true)) {
                val commaIdx = trimmed.indexOf(',')
                if (commaIdx <= 4) {
                    throw IllegalArgumentException("Malformed data URI payload")
                }
                val meta = trimmed.substring(5, commaIdx)
                val dataPart = trimmed.substring(commaIdx + 1)
                val isBase64 = meta.split(';').any { it.trim().equals("base64", ignoreCase = true) }
                val mime = meta.substringBefore(';').trim().ifBlank { fallbackMime }
                val bytes = if (isBase64) {
                    val decodedInput = if (dataPart.contains('%')) {
                        String(percentDecodeToBytes(dataPart), StandardCharsets.US_ASCII)
                    } else {
                        dataPart
                    }
                    Base64.getMimeDecoder().decode(decodedInput)
                } else {
                    percentDecodeToBytes(dataPart)
                }
                if (bytes.size > MAX_DATA_DOWNLOAD_BYTES) {
                    throw IOException("Download exceeds maximum supported size")
                }
                return mime to bytes
            }
            val base64Idx = trimmed.indexOf(";base64,", ignoreCase = true)
            val cleanBase64 = if (base64Idx >= 0) {
                trimmed.substring(base64Idx + 8)
            } else {
                trimmed.substringAfter("base64,", trimmed)
            }
            val bytes = Base64.getMimeDecoder().decode(cleanBase64)
            if (bytes.size > MAX_DATA_DOWNLOAD_BYTES) {
                throw IOException("Download exceeds maximum supported size")
            }
            return fallbackMime to bytes
        }
    }
}
