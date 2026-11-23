package app.overbay.swiftfox.features.omnibox

import app.overbay.swiftfox.BuildConfig
import app.overbay.swiftfox.data.prefs.BrowserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class SearchBang(
    val triggers: List<String>,
    val name: String,
    val urlTemplate: String
)

data class ResolvedBang(
    val bang: SearchBang,
    val triggerUsed: String,
    val query: String
)

enum class SuggestionKind {
    OPEN_TAB,
    BOOKMARK,
    HISTORY,
    BANG,
    SEARCH,
    DIRECT_URL
}

data class OmniboxSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val targetUrl: String,
    val kind: SuggestionKind,
    val tabId: String? = null
)

object OmniboxEngine {

    const val NEW_TAB_URL = "about:blank"

    val bangs: List<SearchBang> = listOf(
        SearchBang(listOf("!w", "@w"), "Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s"),
        SearchBang(listOf("!yt", "@yt"), "YouTube", "https://www.youtube.com/results?search_query=%s"),
        SearchBang(listOf("!gh", "@gh"), "GitHub", "https://github.com/search?q=%s"),
        SearchBang(listOf("!r", "@r"), "Reddit", "https://www.reddit.com/search/?q=%s"),
        SearchBang(listOf("!g", "@g"), "Google", "https://www.google.com/search?q=%s"),
        SearchBang(listOf("!ddg", "@ddg"), "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        SearchBang(listOf("!k", "@k"), "Kagi", "https://kagi.com/search?q=%s"),
        SearchBang(listOf("!mdn", "@mdn"), "MDN Docs", "https://developer.mozilla.org/en-US/search?q=%s"),
        SearchBang(listOf("!maps", "@maps"), "Google Maps", "https://www.google.com/maps/search/%s"),
        SearchBang(listOf("!a", "@a"), "Amazon", "https://www.amazon.com/s?k=%s")
    )

    private const val MAX_SUGGESTION_BYTES = 128 * 1024

    private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(:\d{1,5})?([/?#].*)?$""")
    private val ipv4StrictHostRegex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val ipv6HostRegex = Regex("""^\[[0-9a-fA-F:]+](:\d{1,5})?([/?#].*)?$""")
    private val domainRegex = Regex(
        """^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\.([a-zA-Z]{2,63}|xn--[a-zA-Z0-9-]{2,59})(:\d{1,5})?([/?#].*)?$"""
    )
    private val localhostRegex = Regex("""^localhost(:\d{1,5})?([/?#].*)?$""", RegexOption.IGNORE_CASE)
    private val bareHostnameRegex = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$""")
    private val authorityExtractRegex = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#\\]+)""")

    /** Common file/code extensions that users search for rather than navigating to as TLDs. */
    private val nonTldFileExtensions = setOf(
        "js", "ts", "jsx", "tsx", "mjs", "cjs", "kt", "kts", "java", "cpp", "hpp",
        "html", "htm", "css", "scss", "sass", "less", "json", "xml", "yaml", "yml",
        "toml", "ini", "cfg", "conf", "env", "txt", "log", "csv", "sql", "pdf",
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg",
        "apk", "aab", "exe", "dll", "dmg", "pkg", "deb", "rpm", "msi",
        "zip", "tar", "gz", "tgz", "rar", "7z", "bz2", "xz",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "mp3", "mp4", "mkv", "avi", "mov", "wav", "flac", "ogg", "webm",
        "wasm", "class", "jar", "aar", "gradle"
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var configuredClient: OkHttpClient? = null

    private val fallbackHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    fun configureHttpClient(baseClient: OkHttpClient) {
        configuredClient = baseClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val httpClient: OkHttpClient
        get() = configuredClient ?: fallbackHttpClient

    fun detectBang(input: String): ResolvedBang? {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) return null
        val firstSpace = trimmed.indexOf(' ')
        val token = if (firstSpace >= 0) trimmed.substring(0, firstSpace) else trimmed
        val normalized = token.lowercase(Locale.ROOT)
        val matched = bangs.firstOrNull { bang -> bang.triggers.any { it == normalized } }
            ?: return null
        val remainder = if (firstSpace >= 0) trimmed.substring(firstSpace + 1).trim() else ""
        return ResolvedBang(bang = matched, triggerUsed = normalized, query = remainder)
    }

    private fun matchesValidIpv4(input: String): Boolean {
        val m = ipv4Regex.matchEntire(input) ?: return false
        return (1..4).all { idx ->
            val octet = m.groupValues[idx].toIntOrNull()
            octet != null && octet in 0..255
        }
    }

    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return true
        }
        if (localhostRegex.matches(trimmed) || matchesValidIpv4(trimmed) || ipv6HostRegex.matches(trimmed)) {
            return true
        }
        val domainMatch = domainRegex.matchEntire(trimmed) ?: return false
        val tld = domainMatch.groupValues[4].lowercase(Locale.ROOT)
        val hasPathPortOrQuery = domainMatch.groupValues[5].isNotEmpty() || domainMatch.groupValues[6].isNotEmpty()
        if (!hasPathPortOrQuery && tld in nonTldFileExtensions) {
            return false
        }
        return true
    }

    fun buildSearchUrl(query: String, settings: BrowserSettings): String {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        return settings.resolvedSearchTemplate().replace("%s", encoded)
    }

    fun bangHomepageUrl(bang: SearchBang): String {
        return runCatching {
            val uri = URI(bang.urlTemplate.replace("%s", ""))
            val scheme = uri.scheme ?: "https"
            val authority = uri.rawAuthority ?: return@runCatching bang.urlTemplate.replace("%s", "")
            "$scheme://$authority/"
        }.getOrElse { bang.urlTemplate.replace("%s", "") }
    }

    fun resolveInput(rawInput: String, settings: BrowserSettings): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return NEW_TAB_URL

        val bangMatch = detectBang(trimmed)
        if (bangMatch != null) {
            return if (bangMatch.query.isNotEmpty()) {
                val encoded = URLEncoder.encode(bangMatch.query, StandardCharsets.UTF_8.name())
                bangMatch.bang.urlTemplate.replace("%s", encoded)
            } else {
                bangHomepageUrl(bangMatch.bang)
            }
        }

        if (looksLikeUrl(trimmed)) {
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("about:", ignoreCase = true)
            ) {
                return trimmed
            }
            val defaultScheme = if (
                localhostRegex.matches(trimmed) ||
                matchesValidIpv4(trimmed) ||
                ipv6HostRegex.matches(trimmed)
            ) {
                "http://"
            } else {
                "https://"
            }
            return "$defaultScheme$trimmed"
        }

