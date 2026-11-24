package app.overbay.swiftfox.features.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure coroutine-safe host fetch deduplicator + negative-cache + cache-generation gate.
 *
 * Runs each deduplicated host fetch on [fetchScope] so cancelling an individual UI caller
 * (e.g. scrolling a list or recomposing a badge) never aborts shared fetches or poisons the
 * negative cache. Also guards against stale writes racing with [invalidateAll].
 */
internal class FaviconFetchGate<T : Any>(
    private val fetchScope: CoroutineScope,
    private val fetchDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val negativeCacheMs: Long = 5 * 60 * 1000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<T?>>()
    private val failedTimestamps = ConcurrentHashMap<String, Long>()
    private val generation = AtomicLong(0L)

    fun currentGeneration(): Long = generation.get()

    fun isNegativelyCached(host: String): Boolean {
        val lastFail = failedTimestamps[host] ?: return false
        return clockMs() - lastFail < negativeCacheMs
    }

    fun clearNegativeCache(host: String) {
        failedTimestamps.remove(host)
    }

    fun invalidateAll(): Long {
        val next = generation.incrementAndGet()
        failedTimestamps.clear()
        return next
    }

    suspend fun getOrFetch(
        host: String,
        fetcher: suspend () -> T?,
        onFetched: suspend (value: T, fetchedGen: Long) -> Unit
    ): T? {
        if (isNegativelyCached(host)) {
            return null
        }

        val deferred = CompletableDeferred<T?>()
        val existing = inflight.putIfAbsent(host, deferred)
        if (existing != null) {
            return existing.await()
        }

        val startGen = generation.get()
        fetchScope.launch(fetchDispatcher) {
            try {
                val fetched = fetcher()
                val stillValid = generation.get() == startGen
                if (fetched != null) {
                    if (stillValid) {
                        failedTimestamps.remove(host)
                        onFetched(fetched, startGen)
                        deferred.complete(fetched)
                    } else {
                        deferred.complete(null)
                    }
                } else {
                    if (stillValid) {
                        failedTimestamps[host] = clockMs()
                    }
                    deferred.complete(null)
                }
            } catch (ce: CancellationException) {
                deferred.cancel(ce)
                throw ce
            } catch (_: Throwable) {
                if (generation.get() == startGen) {
                    failedTimestamps[host] = clockMs()
                }
                deferred.complete(null)
            } finally {
                inflight.remove(host, deferred)
            }
        }

        return deferred.await()
    }
}

/**
 * Isolated in-memory store for Private tab favicons. Keyed by tab ID and validated against
 * the current host so private tabs never leak icons across host navigations or read/write
 * the persistent normal-browsing cache.
 */
internal class PrivateTabIconStore<T : Any> {
    private data class Entry<T : Any>(val host: String?, val value: T)

    private val icons = ConcurrentHashMap<String, Entry<T>>()

    fun put(tabId: String, host: String?, value: T) {
        icons[tabId] = Entry(host, value)
    }

    fun get(tabId: String, requestedHost: String?): T? {
        val entry = icons[tabId] ?: return null
        return if (entry.host == null || requestedHost == null || entry.host == requestedHost) {
            entry.value
        } else {
            null
        }
    }

    fun remove(tabId: String): Boolean = icons.remove(tabId) != null

    fun clearAll(): Boolean {
        if (icons.isEmpty()) return false
        icons.clear()
        return true
    }
}

/**
 * Disk + memory favicon cache and local custom image store.
 *
 * Fetching order (privacy-first & self-hosted friendly):
 * 1. In-memory LRU cache (or isolated per-tab private memory store for private tabs).
 * 2. Disk cache (`cacheDir/favicons/<sha256(host)>.png`).
 * 3. Direct site origin fetch: HTML `<link rel="apple-touch-icon|icon">` on origin root +
 *    `/apple-touch-icon.png` + `/favicon.ico` (works on self-hosted LAN/Authentik sites
 *    without sending deep URL paths/queries or leaking hostnames).
 * 4. DuckDuckGo icon fallback (`icons.duckduckgo.com/ip3/<host>.ico`) ONLY if direct fetch
 *    fails and the host is a public domain (never called for local/LAN/RFC1918/IP hosts).
 *
 * Private tabs NEVER read from or write to the normal-browsing memory or disk favicon cache.
 */
