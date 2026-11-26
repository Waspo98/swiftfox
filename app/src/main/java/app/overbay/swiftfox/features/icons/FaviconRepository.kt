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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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
import kotlin.math.abs

/**
 * Pure coroutine-safe key fetch deduplicator + negative-cache + cache-generation gate.
 *
 * Runs each deduplicated fetch on [fetchScope] so cancelling an individual UI caller
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
 * the current scope key so private tabs never leak icons across app/host navigations or
 * read/write the persistent normal-browsing cache.
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
 * Resolved favicon scoping keys for an HTTP(S) URL.
 *
 * - [authorityKey] preserves non-default ports (e.g. `192.168.86.200:8989` vs `:7878`), while
 *   omitting default `:80`/`:443` so HTTP/HTTPS share icons on standard web hosts.
 * - [pathKey] adds the first normalized path segment (e.g. `192.168.86.200/sonarr` vs `/radarr`).
 * - [isSubpathScoped] is true for LAN/local/IP hosts where subpaths commonly host distinct apps.
 */
internal data class FaviconScope(
    val host: String,
    val authorityKey: String,
    val pathKey: String?,
    val firstSegment: String?,
    val rawFirstSegment: String? = firstSegment,
    val isSubpathScoped: Boolean
) {
    val primaryKey: String
        get() = if (isSubpathScoped && pathKey != null) pathKey else authorityKey

    val hasExplicitPort: Boolean
        get() = authorityKey != host
}

internal data class FaviconProbeTargets(
    val origin: String,
    val htmlProbeUrl: String,
    val alternateHtmlProbeUrl: String?,
    val directIconUrls: List<String>,
    val isSubpathProbe: Boolean
)

internal data class SampledIcon<T : Any>(
    val value: T,
    val width: Int,
    val samplePixels: IntArray
)

internal data class WebViewRecordResult<T : Any>(
    val writtenKey: String?,
    val retainedBitmap: Boolean
)

/**
 * Pure-Kotlin coordinator governing public-domain root (`authorityKey`) vs subpath (`pathKey`)
 * favicon isolation, cross-session baseline comparison, provisional-seed rescue, and legacy
 * unverified cache repair.
 */