        return buildSearchUrl(trimmed, settings)
    }

    /** Canonical host key used across site overrides, HTTPS-Only bypasses, permissions, and display. */
    fun normalizeHost(rawHost: String?): String {
        if (rawHost.isNullOrBlank()) return ""
        return rawHost.trim()
            .lowercase(Locale.ROOT)
            .removePrefix("[")
            .removeSuffix("]")
            .removePrefix("www.")
    }

    fun extractHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed.equals(NEW_TAB_URL, ignoreCase = true)) return ""
        val fromUri = runCatching { URI(trimmed).host }.getOrNull()
        if (!fromUri.isNullOrBlank()) {
            return normalizeHost(fromUri)
        }
        // Tolerant fallback for URLs containing unencoded spaces, brackets, backslashes, or non-ASCII chars
        val authority = authorityExtractRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: return ""
        val hostPort = authority.substringAfterLast('@')
        val rawHost = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']') + "]"
        } else {
            hostPort.substringBefore(':')
        }
        return normalizeHost(rawHost)
    }

    private fun parseStrictIpv4Octets(host: String): IntArray? {
        val m = ipv4StrictHostRegex.matchEntire(host) ?: return null
        val octets = IntArray(4)
        for (i in 0..3) {
            val octet = m.groupValues[i + 1].toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            octets[i] = octet
        }
        return octets
    }

    private fun isLocalIpv4Octets(octets: IntArray): Boolean {
        val b0 = octets[0]
        val b1 = octets[1]
        if (b0 == 0) return true // 0.0.0.0/8 ("this network", aliases localhost on Linux/Android)
        if (b0 == 127) return true // 127.0.0.0/8 loopback
        if (b0 == 10) return true // 10.0.0.0/8 RFC 1918
        if (b0 == 100 && b1 in 64..127) return true // 100.64.0.0/10 CGNAT / Tailscale
        if (b0 == 169 && b1 == 254) return true // 169.254.0.0/16 link-local & cloud metadata
        if (b0 == 172 && b1 in 16..31) return true // 172.16.0.0/12 RFC 1918
        if (b0 == 192 && b1 == 168) return true // 192.168.0.0/16 RFC 1918
        if (b0 in 224..255) return true // multicast / reserved / broadcast
        return false
    }

    private fun parseIpv6Bytes(rawHost: String): ByteArray? {
        val withoutZone = rawHost.substringBefore('%')
        if (!withoutZone.contains(':')) return null
        val doubleColonIndex = withoutZone.indexOf("::")
        if (doubleColonIndex >= 0 && withoutZone.indexOf("::", doubleColonIndex + 2) >= 0) return null

        val parts = if (doubleColonIndex >= 0) {
            val headStr = withoutZone.substring(0, doubleColonIndex)
            val tailStr = withoutZone.substring(doubleColonIndex + 2)
            val head = if (headStr.isEmpty()) emptyList() else headStr.split(':')
            val tail = if (tailStr.isEmpty()) emptyList() else tailStr.split(':')
            head to tail
        } else {
            withoutZone.split(':') to null
        }

        val headGroups = mutableListOf<Int>()
        val tailGroups = mutableListOf<Int>()

        fun parseSegmentList(segments: List<String>, dest: MutableList<Int>, allowTrailingIpv4: Boolean): Boolean {
            for (idx in segments.indices) {
                val seg = segments[idx]
                if (seg.isEmpty()) return false
                if (seg.contains('.')) {
                    if (!allowTrailingIpv4 || idx != segments.lastIndex) return false
                    val octets = parseStrictIpv4Octets(seg) ?: return false
                    dest.add((octets[0] shl 8) or octets[1])
                    dest.add((octets[2] shl 8) or octets[3])
                } else {
                    if (seg.length > 4) return false
                    val value = seg.toIntOrNull(16) ?: return false
                    if (value !in 0..0xFFFF) return false
                    dest.add(value)
                }
            }
            return true
        }

        val hasCompressed = parts.second != null
        if (!parseSegmentList(parts.first, headGroups, !hasCompressed)) return null
        if (parts.second != null) {
            if (!parseSegmentList(parts.second!!, tailGroups, true)) return null
            if (headGroups.size + tailGroups.size > 7) return null
        } else if (headGroups.size != 8) {
            return null
        }

        val bytes = ByteArray(16)
        var byteIdx = 0
        for (g in headGroups) {
            bytes[byteIdx++] = ((g ushr 8) and 0xFF).toByte()
            bytes[byteIdx++] = (g and 0xFF).toByte()
        }
        val missingGroups = 8 - headGroups.size - tailGroups.size
        byteIdx += missingGroups * 2
        for (g in tailGroups) {
            bytes[byteIdx++] = ((g ushr 8) and 0xFF).toByte()
            bytes[byteIdx++] = (g and 0xFF).toByte()
        }
        return bytes
    }

    private fun isLocalIpv6Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        // :: (unspecified) or ::1 (loopback)
        val first15Zero = (0..14).all { bytes[it].toInt() == 0 }
        if (first15Zero && (bytes[15].toInt() == 0 || bytes[15].toInt() == 1)) return true
        // fc00::/7 (Unique Local IPv6)
        if ((b0 and 0xFE) == 0xFC) return true
        // fe80::/10 (Link-Local)
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
        // ff00::/8 (Multicast)
        if (b0 == 0xFF) return true
        // ::ffff:0:0/96 (IPv4-mapped) or ::/96 (IPv4-compatible)
        val prefixZero = (0..9).all { bytes[it].toInt() == 0 }
        val isMapped = prefixZero && (bytes[10].toInt() and 0xFF) == 0xFF && (bytes[11].toInt() and 0xFF) == 0xFF
        val isCompat = prefixZero && bytes[10].toInt() == 0 && bytes[11].toInt() == 0
        if (isMapped || isCompat) {
            val octets = IntArray(4) { bytes[12 + it].toInt() and 0xFF }
            return isLocalIpv4Octets(octets)
        }
        return false
    }

    fun isLocalOrPrivateInetAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
        ) {
            return true
        }
        val bytes = addr.address
        return when (bytes.size) {
            4 -> isLocalIpv4Octets(IntArray(4) { bytes[it].toInt() and 0xFF })
            16 -> isLocalIpv6Bytes(bytes)
            else -> false
        }
    }

    fun isLocalHostOrLan(host: String): Boolean {
        val clean = host.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trimEnd('.')
            .lowercase(Locale.ROOT)
        if (clean.isEmpty()) return false
        if (clean == "localhost" || clean.endsWith(".localhost")) return true

        val ipv4 = parseStrictIpv4Octets(clean)
        if (ipv4 != null) {
            return isLocalIpv4Octets(ipv4)
        }

        if (clean.contains(':')) {
            val ipv6 = parseIpv6Bytes(clean)
            if (ipv6 != null) {
                return isLocalIpv6Bytes(ipv6)
            }
        }

        if (clean.endsWith(".local") ||
            clean.endsWith(".lan") ||
            clean.endsWith(".home") ||
            clean.endsWith(".internal")
        ) {
            return true
        }
        // Single-label intranet hostnames (e.g. "router", "nas")
        if (!clean.contains('.') && !clean.contains(':') && bareHostnameRegex.matches(clean)) {
            return true
        }
        return false
    }

    suspend fun fetchLiveSuggestions(
        query: String,
        settings: BrowserSettings
    ): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !settings.searchSuggestionsEnabled) return@withContext emptyList()
        if (looksLikeUrl(trimmed) || detectBang(trimmed) != null) return@withContext emptyList()

        val template = settings.searchEngine.suggestUrlTemplate ?: return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        val requestUrl = template.replace("%s", encoded)

        val call = httpClient.newCall(
            Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 15; Mobile) Swiftfox/${BuildConfig.VERSION_NAME}")
                .build()
        )
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = readBoundedBody(response.body, MAX_SUGGESTION_BYTES)
                val root = json.parseToJsonElement(body).jsonArray
                if (root.size >= 2 && root[1] is JsonArray) {
                    root[1].jsonArray
                        .mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        .filter { it.isNotBlank() }
                        .take(5)
                } else {
                    emptyList()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun readBoundedBody(body: ResponseBody?, maxBytes: Int): String {
        if (body == null) return ""
        val contentLength = body.contentLength()
        if (contentLength > maxBytes) throw IOException("Suggestion payload too large")
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                total += read
                if (total > maxBytes) throw IOException("Suggestion payload too large")
                out.write(buf, 0, read)
            }
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }
}
