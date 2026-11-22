package app.overbay.swiftfox.features.omnibox

import app.overbay.swiftfox.data.prefs.BrowserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

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

    private val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}(:\d+)?(/.*)?$""")
    private val domainRegex = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9-]+)+(:\d+)?(/.*)?$""")
    private val localhostRegex = Regex("""^localhost(:\d+)?(/.*)?$""", RegexOption.IGNORE_CASE)

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    fun detectBang(input: String): ResolvedBang? {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) return null
        val firstSpace = trimmed.indexOf(' ')
        val token = if (firstSpace >= 0) trimmed.substring(0, firstSpace) else trimmed
        val normalized = token.lowercase()
        val matched = bangs.firstOrNull { bang -> bang.triggers.any { it == normalized } }
            ?: return null
        val remainder = if (firstSpace >= 0) trimmed.substring(firstSpace + 1).trim() else ""
        return ResolvedBang(bang = matched, triggerUsed = normalized, query = remainder)
    }

    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return true
        }
        return localhostRegex.matches(trimmed) ||
            ipv4Regex.matches(trimmed) ||
            domainRegex.matches(trimmed)
    }

    fun resolveInput(rawInput: String, settings: BrowserSettings): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return NEW_TAB_URL

        val bangMatch = detectBang(trimmed)
        if (bangMatch != null && bangMatch.query.isNotEmpty()) {
            val encoded = URLEncoder.encode(bangMatch.query, StandardCharsets.UTF_8.name())
            return bangMatch.bang.urlTemplate.replace("%s", encoded)
        }

        if (looksLikeUrl(trimmed)) {
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("about:", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true)
            ) {
                return trimmed
            }
            val defaultScheme = if (localhostRegex.matches(trimmed) || ipv4Regex.matches(trimmed)) {
                "http://"
            } else {
                "https://"
            }
            return "$defaultScheme$trimmed"
        }

        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        return settings.resolvedSearchTemplate().replace("%s", encoded)
    }

    fun extractHost(url: String): String {
        if (url.isBlank() || url == NEW_TAB_URL) return ""
        return runCatching {
            URI(url).host?.removePrefix("www.")?.lowercase().orEmpty()
        }.getOrDefault("")
    }

    fun isLocalHostOrLan(host: String): Boolean {
        val clean = host.lowercase()
        if (clean == "localhost" || clean == "127.0.0.1" || clean == "::1") return true
        if (clean.endsWith(".local") || clean.endsWith(".lan") || clean.endsWith(".home")) return true
        if (clean.startsWith("192.168.") || clean.startsWith("10.")) return true
        if (clean.startsWith("172.")) {
            val second = clean.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            if (second in 16..31) return true
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

        runCatching {
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 15; Mobile) Swiftfox/0.1")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string() ?: return@use emptyList()
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
        }.getOrDefault(emptyList())
    }
}