class FaviconRepository(
    private val context: Context,
    baseHttpClient: OkHttpClient,
    private val repoScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val httpClient: OkHttpClient = baseHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val faviconCacheDir: File = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val customIconsDir: File = File(context.filesDir, "custom_icons").apply { mkdirs() }

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    // Strictly isolated in-memory store for Private tabs (zero disk trace, zero normal-cache fallthrough)
    private val privateTabIcons = PrivateTabIconStore<Bitmap>()
    private val fetchGate = FaviconFetchGate<Bitmap>(
        fetchScope = repoScope,
        fetchDispatcher = Dispatchers.IO,
        negativeCacheMs = NEGATIVE_CACHE_MS
    )

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun getCachedFavicon(url: String, privateTabId: String? = null): Bitmap? {
        if (privateTabId != null) {
            return privateTabIcons.get(privateTabId, normalizedHost(url))
        }
        val host = normalizedHost(url) ?: return null
        return synchronized(memoryCache) { memoryCache.get(hostKey(host)) }
    }

    fun getCachedCustomImage(fileName: String): Bitmap? {
        val safe = CustomIconSpec.sanitizeImageFileName(fileName)
        if (safe.isEmpty()) return null
        return synchronized(memoryCache) { memoryCache.get(imageKey(safe)) }
    }

    suspend fun loadCustomImage(fileName: String): Bitmap? {
        val safe = CustomIconSpec.sanitizeImageFileName(fileName)
        if (safe.isEmpty()) return null
        getCachedCustomImage(safe)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(customIconsDir, safe)
            if (!file.isFile) return@withContext null
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                ?: return@withContext null
            synchronized(memoryCache) { memoryCache.put(imageKey(safe), bmp) }
            bmp
        }
    }

    /**
     * Imports a user-selected image URI (from Android Photo Picker), center-crops & downscales
     * to 192x192 PNG in persistent app storage, and returns the encoded spec (`image:<file>`).
     */
    suspend fun importCustomImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@runCatching null

            val sampleSize = computeInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, CUSTOM_IMAGE_SIZE_PX * 2)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val prepared = centerCropAndScale(decoded, CUSTOM_IMAGE_SIZE_PX)
            if (prepared !== decoded) decoded.recycle()

            val fileName = "custom_${UUID.randomUUID().toString().take(12)}.png"
            val outFile = File(customIconsDir, fileName)
            FileOutputStream(outFile).use { fos ->
                prepared.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            synchronized(memoryCache) { memoryCache.put(imageKey(fileName), prepared) }
            bumpRevision()
            "${CustomIconSpec.IMAGE_PREFIX}$fileName"
        }.getOrNull()
    }

    fun deleteCustomImageIfUnused(encodedSpec: String?) {
        val spec = CustomIconSpec.parse(encodedSpec)
        if (spec !is CustomIconSpec.LocalImage) return
        val file = File(customIconsDir, spec.fileName)
        if (file.isFile) runCatching { file.delete() }
        synchronized(memoryCache) { memoryCache.remove(imageKey(spec.fileName)) }
    }

    /**
     * Clears all cached website favicons from RAM and disk while keeping user-imported
     * custom workspace/item photos intact.
     */
    suspend fun clearFaviconCache() {
        fetchGate.invalidateAll()
        synchronized(memoryCache) {
            val hostKeys = memoryCache.snapshot().keys.filter { it.startsWith("host:") }
            hostKeys.forEach { memoryCache.remove(it) }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                faviconCacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) runCatching { file.delete() }
                }
            }
        }
        bumpRevision()
    }

    /**
     * Records a live WebView favicon (from `WebChromeClient.onReceivedIcon`).
     * Copies the WebView-owned bitmap synchronously on the caller thread before hopping
     * dispatchers so WebView recycling can never crash background scaling.
     * Private tabs are strictly stored in volatile per-tab memory.
     */
    suspend fun recordWebViewFavicon(
        url: String,
        bitmap: Bitmap,
        isPrivate: Boolean,
        tabId: String? = null
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val safeCopy = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            ?: return
        val normalized = withContext(Dispatchers.Default) {
            val scaled = scaleBitmapIfNeeded(safeCopy, FAVICON_MAX_PX)
            if (scaled != null && scaled !== safeCopy) {
                safeCopy.recycle()
            }
            scaled
        } ?: run {
            if (!safeCopy.isRecycled) safeCopy.recycle()
            return
        }
        val host = normalizedHost(url)
        if (isPrivate) {
            if (!tabId.isNullOrBlank()) {
                privateTabIcons.put(tabId, host, normalized)
                bumpRevision()
            }
            return
        }
        if (host == null) return
        val existing = synchronized(memoryCache) { memoryCache.get(hostKey(host)) }
        // Keep higher-resolution icon if we already fetched a crisp apple-touch-icon for this host
        if (existing != null && existing.width >= normalized.width && existing.width >= 48) {
            return
        }
        fetchGate.clearNegativeCache(host)
        putHostBitmap(host, normalized, writeDisk = true)
    }

    fun clearPrivateTabIcon(tabId: String) {
        if (privateTabIcons.remove(tabId)) {
            bumpRevision()
        }
    }

    fun clearAllPrivateIcons() {
        if (privateTabIcons.clearAll()) {
            bumpRevision()
        }
    }

    /**
     * Returns the favicon bitmap for [url], checking memory -> disk -> direct origin -> DDG fallback.
     * Deduplicates concurrent fetches for the same host on [repoScope] so individual caller
     * cancellations never abort in-flight downloads or poison the negative cache.
     */
    suspend fun getOrFetchFavicon(url: String, privateTabId: String? = null): Bitmap? {
        if (privateTabId != null) {
            // Never trigger network or normal-cache reads for private tabs
            return getCachedFavicon(url, privateTabId)
        }
        val host = normalizedHost(url) ?: return null
        synchronized(memoryCache) { memoryCache.get(hostKey(host)) }?.let { return it }

        val startGen = fetchGate.currentGeneration()
        val diskHit = withContext(Dispatchers.IO) { readDiskFavicon(host) }
        if (diskHit != null) {
            if (fetchGate.currentGeneration() == startGen) {
                synchronized(memoryCache) { memoryCache.put(hostKey(host), diskHit) }
                return diskHit
            }
            return null
        }

        return fetchGate.getOrFetch(
            host = host,
            fetcher = { fetchFaviconFromNetwork(url, host) },
            onFetched = { fetched, _ ->
                putHostBitmap(host, fetched, writeDisk = true)
            }
        )
    }

    private fun fetchFaviconFromNetwork(originalUrl: String, host: String): Bitmap? {
        val baseUri = runCatching { URI(originalUrl) }.getOrNull()
        val scheme = baseUri?.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) } ?: "https"
        val authority = baseUri?.rawAuthority?.takeIf { it.isNotBlank() } ?: host
        val origin = "$scheme://$authority"

        // 1. Parse origin root HTML <head> for high-res apple-touch-icon / icon links
        // (avoids sending unauthenticated GETs to deep URL paths or query strings)
        val htmlCandidates = discoverIconUrlsFromHtml(origin)
        for (iconUrl in htmlCandidates) {
            downloadAndDecodeBitmap(iconUrl)?.let { return it }
        }

        // 2. Direct origin /apple-touch-icon.png and /favicon.ico
        downloadAndDecodeBitmap("$origin/apple-touch-icon.png")?.let { return it }
        downloadAndDecodeBitmap("$origin/favicon.ico")?.let { return it }

        // 3. DuckDuckGo fallback ONLY for public domains (never leak LAN / localhost / RFC1918 / IP hosts)
        if (isPublicFallbackEligibleHost(host)) {
            downloadAndDecodeBitmap("https://icons.duckduckgo.com/ip3/$host.ico")?.let { return it }
        }

        return null
    }

    private fun discoverIconUrlsFromHtml(origin: String): List<String> {
        val target = "$origin/"
        val req = Request.Builder()
            .url(target)
            .header("User-Agent", FAVICON_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
            .build()
        val htmlSnippet = runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.isNotBlank() && !contentType.contains("html", ignoreCase = true)) {
                    return@use ""
                }
                val source = resp.body.source()
                source.request(MAX_HTML_PEEK_BYTES)
                val peek = source.buffer.clone()
                val byteCount = minOf(peek.size, MAX_HTML_PEEK_BYTES)
                peek.readUtf8(byteCount)
            }
        }.getOrDefault("")

        if (htmlSnippet.isBlank()) return emptyList()
        return extractIconCandidatesFromHtml(htmlSnippet, target)
    }

    private fun downloadAndDecodeBitmap(url: String): Bitmap? {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", FAVICON_USER_AGENT)
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (contentType.contains("svg") || contentType.contains("html") || contentType.contains("text/plain")) {
                    return@use null
                }
                val bytes = resp.body.byteStream().use { stream ->
                    readBoundedBytes(stream, MAX_ICON_DOWNLOAD_BYTES)
                }
                if (bytes.isEmpty()) return@use null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, FAVICON_MAX_PX * 2)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@use null
                val scaled = scaleBitmapIfNeeded(raw, FAVICON_MAX_PX)
                if (scaled == null) {
                    if (!raw.isRecycled) raw.recycle()
                    return@use null
                }
                if (scaled !== raw) raw.recycle()
                scaled
            }
        }.getOrNull()
    }

    private suspend fun putHostBitmap(host: String, bitmap: Bitmap, writeDisk: Boolean) {
        synchronized(memoryCache) { memoryCache.put(hostKey(host), bitmap) }
        if (writeDisk) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = diskFileForHost(host)
                    FileOutputStream(file).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
                    }
                }
            }
        }
        bumpRevision()
    }

    private fun readDiskFavicon(host: String): Bitmap? {
        val file = diskFileForHost(host)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun diskFileForHost(host: String): File = File(faviconCacheDir, "${sha256Hex(host)}.png")

    private fun bumpRevision() {
        _revision.update { it + 1L }
    }

    private fun hostKey(host: String) = "host:$host"
    private fun imageKey(fileName: String) = "img:$fileName"

    companion object {
        private const val MAX_MEMORY_CACHE_KB = 4 * 1024
        private const val FAVICON_MAX_PX = 96
        private const val CUSTOM_IMAGE_SIZE_PX = 192
        private const val MAX_HTML_PEEK_BYTES = 32L * 1024L
        private const val MAX_ICON_DOWNLOAD_BYTES = 512 * 1024
        private const val NEGATIVE_CACHE_MS = 5 * 60 * 1000L
        private const val FAVICON_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

        private val LINK_TAG_REGEX = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")

        internal fun normalizedHost(url: String): String? {
            if (url.isBlank() || url == OmniboxEngine.NEW_TAB_URL) return null
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
            return OmniboxEngine.extractHost(url)
                .trim()
                .lowercase(Locale.ROOT)
                .takeIf { it.isNotBlank() }
        }

        internal fun isPublicFallbackEligibleHost(host: String): Boolean {
            val lower = host.trim().lowercase(Locale.ROOT)
            if (lower.isBlank() || !lower.contains('.')) return false
            if (lower == "localhost" ||
                lower.endsWith(".local") ||
                lower.endsWith(".lan") ||
                lower.endsWith(".home") ||
                lower.endsWith(".internal") ||
                lower.endsWith(".arpa")
            ) {
                return false
            }
            // Reject IPv4 literals (private or raw IPs) and bracketed/unbracketed IPv6 hosts
            if (lower.startsWith("[") || lower.contains(':') || lower.all { it.isDigit() || it == '.' }) {
                return false
            }
            return true
        }

        internal fun extractIconCandidatesFromHtml(html: String, baseUrl: String): List<String> {
            val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return emptyList()
            data class ScoredUrl(val url: String, val score: Int)
            val scored = mutableListOf<ScoredUrl>()

            for (match in LINK_TAG_REGEX.findAll(html)) {
                val tag = match.value
                val attrs = mutableMapOf<String, String>()
                for (attrMatch in ATTR_REGEX.findAll(tag)) {
                    val key = attrMatch.groupValues[1].lowercase(Locale.ROOT)
                    val value = attrMatch.groupValues[2]
                        .ifEmpty { attrMatch.groupValues[3] }
                        .ifEmpty { attrMatch.groupValues[4] }
                    attrs[key] = value
                }
                val rel = attrs["rel"]?.lowercase(Locale.ROOT) ?: continue
                if (!rel.contains("icon")) continue
                val href = attrs["href"]?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val type = attrs["type"]?.lowercase(Locale.ROOT).orEmpty()
                if (type.contains("svg") || href.lowercase(Locale.ROOT).substringBefore('?').endsWith(".svg")) {
                    continue
                }
                val resolved = runCatching { baseUri.resolve(href).toString() }.getOrNull() ?: continue
                if (!resolved.startsWith("http://", true) && !resolved.startsWith("https://", true)) {
                    continue
                }
                val sizeHint = attrs["sizes"]
                    ?.lowercase(Locale.ROOT)
                    ?.split(' ', 'x')
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.maxOrNull()
                    ?: 0
                val baseScore = when {
                    rel.contains("apple-touch-icon") -> 300
                    resolved.lowercase(Locale.ROOT).substringBefore('?').endsWith(".png") -> 200
                    resolved.lowercase(Locale.ROOT).substringBefore('?').endsWith(".webp") -> 190
                    else -> 100
                }
                scored += ScoredUrl(resolved, baseScore + sizeHint.coerceAtMost(256))
            }
            return scored
                .sortedByDescending { it.score }
                .map { it.url }
                .distinct()
                .take(4)
        }

        private fun readBoundedBytes(stream: java.io.InputStream, maxBytes: Int): ByteArray {
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (total < maxBytes) {
                val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
            return out.toByteArray()
        }

        private fun computeInSampleSize(width: Int, height: Int, reqMaxDim: Int): Int {
            var inSampleSize = 1
            val maxDim = maxOf(width, height)
            while (maxDim / (inSampleSize * 2) >= reqMaxDim) {
                inSampleSize *= 2
            }
            return inSampleSize
        }

        private fun scaleBitmapIfNeeded(source: Bitmap, maxDim: Int): Bitmap? {
            if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
            val w = source.width
            val h = source.height
            if (w <= maxDim && h <= maxDim) {
                return if (source.config == Bitmap.Config.ARGB_8888) {
                    source
                } else {
                    runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                }
            }
            val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            return runCatching { Bitmap.createScaledBitmap(source, targetW, targetH, true) }.getOrNull()
        }

        private fun centerCropAndScale(source: Bitmap, targetSize: Int): Bitmap {
            val side = minOf(source.width, source.height)
            val x = (source.width - side) / 2
            val y = (source.height - side) / 2
            val square = Bitmap.createBitmap(source, x, y, side, side)
            return if (side == targetSize) {
                square
            } else {
                val scaled = Bitmap.createScaledBitmap(square, targetSize, targetSize, true)
                if (scaled !== square) square.recycle()
                scaled
            }
        }

        private fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(32)
        }
    }
}