internal class PublicFaviconCoordinator<T : Any>(
    private val fetchGate: FaviconFetchGate<SampledIcon<T>>,
    private val subpathFetchGate: FaviconFetchGate<SampledIcon<T>>,
    private val getInMemory: (key: String) -> SampledIcon<T>?,
    private val hasInMemory: (key: String) -> Boolean = { key -> getInMemory(key) != null },
    private val loadFromDisk: suspend (key: String) -> SampledIcon<T>?,
    private val putBitmap: suspend (key: String, icon: SampledIcon<T>, writeDisk: Boolean) -> Unit,
    private val removeBitmap: suspend (key: String) -> Unit,
    private val isLegacyUnverified: (key: String) -> Boolean,
    private val markVerified: suspend (key: String) -> Unit,
    private val getProvisionalPath: (authorityKey: String) -> String?,
    private val setProvisionalPath: suspend (authorityKey: String, pathKey: String?) -> Unit,
    private val fetchNetworkRoot: suspend (originRootUrl: String, rootScope: FaviconScope) -> SampledIcon<T>?,
    private val fetchNetworkSubpath: suspend (originalUrl: String, scope: FaviconScope) -> SampledIcon<T>?,
    private val areSimilar: (IntArray, IntArray) -> Boolean
) {
    private data class AuthorityBaseline<T : Any>(
        val pathKey: String?,
        val samplePixels: IntArray,
        val icon: SampledIcon<T>?
    )

    private val baselines = object : LinkedHashMap<String, AuthorityBaseline<T>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AuthorityBaseline<T>>?): Boolean =
            size > MAX_BASELINES
    }
    private val missingDiskPathKeys = ConcurrentHashMap.newKeySet<String>()
    private val sameAsAuthoritySubpaths = ConcurrentHashMap.newKeySet<String>()

    fun clearMemoryState() {
        synchronized(baselines) {
            baselines.clear()
        }
        missingDiskPathKeys.clear()
        sameAsAuthoritySubpaths.clear()
    }

    fun hasBaselineSubpathPending(authorityKey: String): Boolean =
        synchronized(baselines) { baselines[authorityKey]?.pathKey != null }

    fun needsBackgroundFetch(
        scope: FaviconScope,
        probeDistinctSubpath: Boolean
    ): Boolean {
        val authKey = scope.authorityKey
        val pathKey = scope.pathKey
        if (pathKey == null) {
            val needsRootRefresh = (isLegacyUnverified(authKey) || getProvisionalPath(authKey) != null) &&
                !fetchGate.isNegativelyCached(authKey)
            return needsRootRefresh || !hasInMemory(authKey)
        }

        val pathLegacy = isLegacyUnverified(pathKey)
        val hasPathInMemory = hasInMemory(pathKey)
        if (hasPathInMemory && !pathLegacy) {
            return false
        }
        if (hasPathInMemory && pathLegacy && !subpathFetchGate.isNegativelyCached(pathKey)) {
            return true
        }
        if (!missingDiskPathKeys.contains(pathKey)) {
            return true
        }
        val needsAuthRefresh = (isLegacyUnverified(authKey) || getProvisionalPath(authKey) != null) &&
            !fetchGate.isNegativelyCached(authKey)
        if (needsAuthRefresh || !hasInMemory(authKey)) {
            return true
        }
        return probeDistinctSubpath &&
            !sameAsAuthoritySubpaths.contains(pathKey) &&
            !subpathFetchGate.isNegativelyCached(pathKey)
    }

    suspend fun getOrFetch(
        originalUrl: String,
        scope: FaviconScope,
        originRootUrl: String,
        probeDistinctSubpath: Boolean
    ): T? {
        val authKey = scope.authorityKey
        val pathKey = scope.pathKey
        val rootScope = scope.copy(pathKey = null, firstSegment = null, rawFirstSegment = null)
        if (pathKey == null) {
            return getOrFetchRoot(originRootUrl, rootScope)?.value
        }

        val pathLegacy = isLegacyUnverified(pathKey)
        if (!pathLegacy) {
            getInMemory(pathKey)?.let { return it.value }
            if (!missingDiskPathKeys.contains(pathKey)) {
                val diskPath = loadFromDisk(pathKey)
                if (diskPath != null) {
                    putBitmap(pathKey, diskPath, false)
                    return diskPath.value
                }
                missingDiskPathKeys.add(pathKey)
            }
        }

        val rootInfo = getOrFetchRoot(originRootUrl, rootScope)

        if (!probeDistinctSubpath) {
            return rootInfo?.value
        }

        if (!pathLegacy && (sameAsAuthoritySubpaths.contains(pathKey) || subpathFetchGate.isNegativelyCached(pathKey))) {
            return rootInfo?.value
        }

        val genAtStart = subpathFetchGate.currentGeneration()
        val fetchedSubpath = subpathFetchGate.getOrFetch(
            host = pathKey,
            fetcher = {
                val candidate = fetchNetworkSubpath(originalUrl, scope) ?: return@getOrFetch null
                val currentRoot = getVerifiedRootInfo(authKey) ?: rootInfo
                if (currentRoot != null && areSimilar(currentRoot.samplePixels, candidate.samplePixels)) {
                    if (subpathFetchGate.currentGeneration() == genAtStart) {
                        recordSameAsAuthority(pathKey)
                        if (pathLegacy) {
                            removeBitmap(pathKey)
                            markVerified(pathKey)
                        }
                    }
                    null
                } else {
                    candidate
                }
            },
            onFetched = { info, _ ->
                sameAsAuthoritySubpaths.remove(pathKey)
                missingDiskPathKeys.remove(pathKey)
                putBitmap(pathKey, info, true)
                markVerified(pathKey)
            }
        )

        if (fetchedSubpath != null) {
            return fetchedSubpath.value
        }
        if (pathLegacy && rootInfo == null) {
            getInMemory(pathKey)?.let { return it.value }
            loadFromDisk(pathKey)?.let {
                putBitmap(pathKey, it, false)
                return it.value
            }
        }
        return rootInfo?.value
    }

    suspend fun recordWebViewIcon(
        originalUrl: String,
        scope: FaviconScope,
        originRootUrl: String,
        incoming: SampledIcon<T>
    ): WebViewRecordResult<T> {
        val authKey = scope.authorityKey
        val pathKey = scope.pathKey

        if (pathKey == null) {
            val prevBaseline = synchronized(baselines) { baselines[authKey] }
            val prevProvisionalPath = getProvisionalPath(authKey) ?: prevBaseline?.pathKey
            val wasLegacy = isLegacyUnverified(authKey)
            val forceReplace = wasLegacy || prevProvisionalPath != null

            if (prevBaseline != null &&
                prevBaseline.pathKey != null &&
                prevBaseline.icon != null &&
                !areSimilar(prevBaseline.samplePixels, incoming.samplePixels)
            ) {
                val hasExistingPath = getInMemory(prevBaseline.pathKey) != null ||
                    loadFromDisk(prevBaseline.pathKey) != null
                if (!hasExistingPath) {
                    missingDiskPathKeys.remove(prevBaseline.pathKey)
                    putBitmap(prevBaseline.pathKey, prevBaseline.icon, true)
                    markVerified(prevBaseline.pathKey)
                }
            }

            updateBaseline(authKey, AuthorityBaseline(null, incoming.samplePixels, null))
            val wrote = recordToKey(authKey, incoming, forceReplace = forceReplace)
            if (wrote || forceReplace) {
                setProvisionalPath(authKey, null)
                markVerified(authKey)
            }
            return WebViewRecordResult(
                writtenKey = if (wrote) authKey else null,
                retainedBitmap = wrote
            )
        }

        val verifiedAuth = getVerifiedRootInfo(authKey)
        if (verifiedAuth != null) {
            synchronized(baselines) {
                if (baselines[authKey] == null) {
                    baselines[authKey] = AuthorityBaseline(null, verifiedAuth.samplePixels, null)
                }
            }
            val pathLegacy = isLegacyUnverified(pathKey)
            val existingPath = if (!pathLegacy) {
                getInMemory(pathKey) ?: loadFromDisk(pathKey)?.also { putBitmap(pathKey, it, false) }
            } else {
                null
            }

            if (existingPath != null) {
                val wrote = recordToKey(pathKey, incoming, forceReplace = false)
                return WebViewRecordResult(
                    writtenKey = if (wrote) pathKey else null,
                    retainedBitmap = wrote
                )
            }

            if (!areSimilar(verifiedAuth.samplePixels, incoming.samplePixels)) {
                val wrote = recordToKey(pathKey, incoming, forceReplace = pathLegacy)
                return WebViewRecordResult(
                    writtenKey = if (wrote) pathKey else null,
                    retainedBitmap = wrote
                )
            }

            recordSameAsAuthority(pathKey)
            if (pathLegacy) {
                removeBitmap(pathKey)
                markVerified(pathKey)
            }
            val wroteAuth = recordToKey(authKey, incoming, forceReplace = false)
            return WebViewRecordResult(
                writtenKey = if (wroteAuth) authKey else null,
                retainedBitmap = wroteAuth
            )
        }

        // Authority icon is missing, legacy-unverified, or provisional: persist subpath safely first
        // while verifying the origin root so a subpath (e.g. /athletic) never overwrites the root.
        val pathLegacy = isLegacyUnverified(pathKey)
        val existingDistinctPath = if (!pathLegacy) {
            getInMemory(pathKey) ?: loadFromDisk(pathKey)?.also { putBitmap(pathKey, it, false) }
        } else {
            null
        }
        val wroteImmediatePath = recordToKey(pathKey, incoming, forceReplace = pathLegacy)

        val rootScope = scope.copy(pathKey = null, firstSegment = null, rawFirstSegment = null)
        val rootInfo = getOrFetchRoot(originRootUrl, rootScope)
        if (rootInfo != null) {
            if (areSimilar(rootInfo.samplePixels, incoming.samplePixels)) {
                val upgraded = recordToKey(authKey, incoming, forceReplace = false)
                val keepExistingDistinct = existingDistinctPath != null &&
                    !areSimilar(rootInfo.samplePixels, existingDistinctPath.samplePixels)
                if (!keepExistingDistinct) {
                    removeBitmap(pathKey)
                    recordSameAsAuthority(pathKey)
                    markVerified(pathKey)
                }
                return WebViewRecordResult(
                    writtenKey = if (upgraded) authKey else null,
                    retainedBitmap = wroteImmediatePath || upgraded
                )
            }
            return WebViewRecordResult(
                writtenKey = if (wroteImmediatePath) pathKey else null,
                retainedBitmap = wroteImmediatePath
            )
        }

        val anyExistingAuth = getInMemory(authKey) ?: loadFromDisk(authKey)
        var seededProvisional = false
        if (anyExistingAuth == null) {
            putBitmap(authKey, incoming, true)
            setProvisionalPath(authKey, pathKey)
            updateBaseline(authKey, AuthorityBaseline(pathKey, incoming.samplePixels, incoming))
            seededProvisional = true
        }
        return WebViewRecordResult(
            writtenKey = if (wroteImmediatePath) pathKey else null,
            retainedBitmap = wroteImmediatePath || seededProvisional
        )
    }

    private suspend fun getOrFetchRoot(originRootUrl: String, rootScope: FaviconScope): SampledIcon<T>? {
        val authKey = rootScope.authorityKey
        val needsRefresh = isLegacyUnverified(authKey) || getProvisionalPath(authKey) != null
        if (!needsRefresh) {
            getInMemory(authKey)?.let { return it }
            val disk = loadFromDisk(authKey)
            if (disk != null) {
                putBitmap(authKey, disk, false)
                return disk
            }
        }
        val fetched = fetchGate.getOrFetch(
            host = authKey,
            fetcher = { fetchNetworkRoot(originRootUrl, rootScope) },
            onFetched = { info, _ ->
                missingDiskPathKeys.remove(authKey)
                putBitmap(authKey, info, true)
                markVerified(authKey)
                setProvisionalPath(authKey, null)
                updateBaseline(authKey, AuthorityBaseline(null, info.samplePixels, null))
            }
        )
        if (fetched != null) {
            return fetched
        }
        if (needsRefresh) {
            getInMemory(authKey)?.let { return it }
            loadFromDisk(authKey)?.let { disk ->
                putBitmap(authKey, disk, false)
                return disk
            }
        }
        return null
    }

    private suspend fun getVerifiedRootInfo(authKey: String): SampledIcon<T>? {
        if (isLegacyUnverified(authKey) || getProvisionalPath(authKey) != null) return null
        return getInMemory(authKey) ?: loadFromDisk(authKey)?.also { putBitmap(authKey, it, false) }
    }

    private suspend fun recordToKey(
        cacheKey: String,
        incoming: SampledIcon<T>,
        forceReplace: Boolean
    ): Boolean {
        if (!forceReplace) {
            val existing = getInMemory(cacheKey) ?: loadFromDisk(cacheKey)?.also {
                putBitmap(cacheKey, it, false)
            }
            if (existing != null) {
                if (existing.width > incoming.width && existing.width >= 48) {
                    return false
                }
                if (existing.width >= incoming.width && areSimilar(existing.samplePixels, incoming.samplePixels)) {
                    return false
                }
            }
        }
        fetchGate.clearNegativeCache(cacheKey)
        subpathFetchGate.clearNegativeCache(cacheKey)
        sameAsAuthoritySubpaths.remove(cacheKey)
        missingDiskPathKeys.remove(cacheKey)
        putBitmap(cacheKey, incoming, true)
        markVerified(cacheKey)
        return true
    }

    private fun updateBaseline(authorityKey: String, baseline: AuthorityBaseline<T>) {
        synchronized(baselines) {
            baselines[authorityKey] = baseline
        }
    }

    private fun recordSameAsAuthority(pathKey: String) {
        if (sameAsAuthoritySubpaths.size > MAX_SAME_AS_AUTHORITY_CACHE) {
            sameAsAuthoritySubpaths.clear()
        }
        sameAsAuthoritySubpaths.add(pathKey)
    }

    companion object {
        private const val MAX_BASELINES = 32
        private const val MAX_SAME_AS_AUTHORITY_CACHE = 512
    }
}

