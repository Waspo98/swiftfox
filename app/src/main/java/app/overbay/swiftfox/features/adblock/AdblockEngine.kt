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
import java.net.URI

data class AdblockEngineStatus(
    val isNativeRust: Boolean = false,
    val totalRules: Int = 0,
    val isCompiling: Boolean = false
)

/**
 * Coordinates native `adblock-rust` compilation, serialized cache loading (`engine.dat`),
 * Kotlin fallback matching, network request interception, and cosmetic CSS lookup.
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

    @Volatile
    private var activeMatcher: AdblockMatcher? = initialMatcher

    private val _status = MutableStateFlow(initialStatus)
    val status: StateFlow<AdblockEngineStatus> = _status.asStateFlow()

    suspend fun initialize(enabledLists: List<FilterListEntity> = emptyList()) {
        val ctx = context ?: return
        compileMutex.withLock {
            withContext(Dispatchers.IO) {
                _status.value = _status.value.copy(isCompiling = true)
                try {
                    val dir = adblockDir(ctx)
                    val engineFile = File(dir, ENGINE_CACHE_FILE)
                    val metaFile = File(dir, ENGINE_META_FILE)

                    if (NativeAdblockBridge.isLoaded && engineFile.isFile && engineFile.length() > 0L) {
                        val loadedMatcher = runCatching {
                            val bytes = engineFile.readBytes()
                            val handle = NativeAdblockBridge.nativeDeserialize(bytes)
                            if (handle != 0L) NativeAdblockMatcher(handle) else null
                        }.getOrNull()

                        if (loadedMatcher != null) {
                            val cachedCount = readMetaRuleCount(metaFile)
                            val listRuleSum = enabledLists.filter { it.enabled }.sumOf { it.ruleCount }
                            val totalRules = maxOf(listRuleSum, cachedCount)
                            swapMatcher(loadedMatcher)
                            _status.value = AdblockEngineStatus(
                                isNativeRust = true,
                                totalRules = totalRules,
                                isCompiling = false
                            )
                            return@withContext
                        }
                    }

                    val combinedRules = buildRulesText(ctx, enabledLists, includeAllExistingWhenEmpty = true)
                    compileAndSwapLocked(ctx, combinedRules)
                } finally {
                    if (_status.value.isCompiling) {
                        _status.value = _status.value.copy(isCompiling = false)
                    }
                }
            }
        }
    }

    suspend fun rebuildFromLists(enabledLists: List<FilterListEntity>) {
        val ctx = context ?: return
        compileMutex.withLock {
            withContext(Dispatchers.IO) {
                _status.value = _status.value.copy(isCompiling = true)
                try {
                    val combinedRules = buildRulesText(ctx, enabledLists, includeAllExistingWhenEmpty = false)
                    compileAndSwapLocked(ctx, combinedRules)
                } finally {
                    if (_status.value.isCompiling) {
                        _status.value = _status.value.copy(isCompiling = false)
                    }
                }
            }
        }
    }

    fun shouldBlock(
        requestUrl: String,
        pageUrl: String,
        resourceType: String,
        globalEnabled: Boolean,
        siteDisabled: Boolean
    ): Boolean {
        if (!globalEnabled || siteDisabled) return false
        if (!isHttpOrHttps(requestUrl)) return false

        val reqHost = OmniboxEngine.extractHost(requestUrl).trim('[', ']')
        if (reqHost.isBlank() || OmniboxEngine.isLocalHostOrLan(reqHost)) return false

        val matcher = activeMatcher ?: return false
        return matcher.checkNetworkRequest(requestUrl, pageUrl, resourceType)
    }

    fun cosmeticForUrl(
        url: String,
        globalEnabled: Boolean,
        cosmeticEnabled: Boolean,
        siteDisabled: Boolean
    ): CosmeticPayload {
        if (!globalEnabled || !cosmeticEnabled || siteDisabled) return CosmeticPayload()
        if (!isHttpOrHttps(url)) return CosmeticPayload()

        val host = OmniboxEngine.extractHost(url).trim('[', ']')
        if (host.isBlank() || OmniboxEngine.isLocalHostOrLan(host)) return CosmeticPayload()

        val matcher = activeMatcher ?: return CosmeticPayload()
        return matcher.cosmeticResources(url)
    }

    fun inferResourceType(url: String, headers: Map<String, String>): String {
        val secFetchDest = headerIgnoreCase(headers, "Sec-Fetch-Dest")?.lowercase()
        when (secFetchDest) {
            "script" -> return "script"
            "style" -> return "stylesheet"
            "image" -> return "image"
            "font" -> return "font"
            "audio", "video", "track" -> return "media"
            "document" -> return "document"
            "iframe", "frame" -> return "subdocument"
        }

        val accept = headerIgnoreCase(headers, "Accept")?.lowercase().orEmpty()
        if (accept.isNotBlank() && accept != "*/*") {
            when {
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
            else -> if (secFetchDest == "empty") "xmlhttprequest" else "other"
        }
    }

    private fun compileAndSwapLocked(ctx: Context, combinedRules: String) {
        val ruleCount = countFilterRules(combinedRules)
        val nativeHandle = if (NativeAdblockBridge.isLoaded) {
            NativeAdblockBridge.nativeCreateFromRules(combinedRules)
        } else {
            0L
        }

        val newMatcher: AdblockMatcher = if (nativeHandle != 0L) {
            NativeAdblockMatcher(nativeHandle)
        } else {
            FallbackKotlinAdblockMatcher(combinedRules)
        }

        swapMatcher(newMatcher)
        _status.value = AdblockEngineStatus(
            isNativeRust = newMatcher is NativeAdblockMatcher,
            totalRules = ruleCount,
            isCompiling = false
        )

        if (newMatcher is NativeAdblockMatcher) {
            val serialized = newMatcher.serialize()
            if (serialized != null && serialized.isNotEmpty()) {
                val dir = adblockDir(ctx)
                writeAtomicBytes(File(dir, ENGINE_CACHE_FILE), serialized)
                writeAtomicText(File(dir, ENGINE_META_FILE), ruleCount.toString())
            }
        }
    }

    private fun swapMatcher(newMatcher: AdblockMatcher) {
        val previous = activeMatcher
        activeMatcher = newMatcher
        previous?.close()
    }

    private fun buildRulesText(
        ctx: Context,
        enabledLists: List<FilterListEntity>,
        includeAllExistingWhenEmpty: Boolean
    ): String {
        val sb = StringBuilder(64 * 1024)
        val starter = readStarterFilters(ctx)
        if (starter.isNotEmpty()) {
            sb.append(starter).append('\n')
        }

        val listsDir = File(adblockDir(ctx), LISTS_SUBDIR)
        if (enabledLists.isNotEmpty()) {
            enabledLists.filter { it.enabled }.forEach { entity ->
                val listFile = File(listsDir, "${entity.id}.txt")
                if (listFile.isFile) {
                    runCatching { listFile.readText() }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { sb.append(it).append('\n') }
                }
            }
        } else if (includeAllExistingWhenEmpty && listsDir.isDirectory) {
            listsDir.listFiles { file -> file.isFile && file.extension == "txt" }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    runCatching { file.readText() }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { sb.append(it).append('\n') }
                }
        }
        return sb.toString()
    }

    private fun readStarterFilters(ctx: Context): String =
        runCatching {
            ctx.assets.open(STARTER_ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrDefault("")

    private fun adblockDir(ctx: Context): File =
        File(ctx.filesDir, ADBLOCK_DIR).apply { mkdirs() }

    private fun readMetaRuleCount(metaFile: File): Int =
        runCatching {
            if (metaFile.isFile) metaFile.readText().trim().toIntOrNull() ?: 0 else 0
        }.getOrDefault(0)

    private fun writeAtomicBytes(target: File, bytes: ByteArray) {
        runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeBytes(bytes)
            if (target.exists() && !tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            } else if (!target.exists()) {
                tmp.renameTo(target)
            }
        }
    }

    private fun writeAtomicText(target: File, content: String) {
        writeAtomicBytes(target, content.toByteArray(Charsets.UTF_8))
    }

    private fun isHttpOrHttps(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    private fun headerIgnoreCase(headers: Map<String, String>, key: String): String? {
        headers[key]?.let { return it }
        return headers.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    private fun extractPathExtension(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull()
            ?: url.substringAfter("://", url)
                .substringAfter('/', "")
                .substringBefore('?')
                .substringBefore('#')
        if (path.isBlank()) return ""
        val lastSegment = path.substringAfterLast('/')
        val dotIdx = lastSegment.lastIndexOf('.')
        if (dotIdx < 0 || dotIdx == lastSegment.lastIndex) return ""
        return lastSegment.substring(dotIdx + 1).lowercase()
    }

    companion object {
        private const val ADBLOCK_DIR = "adblock"
        private const val LISTS_SUBDIR = "lists"
        private const val ENGINE_CACHE_FILE = "engine.dat"
        private const val ENGINE_META_FILE = "engine.meta"
        private const val STARTER_ASSET_PATH = "adblock/starter_filters.txt"

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
