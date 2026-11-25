package app.overbay.swiftfox.features.adblock

import android.content.Context
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class AdblockEngineStatus(
    val isNativeRust: Boolean = false,
    val totalRules: Int = 0,
    val isCompiling: Boolean = false
)

/**
 * Coordinates native `adblock-rust` compilation, serialized cache loading (`engine.dat`),
 * Kotlin fallback matching, network request interception, and two-stage cosmetic CSS lookup.
 */
class AdblockEngine private constructor(
    private val context: Context?,
    initialMatcher: AdblockMatcher?,
    initialStatus: AdblockEngineStatus
) {
    constructor(context: Context) : this(
        context = context.applicationContext ?: context,
        initialMatcher = null,
        initialStatus = AdblockEngineStatus()
    )

    internal constructor(matcher: AdblockMatcher, totalRules: Int = 0) : this(
        context = null,
        initialMatcher = matcher,
        initialStatus = AdblockEngineStatus(
            isNativeRust = matcher is NativeAdblockMatcher,
            totalRules = totalRules,
            isCompiling = false
        )
    )

    private val compileMutex = Mutex()
    private val swapLock = Any()
    private val closed = AtomicBoolean(false)

    @Volatile
    private var activeMatcher: AdblockMatcher? = initialMatcher

    @Volatile
    private var activeCacheFingerprint: String? = null

    private val _status = MutableStateFlow(initialStatus)
    val status: StateFlow<AdblockEngineStatus> = _status.asStateFlow()

    /**
     * Fast cold-start path: immediately loads `engine.dat` directly in Rust on IO before Room
     * finishes seeding/emitting, closing the startup fail-open window with zero Java byte copies.
     */
    suspend fun tryLoadWarmCacheFast(): Boolean {
        val ctx = context ?: return false
        if (closed.get() || !NativeAdblockBridge.isLoaded) return false
        return compileMutex.withLock {
            if (closed.get() || activeMatcher != null) return@withLock activeMatcher != null
            withContext(Dispatchers.IO) {
                val dir = adblockDir(ctx)
                val engineFile = File(dir, ENGINE_CACHE_FILE)
                val metaFile = File(dir, ENGINE_META_FILE)
                if (!engineFile.isFile || engineFile.length() <= 0L) return@withContext false
                val meta = readCacheMeta(metaFile) ?: return@withContext false
                val loadedMatcher = runCatching {
                    val handle = NativeAdblockBridge.nativeDeserializeFromFile(engineFile.absolutePath)
                    if (handle != 0L) NativeAdblockMatcher(handle) else null
                }.getOrNull() ?: return@withContext false

                if (!swapMatcher(loadedMatcher, meta.fingerprint)) {
                    return@withContext false
                }
                _status.value = AdblockEngineStatus(
                    isNativeRust = true,
                    totalRules = meta.ruleCount,
                    isCompiling = false
                )
                true
            }
        }
    }

    suspend fun initialize(
        enabledLists: List<FilterListEntity> = emptyList(),
        hasAnyConfiguredLists: Boolean = enabledLists.isNotEmpty()
    ) {
        val ctx = context ?: return
        if (closed.get()) return
        compileMutex.withLock {
            if (closed.get()) return
            withContext(Dispatchers.IO) {
                val expectedFingerprint = computeCacheFingerprint(ctx, enabledLists, hasAnyConfiguredLists)
                if (activeMatcher != null && activeCacheFingerprint == expectedFingerprint) {
                    return@withContext
                }
                _status.value = _status.value.copy(isCompiling = true)
                try {
                    val dir = adblockDir(ctx)
                    val engineFile = File(dir, ENGINE_CACHE_FILE)
                    val metaFile = File(dir, ENGINE_META_FILE)

                    if (NativeAdblockBridge.isLoaded && engineFile.isFile && engineFile.length() > 0L) {
                        val meta = readCacheMeta(metaFile)
                        if (meta != null && meta.fingerprint == expectedFingerprint) {
                            val loadedMatcher = runCatching {
                                val handle = NativeAdblockBridge.nativeDeserializeFromFile(engineFile.absolutePath)
                                if (handle != 0L) NativeAdblockMatcher(handle) else null
                            }.getOrNull()

                            if (loadedMatcher != null) {
                                if (!swapMatcher(loadedMatcher, expectedFingerprint)) {
                                    return@withContext
                                }
                                _status.value = AdblockEngineStatus(
                                    isNativeRust = true,
                                    totalRules = meta.ruleCount,
                                    isCompiling = false
                                )
                                return@withContext
                            }
                        }
                    }

                    compileFromDiskAndSwapLocked(
                        ctx = ctx,
                        enabledLists = enabledLists,
                        hasAnyConfiguredLists = hasAnyConfiguredLists,
                        fingerprint = expectedFingerprint
                    )
                } finally {
                    if (_status.value.isCompiling) {
                        _status.value = _status.value.copy(isCompiling = false)
                    }
                }
            }
        }
    }

    suspend fun rebuildFromLists(
        enabledLists: List<FilterListEntity>,
        hasAnyConfiguredLists: Boolean = true
    ) {
        val ctx = context ?: return
        if (closed.get()) return
        compileMutex.withLock {
            if (closed.get()) return
            withContext(Dispatchers.IO) {
                _status.value = _status.value.copy(isCompiling = true)
                try {
                    val fingerprint = computeCacheFingerprint(ctx, enabledLists, hasAnyConfiguredLists)
                    compileFromDiskAndSwapLocked(
                        ctx = ctx,
                        enabledLists = enabledLists,
                        hasAnyConfiguredLists = hasAnyConfiguredLists,
                        fingerprint = fingerprint
                    )
                } finally {
                    if (_status.value.isCompiling) {
                        _status.value = _status.value.copy(isCompiling = false)
                    }
                }
            }
        }
    }

    fun hasSerializedCache(): Boolean {
        val ctx = context ?: return false
        val engineFile = File(adblockDir(ctx), ENGINE_CACHE_FILE)
        return engineFile.isFile && engineFile.length() > 0L
    }

    fun shouldBlock(
        requestUrl: String,
        pageUrl: String,
        resourceType: String,
        globalEnabled: Boolean,
        siteDisabled: Boolean
    ): Boolean {
        if (!globalEnabled || siteDisabled) return false
        val matcher = activeMatcher ?: return false
        if (!isHttpOrHttps(requestUrl)) return false

        val reqHost = OmniboxEngine.extractHost(requestUrl).trim('[', ']')
        if (reqHost.isBlank() || OmniboxEngine.isLocalHostOrLan(reqHost)) return false

        val typeCode = NativeAdblockBridge.codeForRequestType(resourceType)
        val first = matcher.checkNetworkRequestTriState(requestUrl, pageUrl, resourceType, typeCode)
        if (first >= 0) return first == 1
        if (closed.get()) return false
        val retryMatcher = activeMatcher ?: return false
        return retryMatcher.checkNetworkRequestTriState(requestUrl, pageUrl, resourceType, typeCode) == 1
    }

    fun cosmeticForUrl(
        url: String,
        globalEnabled: Boolean,
        cosmeticEnabled: Boolean,
        siteDisabled: Boolean
    ): CosmeticPayload {
        if (!globalEnabled || !cosmeticEnabled || siteDisabled) return CosmeticPayload()
        val matcher = activeMatcher ?: return CosmeticPayload()
        if (!isHttpOrHttps(url)) return CosmeticPayload()

        val host = OmniboxEngine.extractHost(url).trim('[', ']')
        if (host.isBlank() || OmniboxEngine.isLocalHostOrLan(host)) return CosmeticPayload()

        return matcher.cosmeticResources(url)
    }

    fun genericCosmeticCssFromJson(
        url: String,
        classesJson: String,
        idsJson: String,
        exceptions: Set<String>,
        globalEnabled: Boolean,
        cosmeticEnabled: Boolean,
        siteDisabled: Boolean
    ): String {
        if (!globalEnabled || !cosmeticEnabled || siteDisabled) return ""
        val matcher = activeMatcher ?: return ""
        if (!isHttpOrHttps(url)) return ""
        if ((classesJson.isBlank() || classesJson == "[]") && (idsJson.isBlank() || idsJson == "[]")) return ""

        val host = OmniboxEngine.extractHost(url).trim('[', ']')
        if (host.isBlank() || OmniboxEngine.isLocalHostOrLan(host)) return ""

        return matcher.hiddenClassIdCssFromJson(classesJson, idsJson, exceptions)
    }

    fun inferResourceType(url: String, headers: Map<String, String>): String {
        val secFetchDest = headerIgnoreCase(headers, "Sec-Fetch-Dest")?.lowercase(Locale.ROOT)
        when (secFetchDest) {
            "script" -> return "script"
            "style" -> return "stylesheet"
            "image" -> return "image"
            "font" -> return "font"
            "audio", "video", "track" -> return "media"
            "document" -> return "document"
            "iframe", "frame" -> return "subdocument"
            "empty" -> return "xmlhttprequest"
        }

        val accept = headerIgnoreCase(headers, "Accept")?.lowercase(Locale.ROOT).orEmpty()
        if (accept.isNotBlank() && accept != "*/*") {
            when {
                // Check HTML before image/ because Chromium's document/iframe Accept header
                // lists text/html alongside image/avif,image/webp on plain http:// origins.
                accept.contains("text/html") || accept.contains("application/xhtml+xml") -> return "subdocument"
                accept.contains("text/css") -> return "stylesheet"
                accept.contains("image/") -> return "image"
                accept.contains("font/") || accept.contains("application/font") -> return "font"
                accept.contains("video/") || accept.contains("audio/") -> return "media"
                accept.contains("javascript") -> return "script"
            }
        }

        val extension = extractPathExtension(url)
        return when (extension) {
            "js", "mjs" -> "script"
            "css" -> "stylesheet"
            "png", "jpg", "jpeg", "webp", "gif", "svg", "ico", "avif" -> "image"
            "woff", "woff2", "ttf", "otf", "eot" -> "font"
            "mp4", "webm", "m3u8", "mpd", "mp3", "ogg" -> "media"
            else -> "other"
        }
    }

    fun close() {
        closed.set(true)
        val previous = synchronized(swapLock) {
            val prev = activeMatcher
            activeMatcher = null
            activeCacheFingerprint = null
            prev
        }
        previous?.close()
    }

    private fun compileFromDiskAndSwapLocked(
        ctx: Context,
        enabledLists: List<FilterListEntity>,
        hasAnyConfiguredLists: Boolean,
        fingerprint: String
    ) {
        if (closed.get()) return
        val activeLists = enabledLists.filter { it.enabled }
        val compileZero = hasAnyConfiguredLists && activeLists.isEmpty()

        val starter = if (compileZero) "" else readStarterFilters(ctx)
        val starterCount = if (starter.isEmpty()) 0 else countFilterRules(starter)
        val listsDir = File(adblockDir(ctx), LISTS_SUBDIR)
        val existingFiles = if (compileZero) {
            emptyList()
        } else {
            activeLists.mapNotNull { entity ->
                val f = File(listsDir, "${entity.id}.txt")
                if (f.isFile) entity to f else null
            }
        }
        val listRuleSum = existingFiles.sumOf { (entity, file) ->
            if (entity.ruleCount > 0) entity.ruleCount else countFilterRulesInFile(file)
        }
        val ruleCount = starterCount + listRuleSum

        val nativeHandle = if (NativeAdblockBridge.isLoaded) {
            if (compileZero) {
                NativeAdblockBridge.nativeCreateFromRules("")
            } else {
                val paths = existingFiles.map { (_, f) -> f.absolutePath }.toTypedArray()
                NativeAdblockBridge.nativeCreateFromFiles(starter, paths)
            }
        } else {
            0L
        }

        val newMatcher: AdblockMatcher = if (nativeHandle != 0L) {
            NativeAdblockMatcher(nativeHandle)
        } else {
            val combinedRules = buildRulesText(ctx, enabledLists, hasAnyConfiguredLists)
            FallbackKotlinAdblockMatcher(combinedRules)
        }

        if (closed.get()) {
            newMatcher.close()
            return
        }

        // Serialize directly to file in Rust before swapping so zero multi-MB ByteArrays hit Java heap
        if (newMatcher is NativeAdblockMatcher && !closed.get()) {
            val dir = adblockDir(ctx)
            val engineFile = File(dir, ENGINE_CACHE_FILE)
            if (newMatcher.serializeToFile(engineFile.absolutePath)) {
                writeAtomicText(File(dir, ENGINE_META_FILE), "$CACHE_META_VERSION|$fingerprint|$ruleCount")
            }
        }

        if (!swapMatcher(newMatcher, fingerprint)) {
            return
        }
        _status.value = AdblockEngineStatus(
            isNativeRust = newMatcher is NativeAdblockMatcher,
            totalRules = ruleCount,
            isCompiling = false
        )
    }

    private fun swapMatcher(newMatcher: AdblockMatcher, fingerprint: String? = null): Boolean {
        var shouldCloseNew = false
        val previous = synchronized(swapLock) {
            if (closed.get()) {
                shouldCloseNew = true
                null
            } else {
                val prev = activeMatcher
                activeMatcher = newMatcher
                activeCacheFingerprint = fingerprint
                prev
            }
        }
        if (shouldCloseNew) {
            newMatcher.close()
            return false
        }
        previous?.close()
        return true
    }

    private fun buildRulesText(
        ctx: Context,
        enabledLists: List<FilterListEntity>,
        hasAnyConfiguredLists: Boolean
    ): String {
        val activeLists = enabledLists.filter { it.enabled }
        // When the user has configured lists and explicitly disabled all of them, compile 0 rules
        if (hasAnyConfiguredLists && activeLists.isEmpty()) {
            return ""
        }

        val sb = StringBuilder(64 * 1024)
        val starter = readStarterFilters(ctx)
        if (starter.isNotEmpty()) {
            sb.append(starter).append('\n')
        }

        val listsDir = File(adblockDir(ctx), LISTS_SUBDIR)
        activeLists.forEach { entity ->
            val listFile = File(listsDir, "${entity.id}.txt")
            if (listFile.isFile) {
                runCatching { listFile.readText() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { sb.append(it).append('\n') }
            }
        }
        return sb.toString()
    }

    private data class CacheMeta(val fingerprint: String, val ruleCount: Int)

    private fun readCacheMeta(metaFile: File): CacheMeta? =
        runCatching {
            if (!metaFile.isFile) return@runCatching null
            val parts = metaFile.readText().trim().split('|')
            if (parts.size != 3 || parts[0] != CACHE_META_VERSION) return@runCatching null
            val count = parts[2].toIntOrNull() ?: return@runCatching null
            CacheMeta(fingerprint = parts[1], ruleCount = count)
        }.getOrNull()

    private fun computeCacheFingerprint(
        ctx: Context,
        enabledLists: List<FilterListEntity>,
        hasAnyConfiguredLists: Boolean
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CACHE_META_VERSION.toByteArray(Charsets.UTF_8))
        digest.update(if (hasAnyConfiguredLists) 1.toByte() else 0.toByte())
        val activeLists = enabledLists.filter { it.enabled }.sortedBy { it.id }
        if (!hasAnyConfiguredLists || activeLists.isNotEmpty()) {
            digest.update(readStarterFilters(ctx).toByteArray(Charsets.UTF_8))
        }
        val listsDir = File(adblockDir(ctx), LISTS_SUBDIR)
        activeLists.forEach { entity ->
            val f = File(listsDir, "${entity.id}.txt")
            // Omit entity.lastUpdatedAt so HTTP 304 checks don't invalidate engine.dat cache
            val sig = "${entity.id}:${if (f.isFile) f.length() else -1L}:${f.lastModified()};"
            digest.update(sig.toByteArray(Charsets.UTF_8))
        }
        val hash = digest.digest()
        val hex = CharArray(24)
        for (i in 0 until 12) {
            val b = hash[i].toInt() and 0xFF
            hex[i * 2] = HEX_CHARS[b ushr 4]
            hex[i * 2 + 1] = HEX_CHARS[b and 0x0F]
        }
        return String(hex)
    }

    private fun readStarterFilters(ctx: Context): String =
        runCatching {
            ctx.assets.open(STARTER_ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrDefault("")

    private fun adblockDir(ctx: Context): File =
        File(ctx.filesDir, ADBLOCK_DIR).apply { mkdirs() }

    private fun writeAtomicBytes(target: File, bytes: ByteArray): Boolean =
        runCatching {
            val parent = target.parentFile ?: error("Missing parent dir")
            val tmp = File.createTempFile("${target.name}.", ".tmp", parent)
            try {
                tmp.writeBytes(bytes)
                val renamed = if (target.exists()) {
                    tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))
                } else {
                    tmp.renameTo(target)
                }
                renamed
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }.getOrDefault(false)

    private fun writeAtomicText(target: File, content: String): Boolean =
        writeAtomicBytes(target, content.toByteArray(Charsets.UTF_8))

    private fun isHttpOrHttps(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    private fun headerIgnoreCase(headers: Map<String, String>, key: String): String? {
        headers[key]?.let { return it }
        for ((k, v) in headers) {
            if (k.equals(key, ignoreCase = true)) return v
        }
        return null
    }

    private fun extractPathExtension(url: String): String {
        val schemeIdx = url.indexOf("://")
        val afterAuthorityStart = if (schemeIdx >= 0) schemeIdx + 3 else 0
        val slashIdx = url.indexOf('/', afterAuthorityStart)
        if (slashIdx < 0 || slashIdx + 1 >= url.length) return ""
        var pathEnd = url.length
        for (i in slashIdx + 1 until url.length) {
            val c = url[i]
            if (c == '?' || c == '#') {
                pathEnd = i
                break
            }
        }
        if (pathEnd <= slashIdx + 1) return ""
        var lastSlash = slashIdx
        for (i in pathEnd - 1 downTo slashIdx + 1) {
            if (url[i] == '/') {
                lastSlash = i
                break
            }
        }
        var dotIdx = -1
        for (i in pathEnd - 1 downTo lastSlash + 1) {
            if (url[i] == '.') {
                dotIdx = i
                break
            }
        }
        if (dotIdx < 0 || dotIdx >= pathEnd - 1) return ""
        val extLen = pathEnd - (dotIdx + 1)
        if (extLen > 8) return ""
        return url.substring(dotIdx + 1, pathEnd).lowercase(Locale.ROOT)
    }

    private fun countFilterRulesInFile(file: File): Int =
        runCatching {
            file.useLines { lines ->
                var count = 0
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith('!') && !trimmed.startsWith('[')) {
                        count++
                    }
                }
                count
            }
        }.getOrDefault(0)

    companion object {
        private const val ADBLOCK_DIR = "adblock"
        private const val LISTS_SUBDIR = "lists"
        private const val ENGINE_CACHE_FILE = "engine.dat"
        private const val ENGINE_META_FILE = "engine.meta"
        private const val CACHE_META_VERSION = "v3"
        private const val STARTER_ASSET_PATH = "adblock/starter_filters.txt"
        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        fun createForTesting(rulesText: String): AdblockEngine {
            val matcher = FallbackKotlinAdblockMatcher(rulesText)
            return AdblockEngine(matcher = matcher, totalRules = countFilterRules(rulesText))
        }

        internal fun countFilterRules(rulesText: String): Int {
            var count = 0
            rulesText.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith('!') && !trimmed.startsWith('[')) {
                    count++
                }
            }
            return count
        }
    }
}