/**
 * Disk + memory favicon cache and local custom image store.
 *
 * Fetching order (privacy-first & self-hosted friendly):
 * 1. In-memory LRU cache (or isolated per-tab private memory store for private tabs).
 * 2. Disk cache (`cacheDir/favicons/<sha256(key)>.png`).
 * 3. Direct site origin/app-subpath fetch: HTML `<link rel="apple-touch-icon|icon">` +
 *    `/apple-touch-icon.png` + `/favicon.ico` (works on self-hosted LAN/Authentik sites
 *    across distinct ports and reverse-proxy subpaths without leaking LAN hostnames).
 * 4. DuckDuckGo icon fallback (`icons.duckduckgo.com/ip3/<host>.ico`) ONLY if direct fetch
 *    fails and the host is a public default-port domain (never called for local/LAN/RFC1918/IP
 *    or custom-port hosts).
 *
 * Private tabs NEVER read from or write to the normal-browsing memory or disk favicon cache.
 */
class FaviconRepository(
    private val context: Context,
    baseHttpClientProvider: () -> OkHttpClient,
    private val repoScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    constructor(
        context: Context,
        baseHttpClient: OkHttpClient,
        repoScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) : this(context, { baseHttpClient }, repoScope)

    private val httpClient: OkHttpClient by lazy {
        baseHttpClientProvider().newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val faviconCacheDir: File by lazy {
        File(context.cacheDir, "favicons").apply { mkdirs() }
    }
    private val customIconsDir: File by lazy {
        File(context.filesDir, "custom_icons").apply { mkdirs() }
    }

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    // Strictly isolated in-memory store for Private tabs (zero disk trace, zero normal-cache fallthrough)
    private val privateTabIcons = PrivateTabIconStore<Bitmap>()
    private val keyRevisions = ConcurrentHashMap<String, Long>()
    private val metadataMutex = Mutex()
    @Volatile
    private var metadataLoaded = false
    private val legacyUnverifiedHashes = ConcurrentHashMap.newKeySet<String>()
    private val provisionalSeeds = ConcurrentHashMap<String, String>()
    private val fetchGate = FaviconFetchGate<SampledIcon<Bitmap>>(
        fetchScope = repoScope,
        fetchDispatcher = Dispatchers.IO,
        negativeCacheMs = NEGATIVE_CACHE_MS
    )
    private val subpathFetchGate = FaviconFetchGate<SampledIcon<Bitmap>>(
        fetchScope = repoScope,
        fetchDispatcher = Dispatchers.IO,
        negativeCacheMs = NEGATIVE_CACHE_MS
    )

    private val publicCoordinator = PublicFaviconCoordinator<Bitmap>(
        fetchGate = fetchGate,
        subpathFetchGate = subpathFetchGate,
        getInMemory = { key ->
            val bmp = synchronized(memoryCache) { memoryCache.get(hostKey(key)) }
            bmp?.let { toSampledIcon(it) }
        },
        hasInMemory = { key ->
            synchronized(memoryCache) { memoryCache.get(hostKey(key)) != null }
        },
        loadFromDisk = { key ->
            val bmp = withContext(Dispatchers.IO) { readDiskFavicon(key) }
            bmp?.let { toSampledIcon(it) }
        },
        putBitmap = { key, info, writeDisk ->
            putHostBitmap(key, info.value, writeDisk = writeDisk)
        },
        removeBitmap = { key ->
            removeHostBitmap(key)
        },
        isLegacyUnverified = { key -> isLegacyUnverifiedKey(key) },
        markVerified = { key -> markKeyVerified(key) },
        getProvisionalPath = { authKey -> provisionalSeeds[authKey] },
        setProvisionalPath = { authKey, pathKey -> updateProvisionalSeed(authKey, pathKey) },
        fetchNetworkRoot = { originRootUrl, rootScope ->
            fetchFaviconFromNetwork(originRootUrl, rootScope)?.let { toSampledIcon(it) }
        },
        fetchNetworkSubpath = { originalUrl, scope ->
            fetchPublicSubpathOnly(originalUrl, scope)
        },
        areSimilar = { a, b -> arePixelSamplesSimilar(a, b) }
    )

    init {
        repoScope.launch(Dispatchers.IO) {
            ensureCacheMetadataLoaded()
        }
    }

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun keyRevision(cacheKey: String): Long = keyRevisions[cacheKey] ?: 0L

    fun trimMemory(evictAll: Boolean) {
        publicCoordinator.clearMemoryState()
        synchronized(memoryCache) {
            if (evictAll) {
                memoryCache.evictAll()
            } else {
                memoryCache.trimToSize(MAX_MEMORY_CACHE_KB / 2)
            }
        }
    }

    internal fun needsBackgroundFetch(scope: FaviconScope?, probeDistinctSubpath: Boolean): Boolean {
        if (scope == null) return false
        if (!metadataLoaded) return true
        if (scope.isSubpathScoped) {
            return synchronized(memoryCache) { memoryCache.get(hostKey(scope.primaryKey)) == null }
        }
        return publicCoordinator.needsBackgroundFetch(scope, probeDistinctSubpath)
    }

    fun getCachedFavicon(url: String, privateTabId: String? = null): Bitmap? =
        getCachedFaviconForScope(resolveScope(url), privateTabId)

    internal fun getCachedFaviconForScope(scope: FaviconScope?, privateTabId: String? = null): Bitmap? {
        if (privateTabId != null) {
            return privateTabIcons.get(privateTabId, scope?.primaryKey)
        }
        if (scope == null) return null
        return synchronized(memoryCache) {
            scope.pathKey?.let { memoryCache.get(hostKey(it)) }
                ?: if (scope.isSubpathScoped && scope.pathKey != null) {
                    null
                } else {
                    memoryCache.get(hostKey(scope.authorityKey))
                }
        }
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

    private suspend fun ensureCacheMetadataLoaded() {
        if (metadataLoaded) return
        metadataMutex.withLock {
            if (metadataLoaded) return
            withContext(Dispatchers.IO) {
                val marker = File(faviconCacheDir, CACHE_V2_MARKER_FILE)
                val legacyFile = File(faviconCacheDir, LEGACY_UNVERIFIED_FILE)
                if (!marker.exists()) {
                    val existingHashes = faviconCacheDir.listFiles()
                        ?.asSequence()
                        ?.filter { it.isFile && it.name.endsWith(".png") }
                        ?.map { it.name.removeSuffix(".png") }
                        ?.filter { it.isNotBlank() }
                        ?.toList()
                        .orEmpty()
                    legacyUnverifiedHashes.clear()
                    legacyUnverifiedHashes.addAll(existingHashes)
                    if (existingHashes.isNotEmpty()) {
                        runCatching { legacyFile.writeText(existingHashes.joinToString("\n")) }
                    }
                    runCatching { marker.writeText("2") }
                } else if (legacyFile.isFile) {
                    val lines = runCatching { legacyFile.readLines() }.getOrDefault(emptyList())
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    legacyUnverifiedHashes.clear()
                    legacyUnverifiedHashes.addAll(lines)
                    if (lines.isEmpty()) {
                        runCatching { legacyFile.delete() }
                    }
                }

                provisionalSeeds.clear()
                faviconCacheDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(PROVISIONAL_PREFIX) && it.name.endsWith(PROVISIONAL_SUFFIX) }
                    ?.forEach { file ->
                        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
                        if (lines.size >= 2 && lines[0].isNotBlank() && lines[1].isNotBlank()) {
                            provisionalSeeds[lines[0].trim()] = lines[1].trim()
                        }
                    }
            }
            metadataLoaded = true
        }
    }

    private fun isLegacyUnverifiedKey(key: String): Boolean =
        legacyUnverifiedHashes.isNotEmpty() && legacyUnverifiedHashes.contains(sha256Hex(key))

    private suspend fun markKeyVerified(key: String) {
        val hash = sha256Hex(key)
        if (!legacyUnverifiedHashes.remove(hash)) return
        metadataMutex.withLock {
            withContext(Dispatchers.IO) {
                val legacyFile = File(faviconCacheDir, LEGACY_UNVERIFIED_FILE)
                if (legacyUnverifiedHashes.isEmpty()) {
                    runCatching { legacyFile.delete() }
                } else {
                    runCatching { legacyFile.writeText(legacyUnverifiedHashes.joinToString("\n")) }
                }
            }
        }
    }

    private suspend fun updateProvisionalSeed(authorityKey: String, pathKey: String?) {
        if (pathKey == null) {
            if (provisionalSeeds.remove(authorityKey) != null) {
                withContext(Dispatchers.IO) {
                    runCatching { provisionalSeedFile(authorityKey).delete() }
                }
            }
        } else {
            provisionalSeeds[authorityKey] = pathKey
            withContext(Dispatchers.IO) {
                runCatching { provisionalSeedFile(authorityKey).writeText("$authorityKey\n$pathKey") }
            }
        }
    }

    private fun provisionalSeedFile(authorityKey: String): File =
        File(faviconCacheDir, "$PROVISIONAL_PREFIX${sha256Hex(authorityKey)}$PROVISIONAL_SUFFIX")

    /**
     * Clears all cached website favicons from RAM and disk while keeping user-imported
     * custom workspace/item photos intact.
     */
    suspend fun clearFaviconCache() {
        fetchGate.invalidateAll()
        subpathFetchGate.invalidateAll()
        publicCoordinator.clearMemoryState()
        synchronized(memoryCache) {
            val hostKeys = memoryCache.snapshot().keys.filter { it.startsWith("host:") }
            hostKeys.forEach { memoryCache.remove(it) }
        }
        metadataMutex.withLock {
            legacyUnverifiedHashes.clear()
            provisionalSeeds.clear()
            withContext(Dispatchers.IO) {
                runCatching {
                    faviconCacheDir.listFiles()?.forEach { file ->
                        if (file.isFile) runCatching { file.delete() }
                    }
                    File(faviconCacheDir, CACHE_V2_MARKER_FILE).writeText("2")
                }
            }
            metadataLoaded = true
        }
        bumpRevision()
    }

    /**
     * Records a live WebView favicon (from `WebChromeClient.onReceivedIcon`).
     * Copies the WebView-owned bitmap synchronously on the caller thread before hopping
     * dispatchers so WebView recycling can never crash background scaling.
     * Private tabs are strictly stored in volatile per-tab memory.
     *
     * [aliasUrl] may pass a bound pinned root URL (e.g. `https://192.168.86.200:8989/`) that
     * redirected to an internal route (`/series`) on the exact same `authorityKey`, ensuring
     * the pinned item badge also receives the live icon without polluting unrelated subpaths.
     */
    suspend fun recordWebViewFavicon(
        url: String,
        bitmap: Bitmap,
        isPrivate: Boolean,
        tabId: String? = null,
        aliasUrl: String? = null
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val scope = resolveScope(url)
        if (!isPrivate && scope == null) return

        // Fast short-circuit before copying a full-resolution WebView bitmap on the Main thread
        // when we already hold a strictly higher-res verified icon for a LAN or root key.
        if (!isPrivate && scope != null && aliasUrl.isNullOrBlank()) {
            val canFastSkip = if (scope.isSubpathScoped) {
                true
            } else {
                scope.pathKey == null &&
                    metadataLoaded &&
                    !isLegacyUnverifiedKey(scope.authorityKey) &&
                    provisionalSeeds[scope.authorityKey] == null &&
                    !publicCoordinator.hasBaselineSubpathPending(scope.authorityKey)
            }
            if (canFastSkip) {
                val targetDim = minOf(maxOf(bitmap.width, bitmap.height), FAVICON_MAX_PX)
                val existing = synchronized(memoryCache) { memoryCache.get(hostKey(scope.primaryKey)) }
                if (existing != null && existing.width > targetDim && existing.width >= 48) {
                    return
                }
            }
        }

        val safeCopy = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            ?: return
        withContext(Dispatchers.Default) {
            val normalized = scaleBitmapIfNeeded(safeCopy, FAVICON_MAX_PX)
            if (normalized != null && normalized !== safeCopy) {
                safeCopy.recycle()
            }
            if (normalized == null) {
                if (!safeCopy.isRecycled) safeCopy.recycle()
                return@withContext
            }
            if (isPrivate) {
                if (!tabId.isNullOrBlank()) {
                    privateTabIcons.put(tabId, scope?.primaryKey, normalized)
                    bumpRevision(tabId)
                } else if (!normalized.isRecycled) {
                    normalized.recycle()
                }
                return@withContext
            }
            val nonNullScope = scope ?: run {
                if (!normalized.isRecycled) normalized.recycle()
                return@withContext
            }

            ensureCacheMetadataLoaded()

            if (nonNullScope.isSubpathScoped) {
                val targetKey = nonNullScope.primaryKey
                val storedPrimary = recordBitmapForKey(targetKey, normalized)
                var storedAlias = false
                if (!aliasUrl.isNullOrBlank()) {
                    val aliasScope = resolveScope(aliasUrl)
                    val aliasAlreadyCached = aliasScope != null && hasCachedIconForKey(aliasScope.primaryKey)
                    if (shouldWriteAliasFavicon(
                            scope = nonNullScope,
                            aliasScope = aliasScope,
                            targetKey = targetKey,
                            aliasAlreadyCached = aliasAlreadyCached
                        )
                    ) {
                        storedAlias = recordBitmapForKey(aliasScope!!.primaryKey, normalized)
                    }
                }
                if (!storedPrimary && !storedAlias && !normalized.isRecycled) {
                    normalized.recycle()
                }
                return@withContext
            }

            val sampled = toSampledIcon(normalized)
            if (sampled == null) {
                if (!normalized.isRecycled) normalized.recycle()
                return@withContext
            }

            val result = publicCoordinator.recordWebViewIcon(
                originalUrl = url,
                scope = nonNullScope,
                originRootUrl = buildOriginRootUrl(url, nonNullScope),
                incoming = sampled
            )
            var storedAlias = false
            val targetKey = result.writtenKey
            if (!aliasUrl.isNullOrBlank() && targetKey != null) {
                val aliasScope = resolveScope(aliasUrl)
                val aliasAlreadyCached = aliasScope != null && hasCachedIconForKey(aliasScope.primaryKey)
                if (shouldWriteAliasFavicon(
                        scope = nonNullScope,
                        aliasScope = aliasScope,
                        targetKey = targetKey,
                        aliasAlreadyCached = aliasAlreadyCached
                    )
                ) {
                    storedAlias = recordBitmapForKey(aliasScope!!.primaryKey, normalized)
                }
            }
            if (!result.retainedBitmap && !storedAlias && !normalized.isRecycled) {
                normalized.recycle()
            }
        }
    }

    private suspend fun hasCachedIconForKey(cacheKey: String): Boolean {
        val inMemory = synchronized(memoryCache) { memoryCache.get(hostKey(cacheKey)) != null }
        if (inMemory) return true
        return withContext(Dispatchers.IO) { diskFileForHost(cacheKey).isFile }
    }

    private suspend fun recordBitmapForKey(cacheKey: String, bitmap: Bitmap): Boolean {
        val existing = synchronized(memoryCache) { memoryCache.get(hostKey(cacheKey)) }
            ?: withContext(Dispatchers.IO) { readDiskFavicon(cacheKey) }?.also {
                synchronized(memoryCache) { memoryCache.put(hostKey(cacheKey), it) }
            }
        if (existing != null) {
            if (existing.width > bitmap.width && existing.width >= 48) {
                return false
            }
            if (existing.width >= bitmap.width && bitmapsLookSimilar(existing, bitmap)) {
                return false
            }
        }
        fetchGate.clearNegativeCache(cacheKey)
        subpathFetchGate.clearNegativeCache(cacheKey)
        putHostBitmap(cacheKey, bitmap, writeDisk = true)
        markKeyVerified(cacheKey)
        return true
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
     * Returns the favicon bitmap for [url], checking memory -> disk -> direct origin/subpath -> DDG fallback.
     * Deduplicates concurrent fetches for the same scope key on [repoScope] so individual caller
     * cancellations never abort in-flight downloads or poison the negative cache.
     */
    suspend fun getOrFetchFavicon(
        url: String,
        privateTabId: String? = null,
        probeDistinctSubpath: Boolean = true
    ): Bitmap? {
        if (privateTabId != null) {
            // Never trigger network or normal-cache reads for private tabs
            return getCachedFavicon(url, privateTabId)
        }
        val scope = resolveScope(url) ?: return null
        ensureCacheMetadataLoaded()

        if (!scope.isSubpathScoped) {
            return publicCoordinator.getOrFetch(
                originalUrl = url,
                scope = scope,
                originRootUrl = buildOriginRootUrl(url, scope),
                probeDistinctSubpath = probeDistinctSubpath
            )
        }

        getCachedFaviconForScope(scope, privateTabId = null)?.let { return it }

        val startGen = fetchGate.currentGeneration()
        val diskHit = withContext(Dispatchers.IO) { readDiskFavicon(scope.primaryKey) }
        if (diskHit != null) {
            if (fetchGate.currentGeneration() == startGen) {
                synchronized(memoryCache) { memoryCache.put(hostKey(scope.primaryKey), diskHit) }
                return diskHit
            }
            return null
        }

        val fetchKey = scope.primaryKey
        return fetchGate.getOrFetch(
            host = fetchKey,
            fetcher = { fetchFaviconFromNetwork(url, scope)?.let { toSampledIcon(it) } },
            onFetched = { fetched, _ ->
                putHostBitmap(fetchKey, fetched.value, writeDisk = true)
            }
        )?.value
    }

    private data class HtmlDiscoveryResult(
        val succeeded: Boolean,
        val isAuthBlocked: Boolean = false,
        val finalUrl: String? = null,
        val candidates: List<String> = emptyList()
    )

    private fun isHtmlResultWithinScope(result: HtmlDiscoveryResult, scope: FaviconScope): Boolean {
        if (!result.succeeded) return false
        val finalUrl = result.finalUrl ?: return true
        val finalScope = resolveScope(finalUrl) ?: return false
        return finalScope.authorityKey == scope.authorityKey && finalScope.pathKey == scope.pathKey
    }

    private suspend fun fetchPublicSubpathOnly(
        originalUrl: String,
        scope: FaviconScope
    ): SampledIcon<Bitmap>? {
        val targets = buildPublicSubpathProbeTargets(originalUrl, scope) ?: return null
        var htmlResult = discoverIconUrlsFromHtml(targets.htmlProbeUrl)
        if (!isHtmlResultWithinScope(htmlResult, scope) && targets.alternateHtmlProbeUrl != null) {
            val alternate = discoverIconUrlsFromHtml(targets.alternateHtmlProbeUrl)
            if (alternate.succeeded && isHtmlResultWithinScope(alternate, scope)) {
                htmlResult = alternate
            }
        }
        if (!htmlResult.succeeded || !isHtmlResultWithinScope(htmlResult, scope)) {
            return null
        }
        for (iconUrl in htmlResult.candidates) {
            downloadAndDecodeBitmap(iconUrl)?.let { bmp ->
                toSampledIcon(bmp)?.let { return it }
            }
        }
        for (directUrl in targets.directIconUrls) {
            downloadAndDecodeBitmap(directUrl)?.let { bmp ->
                toSampledIcon(bmp)?.let { return it }
            }
        }
        return null
    }

    private suspend fun fetchFaviconFromNetwork(originalUrl: String, scope: FaviconScope): Bitmap? {
        val targets = buildProbeTargets(originalUrl, scope)

        // 1. Parse target HTML <head> for high-res apple-touch-icon / icon links
        // (avoids sending unauthenticated GETs to deep URL paths or query strings)
        var htmlResult = discoverIconUrlsFromHtml(targets.htmlProbeUrl)
        if (!htmlResult.succeeded && targets.alternateHtmlProbeUrl != null) {
            val alternate = discoverIconUrlsFromHtml(targets.alternateHtmlProbeUrl)
            htmlResult = if (alternate.succeeded || alternate.isAuthBlocked) {
                alternate
            } else {
                htmlResult.copy(isAuthBlocked = htmlResult.isAuthBlocked || alternate.isAuthBlocked)
            }
        }
        for (iconUrl in htmlResult.candidates) {
            downloadAndDecodeBitmap(iconUrl)?.let { return it }
        }

        // 2. Direct /apple-touch-icon.png and /favicon.ico on the target base
        for (directUrl in targets.directIconUrls) {
            downloadAndDecodeBitmap(directUrl)?.let { return it }
        }

        // 2b. For a custom-port LAN app navigated to an internal route (e.g. :8989/series) whose
        // HTML returned 200 OK (with no explicit <link rel="icon">) or 401/403 (behind app/SSO auth),
        // fall back to the port's root icon. Never steal origin root icons for default-port subpath
        // reverse proxies (e.g. /radarr).
        if (targets.isSubpathProbe && scope.hasExplicitPort && (htmlResult.succeeded || htmlResult.isAuthBlocked)) {
            downloadAndDecodeBitmap("${targets.origin}/apple-touch-icon.png")?.let { return it }
            downloadAndDecodeBitmap("${targets.origin}/favicon.ico")?.let { return it }
        }

        // 3. DuckDuckGo fallback ONLY for public default-port domains (never LAN/IP or custom ports)
        if (!scope.hasExplicitPort && isPublicFallbackEligibleHost(scope.host)) {
            downloadAndDecodeBitmap("https://icons.duckduckgo.com/ip3/${scope.host}.ico")?.let { return it }
        }

        return null
    }

    private suspend fun discoverIconUrlsFromHtml(targetUrl: String): HtmlDiscoveryResult {
        var succeeded = false
        var isAuthBlocked = false
        var finalUrl = targetUrl
        val htmlSnippet = try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", FAVICON_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                .build()
            val call = httpClient.newCall(req)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 401 || resp.code == 403) {
                            isAuthBlocked = true
                        }
                        ""
                    } else {
                        succeeded = true
                        finalUrl = resp.request.url.toString()
                        val contentType = resp.header("Content-Type").orEmpty()
                        if (contentType.isNotBlank() && !contentType.contains("html", ignoreCase = true)) {
                            ""
                        } else {
                            val source = resp.body.source()
                            source.request(MAX_HTML_PEEK_BYTES)
                            val peek = source.buffer.clone()
                            val byteCount = minOf(peek.size, MAX_HTML_PEEK_BYTES)
                            peek.readUtf8(byteCount)
                        }
                    }
                }
            } finally {
                cancelHandle?.dispose()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            ""
        }

        if (htmlSnippet.isBlank()) {
            return HtmlDiscoveryResult(
                succeeded = succeeded,
                isAuthBlocked = isAuthBlocked,
                finalUrl = if (succeeded) finalUrl else null,
                candidates = emptyList()
            )
        }
        return HtmlDiscoveryResult(
            succeeded = succeeded,
            isAuthBlocked = isAuthBlocked,
            finalUrl = finalUrl,
            candidates = extractIconCandidatesFromHtml(htmlSnippet, finalUrl)
        )
    }

    private suspend fun downloadAndDecodeBitmap(url: String): Bitmap? {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", FAVICON_USER_AGENT)
                .build()
            val call = httpClient.newCall(req)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { resp ->
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
            } finally {
                cancelHandle?.dispose()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun putHostBitmap(key: String, bitmap: Bitmap, writeDisk: Boolean) {
        synchronized(memoryCache) { memoryCache.put(hostKey(key), bitmap) }
        if (writeDisk) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = diskFileForHost(key)
                    FileOutputStream(file).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
                    }
                }
            }
        }
        bumpRevision(key)
    }

    private suspend fun removeHostBitmap(key: String) {
        val removed = synchronized(memoryCache) { memoryCache.remove(hostKey(key)) != null }
        val deleted = withContext(Dispatchers.IO) {
            runCatching { diskFileForHost(key).takeIf { it.isFile }?.delete() ?: false }.getOrDefault(false)
        }
        if (removed || deleted) {
            bumpRevision(key)
        }
    }

    private fun toSampledIcon(bitmap: Bitmap): SampledIcon<Bitmap>? {
        val samples = sampleBitmapGrid(bitmap) ?: return null
        return SampledIcon(value = bitmap, width = bitmap.width, samplePixels = samples)
    }

    private fun readDiskFavicon(key: String): Bitmap? {
        val file = diskFileForHost(key)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun diskFileForHost(key: String): File = File(faviconCacheDir, "${sha256Hex(key)}.png")

    private fun bumpRevision(changedKey: String? = null) {
        if (changedKey != null) {
            if (keyRevisions.size > 1024) keyRevisions.clear()
            keyRevisions.compute(changedKey) { _, prev -> (prev ?: 0L) + 1L }
            val authPrefix = changedKey.substringBefore('/')
            if (authPrefix != changedKey) {
                keyRevisions.compute(authPrefix) { _, prev -> (prev ?: 0L) + 1L }
            }
        } else {
            keyRevisions.clear()
        }
        _revision.update { it + 1L }
    }

    private fun hostKey(key: String) = "host:$key"
    private fun imageKey(fileName: String) = "img:$fileName"

    companion object {
        private const val MAX_MEMORY_CACHE_KB = 4 * 1024
        private const val FAVICON_MAX_PX = 96
        private const val CUSTOM_IMAGE_SIZE_PX = 192
        private const val MAX_HTML_PEEK_BYTES = 128L * 1024L
        private const val MAX_ICON_DOWNLOAD_BYTES = 512 * 1024
        private const val NEGATIVE_CACHE_MS = 5 * 60 * 1000L
        private const val MAX_PATH_SEGMENT_LEN = 64
        private const val SAMPLE_GRID_SIZE = 8
        private const val CACHE_V2_MARKER_FILE = ".cache_v2_ready"
        private const val LEGACY_UNVERIFIED_FILE = ".legacy_unverified"
        private const val PROVISIONAL_PREFIX = "provisional_"
        private const val PROVISIONAL_SUFFIX = ".seed"
        private const val FAVICON_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

        private val LINK_TAG_REGEX = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        /**
         * Stable UI/cache key that changes whenever the host, non-default port, or first
         * path segment changes (ensuring Compose badges re-evaluate across self-hosted apps).
         */
        fun cacheKeyForUrl(url: String): String? {
            val scope = resolveScope(url) ?: return null
            return scope.pathKey ?: scope.authorityKey
        }

        /**
         * Scope key for private-tab icon isolation (differentiates ports and LAN subpaths without
         * dropping icons across normal article navigations on public sites).
         */
        fun privateScopeKey(url: String): String? = resolveScope(url)?.primaryKey

        internal fun shouldWriteAliasFavicon(
            scope: FaviconScope,
            aliasScope: FaviconScope?,
            targetKey: String,
            aliasAlreadyCached: Boolean
        ): Boolean {
            if (aliasScope == null || aliasAlreadyCached) return false
            return aliasScope.hasExplicitPort &&
                aliasScope.authorityKey == scope.authorityKey &&
                aliasScope.pathKey == null &&
                scope.pathKey != null &&
                aliasScope.primaryKey != targetKey
        }

        internal fun resolveScope(url: String): FaviconScope? {
            val trimmed = url.trim()
            if (trimmed.isBlank() || trimmed.equals(OmniboxEngine.NEW_TAB_URL, ignoreCase = true)) return null
            val scheme: String
            val schemeEnd: Int
            when {
                trimmed.startsWith("https://", ignoreCase = true) -> {
                    scheme = "https"
                    schemeEnd = 8
                }
                trimmed.startsWith("http://", ignoreCase = true) -> {
                    scheme = "http"
                    schemeEnd = 7
                }
                else -> return null
            }

            val rest = trimmed.substring(schemeEnd)
            if (rest.isEmpty()) return null
            var authEnd = rest.length
            for (i in rest.indices) {
                val c = rest[i]
                if (c == '/' || c == '?' || c == '#' || c == '\\') {
                    authEnd = i
                    break
                }
            }
            if (authEnd <= 0) return null
            val rawAuth = rest.substring(0, authEnd)
            if (rawAuth.any { it.isWhitespace() }) return null
            val hostPort = rawAuth.substringAfterLast('@')
            if (hostPort.isEmpty()) return null

            val rawHost: String
            val port: Int
            if (hostPort.startsWith("[")) {
                val closeIdx = hostPort.indexOf(']')
                if (closeIdx <= 1) return null
                rawHost = hostPort.substring(0, closeIdx + 1)
                val after = hostPort.substring(closeIdx + 1)
                port = when {
                    after.isEmpty() -> -1
                    after.startsWith(":") -> after.substring(1).toIntOrNull() ?: return null
                    else -> return null
                }
            } else {
                val colonIdx = hostPort.lastIndexOf(':')
                if (colonIdx >= 0) {
                    if (colonIdx == 0) return null
                    rawHost = hostPort.substring(0, colonIdx)
                    port = hostPort.substring(colonIdx + 1).toIntOrNull() ?: return null
                } else {
                    rawHost = hostPort
                    port = -1
                }
            }

            val rawPath = if (authEnd < rest.length && rest[authEnd] == '/') {
                var pathEnd = rest.length
                for (i in authEnd until rest.length) {
                    val c = rest[i]
                    if (c == '?' || c == '#' || c == '\\') {
                        pathEnd = i
                        break
                    }
                }
                rest.substring(authEnd, pathEnd)
            } else {
                ""
            }

            val normalizedHost = OmniboxEngine.normalizeHost(rawHost)
            if (normalizedHost.isBlank()) return null

            val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            val authorityKey = if (port in 1..65535 && !isDefaultPort) {
                if (normalizedHost.contains(':')) "[$normalizedHost]:$port" else "$normalizedHost:$port"
            } else {
                normalizedHost
            }

            val rawFirstSegment = extractRawFirstPathSegment(rawPath)
            val firstSegment = rawFirstSegment?.lowercase(Locale.ROOT)
            val pathKey = firstSegment?.let { "$authorityKey/$it" }
            val isSubpathScoped = OmniboxEngine.isLocalHostOrLan(normalizedHost)

            return FaviconScope(
                host = normalizedHost,
                authorityKey = authorityKey,
                pathKey = pathKey,
                firstSegment = firstSegment,
                rawFirstSegment = rawFirstSegment,
                isSubpathScoped = isSubpathScoped
            )
        }

        private fun extractRawFirstPathSegment(rawPath: String): String? {
            if (rawPath.isBlank()) return null
            val withoutLeadingSlash = rawPath.trimStart('/')
            if (withoutLeadingSlash.isEmpty()) return null
            val first = withoutLeadingSlash.substringBefore('/').trim()
            if (first.isEmpty() || first.length > MAX_PATH_SEGMENT_LEN) return null
            if (first == "." || first == ".." || first.contains('\\')) return null
            return first
        }

        internal fun resolveOrigin(originalUrl: String, scope: FaviconScope): String {
            val baseUri = runCatching { URI(originalUrl) }.getOrNull()
            val scheme = baseUri?.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) }
                ?.lowercase(Locale.ROOT)
                ?: if (originalUrl.startsWith("http://", true)) "http" else "https"
            val fallbackAuthority = if (scope.host.contains(':') && !scope.authorityKey.startsWith("[")) {
                "[${scope.host}]"
            } else {
                scope.authorityKey
            }
            val authority = baseUri?.rawAuthority
                ?.substringAfterLast('@')
                ?.takeIf { it.isNotBlank() }
                ?: fallbackAuthority
            return "$scheme://$authority"
        }

        internal fun buildOriginRootUrl(originalUrl: String, scope: FaviconScope): String =
            "${resolveOrigin(originalUrl, scope)}/"

        internal fun buildPublicSubpathProbeTargets(originalUrl: String, scope: FaviconScope): FaviconProbeTargets? {
            if (scope.isSubpathScoped) return null
            val probeSegment = scope.rawFirstSegment ?: scope.firstSegment ?: return null
            val origin = resolveOrigin(originalUrl, scope)
            val appBase = "$origin/$probeSegment"
            val cleanUrl = originalUrl.substringBefore('?').substringBefore('#')
            val preferBareBase = cleanUrl.equals(appBase, ignoreCase = true)
            return FaviconProbeTargets(
                origin = origin,
                htmlProbeUrl = if (preferBareBase) appBase else "$appBase/",
                alternateHtmlProbeUrl = if (preferBareBase) "$appBase/" else appBase,
                directIconUrls = listOf(
                    "$appBase/apple-touch-icon.png",
                    "$appBase/favicon.ico"
                ),
                isSubpathProbe = true
            )
        }

        internal fun buildProbeTargets(originalUrl: String, scope: FaviconScope): FaviconProbeTargets {
            val origin = resolveOrigin(originalUrl, scope)

            val probeSegment = scope.rawFirstSegment ?: scope.firstSegment
            if (scope.isSubpathScoped && probeSegment != null) {
                val appBase = "$origin/$probeSegment"
                return FaviconProbeTargets(
                    origin = origin,
                    htmlProbeUrl = "$appBase/",
                    alternateHtmlProbeUrl = appBase,
                    directIconUrls = listOf(
                        "$appBase/apple-touch-icon.png",
                        "$appBase/favicon.ico"
                    ),
                    isSubpathProbe = true
                )
            }

            return FaviconProbeTargets(
                origin = origin,
                htmlProbeUrl = "$origin/",
                alternateHtmlProbeUrl = null,
                directIconUrls = listOf(
                    "$origin/apple-touch-icon.png",
                    "$origin/favicon.ico"
                ),
                isSubpathProbe = false
            )
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
                val relTokens = rel.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                val isIconRel = relTokens.any {
                    it == "icon" ||
                        it == "apple-touch-icon" ||
                        it == "apple-touch-icon-precomposed" ||
                        it == "fluid-icon"
                }
                if (!isIconRel || relTokens.contains("mask-icon")) continue
                val rawHref = attrs["href"]?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val href = rawHref.replace("&amp;", "&")
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

        private fun sampleBitmapGrid(bitmap: Bitmap, grid: Int = SAMPLE_GRID_SIZE): IntArray? {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            return runCatching {
                val thumb = if (bitmap.width == grid && bitmap.height == grid && bitmap.config == Bitmap.Config.ARGB_8888) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, grid, grid, true)
                }
                try {
                    IntArray(grid * grid).also { samples ->
                        thumb.getPixels(samples, 0, grid, 0, 0, grid, grid)
                    }
                } finally {
                    if (thumb !== bitmap && !thumb.isRecycled) {
                        thumb.recycle()
                    }
                }
            }.getOrNull()
        }

        internal fun arePixelSamplesSimilar(pixelsA: IntArray, pixelsB: IntArray): Boolean {
            if (pixelsA.isEmpty() || pixelsA.size != pixelsB.size) return false
            var totalDiff = 0L
            var majorDiffPixels = 0
            for (i in pixelsA.indices) {
                val pa = pixelsA[i]
                val pb = pixelsB[i]
                val pixelDiff = abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF)) +
                    abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF)) +
                    abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF)) +
                    abs((pa and 0xFF) - (pb and 0xFF))
                totalDiff += pixelDiff
                if (pixelDiff > 144) { // >36 mean per-channel difference on this sample cell
                    majorDiffPixels++
                }
            }
            val meanChannelDelta = totalDiff / (pixelsA.size * 4L)
            val maxMajorDiffPixels = pixelsA.size / 12
            return meanChannelDelta <= 10L && majorDiffPixels <= maxMajorDiffPixels
        }

        private fun bitmapsLookSimilar(a: Bitmap, b: Bitmap): Boolean {
            if (a === b) return true
            val samplesA = sampleBitmapGrid(a) ?: return false
            val samplesB = sampleBitmapGrid(b) ?: return false
            return arePixelSamplesSimilar(samplesA, samplesB)
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
